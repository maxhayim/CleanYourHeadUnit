package io.github.maxhayim.cleanyourheadunit;

import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Privacy Shield: findings, permissions, location, Private DNS and the local firewall. */
final class PrivacyScreen implements MainActivity.Section {
    static final int VPN_CONSENT = 51, PICK_LIST = 52, PICK_CONFIG = 53;

    public String id() { return "privacy"; }
    public String title() { return "Privacy"; }
    public int icon() { return R.drawable.ic_shield; }

    static String findingLabel(String f) {
        switch (f) {
            case "malware_indicator": return "Malware indicator";
            case "confirmed_telemetry": return "Confirmed telemetry";
            case "advertising": return "Advertising";
            case "suspected_tracking": return "Suspected tracking";
            case "connected_service": return "Connected service";
            default: return "Unknown traffic";
        }
    }

    static String profileText(String p) {
        switch (p) {
            case "strict": return "Strict: also proposes the Advanced telemetry packages (each still needs your OK), suggests a filtering Private DNS "
                    + "and the local firewall. Vehicle functions stay protected.";
            case "custom": return "Custom: you choose every package and network rule yourself. Protected packages still can't be chosen.";
            default: return "Standard (recommended): shows verified telemetry, proposes only tested removals, keeps navigation and connected apps, "
                    + "and doesn't filter the network.";
        }
    }

    public View build(MainActivity a) {
        Theme th = Ui.t(a);
        LinearLayout l = Ui.vbox(a);
        LinearLayout head = Ui.hbox(a);
        head.addView(Ui.header(a, "Privacy Shield", "Find software that phones home, and choose what to do about it. Works with any launcher."), Ui.weight(1));
        head.addView(Ui.pill(a, Screens.cap(Prefs.privacyProfile(a)) + " profile", th.accent));
        l.addView(head);

        List<Privacy.Finding> f = Privacy.findings(a, a.unit);
        Ui.Grid stats = new Ui.Grid(a, 150);
        stats.addView(Ui.stat(a, String.valueOf(f.size()), "catalogued findings", f.isEmpty() ? th.ok : th.warn));
        int disabled = 0;
        for (Privacy.Finding x : f) if (!x.pkg.enabled) disabled++;
        stats.addView(Ui.stat(a, String.valueOf(disabled), "of them disabled", th.text));
        stats.addView(Ui.stat(a, Privacy.dnsLabel(Privacy.privateDns(a)).split(" \\(")[0], "Private DNS", th.text));
        stats.addView(Ui.stat(a, Firewall.isRunning() ? Firewall.counter(a, "dns_blocked") + " blocked" : "Off",
                Firewall.isRunning() ? "lookups blocked by the firewall" : "local firewall", Firewall.isRunning() ? th.ok : th.dim));
        Ui.add(l, stats, 16);

        Ui.Grid g = new Ui.Grid(a, 320);
        g.addView(tile(a, "Telemetry detection", f.isEmpty() ? "No catalogued telemetry, advertising or malware package is installed."
                : f.size() + " package(s) with published evidence.", "Review", () -> a.push("Telemetry", telemetry(a))));
        Map<String, List<String>> perms = Privacy.permissions(a, a.unit);
        g.addView(tile(a, "Permissions", perms.get("Location").size() + " apps may use location, " + perms.get("Microphone").size()
                + " the microphone.", "Audit", () -> a.push("Permissions", permissions(a, perms))));
        g.addView(tile(a, "Network protection", "Private DNS and the optional local firewall.", "Open", () -> a.push("Network", network(a))));
        g.addView(tile(a, "Location and GPS", "What receiving GPS means, and what it doesn't.", "Read", () -> a.push("Location", location(a))));
        Ui.add(l, g, 14);

        LinearLayout prof = Ui.card(a);
        prof.addView(Ui.text(a, "Profile proposal", 17, th.text, true));
        Ui.add(prof, Ui.dim(a, profileText(Prefs.privacyProfile(a))), 6);
        Map<String, List<String>> plan = Privacy.profilePlan(a, a.unit, Prefs.privacyProfile(a));
        Ui.add(prof, Ui.kv(a, "Disable", plan.get("disable").isEmpty() ? "Nothing" : TextUtils.join(", ", plan.get("disable"))), 8);
        prof.addView(Ui.kv(a, "Review", plan.get("review").isEmpty() ? "Nothing" : TextUtils.join(", ", plan.get("review"))));
        prof.addView(Ui.kv(a, "Firewall", plan.get("firewall").isEmpty() ? "Not proposed" : "Block " + TextUtils.join(", ", plan.get("firewall"))));
        Ui.add(prof, Ui.button(a, "Change profile in Settings", false, v -> { a.current = "settings"; a.rebuild(); }), 10);
        Ui.add(l, prof, 14);
        Ui.add(l, Ui.dim(a, "No tool can block every kind of tracking. Findings come from a catalogue of packages with published evidence; "
                + "an empty list means none of those is installed, not that nothing on the unit talks to the internet."), 12);
        return l;
    }

    static View tile(MainActivity a, String title, String text, String action, Runnable r) {
        LinearLayout c = Ui.card(a);
        c.addView(Ui.text(a, title, 17, Ui.t(a).text, true));
        Ui.add(c, Ui.dim(a, text), 6);
        TextView b = Ui.button(a, action, false, v -> r.run());
        Ui.add(c, b, 12);
        return c;
    }

    // --- telemetry -----------------------------------------------------------------------------------
    static View telemetry(MainActivity a) {
        Theme th = Ui.t(a);
        LinearLayout l = Ui.vbox(a);
        l.addView(Ui.header(a, "Telemetry detection", "Only packages with published evidence are listed, each with its source. A contact with a maker's server is not called spyware."));
        List<Privacy.Finding> f = Privacy.findings(a, a.unit);
        if (f.isEmpty()) Ui.add(l, Ui.body(a, "None of the catalogued packages is installed on this unit."), 14);
        for (final Privacy.Finding x : f) {
            JSONObject e = x.entry;
            LinearLayout c = Ui.card(a);
            LinearLayout top = Ui.hbox(a);
            LinearLayout names = Ui.vbox(a);
            names.addView(Ui.text(a, e.optString("name"), 17, th.text, true));
            names.addView(Ui.dim(a, x.pkg.name + (x.pkg.enabled ? " · enabled" : " · disabled")));
            top.addView(names, Ui.weight(1));
            String fd = e.optString("finding");
            top.addView(Ui.pill(a, findingLabel(fd), "malware_indicator".equals(fd) || "confirmed_telemetry".equals(fd) ? th.warn : th.accent));
            c.addView(top);
            Ui.add(c, Ui.kv(a, "What it is", e.optString("purpose")), 8);
            c.addView(Ui.kv(a, "Evidence", e.optString("evidence")));
            c.addView(Ui.kv(a, "If disabled", e.optString("risk")));
            c.addView(Ui.kv(a, "Category here", Ui.categoryLabel(x.verdict.category)));
            for (String s : Data.strings(e.optJSONArray("sources"))) Ui.add(c, Ui.text(a, "Source: " + s, 12, th.dim, false), 4);
            Ui.Flow b = new Ui.Flow(a);
            b.addView(Ui.button(a, "Package details", false, v -> a.push(x.pkg.label, Screens.Debloater.detail(a, x.verdict))));
            if (x.pkg.enabled && Build.VERSION.SDK_INT >= 21) {
                b.addView(Ui.button(a, Firewall.blockedApps(a).contains(x.pkg.name) ? "In firewall block list" : "Block its network", false, v -> {
                    Firewall.setBlockedApp(a, x.pkg.name, true);
                    a.info("Added to App block", x.pkg.label + " is on the firewall's App block list. Start the firewall in App block mode under Network protection.");
                }));
            }
            Ui.add(c, b, 10);
            Ui.add(l, c, 12);
        }
        return l;
    }

    // --- permissions ---------------------------------------------------------------------------------
    static View permissions(MainActivity a, Map<String, List<String>> perms) {
        Theme th = Ui.t(a);
        LinearLayout l = Ui.vbox(a);
        l.addView(Ui.header(a, "Permission audit", "What apps are allowed to do, from Android's own records. Allowed is not the same as used."));
        for (Map.Entry<String, List<String>> e : perms.entrySet()) {
            LinearLayout c = Ui.card(a);
            LinearLayout top = Ui.hbox(a);
            top.addView(Ui.text(a, e.getKey(), 17, th.text, true), Ui.weight(1));
            top.addView(Ui.pill(a, e.getValue().size() + " apps", th.accent));
            c.addView(top);
            int shown = 0;
            for (final String pkg : e.getValue()) {
                if (shown++ >= 12) {
                    Ui.add(c, Ui.dim(a, "… and " + (e.getValue().size() - 12) + " more"), 4);
                    break;
                }
                LinearLayout r = Ui.hbox(a);
                Unit.Pkg p = a.unit.packages.get(pkg);
                r.addView(Ui.body(a, p == null ? pkg : p.label), Ui.weight(1));
                TextView open = Ui.text(a, "App info  ›", 14, th.accent, true);
                open.setMinHeight(Ui.dp(a, 44));
                open.setGravity(Gravity.CENTER_VERTICAL);
                open.setClickable(true);
                open.setFocusable(true);
                open.setOnClickListener(v -> a.openAppInfo(pkg));
                r.addView(open);
                Ui.add(c, r, 2);
            }
            Ui.add(l, c, 10);
        }
        Ui.add(l, Ui.dim(a, Build.VERSION.SDK_INT >= 23 ? "Change a permission on the app's page in Android Settings."
                : "Android " + Build.VERSION.RELEASE + " grants permissions at install time; they can't be taken back one by one. Disable or uninstall the app instead."), 12);
        return l;
    }

    // --- location ---------------------------------------------------------------------------------------
    static View location(MainActivity a) {
        LinearLayout l = Ui.vbox(a);
        l.addView(Ui.header(a, "Location and GPS", null));
        String[][] parts = {
                {"Receiving GPS", "The unit hears satellites. That alone sends nothing anywhere. Navigation needs it, so CleanYourHeadUnit never turns GPS off."},
                {"Apps reading location", "An app with the location permission can read where the car is. The permission audit lists them."},
                {"Sending location out", "Only an app with network access can send it. Seeing that needs a network capture; the local firewall counts what it blocks, not what others send."},
                {"The car's own telematics", "Many cars have a separate cellular module outside Android. The head unit can't see or control it, and nothing here does."},
        };
        for (String[] p : parts) {
            LinearLayout c = Ui.card(a);
            c.addView(Ui.text(a, p[0], 17, Ui.t(a).text, true));
            Ui.add(c, Ui.body(a, p[1]), 6);
            Ui.add(l, c, 10);
        }
        Ui.add(l, Ui.dim(a, "CleanYourHeadUnit can reduce what the head unit's apps share. It can't stop all vehicle tracking."), 12);
        return l;
    }

    // --- network -----------------------------------------------------------------------------------------
    static void openDnsSettings(MainActivity a) {
        a.openSettings(Build.VERSION.SDK_INT >= 29 ? "android.settings.NETWORK_AND_INTERNET_SETTINGS" : Settings.ACTION_WIRELESS_SETTINGS,
                "Open Settings → Network → Private DNS on the unit.");
    }

    static View network(final MainActivity a) {
        Theme th = Ui.t(a);
        LinearLayout l = Ui.vbox(a);
        l.addView(Ui.header(a, "Network protection", "DNS filtering stops lookups of listed domains. It doesn't stop apps that use their own DNS or fixed addresses."));

        LinearLayout dns = Ui.card(a);
        dns.addView(Ui.text(a, "Private DNS", 17, th.text, true));
        Ui.add(dns, Ui.kv(a, "Now", Privacy.dnsLabel(Privacy.privateDns(a))), 8);
        if (!Privacy.dnsSupported()) {
            Ui.add(dns, Ui.dim(a, "Needs Android 9. Use the local firewall's DNS filter instead."), 6);
        } else {
            for (JSONObject p : Data.list(Data.dns(a).optJSONArray("providers"))) {
                final String host = p.optString("hostname");
                LinearLayout r = Ui.hbox(a);
                LinearLayout names = Ui.vbox(a);
                names.addView(Ui.text(a, p.optString("name"), 15, th.text, true));
                names.addView(Ui.dim(a, host + " · " + p.optString("blocks")));
                r.addView(names, Ui.weight(1));
                r.addView(Ui.button(a, "Use", false, v -> setDns(a, host)));
                Ui.add(dns, r, 8);
            }
            Ui.Flow b = new Ui.Flow(a);
            b.addView(Ui.button(a, "Back to automatic", false, v -> setDns(a, null)));
            b.addView(Ui.button(a, "Android's DNS settings", false, v -> openDnsSettings(a)));
            Ui.add(dns, b, 12);
            if (!Privacy.canWriteDns(a)) {
                Ui.add(dns, Ui.dim(a, "To let the app set it directly, grant once from a computer: adb shell pm grant io.github.maxhayim.cleanyourheadunit android.permission.WRITE_SECURE_SETTINGS. "
                        + "Without it, the app shows the hostname to type into Android's setting."), 8);
            }
        }
        Ui.add(l, dns, 14);

        LinearLayout fw = Ui.card(a);
        LinearLayout top = Ui.hbox(a);
        top.addView(Ui.text(a, "Local firewall", 17, th.text, true), Ui.weight(1));
        top.addView(Ui.pill(a, Firewall.isRunning() ? "On" : "Off", Firewall.isRunning() ? th.ok : th.dim));
        fw.addView(top);
        Ui.add(fw, Ui.dim(a, "Runs on the unit through Android's VPN service. Nothing is sent to any server of ours, HTTPS is never opened, "
                + "and no certificate is installed. Android allows one VPN at a time."), 6);
        final String mode = Firewall.mode(a);
        Ui.Flow modes = new Ui.Flow(a);
        modes.addView(Ui.button(a, "DNS filter", Firewall.MODE_DNS.equals(mode), v -> { Firewall.setMode(a, Firewall.MODE_DNS); a.back(); a.push("Network", network(a)); }));
        if (Build.VERSION.SDK_INT >= 21) {
            modes.addView(Ui.button(a, "App block (" + Firewall.blockedApps(a).size() + ")", Firewall.MODE_APPS.equals(mode),
                    v -> { Firewall.setMode(a, Firewall.MODE_APPS); a.back(); a.push("Network", network(a)); }));
        }
        Ui.add(fw, modes, 10);
        Ui.add(fw, Ui.dim(a, Firewall.MODE_DNS.equals(mode)
                ? "Every lookup goes through the firewall; listed domains get 'no such domain', the rest go to your network's own DNS. Per-app attribution isn't available for DNS: Android resolves for all apps."
                : "The chosen apps get no network at all; other apps are untouched."), 6);
        if (Firewall.MODE_DNS.equals(mode)) {
            Ui.add(fw, Ui.kv(a, "Block list", (Firewall.listSize(a) + Data.list(Data.telemetry(a).optJSONArray("domains")).size())
                    + " domains (" + Data.list(Data.telemetry(a).optJSONArray("domains")).size() + " from the catalogue)"), 8);
            fw.addView(Ui.kv(a, "Allowlist", Firewall.allowlist(a).isEmpty() ? "Empty" : TextUtils.join(", ", Firewall.allowlist(a))));
        } else {
            fw.addView(Ui.kv(a, "Blocked apps", Firewall.blockedApps(a).isEmpty() ? "None chosen" : TextUtils.join(", ", Firewall.blockedApps(a))));
        }
        fw.addView(Ui.kv(a, "Blocked lookups", Firewall.counter(a, "dns_blocked") + " (counted by the firewall)"));
        fw.addView(Ui.kv(a, "Forwarded lookups", String.valueOf(Firewall.counter(a, "dns_allowed"))));
        fw.addView(Ui.kv(a, "Dropped packets", Firewall.counter(a, "dropped_packets") + " (App block)"));
        Ui.Flow b = new Ui.Flow(a);
        b.addView(Ui.button(a, Firewall.isRunning() ? "Stop" : "Start", !Firewall.isRunning(), v -> {
            if (Firewall.isRunning()) {
                FirewallService.stop(a);
                a.back();
            } else {
                startFirewall(a);
            }
        }));
        if (Firewall.MODE_DNS.equals(mode)) {
            b.addView(Ui.button(a, "Block lists", false, v -> a.push("Block lists", lists(a))));
        } else {
            b.addView(Ui.button(a, "Choose apps", false, v -> a.push("App block", apps(a))));
        }
        b.addView(Ui.button(a, "Reset counters", false, v -> { Firewall.resetCounters(a); a.back(); a.push("Network", network(a)); }));
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
        hs.addView(b);
        Ui.add(fw, hs, 12);
        List<String[]> recent = Firewall.recent(a);
        if (!recent.isEmpty()) {
            Ui.add(fw, Ui.text(a, "Recently blocked", 15, th.text, true), 12);
            for (int i = 0; i < Math.min(8, recent.size()); i++) {
                fw.addView(Ui.kv(a, DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(Long.parseLong(recent.get(i)[1]))), recent.get(i)[0]));
            }
        }
        Ui.add(l, fw, 14);
        Ui.Flow io = new Ui.Flow(a);
        io.addView(Ui.button(a, "Export firewall settings", false, v -> {
            try {
                java.io.File f = a.export("cleanyourheadunit-firewall.json", Firewall.export(a));
                if (f != null) a.info("Exported", f.getAbsolutePath());
            } catch (org.json.JSONException e) {
                a.info("Couldn't export", e.getMessage());
            }
        }));
        io.addView(Ui.button(a, "Import", false, v -> pick(a, PICK_CONFIG)));
        Ui.add(l, io, 12);
        return l;
    }

    static void setDns(MainActivity a, String host) {
        if (Privacy.canWriteDns(a)) {
            a.confirm(host == null ? "Back to automatic Private DNS?" : "Use " + host + "?",
                    "Changes Android's Private DNS for every app. Backup & Restore shows it, and this screen can set it back.",
                    () -> {
                        boolean ok = Privacy.setPrivateDns(a, host);
                        a.info(ok ? "Done" : "Not changed", ok ? "Private DNS: " + Privacy.dnsLabel(Privacy.privateDns(a)) : "Android didn't accept the change.");
                    });
        } else {
            a.info("Set it in Android", host == null ? "In Settings → Network → Private DNS choose Automatic."
                    : "In Settings → Network → Private DNS choose 'Private DNS provider hostname' and type:\n\n" + host);
            openDnsSettings(a);
        }
    }

    static void startFirewall(final MainActivity a) {
        Runnable go = () -> {
            Intent consent = VpnService.prepare(a);
            if (consent != null) a.startActivityForResult(consent, VPN_CONSENT);
            else onConsent(a);
        };
        if (Firewall.otherVpnActive(a)) {
            a.confirm("Another VPN is on", "Android allows one VPN at a time. Starting the firewall disconnects the other VPN.", go);
        } else {
            go.run();
        }
    }

    static void onConsent(MainActivity a) {
        if (Build.VERSION.SDK_INT >= 33 && a.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        FirewallService.start(a);
        a.info("Firewall starting", "Android shows a key icon while it runs. Stop it here or from its notification. It doesn't start again by itself after a restart.");
    }

    static void pick(MainActivity a, int code) {
        try {
            a.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), code);
        } catch (android.content.ActivityNotFoundException e) {
            a.info("No file picker", "This unit has no file picker.");
        }
    }

    static View lists(final MainActivity a) {
        Theme th = Ui.t(a);
        LinearLayout l = Ui.vbox(a);
        l.addView(Ui.header(a, "Block lists", "The catalogue's telemetry domains are always included. Add one maintained list, or your own."));
        for (final JSONObject s : Data.list(Data.dns(a).optJSONArray("blocklists"))) {
            LinearLayout c = Ui.card(a);
            c.addView(Ui.text(a, s.optString("name"), 16, th.text, true));
            c.addView(Ui.dim(a, s.optString("homepage") + " · " + s.optString("license")));
            Ui.add(c, Ui.button(a, "Download and use", false, v -> a.confirm("Download " + s.optString("name") + "?",
                    "From " + s.optString("url") + ". It replaces your current list.", () -> download(a, s.optString("url")))), 10);
            Ui.add(l, c, 10);
        }
        Ui.Flow b = new Ui.Flow(a);
        b.addView(Ui.button(a, "Import a file", false, v -> pick(a, PICK_LIST)));
        b.addView(Ui.button(a, "Clear my list", false, v -> { Firewall.clearList(a); a.info("Cleared", "Only the catalogue's domains are left."); }));
        Ui.add(l, b, 12);
        LinearLayout allow = Ui.card(a);
        allow.addView(Ui.text(a, "Allowlist", 16, th.text, true));
        final EditText in = new EditText(a);
        in.setHint("domain.example");
        in.setTextColor(th.text);
        in.setHintTextColor(th.dim);
        in.setSingleLine(true);
        Ui.add(allow, in, 6);
        Ui.add(allow, Ui.button(a, "Always allow", false, v -> {
            Firewall.setAllowed(a, in.getText().toString(), true);
            a.back();
            a.push("Block lists", lists(a));
        }), 8);
        for (final String d : Firewall.allowlist(a)) {
            LinearLayout r = Ui.hbox(a);
            r.addView(Ui.body(a, d), Ui.weight(1));
            r.addView(Ui.button(a, "Remove", false, v -> { Firewall.setAllowed(a, d, false); a.back(); a.push("Block lists", lists(a)); }));
            Ui.add(allow, r, 6);
        }
        Ui.add(l, allow, 12);
        Ui.add(l, Ui.dim(a, "Changes apply the next time the firewall starts."), 8);
        return l;
    }

    static void download(final MainActivity a, final String url) {
        new Thread(() -> {
            String msg;
            try {
                java.net.HttpURLConnection c = Installer.open(url);
                try (java.io.Reader r = new java.io.InputStreamReader(c.getInputStream(), "UTF-8")) {
                    msg = Firewall.importList(a, r) + " domains read.";
                } finally {
                    c.disconnect();
                }
            } catch (Exception e) {
                msg = "Download failed: " + e.getMessage();
            }
            final String m = msg;
            a.runOnUiThread(() -> a.info("Block list", m));
        }, "cyhu-list").start();
    }

    static View apps(final MainActivity a) {
        Theme th = Ui.t(a);
        LinearLayout l = Ui.vbox(a);
        l.addView(Ui.header(a, "App block", "Chosen apps lose all network access while the firewall runs in App block mode. Protected apps can't be chosen."));
        Set<String> blocked = Firewall.blockedApps(a);
        Map<String, List<String>> perms = Privacy.permissions(a, a.unit);
        Rules r = a.unit.rules(a);
        for (final String pkg : perms.get("Network")) {
            Unit.Pkg p = a.unit.packages.get(pkg);
            if (p == null || pkg.equals(a.getPackageName())) continue;
            boolean prot = Rules.PROTECTED.equals(r.classify(pkg, p.system).category);
            final boolean on = blocked.contains(pkg);
            LinearLayout row = Ui.card(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout n = Ui.vbox(a);
            n.addView(Ui.text(a, p.label, 15, th.text, true));
            n.addView(Ui.dim(a, pkg));
            row.addView(n, Ui.weight(1));
            if (prot) row.addView(Ui.pill(a, "Protected", Ui.categoryColor(a, Rules.PROTECTED)));
            else row.addView(Ui.button(a, on ? "Blocked" : "Block", on, v -> {
                Firewall.setBlockedApp(a, pkg, !on);
                a.back();
                a.push("App block", apps(a));
            }));
            Ui.add(l, row, 8);
        }
        return l;
    }
}
