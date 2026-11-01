package io.github.maxhayim.cleanyourheadunit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.security.MessageDigest;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Installs a catalog entry from the developer's own GitHub release, never from anywhere else:
 * find the release asset, download it, check its SHA-256 against the one GitHub publishes, check
 * the package name and signing certificate against the catalog's pin, then hand it to Android's
 * installer, which asks the owner again. Nothing is installed silently.
 */
public final class Installer {
    public interface Listener {
        void progress(String message);

        void done(boolean ok, String message);
    }

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());

    public Installer(Context c) {
        ctx = c.getApplicationContext();
    }

    public void start(final JSONObject item, final Listener l) {
        new Thread(() -> {
            try {
                File apk = fetch(item, l);
                post(l, true, apk.getAbsolutePath());
            } catch (Exception e) {
                post(l, false, e.getMessage() == null ? e.toString() : e.getMessage());
            }
        }, "cyhu-install").start();
    }

    private void post(final Listener l, final boolean ok, final String msg) {
        main.post(() -> l.done(ok, msg));
    }

    private void say(final Listener l, final String msg) {
        main.post(() -> l.progress(msg));
    }

    File fetch(JSONObject item, Listener l) throws Exception {
        if (!"github_release".equals(item.optString("install"))) throw new IOException("No direct download for this entry.");
        JSONObject src = item.getJSONObject("source");
        String repo = src.getString("repo");
        if (!repo.matches("^[A-Za-z0-9-]{1,39}/[A-Za-z0-9._-]{1,100}$")) throw new IOException("Bad repository name.");
        say(l, "Asking github.com/" + repo + " for the latest release");
        JSONObject rel = new JSONObject(readText("https://api.github.com/repos/" + repo + "/releases/latest"));
        JSONArray assets = rel.optJSONArray("assets");
        Pattern p = Pattern.compile(src.getString("asset_pattern"));
        JSONObject asset = null;
        for (int i = 0; assets != null && i < assets.length(); i++) {
            if (p.matcher(assets.getJSONObject(i).optString("name")).matches()) {
                asset = assets.getJSONObject(i);
                break;
            }
        }
        if (asset == null) throw new IOException("The latest release has no APK matching the catalog.");
        String url = asset.getString("browser_download_url");
        if (!url.startsWith("https://github.com/" + repo + "/releases/download/")) throw new IOException("Download is not on the developer's own release page.");
        say(l, "Downloading " + asset.optString("name") + " (" + Unit.mb(asset.optLong("size")) + ")");
        File dir = Build.VERSION.SDK_INT >= 24 ? new File(ctx.getCacheDir(), "apk") : ctx.getExternalCacheDir();
        if (dir == null) throw new IOException("No storage for the download.");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File out = new File(dir, "download.apk");
        String sha = download(url, out);
        String digest = asset.optString("digest", "");
        if (digest.startsWith("sha256:")) {
            if (!digest.substring(7).equalsIgnoreCase(sha)) throw new IOException("Checksum doesn't match the release. Nothing installed.");
            say(l, "Checksum matches the release");
        } else {
            say(l, "The release publishes no checksum; checking the signature only");
        }
        String[] info = Catalog.archiveInfo(ctx, out.getAbsolutePath());
        if (info == null) throw new IOException("Android can't read this APK.");
        if (!item.optString("package").equals(info[0])) throw new IOException("Package is " + info[0] + ", not " + item.optString("package") + ". Refused.");
        if (info[1] == null || !info[1].equals(item.optString("signing_cert_sha256"))) {
            throw new IOException("Signed by a different certificate than the catalog's pin. Refused.");
        }
        say(l, "Signature matches " + Catalog.credit(ctx, item.optString("developer")));
        return out;
    }

    /** Hands the verified APK to Android's own installer. Returns false if the owner must allow installs first. */
    public static boolean handOff(Activity a, File apk) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
            return false;
        }
        Intent i;
        if (Build.VERSION.SDK_INT >= 24) {
            i = new Intent(Intent.ACTION_INSTALL_PACKAGE, ApkProvider.uri(a));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            //noinspection ResultOfMethodCallIgnored
            apk.setReadable(true, false);
            i = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(apk), "application/vnd.android.package-archive");
        }
        a.startActivity(i);
        return true;
    }

    // --- HTTPS ---------------------------------------------------------------------------------
    static HttpURLConnection open(String url) throws IOException {
        if (!url.startsWith("https://")) throw new IOException("https only");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        if (Build.VERSION.SDK_INT < 21 && c instanceof HttpsURLConnection) {
            try {
                ((HttpsURLConnection) c).setSSLSocketFactory(new Tls12(SSLContext.getDefault().getSocketFactory()));
            } catch (Exception ignored) {
            }
        }
        c.setRequestProperty("User-Agent", "CleanYourHeadUnit");
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        return c;
    }

    static String readText(String url) throws IOException {
        HttpURLConnection c = open(url);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        try (InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    static String download(String url, File out) throws Exception {
        HttpURLConnection c = open(url);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = c.getInputStream(); FileOutputStream fo = new FileOutputStream(out)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                fo.write(buf, 0, n);
                md.update(buf, 0, n);
            }
        } finally {
            c.disconnect();
        }
        StringBuilder sb = new StringBuilder();
        for (byte x : md.digest()) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    /** Android 4.4 supports TLS 1.2 but doesn't turn it on by default; GitHub needs it. */
    static final class Tls12 extends SSLSocketFactory {
        private final SSLSocketFactory d;

        Tls12(SSLSocketFactory d) {
            this.d = d;
        }

        private Socket on(Socket s) {
            if (s instanceof SSLSocket) ((SSLSocket) s).setEnabledProtocols(new String[]{"TLSv1.2"});
            return s;
        }

        @Override public String[] getDefaultCipherSuites() { return d.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return d.getSupportedCipherSuites(); }
        @Override public Socket createSocket(Socket s, String h, int p, boolean a) throws IOException { return on(d.createSocket(s, h, p, a)); }
        @Override public Socket createSocket(String h, int p) throws IOException { return on(d.createSocket(h, p)); }
        @Override public Socket createSocket(String h, int p, InetAddress la, int lp) throws IOException { return on(d.createSocket(h, p, la, lp)); }
        @Override public Socket createSocket(InetAddress h, int p) throws IOException { return on(d.createSocket(h, p)); }
        @Override public Socket createSocket(InetAddress h, int p, InetAddress la, int lp) throws IOException { return on(d.createSocket(h, p, la, lp)); }
    }
}
