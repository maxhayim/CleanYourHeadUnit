package io.github.maxhayim.cleanyourheadunit;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Updates for CleanYourHeadUnit itself, in three modes (Settings → About → Updates):
 * Off (the default): only the Check for updates button contacts GitHub.
 * Notify only: when the app opens, at most daily, look for a newer release and show a Dashboard card.
 * Automatic: the same check, then download and install. Android 12+ installs silently once this app
 * installed itself before; older Android asks once, and only while the app is on screen.
 * Every path takes only this project's GitHub releases (tags vX.Y.Z; the phone companion's
 * companion-v* releases are skipped), checks GitHub's checksum and requires the same signing
 * certificate as the installed copy.
 */
final class SelfUpdate {
    private SelfUpdate() {}

    static final String REPO = "maxhayim/CleanYourHeadUnit";
    static final Pattern TAG = Pattern.compile("^v(\\d+)\\.(\\d+)\\.(\\d+)$");
    static final Pattern ASSET = Pattern.compile("^CleanYourHeadUnit-\\d+\\.\\d+\\.\\d+\\.apk$");

    /** The newest app release (drafts skipped, pre-releases included), or null. {tag, version, asset}. */
    static Object[] latest(JSONArray releases) {
        Object[] best = null;
        for (int i = 0; releases != null && i < releases.length(); i++) {
            JSONObject r = releases.optJSONObject(i);
            if (r == null || r.optBoolean("draft")) continue;
            Matcher m = TAG.matcher(r.optString("tag_name"));
            if (!m.matches()) continue;
            JSONObject asset = null;
            JSONArray as = r.optJSONArray("assets");
            for (int j = 0; as != null && j < as.length(); j++) {
                if (ASSET.matcher(as.optJSONObject(j).optString("name")).matches()) asset = as.optJSONObject(j);
            }
            if (asset == null) continue;
            String v = m.group(1) + "." + m.group(2) + "." + m.group(3);
            if (best == null || compare(v, (String) best[1]) > 0) best = new Object[]{r.optString("tag_name"), v, asset};
        }
        return best;
    }

    /** Compares "1.2.3" style versions; anything unreadable counts as 0.0.0. */
    static int compare(String a, String b) {
        int[] x = parts(a), y = parts(b);
        for (int i = 0; i < 3; i++) if (x[i] != y[i]) return x[i] < y[i] ? -1 : 1;
        return 0;
    }

    private static int[] parts(String v) {
        int[] out = new int[3];
        if (v == null) return out;
        String[] s = v.trim().split("[.-]");
        for (int i = 0; i < 3 && i < s.length; i++) {
            try {
                out[i] = Integer.parseInt(s[i]);
            } catch (NumberFormatException e) {
                return new int[3];
            }
        }
        return out;
    }

    static final long DAY = 24L * 60 * 60 * 1000;

    /** Is an automatic check due? Only in Notify only or Automatic, and at most once a day. */
    static boolean due(boolean enabled, long last, long now) {
        return enabled && (last <= 0 || now - last >= DAY || now < last);
    }

    /** True while a CleanYourHeadUnit screen is showing: Android's install prompt is only shown then. */
    static volatile boolean visible;

    /**
     * The check when the app opens, in Notify only and Automatic: at most once a day, quiet on
     * errors (offline is normal in a car). Notify only just notes the version for the Dashboard.
     */
    static void autoCheck(final MainActivity a) {
        final Context app = a.getApplicationContext();
        final String mode = Prefs.updateMode(app);
        long now = System.currentTimeMillis();
        if (!due(!Prefs.UPDATES_OFF.equals(mode), Prefs.lastUpdateCheck(app), now)) return;
        Prefs.setLastUpdateCheck(app, now);
        new Thread(() -> {
            Object[] rel;
            try {
                rel = latest(new JSONArray(Installer.readText("https://api.github.com/repos/" + REPO + "/releases?per_page=30")));
            } catch (Exception ignored) {
                return; // try again next time the app opens after a day
            }
            String mine = installedVersion(app);
            boolean newer = rel != null && compare((String) rel[1], mine) > 0;
            Prefs.setUpdateAvailable(app, newer ? (String) rel[1] : null);
            if (!newer) Prefs.setUpdateStatus(app, null);
            a.runOnUiThread(a::refreshDashboard);
            if (newer && Prefs.UPDATES_AUTO.equals(mode)) automatic(a, (String) rel[1], (JSONObject) rel[2]);
        }, "cyhu-autoupdate").start();
    }

    /** Automatic mode: download, verify, install. Runs on the background thread. */
    static void automatic(final MainActivity a, String version, JSONObject asset) {
        final Context app = a.getApplicationContext();
        try {
            Prefs.setUpdateStatus(app, "Downloading " + version + "…");
            File apk = fetch(app, asset, m -> { });
            if (Build.VERSION.SDK_INT >= 21) {
                Prefs.setUpdateStatus(app, "Installing " + version + "…");
                commit(app, apk);
            } else {
                // Android 4.4 has no PackageInstaller sessions: hand it to the installer screen.
                Prefs.setUpdateStatus(app, "Downloaded " + version + ": confirm the install");
                a.runOnUiThread(() -> {
                    if (visible) Installer.handOff(a, apk);
                });
            }
        } catch (Exception e) {
            Prefs.setUpdateStatus(app, "Automatic update to " + version + " failed: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
        }
        a.runOnUiThread(a::refreshDashboard);
    }

    /** Writes the verified APK into a PackageInstaller session. UpdateReceiver hears the result. */
    @android.annotation.TargetApi(21)
    static void commit(Context c, File apk) throws IOException {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(c.getPackageName());
        if (Build.VERSION.SDK_INT >= 31) {
            // Silent only when this app installed the current copy; otherwise Android still asks.
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }
        int id = pi.createSession(params);
        try (PackageInstaller.Session session = pi.openSession(id)) {
            try (OutputStream out = session.openWrite("base.apk", 0, apk.length()); InputStream in = new FileInputStream(apk)) {
                byte[] buf = new byte[64 * 1024];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                session.fsync(out);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent done = PendingIntent.getBroadcast(c, id, new Intent(c, UpdateReceiver.class), flags);
            session.commit(done.getIntentSender());
        }
    }

    /** The version to announce on the dashboard, if it's newer than what's installed. */
    static String pending(Context c) {
        String v = Prefs.updateAvailable(c);
        return v != null && compare(v, installedVersion(c)) > 0 ? v : null;
    }

    static String installedVersion(Context c) {
        return Screens.BuildConfigHolder.version(c);
    }

    /** Press "Check for updates": ask GitHub, then offer the download if there's something newer. */
    static void check(final MainActivity a) {
        a.info("Checking", "Asking github.com/" + REPO + " for the latest release…");
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            Object[] rel;
            String err = null;
            try {
                rel = latest(new JSONArray(Installer.readText("https://api.github.com/repos/" + REPO + "/releases?per_page=30")));
            } catch (Exception e) {
                rel = null;
                err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final Object[] r = rel;
            final String error = err;
            main.post(() -> offer(a, r, error));
        }, "cyhu-selfupdate").start();
    }

    private static void offer(final MainActivity a, final Object[] rel, String error) {
        String mine = installedVersion(a);
        if (error != null) {
            a.info("Couldn't check", "GitHub didn't answer (" + error + "). Check the unit is online and try again.");
            return;
        }
        if (rel == null) {
            a.info("No release yet", "There's no CleanYourHeadUnit release on GitHub yet.");
            return;
        }
        final String version = (String) rel[1];
        if (compare(version, mine) <= 0) {
            a.info("Up to date", "You have " + mine + ", the latest release.");
            return;
        }
        final JSONObject asset = (JSONObject) rel[2];
        a.confirm("Update to " + version + "?",
                "You have " + mine + ". The update comes from github.com/" + REPO + "/releases, is checked against GitHub's checksum "
                        + "and must carry the same signature as this copy. Then Android's installer asks you. Your settings and records stay.",
                () -> download(a, version, asset));
    }

    private static void download(final MainActivity a, final String version, final JSONObject asset) {
        final android.app.ProgressDialog pd = new android.app.ProgressDialog(a);
        pd.setMessage("Starting…");
        pd.setCancelable(false);
        pd.show();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String msg;
            File apk = null;
            try {
                apk = fetch(a, asset, m -> main.post(() -> pd.setMessage(m)));
                msg = null;
            } catch (Exception e) {
                msg = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final File f = apk;
            final String m = msg;
            main.post(() -> {
                pd.dismiss();
                if (f == null) {
                    a.info("Not updated", m);
                } else if (!Installer.handOff(a, f)) {
                    a.info("One more step", "Allow CleanYourHeadUnit to install apps in the screen that opened, then press Check for updates again.");
                }
            });
        }, "cyhu-selfupdate").start();
    }

    interface Progress {
        void say(String m);
    }

    /** Downloads and verifies the release APK. Refuses anything not signed like the installed copy. */
    static File fetch(Context a, JSONObject asset, Progress p) throws Exception {
        String url = asset.getString("browser_download_url");
        if (!url.startsWith("https://github.com/" + REPO + "/releases/download/")) throw new IOException("Download is not on the project's own release page.");
        p.say("Downloading " + asset.optString("name") + " (" + Unit.mb(asset.optLong("size")) + ")");
        File dir = Build.VERSION.SDK_INT >= 24 ? new File(a.getCacheDir(), "apk") : a.getExternalCacheDir();
        if (dir == null) throw new IOException("No storage for the download.");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File out = new File(dir, "download.apk");
        String sha = Installer.download(url, out);
        String digest = asset.optString("digest", "");
        if (!digest.startsWith("sha256:")) throw new IOException("The release publishes no checksum. Nothing installed.");
        if (!digest.substring(7).equalsIgnoreCase(sha)) throw new IOException("Checksum doesn't match the release. Nothing installed.");
        p.say("Checksum matches the release");
        String[] info = Catalog.archiveInfo(a, out.getAbsolutePath());
        if (info == null) throw new IOException("Android can't read this APK.");
        if (!a.getPackageName().equals(info[0])) throw new IOException("The APK is " + info[0] + ", not CleanYourHeadUnit. Refused.");
        String mine = Catalog.signer(a, a.getPackageName());
        if (mine == null || !mine.equals(info[1])) {
            throw new IOException("This copy is signed differently from the release (for example a test build). Android can't update it in place: "
                    + "uninstall this copy once, then install the release from github.com/" + REPO + "/releases. Nothing installed.");
        }
        p.say("Signature matches this copy");
        return out;
    }
}
