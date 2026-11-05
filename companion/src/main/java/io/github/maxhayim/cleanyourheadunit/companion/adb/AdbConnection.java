package io.github.maxhayim.cleanyourheadunit.companion.adb;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * A minimal ADB client: connect and authenticate, run shell commands one at a time, and ask adbd
 * to listen on TCP (the bridge). It speaks the original protocol, which every head unit from
 * Android 4.4 up understands. It does not do Android 11+ wireless-debugging pairing (TLS).
 *
 * Nothing here gets round Android's protection: the unit must have USB debugging on, and the
 * first time it shows "Allow USB debugging?" for this phone's key, which the owner must accept.
 */
public final class AdbConnection implements Closeable {

    /** Tells the screen what's happening during a slow step (waiting for the owner to tap Allow). */
    public interface Listener {
        void waitingForApproval(String fingerprint);
    }

    public static final String RC_MARK = "__cyhu_rc=";

    private final Transport t;
    private int maxPayload = 4096;
    private int nextLocalId = 1;
    private String banner = "";

    private AdbConnection(Transport t) {
        this.t = t;
    }

    public static AdbConnection connect(Transport t, AdbKey key, String keyName, Listener l) throws IOException {
        AdbConnection c = new AdbConnection(t);
        try {
            c.handshake(key, keyName, l);
        } catch (IOException | RuntimeException e) {
            t.close();
            throw e;
        }
        return c;
    }

    private void handshake(AdbKey key, String keyName, Listener l) throws IOException {
        t.send(AdbMessage.of(AdbMessage.CNXN, AdbMessage.VERSION, AdbMessage.MAX_PAYLOAD, "host::"));
        boolean signed = false, sentKey = false;
        while (true) {
            AdbMessage m = read();
            if (m.command == AdbMessage.CNXN) {
                maxPayload = Math.max(1024, Math.min(m.arg1, AdbMessage.MAX_PAYLOAD));
                banner = m.text();
                return;
            }
            if (m.command == AdbMessage.STLS) {
                throw new IOException("This port wants Android 11+ wireless-debugging pairing, which the companion doesn't do yet. Use the bridge (port 5555) instead.");
            }
            if (m.command != AdbMessage.AUTH || m.arg0 != AdbMessage.AUTH_TOKEN) {
                throw new IOException("Unexpected answer from the unit: " + m);
            }
            if (!signed) {
                // Already allowed before? Then the signature is enough.
                try {
                    t.send(new AdbMessage(AdbMessage.AUTH, AdbMessage.AUTH_SIGNATURE, 0, key.sign(m.payload)));
                } catch (GeneralSecurityException e) {
                    throw new IOException("Couldn't sign with this phone's key: " + e.getMessage());
                }
                signed = true;
            } else if (!sentKey) {
                // Not known yet: offer the key. The unit now shows "Allow USB debugging?".
                if (l != null) l.waitingForApproval(key.fingerprint());
                if (t instanceof TcpTransport) ((TcpTransport) t).setReadTimeout(120_000);
                t.send(new AdbMessage(AdbMessage.AUTH, AdbMessage.AUTH_RSAPUBLICKEY, 0, key.adbPublicKey(keyName)));
                sentKey = true;
            } else {
                throw new IOException("The head unit didn't allow this phone. Tap \"Allow\" on its screen and try again.");
            }
        }
    }

    private AdbMessage read() throws IOException {
        byte[] h = new byte[AdbMessage.HEADER];
        t.readFully(h);
        int[] f = AdbMessage.parseHeader(h);
        byte[] p = new byte[f[3]];
        if (p.length > 0) t.readFully(p);
        return new AdbMessage(f[0], f[1], f[2], p);
    }

    /** What the unit said about itself when it accepted the connection (device::ro.product.name=...). */
    public String banner() {
        return banner;
    }

    public String kind() {
        return t.kind();
    }

    /** Runs one service (e.g. "shell:getprop") and returns everything it wrote until it closed. */
    public synchronized String service(String name) throws IOException {
        int local = nextLocalId++;
        t.send(AdbMessage.of(AdbMessage.OPEN, local, 0, name));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // tcpip: and usb: restart adbd, which drops the connection right after its answer.
        boolean restarts = name.startsWith("tcpip:") || name.equals("usb:");
        int remote = 0;
        while (true) {
            AdbMessage m;
            try {
                m = read();
            } catch (IOException e) {
                if (restarts && out.size() > 0) return out.toString("UTF-8");
                throw e;
            }
            if (m.arg1 != local) continue;          // something for another (finished) stream
            switch (m.command) {
                case AdbMessage.OKAY:
                    remote = m.arg0;
                    break;
                case AdbMessage.WRTE:
                    remote = m.arg0;
                    out.write(m.payload);
                    try {
                        t.send(new AdbMessage(AdbMessage.OKAY, local, remote, null));
                    } catch (IOException e) {
                        if (restarts) return out.toString("UTF-8");
                        throw e;
                    }
                    break;
                case AdbMessage.CLSE:
                    if (remote == 0 && out.size() == 0) throw new IOException("The unit refused \"" + name + "\".");
                    t.send(new AdbMessage(AdbMessage.CLSE, local, remote, null));
                    return out.toString("UTF-8");
                default:
                    break;
            }
        }
    }

    /** Runs a shell command. Returns its output and exit code (legacy shell has no exit code, so we print it). */
    public Result shell(String command) throws IOException {
        String raw = service("shell:" + command + "; echo " + RC_MARK + "$?");
        return Result.parse(raw);
    }

    /**
     * The bridge: asks adbd to listen on a TCP port. adbd restarts, so this connection ends. It
     * lasts until the unit reboots (most head units only sleep when the ignition is off).
     */
    public String tcpip(int port) throws IOException {
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("port");
        return service("tcpip:" + port).trim();
    }

    public int maxPayload() {
        return maxPayload;
    }

    @Override
    public void close() {
        t.close();
    }

    /** A shell command's output and exit code. */
    public static final class Result {
        public final String out;
        public final int rc;

        Result(String out, int rc) {
            this.out = out;
            this.rc = rc;
        }

        /** Splits raw shell output into output and the exit code printed after RC_MARK. */
        public static Result parse(String raw) {
            String text = raw.replace("\r\n", "\n");
            int i = text.lastIndexOf(RC_MARK);
            if (i < 0) return new Result(text, 1);
            String tail = text.substring(i + RC_MARK.length()).trim();
            int rc;
            try {
                rc = Integer.parseInt(tail);
            } catch (NumberFormatException e) {
                rc = 1;
            }
            String out = text.substring(0, i);
            while (out.endsWith("\n")) out = out.substring(0, out.length() - 1);
            return new Result(out, rc);
        }
    }
}
