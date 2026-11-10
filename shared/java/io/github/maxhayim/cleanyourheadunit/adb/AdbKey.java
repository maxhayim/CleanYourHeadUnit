package io.github.maxhayim.cleanyourheadunit.adb;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * This phone's ADB key: a 2048-bit RSA pair kept in the app's private storage. The head unit
 * shows "Allow USB debugging?" with its fingerprint the first time, exactly as it does for a
 * computer. The private key never leaves the phone.
 */
public final class AdbKey {
    private static final int BITS = 2048, WORDS = BITS / 32;

    /** DER prefix of a SHA-1 DigestInfo: adbd checks the token as if it were a SHA-1 hash. */
    private static final byte[] SHA1_PREFIX = {
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};

    private final RSAPrivateKey priv;
    private final RSAPublicKey pub;

    AdbKey(KeyPair kp) {
        this.priv = (RSAPrivateKey) kp.getPrivate();
        this.pub = (RSAPublicKey) kp.getPublic();
    }

    public static AdbKey generate() throws GeneralSecurityException {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(BITS);
        return new AdbKey(g.generateKeyPair());
    }

    /** Loads the key from dir, or makes and saves one the first time. */
    public static synchronized AdbKey loadOrCreate(File dir) throws IOException, GeneralSecurityException {
        File privFile = new File(dir, "adbkey.pk8"), pubFile = new File(dir, "adbkey.x509");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        if (privFile.exists() && pubFile.exists()) {
            return new AdbKey(new KeyPair(
                    kf.generatePublic(new X509EncodedKeySpec(readAll(pubFile))),
                    kf.generatePrivate(new PKCS8EncodedKeySpec(readAll(privFile)))));
        }
        AdbKey k = generate();
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Can't create " + dir);
        writeAll(pubFile, k.pub.getEncoded());
        writeAll(privFile, k.priv.getEncoded());
        return k;
    }

    /** Signs adbd's 20-byte AUTH token. */
    public byte[] sign(byte[] token) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        c.init(Cipher.ENCRYPT_MODE, priv);
        c.update(SHA1_PREFIX);
        c.update(token);
        return c.doFinal();
    }

    /** The public key in adbd's format: base64 of Android's RSAPublicKey struct, a space, a name. */
    public byte[] adbPublicKey(String name) {
        String b64 = base64(androidPublicKey());
        return (b64 + " " + name + "\0").getBytes(StandardCharsets.UTF_8);
    }

    /** The fingerprint the head unit shows: MD5 of the decoded key struct, as colon-separated hex. */
    public String fingerprint() {
        try {
            byte[] d = MessageDigest.getInstance("MD5").digest(androidPublicKey());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < d.length; i++) sb.append(i == 0 ? "" : ":").append(String.format("%02X", d[i] & 0xff));
            return sb.toString();
        } catch (GeneralSecurityException e) {
            return "";
        }
    }

    public RSAPublicKey publicKey() {
        return pub;
    }

    /** For the TLS certificate (Android 11+ Wireless debugging). Never leaves the app. */
    java.security.PrivateKey privateKey() {
        return priv;
    }

    /**
     * struct RSAPublicKey { uint32 len; uint32 n0inv; uint32 n[len]; uint32 rr[len]; uint32 exponent; }
     * little-endian, from AOSP libcrypto_utils android_pubkey.
     */
    byte[] androidPublicKey() {
        BigInteger n = pub.getModulus();
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = n.mod(r32).modInverse(r32).negate().mod(r32);
        BigInteger rr = BigInteger.ONE.shiftLeft(BITS * 2).mod(n);
        ByteBuffer b = ByteBuffer.allocate(4 * (3 + 2 * WORDS)).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(WORDS);
        b.putInt(n0inv.intValue());
        putWords(b, n);
        putWords(b, rr);
        b.putInt(pub.getPublicExponent().intValue());
        return b.array();
    }

    private static void putWords(ByteBuffer b, BigInteger v) {
        BigInteger mask = BigInteger.valueOf(0xffffffffL);
        for (int i = 0; i < WORDS; i++) b.putInt(v.shiftRight(32 * i).and(mask).intValue());
    }

    // Plain base64 without line breaks; android.util.Base64 isn't available in JVM tests, and
    // java.util.Base64 needs API 26.
    static String base64(byte[] data) {
        final char[] t = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
        StringBuilder sb = new StringBuilder((data.length + 2) / 3 * 4);
        for (int i = 0; i < data.length; i += 3) {
            int b0 = data[i] & 0xff, b1 = i + 1 < data.length ? data[i + 1] & 0xff : 0, b2 = i + 2 < data.length ? data[i + 2] & 0xff : 0;
            int v = (b0 << 16) | (b1 << 8) | b2;
            sb.append(t[(v >> 18) & 63]).append(t[(v >> 12) & 63]);
            sb.append(i + 1 < data.length ? t[(v >> 6) & 63] : '=');
            sb.append(i + 2 < data.length ? t[v & 63] : '=');
        }
        return sb.toString();
    }

    private static byte[] readAll(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int off = 0, n;
            while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) off += n;
            return buf;
        }
    }

    private static void writeAll(File f, byte[] data) throws IOException {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(data);
        }
        //noinspection ResultOfMethodCallIgnored
        f.setReadable(false, false);
        //noinspection ResultOfMethodCallIgnored
        f.setReadable(true, true);
    }
}
