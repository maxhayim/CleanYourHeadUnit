package io.github.maxhayim.cleanyourheadunit.wireless;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import io.github.maxhayim.cleanyourheadunit.adb.AdbConnection;
import io.github.maxhayim.cleanyourheadunit.adb.Console;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The ADB console, full screen: type a shell command (or tap a quick one), see the output. Runs
 * over whatever connection the app already has. Commands that would disable, remove or clear a
 * Protected or Unknown app ask first. Each command is appended to a log file on this device only.
 */
public final class ConsoleDialog {
    private ConsoleDialog() {}

    public interface Runner {
        AdbConnection.Result run(String command) throws IOException;
    }

    public static final class Colors {
        final int bg, surface, text, dim, accent, bad;

        public Colors(int bg, int surface, int text, int dim, int accent, int bad) {
            this.bg = bg;
            this.surface = surface;
            this.text = text;
            this.dim = dim;
            this.accent = accent;
            this.bad = bad;
        }
    }

    public static Dialog show(final Activity a, String title, String connectedTo, final Colors col, final Runner runner,
                              final Console.Guard guard, final File log) {
        final Dialog d = new Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar);
        final Handler main = new Handler(Looper.getMainLooper());
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        final List<String> history = new ArrayList<>();
        final int[] historyAt = {0};
        final Boolean[] haveTimeout = {null};

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(col.bg);
        int p = dp(a, 12);
        root.setPadding(p, p, p, p);

        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text(a, title, 18, col.text, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(button(a, col, "Close", false, v -> d.dismiss()));
        root.addView(top);
        root.addView(text(a, "Connected to " + connectedTo + ". Like adb shell from a computer: commands run as the shell user, never root. "
                + "Changes made here skip the Debloater's rules and aren't recorded in Backup & Restore; disabling, removing or clearing a "
                + "Protected or Unknown app asks first. Each command stops after " + Console.TIMEOUT_SECONDS + " seconds.", 12, col.dim, false), lp(a, 4));

        final TextView out = text(a, "", 12, col.text, false);
        out.setTypeface(Typeface.MONOSPACE);
        out.setTextIsSelectable(true);
        final ScrollView sv = new ScrollView(a);
        sv.setBackground(round(a, col.surface));
        sv.setPadding(p, p, p, p);
        sv.addView(out);
        LinearLayout.LayoutParams svp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        svp.topMargin = dp(a, 8);
        root.addView(sv, svp);

        final EditText in = new EditText(a);
        in.setHint("Shell command, e.g. pm list packages -d");
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        in.setImeOptions(EditorInfo.IME_ACTION_GO);
        in.setTypeface(Typeface.MONOSPACE);
        in.setTextColor(col.text);
        in.setHintTextColor(col.dim);

        final Runnable[] runNow = new Runnable[1];
        final RunOne one = (cmd) -> {
            history.add(cmd);
            historyAt[0] = history.size();
            append(out, sv, "$ " + cmd + "\n", col.accent);
            worker.execute(() -> {
                String shown;
                try {
                    if (haveTimeout[0] == null) haveTimeout[0] = runner.run(Console.PROBE).rc == 0;
                    AdbConnection.Result r = runner.run(Console.wrap(cmd, haveTimeout[0]));
                    shown = Console.present(r, haveTimeout[0]);
                    write(log, cmd, r.rc);
                } catch (IOException e) {
                    shown = "(failed: " + (e.getMessage() == null ? e.toString() : e.getMessage()) + ")";
                    write(log, cmd, -1);
                }
                final String s = shown.endsWith("\n") || shown.isEmpty() ? shown : shown + "\n";
                main.post(() -> append(out, sv, s, col.text));
            });
        };
        runNow[0] = () -> {
            final String cmd = in.getText().toString().trim();
            if (cmd.isEmpty()) return;
            List<String> risky = Console.risky(cmd, guard);
            if (risky.isEmpty()) {
                in.setText("");
                one.run(cmd);
                return;
            }
            new AlertDialog.Builder(a).setTitle("This changes an app the rules protect")
                    .setMessage(android.text.TextUtils.join("\n", risky)
                            + "\n\nThe Debloater never offers these: they may be what talks to the vehicle. Undo with pm enable, pm unhide or pm install-existing; "
                            + "data cleared with pm clear can't be brought back.")
                    .setPositiveButton("Run anyway", (x, w) -> {
                        in.setText("");
                        one.run(cmd);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        };
        in.setOnEditorActionListener((v, action, ev) -> {
            boolean enter = ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN;
            if (action == EditorInfo.IME_ACTION_GO || enter) {
                runNow[0].run();
                return true;
            }
            return false;
        });

        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(in, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(button(a, col, "↑", false, v -> {
            if (history.isEmpty()) return;
            historyAt[0] = Math.max(0, historyAt[0] - 1);
            in.setText(history.get(historyAt[0]));
            in.setSelection(in.getText().length());
        }));
        TextView run = button(a, col, "Run", true, v -> runNow[0].run());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.leftMargin = dp(a, 8);
        row.addView(run, rp);
        root.addView(row, lp(a, 8));

        LinearLayout quick = new LinearLayout(a);
        for (final String[] q : Console.QUICK) {
            LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            qp.rightMargin = dp(a, 8);
            quick.addView(button(a, col, q[0], false, v -> one.run(q[1])), qp);
        }
        quick.addView(button(a, col, "Clear", false, v -> out.setText("")));
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.addView(quick);
        root.addView(hs, lp(a, 8));

        d.setContentView(root);
        d.setOnDismissListener(x -> worker.shutdown());
        d.show();
        return d;
    }

    private interface RunOne {
        void run(String cmd);
    }

    private static void append(TextView out, final ScrollView sv, String s, int color) {
        android.text.SpannableString span = new android.text.SpannableString(s);
        span.setSpan(new android.text.style.ForegroundColorSpan(color), 0, s.length(), 0);
        out.append(span);
        CharSequence all = out.getText();
        if (all.length() > 2 * Console.MAX_OUTPUT) out.setText(all.subSequence(all.length() - Console.MAX_OUTPUT, all.length()));
        sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
    }

    private static synchronized void write(File log, String cmd, int rc) {
        if (log == null) return;
        try (FileOutputStream f = new FileOutputStream(log, true)) {
            String line = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT).format(new java.util.Date())
                    + "\trc=" + rc + "\t" + cmd.replace('\n', ' ') + "\n";
            f.write(line.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    private static int dp(Activity a, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, a.getResources().getDisplayMetrics()));
    }

    private static LinearLayout.LayoutParams lp(Activity a, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(a, topDp);
        return p;
    }

    private static GradientDrawable round(Activity a, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(a, 10));
        return g;
    }

    private static TextView text(Activity a, String s, float sp, int color, boolean bold) {
        TextView v = new TextView(a);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private static TextView button(Activity a, Colors col, String label, boolean primary, View.OnClickListener l) {
        TextView b = text(a, label, 14, primary ? 0xFF000000 : col.text, true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(a, 44));
        b.setMinWidth(dp(a, 44));
        b.setPadding(dp(a, 14), 0, dp(a, 14), 0);
        b.setBackground(round(a, primary ? col.accent : col.surface));
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(l);
        return b;
    }
}
