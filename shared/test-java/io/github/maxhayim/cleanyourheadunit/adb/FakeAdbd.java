package io.github.maxhayim.cleanyourheadunit.adb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import javax.crypto.Cipher;

/**
 * A simulated adbd on the other end of a Transport, written from AOSP's protocol.txt, independently
 * of AdbConnection: it parses the key from adbd's struct format and verifies signatures with
 * plain RSA, so a wrong key encoding or signature fails the tests.
 */
final class FakeAdbd implements Transport {
    static final byte[] PREFIX = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};

    final Set<BigInteger> authorized = new HashSet<>();
    boolean ownerTapsAllow = true;
    boolean wantsTls;
    int maxPayload = 4096;
    int keyPrompts;
    final List<String> opened = new ArrayList<>();
    Function<String, String> shell = cmd -> "";
    String tcpipAnswer = null;      // set when a tcpip: arrived

    private final ByteArrayOutputStream inbound = new ByteArrayOutputStream();
    private final Deque<Byte> outbound = new ArrayDeque<>();
    private byte[] token;
    private boolean dropped;
    private int nextRemote = 100;

    // --- Transport: what the client sends arrives here --------------------------------------------

    @Override
    public void send(AdbMessage m) throws IOException {
        if (dropped) throw new IOException("closed");
        byte[] h = m.header();
        int[] f = AdbMessage.parseHeader(h);
        if (f[4] != AdbMessage.checksum(m.payload)) throw new AssertionError("bad checksum");
        handle(m);
    }

    @Override
    public void readFully(byte[] buf) throws IOException {
        for (int i = 0; i < buf.length; i++) {
            if (outbound.isEmpty()) throw new IOException(dropped ? "connection dropped" : "nothing to read (client waits forever)");
            buf[i] = outbound.poll();
        }
    }

    @Override
    public String kind() {
        return "Fake";
    }

    @Override
    public void close() {
        dropped = true;
    }

    private void reply(AdbMessage m) {
        if (m.payload.length > maxPayload) throw new AssertionError("payload over max");
        for (byte b : m.header()) outbound.add(b);
        for (byte b : m.payload) outbound.add(b);
    }

    private void handle(AdbMessage m) throws IOException {
        switch (m.command) {
            case AdbMessage.CNXN:
                if (m.arg0 != AdbMessage.VERSION) throw new AssertionError("version");
                if (wantsTls) {
                    reply(new AdbMessage(AdbMessage.STLS, 0x01000000, 0, null));
                    return;
                }
                token = new byte[20];
                new SecureRandom().nextBytes(token);
                reply(new AdbMessage(AdbMessage.AUTH, AdbMessage.AUTH_TOKEN, 0, token));
                return;
            case AdbMessage.AUTH:
                if (m.arg0 == AdbMessage.AUTH_SIGNATURE) {
                    for (BigInteger n : authorized) {
                        if (verifies(n, m.payload)) {
                            accept();
                            return;
                        }
                    }
                    token = new byte[20];
                    new SecureRandom().nextBytes(token);
                    reply(new AdbMessage(AdbMessage.AUTH, AdbMessage.AUTH_TOKEN, 0, token));
                } else if (m.arg0 == AdbMessage.AUTH_RSAPUBLICKEY) {
                    keyPrompts++;
                    BigInteger n = parseKey(m.payload);
                    if (ownerTapsAllow) {
                        authorized.add(n);
                        accept();
                    } else {
                        token = new byte[20];
                        new SecureRandom().nextBytes(token);
                        reply(new AdbMessage(AdbMessage.AUTH, AdbMessage.AUTH_TOKEN, 0, token));
                    }
                }
                return;
            case AdbMessage.OPEN: {
                String svc = m.text();
                opened.add(svc);
                int local = m.arg0, remote = nextRemote++;
                if (svc.startsWith("shell:")) {
                    String out = shell.apply(svc.substring("shell:".length()));
                    if (out == null) {
                        reply(new AdbMessage(AdbMessage.CLSE, 0, local, null));     // refused
                        return;
                    }
                    reply(new AdbMessage(AdbMessage.OKAY, remote, local, null));
                    byte[] data = out.getBytes(StandardCharsets.UTF_8);
                    for (int off = 0; off < data.length; off += maxPayload) {
                        reply(new AdbMessage(AdbMessage.WRTE, remote, local, Arrays.copyOfRange(data, off, Math.min(data.length, off + maxPayload))));
                    }
                    reply(new AdbMessage(AdbMessage.CLSE, remote, local, null));
                } else if (svc.startsWith("tcpip:")) {
                    tcpipAnswer = "restarting in TCP mode port: " + svc.substring(6) + "\n";
                    reply(new AdbMessage(AdbMessage.OKAY, remote, local, null));
                    reply(new AdbMessage(AdbMessage.WRTE, remote, local, tcpipAnswer.getBytes(StandardCharsets.UTF_8)));
                    dropped = true;     // adbd restarts: no CLSE, the connection just ends
                } else {
                    reply(new AdbMessage(AdbMessage.CLSE, 0, local, null));
                }
                return;
            }
            case AdbMessage.OKAY:
            case AdbMessage.CLSE:
                return;
            default:
                throw new AssertionError("unexpected " + m);
        }
    }

    private void accept() {
        reply(AdbMessage.of(AdbMessage.CNXN, AdbMessage.VERSION, maxPayload, "device::ro.product.name=fake;ro.product.model=Fake Unit;"));
    }

    /** Decodes "base64 name\0" into the modulus, checking every field of the struct. */
    static BigInteger parseKey(byte[] payload) {
        String s = new String(payload, StandardCharsets.UTF_8);
        if (!s.endsWith("\0")) throw new AssertionError("key not NUL-terminated");
        byte[] raw = Base64.getDecoder().decode(s.substring(0, s.indexOf(' ')));
        ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        int words = b.getInt();
        if (words != 64) throw new AssertionError("words " + words);
        long n0inv = b.getInt() & 0xffffffffL;
        BigInteger n = readWords(b, words), rr = readWords(b, words);
        int e = b.getInt();
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        if (!n.mod(r32).multiply(BigInteger.valueOf(n0inv)).mod(r32).equals(r32.subtract(BigInteger.ONE))) throw new AssertionError("n0inv");
        if (!rr.equals(BigInteger.ONE.shiftLeft(4096).mod(n))) throw new AssertionError("rr");
        if (e != 65537) throw new AssertionError("exponent " + e);
        return n;
    }

    private static BigInteger readWords(ByteBuffer b, int words) {
        BigInteger v = BigInteger.ZERO;
        for (int i = 0; i < words; i++) v = v.or(BigInteger.valueOf(b.getInt() & 0xffffffffL).shiftLeft(32 * i));
        return v;
    }

    private boolean verifies(BigInteger n, byte[] sig) {
        try {
            PublicKey pk = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, BigInteger.valueOf(65537)));
            Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            c.init(Cipher.DECRYPT_MODE, pk);
            byte[] plain = c.doFinal(sig);
            byte[] want = new byte[PREFIX.length + token.length];
            System.arraycopy(PREFIX, 0, want, 0, PREFIX.length);
            System.arraycopy(token, 0, want, PREFIX.length, token.length);
            return Arrays.equals(plain, want);
        } catch (Exception e) {
            return false;
        }
    }
}
