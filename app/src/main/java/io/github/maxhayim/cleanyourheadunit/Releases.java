package io.github.maxhayim.cleanyourheadunit;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

/**
 * "Check for new releases" in the Community Hub: asks GitHub for the latest release tag of each
 * catalog entry with a GitHub source. Only when the owner presses the button; never on its own.
 */
final class Releases {
    private Releases() {}

    static void check(final MainActivity a) {
        a.info("Checking", "Asking GitHub for the latest releases…");
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            for (JSONObject it : Catalog.items(a, null)) {
                JSONObject src = it.optJSONObject("source");
                if (src == null || !"github_release".equals(src.optString("type"))) continue;
                String line;
                try {
                    JSONObject rel = new JSONObject(Installer.readText("https://api.github.com/repos/" + src.optString("repo") + "/releases/latest"));
                    String inst = Catalog.installedVersion(a, it.optString("package"));
                    line = it.optString("name") + ": " + rel.optString("tag_name") + (inst != null ? "  (installed " + inst + ")" : "");
                } catch (Exception e) {
                    line = it.optString("name") + ": couldn't check (" + e.getClass().getSimpleName() + ")";
                }
                sb.append(line).append('\n');
            }
            final String text = sb.toString();
            new Handler(Looper.getMainLooper()).post(() -> a.info("Latest releases", text));
        }, "cyhu-releases").start();
    }
}
