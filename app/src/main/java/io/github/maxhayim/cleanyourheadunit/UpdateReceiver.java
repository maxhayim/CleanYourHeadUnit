package io.github.maxhayim.cleanyourheadunit;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** Hears back from Android's installer after an automatic update, and notes the result for Settings. */
@TargetApi(21)
public class UpdateReceiver extends BroadcastReceiver {

    @Override
    @SuppressWarnings("deprecation") // the typed getParcelableExtra needs API 33
    public void onReceive(Context c, Intent intent) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            // The new copy is running; the installer's success report went to the old one.
            Prefs.setUpdateStatus(c, "Updated to " + SelfUpdate.installedVersion(c));
            Prefs.setUpdateAvailable(c, null);
            return;
        }
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null && SelfUpdate.visible) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    c.startActivity(confirm);
                    Prefs.setUpdateStatus(c, "Waiting for you to confirm the install");
                } else {
                    // Never pop up over the radio, maps or camera: wait until the app is opened.
                    Prefs.setUpdateStatus(c, "Downloaded: press Update on the Dashboard to install");
                }
                break;
            case PackageInstaller.STATUS_SUCCESS:
                Prefs.setUpdateStatus(c, "Updated");
                break;
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                // Also what Android reports right after the owner allows "install unknown apps".
                Prefs.setUpdateStatus(c, "Install cancelled. If you just allowed installs, press Update on the Dashboard.");
                break;
            default:
                String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Prefs.setUpdateStatus(c, "Update failed" + (msg == null ? "" : ": " + msg));
        }
    }
}
