package io.github.maxhayim.cleanyourheadunit;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.widget.ImageView;
import android.widget.LinearLayout;

import io.nayuki.qrcodegen.QrCode;

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
 * adds the car and what worked, and presses Submit on GitHub themselves. To avoid signing in to GitHub
 * on the car, a QR code opens the same form on a phone, or Share sends the whole report (LocalSend, etc.).
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
        Map<String, String> q = fields(c, u);
        String full = link(q);
        if (full.length() <= MAX_URL) return full;
        // Drop the long part rather than send a link GitHub would reject.
        q.remove("diagnostics");
        return link(q);
    }

    /** For the QR code: the form with the title, unit and firmware filled in, short enough to scan from a car screen. */
    static String shortUrl(Context c, Unit u) throws JSONException {
        Map<String, String> q = fields(c, u);
        q.remove("checklist");
        q.remove("diagnostics");
        return link(q);
    }

    private static Map<String, String> fields(Context c, Unit u) throws JSONException {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("title", "Unit report: " + unitLine(u) + ", Android " + u.android());
        q.put("unit", unitLine(u));
        q.put("firmware", firmwareLine(u));
        q.put("checklist", CHECKLIST);
        q.put("diagnostics", summary(c, u).toString(2));
        return q;
    }

    private static String link(Map<String, String> q) {
        StringBuilder s = new StringBuilder(FORM);
        for (Map.Entry<String, String> e : q.entrySet()) s.append('&').append(e.getKey()).append('=').append(enc(e.getValue()));
        return s.toString();
    }

    /** What Share sends: the form link first (tap it on the phone), then the report to paste if needed. */
    static String shareText(Context c, Unit u) throws JSONException {
        return "CleanYourHeadUnit unit report. Open this on a phone signed in to GitHub, add your car and what worked, then Submit:\n"
                + url(c, u) + "\n\n" + plain(c, u);
    }

    static String plain(Context c, Unit u) throws JSONException {
        return "Unit: " + unitLine(u) + "\nFirmware: " + firmwareLine(u) + "\n\n" + summary(c, u).toString(2) + "\n";
    }

    /** Dark modules on white, with the standard 4-module quiet zone. */
    static Bitmap qr(String text, int px) {
        QrCode code = QrCode.encodeText(text, QrCode.Ecc.LOW);
        int border = 4, n = code.size + 2 * border, scale = Math.max(1, px / n);
        Bitmap b = Bitmap.createBitmap(n * scale, n * scale, Bitmap.Config.RGB_565);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int color = code.getModule(x - border, y - border) ? Color.BLACK : Color.WHITE;
                for (int dy = 0; dy < scale; dy++) for (int dx = 0; dx < scale; dx++) b.setPixel(x * scale + dx, y * scale + dy, color);
            }
        }
        return b;
    }

    static String enc(String v) {
        try {
            return URLEncoder.encode(v, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Shows exactly what goes in, then lets the owner choose how to send it: this unit's browser, a QR code, or Share. */
    static void open(final MainActivity a) {
        final String url, preview;
        try {
            url = url(a, a.unit);
            preview = plain(a, a.unit);
        } catch (JSONException e) {
            a.info("Couldn't build the report", String.valueOf(e.getMessage()));
            return;
        }
        new AlertDialog.Builder(a).setTitle("Report this unit?")
                .setMessage("This fills in GitHub's Compatibility report form with:\n\n" + preview
                        + "\nNo serial numbers, MAC or IP addresses, network or account names. Nothing is sent until you press Submit on GitHub, "
                        + "after adding your car and what worked. Reporting needs a free GitHub account.\n\n"
                        + "Rather not sign in on the car? Use QR code or Share to finish on your phone.")
                .setPositiveButton("Open here", (d, w) -> launch(a, url))
                .setNeutralButton("QR code", (d, w) -> showQr(a))
                .setNegativeButton("Share", (d, w) -> share(a))
                .show();
    }

    static void showQr(MainActivity a) {
        String link;
        try {
            link = shortUrl(a, a.unit);
        } catch (JSONException e) {
            a.info("Couldn't build the report", String.valueOf(e.getMessage()));
            return;
        }
        int px = Math.min(a.getResources().getDisplayMetrics().heightPixels, a.getResources().getDisplayMetrics().widthPixels) * 3 / 5;
        ImageView img = new ImageView(a);
        img.setImageBitmap(qr(link, px));
        img.setAdjustViewBounds(true);
        LinearLayout box = new LinearLayout(a);
        box.setGravity(android.view.Gravity.CENTER);
        int pad = Ui.dp(a, 12);
        box.setPadding(pad, pad, pad, pad);
        box.addView(img);
        new AlertDialog.Builder(a).setTitle("Scan with your phone")
                .setMessage("Opens the form on your phone with the unit, firmware and title filled in. "
                        + "The longer diagnostics part doesn't fit in a QR code: send it with Share if you'd like to add it.")
                .setView(box)
                .setPositiveButton("Done", null)
                .setNeutralButton("Share", (d, w) -> share(a))
                .show();
    }

    static void share(MainActivity a) {
        String text;
        try {
            text = shareText(a, a.unit);
        } catch (JSONException e) {
            a.info("Couldn't build the report", String.valueOf(e.getMessage()));
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "CleanYourHeadUnit unit report")
                .putExtra(Intent.EXTRA_TEXT, text);
        try {
            a.startActivity(Intent.createChooser(send, "Send the report to your phone"));
        } catch (ActivityNotFoundException e) {
            saveInstead(a, text, "Nothing on this unit can share text");
        }
    }

    static void launch(MainActivity a, String url) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            String text;
            try {
                text = shareText(a, a.unit);
            } catch (JSONException x) {
                text = "";
            }
            saveInstead(a, text, "No browser on this unit");
        }
    }

    private static void saveInstead(MainActivity a, String text, String title) {
        File f = a.export("cleanyourheadunit-unit-report.txt", text);
        a.info(title, (f == null ? "" : "The report is saved at " + f.getAbsolutePath() + ".\n\n")
                + "Use QR code to open the form on your phone, or carry the file over (LocalSend or a flash drive) "
                + "and open github.com/" + SelfUpdate.REPO + "/issues → New issue → Compatibility report.");
    }
}
