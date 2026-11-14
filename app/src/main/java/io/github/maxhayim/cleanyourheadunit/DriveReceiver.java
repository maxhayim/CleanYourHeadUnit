package io.github.maxhayim.cleanyourheadunit;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

/**
 * Power-up (and, with the service, wake-up): starts the drive service if needed, runs the
 * startup actions after the chosen delay, and sets brightness at each sunrise and sunset.
 */
public class DriveReceiver extends BroadcastReceiver {
    static final String STARTUP = "io.github.maxhayim.cleanyourheadunit.STARTUP";
    static final String BRIGHTNESS = "io.github.maxhayim.cleanyourheadunit.BRIGHTNESS";

    /** What the startup actions last did, for the Drive screen. */
    static volatile String lastStartup = "";

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (a == null) return;
        switch (a) {
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
                if (Drive.needsService(c)) DriveService.start(c);
                scheduleBrightness(c);
                if (Intent.ACTION_BOOT_COMPLETED.equals(a)) scheduleStartup(c);
                break;
            case STARTUP:
                final PendingResult pending = goAsync();
                runStartup(c.getApplicationContext(), () -> pending.finish());
                break;
            case BRIGHTNESS:
                Drive.applyBrightness(c);
                scheduleBrightness(c);
                break;
            default:
        }
    }

    private static PendingIntent pi(Context c, String action) {
        Intent i = new Intent(c, DriveReceiver.class).setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(c, action.hashCode(), i, flags);
    }

    static void scheduleStartup(Context c) {
        if (!Drive.startupOn(c)) return;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + Drive.startDelay(c) * 1000L, pi(c, STARTUP));
    }

    /** Applies now and sets an alarm for the next sunrise or sunset (inexact by a few minutes is fine). */
    static void scheduleBrightness(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent p = pi(c, BRIGHTNESS);
        am.cancel(p);
        if (!Drive.sunBrightness(c)) return;
        Drive.applyBrightness(c);
        double[] pos = Drive.position(c);
        long next = pos == null ? System.currentTimeMillis() + 30 * 60_000L
                : Sun.nextChange(pos[0], pos[1], System.currentTimeMillis(), java.util.TimeZone.getDefault()) + 60_000L;
        am.set(AlarmManager.RTC, next, p);
    }

    /** Volume, then the app, then (a few seconds later) Play. */
    static void runStartup(final Context c, final Runnable done) {
        StringBuilder s = new StringBuilder();
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        int vol = Drive.startVolume(c);
        if (vol >= 0 && am != null) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.min(vol, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)), 0);
            s.append("Volume ").append(vol).append(". ");
        }
        String pkg = Drive.startApp(c);
        if (pkg != null) {
            Intent launch = c.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) {
                s.append(pkg).append(" isn't installed or can't be opened. ");
            } else {
                try {
                    c.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    s.append("Opened ").append(pkg).append(". ");
                } catch (RuntimeException e) {
                    s.append("Couldn't open ").append(pkg).append(": ").append(e.getMessage()).append(". ");
                }
            }
        }
        lastStartup = s.toString().trim();
        if (Drive.startPlay(c) && am != null) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY));
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY));
                lastStartup = (lastStartup + " Sent Play.").trim();
                done.run();
            }, 4000);
        } else {
            done.run();
        }
    }
}
