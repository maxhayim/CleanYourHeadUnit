package io.github.maxhayim.cleanyourheadunit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** The Community Hub catalog (data/catalog.json): entries, developers, what's installed, and who signed it. */
public final class Catalog {
    private Catalog() {}

    public static List<JSONObject> items(Context c, String kind) {
        List<JSONObject> out = new ArrayList<>();
        for (JSONObject it : Data.list(Data.catalog(c).optJSONArray("items"))) {
            if (kind == null || kind.equals(it.optString("kind"))) out.add(it);
        }
        return out;
    }

    public static JSONObject item(Context c, String id) {
        for (JSONObject it : items(c, null)) if (id.equals(it.optString("id"))) return it;
        return null;
    }

    /** The recommended apps, in order: {item, why}. Entries whose item is missing are skipped. */
    public static List<Object[]> recommended(Context c) {
        List<Object[]> out = new ArrayList<>();
        for (JSONObject r : Data.list(Data.catalog(c).optJSONArray("recommended"))) {
            JSONObject it = item(c, r.optString("id"));
            if (it != null) out.add(new Object[]{it, r.optString("why")});
        }
        return out;
    }

    public static List<JSONObject> developers(Context c) {
        return Data.list(Data.catalog(c).optJSONArray("developers"));
    }

    public static JSONObject developer(Context c, String id) {
        for (JSONObject d : developers(c)) if (id.equals(d.optString("id"))) return d;
        return null;
    }

    /** GitHub username first (@name); a real name only where no username is verified. */
    public static String credit(Context c, String devId) {
        JSONObject d = developer(c, devId);
        if (d == null) return devId;
        if (!d.isNull("github") && !d.optString("github").isEmpty()) return "@" + d.optString("github");
        return d.optString("name", devId);
    }

    public static String status(JSONObject it) {
        if (it.has("automotive_status")) return it.optString("automotive_status");
        switch (it.optJSONObject("compatibility").optString("status")) {
            case "verified": return "Verified on a listed unit";
            case "community": return "Community reports";
            case "planned": return "Planned";
            default: return "Untested on head units";
        }
    }

    public static String installedVersion(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty()) return null;
        try {
            return c.getPackageManager().getPackageInfo(pkg, 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** SHA-256 of the installed app's first signing certificate, or null. */
    @SuppressLint("PackageManagerGetSignatures")
    @SuppressWarnings("deprecation")
    public static String signer(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            Signature[] sigs;
            if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
                sigs = pi.signingInfo == null ? null : pi.signingInfo.getApkContentsSigners();
            } else {
                sigs = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures;
            }
            return sigs == null || sigs.length == 0 ? null : sha256(sigs[0].toByteArray());
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** Signer of an APK file that hasn't been installed yet. */
    @SuppressLint("PackageManagerGetSignatures")
    @SuppressWarnings("deprecation")
    public static String[] archiveInfo(Context c, String path) {
        PackageManager pm = c.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo pi = pm.getPackageArchiveInfo(path, flags);
        if (pi == null) return null;
        Signature[] sigs = null;
        if (Build.VERSION.SDK_INT >= 28 && pi.signingInfo != null) sigs = pi.signingInfo.getApkContentsSigners();
        else if (pi.signatures != null) sigs = pi.signatures;
        return new String[]{pi.packageName, sigs == null || sigs.length == 0 ? null : sha256(sigs[0].toByteArray()), pi.versionName};
    }

    public static String sha256(byte[] b) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** "official", "different" (installed but signed by someone else), or null when not installed / not pinned. */
    public static String signature(Context c, JSONObject it) {
        String pin = it.optString("signing_cert_sha256", "");
        if (pin.isEmpty()) return null;
        String s = signer(c, it.optString("package"));
        if (s == null) return null;
        return s.equals(pin) ? "official" : "different";
    }
}
