package io.github.maxhayim.cleanyourheadunit;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Privacy Shield, in the app: catalogue findings, permission audit, Private DNS.
 * Mirrors tools/cyhu/privacy.py. The permission audit says what apps MAY do (granted permissions),
 * never what they did.
 */
public final class Privacy {
    private Privacy() {}

    public static final String[][] GROUPS = {
            {"Location", "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_BACKGROUND_LOCATION"},
            {"Microphone", "android.permission.RECORD_AUDIO"},
            {"Contacts and messages", "android.permission.READ_CONTACTS", "android.permission.READ_CALL_LOG", "android.permission.READ_SMS"},
            {"Storage", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.MANAGE_EXTERNAL_STORAGE",
                    "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_AUDIO", "android.permission.READ_MEDIA_VIDEO"},
            {"Network", "android.permission.INTERNET"},
            {"Starts by itself", "android.permission.RECEIVE_BOOT_COMPLETED"},
    };

    /** A catalogue entry that is installed here. */
    public static final class Finding {
        public final JSONObject entry;
        public final Unit.Pkg pkg;
        public final Rules.Verdict verdict;

        Finding(JSONObject e, Unit.Pkg p, Rules.Verdict v) {
            entry = e;
            pkg = p;
            verdict = v;
        }
    }

    static int order(String finding) {
        switch (finding) {
            case "malware_indicator": return 0;
            case "confirmed_telemetry": return 1;
            case "advertising": return 2;
            case "suspected_tracking": return 3;
            case "connected_service": return 4;
            default: return 5;
        }
    }

    public static List<Finding> findings(Context c, Unit u) {
        List<Finding> out = new ArrayList<>();
        for (JSONObject e : Data.list(Data.telemetry(c).optJSONArray("packages"))) {
            Unit.Pkg p = u.packages.get(e.optString("package"));
            if (p != null) out.add(new Finding(e, p, u.rules(c).classify(p.name, p.system)));
        }
        java.util.Collections.sort(out, (a, b) -> order(a.entry.optString("finding")) - order(b.entry.optString("finding")));
        return out;
    }

    /** Group name -> packages that hold a granted permission of that group. Enabled packages only. */
    public static Map<String, List<String>> permissions(Context c, Unit u) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String[] g : GROUPS) out.put(g[0], new ArrayList<String>());
        PackageManager pm = c.getPackageManager();
        for (Unit.Pkg p : u.packages.values()) {
            if (!p.enabled) continue;
            PackageInfo pi;
            try {
                pi = pm.getPackageInfo(p.name, PackageManager.GET_PERMISSIONS);
            } catch (PackageManager.NameNotFoundException e) {
                continue;
            }
            if (pi.requestedPermissions == null) continue;
            for (int i = 0; i < pi.requestedPermissions.length; i++) {
                boolean granted = pi.requestedPermissionsFlags == null
                        || (pi.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
                if (!granted) continue;
                for (String[] g : GROUPS) {
                    for (int k = 1; k < g.length; k++) {
                        if (g[k].equals(pi.requestedPermissions[i]) && !out.get(g[0]).contains(p.name)) out.get(g[0]).add(p.name);
                    }
                }
            }
        }
        return out;
    }

    // --- Private DNS (Android 9+) ----------------------------------------------------------------
    public static boolean dnsSupported() {
        return Build.VERSION.SDK_INT >= 28;
    }

    /** {mode, specifier}; null values when Android doesn't let the app read them. */
    public static String[] privateDns(Context c) {
        if (!dnsSupported()) return null;
        ContentResolver cr = c.getContentResolver();
        try {
            return new String[]{Settings.Global.getString(cr, "private_dns_mode"), Settings.Global.getString(cr, "private_dns_specifier")};
        } catch (SecurityException e) {
            return new String[]{null, null};
        }
    }

    /** True only if the owner granted WRITE_SECURE_SETTINGS over ADB. Installing never grants it. */
    public static boolean canWriteDns(Context c) {
        return dnsSupported() && c.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean setPrivateDns(Context c, String hostname) {
        if (!canWriteDns(c)) return false;
        ContentResolver cr = c.getContentResolver();
        try {
            if (hostname == null) {
                Settings.Global.putString(cr, "private_dns_mode", "opportunistic");
            } else {
                if (!hostname.matches("^(?=.{1,253}$)([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}$")) return false;
                Settings.Global.putString(cr, "private_dns_specifier", hostname);
                Settings.Global.putString(cr, "private_dns_mode", "hostname");
            }
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    public static String dnsLabel(String[] dns) {
        if (dns == null) return "Needs Android 9";
        if (dns[0] == null) return "Automatic (Android default)";
        switch (dns[0]) {
            case "off": return "Off";
            case "opportunistic": return "Automatic";
            case "hostname": return dns[1] == null ? "Custom" : dns[1];
            default: return dns[0];
        }
    }

    /** What a Privacy Shield profile proposes on this unit. Nothing is applied from here. */
    public static Map<String, List<String>> profilePlan(Context c, Unit u, String profile) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        out.put("disable", new ArrayList<String>());
        out.put("review", new ArrayList<String>());
        out.put("firewall", new ArrayList<String>());
        Rules r = u.rules(c);
        for (Unit.Pkg p : u.packages.values()) {
            if (p.enabled && Rules.RECOMMENDED.equals(r.classify(p.name, p.system).category)) out.get("disable").add(p.name);
        }
        for (Finding f : findings(c, u)) {
            if (!f.pkg.enabled || Rules.PROTECTED.equals(f.verdict.category)) continue;
            String finding = f.entry.optString("finding");
            if ("strict".equals(profile) && Rules.ADVANCED.equals(f.verdict.category)) out.get("disable").add(f.pkg.name);
            else out.get("review").add(f.pkg.name);
            if ("strict".equals(profile) && ("malware_indicator".equals(finding) || "confirmed_telemetry".equals(finding))) {
                out.get("firewall").add(f.pkg.name);
            }
        }
        return out;
    }
}
