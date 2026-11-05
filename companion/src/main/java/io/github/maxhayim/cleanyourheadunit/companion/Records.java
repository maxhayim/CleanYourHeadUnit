package io.github.maxhayim.cleanyourheadunit.companion;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Backups and the change record, kept on the phone, one folder per head unit. The same files
 * and fields as tools/cyhu/records.py, so either tool can restore what the other changed:
 *
 *   units/<unit key>/baseline.json   the unit as first seen; written once, never overwritten
 *   units/<unit key>/changes.json    every change: before, after, status
 *   units/<unit key>/log.jsonl       one line per action, including refusals and failures
 */
public final class Records {
    private final File dir;

    public Records(File root, String unitKey) throws IOException {
        dir = new File(root, unitKey);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Can't create " + dir);
    }

    public File dir() {
        return dir;
    }

    static String now() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT).format(new Date());
    }

    private String read(String name) throws IOException {
        File f = new File(dir, name);
        if (!f.exists()) return null;
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int off = 0, n;
            while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) off += n;
            return new String(buf, StandardCharsets.UTF_8);
        }
    }

    private void write(String name, String text) throws IOException {
        File tmp = new File(dir, name + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(new File(dir, name))) throw new IOException("Couldn't save " + name);
    }

    // --- baseline ------------------------------------------------------------------------------

    public JSONObject baseline() throws IOException, JSONException {
        String s = read("baseline.json");
        return s == null ? null : new JSONObject(s);
    }

    /** Writes the baseline unless one exists. True when it was written now. */
    public boolean saveBaseline(HeadUnit u) throws IOException, JSONException {
        if (baseline() != null) return false;
        JSONObject b = new JSONObject();
        b.put("time", now());
        b.put("summary", u.summary());
        JSONObject pk = new JSONObject();
        for (Map.Entry<String, HeadUnit.Pkg> e : new java.util.TreeMap<>(u.packages).entrySet()) {
            pk.put(e.getKey(), new JSONObject().put("system", e.getValue().system).put("enabled", e.getValue().enabled));
        }
        b.put("packages", pk);
        b.put("home", u.home == null ? JSONObject.NULL : u.home);
        b.put("private_dns", JSONObject.NULL);
        b.put("tool", "companion");
        write("baseline.json", b.toString(2) + "\n");
        log("baseline", new JSONObject().put("packages", u.packages.size()));
        return true;
    }

    // --- changes -------------------------------------------------------------------------------

    public JSONArray changes() throws IOException, JSONException {
        String s = read("changes.json");
        return s == null ? new JSONArray() : new JSONArray(s);
    }

    public void addChange(String kind, String target, String before, String after, String note) throws IOException, JSONException {
        JSONArray ch = changes();
        ch.put(new JSONObject().put("time", now()).put("kind", kind).put("target", target).put("before", before)
                .put("after", after).put("status", "applied").put("note", note == null ? "" : note));
        write("changes.json", ch.toString(2) + "\n");
    }

    public void mark(int index, String status, String note) throws IOException, JSONException {
        JSONArray ch = changes();
        JSONObject c = ch.getJSONObject(index);
        c.put("status", status).put("status_time", now());
        if (note != null && !note.isEmpty()) c.put("note", note);
        write("changes.json", ch.toString(2) + "\n");
    }

    public void log(String action, JSONObject detail) throws IOException, JSONException {
        JSONObject line = new JSONObject().put("time", now()).put("action", action);
        Iterator<String> k = detail.keys();
        while (k.hasNext()) {
            String key = k.next();
            line.put(key, detail.get(key));
        }
        try (OutputStream out = new FileOutputStream(new File(dir, "log.jsonl"), true)) {
            out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }
}
