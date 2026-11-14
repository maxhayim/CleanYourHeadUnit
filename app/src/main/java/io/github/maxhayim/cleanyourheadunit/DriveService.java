package io.github.maxhayim.cleanyourheadunit;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Runs only while speed-based volume, the hotspot helper or startup-on-wake is on, with a
 * notification that says what it's doing. Nothing it sees leaves the unit; the position is kept
 * only roughly, for sunrise and sunset.
 */
public class DriveService extends Service {
    static final String CHANNEL = "drive";
    static final int ID = 51;

    /** Tests and the screen read what the service last did. */
    static volatile String status = "Not running";
    static volatile float lastKmh = -1;
    static volatile int boostSteps;

    private final Handler main = new Handler(Looper.getMainLooper());
    private LocationManager lm;
    private AudioManager am;
    private int applied = -1;           // the volume this service last set
    private long screenOffAt, remembered = -600_001;
    private String net = "";

    static void start(Context c) {
        Intent i = new Intent(c, DriveService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (RuntimeException e) {
            status = "Couldn't start: " + e.getMessage();
        }
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, DriveService.class));
        status = "Not running";
    }

    @Override
    public void onCreate() {
        super.onCreate();
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        foreground("Starting…");
        IntentFilter f = new IntentFilter();
        f.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(events, f);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Drive.needsService(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        listenForSpeed();
        if (Drive.wifiHelper(this)) main.post(hotspotCheck);
        update();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(events);
        } catch (RuntimeException ignored) {
        }
        if (lm != null) lm.removeUpdates(speed);
        main.removeCallbacksAndMessages(null);
        status = "Not running";
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // --- speed-based volume ---------------------------------------------------------------------

    @SuppressLint("MissingPermission")     // checked by Drive.hasFineLocation
    private void listenForSpeed() {
        if (lm == null) return;
        lm.removeUpdates(speed);
        if (!Drive.speedVolume(this) || !Drive.hasFineLocation(this)) return;
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, speed, Looper.getMainLooper());
        } catch (RuntimeException e) {
            status = "No GPS: " + e.getMessage();
        }
    }

    final LocationListener speed = new LocationListener() {
        @Override
        public void onLocationChanged(Location l) {
            onSpeed(l.hasSpeed() ? l.getSpeed() * 3.6f : -1);
            // The rough position for sunrise and sunset, at most every ten minutes.
            if (SystemClock.elapsedRealtime() - remembered > 600_000) {
                remembered = SystemClock.elapsedRealtime();
                Drive.remember(DriveService.this, l);
            }
        }

        @Override public void onStatusChanged(String p, int s, Bundle b) {}
        @Override public void onProviderEnabled(String p) {}
        @Override public void onProviderDisabled(String p) {}
    };

    /** Applies the boost for this speed on top of the owner's own volume. */
    void onSpeed(float kmh) {
        if (am == null || kmh < 0) return;
        lastKmh = kmh;
        int now = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        // If the volume isn't what we last set, the owner changed it: that's the new base.
        int base = applied >= 0 && now == applied ? now - boostSteps : now;
        if (applied >= 0 && now != applied) boostSteps = 0;
        int steps = Drive.boost(kmh, Drive.speedStartKmh(this), Drive.speedStepKmh(this), Drive.speedMaxSteps(this), boostSteps);
        int target = Math.max(0, Math.min(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), base + steps));
        boostSteps = steps;
        if (target != now) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
            applied = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        } else {
            applied = now;
        }
        update();
    }

    // --- hotspot helper ---------------------------------------------------------------------------

    final Runnable hotspotCheck = new Runnable() {
        @Override
        public void run() {
            main.removeCallbacks(this);
            if (!Drive.wifiHelper(DriveService.this)) return;
            if (!online()) {
                reconnect();
                main.postDelayed(this, 30_000);
            } else {
                main.postDelayed(this, 120_000);
            }
            update();
        }
    };

    @SuppressWarnings("deprecation") // the only API for this up to Android 9, which is what it's for
    @SuppressLint("MissingPermission")
    void reconnect() {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        String want = Drive.hotspot(this);
        if (wm == null || want == null || !Drive.canPickWifi()) return;
        try {
            if (!wm.isWifiEnabled()) wm.setWifiEnabled(true);
            WifiInfo info = wm.getConnectionInfo();
            if (info != null && want.equals(Drive.ssid(info.getSSID()))) return;
            if (wm.getConfiguredNetworks() == null) return;
            for (WifiConfiguration w : wm.getConfiguredNetworks()) {
                if (want.equals(Drive.ssid(w.SSID))) {
                    wm.enableNetwork(w.networkId, false);   // false: never switch off the owner's other networks
                    wm.reconnect();
                    return;
                }
            }
        } catch (RuntimeException ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    boolean online() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
        net = ni != null && ni.isConnected() ? (ni.getType() == ConnectivityManager.TYPE_WIFI ? "Wi-Fi" : ni.getTypeName()) : "";
        return ni != null && ni.isConnected();
    }

    @SuppressWarnings("deprecation")
    String wifiName() {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        try {
            return wm == null || wm.getConnectionInfo() == null ? null : Drive.ssid(wm.getConnectionInfo().getSSID());
        } catch (RuntimeException e) {
            return null;
        }
    }

    // --- events -------------------------------------------------------------------------------------

    final BroadcastReceiver events = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                screenOffAt = SystemClock.elapsedRealtime();
            } else if (Intent.ACTION_SCREEN_ON.equals(a)) {
                // Many units sleep when the ignition is off instead of shutting down: treat a wake
                // after at least two minutes off like a power-up.
                if (screenOffAt > 0 && SystemClock.elapsedRealtime() - screenOffAt > 120_000 && Drive.startOnWake(c)) {
                    DriveReceiver.scheduleStartup(c);
                }
                screenOffAt = 0;
                if (Drive.wifiHelper(c)) main.postDelayed(hotspotCheck, 5000);
            } else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(a)) {
                if (Drive.wifiHelper(c)) main.postDelayed(hotspotCheck, 3000);
                update();
            }
        }
    };

    // --- notification -------------------------------------------------------------------------------

    void update() {
        StringBuilder s = new StringBuilder();
        if (Drive.speedVolume(this)) {
            if (!Drive.hasFineLocation(this)) s.append("Speed volume needs location permission. ");
            else if (lastKmh < 0) s.append("Speed volume: waiting for GPS. ");
            else s.append("Speed volume +").append(boostSteps).append(". ");
        }
        if (Drive.wifiHelper(this)) {
            boolean on = online();
            String ssid = wifiName();
            if (on) s.append("Online").append(ssid != null ? " via " + ssid : net.isEmpty() ? "" : " (" + net + ")").append('.');
            else s.append("NO INTERNET: ").append(Drive.canPickWifi() ? "trying " + Drive.hotspot(this) + ". Is the phone's hotspot on?"
                    : "turn on the phone's hotspot; Android 10+ lets only you pick the network.");
        }
        status = s.length() == 0 ? "Running" : s.toString().trim();
        foreground(status);
    }

    private void foreground(String text) {
        if (Build.VERSION.SDK_INT >= 26) Drive.channel(this, CHANNEL, "Drive features", android.app.NotificationManager.IMPORTANCE_LOW);
        Intent open = new Intent(this, MainActivity.class).putExtra("section", "drive").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, ID, open, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_drive).setContentTitle("CleanYourHeadUnit").setContentText(text).setContentIntent(pi).setOngoing(true);
        b.setStyle(new Notification.BigTextStyle().bigText(text));
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 29) {
            int type = Drive.speedVolume(this) && Drive.hasFineLocation(this) ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0;
            if (type == 0 && Build.VERSION.SDK_INT >= 34) type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
            try {
                startForeground(ID, n, type);
            } catch (RuntimeException e) {
                status = "Android didn't let it run in the background: " + e.getMessage();
                stopSelf();
            }
        } else {
            startForeground(ID, n);
        }
    }
}
