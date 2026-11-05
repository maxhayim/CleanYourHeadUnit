package io.github.maxhayim.cleanyourheadunit.companion;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import io.github.maxhayim.cleanyourheadunit.Data;
import io.github.maxhayim.cleanyourheadunit.Rules;
import io.github.maxhayim.cleanyourheadunit.companion.adb.UsbTransport;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CleanYourHeadUnit Companion: this phone does what the computer tool does, over USB (the phone
 * is the host, with an OTG cable) or Wi-Fi. Over USB it can also be the bridge: it asks the
 * unit's adbd to listen on Wi-Fi, so older units without Wireless debugging can then be reached
 * without a cable.
 */
public class MainActivity extends Activity {
    static final String ACTION_USB_PERMISSION = "io.github.maxhayim.cleanyourheadunit.companion.USB_PERMISSION";

    static final int BG = 0xFF0B0E11, SURFACE = 0xFF161B21, TEXT = 0xFFE8EDF2, DIM = 0xFF8A96A3, ACCENT = 0xFF8FA8FF,
            WARN = 0xFFF2A93B, OK = 0xFF4CC38A, BAD = 0xFFEF5350;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout page;
    private TextView status;
    private String screen = "connect";
    private final Set<String> picked = new LinkedHashSet<>();
    private boolean includeAdvanced;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        root.setPadding(p, p, p, p);
        TextView title = text("CleanYourHeadUnit Companion", 22, TEXT, true);
        root.addView(title);
        status = text("", 14, DIM, false);
        root.addView(status, lp(6));
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, lp(10));
        sv.addView(root);
        setContentView(sv);
        show(Session.current() == null ? "connect" : "unit");
        handleAttach(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleAttach(intent);
    }

    private void handleAttach(Intent i) {
        if (i == null) return;
        if (ACTION_USB_PERMISSION.equals(i.getAction())) {
            // Android's answer to our permission request comes straight back to this activity.
            UsbDevice d = i.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (d != null && i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) connectUsb(d);
            else say("USB permission wasn't given.", BAD);
            return;
        }
        if (!UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(i.getAction())) return;
        UsbDevice d = i.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (d != null && UsbTransport.adbInterface(d) != null) askUsb(d);
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if ("plan".equals(screen) || "restore".equals(screen) || "packages".equals(screen)) show("unit");
        else super.onBackPressed();
    }

    // --- screens --------------------------------------------------------------------------------

    void show(String s) {
        screen = s;
        page.removeAllViews();
        switch (s) {
            case "unit": unitScreen(); break;
            case "packages": packagesScreen(); break;
            case "plan": planScreen(); break;
            case "restore": restoreScreen(); break;
            default: connectScreen();
        }
    }

    private void connectScreen() {
        say("Not connected. Park the vehicle first.", DIM);
        LinearLayout usb = card();
        usb.addView(text("USB (the bridge)", 17, TEXT, true));
        usb.addView(dim("Connect this phone to the head unit with an OTG cable or adapter. The unit needs USB debugging on, and its USB port must be one that accepts a computer. The first time, the unit asks \"Allow USB debugging?\": tap Allow."), lp(4));
        UsbManager um = (UsbManager) getSystemService(USB_SERVICE);
        int found = 0;
        if (um != null) {
            for (final UsbDevice d : um.getDeviceList().values()) {
                if (UsbTransport.adbInterface(d) == null) continue;
                found++;
                String name = d.getProductName() != null ? d.getProductName() : d.getDeviceName();
                usb.addView(button("Connect to " + name, true, v -> askUsb(d)), lp(10));
            }
        }
        if (found == 0) usb.addView(dim("No head unit with USB debugging is plugged in."), lp(8));
        usb.addView(button("Look again", false, v -> show("connect")), lp(8));
        page.addView(usb);

        LinearLayout wifi = card();
        wifi.addView(text("Wi-Fi", 17, TEXT, true));
        wifi.addView(dim("For a unit whose ADB already listens on the network: after the bridge (port 5555), or one with a network-ADB switch. Phone and unit must be on the same Wi-Fi. Android 11+ wireless-debugging pairing is not supported yet."), lp(4));
        final EditText addr = new EditText(this);
        addr.setHint("10.0.0.115 or 10.0.0.115:5555");
        addr.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addr.setText(Store.lastAddress(this));
        addr.setTextColor(TEXT);
        addr.setHintTextColor(DIM);
        wifi.addView(addr, lp(8));
        wifi.addView(button("Connect over Wi-Fi", true, v -> connectWifi(addr.getText().toString().trim())), lp(8));
        page.addView(wifi, lp(12));

        LinearLayout about = card();
        about.addView(text("What this app does", 17, TEXT, true));
        about.addView(dim("It reads the unit, sorts every app with CleanYourHeadUnit's rules, and only ever disables apps that are documented as safe to disable, with a backup first and a one-tap restore. Vehicle apps (MCU, CAN bus, doors, cameras, radio, Bluetooth calling, climate, steering-wheel keys, updaters, Home) are always protected. Apps with no documented purpose are never offered. Nothing is uninstalled. No account, no analytics; it only talks to the head unit."), lp(4));
        page.addView(about, lp(12));
    }

    private void askUsb(UsbDevice d) {
        UsbManager um = (UsbManager) getSystemService(USB_SERVICE);
        if (um == null) return;
        if (um.hasPermission(d)) {
            connectUsb(d);
            return;
        }
        int flags = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
        Intent i = new Intent(this, MainActivity.class).setAction(ACTION_USB_PERMISSION);
        um.requestPermission(d, PendingIntent.getActivity(this, 0, i, flags));
    }

    private void connectUsb(final UsbDevice d) {
        say("Connecting over USB…", DIM);
        final UsbManager um = (UsbManager) getSystemService(USB_SERVICE);
        work(() -> {
            Session.usb(this, um, d, fp -> say("Tap \"Allow\" on the head unit's screen. This phone's key: " + fp, WARN));
            main.post(() -> show("unit"));
        });
    }

    private void connectWifi(String a) {
        if (a.isEmpty()) {
            say("Type the unit's IP address.", BAD);
            return;
        }
        String host = a;
        int port = Session.BRIDGE_PORT;
        int colon = a.lastIndexOf(':');
        if (colon > 0 && a.indexOf(':') == colon) {
            host = a.substring(0, colon);
            try {
                port = Integer.parseInt(a.substring(colon + 1));
            } catch (NumberFormatException e) {
                say("That port isn't a number.", BAD);
                return;
            }
        }
        final String h = host;
        final int p = port;
        Store.setLastAddress(this, a);
        say("Connecting to " + h + ":" + p + "…", DIM);
        work(() -> {
            Session.wifi(this, h, p, fp -> say("Tap \"Allow\" on the head unit's screen. This phone's key: " + fp, WARN));
            main.post(() -> show("unit"));
        });
    }

    private void unitScreen() {
        final Session s = Session.current();
        if (s == null) {
            show("connect");
            return;
        }
        HeadUnit u = s.unit;
        say("Connected over " + s.address + ".", OK);
        LinearLayout c = card();
        c.addView(text(u.props.getOrDefault("ro.product.manufacturer", "") + " " + u.model(), 18, TEXT, true));
        c.addView(kv("Android", "Android " + u.realAndroid() + " (API " + u.sdk + ")"), lp(6));
        if (u.androidVersionSpoofed()) {
            c.addView(colored("The unit's settings say Android " + u.claimedAndroid() + ", but it runs Android " + u.realAndroid() + ". That's common on budget head units: features need the real version.", WARN), lp(4));
        }
        c.addView(kv("Build", u.props.getOrDefault("ro.build.display.id", "Not reported")));
        c.addView(kv("Chipset", u.chipset() == null ? "Not reported" : u.chipset()));
        c.addView(kv("Screen", u.size == null ? "Not reported" : u.size + (u.density == null ? "" : " · " + u.density + " dpi")));
        c.addView(kv("Memory", u.memTotalKb > 0 ? (u.memTotalKb / 1024) + " MB" : "Not reported"));
        c.addView(kv("Firmware family", u.firmwareFamily()));
        c.addView(kv("Home app", u.home == null ? "Not reported" : u.home.split("/")[0]));
        c.addView(kv("Packages", u.packages.size() + " · " + u.disabledCount() + " disabled"));
        page.addView(c);

        // Firmware that can't be recognised automatically (e.g. Sergey Kozlov's): the owner declares it.
        LinearLayout fw = null;
        for (final JSONObject p : Data.profiles(this)) {
            JSONObject m = p.optJSONObject("match");
            if (m == null || !m.optBoolean("declared")) continue;
            if (fw == null) {
                fw = card();
                fw.addView(text("Custom firmware", 17, TEXT, true));
                fw.addView(dim("Some firmware can't be recognised automatically. Tick it if this unit runs it: nothing becomes Recommended, and launcher changes come with a warning."), lp(4));
            }
            final CheckBox cb = check(p.optString("name"), u.declared.contains(p.optString("id")));
            cb.setOnCheckedChangeListener((v, on) -> {
                Store.setDeclared(this, u.key(), p.optString("id"), on);
                work(() -> {
                    s.read(this);
                    main.post(() -> show("unit"));
                });
            });
            fw.addView(cb, lp(6));
        }
        if (fw != null) page.addView(fw, lp(12));

        LinearLayout act = card();
        act.addView(text("Debloat", 17, TEXT, true));
        act.addView(dim("Review every app, pick what to disable, and apply. A backup of the unit's state is saved on this phone first."), lp(4));
        act.addView(button("Review packages", true, v -> show("packages")), lp(10));
        act.addView(button("Restore", false, v -> show("restore")), lp(8));
        page.addView(act, lp(12));

        LinearLayout br = card();
        br.addView(text("Wireless ADB", 17, TEXT, true));
        if ("USB".equals(s.address)) {
            br.addView(dim("Turn on ADB over Wi-Fi (port " + Session.BRIDGE_PORT + ") so this phone, or a computer, can reach the unit without the cable. It lasts until the unit restarts; you can turn it off again here."), lp(4));
            if (s.adbUnsecured()) {
                br.addView(colored("Warning: this unit lets any device connect to ADB without asking (ro.adb.secure=0). With wireless ADB on, anyone on the same Wi-Fi could control it. Use it on a network you trust, and turn it off when you're done.", BAD), lp(6));
            }
            br.addView(button("Make wireless (bridge)", true, v -> bridge()), lp(10));
        } else {
            br.addView(dim("Connected over Wi-Fi. A computer can use it too: adb connect " + s.address), lp(4));
            br.addView(button("Turn wireless ADB off", false, v -> usbOnly()), lp(10));
        }
        page.addView(br, lp(12));

        page.addView(button("Disconnect", false, v -> {
            work(() -> {
                Session.close();
                main.post(() -> show("connect"));
            });
        }), lp(12));
    }

    private void bridge() {
        final Session s = Session.current();
        if (s == null) return;
        say("Asking the unit to listen on Wi-Fi…", DIM);
        work(() -> {
            String ip = s.unitWifiAddress();
            if (ip == null) {
                main.post(() -> say("The unit has no Wi-Fi IPv4 address. Connect it to Wi-Fi first (a network that gives normal IPv4 addresses).", BAD));
                return;
            }
            String answer = s.adb.tcpip(Session.BRIDGE_PORT);
            Session.close();
            String addr = ip + ":" + Session.BRIDGE_PORT;
            Store.setLastAddress(this, addr);
            main.post(() -> say("Unit says: " + (answer.isEmpty() ? "OK" : answer) + ". Reconnecting over Wi-Fi at " + addr + "…", DIM));
            Thread.sleep(3000);
            Session.wifi(this, ip, Session.BRIDGE_PORT, fp -> say("Tap \"Allow\" on the head unit's screen.", WARN));
            main.post(() -> {
                show("unit");
                say("Wireless ADB is on at " + addr + ". You can unplug the cable. From a computer: adb connect " + addr, OK);
            });
        });
    }

    private void usbOnly() {
        final Session s = Session.current();
        if (s == null) return;
        work(() -> {
            String answer = s.adb.service("usb:").trim();
            Session.close();
            main.post(() -> {
                show("connect");
                say("Wireless ADB is off" + (answer.isEmpty() ? "." : " (" + answer + ")."), OK);
            });
        });
    }

    private void packagesScreen() {
        Session s = Session.current();
        if (s == null) {
            show("connect");
            return;
        }
        Rules r = s.rules(this);
        Map<String, List<Rules.Verdict>> groups = new LinkedHashMap<>();
        for (String cat : new String[]{Rules.RECOMMENDED, Rules.OPTIONAL, Rules.ADVANCED, Rules.PROTECTED, Rules.UNKNOWN, Rules.USER}) groups.put(cat, new ArrayList<>());
        for (HeadUnit.Pkg p : s.unit.packages.values()) {
            Rules.Verdict v = r.classify(p.name, p.system);
            List<Rules.Verdict> g = groups.get(v.category);
            if (g != null) g.add(v);
        }
        say(s.unit.packages.size() + " packages on the unit.", DIM);
        final CheckBox adv = check("Include Advanced (higher risk: read each one first)", includeAdvanced);
        adv.setOnCheckedChangeListener((v, on) -> {
            includeAdvanced = on;
            if (!on) {
                for (Rules.Verdict x : groups.get(Rules.ADVANCED)) picked.remove(x.pkg);
            }
            show("packages");
        });
        page.addView(adv);
        for (Map.Entry<String, List<Rules.Verdict>> e : groups.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            LinearLayout c = card();
            c.addView(text(label(e.getKey()) + " (" + e.getValue().size() + ")", 17, color(e.getKey()), true));
            c.addView(dim(explain(e.getKey())), lp(2));
            boolean many = e.getValue().size() > 40 && !Debloat.offerable(e.getValue().get(0), true);
            int shown = 0;
            for (final Rules.Verdict v : e.getValue()) {
                if (many && shown++ >= 40) {
                    c.addView(dim("…and " + (e.getValue().size() - 40) + " more."), lp(6));
                    break;
                }
                HeadUnit.Pkg p = s.unit.packages.get(v.pkg);
                String line = v.pkg + (p.enabled ? "" : "  (disabled)");
                String why = !v.purpose.isEmpty() ? v.purpose : v.reason;
                if (Debloat.offerable(v, includeAdvanced) && p.enabled) {
                    final CheckBox cb = check(line, picked.contains(v.pkg));
                    cb.setOnCheckedChangeListener((x, on) -> {
                        if (on) picked.add(v.pkg);
                        else picked.remove(v.pkg);
                    });
                    c.addView(cb, lp(6));
                } else {
                    c.addView(text(line, 14, TEXT, false), lp(6));
                }
                if (!why.isEmpty()) c.addView(dim(why));
                if (!v.risk.isEmpty()) c.addView(colored("Risk: " + v.risk, WARN));
            }
            page.addView(c, lp(12));
        }
        page.addView(button("Review plan", true, v -> show("plan")), lp(12));
    }

    private void planScreen() {
        final Session s = Session.current();
        if (s == null) {
            show("connect");
            return;
        }
        final Debloat.Plan plan = Debloat.plan(s.unit, s.rules(this), new ArrayList<>(picked), includeAdvanced);
        LinearLayout c = card();
        c.addView(text("Plan: disable " + plan.items.size() + " app" + (plan.items.size() == 1 ? "" : "s"), 18, TEXT, true));
        c.addView(dim("Each one is disabled for user 0 with pm disable-user. Nothing is uninstalled; Restore turns them back on."), lp(4));
        for (Debloat.Item it : plan.items) {
            c.addView(text(it.verdict.pkg + "  ·  " + label(it.verdict.category), 14, TEXT, true), lp(8));
            c.addView(dim(it.verdict.purpose.isEmpty() ? it.verdict.reason : it.verdict.purpose));
            c.addView(colored("Risk: " + (it.verdict.risk.isEmpty() ? "not documented: test the unit's functions afterwards" : it.verdict.risk), WARN));
        }
        for (String[] rf : plan.refused) c.addView(colored("Not in the plan: " + rf[0] + ". " + rf[1], DIM), lp(6));
        page.addView(c);
        if (plan.items.isEmpty()) {
            page.addView(dim("Nothing to apply. Pick apps under Review packages."), lp(12));
            return;
        }
        page.addView(button("Apply", true, v -> new AlertDialog.Builder(this)
                .setTitle("Disable " + plan.items.size() + " app" + (plan.items.size() == 1 ? "" : "s") + "?")
                .setMessage("Only while the vehicle is parked. A backup is saved first. Afterwards, check the unit's functions: radio, Bluetooth calls, camera, steering-wheel keys, door warnings.")
                .setPositiveButton("Disable", (d, w) -> apply(plan))
                .setNegativeButton("Cancel", null)
                .show()), lp(12));
    }

    private void apply(final Debloat.Plan plan) {
        final Session s = Session.current();
        if (s == null) return;
        say("Applying…", DIM);
        work(() -> {
            List<Debloat.Outcome> res = Debloat.apply(s.shell(), s.unit, s.records(this), plan);
            int ok = 0;
            StringBuilder sb = new StringBuilder();
            for (Debloat.Outcome o : res) {
                if (o.ok) ok++;
                else sb.append("\n").append(o.pkg).append(": ").append(o.detail);
            }
            final String msg = ok + " of " + res.size() + " disabled." + sb;
            final boolean all = ok == res.size();
            main.post(() -> {
                picked.clear();
                show("unit");
                say(msg, all ? OK : WARN);
            });
        });
    }

    private void restoreScreen() {
        final Session s = Session.current();
        if (s == null) {
            show("connect");
            return;
        }
        LinearLayout c = card();
        c.addView(text("Restore", 18, TEXT, true));
        int n = 0;
        try {
            JSONArray ch = s.records(this).changes();
            for (int i = ch.length() - 1; i >= 0; i--) {
                JSONObject x = ch.getJSONObject(i);
                if (!"applied".equals(x.optString("status"))) continue;
                n++;
                c.addView(text(x.optString("target"), 14, TEXT, true), lp(8));
                c.addView(dim(x.optString("kind") + ": " + x.optString("before") + " → " + x.optString("after") + " · " + x.optString("time")));
            }
        } catch (Exception e) {
            c.addView(colored("Couldn't read this unit's records: " + e.getMessage(), BAD), lp(6));
        }
        if (n == 0) c.addView(dim("Nothing to restore: the companion hasn't changed this unit (or everything is already back)."), lp(6));
        page.addView(c);
        if (n > 0) {
            page.addView(button("Restore all", true, v -> work(() -> {
                List<Debloat.Outcome> res = Debloat.restore(s.shell(), s.records(this), s.unit.sdk, null);
                s.read(this);
                int ok = 0;
                for (Debloat.Outcome o : res) if (o.ok) ok++;
                final String msg = ok + " of " + res.size() + " restored.";
                main.post(() -> {
                    show("restore");
                    say(msg, OK);
                });
            })), lp(12));
        }
        c.addView(dim("Records for this unit are kept on this phone, in the same format as the computer tool's."), lp(10));
    }

    // --- helpers --------------------------------------------------------------------------------

    interface Job {
        void run() throws Exception;
    }

    private void work(Job j) {
        worker.execute(() -> {
            try {
                j.run();
            } catch (Exception e) {
                String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                main.post(() -> {
                    if (Session.current() == null && !"connect".equals(screen)) show("connect");
                    say(m, BAD);
                });
            }
        });
    }

    void say(String msg, int color) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> say(msg, color));
            return;
        }
        status.setText(msg);
        status.setTextColor(color);
    }

    static String label(String cat) {
        switch (cat) {
            case Rules.RECOMMENDED: return "Recommended";
            case Rules.OPTIONAL: return "Optional";
            case Rules.ADVANCED: return "Advanced";
            case Rules.PROTECTED: return "Protected";
            case Rules.UNKNOWN: return "Unknown";
            default: return "Your apps";
        }
    }

    static String explain(String cat) {
        switch (cat) {
            case Rules.RECOMMENDED: return "Tested on this unit's profile as safe to disable.";
            case Rules.OPTIONAL: return "Documented and usually safe; you decide.";
            case Rules.ADVANCED: return "Documented, but with a real risk. Read the risk before ticking.";
            case Rules.PROTECTED: return "Never disabled: Android, the vehicle, Home.";
            case Rules.UNKNOWN: return "Purpose not documented. Never offered: it may be the app that talks to the car.";
            default: return "Installed by you. Uninstall them in the unit's Settings if you want.";
        }
    }

    static int color(String cat) {
        switch (cat) {
            case Rules.RECOMMENDED: return OK;
            case Rules.OPTIONAL: return ACCENT;
            case Rules.ADVANCED: return WARN;
            case Rules.PROTECTED: return 0xFF7FB2FF;
            default: return DIM;
        }
    }

    int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    LinearLayout.LayoutParams lp(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
        return p;
    }

    TextView text(CharSequence s, float sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setLineSpacing(0, 1.1f);
        return v;
    }

    TextView dim(CharSequence s) {
        return text(s, 13, DIM, false);
    }

    TextView colored(CharSequence s, int color) {
        return text(s, 13, color, false);
    }

    View kv(String k, String v) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(4), 0, 0);
        TextView a = text(k, 14, DIM, false);
        r.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2));
        TextView b = text(v, 14, TEXT, true);
        r.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 3));
        return r;
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        int p = dp(14);
        c.setPadding(p, p, p, p);
        GradientDrawable g = new GradientDrawable();
        g.setColor(SURFACE);
        g.setCornerRadius(dp(12));
        c.setBackground(g);
        return c;
    }

    TextView button(String label, boolean primary, View.OnClickListener l) {
        TextView b = text(label, 15, primary ? Color.BLACK : TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(48));
        b.setPadding(dp(16), 0, dp(16), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(primary ? ACCENT : 0xFF2A313A);
        g.setCornerRadius(dp(10));
        b.setBackground(g);
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(l);
        return b;
    }

    CheckBox check(String label, boolean on) {
        CheckBox cb = new CheckBox(this);
        cb.setText(label);
        cb.setTextColor(TEXT);
        cb.setChecked(on);
        return cb;
    }
}
