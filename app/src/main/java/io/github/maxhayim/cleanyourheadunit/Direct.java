package io.github.maxhayim.cleanyourheadunit;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/** The screens for direct changes over this unit's own ADB (see LocalAdb). */
final class Direct {
    private Direct() {}

    interface Task {
        String run() throws Exception;
    }

    /** Runs a blocking step off the main thread with a progress dialog, then shows the result. */
    static void work(final MainActivity a, String progress, final Task task, final Runnable after) {
        final android.app.ProgressDialog pd = new android.app.ProgressDialog(a);
        pd.setMessage(progress);
        pd.setCancelable(false);
        pd.show();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String msg;
            boolean ok;
            try {
                msg = task.run();
                ok = true;
            } catch (Exception e) {
                msg = e.getMessage() == null ? e.toString() : e.getMessage();
                ok = false;
            }
            final String m = msg;
            final boolean good = ok;
            main.post(() -> {
                pd.dismiss();
                if (m != null) a.info(good ? "Done" : "Didn't work", m);
                if (after != null) after.run();
            });
        }, "cyhu-adb").start();
    }

    /** The Diagnostics card: status, how to switch it on for this Android version, and the buttons. */
    static View card(final MainActivity a) {
        Theme th = Ui.t(a);
        LinearLayout c = Ui.card(a);
        c.addView(Ui.text(a, "Direct changes (ADB on this unit)", 17, th.text, true));
        boolean on = LocalAdb.connected();
        Ui.add(c, Ui.kv(a, "Status", on ? "Connected over " + LocalAdb.how() : "Not connected"), 8);
        Ui.add(c, Ui.body(a, "When it's connected, the Debloater and Backup can disable and restore apps here directly, as a computer would. "
                + "The rules don't change: never Protected, Unknown, your own apps or the current Home app, and every change appears in Backup & Restore."), 6);
        Ui.add(c, Ui.dim(a, howText()), 8);
        Ui.Flow b = new Ui.Flow(a);
        if (on) {
            b.addView(Ui.button(a, "Disconnect", false, v -> { LocalAdb.disconnect(); a.show("diagnostics"); }));
        } else {
            b.addView(Ui.button(a, "Connect", true, v -> connect(a)));
            if (LocalAdb.canPair()) {
                Ui.add(b, Ui.button(a, "Pair", false, v -> startPairing(a)), 10);
                Ui.add(b, Ui.button(a, "Pair with code here", false, v -> pairHere(a)), 10);
            }
            Ui.add(b, Ui.button(a, "Developer options", false, v -> {
                try {
                    a.startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                } catch (Exception e) {
                    a.info("Not available", "This unit's Settings has no Developer options page to open. Turn it on in Settings → About (tap Build number seven times).");
                }
            }), 10);
        }
        Ui.add(c, b, 10);
        return c;
    }

    static String howText() {
        if (LocalAdb.canPair()) {
            return "To switch it on: Developer options → Wireless debugging (on Wi-Fi). Press Pair here, then on that screen choose "
                    + "Pair device with pairing code and type the six digits into the notification that appears (the window has to stay open). "
                    + "Pairing is once; after that, Connect is enough while Wireless debugging is on. Port 5555 also works if it's open.";
        }
        if (LocalAdb.hasWirelessDebugging()) {
            return "Android 11 has Wireless debugging, but pairing from the unit itself needs Android 12. Instead, open port 5555: "
                    + "\"ADB over network\" in Developer options if the firmware has it, or adb tcpip 5555 once from a computer or the phone companion's bridge "
                    + "(until the unit restarts). Then press Connect and tap Allow.";
        }
        return "This unit has no Wireless debugging (it needs Android 11). Direct changes need port 5555: \"ADB over network\" in Developer options "
                + "if the firmware has it, or adb tcpip 5555 once from a computer or the phone companion's bridge (until the unit restarts). "
                + "Then press Connect and tap Allow. Without either, use Disable in Android Settings.";
    }

    static void connect(final MainActivity a) {
        final Handler main = new Handler(Looper.getMainLooper());
        work(a, "Connecting to this unit's adbd…", () -> {
            String via = LocalAdb.connect(a.getApplicationContext(), fp -> main.post(() ->
                    a.info("Tap Allow", "The unit is asking \"Allow USB debugging?\" for CleanYourHeadUnit (key " + fp + "). Tap Allow; "
                            + "\"Always allow\" saves asking next time.")));
            return "Connected over " + via + ". The Debloater and Backup now offer direct changes.";
        }, () -> a.show("diagnostics"));
    }

    static void startPairing(final MainActivity a) {
        if (Build.VERSION.SDK_INT >= 33 && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 41);
        }
        PairingReceiver.prompt(a, "On Wireless debugging, choose Pair device with pairing code, keep that window open, and type its six digits here.");
        a.info("Pair with Wireless debugging",
                "1. Developer options → Wireless debugging → On (the unit must be on Wi-Fi).\n"
                        + "2. Choose Pair device with pairing code.\n"
                        + "3. Pull down the notifications, choose Enter pairing code on CleanYourHeadUnit's notification, and type the six digits.\n\n"
                        + "If no notification appears, use Pair with code here instead.");
        try {
            a.startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (Exception ignored) {
        }
    }

    /** For when the notification can't be used: code (and optionally the port) typed in the app. */
    static void pairHere(final MainActivity a) {
        LinearLayout box = Ui.vbox(a);
        int pad = Ui.dp(a, 20);
        box.setPadding(pad, pad / 2, pad, 0);
        final EditText code = new EditText(a);
        code.setHint("Six-digit code");
        code.setInputType(InputType.TYPE_CLASS_NUMBER);
        final EditText port = new EditText(a);
        port.setHint("Port (optional: found automatically)");
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(code);
        box.addView(port);
        new AlertDialog.Builder(a).setTitle("Pair with code")
                .setMessage("Type the code and port shown under Pair device with pairing code. If that window closes when you come back here, "
                        + "use the notification instead (Pair).")
                .setView(box)
                .setPositiveButton("Pair", (d, w) -> {
                    final String c = code.getText().toString();
                    int p;
                    try {
                        p = Integer.parseInt(port.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        p = 0;
                    }
                    final int pp = p;
                    work(a, "Pairing…", () -> {
                        LocalAdb.pair(a.getApplicationContext(), pp, c);
                        return "Paired and connected over " + LocalAdb.connect(a.getApplicationContext(), null) + ".";
                    }, () -> a.show("diagnostics"));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // --- changes ------------------------------------------------------------------------------------

    static void disable(final MainActivity a, final Rules.Verdict v, String label, String risk) {
        a.confirm("Disable " + label + " now?",
                "Disabled for user 0 over this unit's ADB, then checked. Nothing is uninstalled; Backup & Restore can put it back.\n\nRisk: " + risk,
                () -> work(a, "Disabling " + label + "…", () -> {
                    LocalAdb.disable(a.getApplicationContext(), a.unit, v.pkg);
                    return label + " is disabled. Check the car's functions you use, and restore it from Backup & Restore if anything misbehaves.";
                }, () -> { a.back(); a.rescan(); }));
    }

    static void enable(final MainActivity a, final String pkg, String label) {
        work(a, "Enabling " + label + "…", () -> {
            LocalAdb.enable(a.getApplicationContext(), pkg);
            return label + " is enabled again.";
        }, a::rescan);
    }

    /** Applies the Debloater's plan, one package at a time, skipping anything the rules refuse. */
    static void applyPlan(final MainActivity a, final java.util.Collection<String> plan) {
        a.confirm("Apply the plan now?",
                plan.size() + " app(s) are disabled one by one over this unit's ADB and each is checked. Anything the rules don't allow is skipped.",
                () -> work(a, "Applying the plan…", () -> {
                    List<String> done = new ArrayList<>(), failed = new ArrayList<>();
                    for (String p : new ArrayList<>(plan)) {
                        try {
                            LocalAdb.disable(a.getApplicationContext(), a.unit, p);
                            done.add(p);
                            plan.remove(p);
                        } catch (Exception e) {
                            failed.add(p + ": " + e.getMessage());
                        }
                    }
                    return "Disabled " + done.size() + "." + (failed.isEmpty() ? "" : "\n\nNot disabled:\n" + android.text.TextUtils.join("\n", failed))
                            + "\n\nCheck the car's functions you use. Backup & Restore can put each one back.";
                }, a::rescan));
    }
}
