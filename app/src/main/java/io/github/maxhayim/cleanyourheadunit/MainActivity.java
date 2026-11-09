package io.github.maxhayim.cleanyourheadunit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * The management app: ten sections in a rail (landscape) or a bottom bar (portrait).
 * Reading the unit happens off the main thread; nothing runs in the background afterwards.
 */
public final class MainActivity extends Activity {

    /** One screen of the app. */
    interface Section {
        String id();

        String title();

        int icon();

        View build(MainActivity a);
    }

    static final List<Section> SECTIONS = Arrays.asList(
            new Screens.Dashboard(), new Screens.Debloater(), new PrivacyScreen(), new Screens.Launchers(),
            new Screens.Themes(), new Screens.Modules(), new Screens.Hub(), new Screens.Diagnostics(),
            new Screens.Backup(), new Screens.SettingsScreen());

    /** Tests replace this with a direct executor so the scan finishes before they draw. */
    static Executor background = r -> new Thread(r, "cyhu-scan").start();

    Unit unit;
    String current = "dashboard";
    private FrameLayout content;
    private final ArrayDeque<Object[]> stack = new ArrayDeque<>();   // {title, view}

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) current = saved.getString("section", current);
        String asked = getIntent().getStringExtra("section");
        if (asked != null) current = asked;
        rebuild();
        rescan();
        if (saved == null) SelfUpdate.autoCheck(this);
    }

    /** Redraws the dashboard if it's on screen (for the automatic update check's note). */
    void refreshDashboard() {
        if (stack.isEmpty() && "dashboard".equals(current) && unit != null) show(current);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("section", current);
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if (unit != null) {
            unit.width = getResources().getDisplayMetrics().widthPixels;
            unit.height = getResources().getDisplayMetrics().heightPixels;
        }
        rebuild();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SelfUpdate.visible = true;
        // Back from Android Settings (App info, Home, VPN): read the unit again.
        if (unit != null) rescan();
    }

    @Override
    protected void onPause() {
        super.onPause();
        SelfUpdate.visible = false;
    }

    void rescan() {
        final android.content.Context app = getApplicationContext();
        background.execute(() -> {
            final Unit u = Unit.scan(app);
            Snapshot.save(app, u, false);       // the first time only: the unit as first seen
            runOnUiThread(() -> {
                unit = u;
                if (stack.isEmpty()) show(current);
            });
        });
    }

    boolean portrait() {
        return getResources().getDisplayMetrics().heightPixels > getResources().getDisplayMetrics().widthPixels;
    }

    void rebuild() {
        Theme th = Ui.t(this);
        getWindow().getDecorView().setBackgroundColor(th.bg);
        content = new FrameLayout(this);
        LinearLayout root = portrait() ? Ui.vbox(this) : Ui.hbox(this);
        root.setBackgroundColor(th.bg);
        if (portrait()) {
            root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setBackgroundColor(th.surface);
            hs.addView(nav(false));
            root.addView(hs, Ui.match());
        } else {
            root.setGravity(Gravity.TOP);
            ScrollView rail = new ScrollView(this);
            rail.setBackgroundColor(th.surface);
            rail.addView(nav(true));
            root.addView(rail, new LinearLayout.LayoutParams(Ui.dp(this, railWidth()), ViewGroup.LayoutParams.MATCH_PARENT));
            root.addView(content, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        setContentView(root);
        if (!stack.isEmpty()) {
            Object[] top = stack.peek();
            showView((String) top[0], (View) top[1]);
        } else {
            showSection();
        }
    }

    int railWidth() {
        float wDp = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density;
        return wDp < 900 ? 84 : 196;     // icon-only rail on 800x480-class screens
    }

    private View nav(boolean rail) {
        Theme th = Ui.t(this);
        navFor = current;
        LinearLayout l = rail ? Ui.vbox(this) : Ui.hbox(this);
        int pad = Ui.dp(this, 8);
        l.setPadding(pad, pad, pad, pad);
        boolean compact = rail && railWidth() < 100;
        if (rail && !compact) {
            TextView brand = Ui.text(this, "CleanYourHeadUnit", 15, th.text, true);
            brand.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), 0, Ui.dp(this, 12));
            l.addView(brand);
        }
        for (final Section s : SECTIONS) {
            boolean on = s.id().equals(current);
            int color = on ? th.accent : th.dim;
            LinearLayout item = rail && !compact ? Ui.hbox(this) : Ui.vbox(this);
            item.setGravity(Gravity.CENTER_VERTICAL | (rail && !compact ? Gravity.START : Gravity.CENTER_HORIZONTAL));
            item.setMinimumHeight(Ui.dp(this, rail ? 52 : 64));
            item.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
            if (on) item.setBackground(Ui.round(Ui.mix(th.surface, th.accent, 0.14f), Ui.dp(this, 12)));
            item.addView(Ui.icon(this, s.icon(), color, 24));
            TextView label = Ui.text(this, s.title(), rail && !compact ? 14 : 11, on ? th.text : th.dim, on);
            if (rail && !compact) label.setPadding(Ui.dp(this, 12), 0, 0, 0);
            if (!compact) item.addView(label);
            item.setFocusable(true);
            item.setClickable(true);
            item.setContentDescription(s.title());
            item.setOnClickListener(v -> {
                stack.clear();
                current = s.id();
                rebuild();
            });
            LinearLayout.LayoutParams p = rail ? Ui.match() : new LinearLayout.LayoutParams(Ui.dp(this, 92), ViewGroup.LayoutParams.WRAP_CONTENT);
            p.bottomMargin = Ui.dp(this, 2);
            l.addView(item, p);
        }
        return l;
    }

    /** The section the navigation was last drawn for, so its highlight always matches the page. */
    private String navFor;

    /** Shows a top-level section (clearing any sub-pages). */
    void show(String id) {
        current = id;
        stack.clear();
        if (!id.equals(navFor)) {
            rebuild();          // redraws the navigation, then comes back here
            return;
        }
        for (Section s : SECTIONS) {
            if (s.id().equals(id)) {
                showView(null, unit == null ? loading() : s.build(this));
                return;
            }
        }
    }

    private void showSection() {
        for (Section s : SECTIONS) {
            if (s.id().equals(current)) {
                showView(null, unit == null ? loading() : s.build(this));
                return;
            }
        }
    }

    private View loading() {
        LinearLayout l = Ui.vbox(this);
        l.setGravity(Gravity.CENTER);
        l.addView(Ui.body(this, "Reading the head unit…"));
        return l;
    }

    /** Puts a page in the content area, scrollable, with a back row when it's a sub-page. */
    private void showView(String subTitle, View v) {
        Theme th = Ui.t(this);
        content.removeAllViews();
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout page = Ui.vbox(this);
        int p = Ui.dp(this, portrait() ? 16 : 24);
        page.setPadding(p, p, p, p);
        if (subTitle != null) {
            TextView back = Ui.button(this, "‹  Back", false, x -> back());
            page.addView(back, Ui.wrap());
        }
        page.addView(v, Ui.match());
        sv.addView(page);
        content.addView(sv);
        sv.setBackgroundColor(th.bg);
    }

    void push(String title, View v) {
        stack.push(new Object[]{title, v});
        showView(title, v);
    }

    void back() {
        stack.poll();
        if (stack.isEmpty()) show(current);
        else showView((String) stack.peek()[0], (View) stack.peek()[1]);
    }

    @Override
    public void onBackPressed() {
        if (!stack.isEmpty()) back();
        else super.onBackPressed();
    }

    void refresh() {
        Theme.reset();
        rebuild();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PrivacyScreen.VPN_CONSENT) {
            if (result == RESULT_OK) PrivacyScreen.onConsent(this);
            else info("Firewall not started", "Android's VPN permission was not given.");
            return;
        }
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
            if (in == null) return;
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 32L << 20) throw new java.io.IOException("file is over 32 MB");
                b.write(buf, 0, n);
            }
            String text = b.toString("UTF-8");
            if (request == Screens.Themes.PICK) {
                String id = Theme.importTheme(this, text);
                Prefs.setTheme(this, id);
                refresh();
                info("Theme imported", "Now using " + Ui.t(this).name + ".");
            } else if (request == PrivacyScreen.PICK_LIST) {
                info("Block list", Firewall.importList(this, new java.io.StringReader(text)) + " domains read. Applies the next time the firewall starts.");
            } else if (request == PrivacyScreen.PICK_CONFIG) {
                Firewall.importConfig(this, text);
                info("Firewall settings imported", "Mode, blocked apps and allowlist were replaced.");
            }
        } catch (Exception e) {
            info("Couldn't import", String.valueOf(e.getMessage()));
        }
    }

    // --- things every section uses -------------------------------------------------------------
    void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            // Many head units ship without a browser: show the address instead.
            info("No browser on this unit", "Open this address on your phone:\n\n" + url);
        }
    }

    void openAppInfo(String pkg) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
        } catch (ActivityNotFoundException e) {
            info("Not available", "This unit's Settings has no app page. Use the computer tool: tools/cyhu.py");
        }
    }

    void openSettings(String action, String fallbackText) {
        try {
            startActivity(new Intent(action));
        } catch (ActivityNotFoundException e) {
            info("Not available on this unit", fallbackText);
        }
    }

    void info(String title, String msg) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show();
    }

    /** Asks before anything that changes the unit. The parked reminder is part of every such question. */
    void confirm(String title, String msg, Runnable yes) {
        new AlertDialog.Builder(this).setTitle(title)
                .setMessage(msg + "\n\nOnly do this with the vehicle parked.")
                .setPositiveButton("Go ahead", (d, w) -> yes.run())
                .setNegativeButton("Cancel", null).show();
    }

    /** Writes text to the app's folder on the unit's storage and offers to share it. Nothing is uploaded. */
    File export(String name, String text) {
        try {
            File dir = getExternalFilesDir(null);
            if (dir == null) dir = getFilesDir();
            File f = new File(dir, name);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(text.getBytes("UTF-8"));
            }
            return f;
        } catch (Exception e) {
            info("Couldn't save", String.valueOf(e.getMessage()));
            return null;
        }
    }

    void share(String subject, String text) {
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(i, subject));
        } catch (ActivityNotFoundException ignored) {
        }
    }

    /** Thin reminder line at the top of screens that lead to changes. */
    View parked() {
        Theme th = Ui.t(this);
        LinearLayout l = Ui.hbox(this);
        l.setBackground(Ui.round(Ui.mix(th.bg, th.warn, 0.14f), Ui.dp(this, 10)));
        l.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        l.addView(Ui.text(this, "Use while parked. Every change can be undone in Backup & Restore.", 13, th.warn, false));
        return l;
    }
}
