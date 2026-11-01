package io.github.maxhayim.cleanyourheadunit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The debloat classifier, the same rules as tools/cyhu/classify.py, read from the same JSON
 * (data/protected.json, data/telemetry.json, data/profiles/). tests/vectors/classify.json is run
 * against both, so the app and the computer tool can't give different answers.
 *
 * Order, first match wins: core Android, Home apps found on the unit, profile-protected,
 * telemetry catalogue, profile entries, vehicle namespaces, vehicle words, the owner's own apps,
 * unknown. Recommended only comes from a profile whose status is "tested".
 */
public final class Rules {

    public static final String RECOMMENDED = "recommended", OPTIONAL = "optional", ADVANCED = "advanced",
            PROTECTED = "protected", UNKNOWN = "unknown", USER = "user";

    static int strictness(String cat) {
        switch (cat) {
            case RECOMMENDED: return 0;
            case OPTIONAL: return 1;
            case ADVANCED: return 2;
            case PROTECTED: return 4;
            default: return 3;
        }
    }

    public static String confidence(String cat) {
        switch (cat) {
            case RECOMMENDED: return "High: tested on this device profile";
            case OPTIONAL: return "Medium: known purpose";
            case ADVANCED: return "Low: needs your review";
            case PROTECTED: return "Never changed";
            case USER: return "Your own app";
            default: return "None: purpose not verified";
        }
    }

    /** One package's verdict. */
    public static final class Verdict {
        public final String pkg, category, reason, rule;
        public String purpose = "", risk = "", finding = "", profile = "";
        public List<String> sources = new ArrayList<>();

        Verdict(String pkg, String category, String reason, String rule) {
            this.pkg = pkg;
            this.category = category;
            this.reason = reason;
            this.rule = rule;
        }
    }

    private static final class Rule {
        final Pattern pattern;
        final String reason;

        Rule(String p, String r) {
            pattern = Pattern.compile(p);
            reason = r;
        }
    }

    private final List<Rule> core = new ArrayList<>(), vehicle = new ArrayList<>();
    private final List<String> words = new ArrayList<>();
    private final List<String> prefixShort = new ArrayList<>();
    private final String keywordReason;
    private final Map<String, JSONObject> telemetry = new HashMap<>();
    private final List<Object[]> profileProtect = new ArrayList<>();   // {Pattern, JSONObject profile}
    private final Map<String, Object[]> profilePkgs = new HashMap<>(); // pkg -> {category, entry, profile}
    private final Map<String, String> extraProtected = new LinkedHashMap<>();

    public Rules(JSONObject protectedJson, JSONObject telemetryJson, List<JSONObject> matchedProfiles,
                 Map<String, String> extra) throws JSONException {
        JSONArray c = protectedJson.getJSONArray("core");
        for (int i = 0; i < c.length(); i++) core.add(new Rule(c.getJSONObject(i).getString("pattern"), c.getJSONObject(i).getString("reason")));
        JSONArray v = protectedJson.getJSONArray("vehicle");
        for (int i = 0; i < v.length(); i++) vehicle.add(new Rule(v.getJSONObject(i).getString("pattern"), v.getJSONObject(i).getString("reason")));
        JSONObject kw = protectedJson.getJSONObject("keyword");
        JSONArray w = kw.getJSONArray("words");
        for (int i = 0; i < w.length(); i++) words.add(w.getString(i));
        JSONArray ps = kw.optJSONArray("prefix_short");
        if (ps != null) for (int i = 0; i < ps.length(); i++) prefixShort.add(ps.getString(i));
        keywordReason = kw.getString("reason");
        JSONArray t = telemetryJson.getJSONArray("packages");
        for (int i = 0; i < t.length(); i++) telemetry.put(t.getJSONObject(i).getString("package"), t.getJSONObject(i));
        for (JSONObject p : matchedProfiles) {
            JSONArray prot = p.optJSONArray("protected");
            if (prot != null) for (int i = 0; i < prot.length(); i++) profileProtect.add(new Object[]{Pattern.compile(prot.getString(i)), p});
            JSONArray pk = p.optJSONArray("packages");
            if (pk == null) continue;
            for (int i = 0; i < pk.length(); i++) {
                JSONObject e = pk.getJSONObject(i);
                String cat = e.getString("category");
                if (RECOMMENDED.equals(cat) && !"tested".equals(p.optString("status"))) cat = OPTIONAL;
                JSONObject policy = p.optJSONObject("policy");
                if (RECOMMENDED.equals(cat) && policy != null && "none".equals(policy.optString("recommended"))) cat = OPTIONAL;
                Object[] prev = profilePkgs.get(e.getString("package"));
                if (prev == null || strictness(cat) > strictness((String) prev[0])) {
                    profilePkgs.put(e.getString("package"), new Object[]{cat, e, p});
                }
            }
        }
        if (extra != null) extraProtected.putAll(extra);
    }

    static String keywordHit(String pkg, List<String> words, List<String> prefixShort) {
        if (pkg.startsWith("com.android.") || pkg.startsWith("com.google.android.") || pkg.startsWith("android.")) return null;
        String[] segs = pkg.toLowerCase(java.util.Locale.ROOT).split("\\.");
        for (int s = 1; s < segs.length; s++) {
            for (String part : segs[s].split("[_\\d]+")) {
                for (String w : words) {
                    if (part.equals(w) || ((w.length() >= 4 || prefixShort.contains(w)) && part.startsWith(w))) return w;
                }
            }
        }
        return null;
    }

    public Verdict classify(String pkg, boolean system) {
        for (Rule r : core) if (r.pattern.matcher(pkg).matches()) return new Verdict(pkg, PROTECTED, r.reason, "core");
        if (extraProtected.containsKey(pkg)) return new Verdict(pkg, PROTECTED, extraProtected.get(pkg), "device");
        for (Object[] pp : profileProtect) {
            if (((Pattern) pp[0]).matcher(pkg).matches()) {
                String why = null;
                for (Rule r : vehicle) if (r.pattern.matcher(pkg).matches()) { why = r.reason; break; }
                JSONObject p = (JSONObject) pp[1];
                Verdict v = new Verdict(pkg, PROTECTED, why != null ? why : "Protected by the " + p.optString("name") + " profile.", "profile");
                v.profile = p.optString("id");
                return v;
            }
        }
        JSONObject t = telemetry.get(pkg);
        if (t != null) {
            String cat = t.optString("category");
            Object[] prof = profilePkgs.get(pkg);
            if (prof != null && strictness((String) prof[0]) > strictness(cat)) cat = (String) prof[0];
            Verdict v = new Verdict(pkg, cat, t.optString("evidence"), "telemetry");
            v.purpose = t.optString("purpose");
            v.risk = t.optString("risk");
            v.finding = t.optString("finding");
            JSONArray src = t.optJSONArray("sources");
            if (src != null) for (int i = 0; i < src.length(); i++) v.sources.add(src.optString(i));
            return v;
        }
        Object[] prof = profilePkgs.get(pkg);
        if (prof != null) {
            JSONObject e = (JSONObject) prof[1];
            Verdict v = new Verdict(pkg, (String) prof[0], e.optString("purpose"), "profile");
            v.purpose = e.optString("purpose");
            v.risk = e.optString("risk");
            v.profile = ((JSONObject) prof[2]).optString("id");
            return v;
        }
        for (Rule r : vehicle) if (r.pattern.matcher(pkg).matches()) return new Verdict(pkg, PROTECTED, r.reason, "vehicle");
        String hit = keywordHit(pkg, words, prefixShort);
        if (hit != null) return new Verdict(pkg, PROTECTED, keywordReason + " (word: " + hit + ")", "keyword");
        if (!system) return new Verdict(pkg, USER, "Installed by the owner, not part of the firmware.", "user");
        return new Verdict(pkg, UNKNOWN, "Purpose not verified on any device profile.", "unknown");
    }

    // --- device profiles ----------------------------------------------------------------------
    public static boolean matches(JSONObject profile, Map<String, String> props, Set<String> packages, Set<String> declared) {
        JSONObject m = profile.optJSONObject("match");
        if (m == null) return false;
        if (m.optBoolean("always")) return true;
        if (m.optBoolean("declared")) return declared.contains(profile.optString("id"));
        boolean ok = false;
        JSONArray all = m.optJSONArray("packages_all");
        if (all != null) {
            for (int i = 0; i < all.length(); i++) if (!packages.contains(all.optString(i))) return false;
            ok = true;
        }
        JSONArray any = m.optJSONArray("packages_regex_any");
        if (any != null) {
            boolean hit = false;
            for (int i = 0; i < any.length() && !hit; i++) {
                Pattern p = Pattern.compile(any.optString(i));
                for (String pkg : packages) if (p.matcher(pkg).matches()) { hit = true; break; }
            }
            if (!hit) return false;
            ok = true;
        }
        JSONObject propsAny = m.optJSONObject("props_any");
        if (propsAny != null) {
            boolean hit = false;
            java.util.Iterator<String> keys = propsAny.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                String val = props.get(k);
                if (val != null && Pattern.compile(propsAny.optString(k)).matcher(val).find()) hit = true;
            }
            if (!hit) return false;
            ok = true;
        }
        return ok;   // a profile needs a hardware or firmware signal; a name alone never matches
    }
}
