package io.github.maxhayim.cleanyourheadunit;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Check for updates" for CleanYourHeadUnit itself. Only when the owner presses the button, never
 * on its own. The update comes only from this project's GitHub releases (tags vX.Y.Z; the phone
 * companion's companion-v* releases are skipped), is checked against the checksum GitHub publishes,
 * and must be signed with the same certificate as the copy that's installed. Android's installer
 * then asks the owner; nothing is installed silently.
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

    /** Is an automatic check due? Only when the owner turned it on, and at most once a day. */
    static boolean due(boolean enabled, long last, long now) {
        return enabled && (last <= 0 || now - last >= DAY || now < last);
    }

    /**
     * The optional check when the app opens: off by default, at most once a day, quiet on errors.
     * It only notes a newer version for the dashboard; downloading still needs the owner's tap,
     * and Android's installer still asks.
     */
    static void autoCheck(final MainActivity a) {
        final Context app = a.getApplicationContext();
        long now = System.currentTimeMillis();
        if (!due(Prefs.autoCheckUpdates(app), Prefs.lastUpdateCheck(app), now)) return;
        Prefs.setLastUpdateCheck(app, now);
        new Thread(() -> {
            try {
                Object[] rel = latest(new JSONArray(Installer.readText("https://api.github.com/repos/" + REPO + "/releases?per_page=30")));
                String mine = installedVersion(app);
                Prefs.setUpdateAvailable(app, rel != null && compare((String) rel[1], mine) > 0 ? (String) rel[1] : null);
                a.runOnUiThread(a::refreshDashboard);
            } catch (Exception ignored) {
                // Offline in a car is normal: try again next time the app opens after a day.
            }
        }, "cyhu-autoupdate").start();
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
    static File fetch(Activity a, JSONObject asset, Progress p) throws Exception {
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
