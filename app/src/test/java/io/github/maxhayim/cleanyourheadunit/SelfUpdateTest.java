package io.github.maxhayim.cleanyourheadunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SelfUpdateTest {
    private static String rel(String tag, boolean draft, String asset) {
        return "{\"tag_name\":\"" + tag + "\",\"draft\":" + draft + ",\"assets\":[{\"name\":\"" + asset + "\"}]}";
    }

    @Test
    public void picksNewestAppReleaseOnly() throws Exception {
        JSONArray rs = new JSONArray("[" + rel("companion-v0.9.0", false, "CleanYourHeadUnit-Companion-0.9.0.apk") + ","
                + rel("v0.2.0", true, "CleanYourHeadUnit-0.2.0.apk") + ","          // draft: skipped
                + rel("v0.1.10", false, "CleanYourHeadUnit-0.1.10.apk") + ","
                + rel("v0.1.9", false, "CleanYourHeadUnit-0.1.9.apk") + ","
                + rel("v0.3.0", false, "notes.txt") + "]");                         // no APK: skipped
        Object[] best = SelfUpdate.latest(rs);
        assertEquals("v0.1.10", best[0]);
        assertEquals("0.1.10", best[1]);
        assertNull(SelfUpdate.latest(new JSONArray("[" + rel("companion-v0.1.0", false, "CleanYourHeadUnit-Companion-0.1.0.apk") + "]")));
    }

    @Test
    public void automaticCheckIsOffByDefaultAndDaily() {
        android.content.Context c = org.robolectric.RuntimeEnvironment.getApplication();
        org.junit.Assert.assertEquals("off unless the owner turns it on", Prefs.UPDATES_OFF, Prefs.updateMode(c));
        long day = SelfUpdate.DAY, now = 10 * day;
        org.junit.Assert.assertFalse(SelfUpdate.due(false, 0, now));
        assertTrue(SelfUpdate.due(true, 0, now));
        org.junit.Assert.assertFalse("not twice a day", SelfUpdate.due(true, now - day / 2, now));
        assertTrue(SelfUpdate.due(true, now - day, now));
        assertTrue("clock went back", SelfUpdate.due(true, now + day, now));
        Prefs.setUpdateMode(c, Prefs.UPDATES_NOTIFY);
        Prefs.setUpdateAvailable(c, "99.0.0");
        org.junit.Assert.assertEquals("99.0.0", SelfUpdate.pending(c));
        Prefs.setUpdateAvailable(c, "0.0.1");
        assertNull("older than installed: no note", SelfUpdate.pending(c));
        Prefs.setUpdateAvailable(c, "99.0.0");
        Prefs.setUpdateStatus(c, "Downloading 99.0.0…");
        Prefs.setUpdateMode(c, Prefs.UPDATES_OFF);
        assertNull("turning it off clears the note", Prefs.updateAvailable(c));
        assertNull(Prefs.updateStatus(c));
    }

    @Test
    public void switchFrom011CarriesOver() {
        android.content.Context c = org.robolectric.RuntimeEnvironment.getApplication();
        // 0.1.1's single switch, On: becomes Notify only, never Automatic.
        Prefs.sp(c).edit().putBoolean("autoCheckUpdates", true).commit();
        org.junit.Assert.assertEquals(Prefs.UPDATES_NOTIFY, Prefs.updateMode(c));
        Prefs.setUpdateMode(c, Prefs.UPDATES_AUTO);
        org.junit.Assert.assertEquals(Prefs.UPDATES_AUTO, Prefs.updateMode(c));
        org.junit.Assert.assertFalse(Prefs.sp(c).contains("autoCheckUpdates"));
    }

    @Test
    public void comparesVersionsNumerically() {
        assertTrue(SelfUpdate.compare("0.1.10", "0.1.9") > 0);
        assertTrue(SelfUpdate.compare("1.0.0", "0.9.9") > 0);
        assertEquals(0, SelfUpdate.compare("0.1.0", "0.1.0"));
        assertTrue(SelfUpdate.compare("0.1.0", "0.2.0") < 0);
        assertTrue("unreadable never looks newer", SelfUpdate.compare("x", "0.0.1") < 0);
    }
}
