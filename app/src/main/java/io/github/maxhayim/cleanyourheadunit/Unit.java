package io.github.maxhayim.cleanyourheadunit;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;

import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * What the app can see of the head unit from inside, without root or ADB: Build fields, the
 * public system properties, installed packages and their enabled state, Home apps, display,
 * memory and storage. A value Android doesn't give is null and shown as "Not reported".
 */
public final class Unit {
    public static final class Pkg {
        public final String name, label;
        public final boolean system, enabled;
        public final String version;

        Pkg(String name, String label, boolean system, boolean enabled, String version) {
            this.name = name;
            this.label = label;
            this.system = system;
            this.enabled = enabled;
            this.version = version;
        }
    }

    public final Map<String, String> props = new TreeMap<>();
    public final Map<String, Pkg> packages = new TreeMap<>();
    public final List<ComponentName> homes = new ArrayList<>();
    public ComponentName home;
    public int width, height, dpi;
    public long ramTotal, ramAvail, storageTotal, storageFree;
    public final List<JSONObject> profiles = new ArrayList<>();
    private Rules rules;

    static final String[] PROPS = {"ro.board.platform", "ro.hardware", "ro.product.board", "ro.build.display.id",
            "ro.build.description", "ro.product.device", "ro.product.model", "ro.product.manufacturer",
            "ro.product.brand", "ro.build.version.release", "ro.build.version.sdk", "ro.product.cpu.abi"};
    /** Property names that some firmware uses for an MCU version. Only shown when the unit has them. */
    static final String[] MCU_PROPS = {"ro.build.mcu.version", "persist.sys.mcu.version", "sys.mcu.version",
            "persist.sys.fyt.mcu.version", "ro.mcu.version", "persist.mcu.version"};

    public static Unit scan(Context c) {
        Unit u = new Unit();
        u.props.put("ro.product.manufacturer", Build.MANUFACTURER);
        u.props.put("ro.product.brand", Build.BRAND);
        u.props.put("ro.product.model", Build.MODEL);
        u.props.put("ro.product.device", Build.DEVICE);
        u.props.put("ro.product.board", Build.BOARD);
        u.props.put("ro.hardware", Build.HARDWARE);
        u.props.put("ro.build.display.id", Build.DISPLAY);
        u.props.put("ro.build.version.release", Build.VERSION.RELEASE);
        u.props.put("ro.build.version.sdk", String.valueOf(Build.VERSION.SDK_INT));
        for (String k : PROPS) putProp(u.props, k);
        for (String k : MCU_PROPS) putProp(u.props, k);

        PackageManager pm = c.getPackageManager();
        for (ApplicationInfo ai : pm.getInstalledApplications(PackageManager.GET_META_DATA | getUninstalledFlag())) {
            if ((ai.flags & ApplicationInfo.FLAG_INSTALLED) == 0) continue;
            String version = null;
            try {
                version = pm.getPackageInfo(ai.packageName, 0).versionName;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            CharSequence label = ai.loadLabel(pm);
            u.packages.put(ai.packageName, new Pkg(ai.packageName, label == null ? ai.packageName : label.toString(),
                    (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0,
                    ai.enabled, version));
        }
        Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo ri : pm.queryIntentActivities(homeIntent, 0)) {
            u.homes.add(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
        }
        ResolveInfo def = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (def != null && def.activityInfo != null && !"android".equals(def.activityInfo.packageName)) {
            u.home = new ComponentName(def.activityInfo.packageName, def.activityInfo.name);
        }
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        u.width = dm.widthPixels;
        u.height = dm.heightPixels;
        u.dpi = dm.densityDpi;
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            u.ramTotal = mi.totalMem;
            u.ramAvail = mi.availMem;
        }
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            u.storageTotal = fs.getBlockCountLong() * fs.getBlockSizeLong();
            u.storageFree = fs.getAvailableBlocksLong() * fs.getBlockSizeLong();
        } catch (RuntimeException ignored) {
        }
        u.matchProfiles(c, Prefs.declaredFirmware(c));
        return u;
    }

    @SuppressWarnings("deprecation")
    private static int getUninstalledFlag() {
        return Build.VERSION.SDK_INT >= 24 ? PackageManager.MATCH_DISABLED_COMPONENTS : PackageManager.GET_DISABLED_COMPONENTS;
    }

    private static void putProp(Map<String, String> props, String key) {
        String v = sysProp(key);
        if (v != null && !v.isEmpty()) props.put(key, v);
    }

    /** android.os.SystemProperties is not public; reading it is best effort and may return null. */
    static String sysProp(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method get = sp.getMethod("get", String.class);
            Object v = get.invoke(null, key);
            return v == null ? null : v.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    public void matchProfiles(Context c, Set<String> declared) {
        profiles.clear();
        JSONObject generic = null;
        for (JSONObject p : Data.profiles(c)) {
            if (!Rules.matches(p, props, packages.keySet(), declared)) continue;
            if ("generic-android".equals(p.optString("id"))) generic = p;
            else profiles.add(p);
        }
        if (generic != null) profiles.add(generic);
        rules = null;
    }

    public Rules rules(Context c) {
        if (rules == null) {
            Map<String, String> extra = new LinkedHashMap<>();
            for (ComponentName h : homes) extra.put(h.getPackageName(), "Can be the Home app on this unit. Home apps are never disabled.");
            if (home != null) extra.put(home.getPackageName(), "The current Home app. It is never disabled.");
            try {
                rules = new Rules(Data.protectedRules(c), Data.telemetry(c), profiles, extra);
            } catch (org.json.JSONException e) {
                throw new IllegalStateException(e);
            }
        }
        return rules;
    }

    /** From the scanned properties (scan fills them from Build), so a report or test describes the unit it read. */
    public int sdk() {
        try {
            return Integer.parseInt(props.get("ro.build.version.sdk"));
        } catch (RuntimeException e) {
            return Build.VERSION.SDK_INT;
        }
    }

    /** The Android version the unit really runs (from its API level, which can't be faked). */
    public String android() {
        return AndroidVersion.real(sdk(), claimedAndroid());
    }

    /** What the unit's Settings say. Budget units often show a bigger number than they run. */
    public String claimedAndroid() {
        String v = props.get("ro.build.version.release");
        return v == null || v.isEmpty() ? Build.VERSION.RELEASE : v;
    }

    public boolean androidSpoofed() {
        return AndroidVersion.spoofed(sdk(), claimedAndroid());
    }

    /** "8.1 (Settings say 13)" when they disagree, otherwise "8.1". */
    public String androidLabel() {
        return androidSpoofed() ? android() + " (Settings say " + claimedAndroid() + ")" : android();
    }

    public String chipset() {
        for (String k : new String[]{"ro.board.platform", "ro.hardware", "ro.product.board"}) {
            String v = props.get(k);
            if (v != null && !v.isEmpty() && !"unknown".equals(v)) return v;
        }
        return null;
    }

    public Map<String, String> mcu() {
        Map<String, String> out = new TreeMap<>();
        for (String k : MCU_PROPS) if (props.containsKey(k)) out.put(k, props.get(k));
        return out;
    }

    public String firmwareFamily() {
        for (JSONObject p : profiles) if (!"generic-android".equals(p.optString("id"))) return p.optString("firmware_family");
        return "unknown";
    }

    public JSONObject mainProfile() {
        return profiles.isEmpty() ? null : profiles.get(0);
    }

    public String orientation() {
        return height > width ? "portrait" : "landscape";
    }

    public String abi() {
        String v = props.get("ro.product.cpu.abi");
        if (v == null || v.isEmpty()) v = Build.VERSION.SDK_INT >= 21 && Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : null;
        return v == null || v.isEmpty() || "unknown".equals(v) ? null : v;
    }

    public int disabledCount() {
        int n = 0;
        for (Pkg p : packages.values()) if (!p.enabled) n++;
        return n;
    }

    public Set<String> enabledSet() {
        Set<String> s = new HashSet<>();
        for (Pkg p : packages.values()) if (p.enabled) s.add(p.name);
        return s;
    }

    public static String mb(long bytes) {
        if (bytes <= 0) return null;
        if (bytes >= 1L << 30) return String.format(Locale.US, "%.1f GB", bytes / (double) (1L << 30));
        return (bytes >> 20) + " MB";
    }
}
