package io.github.maxhayim.brightness;

import android.content.Context;
import android.content.SharedPreferences;

/** The settings in the app's own storage. Writing them is all it takes: the service listens and redraws. */
final class Prefs {
    static final String FILE = "brightness";
    static final String TAG = "Brightness";

    private Prefs() {
    }

    static SharedPreferences of(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static Dimming.Settings load(Context c) {
        SharedPreferences p = of(c);
        Dimming.Settings s = new Dimming.Settings();
        s.on = p.getBoolean("on", s.on);
        s.brightness = p.getInt("brightness", s.brightness);
        s.nightBrightness = p.getInt("night_brightness", s.nightBrightness);
        s.warmth = p.getInt("warmth", s.warmth);
        s.nightStart = p.getInt("night_start", s.nightStart);
        s.nightEnd = p.getInt("night_end", s.nightEnd);
        try {
            s.night = Dimming.Night.valueOf(p.getString("night", s.night.name()));
        } catch (IllegalArgumentException e) {
            s.night = Dimming.Night.SCHEDULED;
        }
        return s.clamped();
    }

    static void save(Context c, Dimming.Settings s) {
        s.clamped();
        of(c).edit()
                .putBoolean("on", s.on)
                .putInt("brightness", s.brightness)
                .putInt("night_brightness", s.nightBrightness)
                .putInt("warmth", s.warmth)
                .putInt("night_start", s.nightStart)
                .putInt("night_end", s.nightEnd)
                .putString("night", s.night.name())
                .apply();
    }

    /** Normal screen at once; the other settings stay for next time. */
    static void switchOff(Context c) {
        of(c).edit().putBoolean("on", false).apply();
    }
}
