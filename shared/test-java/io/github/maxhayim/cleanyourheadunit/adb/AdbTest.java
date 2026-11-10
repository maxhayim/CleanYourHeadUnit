package io.github.maxhayim.cleanyourheadunit.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

/** The ADB client against a simulated adbd: keys, approval, shell, refusals, the bridge. Plain JVM. */
public class AdbTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private AdbKey key;

    @Before
    public void setUp() throws Exception {
        key = AdbKey.loadOrCreate(tmp.newFolder("adb"));
    }

    @Test
    public void publicKeyIsInAdbdsFormatAndMatchesTheKey() {
        BigInteger n = FakeAdbd.parseKey(key.adbPublicKey("test@phone"));   // checks len, n0inv, rr, exponent
        assertEquals(key.publicKey().getModulus(), n);
        assertTrue(new String(key.adbPublicKey("test@phone")).contains(" test@phone\0"));
    }

    @Test
    public void ownBase64MatchesJavas() {
        for (int len = 0; len < 10; len++) {
            byte[] b = new byte[len];
            for (int i = 0; i < len; i++) b[i] = (byte) (i * 37 + 200);
            assertEquals(Base64.getEncoder().encodeToString(b), AdbKey.base64(b));
        }
        byte[] k = key.androidPublicKey();
        assertEquals(Base64.getEncoder().encodeToString(k), AdbKey.base64(k));
    }

    @Test
    public void keyIsKeptBetweenRuns() throws Exception {
        File dir = tmp.newFolder("again");
        AdbKey a = AdbKey.loadOrCreate(dir), b = AdbKey.loadOrCreate(dir);
        assertEquals(a.publicKey().getModulus(), b.publicKey().getModulus());
        assertEquals(a.fingerprint(), b.fingerprint());
        assertTrue(a.fingerprint().matches("([0-9A-F]{2}:){15}[0-9A-F]{2}"));
    }

    @Test
    public void firstTimeAsksTheOwnerThenRemembersThePhone() throws Exception {
        FakeAdbd d = new FakeAdbd();
        AtomicInteger asked = new AtomicInteger();
        AdbConnection c = AdbConnection.connect(d, key, "test@phone", fp -> asked.incrementAndGet());
        assertEquals(1, asked.get());
        assertEquals(1, d.keyPrompts);
        assertTrue(c.banner().startsWith("device::"));

        // Second connection: the signature is enough, no prompt on the unit.
        FakeAdbd again = new FakeAdbd();
        again.authorized.addAll(d.authorized);
        AdbConnection.connect(again, key, "test@phone", fp -> asked.incrementAndGet());
        assertEquals(1, asked.get());
        assertEquals(0, again.keyPrompts);
    }

    @Test
    public void ownerSaysNo() throws Exception {
        FakeAdbd d = new FakeAdbd();
        d.ownerTapsAllow = false;
        try {
            AdbConnection.connect(d, key, "test@phone", null);
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("didn't allow"));
        }
    }

    @Test
    public void tlsPortIsExplainedNotHungOn() throws Exception {
        FakeAdbd d = new FakeAdbd();
        d.wantsTls = true;
        try {
            AdbConnection.connect(d, key, "test@phone", null);
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("pairing"));
        }
    }

    @Test
    public void shellReturnsOutputAndExitCode() throws Exception {
        FakeAdbd d = new FakeAdbd();
        d.shell = cmd -> cmd.startsWith("getprop ro.build.version.sdk;") ? "27\n" + AdbConnection.RC_MARK + "0\n"
                : cmd.startsWith("false;") ? AdbConnection.RC_MARK + "1\n" : "";
        AdbConnection c = AdbConnection.connect(d, key, "t", null);
        AdbConnection.Result r = c.shell("getprop ro.build.version.sdk");
        assertEquals("27", r.out);
        assertEquals(0, r.rc);
        assertEquals(1, c.shell("false").rc);
        assertEquals("shell:getprop ro.build.version.sdk; echo " + AdbConnection.RC_MARK + "$?", d.opened.get(0));
    }

    @Test
    public void longOutputArrivesWhole() throws Exception {
        FakeAdbd d = new FakeAdbd();
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 3000; i++) big.append("package:com.example.app").append(i).append('\n');
        String want = big.toString();
        d.shell = cmd -> want + AdbConnection.RC_MARK + "0\n";
        AdbConnection c = AdbConnection.connect(d, key, "t", null);
        assertEquals(want.trim(), c.shell("pm list packages").out);
    }

    @Test
    public void refusedServiceIsAnError() throws Exception {
        FakeAdbd d = new FakeAdbd();
        d.shell = cmd -> null;
        AdbConnection c = AdbConnection.connect(d, key, "t", null);
        try {
            c.shell("anything");
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("refused"));
        }
    }

    @Test
    public void bridgeAsksForTcpAndSurvivesTheRestart() throws Exception {
        FakeAdbd d = new FakeAdbd();
        AdbConnection c = AdbConnection.connect(d, key, "t", null);
        assertEquals("restarting in TCP mode port: 5555", c.tcpip(5555));
        assertEquals("tcpip:5555", d.opened.get(0));
        try {
            c.tcpip(80);
            fail();
        } catch (IllegalArgumentException expected) {
            // ports below 1024 are refused before anything is sent
        }
    }

    @Test
    public void messageHeaderRoundTrips() throws Exception {
        AdbMessage m = AdbMessage.of(AdbMessage.OPEN, 7, 0, "shell:ls");
        int[] f = AdbMessage.parseHeader(m.header());
        assertEquals(AdbMessage.OPEN, f[0]);
        assertEquals(7, f[1]);
        assertEquals(9, f[3]);
        assertEquals(AdbMessage.checksum(m.payload), f[4]);
        byte[] bad = m.header();
        bad[20] ^= 1;
        try {
            AdbMessage.parseHeader(bad);
            fail();
        } catch (IOException expected) {
            // bad magic
        }
    }
}
