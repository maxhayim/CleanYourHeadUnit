package io.github.maxhayim.brightness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DimmingTest {
    private static Dimming.Settings on() {
        Dimming.Settings s = new Dimming.Settings();
        s.on = true;
        return s;
    }

    private static int alpha(int color) {
        return color >>> 24;
    }

    @Test
    public void offDrawsNothingWhateverTheRestSays() {
        Dimming.Settings s = new Dimming.Settings();
        s.night = Dimming.Night.ON;
        s.brightness = s.nightBrightness = 20;
        s.warmth = 100;
        Dimming.Level l = Dimming.level(s, 0);
        assertFalse(l.visible());
        assertEquals(0, Dimming.color(l));
        assertFalse(Dimming.needsService(s, 0));
    }

    @Test
    public void fullBrightnessAndNoWarmthIsNoLayer() {
        Dimming.Settings s = on();
        s.night = Dimming.Night.OFF;
        s.brightness = 100;
        assertFalse(Dimming.level(s, 600).visible());
        assertFalse("no layer and no schedule: the service stops", Dimming.needsService(s, 600));
    }

    @Test
    public void plainDimmingIsBlackAtTheMissingBrightness() {
        int c = Dimming.color(new Dimming.Level(70, 0, false));
        assertEquals(Math.round(0.3 * 255), alpha(c));
        assertEquals(0, c & 0xFFFFFF);
    }

    @Test
    public void warmthIsAmberAndAddsToTheDimming() {
        int dimOnly = Dimming.color(new Dimming.Level(40, 0, true));
        int warm = Dimming.color(new Dimming.Level(40, 50, true));
        assertTrue(alpha(warm) > alpha(dimOnly));
        int r = (warm >> 16) & 0xFF, g = (warm >> 8) & 0xFF, b = warm & 0xFF;
        assertTrue(r > g && g > b);
        // Warmth alone, at full brightness: amber at its full share and nothing else.
        int pure = Dimming.color(new Dimming.Level(100, 100, true));
        assertEquals(Math.round(Dimming.MAX_WARM_ALPHA * 255), alpha(pure));
        assertEquals(Dimming.WARM_RGB, pure & 0xFFFFFF);
    }

    @Test
    public void theColourMatchesTwoLayersDrawnInTurn() {
        // A grey picture under amber at the warm alpha, then black at 1 - brightness: the folded colour must agree.
        double picture = 200, warmA = Dimming.MAX_WARM_ALPHA * 0.6, blackA = 1 - 0.5;
        double red = ((1 - blackA) * ((1 - warmA) * picture + warmA * 0xFF));
        int c = Dimming.color(new Dimming.Level(50, 60, true));
        double a = alpha(c) / 255.0;
        double folded = (1 - a) * picture + a * ((c >> 16) & 0xFF);
        assertEquals(red, folded, 2.0);
    }

    @Test
    public void neverDarkerThanTheFloor() {
        Dimming.Settings s = on();
        s.brightness = 0;
        s.nightBrightness = -50;
        s.warmth = 400;
        s.clamped();
        assertEquals(Dimming.MIN_BRIGHTNESS, s.brightness);
        assertEquals(Dimming.MIN_BRIGHTNESS, s.nightBrightness);
        assertEquals(100, s.warmth);
        int black = alpha(Dimming.color(new Dimming.Level(Dimming.MIN_BRIGHTNESS, 0, false)));
        assertEquals(Math.round(0.99 * 255), black);
    }

    @Test
    public void clampingSnapsToStepsAndWrapsTimes() {
        Dimming.Settings s = on();
        s.brightness = 73;
        s.nightStart = -15;
        s.nightEnd = Dimming.DAY_MINUTES + 30;
        s.night = null;
        s.clamped();
        assertEquals(75, s.brightness);
        assertEquals(23 * 60 + 45, s.nightStart);
        assertEquals(30, s.nightEnd);
        assertEquals(Dimming.Night.SCHEDULED, s.night);
    }

    @Test
    public void slidersStepFiveThenOneBelowTen() {
        assertEquals(15, Dimming.stepBrightness(20, -1));
        assertEquals(9, Dimming.stepBrightness(10, -1));
        assertEquals(10, Dimming.stepBrightness(9, 1));
        assertEquals(15, Dimming.stepBrightness(10, 1));
        assertEquals(1, Dimming.stepBrightness(1, -1));
        assertEquals(100, Dimming.stepBrightness(100, 1));
        Dimming.Settings s = on();
        s.nightBrightness = 3;
        assertEquals("fine values survive a reload", 3, s.clamped().nightBrightness);
    }

    @Test
    public void windowPastMidnight() {
        int start = 21 * 60, end = 7 * 60;
        assertFalse(Dimming.inWindow(start, end, 20 * 60 + 59));
        assertTrue(Dimming.inWindow(start, end, 21 * 60));
        assertTrue(Dimming.inWindow(start, end, 0));
        assertTrue(Dimming.inWindow(start, end, 6 * 60 + 59));
        assertFalse(Dimming.inWindow(start, end, 7 * 60));
    }

    @Test
    public void windowWithinOneDayAndEmptyWindow() {
        assertTrue(Dimming.inWindow(13 * 60, 15 * 60, 14 * 60));
        assertFalse(Dimming.inWindow(13 * 60, 15 * 60, 15 * 60));
        assertFalse(Dimming.inWindow(13 * 60, 15 * 60, 12 * 60));
        assertFalse(Dimming.inWindow(600, 600, 600));
    }

    @Test
    public void scheduledNightUsesTheNightValues() {
        Dimming.Settings s = on();
        Dimming.Level night = Dimming.level(s, 23 * 60);
        assertTrue(night.night);
        assertEquals(s.nightBrightness, night.brightness);
        assertEquals(s.warmth, night.warmth);
        Dimming.Level day = Dimming.level(s, 12 * 60);
        assertFalse(day.night);
        assertEquals(s.brightness, day.brightness);
        assertEquals("no warmth by day", 0, day.warmth);
    }

    @Test
    public void nightModeOnAndOffIgnoreTheClock() {
        Dimming.Settings s = on();
        s.night = Dimming.Night.ON;
        assertTrue(Dimming.level(s, 12 * 60).night);
        s.night = Dimming.Night.OFF;
        assertFalse(Dimming.level(s, 23 * 60).night);
        assertEquals(-1, Dimming.minutesToNextChange(s, 0));
    }

    @Test
    public void nextChangeIsTheNearerEdgeOfTheWindow() {
        Dimming.Settings s = on();
        assertEquals(60, Dimming.minutesToNextChange(s, 20 * 60));
        assertEquals(8 * 60, Dimming.minutesToNextChange(s, 23 * 60));
        // Exactly on an edge: the next one is the other edge, never zero.
        assertEquals(10 * 60, Dimming.minutesToNextChange(s, 21 * 60));
        assertEquals(14 * 60, Dimming.minutesToNextChange(s, 7 * 60));
    }

    @Test
    public void aScheduleKeepsTheServiceEvenWithNothingToDrawYet() {
        Dimming.Settings s = on();
        s.brightness = 100;
        assertFalse(Dimming.level(s, 12 * 60).visible());
        assertTrue(Dimming.needsService(s, 12 * 60));
    }

    @Test
    public void clockFormats() {
        assertEquals("07:05", Dimming.clock(7 * 60 + 5, true));
        assertEquals("21:00", Dimming.clock(21 * 60, true));
        assertEquals("12:00 AM", Dimming.clock(0, false));
        assertEquals("12:30 PM", Dimming.clock(12 * 60 + 30, false));
        assertEquals("9:00 PM", Dimming.clock(21 * 60, false));
    }
}
