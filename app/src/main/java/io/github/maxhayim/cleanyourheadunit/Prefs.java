package io.github.maxhayim.cleanyourheadunit;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Every setting the app keeps. All local; nothing leaves the unit. */
public final class Prefs {
    private Prefs() {}

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("cyhu", Context.MODE_PRIVATE);
    }

    public static Set<String> declaredFirmware(Context c) {
        return new HashSet<>(sp(c).getStringSet("declared", new HashSet<String>()));
    }

    public static void declareFirmware(Context c, String id, boolean on) {
        Set<String> s = declaredFirmware(c);
        if (on) s.add(id);
        else s.remove(id);
        sp(c).edit().putStringSet("declared", s).apply();
    }

    public static String privacyProfile(Context c) {
        return sp(c).getString("privacy", "standard");
    }

    public static void setPrivacyProfile(Context c, String p) {
        sp(c).edit().putString("privacy", p).apply();
    }

    public static String theme(Context c) {
        return sp(c).getString("theme", "midnight");
    }

    public static void setTheme(Context c, String id) {
        sp(c).edit().putString("theme", id).apply();
    }

    /** The owner's accent colour (#RRGGBB) over the theme's, or null for the theme's own. */
    public static String accent(Context c) {
        String v = sp(c).getString("accent", null);
        return v != null && Theme.COLOR.matcher(v).matches() ? v : null;
    }

    public static void setAccent(Context c, String hex) {
        if (hex == null) sp(c).edit().remove("accent").apply();
        else sp(c).edit().putString("accent", hex.toUpperCase(java.util.Locale.ROOT)).apply();
    }

    /** No animation by default on units with under 2 GB of RAM. */
    public static boolean lowMotion(Context c, long ramTotal) {
        return sp(c).getBoolean("lowMotion", ramTotal > 0 && ramTotal < (2L << 30) - (128L << 20));
    }

    public static void setLowMotion(Context c, boolean on) {
        sp(c).edit().putBoolean("lowMotion", on).apply();
    }

    /** Updates: Off (the default; only Check for updates contacts GitHub), Notify only, or Automatic. */
    public static final String UPDATES_OFF = "off", UPDATES_NOTIFY = "notify", UPDATES_AUTO = "auto";

    public static String updateMode(Context c) {
        SharedPreferences p = sp(c);
        String m = p.getString("updateMode", null);
        if (m != null) return m;
        // 0.1.1 had one switch, "Check when the app opens": On meant what Notify only does now.
        return p.getBoolean("autoCheckUpdates", false) ? UPDATES_NOTIFY : UPDATES_OFF;
    }

    public static void setUpdateMode(Context c, String mode) {
        SharedPreferences.Editor e = sp(c).edit().putString("updateMode", mode).remove("autoCheckUpdates");
        if (UPDATES_OFF.equals(mode)) e.remove("updateAvailable").remove("lastUpdateCheck").remove("updateStatus");
        e.apply();
    }

    /** What the last automatic update did ("Downloaded 0.1.3, waiting for you to confirm"), or null. */
    public static String updateStatus(Context c) {
        return sp(c).getString("updateStatus", null);
    }

    public static void setUpdateStatus(Context c, String s) {
        if (s == null) sp(c).edit().remove("updateStatus").apply();
        else sp(c).edit().putString("updateStatus", s).apply();
    }

    public static long lastUpdateCheck(Context c) {
        return sp(c).getLong("lastUpdateCheck", 0);
    }

    public static void setLastUpdateCheck(Context c, long when) {
        sp(c).edit().putLong("lastUpdateCheck", when).apply();
    }

    /** The newer version the last automatic check found, or null. */
    public static String updateAvailable(Context c) {
        return sp(c).getString("updateAvailable", null);
    }

    public static void setUpdateAvailable(Context c, String version) {
        if (version == null) sp(c).edit().remove("updateAvailable").apply();
        else sp(c).edit().putString("updateAvailable", version).apply();
    }

    public static Set<String> favourites(Context c) {
        return new HashSet<>(sp(c).getStringSet("favourites", new HashSet<String>()));
    }

    public static void setFavourite(Context c, String pkg, boolean on) {
        Set<String> s = favourites(c);
        if (on) s.add(pkg);
        else s.remove(pkg);
        sp(c).edit().putStringSet("favourites", s).apply();
    }

    public static String previousHome(Context c) {
        return sp(c).getString("previousHome", null);
    }

    public static void setPreviousHome(Context c, String flat) {
        sp(c).edit().putString("previousHome", flat).apply();
    }
}
