package io.github.maxhayim.cleanyourheadunit;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** The shared JSON from the repo's data/ and themes/ folders, packed into assets at build time. */
public final class Data {
    private static JSONObject protectedJson, telemetryJson, catalogJson, dnsJson;
    private static List<JSONObject> profiles;

    private Data() {}

    public static String read(Context c, String path) throws IOException {
        try (InputStream in = c.getAssets().open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    static JSONObject json(Context c, String path) {
        try {
            return new JSONObject(read(c, path));
        } catch (IOException | JSONException e) {
            throw new IllegalStateException("bad asset " + path, e);
        }
    }

    public static synchronized JSONObject protectedRules(Context c) {
        if (protectedJson == null) protectedJson = json(c, "data/protected.json");
        return protectedJson;
    }

    public static synchronized JSONObject telemetry(Context c) {
        if (telemetryJson == null) telemetryJson = json(c, "data/telemetry.json");
        return telemetryJson;
    }

    public static synchronized JSONObject catalog(Context c) {
        if (catalogJson == null) catalogJson = json(c, "data/catalog.json");
        return catalogJson;
    }

    public static synchronized JSONObject dns(Context c) {
        if (dnsJson == null) dnsJson = json(c, "data/dns-providers.json");
        return dnsJson;
    }

    public static synchronized List<JSONObject> profiles(Context c) {
        if (profiles == null) {
            profiles = new ArrayList<>();
            try {
                String[] names = c.getAssets().list("data/profiles");
                java.util.Arrays.sort(names);
                for (String n : names) if (n.endsWith(".json")) profiles.add(json(c, "data/profiles/" + n));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return profiles;
    }

    public static JSONObject profile(Context c, String id) {
        for (JSONObject p : profiles(c)) if (id.equals(p.optString("id"))) return p;
        return null;
    }

    public static List<JSONObject> list(JSONArray a) {
        List<JSONObject> out = new ArrayList<>();
        if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.optJSONObject(i));
        return out;
    }

    public static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        return out;
    }
}
