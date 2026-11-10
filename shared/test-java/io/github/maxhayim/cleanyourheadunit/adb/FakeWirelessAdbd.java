package io.github.maxhayim.cleanyourheadunit.adb;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

/**
 * A stand-in for an Android 11+ unit's Wireless debugging: a pairing port (TLS 1.3, SPAKE2 as Bob,
 * encrypted PeerInfo) and an ADB port that answers CNXN with STLS, switches to TLS, and accepts
 * only clients whose certificate key was paired. Used by the JUnit tests (with a fixed key
 * exporter) and by tools/check-adb-pairing.sh, which points Google's real adb at it.
 */
public final class FakeWirelessAdbd implements AutoCloseable {
    final ServerSocket pairing, adb;
    final Set<BigInteger> paired = Collections.synchronizedSet(new HashSet<BigInteger>());
    final String code, guid = "adb-FAKEUNIT-cyhu";
    private final AdbTls.KeyExporter exporter;
    private final SSLContext ctx;
    private final Function<String, String> shell;
    private volatile boolean closed;
    public volatile String lastPairedName = "";

    public FakeWirelessAdbd(String code, AdbTls.KeyExporter exporter, Function<String, String> shell) throws Exception {
        this.code = code;
        this.exporter = exporter;
        this.shell = shell;
        AdbKey own = AdbKey.generate();
        X509Certificate cert = AdbTls.certificate(own);
        java.security.KeyStore ks = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setKeyEntry("adbd", own.privateKey(), "x".toCharArray(), new java.security.cert.Certificate[]{cert});
        // The stand-in unit always uses the JDK's own TLS (SunJSSE). Robolectric can install Conscrypt
        // for the whole JVM; then the client below runs on Conscrypt, as it does on Android, while the
        // server stays independent of it (Conscrypt as a server fails on JDK 17's module rules).
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509", "SunJSSE");
        kmf.init(ks, "x".toCharArray());
        ctx = SSLContext.getInstance("TLSv1.3", "SunJSSE");
        ctx.init(kmf.getKeyManagers(), new TrustManager[]{AdbTls.acceptAll()}, new SecureRandom());
        SSLServerSocket ps = (SSLServerSocket) ctx.getServerSocketFactory().createServerSocket(0, 5, InetAddress.getLoopbackAddress());
        ps.setEnabledProtocols(new String[]{"TLSv1.3"});
        ps.setNeedClientAuth(true);
        pairing = ps;
        adb = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
        thread(this::pairLoop);
        thread(this::adbLoop);
    }

    public int pairingPort() {
        return pairing.getLocalPort();
    }

    public int adbPort() {
        return adb.getLocalPort();
    }

    private static void thread(Runnable r) {
        Thread t = new Thread(r, "fake-wireless-adbd");
        t.setDaemon(true);
        t.start();
    }

    // --- pairing -----------------------------------------------------------------------------

    private void pairLoop() {
        while (!closed) {
            try (SSLSocket s = (SSLSocket) pairing.accept()) {
                s.startHandshake();
                pairOne(s);
            } catch (Exception e) {
                if (!closed) System.err.println("fake adbd: pairing failed: " + e);
            }
        }
    }

    private void pairOne(SSLSocket s) throws Exception {
        byte[] ekm = exporter.export(s, AdbPairing.EXPORT_LABEL, AdbPairing.EXPORT_LENGTH);
        Spake2 sp = new Spake2(Spake2.Role.BOB, AdbPairing.SERVER_NAME, AdbPairing.CLIENT_NAME);
        byte[] msg = sp.generateMsg(AdbPairing.password(code, ekm), new SecureRandom());
        InputStream in = s.getInputStream();
        OutputStream out = s.getOutputStream();
        AdbPairing.writePacket(out, AdbPairing.SPAKE2_MSG, msg);
        byte[] theirs = AdbPairing.readPacket(in, AdbPairing.SPAKE2_MSG);
        AdbPairing.PairingCipher cipher = new AdbPairing.PairingCipher(sp.processMsg(theirs));
        byte[] info = cipher.decrypt(AdbPairing.readPacket(in, AdbPairing.PEER_INFO));   // fails on a wrong code
        if (info[0] != AdbPairing.PEER_RSA_PUB_KEY) throw new IOException("not a key");
        String keyLine = AdbPairing.text(Arrays.copyOfRange(info, 1, info.length)).trim();
        paired.add(FakeAdbd.parseKey((keyLine + "\0").getBytes(StandardCharsets.UTF_8)));
        lastPairedName = keyLine.contains(" ") ? keyLine.substring(keyLine.indexOf(' ') + 1) : "";
        AdbPairing.writePacket(out, AdbPairing.PEER_INFO,
                cipher.encrypt(AdbPairing.peerInfo(AdbPairing.PEER_DEVICE_GUID, guid.getBytes(StandardCharsets.UTF_8))));
    }

    // --- ADB over TLS ---------------------------------------------------------------------------

    private void adbLoop() {
        while (!closed) {
            try {
                final Socket s = adb.accept();
                thread(() -> {
                    try {
                        serve(s);
                    } catch (Exception e) {
                        if (!closed) System.err.println("fake adbd: connection ended: " + e);
                    } finally {
                        try {
                            s.close();
                        } catch (IOException ignored) {
                        }
                    }
                });
            } catch (IOException e) {
                if (!closed) System.err.println("fake adbd: " + e);
            }
        }
    }

    private void serve(Socket plain) throws Exception {
        InputStream in = plain.getInputStream();
        OutputStream out = plain.getOutputStream();
        AdbMessage m = read(in);
        if (m.command != AdbMessage.CNXN) throw new IOException("expected CNXN, got " + m);
        write(out, new AdbMessage(AdbMessage.STLS, AdbMessage.STLS_VERSION, 0, null));
        m = read(in);
        if (m.command != AdbMessage.STLS) throw new IOException("expected STLS, got " + m);
        SSLSocket s = (SSLSocket) ctx.getSocketFactory().createSocket(plain, "localhost", plain.getPort(), true);
        s.setUseClientMode(false);
        s.setNeedClientAuth(true);
        s.setEnabledProtocols(new String[]{"TLSv1.3"});
        s.startHandshake();
        X509Certificate peer = (X509Certificate) s.getSession().getPeerCertificates()[0];
        if (!paired.contains(((RSAPublicKey) peer.getPublicKey()).getModulus())) {
            s.close();
            throw new IOException("client key not paired");
        }
        in = s.getInputStream();
        out = s.getOutputStream();
        write(out, AdbMessage.of(AdbMessage.CNXN, AdbMessage.VERSION, 256 * 1024,
                "device::ro.product.name=fakeunit;ro.product.model=FakeUnit;ro.product.device=fakeunit;features="));
        while (true) {
            m = read(in);
            if (m.command == AdbMessage.OPEN) {
                String svc = m.text();
                int remote = m.arg0, local = 77;
                write(out, new AdbMessage(AdbMessage.OKAY, local, remote, null));
                String result = svc.startsWith("shell:") ? shell.apply(svc.substring(6)) : "";
                write(out, new AdbMessage(AdbMessage.WRTE, local, remote, result.getBytes(StandardCharsets.UTF_8)));
                // Wait for the client's OKAY, then close the stream.
                while ((m = read(in)).command != AdbMessage.OKAY) { /* skip */ }
                write(out, new AdbMessage(AdbMessage.CLSE, local, remote, null));
            }
        }
    }

    static AdbMessage read(InputStream in) throws IOException {
        byte[] h = readFully(in, AdbMessage.HEADER);
        int[] f = AdbMessage.parseHeader(h);
        return new AdbMessage(f[0], f[1], f[2], readFully(in, f[3]));
    }

    private static byte[] readFully(InputStream in, int n) throws IOException {
        byte[] b = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(b, off, n - off);
            if (r < 0) throw new java.io.EOFException();
            off += r;
        }
        return b;
    }

    private static void write(OutputStream out, AdbMessage m) throws IOException {
        out.write(m.header());
        out.write(m.payload);
        out.flush();
    }

    @Override
    public void close() throws IOException {
        closed = true;
        pairing.close();
        adb.close();
    }

    // --- run by tools/check-adb-pairing.sh with a JDK that can export keying material ---------

    /** The JDK's own exporter (JDK 25+), called by reflection so this file still compiles on 17. */
    static final AdbTls.KeyExporter JDK_EXPORTER = (socket, label, length) -> {
        try {
            Object session = socket.getSession();
            java.lang.reflect.Method mth = Class.forName("javax.net.ssl.ExtendedSSLSession")
                    .getMethod("exportKeyingMaterialData", String.class, byte[].class, int.class);
            return (byte[]) mth.invoke(session, label, null, length);
        } catch (ReflectiveOperationException e) {
            throw new IOException("This JDK can't export TLS keying material (needs JDK 25+): " + e);
        }
    };

    /**
     * serve CODE PORTFILE: run until killed, writing "pairPort adbPort" to PORTFILE.
     * selftest CODE: pair and run a shell command with this project's own client.
     */
    public static void main(String[] args) throws Exception {
        String mode = args[0], code = args[1];
        FakeWirelessAdbd d = new FakeWirelessAdbd(code, JDK_EXPORTER, cmd -> cmd.startsWith("echo ") ? cmd.substring(5).replaceAll(";.*", "") + "\n" : "?\n");
        if ("serve".equals(mode)) {
            java.nio.file.Files.write(java.nio.file.Paths.get(args[2]), (d.pairingPort() + " " + d.adbPort() + "\n").getBytes(StandardCharsets.UTF_8));
            System.out.println("fake adbd: pairing on " + d.pairingPort() + ", adb on " + d.adbPort());
            Thread.sleep(Long.MAX_VALUE);
        }
        AdbKey key = AdbKey.generate();
        String guid = AdbPairing.pair("127.0.0.1", d.pairingPort(), code, key, "selftest@cyhu", JDK_EXPORTER, 10000);
        TcpTransport t = new TcpTransport("127.0.0.1", d.adbPort(), 10000).withTls(AdbTls.context(key));
        try (AdbConnection c = AdbConnection.connect(t, key, "selftest@cyhu", null)) {
            String out = c.service("shell:echo selftest-ok").trim();
            System.out.println("selftest: paired with " + guid + " as " + d.lastPairedName + ", over " + c.kind() + ", shell said: " + out);
            if (!"selftest-ok".equals(out)) throw new IllegalStateException("wrong shell output");
        }
        try {
            AdbPairing.pair("127.0.0.1", d.pairingPort(), code.equals("000000") ? "111111" : "000000", AdbKey.generate(), "x", JDK_EXPORTER, 10000);
            throw new IllegalStateException("a wrong code paired");
        } catch (IOException expected) {
            System.out.println("selftest: wrong code refused: " + expected.getMessage());
        }
        d.close();
        System.out.println("SELFTEST PASS");
    }
}
