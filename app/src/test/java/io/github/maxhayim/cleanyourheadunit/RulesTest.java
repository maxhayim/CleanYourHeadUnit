package io.github.maxhayim.cleanyourheadunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The Java classifier must give the same answers as tools/cyhu for every shared vector. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RulesTest {

    static JSONObject repoJson(String path) throws Exception {
        return new JSONObject(new String(Files.readAllBytes(new File(System.getProperty("repo.root"), path).toPath()), "UTF-8"));
    }

    @Test
    public void sharedVectors() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        JSONArray vs = repoJson("tests/vectors/classify.json").getJSONArray("vectors");
        List<String> wrong = new ArrayList<>();
        for (int i = 0; i < vs.length(); i++) {
            JSONObject v = vs.getJSONObject(i);
            List<JSONObject> profiles = new ArrayList<>();
            for (int k = 0; k < v.getJSONArray("profiles").length(); k++) profiles.add(Data.profile(c, v.getJSONArray("profiles").getString(k)));
            Map<String, String> extra = new LinkedHashMap<>();
            Iterator<String> it = v.getJSONObject("extra_protected").keys();
            while (it.hasNext()) {
                String k = it.next();
                extra.put(k, v.getJSONObject("extra_protected").getString(k));
            }
            Rules r = new Rules(Data.protectedRules(c), Data.telemetry(c), profiles, extra);
            String got = r.classify(v.getString("package"), v.getBoolean("system")).category;
            if (!got.equals(v.getString("expect"))) wrong.add(v.getString("package") + ": expected " + v.getString("expect") + ", got " + got);
        }
        assertEquals("vectors: " + wrong, Collections.emptyList(), wrong);
        assertTrue(vs.length() >= 30);
    }

    @Test
    public void profilesNeedAHardwareSignalNotAName() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        Map<String, String> props = new HashMap<>();
        props.put("ro.product.model", "FYT Sergey Kozlov Teyes");        // brand and developer names alone match nothing
        Set<String> none = new HashSet<>();
        Set<String> pk = new HashSet<>(Collections.singletonList("com.android.settings"));
        assertFalse(Rules.matches(Data.profile(c, "fyt"), props, pk, none));
        assertFalse(Rules.matches(Data.profile(c, "sergey-kozlov"), props, pk, none));
        assertFalse(Rules.matches(Data.profile(c, "alps-yt9216"), props, pk, none));
        assertTrue(Rules.matches(Data.profile(c, "generic-android"), props, pk, none));

        pk.add("com.syu.ms");
        assertTrue(Rules.matches(Data.profile(c, "fyt"), props, pk, none));
        props.put("ro.build.display.id", "yt9216-userdebug");
        assertTrue(Rules.matches(Data.profile(c, "alps-yt9216"), props, pk, none));
        assertTrue(Rules.matches(Data.profile(c, "sergey-kozlov"), props, pk, Collections.singleton("sergey-kozlov")));
    }

    @Test
    public void unknownAndProtectedAreNeverOffered() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        Rules r = new Rules(Data.protectedRules(c), Data.telemetry(c),
                Collections.singletonList(Data.profile(c, "generic-android")), new HashMap<String, String>());
        assertEquals(Rules.UNKNOWN, r.classify("com.example.vendor.thing", true).category);
        assertEquals(Rules.PROTECTED, r.classify("com.example.vendor.canbus", true).category);
        assertEquals(Rules.PROTECTED, r.classify("com.syu.ms", true).category);
        assertEquals(Rules.PROTECTED, r.classify("com.android.bluetooth", true).category);
        // Nothing is Recommended without a tested profile.
        assertFalse(Rules.RECOMMENDED.equals(r.classify("com.android.egg", true).category));
    }

    @Test
    public void profileCannotUnprotectDoorsOrVehicle() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        String[] pkgs = {"com.syu.canbus", "com.microntek.canbus", "com.vendor.doorstatus", "com.vendor.carinfo",
                "com.vendor.canbox", "com.vendor.instrumentcluster"};
        org.json.JSONArray list = new org.json.JSONArray();
        for (String p : pkgs) list.put(new org.json.JSONObject().put("package", p).put("category", "recommended"));
        org.json.JSONObject prof = new org.json.JSONObject().put("id", "t").put("name", "t").put("status", "tested").put("packages", list);
        Rules r = new Rules(Data.protectedRules(c), Data.telemetry(c), Collections.singletonList(prof), new HashMap<String, String>());
        for (String p : pkgs) assertEquals(p, Rules.PROTECTED, r.classify(p, true).category);
    }

    @Test
    public void themesValidate() throws Exception {
        for (String id : Theme.BUILTIN) {
            JSONObject t = repoJson("themes/" + id + "/theme.json");
            assertEquals(id + ": " + Theme.validate(t), Collections.emptyList(), Theme.validate(t));
        }
        JSONObject bad = repoJson("themes/midnight/theme.json");
        bad.getJSONObject("colors").put("accent", "red");
        bad.put("script", "alert(1)");
        assertTrue(Theme.validate(bad).size() >= 2);
    }

    @Test
    public void reportDropsSerialsAndAddresses() {
        assertEquals("[mac] and [email] at [lan-ip]", Report.scrub("02:00:00:aa:bb:cc and someone@example.com at 192.168.1.20"));
        assertTrue(Report.SENSITIVE_KEY.matcher("ro.serialno").find());
        assertTrue(Report.SENSITIVE_KEY.matcher("ro.boot.wifimacaddr").find());
        assertFalse(Report.SENSITIVE_KEY.matcher("ro.product.model").find());
    }

    @Test
    public void accentOverridesEveryThemeAndClears() {
        Context c = RuntimeEnvironment.getApplication();
        Prefs.setAccent(c, null);
        Theme.reset();
        int own = Theme.get(c).accent;
        Prefs.setAccent(c, "#ff7eb6");
        assertEquals(android.graphics.Color.parseColor("#FF7EB6"), Theme.get(c).accent);
        assertEquals("#FF7EB6", Prefs.accent(c));
        Prefs.setTheme(c, "daylight");
        assertEquals(android.graphics.Color.parseColor("#FF7EB6"), Theme.get(c).accent);
        Prefs.setAccent(c, null);
        Prefs.setTheme(c, "midnight");
        assertEquals(own, Theme.get(c).accent);
        // a stored value that isn't a colour is ignored
        Prefs.sp(c).edit().putString("accent", "red; DROP").apply();
        assertEquals(null, Prefs.accent(c));
        assertEquals(own, Theme.get(c).accent);
        Prefs.setAccent(c, null);
    }

    @Test
    public void buttonTextReadsOnAnyAccent() {
        assertEquals(android.graphics.Color.BLACK, Theme.onColor(android.graphics.Color.parseColor("#8FA8FF")));
        assertEquals(android.graphics.Color.BLACK, Theme.onColor(android.graphics.Color.parseColor("#E8EDF2")));
        assertEquals(android.graphics.Color.WHITE, Theme.onColor(android.graphics.Color.parseColor("#0A66C2")));
        assertEquals(android.graphics.Color.WHITE, Theme.onColor(android.graphics.Color.parseColor("#B91C1C")));
    }

    @Test
    public void firewallNeverBlocksProtectedApps() {
        Context c = RuntimeEnvironment.getApplication();
        // e.g. from an imported config file, or chosen before a rule change
        Firewall.setBlockedApp(c, "com.syu.canbus", true);
        Firewall.setBlockedApp(c, "com.vendor.doorstatus", true);
        Firewall.setBlockedApp(c, "com.android.systemui", true);
        Firewall.setBlockedApp(c, "com.example.game", true);
        assertEquals(new java.util.TreeSet<>(java.util.Collections.singleton("com.example.game")), Firewall.blockableApps(c));
        for (String p : new String[]{"com.syu.canbus", "com.vendor.doorstatus", "com.android.systemui", "com.example.game"})
            Firewall.setBlockedApp(c, p, false);
    }
}
