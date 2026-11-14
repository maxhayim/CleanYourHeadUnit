package io.github.maxhayim.cleanyourheadunit;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/** Drive: speed-based volume, startup actions, day/night brightness, hotspot helper, slowness check. */
final class DriveScreen implements MainActivity.Section {
    public String id() { return "drive"; }
    public String title() { return "Drive"; }
    public int icon() { return R.drawable.ic_drive; }

    public View build(final MainActivity a) {
        LinearLayout l = Ui.vbox(a);
        l.addView(Ui.header(a, "Drive", "Features that make an older unit better, without ADB or root. Nothing here leaves the unit. "
                + "Each one is off until you turn it on."));
        if (Drive.needsService(a)) Ui.add(l, Ui.kv(a, "Background", DriveService.status), 10);
        Ui.add(l, speedCard(a), 14);
        Ui.add(l, startupCard(a), 12);
        Ui.add(l, brightnessCard(a), 12);
        Ui.add(l, hotspotCard(a), 12);
        Ui.add(l, slownessCard(a), 12);
        return l;
    }

    private static void set(MainActivity a, String key, Object v) {
        android.content.SharedPreferences.Editor e = Drive.sp(a).edit();
        if (v == null) e.remove(key);
        else if (v instanceof Boolean) e.putBoolean(key, (Boolean) v);
        else if (v instanceof Integer) e.putInt(key, (Integer) v);
        else e.putString(key, v.toString());
        e.apply();
        Drive.sync(a);
        a.show("drive");
    }

    private static LinearLayout card(MainActivity a, String title, String body) {
        LinearLayout c = Ui.card(a);
        c.addView(Ui.text(a, title, 17, Ui.t(a).text, true));
        Ui.add(c, Ui.body(a, body), 6);
        return c;
    }

    private static Ui.Flow choices(MainActivity a, final String key, int current, int[] values, String[] labels) {
        Ui.Flow f = new Ui.Flow(a);
        for (int i = 0; i < values.length; i++) {
            final int v = values[i];
            f.addView(Ui.button(a, labels[i], v == current, x -> set(a, key, v)));
        }
        return f;
    }

    static void askLocation(MainActivity a) {
        if (Build.VERSION.SDK_INT >= 23) {
            a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 52);
        }
    }

    // --- 1 ------------------------------------------------------------------------------------------

    static View speedCard(final MainActivity a) {
        boolean on = Drive.speedVolume(a);
        boolean metric = Drive.metric(a);
        LinearLayout c = card(a, "Speed-based volume",
                "Turns the music up a step as you speed up, and back down as you slow down, on top of the volume you choose. Uses the unit's GPS.");
        Ui.add(c, Ui.button(a, on ? "Speed-based volume: On" : "Speed-based volume: Off", on, v -> {
            if (!on && !Drive.hasFineLocation(a)) askLocation(a);
            set(a, "speedVolume", !on);
        }), 10);
        if (on) {
            if (!Drive.hasFineLocation(a)) {
                Ui.add(c, Ui.text(a, "Needs location permission for the GPS speed.", 14, Ui.t(a).warn, true), 8);
                Ui.add(c, Ui.button(a, "Allow location", true, v -> askLocation(a)), 6);
            }
            String unit = metric ? "km/h" : "mph";
            int[] starts = metric ? new int[]{30, 40, 50, 60} : new int[]{32, 40, 48, 64};       // stored in km/h
            int[] steps = metric ? new int[]{10, 20, 30} : new int[]{16, 24, 32};
            Ui.add(c, Ui.dim(a, "Start above"), 10);
            Ui.add(c, choices(a, "speedStart", Drive.speedStartKmh(a), starts, labels(starts, metric, unit)), 4);
            Ui.add(c, Ui.dim(a, "One step louder every"), 10);
            Ui.add(c, choices(a, "speedStep", Drive.speedStepKmh(a), steps, labels(steps, metric, unit)), 4);
            Ui.add(c, Ui.dim(a, "At most"), 10);
            Ui.add(c, choices(a, "speedMax", Drive.speedMaxSteps(a), new int[]{2, 4, 6}, new String[]{"+2 steps", "+4 steps", "+6 steps"}), 4);
            Ui.Flow u = new Ui.Flow(a);
            u.addView(Ui.button(a, "km/h", metric, v -> set(a, "metric", true)));
            u.addView(Ui.button(a, "mph", !metric, v -> set(a, "metric", false)));
            Ui.add(c, u, 10);
        }
        Ui.add(c, Ui.dim(a, "It changes Android's media volume. On units where the amplifier's volume is kept by the car-interface chip instead, "
                + "it may not change what you hear: try it parked first by watching the volume on screen."), 8);
        return c;
    }

    private static String[] labels(int[] kmh, boolean metric, String unit) {
        String[] out = new String[kmh.length];
        for (int i = 0; i < kmh.length; i++) out[i] = (metric ? kmh[i] : Math.round(kmh[i] / 1.609f)) + " " + unit;
        return out;
    }

    // --- 2 ------------------------------------------------------------------------------------------

    static View startupCard(final MainActivity a) {
        LinearLayout c = card(a, "Startup actions",
                "After the unit powers up: set the volume, open your music or navigation app, and press Play. Old units often forget all three.");
        String pkg = Drive.startApp(a);
        Ui.add(c, Ui.kv(a, "Open", pkg == null ? "Nothing" : label(a, pkg)), 8);
        Ui.Flow f = new Ui.Flow(a);
        f.addView(Ui.button(a, "Choose app", false, v -> pickApp(a)));
        if (pkg != null) f.addView(Ui.button(a, "None", false, v -> set(a, "startApp", null)));
        Ui.add(c, f, 6);

        final int vol = Drive.startVolume(a), max = Drive.musicMax(a);
        Ui.add(c, Ui.kv(a, "Volume", vol < 0 ? "Leave as it is" : vol + " of " + max), 10);
        Ui.Flow vf = new Ui.Flow(a);
        vf.addView(Ui.button(a, "−", false, v -> set(a, "startVolume", vol <= 0 ? -1 : vol - 1)));
        vf.addView(Ui.button(a, "+", false, v -> set(a, "startVolume", Math.min(max, vol < 0 ? Drive.musicVolume(a) : vol + 1))));
        vf.addView(Ui.button(a, "Use current (" + Drive.musicVolume(a) + ")", false, v -> set(a, "startVolume", Drive.musicVolume(a))));
        if (vol >= 0) vf.addView(Ui.button(a, "Leave as it is", false, v -> set(a, "startVolume", -1)));
        Ui.add(c, vf, 6);

        boolean play = Drive.startPlay(a);
        Ui.add(c, Ui.button(a, play ? "Press Play: On" : "Press Play: Off", play, v -> set(a, "startPlay", !play)), 10);
        Ui.add(c, Ui.dim(a, "Wait after power-up"), 10);
        Ui.add(c, choices(a, "startDelay", Drive.startDelay(a), new int[]{5, 10, 20, 40}, new String[]{"5 s", "10 s", "20 s", "40 s"}), 4);
        boolean wake = Drive.startOnWake(a);
        Ui.add(c, Ui.button(a, wake ? "Also after waking from sleep: On" : "Also after waking from sleep: Off", wake, v -> set(a, "startOnWake", !wake)), 10);
        Ui.add(c, Ui.dim(a, "Many units only sleep when the ignition is off and don't start up again; this catches the screen coming back on "
                + "after at least two minutes off (it keeps a small background service running)."), 6);
        Ui.Flow t = new Ui.Flow(a);
        t.addView(Ui.button(a, "Try it now", false, v -> DriveReceiver.runStartup(a.getApplicationContext(),
                () -> a.info("Startup actions", DriveReceiver.lastStartup.isEmpty() ? "Nothing to do: choose an app, a volume or Play." : DriveReceiver.lastStartup))));
        Ui.add(c, t, 10);
        if (Build.VERSION.SDK_INT >= 29) {
            Ui.add(c, Ui.dim(a, "Android 10 and newer don't let apps open other apps from the background, so opening the app at power-up may not work "
                    + "here; volume and Play still do."), 6);
        }
        if (!DriveReceiver.lastStartup.isEmpty()) Ui.add(c, Ui.dim(a, "Last run: " + DriveReceiver.lastStartup), 6);
        return c;
    }

    static String label(MainActivity a, String pkg) {
        Unit.Pkg p = a.unit == null ? null : a.unit.packages.get(pkg);
        return p == null ? pkg : p.label;
    }

    static void pickApp(final MainActivity a) {
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        final List<String[]> apps = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ResolveInfo r : a.getPackageManager().queryIntentActivities(main, 0)) {
            String p = r.activityInfo.packageName;
            if (p.equals(a.getPackageName()) || !seen.add(p)) continue;
            apps.add(new String[]{String.valueOf(r.loadLabel(a.getPackageManager())), p});
        }
        Collections.sort(apps, (x, y) -> x[0].compareToIgnoreCase(y[0]));
        String[] names = new String[apps.size()];
        for (int i = 0; i < names.length; i++) names[i] = apps.get(i)[0];
        new AlertDialog.Builder(a).setTitle("Open at power-up")
                .setItems(names, (d, w) -> set(a, "startApp", apps.get(w)[1]))
                .setNegativeButton("Cancel", null).show();
    }

    // --- 3 ------------------------------------------------------------------------------------------

    @android.annotation.SuppressLint("InlinedApi")   // the permission page is only shown on Android 6+, where it exists
    static View brightnessCard(final MainActivity a) {
        boolean on = Drive.sunBrightness(a);
        LinearLayout c = card(a, "Day and night brightness",
                "Bright by day, dimmer after sunset, worked out on the unit from where you are (no internet needed).");
        Ui.add(c, Ui.button(a, on ? "Brightness by sunrise and sunset: On" : "Brightness by sunrise and sunset: Off", on, v -> {
            if (!on && Drive.position(a) == null) askLocation(a);
            set(a, "sunBrightness", !on);
        }), 10);
        if (on) {
            if (!Drive.canWriteSettings(a)) {
                Ui.add(c, Ui.text(a, "Needs permission to change system settings (once).", 14, Ui.t(a).warn, true), 8);
                Ui.add(c, Ui.button(a, "Open the permission", true, v -> {
                    try {
                        a.startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + a.getPackageName())));
                    } catch (RuntimeException e) {
                        a.info("Not available", "This unit's Settings has no page for that permission.");
                    }
                }), 6);
            }
            double[] pos = Drive.position(a);
            if (pos == null) {
                Ui.add(c, Ui.text(a, "Waiting for a location: allow location, or start a navigation app once.", 14, Ui.t(a).warn, true), 8);
                Ui.add(c, Ui.button(a, "Allow location", true, v -> askLocation(a)), 6);
            } else {
                long[] rs = Sun.riseSet(pos[0], pos[1], System.currentTimeMillis(), TimeZone.getDefault());
                DateFormat tf = android.text.format.DateFormat.getTimeFormat(a);
                Ui.add(c, Ui.kv(a, "Today", rs == null ? "No sunrise or sunset at this latitude today"
                        : "Sunrise " + tf.format(new Date(rs[0])) + " · sunset " + tf.format(new Date(rs[1]))), 8);
                c.addView(Ui.kv(a, "Now", Drive.applyBrightness(a)));
            }
            Ui.add(c, Ui.dim(a, "By day"), 10);
            Ui.add(c, choices(a, "dayBrightness", Drive.dayBrightness(a), new int[]{60, 80, 100}, new String[]{"60%", "80%", "100%"}), 4);
            Ui.add(c, Ui.dim(a, "At night"), 10);
            Ui.add(c, choices(a, "nightBrightness", Drive.nightBrightness(a), new int[]{10, 20, 30, 50}, new String[]{"10%", "20%", "30%", "50%"}), 4);
        }
        Ui.add(c, Ui.dim(a, "Some units dim the backlight themselves when the headlights come on; this sets Android's brightness, and the two may combine."), 8);
        return c;
    }

    // --- 4 ------------------------------------------------------------------------------------------

    static View hotspotCard(final MainActivity a) {
        String ssid = Drive.hotspot(a);
        LinearLayout c = card(a, "Phone hotspot helper",
                "After power-up, reconnects to your phone's hotspot and keeps a clear note in the notifications when there's no internet.");
        Ui.add(c, Ui.kv(a, "Hotspot", ssid == null ? "Off" : ssid), 8);
        Ui.Flow f = new Ui.Flow(a);
        f.addView(Ui.button(a, "Choose network", ssid == null, v -> pickNetwork(a)));
        if (ssid != null) f.addView(Ui.button(a, "Turn off", false, v -> set(a, "hotspot", null)));
        Ui.add(c, f, 6);
        if (ssid != null) Ui.add(c, Ui.dim(a, DriveService.status), 8);
        Ui.add(c, Ui.dim(a, Drive.canPickWifi()
                ? "It switches Wi-Fi on and picks this saved network when it's in range; it never forgets or changes your other networks."
                : "Android 10 and newer don't let apps pick a Wi-Fi network, so here it only watches the connection and tells you when it's missing."), 8);
        return c;
    }

    @SuppressWarnings("deprecation")
    @android.annotation.SuppressLint("MissingPermission")    // Android 9 and older only; refusals are caught below
    static void pickNetwork(final MainActivity a) {
        final List<String> names = new ArrayList<>();
        if (Drive.canPickWifi()) {
            try {
                WifiManager wm = (WifiManager) a.getApplicationContext().getSystemService(android.content.Context.WIFI_SERVICE);
                if (wm != null && wm.getConfiguredNetworks() != null) {
                    for (WifiConfiguration w : wm.getConfiguredNetworks()) {
                        String s = Drive.ssid(w.SSID);
                        if (s != null && !names.contains(s)) names.add(s);
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }
        names.add("Type a name…");
        new AlertDialog.Builder(a).setTitle("Your phone's hotspot")
                .setItems(names.toArray(new String[0]), (d, w) -> {
                    if (w < names.size() - 1) set(a, "hotspot", names.get(w));
                    else typeNetwork(a);
                })
                .setNegativeButton("Cancel", null).show();
    }

    static void typeNetwork(final MainActivity a) {
        final EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setHint("Hotspot name (connect to it once in Wi-Fi settings first)");
        new AlertDialog.Builder(a).setTitle("Hotspot name").setView(e)
                .setPositiveButton("Save", (d, w) -> {
                    String s = e.getText().toString().trim();
                    if (!s.isEmpty()) set(a, "hotspot", s);
                })
                .setNegativeButton("Cancel", null).show();
    }

    // --- 5 ------------------------------------------------------------------------------------------

    static View slownessCard(final MainActivity a) {
        LinearLayout c = card(a, "Slowness check",
                "Finds what fills the memory and starts itself at power-up, and points to the ones that can be disabled under the rules, "
                        + "through the routes that work on this unit.");
        Ui.add(c, Ui.button(a, "Check now", true, v -> Slowness.open(a)), 10);
        return c;
    }
}
