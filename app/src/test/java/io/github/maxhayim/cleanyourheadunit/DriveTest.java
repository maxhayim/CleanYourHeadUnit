package io.github.maxhayim.cleanyourheadunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.provider.Settings;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Calendar;
import java.util.Map;
import java.util.TimeZone;

/** Drive features: the sun, speed steps, startup actions, brightness, the hotspot name, the slowness check, the screen. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DriveTest {
    @Before
    public void setUp() {
        MainActivity.background = Runnable::run;
        Theme.reset();
        Drive.sp(RuntimeEnvironment.getApplication()).edit().clear().commit();
        Drive.canWriteForTests = null;
        DriveReceiver.lastStartup = "";
    }

    // --- the sun ------------------------------------------------------------------------------------

    /** Local clock minutes of an epoch time in a zone. */
    private static int minutes(long t, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(t);
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    private static long noon(int y, int m, int d, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.clear();
        c.set(y, m - 1, d, 12, 0);
        return c.getTimeInMillis();
    }

    private static void near(String what, int wantMinutes, int gotMinutes) {
        assertTrue(what + ": wanted " + wantMinutes / 60 + ":" + wantMinutes % 60 + ", got " + gotMinutes / 60 + ":" + gotMinutes % 60,
                Math.abs(wantMinutes - gotMinutes) <= 4);
    }

    /** Published times (timeanddate.com / NOAA) for a few places and seasons, to within four minutes. */
    @Test
    public void sunriseAndSunsetMatchPublishedTimes() {
        TimeZone ny = TimeZone.getTimeZone("America/New_York"), london = TimeZone.getTimeZone("Europe/London"), sydney = TimeZone.getTimeZone("Australia/Sydney");
        long[] r = Sun.riseSet(40.71, -74.01, noon(2026, 6, 21, ny), ny);
        near("New York sunrise, June", 5 * 60 + 25, minutes(r[0], ny));
        near("New York sunset, June", 20 * 60 + 31, minutes(r[1], ny));
        r = Sun.riseSet(51.51, -0.13, noon(2026, 12, 21, london), london);
        near("London sunrise, December", 8 * 60 + 4, minutes(r[0], london));
        near("London sunset, December", 15 * 60 + 54, minutes(r[1], london));
        r = Sun.riseSet(-33.87, 151.21, noon(2026, 1, 1, sydney), sydney);
        near("Sydney sunrise, January", 5 * 60 + 47, minutes(r[0], sydney));
        near("Sydney sunset, January", 20 * 60 + 10, minutes(r[1], sydney));
    }

    @Test
    public void polarNightHasNoSunrise() {
        TimeZone oslo = TimeZone.getTimeZone("Europe/Oslo");
        long midwinter = noon(2026, 12, 21, oslo);
        assertNull(Sun.riseSet(69.65, 18.96, midwinter, oslo));
        assertFalse(Sun.isDay(69.65, 18.96, midwinter, oslo));
        assertTrue(Sun.nextChange(69.65, 18.96, midwinter, oslo) > midwinter);
    }

    @Test
    public void dayAndNightAndTheNextChange() {
        TimeZone ny = TimeZone.getTimeZone("America/New_York");
        long n = noon(2026, 6, 21, ny);
        assertTrue(Sun.isDay(40.71, -74.01, n, ny));
        assertFalse(Sun.isDay(40.71, -74.01, n + 10 * 3_600_000L, ny));       // 10 pm
        long next = Sun.nextChange(40.71, -74.01, n, ny);
        near("next change after noon is sunset", 20 * 60 + 31, minutes(next, ny));
    }

    // --- speed-based volume -------------------------------------------------------------------------

    @Test
    public void speedStepsWithoutFlickering() {
        assertEquals(0, Drive.boost(30, 40, 20, 4, 0));
        assertEquals(1, Drive.boost(40, 40, 20, 4, 0));
        assertEquals(2, Drive.boost(65, 40, 20, 4, 1));
        assertEquals("capped", 4, Drive.boost(200, 40, 20, 4, 0));
        assertEquals("just below the step: holds", 2, Drive.boost(58, 40, 20, 4, 2));
        assertEquals("clearly below: drops", 1, Drive.boost(50, 40, 20, 4, 2));
        assertEquals("stopped", 0, Drive.boost(0, 40, 20, 4, 3));
    }

    @Test
    public void serviceTurnsTheVolumeUpWithSpeedAndKeepsTheOwnersChange() {
        Context c = RuntimeEnvironment.getApplication();
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION);
        Drive.sp(c).edit().putBoolean("speedVolume", true).commit();
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, 5, 0);
        DriveService s = Robolectric.buildService(DriveService.class).create().get();
        s.onSpeed(70);      // 40 start, 20 per step: +2
        assertEquals(7, am.getStreamVolume(AudioManager.STREAM_MUSIC));
        s.onSpeed(10);
        assertEquals("back to the owner's volume", 5, am.getStreamVolume(AudioManager.STREAM_MUSIC));
        s.onSpeed(70);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, 9, 0);    // the owner turns it up while moving
        s.onSpeed(70);
        s.onSpeed(10);
        assertTrue("the owner's change is the new base", am.getStreamVolume(AudioManager.STREAM_MUSIC) >= 9);
        s.onDestroy();
    }

    // --- startup actions ----------------------------------------------------------------------------

    @Test
    public void startupSetsVolumeAndOpensTheApp() {
        Context c = RuntimeEnvironment.getApplication();
        Drive.sp(c).edit().putInt("startVolume", 6).putString("startApp", c.getPackageName()).commit();
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, 1, 0);
        final boolean[] done = {false};
        DriveReceiver.runStartup(c, () -> done[0] = true);
        assertTrue(done[0]);
        assertEquals(6, am.getStreamVolume(AudioManager.STREAM_MUSIC));
        Intent started = shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(c.getPackageName(), started.getComponent().getPackageName());
        assertTrue(DriveReceiver.lastStartup, DriveReceiver.lastStartup.contains("Volume 6"));
    }

    @Test
    public void startupWithAMissingAppSaysSo() {
        Context c = RuntimeEnvironment.getApplication();
        Drive.sp(c).edit().putString("startApp", "com.example.gone").commit();
        DriveReceiver.runStartup(c, () -> {});
        assertTrue(DriveReceiver.lastStartup.contains("isn't installed"));
    }

    // --- brightness ---------------------------------------------------------------------------------

    @Test
    public void brightnessFollowsTheSunOnceAllowed() {
        Context c = RuntimeEnvironment.getApplication();
        Drive.sp(c).edit().putBoolean("sunBrightness", true).putInt("dayBrightness", 100).putInt("nightBrightness", 20)
                .putLong("lat", Double.doubleToLongBits(40.71)).putLong("lon", Double.doubleToLongBits(-74.01)).commit();
        Drive.canWriteForTests = false;
        assertTrue(Drive.applyBrightness(c).contains("permission"));
        Drive.canWriteForTests = true;
        String now = Drive.applyBrightness(c);
        int want = Drive.brightnessValue("day".equals(now) ? 100 : 20);
        assertEquals(want, Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, -1));
        assertEquals(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL, Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, -1));
        assertEquals(10, Drive.brightnessValue(0));
        assertEquals(255, Drive.brightnessValue(100));
    }

    @Test
    public void positionIsKeptOnlyRoughly() {
        Context c = RuntimeEnvironment.getApplication();
        android.location.Location l = new android.location.Location("gps");
        l.setLatitude(40.712776);
        l.setLongitude(-74.005974);
        Drive.remember(c, l);
        double[] p = Drive.position(c);
        assertEquals(40.71, p[0], 1e-9);
        assertEquals(-74.01, p[1], 1e-9);
    }

    // --- hotspot ------------------------------------------------------------------------------------

    @Test
    public void hotspotNamesWithoutQuotes() {
        assertEquals("Max's iPhone", Drive.ssid("\"Max's iPhone\""));
        assertEquals("plain", Drive.ssid("plain"));
        assertNull(Drive.ssid("<unknown ssid>"));
        assertNull(Drive.ssid("\"\""));
    }

    @Test
    public void serviceRunsOnlyForFeaturesThatWatch() {
        Context c = RuntimeEnvironment.getApplication();
        assertFalse(Drive.needsService(c));
        Drive.sp(c).edit().putInt("startVolume", 5).commit();
        assertFalse("startup alone needs no service", Drive.needsService(c));
        Drive.sp(c).edit().putBoolean("startOnWake", true).commit();
        assertTrue(Drive.needsService(c));
        Drive.sp(c).edit().clear().putString("hotspot", "Phone").commit();
        assertTrue(Drive.needsService(c));
    }

    // --- slowness check -----------------------------------------------------------------------------

    static final String MEMINFO = "Applications Memory Usage (in Kilobytes):\nUptime: 1 Realtime: 1\n\n"
            + "Total PSS by process:\n    210,512K: system (pid 812)\n     98,304K: com.example.radio (pid 1500 / activities)\n"
            + "     45,056K: com.android.systemui (pid 990)\n\nTotal PSS by OOM adjustment:\n    300,000K: Native\n";

    @Test
    public void readsTheBiggestAppsFromMeminfo() {
        Map<String, Long> m = Slowness.parseMeminfo(MEMINFO, 10);
        assertEquals(3, m.size());
        assertEquals(Long.valueOf(210512), m.get("system"));
        assertEquals("com.example.radio", m.keySet().toArray()[1]);
        assertEquals(2, Slowness.parseMeminfo(MEMINFO, 2).size());
    }

    @Test
    public void suggestsOnlyAppsTheRulesOfferThatStartAtPowerUp() throws Exception {
        MainActivity a = new ScreensTest().open("fyt-android10-1280x720.json", "w1280dp-h720dp-land-mdpi");
        String optional = null, prot = null;
        for (Map.Entry<String, Unit.Pkg> e : a.unit.packages.entrySet()) {
            String cat = a.unit.rules(a).classify(e.getKey(), e.getValue().system).category;
            if (optional == null && Rules.OPTIONAL.equals(cat) && e.getValue().enabled) optional = e.getKey();
            if (prot == null && Rules.PROTECTED.equals(cat) && e.getValue().enabled) prot = e.getKey();
        }
        for (String p : new String[]{optional, prot}) {
            ResolveInfo ri = new ResolveInfo();
            ri.activityInfo = new ActivityInfo();
            ri.activityInfo.packageName = p;
            ri.activityInfo.name = p + ".Boot";
            ri.activityInfo.enabled = true;
            shadowOf(a.getPackageManager()).addResolveInfoForIntent(new Intent(Intent.ACTION_BOOT_COMPLETED), ri);
        }
        Slowness.Result r = Slowness.check(a, a.unit);
        assertTrue(r.starters.contains(optional) && r.starters.contains(prot));
        assertEquals(1, r.suggest.size());
        assertEquals(optional, r.suggest.get(0).pkg);
        assertEquals(1, r.startersProtected);
        assertTrue(r.adbNote.contains("Direct changes"));
        String shown = String.join("\n", ScreensTest.texts(Slowness.view(a, r)));
        assertTrue(shown, shown.contains("Can be disabled"));
        assertTrue(Slowness.verdict(r).contains("can be disabled"));
    }

    // --- the screen ---------------------------------------------------------------------------------

    @Test
    public void driveScreenShowsAllFiveFeatures() throws Exception {
        MainActivity a = new ScreensTest().open("fyt-android10-1280x720.json", "w1280dp-h720dp-land-mdpi");
        a.show("drive");
        String t = ScreensTest.all(a);
        for (String want : new String[]{"Speed-based volume", "Startup actions", "Day and night brightness", "Phone hotspot helper", "Slowness check"}) {
            assertTrue(want, t.contains(want));
        }
        Drive.sp(a).edit().putBoolean("speedVolume", true).putBoolean("sunBrightness", true).putString("hotspot", "Phone").commit();
        a.show("drive");
        t = ScreensTest.all(a);
        assertTrue(t, t.contains("Start above"));
        assertTrue(t.contains("By day"));
        assertTrue(t.contains("Phone"));
    }
}
