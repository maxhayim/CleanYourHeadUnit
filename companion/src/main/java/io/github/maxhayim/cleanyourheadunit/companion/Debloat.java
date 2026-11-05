package io.github.maxhayim.cleanyourheadunit.companion;

import io.github.maxhayim.cleanyourheadunit.Rules;
import io.github.maxhayim.cleanyourheadunit.companion.adb.AdbConnection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Plan, apply and restore, the same rules as tools/cyhu/debloat.py and restore.py:
 * only `pm disable-user --user 0` and `pm enable --user 0`; Protected, Unknown and the owner's
 * own apps are never in a plan; Advanced only when the owner ticks it; a baseline is saved before
 * the first change; every result is checked against the unit's own package list.
 *
 * The companion is stricter than cyhu in one way: it has no "allow Unknown". A package with no
 * documented purpose can only be disabled from a computer, by name, with cyhu.
 */
public final class Debloat {
    private static final Pattern PACKAGE = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$");

    private Debloat() {}

    public static String disableCommand(String pkg, int sdk) {
        check(pkg);
        return sdk >= 17 ? "pm disable-user --user 0 " + pkg : "pm disable-user " + pkg;
    }

    public static String enableCommand(String pkg, int sdk) {
        check(pkg);
        return sdk >= 17 ? "pm enable --user 0 " + pkg : "pm enable " + pkg;
    }

    /** Package names go into a shell command, so only plain names are accepted. */
    static void check(String pkg) {
        if (pkg == null || pkg.length() > 255 || !PACKAGE.matcher(pkg).matches()) throw new IllegalArgumentException("not a package name: " + pkg);
    }

    /** Can this verdict ever be in a plan? Advanced only with the owner's explicit tick. */
    public static boolean offerable(Rules.Verdict v, boolean advancedAllowed) {
        switch (v.category) {
            case Rules.RECOMMENDED:
            case Rules.OPTIONAL:
                return true;
            case Rules.ADVANCED:
                return advancedAllowed;
            default:
                return false;       // protected, unknown, user
        }
    }

    public static final class Item {
        public final Rules.Verdict verdict;
        public final String command;

        Item(Rules.Verdict v, String command) {
            this.verdict = v;
            this.command = command;
        }
    }

    public static final class Plan {
        public final List<Item> items = new ArrayList<>();
        public final List<String[]> refused = new ArrayList<>();    // {package, why}
    }

    /** Builds a plan from the packages the owner picked. Anything not allowed is refused, with the reason. */
    public static Plan plan(HeadUnit u, Rules rules, List<String> picked, boolean advancedAllowed) {
        Plan plan = new Plan();
        Set<String> seen = new LinkedHashSet<>();
        for (String name : picked) {
            if (!seen.add(name)) continue;
            try {
                check(name);
            } catch (IllegalArgumentException e) {
                plan.refused.add(new String[]{name, "not a package name"});
                continue;
            }
            HeadUnit.Pkg p = u.packages.get(name);
            if (p == null) {
                plan.refused.add(new String[]{name, "not installed on this unit"});
                continue;
            }
            if (!p.enabled) {
                plan.refused.add(new String[]{name, "already disabled"});
                continue;
            }
            Rules.Verdict v = rules.classify(name, p.system);
            if (!offerable(v, advancedAllowed)) {
                String why;
                switch (v.category) {
                    case Rules.PROTECTED: why = "Protected: " + v.reason; break;
                    case Rules.UNKNOWN: why = "Unknown: its purpose isn't documented. The companion never disables these."; break;
                    case Rules.USER: why = "Your own app: uninstall it in the unit's Settings if you don't want it."; break;
                    case Rules.ADVANCED: why = "Advanced: tick \"Include Advanced\" after you've read the risk."; break;
                    default: why = v.category;
                }
                plan.refused.add(new String[]{name, why});
                continue;
            }
            plan.items.add(new Item(v, disableCommand(name, u.sdk)));
        }
        return plan;
    }

    public static final class Outcome {
        public final String pkg, detail;
        public boolean ok;

        Outcome(String pkg, boolean ok, String detail) {
            this.pkg = pkg;
            this.ok = ok;
            this.detail = detail;
        }
    }

    /** Applies a plan: baseline first, then each disable, then a check against the unit's own list. */
    public static List<Outcome> apply(HeadUnit.Shell sh, HeadUnit u, Records rec, Plan plan) throws IOException, JSONException {
        rec.saveBaseline(u);
        List<Outcome> results = new ArrayList<>();
        for (Item it : plan.items) {
            String name = it.verdict.pkg;
            AdbConnection.Result r = sh.run(it.command);
            if (r.rc != 0 || !r.out.contains("disabled")) {
                rec.log("disable-failed", new JSONObject().put("package", name).put("output", tail(r.out)));
                results.add(new Outcome(name, false, r.out.trim().isEmpty() ? "exit " + r.rc : r.out.trim()));
                continue;
            }
            rec.addChange("package", name, "enabled", "disabled", it.verdict.category);
            rec.log("disable", new JSONObject().put("package", name).put("category", it.verdict.category));
            results.add(new Outcome(name, true, r.out.trim()));
        }
        Set<String> disabledNow = new LinkedHashSet<>(HeadUnit.parsePackageList(sh.run("pm list packages -d").out));
        for (Outcome o : results) {
            if (o.ok && !disabledNow.contains(o.pkg)) {
                o.ok = false;
                rec.log("verify-failed", new JSONObject().put("package", o.pkg));
            }
            if (o.ok) u.packages.get(o.pkg).enabled = false;
        }
        return results;
    }

    /** Undoes package changes, newest first. Only puts a package back if it is still how we left it. */
    public static List<Outcome> restore(HeadUnit.Shell sh, Records rec, int sdk, Set<String> only) throws IOException, JSONException {
        List<Outcome> out = new ArrayList<>();
        JSONArray ch = rec.changes();
        for (int i = ch.length() - 1; i >= 0; i--) {
            JSONObject c = ch.getJSONObject(i);
            if (!"applied".equals(c.optString("status")) || !"package".equals(c.optString("kind"))) continue;
            String target = c.getString("target");
            if (only != null && !only.contains(target)) continue;
            String now = state(sh, target);
            if (now == null) {
                rec.mark(i, "gone", "no longer installed");
                out.add(new Outcome(target, false, "no longer installed"));
                continue;
            }
            if (now.equals(c.getString("before"))) {
                rec.mark(i, "restored", "was already back");
                out.add(new Outcome(target, true, "already back"));
                continue;
            }
            if (!now.equals(c.getString("after"))) {
                out.add(new Outcome(target, false, "changed since (now " + now + "); left alone"));
                continue;
            }
            String cmd = "enabled".equals(c.getString("before")) ? enableCommand(target, sdk) : disableCommand(target, sdk);
            AdbConnection.Result r = sh.run(cmd);
            if (r.rc != 0 || !c.getString("before").equals(state(sh, target))) {
                rec.log("restore-failed", new JSONObject().put("package", target).put("output", tail(r.out)));
                out.add(new Outcome(target, false, r.out.trim()));
                continue;
            }
            rec.mark(i, "restored", "");
            rec.log("restore", new JSONObject().put("package", target));
            out.add(new Outcome(target, true, cmd));
        }
        return out;
    }

    static String state(HeadUnit.Shell sh, String pkg) throws IOException {
        if (!HeadUnit.parsePackageList(sh.run("pm list packages").out).contains(pkg)) return null;
        return HeadUnit.parsePackageList(sh.run("pm list packages -d").out).contains(pkg) ? "disabled" : "enabled";
    }

    private static String tail(String s) {
        return s.length() > 300 ? s.substring(s.length() - 300) : s;
    }
}
