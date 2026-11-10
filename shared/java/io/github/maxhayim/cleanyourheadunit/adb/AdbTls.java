package io.github.maxhayim.cleanyourheadunit.adb;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * TLS for Android 11+ Wireless debugging. adbd recognises a client by the RSA key in its TLS
 * certificate (the same key it was given when pairing), so the certificate is simply this app's
 * ADB key, self-signed. adbd's own certificate is self-signed too; who's on the other end is
 * established by the pairing code (SPAKE2), not by a certificate authority.
 */
public final class AdbTls {
    private AdbTls() {}

    /** Asks the TLS library for exported keying material (RFC 5705 / 8446 7.5). */
    public interface KeyExporter {
        byte[] export(SSLSocket socket, String label, int length) throws IOException;
    }

    /** A TLS 1.3 context that presents this key and accepts adbd's self-signed certificate. */
    public static SSLContext context(AdbKey key) throws GeneralSecurityException, IOException {
        X509Certificate cert = certificate(key);
        char[] pw = "cyhu".toCharArray();
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setKeyEntry("adb", key.privateKey(), pw, new Certificate[]{cert});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, pw);
        SSLContext ctx = SSLContext.getInstance("TLSv1.3");
        ctx.init(kmf.getKeyManagers(), new TrustManager[]{ACCEPT_SELF_SIGNED}, new SecureRandom());
        return ctx;
    }

    /** Wraps a connected socket in TLS 1.3, as the client, and completes the handshake. */
    public static SSLSocket client(SSLContext ctx, Socket plain, String host, int port) throws IOException {
        SSLSocket s = (SSLSocket) ctx.getSocketFactory().createSocket(plain, host, port, true);
        s.setUseClientMode(true);
        s.setEnabledProtocols(new String[]{"TLSv1.3"});
        s.startHandshake();
        return s;
    }

    // adbd's certificate is self-signed and changes per device; the pairing code authenticates it.
    private static final X509TrustManager ACCEPT_SELF_SIGNED = new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    static TrustManager acceptAll() {
        return ACCEPT_SELF_SIGNED;
    }

    // --- A minimal self-signed X.509 v3 certificate, DER-encoded by hand (no extra libraries) ---

    private static final byte[] OID_SHA256_RSA = {0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x0b};
    private static final byte[] OID_CN = {0x55, 0x04, 0x03};

    static X509Certificate certificate(AdbKey key) throws GeneralSecurityException {
        byte[] alg = seq(cat(tlv(0x06, OID_SHA256_RSA), new byte[]{0x05, 0x00}));
        byte[] name = seq(tlv(0x31, seq(cat(tlv(0x06, OID_CN), tlv(0x0c, utf8("CleanYourHeadUnit"))))));
        byte[] validity = seq(cat(tlv(0x17, ascii(time(new Date(System.currentTimeMillis() - 86400000L)))),
                tlv(0x17, ascii("491231235959Z"))));
        byte[] serial = tlv(0x02, new BigInteger(63, new SecureRandom()).add(BigInteger.ONE).toByteArray());
        byte[] version = tlv(0xa0, tlv(0x02, new byte[]{2}));
        byte[] tbs = seq(cat(version, serial, alg, name, validity, name, key.publicKey().getEncoded()));
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(key.privateKey());
        sig.update(tbs);
        byte[] s = sig.sign();
        byte[] bits = new byte[s.length + 1];
        System.arraycopy(s, 0, bits, 1, s.length);
        byte[] der = seq(cat(tbs, alg, tlv(0x03, bits)));
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
    }

    private static String time(Date d) {
        SimpleDateFormat f = new SimpleDateFormat("yyMMddHHmmss'Z'", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(d);
    }

    private static byte[] seq(byte[] content) {
        return tlv(0x30, content);
    }

    static byte[] tlv(int tag, byte[] content) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(tag);
        int n = content.length;
        if (n < 0x80) {
            o.write(n);
        } else if (n < 0x100) {
            o.write(0x81);
            o.write(n);
        } else if (n < 0x10000) {
            o.write(0x82);
            o.write(n >> 8);
            o.write(n);
        } else {
            o.write(0x83);
            o.write(n >> 16);
            o.write(n >> 8);
            o.write(n);
        }
        o.write(content, 0, n);
        return o.toByteArray();
    }

    private static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.write(p, 0, p.length);
        return o.toByteArray();
    }

    private static byte[] utf8(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] ascii(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }
}
