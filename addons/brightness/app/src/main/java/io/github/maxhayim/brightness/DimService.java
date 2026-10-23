package io.github.maxhayim.brightness;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import java.util.Calendar;

/**
 * Holds the dimming layer: one full-screen window over every app that never takes focus, keys or touches, so the
 * remote keeps controlling what is on screen. Runs only while there is a layer to hold or a schedule to follow,
 * and stops itself otherwise. It redraws whenever the settings change and at each start and end of the night.
 */
public class DimService extends Service {
    private static final String CHANNEL = "dimming";
    private static final int NOTIFICATION_ID = 1;
    /** Checked at least this often while a schedule runs, so a clock change or a TV standby can't leave it stale. */
    private static final long MAX_WAIT_MS = 5 * 60_000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private View layer;
    /** Kept in a field: SharedPreferences holds its listeners weakly. */
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (p, key) -> apply();
    private final Runnable tick = this::apply;
    private final BroadcastReceiver clockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            apply();
        }
    };

    /** Starts the service when the settings need it; harmless when they don't, as it stops itself at once. */
    static void start(Context c) {
        if (!Dimming.needsService(Prefs.load(c), minuteNow())) return;
        try {
            c.startForegroundService(new Intent(c, DimService.class));
        } catch (RuntimeException e) {
            // Android 12+ refuses a foreground start from the background; the next open of the app starts it.
            Log.w(Prefs.TAG, "service not started: " + e.getMessage());
        }
    }

    static boolean canDraw(Context c) {
        return Settings.canDrawOverlays(c);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = getSystemService(WindowManager.class);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_foreground)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_text))
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
        Prefs.of(this).registerOnSharedPreferenceChangeListener(prefsListener);
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(clockReceiver, f);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        apply();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        main.removeCallbacks(tick);
        Prefs.of(this).unregisterOnSharedPreferenceChangeListener(prefsListener);
        unregisterReceiver(clockReceiver);
        removeLayer();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Draws what the settings ask for at this minute, plans the next check, or stops when nothing is left to do. */
    private void apply() {
        main.removeCallbacks(tick);
        Dimming.Settings s = Prefs.load(this);
        int minute = minuteNow();
        Dimming.Level level = Dimming.level(s, minute);
        if (level.visible() && canDraw(this)) showLayer(Dimming.color(level));
        else removeLayer();
        if (!Dimming.needsService(s, minute)) {
            stopSelf();
            return;
        }
        int next = Dimming.minutesToNextChange(s, minute);
        if (next > 0) {
            Calendar now = Calendar.getInstance();
            long toBoundary = next * 60_000L - now.get(Calendar.SECOND) * 1000L - now.get(Calendar.MILLISECOND);
            main.postDelayed(tick, Math.max(1000, Math.min(MAX_WAIT_MS, toBoundary)));
        }
    }

    private void showLayer(int color) {
        if (layer != null) {
            layer.setBackgroundColor(color);
            return;
        }
        View v = new View(this);
        v.setBackgroundColor(color);
        try {
            wm.addView(v, params());
            layer = v;
        } catch (RuntimeException e) {
            Log.w(Prefs.TAG, "layer not shown (display over other apps not allowed?): " + e.getMessage());
        }
    }

    private void removeLayer() {
        if (layer == null) return;
        try {
            wm.removeView(layer);
        } catch (RuntimeException ignored) {
            // Already gone with the window manager.
        }
        layer = null;
    }

    private static WindowManager.LayoutParams params() {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        if (Build.VERSION.SDK_INT >= 28) {
            p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        p.setTitle("Brightness");
        return p;
    }

    static int minuteNow() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }
}
