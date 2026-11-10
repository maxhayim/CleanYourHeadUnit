package io.github.maxhayim.cleanyourheadunit.adb;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

/**
 * Android 11+ Wireless debugging: SPAKE2, key derivation, the certificate, and pairing then
 * connecting over TLS against a stand-in unit. The JVM in CI can't export TLS keying material, so
 * both ends use the same fixed bytes here; tools/check-adb-pairing.sh repeats the end-to-end part
 * with the real exporter and with Google's adb.
 */
public class WirelessDebuggingTest {
    static final AdbTls.KeyExporter FIXED = (s, label, len) -> {
        assertEquals("adb-label\0", label);
        byte[] b = new byte[len];
        Arrays.fill(b, (byte) 7);
        return b;
    };

    @Test
    public void curveBasics() {
        assertArrayEquals(Ed25519.hex("5866666666666666666666666666666666666666666666666666666666666666"), Ed25519.BASE.encode());
        assertTrue("l·B is the identity", Ed25519.BASE.multiply(Ed25519.L).sameAs(Ed25519.IDENTITY));
        Ed25519.Point p = Ed25519.BASE.multiply(BigInteger.valueOf(123456789));
        assertTrue(Ed25519.decode(p.encode()).sameAs(p));
        assertTrue(p.add(p.negate()).sameAs(Ed25519.IDENTITY));
    }

    /** M and N must be BoringSSL's: the first point found by hashing its seed strings. */
    @Test
    public void spake2PointsAreBoringSsls() throws Exception {
        assertArrayEquals(seedPoint("edwards25519 point generation seed (M)"), Spake2.M.encode());
        assertArrayEquals(seedPoint("edwards25519 point generation seed (N)"), Spake2.N.encode());
    }

    private static byte[] seedPoint(String seed) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] v = sha.digest(seed.getBytes(StandardCharsets.US_ASCII));
        while (Ed25519.decode(v) == null) v = sha.digest(v);
        return Ed25519.decode(v).encode();
    }

    @Test
    public void spake2AgreesOnlyWithTheSameCode() throws Exception {
        byte[] alice = run("123456", "123456");
        assertEquals(64, alice.length);
        byte[][] pair = runBoth("123456", "123456");
        assertArrayEquals(pair[0], pair[1]);
        byte[][] wrong = runBoth("123456", "654321");
        assertFalse(Arrays.equals(wrong[0], wrong[1]));
    }

    private static byte[] run(String a, String b) throws Exception {
        return runBoth(a, b)[0];
    }

    private static byte[][] runBoth(String codeA, String codeB) throws Exception {
        SecureRandom r = new SecureRandom();
        Spake2 a = new Spake2(Spake2.Role.ALICE, AdbPairing.CLIENT_NAME, AdbPairing.SERVER_NAME);
        Spake2 b = new Spake2(Spake2.Role.BOB, AdbPairing.SERVER_NAME, AdbPairing.CLIENT_NAME);
        byte[] ma = a.generateMsg(codeA.getBytes(StandardCharsets.US_ASCII), r);
        byte[] mb = b.generateMsg(codeB.getBytes(StandardCharsets.US_ASCII), r);
        return new byte[][]{a.processMsg(mb), b.processMsg(ma)};
    }

    @Test
    public void hkdfMatchesRfc5869() throws Exception {
        // RFC 5869 test case 3: no salt, no info.
        byte[] ikm = new byte[22];
        Arrays.fill(ikm, (byte) 0x0b);
        assertArrayEquals(Ed25519.hex("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8"),
                AdbPairing.hkdf(ikm, new byte[0], 42));
    }

    @Test
    public void gcmNonceIsALittleEndianCounter() {
        assertArrayEquals(new byte[12], AdbPairing.PairingCipher.nonce(0));
        byte[] n = AdbPairing.PairingCipher.nonce(258);
        assertEquals(2, n[0]);
        assertEquals(1, n[1]);
    }

    @Test
    public void certificateCarriesTheAdbKey() throws Exception {
        AdbKey key = AdbKey.generate();
        X509Certificate c = AdbTls.certificate(key);
        c.verify(key.publicKey());
        c.checkValidity();
        assertEquals(key.publicKey().getModulus(), ((RSAPublicKey) c.getPublicKey()).getModulus());
    }

    @Test
    public void pairsThenConnectsOverTls() throws Exception {
        try (FakeWirelessAdbd unit = new FakeWirelessAdbd("482913", FIXED, cmd -> "ran " + cmd + "\n")) {
            AdbKey key = AdbKey.generate();
            String guid = AdbPairing.pair("127.0.0.1", unit.pairingPort(), "482 913", key, "CleanYourHeadUnit@test", FIXED, 10000);
            assertEquals("adb-FAKEUNIT-cyhu", guid);
            assertEquals("CleanYourHeadUnit@test", unit.lastPairedName);
            TcpTransport t = new TcpTransport("127.0.0.1", unit.adbPort(), 10000).withTls(AdbTls.context(key));
            try (AdbConnection c = AdbConnection.connect(t, key, "x", null)) {
                assertEquals("Wireless debugging", c.kind());
                assertTrue(c.banner().contains("FakeUnit"));
                assertEquals("ran getprop ro.product.model", c.service("shell:getprop ro.product.model").trim());
            }
        }
    }

    @Test
    public void wrongCodeStoresNothing() throws Exception {
        try (FakeWirelessAdbd unit = new FakeWirelessAdbd("482913", FIXED, cmd -> "")) {
            AdbKey key = AdbKey.generate();
            try {
                AdbPairing.pair("127.0.0.1", unit.pairingPort(), "111111", key, "x", FIXED, 10000);
                fail("paired with a wrong code");
            } catch (IOException expected) {
                assertNotNull(expected.getMessage());
            }
            assertTrue(unit.paired.isEmpty());
            // And an unpaired key can't connect.
            TcpTransport t = new TcpTransport("127.0.0.1", unit.adbPort(), 10000).withTls(AdbTls.context(key));
            try {
                AdbConnection.connect(t, key, "x", null).close();
                fail("an unpaired key connected");
            } catch (IOException expected) {
                // refused during or right after the TLS handshake
            }
        }
    }

    @Test
    public void pairingCodeMustBeSixDigits() throws Exception {
        try {
            AdbPairing.pair("127.0.0.1", 1, "12345", AdbKey.generate(), "x", FIXED, 1000);
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("six digits"));
        }
    }

    @Test
    public void withoutPairingStlsGivesAClearMessage() throws Exception {
        try (FakeWirelessAdbd unit = new FakeWirelessAdbd("482913", FIXED, cmd -> "")) {
            AdbKey key = AdbKey.generate();
            try {
                AdbConnection.connect(new TcpTransport("127.0.0.1", unit.adbPort(), 10000), key, "x", null).close();
                fail();
            } catch (IOException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("pairing"));
            }
        }
    }
}
