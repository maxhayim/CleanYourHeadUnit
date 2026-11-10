package io.github.maxhayim.cleanyourheadunit.companion;

import io.github.maxhayim.cleanyourheadunit.Rules;
import io.github.maxhayim.cleanyourheadunit.adb.AdbConnection;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The head unit as read over ADB: build properties, packages, Home apps, screen. The same reads
 * as tools/cyhu/device.py. Nothing here changes the unit.
 */
public final class HeadUnit {

    /** Runs one shell command on the unit. AdbConnection in the app, a fake in tests. */
    public interface Shell {
        AdbConnection.Result run(String command) throws IOException;
    }

    public static final class Pkg {
        public final String name;
        public final boolean system;
        public boolean enabled;

        Pkg(String name, boolean system, boolean enabled) {
            this.name = name;
            this.system = system;
            this.enabled = enabled;
        }
    }

    private static final Pattern PROP_LINE = Pattern.compile("^\\[([^\\]]+)\\]: \\[(.*)\\]$");
    private static final Pattern COMPONENT = Pattern.compile("^[\\w.]+/[\\w.$]+$");

    public final Map<String, String> props = new LinkedHashMap<>();
    public final Map<String, Pkg> packages = new LinkedHashMap<>();
    public int sdk;
    public String home;
    public final List<String> homeCandidates = new ArrayList<>();
    public String size, density;
    public long memTotalKb;
    public final List<JSONObject> profiles = new ArrayList<>();
    public final Set<String> declared = new LinkedHashSet<>();

    public static Map<String, String> parseGetprop(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            Matcher m = PROP_LINE.matcher(line.trim());
            if (m.matches()) out.put(m.group(1), m.group(2));
        }
        return out;
    }

    public static List<String> parsePackageList(String text) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("\n")) {
            line = line.trim();
            if (!line.startsWith("package:")) continue;
            String name = line.substring("package:".length());
            int eq = name.lastIndexOf('=');
            if (eq >= 0) name = name.substring(eq + 1);       // pm list packages -f: path=name
            if (!name.isEmpty()) out.add(name);
        }
        return out;
    }

    private static String out(Shell sh, String cmd) throws IOException {
        AdbConnection.Result r = sh.run(cmd);
        return r.rc == 0 ? r.out : "";
    }

    /** Reads the unit. allProfiles comes from data/profiles; declared are firmware the owner said it runs. */
    public static HeadUnit identify(Shell sh, List<JSONObject> allProfiles, Set<String> declared) throws IOException {
        HeadUnit u = new HeadUnit();
        u.props.putAll(parseGetprop(out(sh, "getprop")));
        try {
            u.sdk = Integer.parseInt(u.props.get("ro.build.version.sdk"));
        } catch (RuntimeException e) {
            u.sdk = 0;
        }
        List<String> all = parsePackageList(out(sh, "pm list packages"));
        if (all.isEmpty()) throw new IOException("The unit didn't list its packages.");
        Set<String> system = new LinkedHashSet<>(parsePackageList(out(sh, "pm list packages -s")));
        Set<String> disabled = new LinkedHashSet<>(parsePackageList(out(sh, "pm list packages -d")));
        for (String n : all) u.packages.put(n, new Pkg(n, system.contains(n), !disabled.contains(n)));
        if (u.sdk >= 24) {
            String h = firstComponent(out(sh, "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"));
            u.home = h != null && !h.startsWith("android/") ? h : null;      // "android/..." is the chooser
            for (String line : out(sh, "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME").split("\n")) {
                if (COMPONENT.matcher(line.trim()).matches()) u.homeCandidates.add(line.trim());
            }
        }
        List<String> sizes = all(Pattern.compile("(\\d+x\\d+)"), out(sh, "wm size"));
        u.size = sizes.isEmpty() ? null : sizes.get(sizes.size() - 1);       // an override is listed last
        List<String> dens = all(Pattern.compile("density: (\\d+)"), out(sh, "wm density"));
        u.density = dens.isEmpty() ? null : dens.get(dens.size() - 1);
        Matcher mm = Pattern.compile("(?m)^MemTotal:\\s+(\\d+) kB").matcher(out(sh, "cat /proc/meminfo"));
        if (mm.find()) u.memTotalKb = Long.parseLong(mm.group(1));
        if (declared != null) u.declared.addAll(declared);
        u.matchProfiles(allProfiles);
        return u;
    }

    public void matchProfiles(List<JSONObject> allProfiles) {
        profiles.clear();
        JSONObject generic = null;
        for (JSONObject p : allProfiles) {
            if (!Rules.matches(p, props, packages.keySet(), declared)) continue;
            if ("generic-android".equals(p.optString("id"))) generic = p;
            else profiles.add(p);
        }
        if (generic != null) profiles.add(generic);
    }

    static String firstComponent(String text) {
        for (String line : text.split("\n")) {
            String s = line.trim();
            if (COMPONENT.matcher(s).matches()) return s;
        }
        return null;
    }

    private static List<String> all(Pattern p, String text) {
        List<String> out = new ArrayList<>();
        Matcher m = p.matcher(text);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** The current Home app and every app that can be Home: protected, same wording as the app and cyhu. */
    public Map<String, String> extraProtected() {
        Map<String, String> extra = new LinkedHashMap<>();
        for (String c : homeCandidates) extra.put(c.split("/")[0], "Can be the Home app on this unit. Home apps are never disabled.");
        if (home != null) extra.put(home.split("/")[0], "The current Home app. It is never disabled.");
        return extra;
    }

    public Rules rules(JSONObject protectedJson, JSONObject telemetryJson) throws JSONException {
        return new Rules(protectedJson, telemetryJson, profiles, extraProtected());
    }

    // --- what to show -------------------------------------------------------------------------

    public String model() {
        String m = props.get("ro.product.model");
        return m == null || m.isEmpty() ? "Head unit" : m;
    }

    /** The Android version from the API level, which a unit can't fake without breaking apps. */
    public String realAndroid() {
        return io.github.maxhayim.cleanyourheadunit.AndroidVersion.real(sdk, props.get("ro.build.version.release"));
    }

    /** What the unit's settings claim. Cheap units often show a bigger number than they run. */
    public String claimedAndroid() {
        return props.getOrDefault("ro.build.version.release", "");
    }

    /** True when the claimed version and the real one disagree (e.g. "13" on API 27, which is 8.1). */
    public boolean androidVersionSpoofed() {
        return io.github.maxhayim.cleanyourheadunit.AndroidVersion.spoofed(sdk, claimedAndroid());
    }

    public String chipset() {
        for (String k : new String[]{"ro.board.platform", "ro.hardware", "ro.boot.hardware", "ro.product.board"}) {
            String v = props.get(k);
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    public String firmwareFamily() {
        for (JSONObject p : profiles) if (!"generic-android".equals(p.optString("id"))) return p.optString("firmware_family", "unknown");
        return "unknown";
    }

    public int disabledCount() {
        int n = 0;
        for (Pkg p : packages.values()) if (!p.enabled) n++;
        return n;
    }

    /** A stable local name for this unit's records. Hashed, so the serial is never written out. Same as cyhu. */
    public String key() {
        StringBuilder raw = new StringBuilder();
        String[] keys = {"ro.serialno", "ro.boot.serialno", "ro.product.model", "ro.product.device"};
        for (int i = 0; i < keys.length; i++) raw.append(i == 0 ? "" : "|").append(props.getOrDefault(keys[i], ""));
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", d[i] & 0xff));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The baseline summary, same fields as cyhu's DeviceInfo.summary (no serial). */
    public JSONObject summary() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("manufacturer", props.get("ro.product.manufacturer"));
        o.put("brand", props.get("ro.product.brand"));
        o.put("model", props.get("ro.product.model"));
        o.put("device", props.get("ro.product.device"));
        o.put("android", realAndroid());
        o.put("android_claimed", claimedAndroid());
        o.put("sdk", sdk);
        o.put("build", props.get("ro.build.display.id"));
        o.put("chipset", chipset());
        o.put("firmware_family", firmwareFamily());
        org.json.JSONArray ids = new org.json.JSONArray();
        for (JSONObject p : profiles) ids.put(p.optString("id"));
        o.put("profiles", ids);
        o.put("screen", size);
        o.put("density", density);
        o.put("ram_total_mb", memTotalKb > 0 ? memTotalKb / 1024 : JSONObject.NULL);
        o.put("home", home);
        o.put("packages", packages.size());
        o.put("disabled", disabledCount());
        return o;
    }
}
