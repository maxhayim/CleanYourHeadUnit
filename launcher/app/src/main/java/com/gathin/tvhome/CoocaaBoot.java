package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;

/**
 * Coocaa OS (Skyworth, China) stores Home in persist.service.homepage.* and sometimes overwrites
 * those with the literal strings home[0] / home[1] at boot, after which the Home key does nothing.
 * When the homepage shim is installed, put the properties back and open this launcher.
 */
public class CoocaaBoot extends BroadcastReceiver {

    private static final String SHIM = "com.ccos.tv.homepage";
    private static final String SHIM_ACTIVITY = "com.ccos.tv.homepage.LaunchActivity";
    private static final Uri SYSTEM = Uri.parse("content://com.tianci.provider");

    /** Something on Coocaa disables third-party boot receivers. An app may re-enable its own. */
    static void keepEnabled(Context context) {
        ComponentName self = new ComponentName(context, CoocaaBoot.class);
        PackageManager pm = context.getPackageManager();
        try {
            if (pm.getComponentEnabledSetting(self) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(self, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP);
            }
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        keepEnabled(context);
        if (!installed(context, SHIM)) return;
        boolean pkg = setProp("persist.service.homepage.pkg", SHIM);
        boolean cls = setProp("persist.service.homepage.cls", SHIM_ACTIVITY);
        log(context, "boot pkgSet=" + pkg + " clsSet=" + cls
                + " now=" + getProp("persist.service.homepage.pkg"));
        try {
            context.getContentResolver().call(SYSTEM, "COMMON", "gotoHomePage", null);
        } catch (RuntimeException e) {
            log(context, "gotoHomePage " + e);
        }
        try {
            context.startActivity(new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (RuntimeException e) {
            log(context, "start " + e);
        }
    }

    private static boolean installed(Context context, String pkg) {
        try {
            context.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static boolean setProp(String key, String value) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method set = c.getMethod("set", String.class, String.class);
            set.invoke(null, key, value);
            return value.equals(getProp(key));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static String getProp(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class);
            Object v = get.invoke(null, key);
            return v == null ? "" : v.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
    }

    private static void log(Context context, String line) {
        File dir = context.getExternalFilesDir(null);
        if (dir == null) return;
        try (FileWriter w = new FileWriter(new File(dir, "boot.log"), true)) {
            w.write(line + "\n");
        } catch (IOException ignored) {
        }
    }
}
