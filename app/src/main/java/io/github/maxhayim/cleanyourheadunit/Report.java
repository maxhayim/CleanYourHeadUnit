package io.github.maxhayim.cleanyourheadunit;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;
import java.util.regex.Pattern;

/** The sanitised diagnostic report, same rules as tools/cyhu/diagnostics.py. Written to a file; never sent. */
final class Report {
    static final Pattern SENSITIVE_KEY = Pattern.compile("(serial|imei|meid|imsi|iccid|msisdn|mac|bt\\.?addr|bluetooth\\.addr|wifi|wlan\\.ssid|ssid|"
            + "phone\\.number|line1|account|email|token|password|passwd|secret|key|\\.sn$|\\.sn\\.|uuid|android_id|gsm\\.operator\\.(alpha|numeric)|gsm\\.sim\\.operator)",
            Pattern.CASE_INSENSITIVE);

    private Report() {}

    static String scrub(String v) {
        if (v == null) return null;
        v = v.replaceAll("\\b([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\\b", "[mac]");
        v = v.replaceAll("[\\w.+-]+@[\\w-]+\\.[\\w.-]+", "[email]");
        v = v.replaceAll("\\b\\d{12,}\\b", "[number]");
        v = v.replaceAll("\\b(10|192\\.168|172\\.(1[6-9]|2\\d|3[01]))(\\.\\d{1,3}){2,3}\\b", "[lan-ip]");
        return v;
    }

    static String build(MainActivity a) {
        try {
            return json(a, a.unit).toString(2);
        } catch (JSONException e) {
            return "{}";
        }
    }

    static JSONObject json(Context c, Unit u) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("report", "CleanYourHeadUnit diagnostics (sanitised; review before sharing)");
        JSONObject props = new JSONObject();
        for (Map.Entry<String, String> e : u.props.entrySet()) {
            if (!e.getKey().startsWith("ro.build.fingerprint") && SENSITIVE_KEY.matcher(e.getKey()).find()) continue;
            props.put(e.getKey(), scrub(e.getValue()));
        }
        o.put("props", props);
        o.put("screen", u.width + "x" + u.height + " " + u.dpi + "dpi");
        o.put("ram_total", Unit.mb(u.ramTotal));
        o.put("storage_total", Unit.mb(u.storageTotal));
        JSONArray profiles = new JSONArray();
        for (JSONObject p : u.profiles) profiles.put(p.optString("id"));
        o.put("profiles", profiles);
        JSONArray disabled = new JSONArray();
        for (Unit.Pkg p : u.packages.values()) if (!p.enabled) disabled.put(p.name);
        o.put("disabled_packages", disabled);
        o.put("home", u.home == null ? JSONObject.NULL : u.home.flattenToString());
        JSONArray f = new JSONArray();
        for (Privacy.Finding x : Privacy.findings(c, u)) {
            f.put(new JSONObject().put("package", x.pkg.name).put("finding", x.entry.optString("finding")).put("enabled", x.pkg.enabled));
        }
        o.put("privacy_findings", f);
        o.put("privacy_profile", Prefs.privacyProfile(c));
        o.put("private_dns", Privacy.dnsLabel(Privacy.privateDns(c)));
        o.put("firewall", Firewall.isRunning() ? Firewall.runningMode : "off");
        JSONObject mods = new JSONObject();
        for (JSONObject it : Catalog.items(c, null)) {
            String v = "builtin".equals(it.optString("install")) ? null : Catalog.installedVersion(c, it.optString("package"));
            if (v != null) mods.put(it.optString("id"), v);
        }
        o.put("catalog_installed", mods);
        return o;
    }
}
