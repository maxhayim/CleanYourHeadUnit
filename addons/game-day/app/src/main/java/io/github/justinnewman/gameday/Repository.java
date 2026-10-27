package io.github.justinnewman.gameday;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Everything Game Day keeps, in the app's own files (never backed up): the calendar list with its links,
 * each calendar's last good copy and fetch status, and the seconds per turn.
 */
final class Repository {
    static final int TURN_DEFAULT = 30;
    static final int TURN_MIN = 10;
    static final int TURN_MAX = 120;
    static final String NOTHING = "nothing in the next 7 days";

    private static final Duration READ_AHEAD = Duration.ofDays(8);
    private static final DateTimeFormatter NEXT = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.US);

    private final File dir;

    Repository(Context ctx) {
        this(ctx.getFilesDir());
    }

    Repository(File dir) {
        this.dir = dir;
    }

    File dir() {
        return dir;
    }

    List<Calendar> calendars() {
        String json = read(new File(dir, "calendars.json"));
        return json == null ? new ArrayList<>() : CalendarStore.fromJson(json);
    }

    /** Saves the list and forgets the copies and statuses of calendars no longer on it. */
    void save(List<Calendar> calendars) {
        write(new File(dir, "calendars.json"), CalendarStore.toJson(calendars));
        Set<String> keep = new HashSet<>();
        for (Calendar c : calendars) keep.add(c.id);
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String n = f.getName();
            String id = n.startsWith("cal-") && n.endsWith(".ics") ? n.substring(4, n.length() - 4)
                    : n.startsWith("status-") && n.endsWith(".json") ? n.substring(7, n.length() - 5) : null;
            //noinspection ResultOfMethodCallIgnored
            if (id != null && !keep.contains(id)) f.delete();
        }
    }

    int turnSeconds() {
        String json = read(new File(dir, "settings.json"));
        if (json == null) return TURN_DEFAULT;
        try {
            return clampTurn(new JSONObject(json).optInt("turn_seconds", TURN_DEFAULT));
        } catch (JSONException unreadable) {
            return TURN_DEFAULT;
        }
    }

    /** 10 to 120 seconds, in steps of 5. */
    void setTurnSeconds(int seconds) {
        try {
            write(new File(dir, "settings.json"), new JSONObject().put("turn_seconds", clampTurn(seconds)).toString());
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static int clampTurn(int s) {
        int stepped = Math.round(s / 5f) * 5;
        return Math.max(TURN_MIN, Math.min(TURN_MAX, stepped));
    }

    FetchStatus status(String id) {
        String json = read(new File(dir, "status-" + id + ".json"));
        return json == null ? null : FetchStatus.fromJson(json);
    }

    void saveStatus(String id, FetchStatus s) {
        write(new File(dir, "status-" + id + ".json"), s.toJson());
    }

    /** Calendars whose copy is over a day old and that did not fail in the last hour. */
    List<Calendar> due(long nowMs) {
        List<Calendar> out = new ArrayList<>();
        for (Calendar c : calendars()) {
            if (Fetcher.due(status(c.id), nowMs)) out.add(c);
        }
        return out;
    }

    FetchStatus fetch(Fetcher fetcher, Calendar c, long nowMs) {
        FetchStatus s = fetcher.fetch(c, dir, status(c.id), nowMs);
        saveStatus(c.id, s);
        return s;
    }

    List<Schedule.Turn> turns(long nowMs) {
        return turns(nowMs, ZoneId.systemDefault());
    }

    List<Schedule.Turn> turns(long nowMs, ZoneId zone) {
        return turns(calendars(), nowMs, zone);
    }

    /** "Sat 1:00 AM", or "nothing in the next 7 days". */
    String nextLine(Calendar c, long nowMs, ZoneId zone) {
        List<Schedule.Turn> t = turns(Collections.singletonList(c), nowMs, zone);
        return t.isEmpty() ? NOTHING : NEXT.format(Instant.ofEpochMilli(t.get(0).startMs).atZone(zone));
    }

    private List<Schedule.Turn> turns(List<Calendar> calendars, long nowMs, ZoneId zone) {
        Instant now = Instant.ofEpochMilli(nowMs);
        Map<String, List<Ics.Event>> events = new HashMap<>();
        for (Calendar c : calendars) {
            File f = Fetcher.cacheFile(dir, c.id);
            if (!f.exists()) continue;
            try (Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
                events.put(c.id, Ics.parse(r, zone, now, now.plus(READ_AHEAD)));
            } catch (IOException | Ics.NotACalendar unreadable) {
                // A copy only lands here after it parsed once; skip it if the file went bad.
            }
        }
        return Schedule.turns(calendars, events, now, zone);
    }

    /**
     * The setup script's file: replaces the list, fetches every calendar, and returns the report the script prints.
     * One line per calendar, {@code ok|name|sport|entries|next} or {@code fail|name|sport|error}; for a file it cannot
     * use, {@code error|line|message} and the list stays as it was. Always ends with {@code done}. Never a link.
     */
    String applySetupFile(String text, long nowMs, Fetcher fetcher, ZoneId zone) {
        List<Calendar> list;
        try {
            list = CalendarStore.parseSetupFile(text);
        } catch (CalendarStore.BadLine bad) {
            return "error|" + bad.line + "|" + bad.getMessage() + "\ndone\n";
        }
        save(list);
        StringBuilder b = new StringBuilder();
        for (Calendar c : list) {
            FetchStatus s = fetch(fetcher, c, nowMs);
            String head = c.name + "|" + c.sport.label() + "|";
            if (s.error == null) b.append("ok|").append(head).append(s.entries).append('|').append(nextLine(c, nowMs, zone));
            else b.append("fail|").append(head).append(s.error);
            b.append('\n');
        }
        return b.append("done\n").toString();
    }

    private static String read(File f) {
        try {
            return f.exists() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Write to a temporary file, then move it into place, so a reader never sees half a file. */
    static void write(File f, String text) {
        File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
        try {
            Files.write(tmp.toPath(), text.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("could not save " + f.getName(), e);
        }
    }
}
