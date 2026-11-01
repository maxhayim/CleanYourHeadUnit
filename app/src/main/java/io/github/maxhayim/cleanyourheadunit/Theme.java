package io.github.maxhayim.cleanyourheadunit;

import android.content.Context;
import android.graphics.Color;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A theme (themes/<id>/theme.json): colours and a few layout choices. Data only: no scripts, no
 * code, nothing that runs. Validated with the same rules as tools/cyhu/catalog.py check_theme.
 */
public final class Theme {
    public final String id, name, author, dock;
    public final boolean dark;
    public final int bg, surface, text, dim, accent, warn, ok;
    public final int colsLandscape, colsPortrait;

    static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9-]{1,40}$");
    static final String[] KEYS = {"schema", "id", "name", "author", "license", "target", "dark", "colors", "layout", "wallpaper", "description"};
    static final String[] COLORS = {"background", "surface", "text", "text_dim", "accent", "warn", "ok"};
    static final String[] BUILTIN = {"midnight", "amber-dash", "daylight"};

    private static Theme current;

    Theme(JSONObject j) {
        id = j.optString("id");
        name = j.optString("name");
        JSONObject a = j.optJSONObject("author");
        author = a == null ? "" : a.optString("github");
        dark = j.optBoolean("dark", true);
        JSONObject c = j.optJSONObject("colors");
        bg = Color.parseColor(c.optString("background"));
        surface = Color.parseColor(c.optString("surface"));
        text = Color.parseColor(c.optString("text"));
        dim = Color.parseColor(c.optString("text_dim"));
        accent = Color.parseColor(c.optString("accent"));
        warn = Color.parseColor(c.optString("warn"));
        ok = Color.parseColor(c.optString("ok"));
        JSONObject l = j.optJSONObject("layout");
        dock = l == null ? "bottom" : l.optString("dock", "bottom");
        colsLandscape = l == null ? 6 : l.optInt("columns_landscape", 6);
        colsPortrait = l == null ? 4 : l.optInt("columns_portrait", 4);
    }

    public static List<String> validate(JSONObject t) {
        List<String> e = new ArrayList<>();
        Iterator<String> keys = t.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            boolean known = false;
            for (String x : KEYS) known |= x.equals(k);
            if (!known) e.add("unknown key " + k + " (themes are data only)");
        }
        if (t.optInt("schema") != 1) e.add("schema must be 1");
        if (!ID.matcher(t.optString("id")).matches()) e.add("bad id");
        if (t.optString("license").isEmpty()) e.add("license is required");
        JSONObject target = t.optJSONObject("target");
        if (target == null || target.optString("launcher").isEmpty() || target.optString("min_version").isEmpty()) e.add("target launcher and version are required");
        JSONObject c = t.optJSONObject("colors");
        if (c == null || c.length() != COLORS.length) e.add("colors must have exactly the seven keys");
        else for (String k : COLORS) if (!COLOR.matcher(c.optString(k)).matches()) e.add("colors." + k + " must be #RRGGBB");
        JSONObject l = t.optJSONObject("layout");
        if (l != null) {
            String dock = l.optString("dock", "bottom");
            if (!dock.equals("bottom") && !dock.equals("left")) e.add("layout.dock must be bottom or left");
            String icon = l.optString("icon_size", "large");
            if (!icon.equals("small") && !icon.equals("medium") && !icon.equals("large")) e.add("layout.icon_size must be small, medium or large");
            for (String k : new String[]{"columns_landscape", "columns_portrait"}) {
                if (l.has(k) && !(l.opt(k) instanceof Integer && l.optInt(k) >= 2 && l.optInt(k) <= 10)) e.add("layout." + k + " must be 2 to 10");
            }
            if (l.has("clock") && !(l.opt("clock") instanceof Boolean)) e.add("layout.clock must be true or false");
        }
        return e;
    }

    public static Theme load(Context c, String id) {
        try {
            JSONObject j = new JSONObject(Data.read(c, "themes/" + id + "/theme.json"));
            if (validate(j).isEmpty()) return new Theme(j);
        } catch (Exception ignored) {
        }
        try {
            JSONObject j = imported(c, id);
            if (j != null && validate(j).isEmpty()) return new Theme(j);
        } catch (Exception ignored) {
        }
        return null;
    }

    public static synchronized Theme get(Context c) {
        if (current == null || !current.id.equals(Prefs.theme(c))) {
            current = load(c, Prefs.theme(c));
            if (current == null) current = load(c, "midnight");
        }
        return current;
    }

    public static synchronized void reset() {
        current = null;
    }

    // --- imported themes (from a file the owner picked) -----------------------------------------
    static java.io.File dir(Context c) {
        java.io.File d = new java.io.File(c.getFilesDir(), "themes");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    static JSONObject imported(Context c, String id) throws Exception {
        java.io.File f = new java.io.File(dir(c), id + ".json");
        if (!f.exists()) return null;
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        byte[] b = new byte[(int) f.length()];
        int off = 0, n;
        while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
        in.close();
        return new JSONObject(new String(b, "UTF-8"));
    }

    /** Saves a validated theme JSON; returns its id or throws with the reasons. */
    public static String importTheme(Context c, String json) throws JSONException, java.io.IOException {
        if (json.length() > 64 * 1024) throw new JSONException("a theme file is at most 64 KB");
        JSONObject j = new JSONObject(json);
        List<String> e = validate(j);
        if (!"cyhu-home".equals(j.getJSONObject("target").optString("launcher"))) e.add("this theme is for " + j.getJSONObject("target").optString("launcher") + ", not CleanYourHeadUnit Home");
        if (!e.isEmpty()) throw new JSONException(android.text.TextUtils.join("; ", e));
        j.remove("wallpaper");
        java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(dir(c), j.getString("id") + ".json"));
        out.write(j.toString(2).getBytes("UTF-8"));
        out.close();
        return j.getString("id");
    }

    public static List<String> all(Context c) {
        List<String> out = new ArrayList<>();
        for (String b : BUILTIN) out.add(b);
        String[] files = dir(c).list();
        if (files != null) for (String f : files) if (f.endsWith(".json")) {
            String id = f.substring(0, f.length() - 5);
            if (!out.contains(id)) out.add(id);
        }
        return out;
    }

    public String export(Context c) throws Exception {
        for (String b : BUILTIN) if (b.equals(id)) return Data.read(c, "themes/" + id + "/theme.json");
        JSONObject j = imported(c, id);
        return j == null ? null : j.toString(2);
    }
}
