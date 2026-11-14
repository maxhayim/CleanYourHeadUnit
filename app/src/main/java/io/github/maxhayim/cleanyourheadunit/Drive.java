package io.github.maxhayim.cleanyourheadunit;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;

import java.util.Locale;

/**
 * Features that make an older head unit nicer to live with, without ADB or root: speed-based
 * volume, startup actions, day/night brightness, the phone-hotspot helper. Settings live here;
 * DriveService and DriveReceiver do the work; DriveScreen shows them.
 */
final class Drive {
    private Drive() {}

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("drive", Context.MODE_PRIVATE);
    }

    // --- 1. speed-based volume ---------------------------------------------------------------------

    static boolean speedVolume(Context c) { return sp(c).getBoolean("speedVolume", false); }
    static int speedStartKmh(Context c) { return sp(c).getInt("speedStart", 40); }
    static int speedStepKmh(Context c) { return sp(c).getInt("speedStep", 20); }
    static int speedMaxSteps(Context c) { return sp(c).getInt("speedMax", 4); }
    static boolean metric(Context c) { return sp(c).getBoolean("metric", !"US".equals(Locale.getDefault().getCountry())); }

    /**
     * How many volume steps to add at this speed: none below the start speed, one more for each
     * step of speed after it, up to the limit. Going down, it waits 5 km/h below each threshold,
     * so the volume doesn't flick up and down at a steady speed.
     */
    static int boost(float kmh, int startKmh, int stepKmh, int maxSteps, int current) {
        if (stepKmh <= 0 || maxSteps <= 0) return 0;
        int up = kmh < startKmh ? 0 : Math.min(maxSteps, 1 + (int) ((kmh - startKmh) / stepKmh));
        if (up >= current) return up;
        float down = kmh + 5;     // only drop once clearly below the step's speed
        int held = down < startKmh ? 0 : Math.min(maxSteps, 1 + (int) ((down - startKmh) / stepKmh));
        return Math.max(up, Math.min(current, held));
    }

    // --- 2. startup actions -----------------------------------------------------------------------

    static String startApp(Context c) { return sp(c).getString("startApp", null); }
    static int startVolume(Context c) { return sp(c).getInt("startVolume", -1); }      // -1: leave it
    static boolean startPlay(Context c) { return sp(c).getBoolean("startPlay", false); }
    static int startDelay(Context c) { return sp(c).getInt("startDelay", 10); }
    static boolean startOnWake(Context c) { return sp(c).getBoolean("startOnWake", false); }

    static boolean startupOn(Context c) {
        return startApp(c) != null || startVolume(c) >= 0 || startPlay(c);
    }

    // --- 3. day/night brightness ------------------------------------------------------------------

    static boolean sunBrightness(Context c) { return sp(c).getBoolean("sunBrightness", false); }
    static int dayBrightness(Context c) { return sp(c).getInt("dayBrightness", 100); }      // percent
    static int nightBrightness(Context c) { return sp(c).getInt("nightBrightness", 30); }

    /** Android's 0–255 brightness from a percentage (never fully dark). */
    static int brightnessValue(int percent) {
        return Math.max(10, Math.min(255, Math.round(percent * 255f / 100)));
    }

    /** Settings → Modify system settings: granted at install before Android 6. */
    static Boolean canWriteForTests;

    static boolean canWriteSettings(Context c) {
        if (canWriteForTests != null) return canWriteForTests;
        return Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(c);
    }

    /** Last position: the app's own last fix, or what Android last had. Null if none yet. */
    @android.annotation.SuppressLint("MissingPermission")    // checked by hasLocation just below
    static double[] position(Context c) {
        SharedPreferences s = sp(c);
        if (s.contains("lat")) return new double[]{Double.longBitsToDouble(s.getLong("lat", 0)), Double.longBitsToDouble(s.getLong("lon", 0))};
        if (!hasLocation(c)) return null;
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return null;
        Location best = null;
        for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (RuntimeException ignored) {
            }
        }
        if (best == null) return null;
        remember(c, best);
        return new double[]{best.getLatitude(), best.getLongitude()};
    }

    /** Kept roughly (two decimals, about a kilometre): enough for sunrise, and nothing more. */
    static void remember(Context c, Location l) {
        double lat = Math.round(l.getLatitude() * 100) / 100.0, lon = Math.round(l.getLongitude() * 100) / 100.0;
        sp(c).edit().putLong("lat", Double.doubleToLongBits(lat)).putLong("lon", Double.doubleToLongBits(lon)).apply();
    }

    static boolean hasLocation(Context c) {
        return Build.VERSION.SDK_INT < 23 || c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean hasFineLocation(Context c) {
        return Build.VERSION.SDK_INT < 23 || c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Sets brightness for day or night now. Returns what it did, or why it couldn't. */
    static String applyBrightness(Context c) {
        if (!sunBrightness(c)) return "off";
        if (!canWriteSettings(c)) return "needs permission to modify system settings";
        double[] pos = position(c);
        if (pos == null) return "waiting for a location fix";
        boolean day = Sun.isDay(pos[0], pos[1], System.currentTimeMillis(), java.util.TimeZone.getDefault());
        try {
            Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, brightnessValue(day ? dayBrightness(c) : nightBrightness(c)));
        } catch (SecurityException e) {
            return "Android refused: " + e.getMessage();
        }
        return day ? "day" : "night";
    }

    // --- 4. phone hotspot helper ------------------------------------------------------------------

    static String hotspot(Context c) { return sp(c).getString("hotspot", null); }
    static boolean wifiHelper(Context c) { return hotspot(c) != null; }

    /** WifiConfiguration/WifiInfo SSIDs come quoted ("MyPhone"); hidden ones as <unknown ssid>. */
    static String ssid(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        return s.isEmpty() || "<unknown ssid>".equals(s) ? null : s;
    }

    /** Apps may switch Wi-Fi on and pick a saved network themselves only up to Android 9. */
    static boolean canPickWifi() {
        return Build.VERSION.SDK_INT <= 28;
    }

    // --- the background service --------------------------------------------------------------------

    /** The service runs only for the parts that must keep watching. */
    static boolean needsService(Context c) {
        return speedVolume(c) || wifiHelper(c) || (startupOn(c) && startOnWake(c));
    }

    static void sync(Context c) {
        if (needsService(c)) DriveService.start(c);
        else DriveService.stop(c);
        DriveReceiver.scheduleBrightness(c);
    }

    static int musicVolume(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        return am == null ? 0 : am.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    static int musicMax(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        return am == null ? 15 : am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    @TargetApi(26)
    static void channel(Context c, String id, String name, int importance) {
        android.app.NotificationManager nm = (android.app.NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.createNotificationChannel(new android.app.NotificationChannel(id, name, importance));
    }
}
