package io.github.maxhayim.cleanyourheadunit;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The local firewall's settings and counters. The firewall is Android's VpnService: everything
 * stays on the unit, nothing is sent to a server of ours (there is none), and HTTPS is never
 * opened. Two modes, because one VPN can't do both without a full network stack:
 *
 *   DNS filter  - every app's DNS lookups go through the firewall; listed domains get "no such
 *                 domain", the rest are forwarded to the network's own DNS servers.
 *   App block   - the chosen apps lose all network access; other apps are not touched at all.
 *                 Needs Android 5.0 (per-app VPN).
 *
 * Counters count what the firewall itself did. They are never estimates.
 */
public final class Firewall {
    public static final String MODE_DNS = "dns", MODE_APPS = "apps";
    static volatile boolean running;
    static volatile String runningMode;

    private Firewall() {}

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("firewall", Context.MODE_PRIVATE);
    }

    public static boolean isRunning() {
        return running;
    }

    public static String mode(Context c) {
        return sp(c).getString("mode", MODE_DNS);
    }

    public static void setMode(Context c, String m) {
        sp(c).edit().putString("mode", m).apply();
    }

    /**
     * The chosen apps minus any that are Protected now (vehicle, core Android, Home). Checked each
     * time the firewall starts, because the list may predate a rule change or come from an imported
     * config file. Protected apps are never cut off from the network.
     */
    public static Set<String> blockableApps(Context c) {
        Set<String> out = new java.util.TreeSet<>();
        Rules r;
        try {
            r = new Rules(Data.protectedRules(c), Data.telemetry(c), java.util.Collections.<org.json.JSONObject>emptyList(),
                    new java.util.HashMap<String, String>());
        } catch (Exception e) {
            return out;   // rules unreadable: block nothing rather than risk a vehicle app
        }
        for (String pkg : blockedApps(c)) {
            if (!Rules.PROTECTED.equals(r.classify(pkg, true).category)) out.add(pkg);
        }
        return out;
    }

    public static Set<String> blockedApps(Context c) {
        return new HashSet<>(sp(c).getStringSet("apps", new HashSet<String>()));
    }

    public static void setBlockedApp(Context c, String pkg, boolean on) {
        Set<String> s = blockedApps(c);
        if (on) s.add(pkg);
        else s.remove(pkg);
        sp(c).edit().putStringSet("apps", s).apply();
    }

    public static Set<String> allowlist(Context c) {
        return new HashSet<>(sp(c).getStringSet("allow", new HashSet<String>()));
    }

    public static void setAllowed(Context c, String domain, boolean on) {
        String d = Dns.normalise(domain);
        if (d == null) return;
        Set<String> s = allowlist(c);
        if (on) s.add(d);
        else s.remove(d);
        sp(c).edit().putStringSet("allow", s).apply();
    }

    // --- counters (observed by the firewall itself) ---------------------------------------------
    public static long counter(Context c, String name) {
        return sp(c).getLong("n_" + name, 0);
    }

    static void add(Context c, String name, long n) {
        SharedPreferences s = sp(c);
        s.edit().putLong("n_" + name, s.getLong("n_" + name, 0) + n).apply();
    }

    public static void resetCounters(Context c) {
        SharedPreferences.Editor e = sp(c).edit();
        for (String k : sp(c).getAll().keySet()) if (k.startsWith("n_") || k.equals("recent")) e.remove(k);
        e.apply();
    }

    static synchronized void recordBlocked(Context c, String domain) {
        try {
            JSONArray a = new JSONArray(sp(c).getString("recent", "[]"));
            JSONArray out = new JSONArray();
            out.put(new JSONObject().put("d", domain).put("t", System.currentTimeMillis()));
            for (int i = 0; i < a.length() && i < 29; i++) out.put(a.get(i));
            sp(c).edit().putString("recent", out.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    public static List<String[]> recent(Context c) {
        List<String[]> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("recent", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new String[]{o.getString("d"), String.valueOf(o.getLong("t"))});
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    // --- block list --------------------------------------------------------------------------------
    static File listFile(Context c) {
        return new File(c.getFilesDir(), "blocklist.txt");
    }

    /** The catalogue's domains (data/telemetry.json) plus whatever list the owner added. */
    public static Dns.Lists loadLists(Context c) {
        Dns.Lists l = new Dns.Lists();
        for (JSONObject d : Data.list(Data.telemetry(c).optJSONArray("domains"))) l.block(d.optString("domain"));
        File f = listFile(c);
        if (f.exists()) {
            try (Reader r = new InputStreamReader(new FileInputStream(f), "UTF-8")) {
                l.read(r);
            } catch (IOException ignored) {
            }
        }
        for (String a : allowlist(c)) l.allow(a);
        return l;
    }

    /** Replaces the owner's list with the domains read from r. Returns how many were read. */
    public static int importList(Context c, Reader r) throws IOException {
        Dns.Lists tmp = new Dns.Lists();
        int n = tmp.read(r);
        File f = listFile(c);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8")) {
            for (String d : tmp.blocked) w.write(d + "\n");
        }
        sp(c).edit().putInt("listSize", tmp.size()).apply();
        return n;
    }

    public static int listSize(Context c) {
        return sp(c).getInt("listSize", 0);
    }

    public static void clearList(Context c) {
        //noinspection ResultOfMethodCallIgnored
        listFile(c).delete();
        sp(c).edit().putInt("listSize", 0).apply();
    }

    /** The configuration as JSON, for export. The block list itself is not included, only its size. */
    public static String export(Context c) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("format", "cleanyourheadunit-firewall-1");
        o.put("mode", mode(c));
        o.put("blocked_apps", new JSONArray(new ArrayList<>(blockedApps(c))));
        o.put("allowlist", new JSONArray(new ArrayList<>(allowlist(c))));
        return o.toString(2);
    }

    public static void importConfig(Context c, String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        if (!"cleanyourheadunit-firewall-1".equals(o.optString("format"))) throw new JSONException("not a CleanYourHeadUnit firewall file");
        String m = o.optString("mode");
        if (MODE_DNS.equals(m) || MODE_APPS.equals(m)) setMode(c, m);
        Set<String> apps = new HashSet<>();
        for (String s : Data.strings(o.optJSONArray("blocked_apps"))) if (Snapshot.PKG.matcher(s).matches()) apps.add(s);
        Set<String> allow = new HashSet<>();
        for (String s : Data.strings(o.optJSONArray("allowlist"))) if (Dns.normalise(s) != null) allow.add(Dns.normalise(s));
        sp(c).edit().putStringSet("apps", apps).putStringSet("allow", allow).apply();
    }

    /** True when another app's VPN is up. Starting ours would replace it: Android allows one VPN at a time. */
    public static boolean otherVpnActive(Context c) {
        if (Build.VERSION.SDK_INT < 21 || running) return false;
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        for (Network n : cm.getAllNetworks()) {
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
        }
        return false;
    }
}
