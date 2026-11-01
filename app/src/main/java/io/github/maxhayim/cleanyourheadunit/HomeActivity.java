package io.github.maxhayim.cleanyourheadunit;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * CleanYourHeadUnit Home: the optional launcher. A clock, a dock of favourites, module shortcuts
 * and the app library, in the chosen theme. It shows apps; it never imitates or replaces vehicle
 * controls. "Change Home" is always one tap away.
 */
public final class HomeActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView clock;
    private final Runnable tick = new Runnable() {
        public void run() {
            if (clock != null) clock.setText(DateFormat.getTimeFormat(HomeActivity.this).format(new java.util.Date()));
            handler.postDelayed(this, 60000 - System.currentTimeMillis() % 60000);
        }
    };

    static ComponentName alias(Context c) {
        return new ComponentName(c, HomeActivity.class.getPackage().getName() + ".HomeAlias");
    }

    public static boolean enabled(Context c) {
        return c.getPackageManager().getComponentEnabledSetting(alias(c)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    public static void setEnabled(Context c, boolean on) {
        c.getPackageManager().setComponentEnabledSetting(alias(c), on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override
    public void onBackPressed() {
        // Home has nowhere to go back to.
    }

    static final class App {
        final String label;
        final ComponentName cn;
        final ResolveInfo ri;

        App(String label, ComponentName cn, ResolveInfo ri) {
            this.label = label;
            this.cn = cn;
            this.ri = ri;
        }
    }

    List<App> apps() {
        PackageManager pm = getPackageManager();
        List<App> out = new ArrayList<>();
        for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            out.add(new App(String.valueOf(ri.loadLabel(pm)), new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name), ri));
        }
        Collections.sort(out, (x, y) -> x.label.compareToIgnoreCase(y.label));
        return out;
    }

    boolean portrait() {
        return getResources().getDisplayMetrics().heightPixels > getResources().getDisplayMetrics().widthPixels;
    }

    void build() {
        Theme th = Theme.get(this);
        List<App> apps = apps();
        Set<String> fav = Prefs.favourites(this);
        boolean left = "left".equals(th.dock) && !portrait();

        LinearLayout root = left ? Ui.hbox(this) : Ui.vbox(this);
        root.setBackgroundColor(th.bg);
        root.setGravity(Gravity.TOP);

        // Dock: favourites, or the first apps with a known module, until the owner picks.
        List<App> dock = new ArrayList<>();
        for (App a : apps) if (fav.contains(a.cn.getPackageName())) dock.add(a);
        if (dock.isEmpty()) {
            for (App a : apps) if (Catalog.item(this, "retrodeck").optString("package").equals(a.cn.getPackageName())) dock.add(a);
        }
        LinearLayout dockView = left ? Ui.vbox(this) : Ui.hbox(this);
        dockView.setGravity(Gravity.CENTER);
        dockView.setBackgroundColor(th.surface);
        int pad = Ui.dp(this, 10);
        dockView.setPadding(pad, pad, pad, pad);
        for (App a : dock) dockView.addView(tile(a, 56, false));
        TextView change = Ui.button(this, "Change Home", false, v -> changeHome());
        TextView manage = Ui.button(this, "Manage", false, v -> startActivity(new Intent(this, MainActivity.class)));
        LinearLayout.LayoutParams lp = left ? Ui.match() : Ui.wrap();
        lp.setMargins(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        dockView.addView(manage, lp);
        LinearLayout.LayoutParams lp2 = left ? Ui.match() : Ui.wrap();
        lp2.setMargins(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        dockView.addView(change, lp2);

        LinearLayout main = Ui.vbox(this);
        int p = Ui.dp(this, 24);
        main.setPadding(p, p, p, p);
        LinearLayout top = Ui.hbox(this);
        clock = Ui.text(this, DateFormat.getTimeFormat(this).format(new java.util.Date()), portrait() ? 56 : 64, th.text, true);
        top.addView(clock, Ui.weight(1));
        top.addView(Ui.text(this, DateFormat.format("EEE d MMM", new java.util.Date()).toString(), 18, th.dim, false));
        main.addView(top);

        Ui.Grid grid = new Ui.Grid(this, 96);
        grid.forcedCols = portrait() ? th.colsPortrait : th.colsLandscape;
        for (App a : apps) grid.addView(tile(a, 52, true));
        Ui.add(main, Ui.text(this, "Apps", 16, th.dim, true), 18);
        Ui.add(main, grid, 10);
        ScrollView sv = new ScrollView(this);
        sv.addView(main);

        if (left) {
            root.addView(dockView, new LinearLayout.LayoutParams(Ui.dp(this, 150), ViewGroup.LayoutParams.MATCH_PARENT));
            root.addView(sv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        } else {
            root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
            hs.setBackgroundColor(th.surface);
            hs.setFillViewport(true);
            hs.addView(dockView);
            root.addView(hs, Ui.match());
        }
        FrameLayout f = new FrameLayout(this);
        f.addView(root);
        setContentView(f);
        getWindow().getDecorView().setBackgroundColor(th.bg);
    }

    View tile(final App a, int iconDp, boolean label) {
        Theme th = Theme.get(this);
        LinearLayout t = Ui.vbox(this);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Ui.dp(this, 8);
        t.setPadding(pad, pad, pad, pad);
        ImageView iv = new ImageView(this);
        Drawable d = a.ri.loadIcon(getPackageManager());
        iv.setImageDrawable(d);
        t.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, iconDp), Ui.dp(this, iconDp)));
        if (label) {
            TextView tv = Ui.text(this, a.label, 13, th.text, false);
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            Ui.add(t, tv, 6);
        }
        t.setFocusable(true);
        t.setClickable(true);
        t.setContentDescription(a.label);
        t.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(a.cn)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            } catch (RuntimeException ignored) {
            }
        });
        t.setOnLongClickListener(v -> {
            boolean on = Prefs.favourites(this).contains(a.cn.getPackageName());
            Prefs.setFavourite(this, a.cn.getPackageName(), !on);
            build();
            return true;
        });
        return t;
    }

    void changeHome() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                startActivity(new Intent(android.provider.Settings.ACTION_HOME_SETTINGS));
                return;
            }
        } catch (RuntimeException ignored) {
        }
        new android.app.AlertDialog.Builder(this).setTitle("Change Home")
                .setMessage("Settings → Apps → CleanYourHeadUnit → Clear defaults, then press Home and pick your launcher. "
                        + "Or turn CleanYourHeadUnit Home off in the app's Settings.")
                .setPositiveButton("OK", null).show();
    }
}
