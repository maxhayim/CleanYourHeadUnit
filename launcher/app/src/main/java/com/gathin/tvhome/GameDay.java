package com.gathin.tvhome;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * game_day (config.xml or a theme): each team's next game in the race panel's place, from an add-on such as
 * addons/game-day, rotating with the theme's F1 weekend. The launcher only reads what the add-on publishes and stays
 * offline. Contract: docs/modules.md.
 */
final class GameDay {

    /** One turn in the rotation: a game from the add-on, or the theme's F1 weekend ({@link #f1}). */
    static final class Turn {
        final String topLine;
        /** "Puck drop": the countdown reads "Puck drop in 2h 18m". */
        final String word;
        /** For the F1 turn: when its next session starts, or now while one is live (the sort key). */
        final long startMs;
        final long endMs;
        /** ARGB, for the short bar. */
        final int colour;
        final boolean f1;

        Turn(String topLine, String word, long startMs, long endMs, int colour) {
            this(topLine, word, startMs, endMs, colour, false);
        }

        private Turn(String topLine, String word, long startMs, long endMs, int colour, boolean f1) {
            this.topLine = topLine;
            this.word = word;
            this.startMs = startMs;
            this.endMs = endMs;
            this.colour = colour;
            this.f1 = f1;
        }
    }

    /**
     * The turns to rotate through, soonest first: the games that have not ended, and the F1 weekend as one turn
     * while the race panel would show it (sorted by its next session, or now while a session is live).
     */
    static List<Turn> merge(List<Turn> games, RaceWeekend.State f1, long nowMs) {
        List<Turn> out = new ArrayList<>();
        for (Turn t : games) {
            if (t.endMs > nowMs) out.add(t);
        }
        if (f1 != null) {
            long key = f1.live == null && f1.next != null ? f1.next.start.toEpochMilli() : nowMs;
            out.add(new Turn("", "", key, Long.MAX_VALUE, 0, true));
        }
        Collections.sort(out, Comparator.comparingLong(t -> t.startMs));
        return out;
    }

    /** Which turn is on screen, {@code sinceMs} after the rotation started. */
    static int current(int count, long sinceMs, int turnSeconds) {
        if (count <= 0) return 0;
        return (int) ((Math.max(0, sinceMs) / (turnSeconds * 1000L)) % count);
    }

    static boolean live(Turn t, long nowMs) {
        return nowMs >= t.startMs && nowMs < t.endMs;
    }

    static Duration until(Turn t, long nowMs) {
        return Duration.ofMillis(t.startMs - nowMs);
    }
}
