package io.github.maxhayim.brightness;

/**
 * The settings and the arithmetic, with no Android types, so the JVM tests cover all of it: which level applies
 * at a given minute, the one colour the layer is drawn in, and when the schedule next changes.
 */
final class Dimming {
    /** The darkest the screen goes: 5 % of normal, so menus and this app stay faintly visible whatever is set. */
    static final int MIN_BRIGHTNESS = 5;
    static final int MAX_BRIGHTNESS = 100;
    static final int BRIGHTNESS_STEP = 5;
    static final int WARMTH_STEP = 10;
    static final int TIME_STEP = 15;
    static final int DAY_MINUTES = 24 * 60;
    /** Warmth 100 lays this amber over the picture at MAX_WARM_ALPHA: blue and green drop, red stays. */
    static final int WARM_RGB = 0xFF8A1E;
    static final float MAX_WARM_ALPHA = 0.4f;

    enum Night { OFF, ON, SCHEDULED }

    static final class Settings {
        /** The master switch. Off draws nothing, whatever the rest says. */
        boolean on;
        int brightness = 70;
        Night night = Night.SCHEDULED;
        int nightBrightness = 40;
        int warmth = 50;
        /** Minutes after midnight, local time. */
        int nightStart = 21 * 60;
        int nightEnd = 7 * 60;

        /** Every value back in range, whatever the stored copy held. */
        Settings clamped() {
            brightness = clamp(snap(brightness, BRIGHTNESS_STEP), MIN_BRIGHTNESS, MAX_BRIGHTNESS);
            nightBrightness = clamp(snap(nightBrightness, BRIGHTNESS_STEP), MIN_BRIGHTNESS, MAX_BRIGHTNESS);
            warmth = clamp(snap(warmth, WARMTH_STEP), 0, 100);
            nightStart = wrap(snap(nightStart, TIME_STEP));
            nightEnd = wrap(snap(nightEnd, TIME_STEP));
            if (night == null) night = Night.SCHEDULED;
            return this;
        }
    }

    /** What the layer shows right now. brightness 100 and warmth 0 means no layer at all. */
    static final class Level {
        final int brightness;
        final int warmth;
        final boolean night;

        Level(int brightness, int warmth, boolean night) {
            this.brightness = brightness;
            this.warmth = warmth;
            this.night = night;
        }

        boolean visible() {
            return brightness < MAX_BRIGHTNESS || warmth > 0;
        }
    }

    private Dimming() {
    }

    /** True from start up to, not including, end; a window may run past midnight. start == end is never night. */
    static boolean inWindow(int start, int end, int minute) {
        if (start == end) return false;
        return start < end ? minute >= start && minute < end : minute >= start || minute < end;
    }

    static boolean nightNow(Settings s, int minute) {
        switch (s.night) {
            case ON:
                return true;
            case SCHEDULED:
                return inWindow(s.nightStart, s.nightEnd, minute);
            default:
                return false;
        }
    }

    static Level level(Settings s, int minute) {
        if (!s.on) return new Level(MAX_BRIGHTNESS, 0, false);
        boolean night = nightNow(s, minute);
        return night ? new Level(s.nightBrightness, s.warmth, true) : new Level(s.brightness, 0, false);
    }

    /**
     * The layer's ARGB colour. Drawing it over the picture equals an amber layer at warmth's alpha with black
     * at (1 - brightness) over that, folded into one window: out = (1 - a) * picture + a * colour.
     */
    static int color(Level l) {
        double black = 1 - l.brightness / 100.0;
        double warm = MAX_WARM_ALPHA * l.warmth / 100.0;
        double alpha = 1 - (1 - black) * (1 - warm);
        if (alpha <= 0) return 0;
        double k = (1 - black) * warm / alpha;
        int r = (int) Math.round(((WARM_RGB >> 16) & 0xFF) * k);
        int g = (int) Math.round(((WARM_RGB >> 8) & 0xFF) * k);
        int b = (int) Math.round((WARM_RGB & 0xFF) * k);
        int a = (int) Math.round(alpha * 255);
        return a << 24 | r << 16 | g << 8 | b;
    }

    /**
     * Minutes from minute until the level can next change by itself: the next start or end of the night window.
     * -1 when nothing changes on its own (dimming off, or night mode not scheduled, or an empty window).
     */
    static int minutesToNextChange(Settings s, int minute) {
        if (!s.on || s.night != Night.SCHEDULED || s.nightStart == s.nightEnd) return -1;
        int toStart = wrap(s.nightStart - minute);
        int toEnd = wrap(s.nightEnd - minute);
        if (toStart == 0) toStart = DAY_MINUTES;
        if (toEnd == 0) toEnd = DAY_MINUTES;
        return Math.min(toStart, toEnd);
    }

    /** True when the service has work: a layer to hold, or a schedule to follow. */
    static boolean needsService(Settings s, int minute) {
        return s.on && (level(s, minute).visible() || minutesToNextChange(s, minute) > 0);
    }

    static String clock(int minute, boolean h24) {
        int h = minute / 60, m = minute % 60;
        if (h24) return String.format(java.util.Locale.ROOT, "%02d:%02d", h, m);
        return String.format(java.util.Locale.ROOT, "%d:%02d %s", h % 12 == 0 ? 12 : h % 12, m, h < 12 ? "AM" : "PM");
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static int wrap(int minute) {
        return ((minute % DAY_MINUTES) + DAY_MINUTES) % DAY_MINUTES;
    }

    private static int snap(int v, int step) {
        return Math.round(v / (float) step) * step;
    }
}
