package io.github.maxhayim.cleanyourheadunit;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Why is this unit slow? Without ADB an app can see memory, storage, and which apps ask to start
 * at power-up; with Direct changes connected it also sees each app's memory (dumpsys meminfo).
 * Suggestions only ever point at apps the rules already offer, through routes that work here.
 */
final class Slowness {
    private Slowness() {}

    static final class Result {
        long ramTotal, ramAvail, storageFree, storageTotal;
        boolean lowMemory;
        final List<String> starters = new ArrayList<>();             // enabled apps that start at power-up
        final List<Rules.Verdict> suggest = new ArrayList<>();       // of those, ones the rules offer
        int startersProtected, startersUnknown, startersOwn;
        final Map<String, Long> memoryKb = new LinkedHashMap<>();    // with ADB: biggest first
        String adbNote;
    }

    /** Blocking (the ADB part); run it off the main thread. */
    static Result check(Context c, Unit u) {
        Result r = new Result();
        r.ramTotal = u.ramTotal;
        r.ramAvail = u.ramAvail;
        r.storageFree = u.storageFree;
        r.storageTotal = u.storageTotal;
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            r.lowMemory = mi.lowMemory;
            if (mi.totalMem > 0) {
                r.ramTotal = mi.totalMem;
                r.ramAvail = mi.availMem;
            }
        }
        Set<String> boot = bootStarters(c);
        Rules rules = u.rules(c);
        for (String pkg : boot) {
            Unit.Pkg p = u.packages.get(pkg);
            if (p == null || !p.enabled || pkg.equals(c.getPackageName())) continue;
            r.starters.add(pkg);
            Rules.Verdict v = rules.classify(pkg, p.system);
            switch (v.category) {
                case Rules.RECOMMENDED:
                case Rules.OPTIONAL:
                case Rules.ADVANCED:
                    r.suggest.add(v);
                    break;
                case Rules.PROTECTED: r.startersProtected++; break;
                case Rules.UNKNOWN: r.startersUnknown++; break;
                default: r.startersOwn++;
            }
        }
        if (LocalAdb.connected()) {
            try {
                r.memoryKb.putAll(parseMeminfo(LocalAdb.shell("dumpsys meminfo").out, 10));
            } catch (Exception e) {
                r.adbNote = "Couldn't read per-app memory: " + e.getMessage();
            }
        } else {
            r.adbNote = "Per-app memory needs Direct changes (Diagnostics). Without it, Android doesn't let one app see another's memory.";
        }
        return r;
    }

    /** Packages with a receiver for power-up. Any app can see this; no permission needed. */
    static Set<String> bootStarters(Context c) {
        Set<String> out = new LinkedHashSet<>();
        List<ResolveInfo> l = c.getPackageManager().queryBroadcastReceivers(new Intent(Intent.ACTION_BOOT_COMPLETED), PackageManager.GET_DISABLED_COMPONENTS);
        if (l != null) for (ResolveInfo ri : l) if (ri.activityInfo != null && ri.activityInfo.enabled) out.add(ri.activityInfo.packageName);
        return out;
    }

    private static final Pattern PSS = Pattern.compile("^\\s*([\\d,]+)K: (\\S+) \\(pid \\d+");

    /** The "Total PSS by process" section of dumpsys meminfo, biggest first. */
    static Map<String, Long> parseMeminfo(String out, int limit) {
        Map<String, Long> m = new LinkedHashMap<>();
        boolean in = false;
        for (String line : out.split("\n")) {
            if (line.startsWith("Total PSS by process")) {
                in = true;
                continue;
            }
            if (!in) continue;
            Matcher x = PSS.matcher(line);
            if (!x.find()) {
                if (line.trim().isEmpty() && !m.isEmpty()) break;
                continue;
            }
            m.put(x.group(2), Long.parseLong(x.group(1).replace(",", "")));
            if (m.size() >= limit) break;
        }
        return m;
    }

    static String verdict(Result r) {
        double freePct = r.ramTotal > 0 ? 100.0 * r.ramAvail / r.ramTotal : 100;
        StringBuilder s = new StringBuilder();
        if (r.lowMemory || freePct < 15) s.append("Memory is nearly full, which is the usual reason an old unit feels slow. ");
        else if (freePct < 30) s.append("Memory is tight. ");
        else s.append("Memory looks fine right now. ");
        if (r.storageTotal > 0 && r.storageFree < r.storageTotal / 10) s.append("Storage is over 90% full: free some space. ");
        if (!r.suggest.isEmpty()) s.append(r.suggest.size()).append(" app(s) that start at power-up can be disabled under the rules (below).");
        else s.append("None of the apps that start at power-up are ones the rules offer to disable.");
        return s.toString().trim();
    }

    // --- the screen ---------------------------------------------------------------------------------

    static void open(final MainActivity a) {
        Direct.work(a, "Checking…", () -> {
            final Result r = check(a.getApplicationContext(), a.unit);
            a.runOnUiThread(() -> a.push("Slowness check", view(a, r)));
            return null;
        }, null);
    }

    static View view(final MainActivity a, Result r) {
        Theme th = Ui.t(a);
        LinearLayout l = Ui.vbox(a);
        LinearLayout c = Ui.card(a);
        c.addView(Ui.text(a, "What the unit shows", 17, th.text, true));
        Ui.add(c, Ui.body(a, verdict(r)), 6);
        Ui.add(c, Ui.kv(a, "Memory free", r.ramTotal > 0 ? mb(r.ramAvail) + " of " + mb(r.ramTotal) + (r.lowMemory ? " (Android says low)" : "") : "Not reported"), 8);
        c.addView(Ui.kv(a, "Storage free", r.storageTotal > 0 ? mb(r.storageFree) + " of " + mb(r.storageTotal) : "Not reported"));
        c.addView(Ui.kv(a, "Apps that start at power-up", r.starters.size() + " (Protected " + r.startersProtected + ", Unknown " + r.startersUnknown
                + ", yours " + r.startersOwn + ", can be disabled " + r.suggest.size() + ")"));
        Ui.add(l, c, 10);

        if (!r.suggest.isEmpty()) {
            LinearLayout s = Ui.card(a);
            s.addView(Ui.text(a, "Can be disabled", 17, th.text, true));
            Ui.add(s, Ui.dim(a, "These start at power-up and the rules offer them. Open one to see its risk and how to disable it on this unit "
                    + (LocalAdb.connected() ? "(Disable now works: Direct changes is connected)." : "(Disable in Android Settings, or Direct changes if this unit has ADB).")), 4);
            for (final Rules.Verdict v : r.suggest) {
                Unit.Pkg p = a.unit.packages.get(v.pkg);
                String label = p == null ? v.pkg : p.label;
                Ui.add(s, Ui.button(a, label + " · " + Ui.categoryLabel(v.category), false, x -> a.push(label, Screens.Debloater.detail(a, v))), 8);
            }
            Ui.add(l, s, 12);
        }

        LinearLayout m = Ui.card(a);
        m.addView(Ui.text(a, "Biggest apps in memory", 17, th.text, true));
        if (r.memoryKb.isEmpty()) {
            Ui.add(m, Ui.dim(a, r.adbNote == null ? "Nothing reported." : r.adbNote), 6);
        } else {
            for (Map.Entry<String, Long> e : r.memoryKb.entrySet()) m.addView(Ui.kv(a, e.getKey(), mb(e.getValue() * 1024)));
            Ui.add(m, Ui.dim(a, "Only apps the rules offer can be disabled; the others may be what runs the car."), 8);
        }
        Ui.add(l, m, 12);
        return l;
    }

    static String mb(long bytes) {
        return bytes >= (1L << 30) ? String.format(java.util.Locale.ROOT, "%.1f GB", bytes / (double) (1L << 30)) : (bytes >> 20) + " MB";
    }
}
