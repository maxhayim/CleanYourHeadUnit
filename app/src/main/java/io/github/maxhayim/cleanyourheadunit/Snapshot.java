package io.github.maxhayim.cleanyourheadunit;

import android.content.ComponentName;
import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Backups kept by the app: the unit as first seen (every package's enabled state, the Home app,
 * Private DNS, the theme), and comparing the unit now against it.
 *
 * The app can't disable or enable system packages itself (that needs ADB). So restore here means:
 * show exactly what differs from the snapshot, open each app's page in Android Settings, and write
 * the adb commands that put it all back (RESTORE.md has the same procedure).
 */
public final class Snapshot {
    public static final class Change {
        public final String kind, target, before, now;

        Change(String kind, String target, String before, String now) {
            this.kind = kind;
            this.target = target;
            this.before = before;
            this.now = now;
        }
    }

    private Snapshot() {}

    static File file(Context c) {
        return new File(c.getFilesDir(), "snapshot.json");
    }

    public static JSONObject load(Context c) {
        File f = file(c);
        if (!f.exists()) return null;
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int off = 0, n;
            while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            return new JSONObject(new String(b, "UTF-8"));
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    /** Writes the snapshot unless one exists; replace=true only when the owner asks for a new one. */
    public static boolean save(Context c, Unit u, boolean replace) {
        if (!replace && file(c).exists()) return false;
        try {
            JSONObject o = new JSONObject();
            o.put("time", System.currentTimeMillis());
            o.put("android", u.android());
            o.put("android_claimed", u.claimedAndroid());
            JSONObject pk = new JSONObject();
            for (Unit.Pkg p : u.packages.values()) pk.put(p.name, p.enabled);
            o.put("packages", pk);
            o.put("home", u.home == null ? JSONObject.NULL : u.home.flattenToString());
            String[] dns = Privacy.privateDns(c);
            if (dns != null) {
                o.put("dns_mode", dns[0] == null ? JSONObject.NULL : dns[0]);
                o.put("dns_specifier", dns[1] == null ? JSONObject.NULL : dns[1]);
            }
            o.put("theme", Prefs.theme(c));
            File tmp = new File(c.getFilesDir(), "snapshot.json.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(o.toString(2).getBytes("UTF-8"));
            }
            return tmp.renameTo(file(c));
        } catch (JSONException | IOException e) {
            return false;
        }
    }

    public static List<Change> compare(Context c, Unit u, JSONObject snap) {
        List<Change> out = new ArrayList<>();
        if (snap == null) return out;
        JSONObject pk = snap.optJSONObject("packages");
        if (pk != null) {
            Iterator<String> it = pk.keys();
            while (it.hasNext()) {
                String name = it.next();
                boolean was = pk.optBoolean(name);
                Unit.Pkg now = u.packages.get(name);
                if (now == null) {
                    out.add(new Change("package", name, was ? "enabled" : "disabled", "not installed"));
                } else if (now.enabled != was) {
                    out.add(new Change("package", name, was ? "enabled" : "disabled", now.enabled ? "enabled" : "disabled"));
                }
            }
        }
        String home = snap.isNull("home") ? null : snap.optString("home", null);
        String homeNow = u.home == null ? null : u.home.flattenToString();
        if (home != null && !home.equals(homeNow)) out.add(new Change("home", "Home app", home, homeNow == null ? "chooser" : homeNow));
        String[] dns = Privacy.privateDns(c);
        if (dns != null && snap.has("dns_mode")) {
            String m = snap.isNull("dns_mode") ? null : snap.optString("dns_mode");
            String s = snap.isNull("dns_specifier") ? null : snap.optString("dns_specifier");
            if (!eq(m, dns[0]) || !eq(s, dns[1])) out.add(new Change("dns", "Private DNS", label(m, s), label(dns[0], dns[1])));
        }
        String theme = snap.optString("theme", null);
        if (theme != null && !theme.equals(Prefs.theme(c))) out.add(new Change("theme", "Theme", theme, Prefs.theme(c)));
        return out;
    }

    static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    static String label(String mode, String spec) {
        return Privacy.dnsLabel(new String[]{mode, spec});
    }

    static final java.util.regex.Pattern PKG = java.util.regex.Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$");

    /** Plain adb commands that undo the differences. Package names are checked before they are written. */
    public static String restoreScript(Context c, List<Change> changes, JSONObject snap) {
        StringBuilder sb = new StringBuilder();
        sb.append("# CleanYourHeadUnit: put the unit back to the snapshot.\n");
        sb.append("# Run these from a computer with adb, with the vehicle parked. Each line is safe to run twice.\n");
        for (Change ch : changes) {
            switch (ch.kind) {
                case "package":
                    if (!PKG.matcher(ch.target).matches()) break;
                    if ("enabled".equals(ch.before) && "disabled".equals(ch.now)) sb.append("adb shell pm enable --user 0 ").append(ch.target).append('\n');
                    else if ("disabled".equals(ch.before) && "enabled".equals(ch.now)) sb.append("# was disabled before: adb shell pm disable-user --user 0 ").append(ch.target).append('\n');
                    else sb.append("# ").append(ch.target).append(" is no longer installed\n");
                    break;
                case "home":
                    ComponentName cn = ComponentName.unflattenFromString(ch.before);
                    if (cn != null && PKG.matcher(cn.getPackageName()).matches()) sb.append("adb shell cmd package set-home-activity ").append(cn.flattenToString()).append('\n');
                    break;
                case "dns":
                    String m = snap.isNull("dns_mode") ? null : snap.optString("dns_mode");
                    String s = snap.isNull("dns_specifier") ? null : snap.optString("dns_specifier");
                    if (m == null) sb.append("adb shell settings delete global private_dns_mode\n");
                    else sb.append("adb shell settings put global private_dns_mode ").append(m.replaceAll("[^a-z]", "")).append('\n');
                    if (s == null) sb.append("adb shell settings delete global private_dns_specifier\n");
                    else sb.append("adb shell settings put global private_dns_specifier ").append(s.replaceAll("[^A-Za-z0-9.-]", "")).append('\n');
                    break;
                default:
                    sb.append("# ").append(ch.target).append(": change it back in the app (").append(ch.before).append(")\n");
            }
        }
        return sb.toString();
    }
}
