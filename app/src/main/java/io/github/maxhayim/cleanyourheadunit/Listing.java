package io.github.maxhayim.cleanyourheadunit;

import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.util.Iterator;
import java.util.List;

/** Catalog entries: a card for grids, and the full listing page with official links and install. */
final class Listing {
    private Listing() {}

    static String orientations(JSONObject it) {
        List<String> o = Data.strings(it.optJSONArray("orientations"));
        return o.isEmpty() ? "" : Screens.cap(TextUtils.join(" & ", o));
    }

    static String minAndroid(JSONObject it) {
        JSONObject an = it.optJSONObject("android");
        return an == null ? null : "Android " + an.optString("min_version") + "+";
    }

    static View card(final MainActivity a, final JSONObject it) {
        Theme th = Ui.t(a);
        LinearLayout c = Ui.card(a);
        LinearLayout top = Ui.hbox(a);
        LinearLayout names = Ui.vbox(a);
        names.addView(Ui.text(a, it.optString("name"), 18, th.text, true));
        names.addView(Ui.dim(a, "by " + Catalog.credit(a, it.optString("developer")) + " · " + Screens.cap(it.optString("kind"))));
        top.addView(names, Ui.weight(1));
        String st = it.optJSONObject("compatibility") == null ? "" : it.optJSONObject("compatibility").optString("status");
        top.addView(Ui.pill(a, Catalog.status(it), Screens.statusColor(a, it.has("automotive_status") ? "planned" : st)));
        c.addView(top);
        TextView d = Ui.text(a, it.optString("description"), 14, th.dim, false);
        d.setMaxLines(3);
        d.setEllipsize(TextUtils.TruncateAt.END);
        Ui.add(c, d, 8);
        Ui.Flow tags = new Ui.Flow(a);
        String or = orientations(it);
        if (!or.isEmpty()) Ui.add(tags, Ui.pill(a, or, th.dim), 0);
        if (minAndroid(it) != null) Ui.add(tags, Ui.pill(a, minAndroid(it), th.dim), 6);
        if (it.has("open_source")) Ui.add(tags, Ui.pill(a, it.optBoolean("open_source") ? "Open source" : "Closed source", th.dim), 6);
        Ui.add(c, tags, 10);
        String ver = "builtin".equals(it.optString("install")) ? null : Catalog.installedVersion(a, it.optString("package"));
        if (ver != null) {
            String sig = Catalog.signature(a, it);
            Ui.add(c, Ui.text(a, "Installed " + ver + ("official".equals(sig) ? " · official signature" : "different".equals(sig) ? " · NOT the official signature" : ""),
                    13, "different".equals(sig) ? th.warn : th.ok, true), 8);
        }
        Ui.add(c, Ui.button(a, "Details", false, v -> a.push(it.optString("name"), page(a, it))), 12);
        return c;
    }

    static View page(final MainActivity a, final JSONObject it) {
        Theme th = Ui.t(a);
        Unit u = a.unit;
        LinearLayout l = Ui.vbox(a);
        LinearLayout head = Ui.hbox(a);
        head.addView(Ui.header(a, it.optString("name"), "by " + Catalog.credit(a, it.optString("developer"))), Ui.weight(1));
        head.addView(Ui.pill(a, Catalog.status(it), Screens.statusColor(a, it.has("automotive_status") ? "planned"
                : it.optJSONObject("compatibility").optString("status"))));
        Ui.add(l, head, 12);
        Ui.add(l, Ui.body(a, it.optString("description")), 10);

        Ui.Grid g = new Ui.Grid(a, 320);
        LinearLayout facts = Ui.card(a);
        facts.addView(Ui.text(a, "Facts", 17, th.text, true));
        Ui.add(facts, Ui.kv(a, "Developer", Catalog.credit(a, it.optString("developer"))), 8);
        facts.addView(Ui.kv(a, "Licence", it.optString("license")));
        if (it.has("package") && !"builtin".equals(it.optString("install"))) facts.addView(Ui.kv(a, "Package", it.optString("package")));
        facts.addView(Ui.kv(a, "Android", minAndroid(it)));
        facts.addView(Ui.kv(a, "Orientation", orientations(it)));
        List<String> screens = Data.strings(it.optJSONArray("screens"));
        if (!screens.isEmpty()) facts.addView(Ui.kv(a, "Made for", TextUtils.join(", ", screens)));
        JSONObject src = it.optJSONObject("source");
        if (src != null && src.has("repo")) facts.addView(Ui.kv(a, "Official source", "github.com/" + src.optString("repo")));
        g.addView(facts);

        LinearLayout here = Ui.card(a);
        here.addView(Ui.text(a, "On this unit", 17, th.text, true));
        String ver = "builtin".equals(it.optString("install")) ? Screens.BuildConfigHolder.version(a) : Catalog.installedVersion(a, it.optString("package"));
        Ui.add(here, Ui.kv(a, "Installed", ver == null ? "No" : ver), 8);
        String sig = Catalog.signature(a, it);
        if (ver != null && sig != null) here.addView(Ui.kv(a, "Signature", "official".equals(sig) ? "Matches the developer's certificate" : "Does NOT match the developer's certificate"));
        boolean orientOk = Screens.Launchers.fitsHere(u, it) || it.optJSONArray("orientations") == null;
        here.addView(Ui.kv(a, "Fits this screen", orientOk ? "Yes (" + u.orientation() + ", Android " + u.android() + ")"
                : "Check: made for " + orientations(it).toLowerCase(java.util.Locale.ROOT) + " / " + minAndroid(it)));
        JSONObject comp = it.optJSONObject("compatibility");
        here.addView(Ui.kv(a, "Verified on", comp == null || Data.strings(comp.optJSONArray("verified")).isEmpty() ? "No head unit yet"
                : TextUtils.join(", ", Data.strings(comp.optJSONArray("verified")))));
        for (String r : Data.strings(comp == null ? null : comp.optJSONArray("reports"))) Ui.add(here, Ui.dim(a, r), 6);
        g.addView(here);
        Ui.add(l, g, 12);

        List<String> warn = Data.strings(it.optJSONArray("warnings"));
        if (!warn.isEmpty()) {
            LinearLayout w = Ui.card(a);
            w.addView(Ui.text(a, "Good to know", 17, th.warn, true));
            for (String s : warn) Ui.add(w, Ui.body(a, "•  " + s), 6);
            Ui.add(l, w, 12);
        }

        Ui.Flow b = new Ui.Flow(a);
        final String pkg = it.optString("package");
        if (ver != null && !"builtin".equals(it.optString("install"))) {
            final Intent launch = a.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) b.addView(Ui.button(a, "Open", true, v -> a.startActivity(launch)));
        }
        if ("github_release".equals(it.optString("install")) && !"planned".equals(it.optString("status"))) {
            JSONObject an = it.optJSONObject("android");
            if (an == null || u.sdk() >= an.optInt("min_sdk")) {
                b.addView(Ui.button(a, ver == null ? "Download and install" : "Check for update", ver == null, v -> install(a, it)));
            }
        }
        JSONObject links = it.optJSONObject("links");
        if (links != null) {
            Iterator<String> k = links.keys();
            while (k.hasNext()) {
                final String key = k.next();
                final String url = links.optString(key);
                b.addView(Ui.button(a, Screens.cap(key), false, v -> a.openUrl(url)));
            }
        }
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
        hs.addView(b);
        Ui.add(l, hs, 16);
        if (it.has("automotive_status")) {
            Ui.add(l, Ui.text(a, it.optString("automotive_status") + ". This listing will offer an install once a head-unit release exists and has been tested.",
                    14, th.dim, false), 12);
        } else if (!it.optBoolean("open_source", true)) {
            Ui.add(l, Ui.dim(a, "Closed source. CleanYourHeadUnit downloads it only from the developer's own release page, checks the signature, "
                    + "and never mirrors or modifies it. Not affiliated with or endorsed by the developer."), 12);
        } else if (!"builtin".equals(it.optString("install"))) {
            Ui.add(l, Ui.dim(a, "Independent project, installed and updated separately. Listing it is not an endorsement by its developer."), 12);
        }
        Ui.add(l, Ui.dim(a, "Screenshots: on the developer's page (the catalog doesn't copy them)."), 6);
        return l;
    }

    static void install(final MainActivity a, final JSONObject it) {
        a.confirm("Download " + it.optString("name") + "?",
                "From github.com/" + it.optJSONObject("source").optString("repo") + " (the developer's own releases). "
                        + "The download is checked against the release checksum and the developer's signing certificate before Android's installer asks you.",
                () -> {
                    final android.app.ProgressDialog pd = new android.app.ProgressDialog(a);
                    pd.setMessage("Starting…");
                    pd.setCancelable(false);
                    pd.show();
                    new Installer(a).start(it, new Installer.Listener() {
                        public void progress(String m) { pd.setMessage(m); }

                        public void done(boolean ok, String m) {
                            pd.dismiss();
                            if (!ok) {
                                a.info("Not installed", m);
                                return;
                            }
                            if (!Installer.handOff(a, new File(m))) {
                                a.info("One more step", "Allow CleanYourHeadUnit to install apps in the screen that opened, then press Download again.");
                            }
                        }
                    });
                });
    }
}
