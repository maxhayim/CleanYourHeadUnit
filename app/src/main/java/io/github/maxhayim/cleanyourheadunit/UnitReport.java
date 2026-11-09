package io.github.maxhayim.cleanyourheadunit;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * "Report this unit": opens GitHub's Compatibility report form in the browser, filled in with what
 * the Dashboard shows. Only a fixed list of non-identifying facts goes in (never serials, MAC or IP
 * addresses, network or account names). Nothing is sent by the app: the owner reads the form,
 * adds the car and what worked, and presses Submit on GitHub themselves.
 */
final class UnitReport {
    private UnitReport() {}

    static final String FORM = "https://github.com/" + SelfUpdate.REPO + "/issues/new?template=compatibility-report.yml";
    /** GitHub refuses very long addresses; stay well under its limit. */
    static final int MAX_URL = 6000;

    /** The only properties that go in. Each is also passed through Report.scrub. */
    static final String[][] FACTS = {
            {"manufacturer", "ro.product.manufacturer"},
            {"brand", "ro.product.brand"},
            {"model", "ro.product.model"},
            {"device", "ro.product.device"},
            {"platform", "ro.board.platform"},
            {"hardware", "ro.hardware"},
            {"build", "ro.build.display.id"},
    };

    static String prop(Unit u, String key) {
        String v = Report.scrub(u.props.get(key));
        return v == null || v.trim().isEmpty() ? null : v.trim();
    }

    /** The summary pasted into the form's Diagnostics box. */
    static JSONObject summary(Context c, Unit u) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("app", "CleanYourHeadUnit " + Screens.BuildConfigHolder.version(c));
        for (String[] f : FACTS) {
            String v = prop(u, f[1]);
            if (v != null) o.put(f[0], v);
        }
        o.put("android", u.android());
        if (u.androidSpoofed()) o.put("android_settings_say", u.claimedAndroid());
        o.put("api", u.sdk());
        if (u.abi() != null) o.put("abi", u.abi());
        JSONObject mcu = new JSONObject();
        for (Map.Entry<String, String> e : u.mcu().entrySet()) mcu.put(e.getKey(), Report.scrub(e.getValue()));
        if (mcu.length() > 0) o.put("mcu", mcu);
        o.put("screen", u.width + "x" + u.height + " " + u.dpi + "dpi " + u.orientation());
        o.put("ram", Unit.mb(u.ramTotal));
        o.put("firmware_family", u.firmwareFamily());
        o.put("profiles", Screens.Diagnostics.profileNames(u));
        Map<String, Integer> counts = new LinkedHashMap<>();
        Rules r = u.rules(c);
        for (Unit.Pkg p : u.packages.values()) {
            String cat = r.classify(p.name, p.system).category;
            Integer n = counts.get(cat);
            counts.put(cat, n == null ? 1 : n + 1);
        }
        o.put("packages", new JSONObject(counts));
        o.put("disabled", u.disabledCount());
        if (u.home != null) o.put("home_app", u.home.getPackageName());
        return o;
    }

    static String firmwareLine(Unit u) {
        StringBuilder s = new StringBuilder("Android ").append(u.androidLabel()).append(", API ").append(u.sdk());
        String build = prop(u, "ro.build.display.id");
        if (build != null) s.append(", build ").append(build);
        for (String v : u.mcu().values()) s.append(", MCU ").append(Report.scrub(v));
        String fam = u.firmwareFamily();
        if (fam != null && !"unknown".equals(fam)) s.append(", ").append(fam).append(" firmware");
        return s.toString();
    }

    static String unitLine(Unit u) {
        String brand = prop(u, "ro.product.brand"), model = prop(u, "ro.product.model");
        StringBuilder s = new StringBuilder();
        if (brand != null) s.append(brand);
        if (model != null) s.append(s.length() > 0 ? " " : "").append(model);
        if (u.chipset() != null) s.append(" (").append(Report.scrub(u.chipset())).append(")");
        return s.toString();
    }

    static final String CHECKLIST = "- [ ] CleanYourHeadUnit opened and the Dashboard looked right\n"
            + "- [ ] Android version and firmware shown correctly\n"
            + "- [ ] Launchers list looked right\n"
            + "- [ ] Disabled something and restored it (say which)\n"
            + "- [ ] Radio, camera, Bluetooth calls, steering-wheel keys still fine afterwards\n";

    static String url(Context c, Unit u) throws JSONException {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("title", "Unit report: " + unitLine(u) + ", Android " + u.android());
        q.put("unit", unitLine(u));
        q.put("firmware", firmwareLine(u));
        q.put("checklist", CHECKLIST);
        q.put("diagnostics", summary(c, u).toString(2));
        StringBuilder s = new StringBuilder(FORM);
        for (Map.Entry<String, String> e : q.entrySet()) s.append('&').append(e.getKey()).append('=').append(enc(e.getValue()));
        if (s.length() > MAX_URL) {
            // Drop the long part rather than send a link GitHub would reject.
            s.setLength(0);
            s.append(FORM);
            q.remove("diagnostics");
            for (Map.Entry<String, String> e : q.entrySet()) s.append('&').append(e.getKey()).append('=').append(enc(e.getValue()));
        }
        return s.toString();
    }

    static String enc(String v) {
        try {
            return URLEncoder.encode(v, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Shows exactly what goes in, then opens the form only if the owner agrees. */
    static void open(final MainActivity a) {
        final String url, preview;
        try {
            url = url(a, a.unit);
            preview = "Unit: " + unitLine(a.unit) + "\nFirmware: " + firmwareLine(a.unit) + "\n\n" + summary(a, a.unit).toString(2);
        } catch (JSONException e) {
            a.info("Couldn't build the report", String.valueOf(e.getMessage()));
            return;
        }
        new AlertDialog.Builder(a).setTitle("Report this unit?")
                .setMessage("This opens GitHub's Compatibility report form in the browser, already filled in with:\n\n" + preview
                        + "\n\nNo serial numbers, MAC or IP addresses, network or account names. Nothing is sent until you press Submit on GitHub, "
                        + "after adding your car and what worked. Reporting needs a free GitHub account.")
                .setPositiveButton("Open the form", (d, w) -> launch(a, url))
                .setNegativeButton("Cancel", null)
                .show();
    }

    static void launch(MainActivity a, String url) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            String text;
            try {
                text = "Unit: " + unitLine(a.unit) + "\nFirmware: " + firmwareLine(a.unit) + "\n\n" + summary(a, a.unit).toString(2) + "\n";
            } catch (JSONException x) {
                text = "";
            }
            File f = a.export("cleanyourheadunit-unit-report.txt", text);
            a.info("No browser on this unit", (f == null ? "" : "The report is saved at " + f.getAbsolutePath() + ".\n\n")
                    + "On your phone, open github.com/" + SelfUpdate.REPO + "/issues, choose New issue → Compatibility report, "
                    + "and paste the report (LocalSend can carry the file over).");
        }
    }
}
