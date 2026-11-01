package io.github.maxhayim.cleanyourheadunit;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import org.json.JSONObject;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowBuild;
import org.robolectric.shadows.ShadowPackageManager;

import java.io.File;
import java.nio.file.Files;
import java.util.Iterator;

/**
 * Loads one of tests/fixtures (the same simulated head units the Python tests use) into
 * Robolectric's package manager, so the real app reads it. Simulated, not a real unit.
 */
final class SimUnit {
    private SimUnit() {}

    static JSONObject fixture(String name) throws Exception {
        File f = new File(System.getProperty("repo.root"), "tests/fixtures/" + name);
        return new JSONObject(new String(Files.readAllBytes(f.toPath()), "UTF-8"));
    }

    static void install(Context c, String name) throws Exception {
        JSONObject fx = fixture(name);
        JSONObject props = fx.getJSONObject("props");
        ShadowBuild.setManufacturer(props.optString("ro.product.manufacturer"));
        ShadowBuild.setBrand(props.optString("ro.product.brand"));
        ShadowBuild.setModel(props.optString("ro.product.model"));
        ShadowBuild.setDevice(props.optString("ro.product.device"));
        ShadowBuild.setHardware(props.optString("ro.board.platform"));
        ShadowBuild.setId(props.optString("ro.build.display.id"));
        PackageManager pm = c.getPackageManager();
        ShadowPackageManager spm = Shadows.shadowOf(pm);
        JSONObject pk = fx.getJSONObject("packages");
        Iterator<String> it = pk.keys();
        String homePkg = fx.optString("home").split("/")[0];
        // The factory Home first, so it resolves as the default.
        if (pk.has(homePkg)) addHome(spm, fx.optString("home"));
        while (it.hasNext()) {
            String n = it.next();
            JSONObject p = pk.getJSONObject(n);
            PackageInfo pi = new PackageInfo();
            pi.packageName = n;
            pi.versionName = p.optString("version", "1.0");
            ApplicationInfo ai = new ApplicationInfo();
            ai.packageName = n;
            ai.name = n;
            ai.flags = (p.optBoolean("system") ? ApplicationInfo.FLAG_SYSTEM : 0) | ApplicationInfo.FLAG_INSTALLED;
            ai.enabled = p.optBoolean("enabled", true);
            ai.nonLocalizedLabel = label(n);
            pi.applicationInfo = ai;
            JSONObject perms = p.optJSONObject("perms");
            if (perms != null) {
                pi.requestedPermissions = new String[perms.length()];
                pi.requestedPermissionsFlags = new int[perms.length()];
                Iterator<String> pi2 = perms.keys();
                for (int i = 0; pi2.hasNext(); i++) {
                    String perm = pi2.next();
                    pi.requestedPermissions[i] = perm;
                    pi.requestedPermissionsFlags[i] = perms.getBoolean(perm) ? PackageInfo.REQUESTED_PERMISSION_GRANTED : 0;
                }
            }
            spm.installPackage(pi);
            if (!ai.enabled) pm.setApplicationEnabledSetting(n, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0);
            String home = p.optString("home", "");
            if (!home.isEmpty() && !home.equals(fx.optString("home"))) addHome(spm, home);
        }
    }

    static void addHome(ShadowPackageManager spm, String flat) {
        ComponentName cn = ComponentName.unflattenFromString(flat);
        ResolveInfo ri = new ResolveInfo();
        ri.activityInfo = new ActivityInfo();
        ri.activityInfo.packageName = cn.getPackageName();
        ri.activityInfo.name = cn.getClassName();
        ri.activityInfo.applicationInfo = new ApplicationInfo();
        ri.activityInfo.applicationInfo.packageName = cn.getPackageName();
        ri.isDefault = true;
        ri.match = IntentFilter.MATCH_CATEGORY_EMPTY;
        spm.addResolveInfoForIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), ri);
    }

    /** Readable app names for the simulated packages. */
    static String label(String pkg) {
        switch (pkg) {
            case "com.syu.ms": return "MCU service";
            case "com.syu.air": return "Navigation bar";
            case "com.syu.example.radio": return "Radio";
            case "com.android.launcher6": return "Launcher";
            case "com.android.launcher3": return "Launcher";
            case "com.adups.fota": return "System update";
            case "com.android.email": return "Email";
            case "com.android.calculator2": return "Calculator";
            case "com.android.egg": return "Android easter egg";
            case "com.android.printspooler": return "Print spooler";
            case "com.android.stk": return "SIM toolkit";
            case "com.example.vendor.promo": return "Featured apps";
            case "com.example.vendor.canbus": return "Car info";
            case "com.example.vendor.weather": return "Weather";
            case "com.google.android.feedback": return "Feedback";
            case "com.spotify.music": return "Spotify";
            case "com.aiconsole.kittlauncher2": return "KITT Launcher.2";
            case "com.aiconsole.knightriderlauncher": return "KITT Launcher";
            case "com.maxhayim.retrodeck": return "RetroDeck";
            case "com.tw.core": return "TWCore";
            case "com.tw.jar1": return "JarService";
            case "com.redstone.ota.ui": return "Wireless update";
            default:
                String[] s = pkg.split("\\.");
                String last = s[s.length - 1];
                return Character.toUpperCase(last.charAt(0)) + last.substring(1);
        }
    }
}
