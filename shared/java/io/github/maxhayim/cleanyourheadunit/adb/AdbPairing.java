package io.github.maxhayim.cleanyourheadunit.adb;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

/**
 * Android 11+ "Pair device with pairing code", written from AOSP's adb pairing_connection and
 * pairing_auth. Over a TLS 1.3 connection to the pairing port:
 *
 *  1. both sides export 64 bytes of keying material ("adb-label\0") from the TLS session;
 *     the SPAKE2 password is the six-digit code followed by those bytes;
 *  2. they swap SPAKE2 messages (we are Alice, "adb pair client\0"; adbd is Bob);
 *  3. the SPAKE2 key, through HKDF-SHA256 ("adb pairing_auth aes-128-gcm key"), keys AES-128-GCM;
 *  4. they swap an encrypted PeerInfo: ours carries this app's ADB public key, adbd's its GUID.
 *
 * With a wrong code the keys differ, adbd's PeerInfo can't be decrypted, and nothing is stored.
 * Packets: version (1 byte, 1), type (1 byte), payload size (4 bytes, big-endian), payload.
 */
public final class AdbPairing {
    private AdbPairing() {}

    static final int VERSION = 1, SPAKE2_MSG = 0, PEER_INFO = 1;
    static final int PEER_RSA_PUB_KEY = 0, PEER_DEVICE_GUID = 1, PEER_INFO_SIZE = 8192;
    static final String EXPORT_LABEL = "adb-label\0";
    static final int EXPORT_LENGTH = 64;
    static final byte[] CLIENT_NAME = "adb pair client\0".getBytes(StandardCharsets.US_ASCII);
    static final byte[] SERVER_NAME = "adb pair server\0".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_PACKET = 16 * 1024;

    /**
     * Pairs this app's key with the unit. Returns adbd's GUID. Throws with a readable message on a
     * wrong code, a closed pairing screen, or a network problem.
     */
    public static String pair(String host, int port, String code, AdbKey key, String keyName, AdbTls.KeyExporter exporter,
                              int timeoutMs) throws IOException, GeneralSecurityException {
        String digits = code == null ? "" : code.replaceAll("\\s", "");
        if (!digits.matches("\\d{6}")) throw new IOException("The pairing code is six digits.");
        SSLContext ctx = AdbTls.context(key);
        Socket plain = new Socket();
        plain.connect(new InetSocketAddress(host, port), timeoutMs);
        plain.setSoTimeout(timeoutMs);
        try (SSLSocket s = AdbTls.client(ctx, plain, host, port)) {
            byte[] ekm = exporter.export(s, EXPORT_LABEL, EXPORT_LENGTH);
            Spake2 sp = new Spake2(Spake2.Role.ALICE, CLIENT_NAME, SERVER_NAME);
            byte[] msg = sp.generateMsg(password(digits, ekm), new SecureRandom());
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            writePacket(out, SPAKE2_MSG, msg);
            byte[] theirs = readPacket(in, SPAKE2_MSG);
            PairingCipher cipher = new PairingCipher(sp.processMsg(theirs));
            writePacket(out, PEER_INFO, cipher.encrypt(peerInfo(PEER_RSA_PUB_KEY, key.adbPublicKey(keyName))));
            byte[] reply;
            try {
                reply = cipher.decrypt(readPacket(in, PEER_INFO));
            } catch (GeneralSecurityException e) {
                throw new IOException("Wrong pairing code. Check the code on the Pair device screen and try again.");
            }
            if (reply.length != PEER_INFO_SIZE || reply[0] != PEER_DEVICE_GUID) throw new IOException("The unit answered with something other than its ID.");
            return text(Arrays.copyOfRange(reply, 1, reply.length));
        } catch (java.io.EOFException e) {
            throw new IOException("The unit closed the pairing connection. That usually means a wrong code, or the Pair device screen was closed.");
        }
    }

    static byte[] password(String digits, byte[] ekm) {
        byte[] c = digits.getBytes(StandardCharsets.US_ASCII);
        byte[] pw = new byte[c.length + ekm.length];
        System.arraycopy(c, 0, pw, 0, c.length);
        System.arraycopy(ekm, 0, pw, c.length, ekm.length);
        return pw;
    }

    static byte[] peerInfo(int type, byte[] data) {
        byte[] p = new byte[PEER_INFO_SIZE];
        p[0] = (byte) type;
        System.arraycopy(data, 0, p, 1, Math.min(data.length, PEER_INFO_SIZE - 1));
        return p;
    }

    static String text(byte[] b) {
        int n = 0;
        while (n < b.length && b[n] != 0) n++;
        return new String(b, 0, n, StandardCharsets.UTF_8);
    }

    static void writePacket(OutputStream out, int type, byte[] payload) throws IOException {
        byte[] h = {VERSION, (byte) type, (byte) (payload.length >>> 24), (byte) (payload.length >>> 16),
                (byte) (payload.length >>> 8), (byte) payload.length};
        out.write(h);
        out.write(payload);
        out.flush();
    }

    static byte[] readPacket(InputStream in, int expectedType) throws IOException {
        DataInputStream d = new DataInputStream(in);
        int version = d.readUnsignedByte(), type = d.readUnsignedByte(), size = d.readInt();
        if (version != VERSION) throw new IOException("Unsupported pairing version " + version + ".");
        if (type != expectedType) throw new IOException("Unexpected pairing message.");
        if (size <= 0 || size > MAX_PACKET) throw new IOException("Pairing message has a bad size.");
        byte[] p = new byte[size];
        d.readFully(p);
        return p;
    }

    /** AES-128-GCM keyed from the SPAKE2 key; the nonce is a per-direction 64-bit little-endian counter. */
    static final class PairingCipher {
        private final SecretKeySpec key;
        private long encSeq, decSeq;

        PairingCipher(byte[] keyMaterial) throws GeneralSecurityException {
            key = new SecretKeySpec(hkdf(keyMaterial, "adb pairing_auth aes-128-gcm key".getBytes(StandardCharsets.US_ASCII), 16), "AES");
        }

        byte[] encrypt(byte[] plain) throws GeneralSecurityException {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce(encSeq++)));
            return c.doFinal(plain);
        }

        byte[] decrypt(byte[] sealed) throws GeneralSecurityException {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce(decSeq++)));
            return c.doFinal(sealed);
        }

        static byte[] nonce(long seq) {
            byte[] n = new byte[12];
            for (int i = 0; i < 8; i++) n[i] = (byte) (seq >>> (8 * i));
            return n;
        }
    }

    /** HKDF-SHA256 (RFC 5869) with no salt. */
    static byte[] hkdf(byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(new byte[32], "HmacSHA256"));      // no salt = HashLen zero bytes
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        byte[] out = new byte[length];
        byte[] t = new byte[0];
        int off = 0;
        for (int i = 1; off < length; i++) {
            mac.update(t);
            mac.update(info);
            mac.update((byte) i);
            t = mac.doFinal();
            int n = Math.min(t.length, length - off);
            System.arraycopy(t, 0, out, off, n);
            off += n;
        }
        return out;
    }
}
