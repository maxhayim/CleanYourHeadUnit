package io.github.justinnewman.gameday;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Fetches a calendar at most once a day and keeps the last good copy: a fetch that fails, or that brings back
 * something that is not a calendar, leaves the cached file as it was, so Home never loses its games because
 * Wi-Fi blinked or a server had a bad hour. The link is never logged.
 */
final class Fetcher {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    static final long STALE_MS = 24 * 3_600_000L;
    static final long RETRY_MS = 3_600_000L;

    private static final Instant ALL_FROM = Instant.EPOCH;
    private static final Instant ALL_TO = Instant.parse("2200-01-01T00:00:00Z");

    private final int connectMs;
    private final int readMs;

    Fetcher() {
        this(15_000, 30_000);
    }

    Fetcher(int connectMs, int readMs) {
        this.connectMs = connectMs;
        this.readMs = readMs;
    }

    /** Fetch when the last good copy is over a day old and the last failure is over an hour old. */
    static boolean due(FetchStatus s, long nowMs) {
        if (s == null) return true;
        return nowMs - s.lastSuccessMs >= STALE_MS && nowMs - s.lastFailureMs >= RETRY_MS;
    }

    static File cacheFile(File dir, String id) {
        return new File(dir, "cal-" + id + ".ics");
    }

    FetchStatus fetch(Calendar c, File dir, FetchStatus previous, long nowMs) {
        File part = new File(dir, "cal-" + c.id + ".part");
        try {
            String error = download(c.link, part);
            if (error != null) return failed(previous, nowMs, error);
            int entries;
            try (Reader r = new InputStreamReader(Files.newInputStream(part.toPath()), StandardCharsets.UTF_8)) {
                entries = Ics.parse(r, ZoneOffset.UTC, ALL_FROM, ALL_TO).size();
            } catch (Ics.NotACalendar notCalendar) {
                return failed(previous, nowMs, notCalendar.getMessage());
            }
            Files.move(part.toPath(), cacheFile(dir, c.id).toPath(), StandardCopyOption.REPLACE_EXISTING);
            return new FetchStatus(nowMs, previous == null ? 0 : previous.lastFailureMs, null, entries);
        } catch (IOException e) {
            return failed(previous, nowMs, "No answer");
        } finally {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
        }
    }

    /** Writes the body to {@code to}; returns an error to show, or null. */
    private String download(String link, File to) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(link).openConnection();
        try {
            conn.setConnectTimeout(connectMs);
            conn.setReadTimeout(readMs);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "GameDay/1 (Android TV)");
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) return "Link refused (" + code + ")";
            long length = conn.getContentLengthLong();
            if (length > MAX_BYTES) return "Over 5 MB";
            try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(to)) {
                byte[] buf = new byte[16 * 1024];
                long total = 0;
                for (int n = in.read(buf); n >= 0; n = in.read(buf)) {
                    total += n;
                    if (total > MAX_BYTES) return "Over 5 MB";
                    out.write(buf, 0, n);
                }
            }
            return null;
        } finally {
            conn.disconnect();
        }
    }

    private static FetchStatus failed(FetchStatus previous, long nowMs, String error) {
        return previous == null
                ? new FetchStatus(0, nowMs, error, 0)
                : new FetchStatus(previous.lastSuccessMs, nowMs, error, previous.entries);
    }
}
