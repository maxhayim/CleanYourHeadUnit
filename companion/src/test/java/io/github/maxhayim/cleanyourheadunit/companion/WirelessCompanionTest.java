package io.github.maxhayim.cleanyourheadunit.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.robolectric.RuntimeEnvironment;

import io.github.maxhayim.cleanyourheadunit.adb.AdbConnection;
import io.github.maxhayim.cleanyourheadunit.adb.AdbPairing;
import io.github.maxhayim.cleanyourheadunit.adb.AdbTls;
import io.github.maxhayim.cleanyourheadunit.adb.FakeWirelessAdbd;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

/**
 * The phone pairs with an Android 11+ unit's Wireless debugging and then reads it over TLS, with
 * the companion's own key and Session code. The unit is a stand-in that answers like the simulated
 * Camry-type unit. Pairing's keying-material export is fixed here (Robolectric has no SSLSockets);
 * tools/check-adb-pairing.sh covers the real export against Google's adb.
 */
@RunWith(RobolectricTestRunner.class)
public class WirelessCompanionTest {
    static final AdbTls.KeyExporter FIXED = (s, label, len) -> {
        byte[] b = new byte[len];
        Arrays.fill(b, (byte) 9);
        return b;
    };

    @After
    public void tearDown() {
        Session.close();
    }

    @Test
    public void pairsThenReadsTheUnitOverWirelessDebugging() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        final CompanionTest.SimUnit sim = new CompanionTest.SimUnit();
        String marker = "; echo " + AdbConnection.RC_MARK;
        try (FakeWirelessAdbd unit = new FakeWirelessAdbd("246810", FIXED, raw -> {
            String cmd = raw.contains(marker) ? raw.substring(0, raw.indexOf(marker)) : raw;
            AdbConnection.Result r = sim.run(cmd);
            return r.out + (r.out.endsWith("\n") || r.out.isEmpty() ? "" : "\n") + AdbConnection.RC_MARK + r.rc + "\n";
        })) {
            assertEquals("adb-FAKEUNIT-cyhu", AdbPairing.pair("127.0.0.1", unit.pairingPort(), "246810", Session.key(c), Session.keyName(), FIXED, 10000));
            assertTrue(unit.lastPairedName.startsWith("CleanYourHeadUnit-Companion@"));
            Session s = Session.wifi(c, "127.0.0.1", unit.adbPort(), null);
            assertTrue("connected over TLS", s.wirelessDebugging());
            assertEquals("27", String.valueOf(s.unit.sdk));
            assertTrue(s.unit.packages.containsKey("com.example.canbus"));
        }
    }
}
