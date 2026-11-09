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

    /** Off by default: look for a new CleanYourHeadUnit release when the app opens (at most daily). Never downloads. */
    public static boolean autoCheckUpdates(Context c) {
        return sp(c).getBoolean("autoCheckUpdates", false);
    }

    public static void setAutoCheckUpdates(Context c, boolean on) {
        SharedPreferences.Editor e = sp(c).edit().putBoolean("autoCheckUpdates", on);
        if (!on) e.remove("updateAvailable").remove("lastUpdateCheck");
        e.apply();
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
