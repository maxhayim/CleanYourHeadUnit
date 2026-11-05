package io.github.maxhayim.cleanyourheadunit.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import io.github.maxhayim.cleanyourheadunit.Data;
import io.github.maxhayim.cleanyourheadunit.Rules;
import io.github.maxhayim.cleanyourheadunit.companion.adb.AdbConnection;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reading a unit, planning, applying and restoring, against a SIMULATED head unit that answers
 * shell commands like a real one. Every com.example.* name is invented; com.adups.fota is the
 * documented Adups updater from data/telemetry.json.
 */
@RunWith(RobolectricTestRunner.class)
public class CompanionTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** A budget unit that claims Android 13 but is API 27 (8.1), like the one in the 2014 Camry. */
    static final class SimUnit implements HeadUnit.Shell {
        final Map<String, String> props = new LinkedHashMap<>();
        final Map<String, boolean[]> pkgs = new LinkedHashMap<>();   // name -> {system, enabled}
        final List<String> ran = new ArrayList<>();
        boolean disableFails;

        SimUnit() {
            props.put("ro.build.version.sdk", "27");
            props.put("ro.build.version.release", "13");
            props.put("ro.product.manufacturer", "Example");
            props.put("ro.product.model", "UIS8141E (simulated)");
            props.put("ro.product.device", "sim8141e");
            props.put("ro.board.platform", "sp9832e");
            props.put("ro.build.display.id", "OPM2.171019.012 release-keys");
            props.put("ro.serialno", "SIMULATED0001");
            props.put("ro.adb.secure", "1");
            for (String p : new String[]{"android", "com.android.systemui", "com.android.settings", "com.android.bluetooth",
                    "com.android.launcher3", "com.android.egg", "com.android.calculator2", "com.android.email",
                    "com.example.canbus", "com.example.doorstatus", "com.example.carinfo", "com.example.mcuservice",
                    "com.example.radio", "com.example.mysteryservice", "com.adups.fota"}) {
                pkgs.put(p, new boolean[]{true, true});
            }
            pkgs.put("com.example.ownersgame", new boolean[]{false, true});
        }

        @Override
        public AdbConnection.Result run(String cmd) {
            ran.add(cmd);
            StringBuilder o = new StringBuilder();
            int rc = 0;
            if (cmd.equals("getprop")) {
                for (Map.Entry<String, String> e : props.entrySet()) o.append('[').append(e.getKey()).append("]: [").append(e.getValue()).append("]\n");
            } else if (cmd.startsWith("pm list packages")) {
                for (Map.Entry<String, boolean[]> e : pkgs.entrySet()) {
                    if (cmd.endsWith("-s") && !e.getValue()[0]) continue;
                    if (cmd.endsWith("-d") && e.getValue()[1]) continue;
                    o.append("package:").append(e.getKey()).append('\n');
                }
            } else if (cmd.startsWith("cmd package resolve-activity")) {
                o.append("priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\ncom.android.launcher3/.Launcher\n");
            } else if (cmd.startsWith("cmd package query-activities")) {
                o.append("com.android.launcher3/.Launcher\n");
            } else if (cmd.equals("wm size")) {
                o.append("Physical size: 1280x800\n");
            } else if (cmd.equals("wm density")) {
                o.append("Physical density: 160\n");
            } else if (cmd.equals("cat /proc/meminfo")) {
                o.append("MemTotal:        1953124 kB\nMemFree:          200000 kB\n");
            } else if (cmd.startsWith("pm disable-user --user 0 ")) {
                String p = cmd.substring("pm disable-user --user 0 ".length());
                if (!disableFails && pkgs.containsKey(p)) {
                    pkgs.get(p)[1] = false;
                    o.append("Package ").append(p).append(" new state: disabled-user");
                } else {
                    rc = 1;
                    o.append("Error");
                }
            } else if (cmd.startsWith("pm enable --user 0 ")) {
                String p = cmd.substring("pm enable --user 0 ".length());
                pkgs.get(p)[1] = true;
                o.append("Package ").append(p).append(" new state: enabled");
            } else {
                rc = 127;
            }
            return AdbConnection.Result.parse(o + "\n" + AdbConnection.RC_MARK + rc + "\n");
        }

        int changing() {
            int n = 0;
            for (String c : ran) if (c.startsWith("pm disable") || c.startsWith("pm enable")) n++;
            return n;
        }
    }

    private Context c;
    private SimUnit sim;
    private HeadUnit unit;
    private Rules rules;

    @Before
    public void setUp() throws Exception {
        c = RuntimeEnvironment.getApplication();
        sim = new SimUnit();
        unit = HeadUnit.identify(sim, Data.profiles(c), new LinkedHashSet<String>());
        rules = unit.rules(Data.protectedRules(c), Data.telemetry(c));
    }

    @Test
    public void readsTheUnitWithoutChangingIt() throws Exception {
        assertEquals(27, unit.sdk);
        assertEquals("8.1", unit.realAndroid());
        assertTrue("claims 13, runs 8.1", unit.androidVersionSpoofed());
        assertEquals("1280x800", unit.size);
        assertEquals("160", unit.density);
        assertEquals(1953124L, unit.memTotalKb);
        assertEquals("com.android.launcher3/.Launcher", unit.home);
        assertEquals(16, unit.packages.size());
        assertFalse(unit.packages.get("com.example.ownersgame").system);
        assertEquals(0, sim.changing());
        // The record key is hashed: the serial itself is never stored.
        assertEquals(16, unit.key().length());
        assertFalse(unit.summary().toString().contains("SIMULATED0001"));
    }

    @Test
    public void honestVersionIsNotFlagged() throws Exception {
        sim.props.put("ro.build.version.release", "8.1.0");
        assertFalse(HeadUnit.identify(sim, Data.profiles(c), new LinkedHashSet<String>()).androidVersionSpoofed());
        sim.props.put("ro.build.version.sdk", "33");
        sim.props.put("ro.build.version.release", "13");
        assertFalse(HeadUnit.identify(sim, Data.profiles(c), new LinkedHashSet<String>()).androidVersionSpoofed());
    }

    @Test
    public void vehicleHomeUnknownAndOwnerAppsAreNeverInAPlan() {
        List<String> all = new ArrayList<>(unit.packages.keySet());
        Debloat.Plan p = Debloat.plan(unit, rules, all, true);
        Set<String> planned = new LinkedHashSet<>();
        for (Debloat.Item it : p.items) planned.add(it.verdict.pkg);
        for (String never : new String[]{"com.example.canbus", "com.example.doorstatus", "com.example.carinfo",
                "com.example.mcuservice", "com.example.radio", "com.android.launcher3", "com.android.systemui",
                "com.android.bluetooth", "com.example.mysteryservice", "com.example.ownersgame"}) {
            assertFalse(never, planned.contains(never));
        }
        assertTrue(planned.contains("com.android.egg"));
        assertEquals(Rules.PROTECTED, rules.classify("com.android.launcher3", true).category);
        assertEquals(Rules.UNKNOWN, rules.classify("com.example.mysteryservice", true).category);
    }

    @Test
    public void advancedNeedsTheTick() {
        assertEquals(Rules.ADVANCED, rules.classify("com.adups.fota", true).category);
        Debloat.Plan without = Debloat.plan(unit, rules, Collections.singletonList("com.adups.fota"), false);
        assertTrue(without.items.isEmpty());
        assertTrue(without.refused.get(0)[1].startsWith("Advanced"));
        assertEquals(1, Debloat.plan(unit, rules, Collections.singletonList("com.adups.fota"), true).items.size());
    }

    @Test
    public void applyBacksUpDisablesVerifiesAndRestores() throws Exception {
        Records rec = new Records(tmp.newFolder("units"), unit.key());
        Debloat.Plan p = Debloat.plan(unit, rules, Arrays.asList("com.android.egg", "com.android.calculator2"), false);
        assertEquals(2, p.items.size());
        assertEquals("pm disable-user --user 0 com.android.egg", p.items.get(0).command);

        List<Debloat.Outcome> res = Debloat.apply(sim, unit, rec, p);
        for (Debloat.Outcome o : res) assertTrue(o.pkg + " " + o.detail, o.ok);
        assertFalse(sim.pkgs.get("com.android.egg")[1]);
        JSONObject base = rec.baseline();
        assertNotNull(base);
        assertTrue(base.getJSONObject("packages").getJSONObject("com.android.egg").getBoolean("enabled"));
        assertEquals("8.1", base.getJSONObject("summary").getString("android"));
        assertEquals("13", base.getJSONObject("summary").getString("android_claimed"));
        JSONArray ch = rec.changes();
        assertEquals(2, ch.length());
        assertEquals("disabled", ch.getJSONObject(0).getString("after"));

        // A second apply never overwrites the first baseline.
        Debloat.apply(sim, unit, rec, Debloat.plan(unit, rules, Collections.singletonList("com.android.email"), false));
        assertTrue(rec.baseline().getJSONObject("packages").getJSONObject("com.android.egg").getBoolean("enabled"));

        List<Debloat.Outcome> back = Debloat.restore(sim, rec, unit.sdk, null);
        assertEquals(3, back.size());
        for (Debloat.Outcome o : back) assertTrue(o.ok);
        assertTrue(sim.pkgs.get("com.android.egg")[1]);
        assertTrue(sim.pkgs.get("com.android.email")[1]);

        // Idempotent: nothing left to restore, nothing run.
        int before = sim.changing();
        assertTrue(Debloat.restore(sim, rec, unit.sdk, null).isEmpty());
        assertEquals(before, sim.changing());
    }

    @Test
    public void restoreLeavesAloneWhatTheOwnerChangedSince() throws Exception {
        Records rec = new Records(tmp.newFolder("units"), unit.key());
        Debloat.apply(sim, unit, rec, Debloat.plan(unit, rules, Collections.singletonList("com.android.egg"), false));
        sim.pkgs.get("com.android.egg")[1] = true;           // the owner turned it back on by hand
        List<Debloat.Outcome> back = Debloat.restore(sim, rec, unit.sdk, null);
        assertTrue(back.get(0).ok);
        assertEquals("already back", back.get(0).detail);
    }

    @Test
    public void aFailedDisableIsReportedNotRecorded() throws Exception {
        sim.disableFails = true;
        Records rec = new Records(tmp.newFolder("units"), unit.key());
        List<Debloat.Outcome> res = Debloat.apply(sim, unit, rec, Debloat.plan(unit, rules, Collections.singletonList("com.android.egg"), false));
        assertFalse(res.get(0).ok);
        assertEquals(0, rec.changes().length());
    }

    @Test
    public void declaredFirmwareMatchesItsProfile() throws Exception {
        HeadUnit u = HeadUnit.identify(sim, Data.profiles(c), new LinkedHashSet<>(Collections.singletonList("sergey-kozlov")));
        assertEquals("sergey-kozlov", u.firmwareFamily());
        assertEquals("unknown", unit.firmwareFamily());
    }

    @Test
    public void packageNamesAreCheckedBeforeAnyCommand() {
        for (String bad : new String[]{"com.x; reboot", "com.x && rm", "../x", "", "com"}) {
            try {
                Debloat.disableCommand(bad, 27);
                throw new AssertionError("accepted " + bad);
            } catch (IllegalArgumentException expected) {
                // refused
            }
        }
        Debloat.Plan p = Debloat.plan(unit, rules, Collections.singletonList("com.x; reboot"), true);
        assertTrue(p.items.isEmpty());
        assertEquals("not a package name", p.refused.get(0)[1]);
    }
}
