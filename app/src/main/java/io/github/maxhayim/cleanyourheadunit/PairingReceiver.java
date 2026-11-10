package io.github.maxhayim.cleanyourheadunit;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * The pairing code goes into a notification, because Android's "Pair device with pairing code"
 * window has to stay open while pairing: the owner types the six digits into the notification's
 * reply field without leaving Settings. Shown only when the owner presses Pair; removed after.
 */
@TargetApi(31)
public class PairingReceiver extends BroadcastReceiver {
    static final String CHANNEL = "pairing", KEY = "code";
    static final int ID = 41;

    /** Shows (or updates) the notification with a reply field for the code. */
    static void prompt(Context c, String text) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Wireless debugging pairing", NotificationManager.IMPORTANCE_HIGH));
        RemoteInput in = new RemoteInput.Builder(KEY).setLabel("Six-digit code").build();
        PendingIntent pi = PendingIntent.getBroadcast(c, ID, new Intent(c, PairingReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        Notification.Action reply = new Notification.Action.Builder(null, "Enter pairing code", pi).addRemoteInput(in).build();
        nm.notify(ID, new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_diagnostics)
                .setContentTitle("Pair CleanYourHeadUnit")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setOnlyAlertOnce(true)
                .addAction(reply)
                .build());
    }

    static void done(Context c, String text) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.notify(ID, new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_diagnostics)
                .setContentTitle("CleanYourHeadUnit")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setTimeoutAfter(60_000)
                .build());
    }

    @Override
    public void onReceive(final Context c, Intent intent) {
        Bundle r = RemoteInput.getResultsFromIntent(intent);
        final String code = r == null || r.getCharSequence(KEY) == null ? "" : r.getCharSequence(KEY).toString();
        final Context app = c.getApplicationContext();
        final PendingResult pending = goAsync();
        prompt(app, "Pairing…");
        new Thread(() -> {
            try {
                LocalAdb.pair(app, 0, code);
                String via = LocalAdb.connect(app, null);
                done(app, "Paired and connected over " + via + ". Go back to CleanYourHeadUnit.");
            } catch (Exception e) {
                prompt(app, (e.getMessage() == null ? e.toString() : e.getMessage()) + " Try the code again.");
            } finally {
                pending.finish();
            }
        }, "cyhu-pair").start();
    }
}
