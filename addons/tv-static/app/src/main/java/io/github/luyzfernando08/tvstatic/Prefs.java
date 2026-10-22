package io.github.luyzfernando08.tvstatic;

import android.content.Context;
import android.content.SharedPreferences;

/** The choices made on the settings screen. */
final class Prefs {
    private Prefs() { }

    /** A box behind HDMI can take several seconds to show a picture after waking; 10 s covers that. */
    static final int[] SECONDS = {3, 5, 10};

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context ctx) {
        return prefs(ctx).getBoolean("enabled", true);
    }

    static boolean sound(Context ctx) {
        return prefs(ctx).getBoolean("sound", true);
    }

    static int seconds(Context ctx) {
        return prefs(ctx).getInt("seconds", 3);
    }

    static void setEnabled(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean("enabled", value).apply();
    }

    static void setSound(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean("sound", value).apply();
    }

    static void setSeconds(Context ctx, int value) {
        prefs(ctx).edit().putInt("seconds", value).apply();
    }
}
