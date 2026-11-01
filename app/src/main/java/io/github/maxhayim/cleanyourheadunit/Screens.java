package io.github.maxhayim.cleanyourheadunit;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Every section except Privacy Shield (PrivacyScreen). Each builds its view from the scanned Unit. */
final class Screens {
    static final String SUBMIT_URL = "https://github.com/maxhayim/CleanYourHeadUnit/issues/new/choose";
    static final String REPO_URL = "https://github.com/maxhayim/CleanYourHeadUnit";

    private Screens() {}

    static String profileStatus(JSONObject p) {
        if (p == null) return "No profile";
        switch (p.optString("status")) {
            case "tested": return "Tested profile";
            case "generic": return "Generic rules only";
            case "experimental": return "Experimental profile";
            default: return "Unverified profile";
        }
    }

    static int statusColor(Context c, String status) {
        Theme t = Ui.t(c);
        switch (status) {
            case "tested": case "verified": return t.ok;
            case "community": return t.accent;
            case "planned": return t.dim;
            default: return t.warn;
        }
    }

    static String homeLabel(MainActivity a) {
        if (a.unit.home == null) return Build.VERSION.SDK_INT >= 21 ? "Not set (Android asks)" : null;
        Unit.Pkg p = a.unit.packages.get(a.unit.home.getPackageName());
        return p == null ? a.unit.home.getPackageName() : p.label;
    }

    static List<String> installedModules(MainActivity a) {
        List<String> out = new ArrayList<>();
        for (JSONObject it : Catalog.items(a, null)) {
            if (!"builtin".equals(it.optString("install")) && a.unit.packages.containsKey(it.optString("package"))) out.add(it.optString("name"));
        }
        return out;
    }

    // =============================================================================================
    static final class Dashboard implements MainActivity.Section {
        public String id() { return "dashboard"; }
        public String title() { return "Dashboard"; }
        public int icon() { return R.drawable.ic_dashboard; }

        public View build(MainActivity a) {
            Theme th = Ui.t(a);
            Unit u = a.unit;
            LinearLayout l = Ui.vbox(a);
            JSONObject prof = u.mainProfile();
            LinearLayout head = Ui.hbox(a);
            head.addView(Ui.header(a, "Your head unit", "Your head unit. Your rules."), Ui.weight(1));
            head.addView(Ui.pill(a, profileStatus(prof), statusColor(a, prof == null ? "" : prof.optString("status"))));
            l.addView(head);

            List<Privacy.Finding> findings = Privacy.findings(a, u);
            Ui.Grid stats = new Ui.Grid(a, 150);
            stats.addView(Ui.stat(a, "Android " + u.android(), "API " + u.sdk(), th.text));
            stats.addView(Ui.stat(a, String.valueOf(u.packages.size()), u.disabledCount() + " disabled", th.text));
            stats.addView(Ui.stat(a, String.valueOf(findings.size()), findings.isEmpty() ? "catalogued trackers found" : "privacy findings",
                    findings.isEmpty() ? th.ok : th.warn));
            stats.addView(Ui.stat(a, Firewall.isRunning() ? "On" : "Off", "local firewall", Firewall.isRunning() ? th.ok : th.dim));
            Ui.add(l, stats, 18);

            LinearLayout dev = Ui.card(a);
            dev.addView(Ui.text(a, "Device", 17, th.text, true));
            Ui.add(dev, Ui.kv(a, "Manufacturer", u.props.get("ro.product.manufacturer")), 8);
            dev.addView(Ui.kv(a, "Model", u.props.get("ro.product.model")));
            dev.addView(Ui.kv(a, "Android", u.android() + " (API " + u.sdk() + ")"));
            dev.addView(Ui.kv(a, "MCU", u.mcu().isEmpty() ? null : android.text.TextUtils.join(", ", u.mcu().values())));
            dev.addView(Ui.kv(a, "Chipset", u.chipset() == null ? null : u.chipset() + (u.abi() == null ? "" : " · " + u.abi())));
            dev.addView(Ui.kv(a, "Screen", u.width + " × " + u.height + " · " + u.dpi + " dpi · " + u.orientation()));
            dev.addView(Ui.kv(a, "Firmware family", "unknown".equals(u.firmwareFamily()) ? "Not recognised (generic rules)" : prof.optString("name")));
            dev.addView(Ui.kv(a, "Memory", Unit.mb(u.ramAvail) == null ? null : Unit.mb(u.ramAvail) + " free of " + Unit.mb(u.ramTotal)));
            dev.addView(Ui.kv(a, "Storage", Unit.mb(u.storageFree) == null ? null : Unit.mb(u.storageFree) + " free of " + Unit.mb(u.storageTotal)));

            LinearLayout state = Ui.card(a);
            state.addView(Ui.text(a, "Setup", 17, th.text, true));
            Ui.add(state, Ui.kv(a, "Home app", homeLabel(a)), 8);
            state.addView(Ui.kv(a, "Privacy profile", cap(Prefs.privacyProfile(a))));
            state.addView(Ui.kv(a, "Private DNS", Privacy.dnsLabel(Privacy.privateDns(a))));
            state.addView(Ui.kv(a, "Firewall", Firewall.isRunning() ? ("dns".equals(Firewall.runningMode) ? "DNS filter on" : "App block on") : "Off"));
            List<String> mods = installedModules(a);
            state.addView(Ui.kv(a, "From the catalog", mods.isEmpty() ? "None installed" : android.text.TextUtils.join(", ", mods)));
            state.addView(Ui.kv(a, "Theme", Ui.t(a).name));

            Ui.Grid two = new Ui.Grid(a, 330);
            two.addView(dev);
            two.addView(state);
            Ui.add(l, two, 12);

            Ui.Flow actions = new Ui.Flow(a);
            actions.addView(Ui.button(a, "Review packages", true, v -> { a.current = "debloater"; a.rebuild(); }));
            actions.addView(Ui.button(a, "Privacy Shield", false, v -> { a.current = "privacy"; a.rebuild(); }));
            actions.addView(Ui.button(a, "Device details", false, v -> a.push("Device", Diagnostics.device(a))));
            Ui.add(l, actions, 16);
            return l;
        }
    }

    /** Presets for the accent colour; any #RRGGBB can also be typed. */
    static final String[][] ACCENTS = {
            {"Blue", "#8FA8FF"}, {"Sky", "#4FC3F7"}, {"Teal", "#2EC4B6"}, {"Lime", "#C5E15A"}, {"Amber", "#FFB74D"},
            {"Coral", "#FF8A65"}, {"Red", "#EF5350"}, {"Pink", "#FF7EB6"}, {"Violet", "#B39DFF"}, {"White", "#E8EDF2"},
    };

    /** Pick the accent colour: a swatch, the theme's own, or any hex colour. */
    static View accentCard(final MainActivity a) {
        Theme th = Ui.t(a);
        final String chosen = Prefs.accent(a);
        LinearLayout c = Ui.card(a);
        c.addView(Ui.text(a, "Accent colour", 17, th.text, true));
        Ui.add(c, Ui.dim(a, "Used for buttons, highlights and the navigation. Works with every theme."), 4);
        Ui.Flow sw = new Ui.Flow(a);
        for (final String[] p : ACCENTS) {
            int col = android.graphics.Color.parseColor(p[1]);
            boolean on = p[1].equalsIgnoreCase(chosen);
            View dot = new View(a);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(col);
            if (on) g.setStroke(Ui.dp(a, 3), th.text);
            dot.setBackground(g);
            dot.setLayoutParams(new android.view.ViewGroup.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));
            dot.setContentDescription(p[0] + (on ? " (in use)" : ""));
            dot.setFocusable(true);
            dot.setClickable(true);
            dot.setOnClickListener(v -> { Prefs.setAccent(a, p[1]); a.refresh(); });
            sw.addView(dot);
        }
        Ui.add(c, sw, 12);
        Ui.Flow row = new Ui.Flow(a);
        final android.widget.EditText hex = new android.widget.EditText(a);
        hex.setHint("#RRGGBB");
        hex.setText(chosen == null ? "" : chosen);
        hex.setSingleLine(true);
        hex.setTextColor(th.text);
        hex.setHintTextColor(th.dim);
        hex.setMinWidth(Ui.dp(a, 140));
        row.addView(hex);
        row.addView(Ui.button(a, "Use colour", false, v -> {
            String h = hex.getText().toString().trim();
            if (!h.startsWith("#")) h = "#" + h;
            if (!Theme.COLOR.matcher(h).matches()) {
                a.info("Not a colour", "Type six hex digits, like #8FA8FF.");
                return;
            }
            Prefs.setAccent(a, h);
            a.refresh();
        }));
        row.addView(Ui.button(a, chosen == null ? "Theme's own colour (in use)" : "Theme's own colour", chosen == null, v -> {
            Prefs.setAccent(a, null);
            a.refresh();
        }));
        Ui.add(c, row, 10);
        return c;
    }

    static String cap(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // =============================================================================================
    static final class Debloater implements MainActivity.Section {
        static String filter = Rules.OPTIONAL;
        static final Set<String> plan = new TreeSet<>();

        public String id() { return "debloater"; }
        public String title() { return "Debloater"; }
        public int icon() { return R.drawable.ic_debloat; }

        public View build(MainActivity a) {
            Theme th = Ui.t(a);
            Unit u = a.unit;
            Rules r = u.rules(a);
            LinearLayout l = Ui.vbox(a);
            l.addView(Ui.header(a, "Debloater", "Reversible: packages are disabled, never uninstalled. Protected and Unknown packages are never offered."));
            Ui.add(l, a.parked(), 12);

            Map<String, List<Rules.Verdict>> by = new LinkedHashMap<>();
            for (String c : new String[]{Rules.RECOMMENDED, Rules.OPTIONAL, Rules.ADVANCED, Rules.UNKNOWN, Rules.PROTECTED, Rules.USER}) by.put(c, new ArrayList<Rules.Verdict>());
            for (Unit.Pkg p : u.packages.values()) {
                Rules.Verdict v = r.classify(p.name, p.system);
                by.get(v.category).add(v);
            }
            LinearLayout chips = Ui.hbox(a);
            for (final String c : by.keySet()) {
                TextView chip = Ui.pill(a, Ui.categoryLabel(c) + "  " + by.get(c).size(), c.equals(filter) ? th.text : Ui.categoryColor(a, c));
                if (c.equals(filter)) chip.setBackground(Ui.round(Ui.mix(th.surface, Ui.categoryColor(a, c), 0.55f), Ui.dp(a, 20)));
                chip.setMinHeight(Ui.dp(a, 40));
                chip.setGravity(Gravity.CENTER);
                chip.setClickable(true);
                chip.setFocusable(true);
                chip.setOnClickListener(v -> { filter = c; a.show("debloater"); });
                Ui.add(chips, chip, 8);
            }
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(chips);
            Ui.add(l, hs, 14);

            Ui.add(l, Ui.dim(a, explain(filter)), 10);
            if (Rules.RECOMMENDED.equals(filter) && by.get(filter).isEmpty()) {
                Ui.add(l, Ui.body(a, "Nothing is Recommended on this unit: Recommended needs a device profile that was tested on real hardware, and "
                        + profileStatus(u.mainProfile()).toLowerCase(java.util.Locale.ROOT) + " applies here. See Optional, and help by contributing a tested profile."), 8);
            }
            LinearLayout list = Ui.vbox(a);
            for (final Rules.Verdict v : by.get(filter)) Ui.add(list, row(a, v), 8);
            Ui.add(l, list, 10);

            LinearLayout foot = Ui.hbox(a);
            foot.addView(Ui.text(a, plan.isEmpty() ? "Plan: empty" : "Plan: " + plan.size() + " package(s)", 15, th.text, true), Ui.weight(1));
            foot.addView(Ui.button(a, "Export plan for computer", plan.isEmpty(), v -> exportPlan(a)));
            Ui.add(l, foot, 16);
            return l;
        }

        static String explain(String c) {
            switch (c) {
                case Rules.RECOMMENDED: return "Verified unnecessary on a tested device profile.";
                case Rules.OPTIONAL: return "Known purpose. Disable if you don't use what it does.";
                case Rules.ADVANCED: return "Needs care: read the risk first. Often an updater or a documented telemetry client.";
                case Rules.UNKNOWN: return "Purpose not verified. Never offered for disabling.";
                case Rules.PROTECTED: return "Android core and vehicle functions: MCU, CAN bus, radio, camera, Bluetooth, audio, climate, updates, Home. Never changed.";
                default: return "Apps you installed. Not part of debloating.";
            }
        }

        static View row(final MainActivity a, final Rules.Verdict v) {
            Theme th = Ui.t(a);
            Unit.Pkg p = a.unit.packages.get(v.pkg);
            LinearLayout row = Ui.card(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Ui.dp(a, 64));
            LinearLayout names = Ui.vbox(a);
            names.addView(Ui.text(a, p.label, 15, th.text, true));
            names.addView(Ui.dim(a, v.pkg));
            row.addView(names, Ui.weight(1));
            if (!v.finding.isEmpty()) Ui.add(row, Ui.pill(a, PrivacyScreen.findingLabel(v.finding), th.warn), 8);
            if (plan.contains(v.pkg)) Ui.add(row, Ui.pill(a, "In plan", th.accent), 8);
            if (!p.enabled) Ui.add(row, Ui.pill(a, "Disabled", th.dim), 8);
            Ui.add(row, Ui.pill(a, Ui.categoryLabel(v.category), Ui.categoryColor(a, v.category)), 8);
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(x -> a.push(p.label, detail(a, v)));
            return row;
        }

        static View detail(final MainActivity a, final Rules.Verdict v) {
            Theme th = Ui.t(a);
            final Unit.Pkg p = a.unit.packages.get(v.pkg);
            LinearLayout l = Ui.vbox(a);
            Ui.add(l, Ui.header(a, p.label, v.pkg), 12);
            LinearLayout c = Ui.card(a);
            c.addView(Ui.kv(a, "Category", Ui.categoryLabel(v.category)));
            c.addView(Ui.kv(a, "State", p.enabled ? "Enabled" : "Disabled"));
            c.addView(Ui.kv(a, "Purpose", v.purpose.isEmpty() ? (Rules.PROTECTED.equals(v.category) ? v.reason : "Not documented") : v.purpose));
            boolean offer = p.enabled && (Rules.RECOMMENDED.equals(v.category) || Rules.OPTIONAL.equals(v.category) || Rules.ADVANCED.equals(v.category));
            c.addView(Ui.kv(a, "Proposed action", offer ? "Disable for user 0. Nothing is uninstalled." : "None"));
            c.addView(Ui.kv(a, "Confidence", Rules.confidence(v.category)));
            c.addView(Ui.kv(a, "Risk", v.risk.isEmpty() ? (offer ? "Not documented: test the unit afterwards" : "-") : v.risk));
            c.addView(Ui.kv(a, "Restore", "pm enable --user 0 " + v.pkg + "  (Backup & Restore lists it)"));
            if (!v.finding.isEmpty()) c.addView(Ui.kv(a, "Privacy finding", PrivacyScreen.findingLabel(v.finding)));
            if (!Rules.PROTECTED.equals(v.category) && !v.reason.isEmpty() && !v.reason.equals(v.purpose)) {
                Ui.add(c, Ui.dim(a, v.reason), 8);
            } else if (Rules.PROTECTED.equals(v.category)) {
                Ui.add(c, Ui.text(a, "Why it's protected: " + v.reason, 14, th.dim, false), 8);
            }
            Ui.add(l, c, 12);
            Ui.Flow b = new Ui.Flow(a);
            if (offer) {
                final boolean inPlan = plan.contains(v.pkg);
                b.addView(Ui.button(a, inPlan ? "Remove from plan" : "Add to plan", true, x -> {
                    if (inPlan) plan.remove(v.pkg);
                    else plan.add(v.pkg);
                    a.back();
                }));
                Ui.add(b, Ui.button(a, "Disable in Android Settings", false, x -> a.confirm("Disable " + p.label + "?",
                        "Android Settings opens on this app's page. Press Disable there if it's offered.\n\nRisk: "
                                + (v.risk.isEmpty() ? "not documented" : v.risk)
                                + "\n\nIf Settings has no Disable button, use the plan with the computer tool.",
                        () -> a.openAppInfo(v.pkg))), 10);
            } else if (!p.enabled) {
                b.addView(Ui.button(a, "Re-enable in Android Settings", true, x -> a.openAppInfo(v.pkg)));
            } else if (Rules.UNKNOWN.equals(v.category)) {
                b.addView(Ui.button(a, "Help identify it", false, x -> a.openUrl(SUBMIT_URL)));
            }
            Ui.add(l, b, 14);
            if (Rules.UNKNOWN.equals(v.category)) {
                Ui.add(l, Ui.dim(a, "Unknown packages are never offered: on a head unit, an unlabelled vendor app can be the one that talks to the car. "
                        + "If you know what it does, report it so a device profile can say so."), 10);
            }
            return l;
        }

        static void exportPlan(MainActivity a) {
            if (plan.isEmpty()) {
                a.info("Plan is empty", "Open a package and choose Add to plan.");
                return;
            }
            StringBuilder sb = new StringBuilder("# CleanYourHeadUnit plan for " + a.unit.props.get("ro.product.model") + "\n");
            sb.append("# On a computer with adb, from the CleanYourHeadUnit folder (it checks every package again and asks first):\n");
            sb.append("python3 tools/cyhu.py debloat apply --packages ").append(android.text.TextUtils.join(",", plan)).append("\n\n");
            sb.append("# The same with adb alone:\n");
            for (String p : plan) sb.append("adb shell pm disable-user --user 0 ").append(p).append('\n');
            sb.append("\n# Undo:\n");
            for (String p : plan) sb.append("adb shell pm enable --user 0 ").append(p).append('\n');
            File f = a.export("cleanyourheadunit-plan.txt", sb.toString());
            if (f != null) {
                a.info("Plan saved", "Saved to " + f.getAbsolutePath() + "\n\nCopy it to your computer, or share it.");
                a.share("CleanYourHeadUnit plan", sb.toString());
            }
        }
    }

    // =============================================================================================
    static final class Launchers implements MainActivity.Section {
        public String id() { return "launchers"; }
        public String title() { return "Launchers"; }
        public int icon() { return R.drawable.ic_launcher_nav; }

        public View build(MainActivity a) {
            Theme th = Ui.t(a);
            Unit u = a.unit;
            LinearLayout l = Ui.vbox(a);
            l.addView(Ui.header(a, "Launchers", "Keep your factory launcher or try another. Nothing is disabled to force a launcher, and you can always go back."));
            LinearLayout cur = Ui.card(a);
            cur.addView(Ui.text(a, "Home on this unit", 17, th.text, true));
            Ui.add(cur, Ui.kv(a, "Current", homeLabel(a)), 8);
            JSONObject snap = Snapshot.load(a);
            String first = snap == null || snap.isNull("home") ? null : snap.optString("home", null);
            if (first != null) {
                ComponentName fc = ComponentName.unflattenFromString(first);
                Unit.Pkg fp = fc == null ? null : u.packages.get(fc.getPackageName());
                cur.addView(Ui.kv(a, "Original", fp == null ? first : fp.label));
            }
            for (ComponentName cn : u.homes) {
                Unit.Pkg p = u.packages.get(cn.getPackageName());
                cur.addView(Ui.kv(a, "Installed", (p == null ? cn.getPackageName() : p.label) + (cn.equals(u.home) ? "  (current)" : "")));
            }
            Ui.Flow b = new Ui.Flow(a);
            b.addView(Ui.button(a, "Choose Home app", true, v -> chooseHome(a)));
            if (first != null && (u.home == null || !first.equals(u.home.flattenToString()))) {
                Ui.add(b, Ui.button(a, "Back to original", false, v -> chooseHome(a)), 10);
            }
            Ui.add(cur, b, 12);
            Ui.add(l, cur, 14);

            Ui.add(l, Ui.text(a, "Launcher catalog", 19, th.text, true), 20);
            Ui.add(l, Ui.dim(a, "Ordered for your " + u.orientation() + " screen. Listings are not endorsements; compatibility says what has actually been tested."), 4);
            Ui.Grid g = new Ui.Grid(a, 300);
            List<JSONObject> items = Catalog.items(a, "launcher");
            List<JSONObject> fits = new ArrayList<>(), rest = new ArrayList<>();
            for (JSONObject it : items) (fitsHere(u, it) ? fits : rest).add(it);
            fits.addAll(rest);
            for (JSONObject it : fits) g.addView(Listing.card(a, it));
            Ui.add(l, g, 12);
            Ui.add(l, Ui.button(a, "Submit your launcher", false, v -> a.openUrl(SUBMIT_URL)), 16);
            return l;
        }

        static boolean fitsHere(Unit u, JSONObject it) {
            boolean orient = false;
            for (String o : Data.strings(it.optJSONArray("orientations"))) orient |= o.equals(u.orientation());
            JSONObject an = it.optJSONObject("android");
            return orient && (an == null || u.sdk() >= an.optInt("min_sdk"));
        }

        static void chooseHome(MainActivity a) {
            if (Build.VERSION.SDK_INT >= 21) {
                a.openSettings(Settings.ACTION_HOME_SETTINGS, "Press the Home key and pick a launcher, then choose Always.");
            } else {
                a.info("Choose a Home app", "Press the Home key: Android asks which launcher to use. Pick one and choose Always.\n\n"
                        + "To get the question again: Settings → Apps → your current launcher → Clear defaults.");
            }
        }
    }

    // =============================================================================================
    static final class Themes implements MainActivity.Section {
        static final int PICK = 41;

        public String id() { return "themes"; }
        public String title() { return "Themes"; }
        public int icon() { return R.drawable.ic_themes; }

        public View build(MainActivity a) {
            Theme th = Ui.t(a);
            LinearLayout l = Ui.vbox(a);
            l.addView(Ui.header(a, "Themes", "Themes are colours and layout only: no code ever runs from a theme. Each one names the launcher it's for."));
            Ui.add(l, Ui.text(a, "For CleanYourHeadUnit (app and Home)", 19, th.text, true), 18);
            Ui.Grid g = new Ui.Grid(a, 220);
            for (String id : Theme.all(a)) {
                Theme t = Theme.load(a, id);
                if (t != null) g.addView(themeCard(a, t));
            }
            Ui.add(l, g, 10);
            Ui.add(l, accentCard(a), 14);
            Ui.Flow b = new Ui.Flow(a);
            b.addView(Ui.button(a, "Import theme file", false, v -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                try {
                    a.startActivityForResult(i, PICK);
                } catch (android.content.ActivityNotFoundException e) {
                    a.info("No file picker", "This unit has no file picker. Ask for a theme pack in the Community Hub.");
                }
            }));
            Ui.add(b, Ui.button(a, "Export current", false, v -> {
                try {
                    String json = Ui.t(a).export(a);
                    File f = a.export("theme-" + Ui.t(a).id + ".json", json);
                    if (f != null) a.info("Theme exported", f.getAbsolutePath());
                } catch (Exception e) {
                    a.info("Couldn't export", String.valueOf(e.getMessage()));
                }
            }), 10);
            Ui.add(l, b, 14);

            Ui.add(l, Ui.text(a, "For other launchers", 19, th.text, true), 22);
            LinearLayout other = Ui.card(a);
            other.addView(Ui.body(a, "No community theme for a third-party launcher is listed yet. There is no universal theme format across launchers, "
                    + "so each theme here will say which launcher and version it supports."));
            Ui.add(other, Ui.button(a, "Submit a theme", false, v -> a.openUrl(SUBMIT_URL)), 12);
            Ui.add(l, other, 10);
            return l;
        }

        static View themeCard(final MainActivity a, final Theme t) {
            Theme cur = Ui.t(a);
            LinearLayout c = Ui.card(a);
            // A small preview drawn in the theme's own colours.
            LinearLayout prev = Ui.vbox(a);
            prev.setBackground(Ui.round(t.bg, Ui.dp(a, 10)));
            prev.setPadding(Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10));
            prev.addView(Ui.text(a, "12:45", 20, t.text, true));
            LinearLayout dots = Ui.hbox(a);
            for (int col : new int[]{t.accent, t.surface, t.ok, t.warn}) {
                View d = new View(a);
                d.setBackground(Ui.round(col, Ui.dp(a, 6)));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Ui.dp(a, 34), Ui.dp(a, 34));
                p.rightMargin = Ui.dp(a, 6);
                dots.addView(d, p);
            }
            Ui.add(prev, dots, 8);
            TextView dim = Ui.text(a, "Dock " + t.dock, 12, t.dim, false);
            Ui.add(prev, dim, 6);
            c.addView(prev);
            LinearLayout row = Ui.hbox(a);
            LinearLayout names = Ui.vbox(a);
            names.addView(Ui.text(a, t.name, 16, cur.text, true));
            names.addView(Ui.dim(a, "by @" + t.author + (t.dark ? " · dark" : " · light")));
            row.addView(names, Ui.weight(1));
            if (t.id.equals(cur.id)) row.addView(Ui.pill(a, "In use", cur.ok));
            else row.addView(Ui.button(a, "Use", true, v -> {
                Prefs.setTheme(a, t.id);
                a.refresh();
            }));
            Ui.add(c, row, 10);
            return c;
        }
    }

    // =============================================================================================
    static final class Modules implements MainActivity.Section {
        static final String CARD_CATEGORY = "io.github.maxhayim.cleanyourheadunit.category.MODULE_CARD";
        static final String UPSTREAM_CARD_CATEGORY = "com.gathin.tvhome.category.DOCK_CARD";

        public String id() { return "modules"; }
        public String title() { return "Modules"; }
        public int icon() { return R.drawable.ic_modules; }

        public View build(MainActivity a) {
            Theme th = Ui.t(a);
            LinearLayout l = Ui.vbox(a);
            l.addView(Ui.header(a, "Modules", "Optional apps that work well on head units. Each is its own project with its own developer; none is required."));
            Ui.Grid g = new Ui.Grid(a, 300);
            for (JSONObject it : Catalog.items(a, "module")) g.addView(Listing.card(a, it));
            Ui.add(l, g, 14);

            Ui.add(l, Ui.text(a, "Found on this unit", 19, th.text, true), 22);
            Ui.add(l, Ui.dim(a, "Apps that declare a module card (the CleanYourTV card format, or its automotive category)."), 4);
            List<ResolveInfo> found = discovered(a);
            if (found.isEmpty()) {
                Ui.add(l, Ui.body(a, "None installed."), 8);
            }
            for (ResolveInfo ri : found) {
                LinearLayout c = Ui.card(a);
                c.addView(Ui.text(a, String.valueOf(ri.loadLabel(a.getPackageManager())), 16, th.text, true));
                c.addView(Ui.dim(a, ri.activityInfo.packageName));
                final ComponentName cn = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
                Ui.add(c, Ui.button(a, "Open", false, v -> a.startActivity(new Intent(Intent.ACTION_MAIN).setComponent(cn)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))), 8);
                Ui.add(l, c, 8);
            }
            Ui.add(l, Ui.button(a, "Submit your module", false, v -> a.openUrl(SUBMIT_URL)), 16);
            return l;
        }

        static List<ResolveInfo> discovered(Context c) {
            List<ResolveInfo> out = new ArrayList<>();
            Set<String> seen = new TreeSet<>();
            for (String cat : new String[]{CARD_CATEGORY, UPSTREAM_CARD_CATEGORY}) {
                for (ResolveInfo ri : c.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                    if (seen.add(ri.activityInfo.packageName)) out.add(ri);
                }
            }
            return out;
        }
    }

    // =============================================================================================
    static final class Hub implements MainActivity.Section {
        public String id() { return "hub"; }
        public String title() { return "Community"; }
        public int icon() { return R.drawable.ic_community; }

        public View build(MainActivity a) {
            Theme th = Ui.t(a);
            LinearLayout l = Ui.vbox(a);
            l.addView(Ui.header(a, "Community Hub", "Projects that work with head units, and the people who make them. Listing is not endorsement or certification."));
            Ui.add(l, Ui.text(a, "Featured", 19, th.text, true), 18);
            Ui.Grid g = new Ui.Grid(a, 280);
            for (JSONObject it : Catalog.items(a, null)) if (it.optBoolean("featured")) g.addView(Listing.card(a, it));
            Ui.add(l, g, 10);

            Ui.add(l, Ui.text(a, "Developers", 19, th.text, true), 22);
            Ui.Grid devs = new Ui.Grid(a, 240);
            for (JSONObject d : Catalog.developers(a)) {
                LinearLayout c = Ui.card(a);
                c.addView(Ui.text(a, Catalog.credit(a, d.optString("id")), 17, th.text, true));
                c.addView(Ui.dim(a, android.text.TextUtils.join(", ", Data.strings(d.optJSONArray("projects")))));
                if (d.has("note")) Ui.add(c, Ui.text(a, d.optString("note"), 13, th.dim, false), 6);
                Ui.Flow links = new Ui.Flow(a);
                if (!d.isNull("github")) {
                    final String url = "https://github.com/" + d.optString("github");
                    links.addView(Ui.button(a, "GitHub profile", false, v -> a.openUrl(url)));
                }
                if (d.optString("website").startsWith("https://")) {
                    final String site = d.optString("website");
                    links.addView(Ui.button(a, "Website", false, v -> a.openUrl(site)));
                }
                if (links.getChildCount() > 0) Ui.add(c, links, 10);
                devs.addView(c);
            }
            Ui.add(l, devs, 10);

            LinearLayout legend = Ui.card(a);
            legend.addView(Ui.text(a, "What the labels mean", 17, th.text, true));
            Ui.add(legend, Ui.kv(a, "Verified", "Tested on the named head unit, with a report"), 8);
            legend.addView(Ui.kv(a, "Community", "Reported working by users; not checked here"));
            legend.addView(Ui.kv(a, "Untested", "No head-unit report yet"));
            legend.addView(Ui.kv(a, "Planned", "Head-unit support not released yet"));
            Ui.add(l, legend, 16);

            Ui.Flow b = new Ui.Flow(a);
            b.addView(Ui.button(a, "Submit your project", true, v -> a.openUrl(SUBMIT_URL)));
            Ui.add(b, Ui.button(a, "Firmware compatibility", false, v -> a.push("Firmware", Firmware.page(a))), 10);
            Ui.add(b, Ui.button(a, "Check for new releases", false, v -> Releases.check(a)), 10);
            Ui.add(l, b, 16);
            return l;
        }
    }

    // =============================================================================================
    static final class Diagnostics implements MainActivity.Section {
        public String id() { return "diagnostics"; }
        public String title() { return "Diagnostics"; }
        public int icon() { return R.drawable.ic_diagnostics; }

        public View build(MainActivity a) {
            LinearLayout l = Ui.vbox(a);
            l.addView(Ui.header(a, "Diagnostics", "What this unit reports. Export a sanitised report to share when asking for help; nothing is ever uploaded."));
            Ui.add(l, device(a), 14);
            Ui.Flow b = new Ui.Flow(a);
            b.addView(Ui.button(a, "Export sanitised report", true, v -> {
                String rep = Report.build(a);
                File f = a.export("cleanyourheadunit-diagnostics.json", rep);
                if (f != null) {
                    a.info("Report saved", f.getAbsolutePath() + "\n\nSerial numbers, addresses, account names and network names are removed. Read it before you share it.");
                }
            }));
            Ui.add(l, b, 14);
            return l;
        }

        static View device(MainActivity a) {
            Theme th = Ui.t(a);
            Unit u = a.unit;
            LinearLayout l = Ui.vbox(a);
            Ui.Grid g = new Ui.Grid(a, 330);
            LinearLayout id = Ui.card(a);
            id.addView(Ui.text(a, "Identity", 17, th.text, true));
            Ui.add(id, Ui.kv(a, "Manufacturer", u.props.get("ro.product.manufacturer")), 8);
            id.addView(Ui.kv(a, "Brand", u.props.get("ro.product.brand")));
            id.addView(Ui.kv(a, "Model", u.props.get("ro.product.model")));
            id.addView(Ui.kv(a, "Device", u.props.get("ro.product.device")));
            id.addView(Ui.kv(a, "Build", u.props.get("ro.build.display.id")));
            id.addView(Ui.kv(a, "Android", u.android() + " (API " + u.sdk() + ")"));
            id.addView(Ui.kv(a, "Platform", u.props.get("ro.board.platform")));
            id.addView(Ui.kv(a, "Hardware", u.props.get("ro.hardware")));
            id.addView(Ui.kv(a, "ABI", u.abi()));
            for (Map.Entry<String, String> e : u.mcu().entrySet()) id.addView(Ui.kv(a, "MCU (" + e.getKey() + ")", e.getValue()));
            if (u.mcu().isEmpty()) id.addView(Ui.kv(a, "MCU", null));
            g.addView(id);
            LinearLayout hw = Ui.card(a);
            hw.addView(Ui.text(a, "Hardware and packages", 17, th.text, true));
            Ui.add(hw, Ui.kv(a, "Screen", u.width + " × " + u.height), 8);
            hw.addView(Ui.kv(a, "Density", u.dpi + " dpi"));
            hw.addView(Ui.kv(a, "Orientation", cap(u.orientation())));
            hw.addView(Ui.kv(a, "Memory", Unit.mb(u.ramTotal)));
            hw.addView(Ui.kv(a, "Storage", Unit.mb(u.storageTotal)));
            hw.addView(Ui.kv(a, "Packages", u.packages.size() + " (" + u.disabledCount() + " disabled)"));
            hw.addView(Ui.kv(a, "Device profiles", profileNames(u)));
            hw.addView(Ui.kv(a, "Firewall", Firewall.isRunning() ? "On" : "Off"));
            String err = a.getSharedPreferences("firewall", Context.MODE_PRIVATE).getString("lastError", null);
            if (err != null) hw.addView(Ui.kv(a, "Last firewall error", err));
            g.addView(hw);
            l.addView(g);
            return l;
        }

        static String profileNames(Unit u) {
            List<String> n = new ArrayList<>();
            for (JSONObject p : u.profiles) n.add(p.optString("id") + " (" + p.optString("status") + ")");
            return android.text.TextUtils.join(", ", n);
        }
    }

    // =============================================================================================
    static final class Backup implements MainActivity.Section {
        public String id() { return "backup"; }
        public String title() { return "Backup"; }
        public int icon() { return R.drawable.ic_backup; }

        public View build(MainActivity a) {
            Theme th = Ui.t(a);
            LinearLayout l = Ui.vbox(a);
            l.addView(Ui.header(a, "Backup & Restore", "The unit as first seen is kept here. Anything that changed since is listed with how to put it back."));
            Ui.add(l, a.parked(), 12);
            JSONObject snap = Snapshot.load(a);
            LinearLayout s = Ui.card(a);
            s.addView(Ui.text(a, "Snapshot", 17, th.text, true));
            Ui.add(s, Ui.kv(a, "Taken", snap == null ? null : DateFormat.getDateTimeInstance().format(new Date(snap.optLong("time")))), 8);
            s.addView(Ui.kv(a, "Contains", "Package states, Home app, Private DNS, theme"));
            s.addView(Ui.kv(a, "Not included", "Firmware images, app data, MCU settings"));
            Ui.add(l, s, 14);

            final List<Snapshot.Change> ch = Snapshot.compare(a, a.unit, snap);
            Ui.add(l, Ui.text(a, ch.isEmpty() ? "No changes since the snapshot" : ch.size() + " change(s) since the snapshot", 19, th.text, true), 20);
            for (final Snapshot.Change c : ch) {
                LinearLayout r = Ui.card(a);
                r.setOrientation(LinearLayout.HORIZONTAL);
                r.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout t = Ui.vbox(a);
                Unit.Pkg p = a.unit.packages.get(c.target);
                t.addView(Ui.text(a, p != null ? p.label : c.target, 15, th.text, true));
                t.addView(Ui.dim(a, (p != null ? c.target + " · " : "") + "was " + c.before + ", now " + c.now));
                r.addView(t, Ui.weight(1));
                if ("package".equals(c.kind) && p != null) r.addView(Ui.button(a, "App info", false, v -> a.openAppInfo(c.target)));
                if ("home".equals(c.kind)) r.addView(Ui.button(a, "Choose Home", false, v -> Launchers.chooseHome(a)));
                if ("dns".equals(c.kind)) r.addView(Ui.button(a, "Network settings", false, v -> PrivacyScreen.openDnsSettings(a)));
                if ("theme".equals(c.kind)) {
                    final String old = c.before;
                    r.addView(Ui.button(a, "Restore theme", false, v -> { Prefs.setTheme(a, old); a.refresh(); }));
                }
                Ui.add(l, r, 8);
            }
            Ui.Flow b = new Ui.Flow(a);
            b.addView(Ui.button(a, "Export restore instructions", true, v -> {
                String sc = Snapshot.restoreScript(a, ch, snap);
                File f = a.export("cleanyourheadunit-restore.txt", sc);
                if (f != null) a.info("Saved", f.getAbsolutePath() + "\n\n" + sc);
            }));
            Ui.add(b, Ui.button(a, "Turn off firewall", false, v -> {
                FirewallService.stop(a);
                a.info("Firewall", "The local firewall is off. Its settings are kept; nothing else is changed.");
            }), 10);
            Ui.add(b, Ui.button(a, "New snapshot", false, v -> a.confirm("Replace the snapshot?",
                    "The current state becomes the new 'before'. Changes listed above will no longer be shown.",
                    () -> { Snapshot.save(a, a.unit, true); a.show("backup"); })), 10);
            Ui.add(l, b, 16);

            LinearLayout em = Ui.card(a);
            em.addView(Ui.text(a, "If this app won't open", 17, th.text, true));
            Ui.add(em, Ui.body(a, "From a computer with adb: adb shell pm list packages -d lists what is disabled, and adb shell pm enable --user 0 <package> brings each back. "
                    + "RESTORE.md in the project has the full emergency procedure."), 6);
            Ui.add(l, em, 16);
            return l;
        }
    }

    // =============================================================================================
    static final class SettingsScreen implements MainActivity.Section {
        public String id() { return "settings"; }
        public String title() { return "Settings"; }
        public int icon() { return R.drawable.ic_settings; }

        public View build(final MainActivity a) {
            Theme th = Ui.t(a);
            LinearLayout l = Ui.vbox(a);
            l.addView(Ui.header(a, "Settings", null));

            LinearLayout p = Ui.card(a);
            p.addView(Ui.text(a, "Privacy Shield profile", 17, th.text, true));
            Ui.Flow pr = new Ui.Flow(a);
            for (final String prof : new String[]{"standard", "strict", "custom"}) {
                boolean on = prof.equals(Prefs.privacyProfile(a));
                pr.addView(Ui.button(a, cap(prof), on, v -> { Prefs.setPrivacyProfile(a, prof); a.show("settings"); }));
            }
            Ui.add(p, pr, 10);
            Ui.add(p, Ui.dim(a, PrivacyScreen.profileText(Prefs.privacyProfile(a))), 8);
            Ui.add(l, p, 14);

            LinearLayout fw = Ui.card(a);
            fw.addView(Ui.text(a, "Custom firmware", 17, th.text, true));
            final boolean sk = Prefs.declaredFirmware(a).contains("sergey-kozlov");
            Ui.add(fw, Ui.body(a, "Sergey Kozlov's firmware can't be recognised automatically. Turn this on if your unit runs it: nothing becomes Recommended, "
                    + "and launcher changes come with a warning."), 6);
            Ui.add(fw, Ui.button(a, sk ? "This unit runs Sergey Kozlov firmware: On" : "This unit runs Sergey Kozlov firmware: Off", sk, v -> {
                Prefs.declareFirmware(a, "sergey-kozlov", !sk);
                a.unit.matchProfiles(a, Prefs.declaredFirmware(a));
                a.show("settings");
            }), 10);
            Ui.add(l, fw, 12);

            LinearLayout home = Ui.card(a);
            home.addView(Ui.text(a, "CleanYourHeadUnit Home (optional launcher)", 17, th.text, true));
            final boolean on = HomeActivity.enabled(a);
            Ui.add(home, Ui.body(a, on ? "Available as a Home app. Choose it, or your old launcher, in Launchers."
                    : "Off. Turning it on only makes it one of the Home apps you can choose; your launcher stays."), 6);
            Ui.Flow hb = new Ui.Flow(a);
            hb.addView(Ui.button(a, on ? "Turn off" : "Turn on", !on, v -> { HomeActivity.setEnabled(a, !on); a.show("settings"); }));
            if (on) hb.addView(Ui.button(a, "Preview", false, v -> a.startActivity(new Intent(a, HomeActivity.class))));
            Ui.add(home, hb, 10);
            Ui.add(l, home, 12);

            LinearLayout mo = Ui.card(a);
            mo.addView(Ui.text(a, "Display", 17, th.text, true));
            final boolean low = Prefs.lowMotion(a, a.unit.ramTotal);
            Ui.add(mo, Ui.button(a, low ? "Reduced motion: On" : "Reduced motion: Off", low, v -> { Prefs.setLowMotion(a, !low); a.show("settings"); }), 10);
            Ui.add(mo, Ui.dim(a, "On by default on units with less than 2 GB of memory."), 6);
            Ui.add(l, mo, 12);
            Ui.add(l, accentCard(a), 12);

            LinearLayout ab = Ui.card(a);
            ab.addView(Ui.text(a, "About", 17, th.text, true));
            Ui.add(ab, Ui.kv(a, "Version", BuildConfigHolder.version(a)), 8);
            ab.addView(Ui.kv(a, "Licence", "MIT"));
            ab.addView(Ui.kv(a, "Tracking", "None. No account, no analytics, no ads."));
            Ui.add(ab, Ui.body(a, "Derived from CleanYourTV by @Gathin23, which is not affiliated with this project and has not endorsed it. "
                    + "CleanYourHeadUnit by @maxhayim. Outfit typeface: The Outfit Project Authors (OFL 1.1)."), 8);
            Ui.add(ab, Ui.button(a, "Project page", false, v -> a.openUrl(REPO_URL)), 10);
            Ui.add(l, ab, 12);
            return l;
        }
    }

    /** The app's version without enabling BuildConfig. */
    static final class BuildConfigHolder {
        static String version(Context c) {
            try {
                return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
        }
    }
}
