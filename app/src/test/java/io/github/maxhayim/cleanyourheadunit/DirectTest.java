package io.github.maxhayim.cleanyourheadunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.github.maxhayim.cleanyourheadunit.adb.AdbConnection;
import io.github.maxhayim.cleanyourheadunit.adb.TestAdbd;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import android.view.View;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Direct changes over this unit's own ADB, against a simulated adbd: what may be disabled, that
 * every change is checked against the unit's package list, and that the screens offer it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DirectTest {
    final Set<String> disabled = new LinkedHashSet<>();
    final List<String> commands = new ArrayList<>();
    boolean firmwareIgnoresDisable;

    @Before
    public void setUp() {
        MainActivity.background = Runnable::run;
        Theme.reset();
    }

    @After
    public void tearDown() {
        LocalAdb.disconnect();
    }

    /** A pm that keeps a disabled list, like the real one; prints the exit-code marker AdbConnection adds. */
    String pm(String full) {
        String cmd = full.substring(0, full.indexOf("; echo " + AdbConnection.RC_MARK));
        // The console sends "timeout 30 sh -c '<command>'"; this stand-in runs the command inside.
        if (cmd.startsWith("timeout 30 sh -c '") && cmd.endsWith("'")) cmd = cmd.substring(18, cmd.length() - 1).replace("'\\''", "'");
        commands.add(cmd);
        String out = "";
        if (cmd.startsWith("pm disable-user --user 0 ")) {
            String p = cmd.substring(25);
            if (!firmwareIgnoresDisable) disabled.add(p);
            out = "Package " + p + " new state: disabled-user\n";
        } else if (cmd.startsWith("pm enable --user 0 ")) {
            String p = cmd.substring(19);
            disabled.remove(p);
            out = "Package " + p + " new state: enabled\n";
        } else if (cmd.equals("pm list packages -d")) {
            StringBuilder sb = new StringBuilder();
            for (String p : disabled) sb.append("package:").append(p).append('\n');
            out = sb.toString();
        }
        return out + AdbConnection.RC_MARK + "0\n";
    }

    MainActivity unit() throws Exception {
        return new ScreensTest().open("fyt-android10-1280x720.json", "w1280dp-h720dp-land-mdpi");
    }

    String first(MainActivity a, String category) {
        for (Map.Entry<String, Unit.Pkg> e : a.unit.packages.entrySet()) {
            if (e.getValue().enabled && category.equals(a.unit.rules(a).classify(e.getKey(), e.getValue().system).category)) return e.getKey();
        }
        return null;
    }

    @Test
    public void onlyOfferedAppsCanBeDisabled() throws Exception {
        MainActivity a = unit();
        String optional = first(a, Rules.OPTIONAL), prot = first(a, Rules.PROTECTED), unknown = first(a, Rules.UNKNOWN);
        assertNotNull(optional);
        assertNull(LocalAdb.refuseDisable(a, a.unit, optional));
        assertTrue(LocalAdb.refuseDisable(a, a.unit, prot), LocalAdb.refuseDisable(a, a.unit, prot).contains("Protected"));
        if (unknown != null) assertTrue(LocalAdb.refuseDisable(a, a.unit, unknown).contains("Unknown"));
        assertTrue(LocalAdb.refuseDisable(a, a.unit, a.getPackageName()).contains("CleanYourHeadUnit itself"));
        if (a.unit.home != null) assertTrue(LocalAdb.refuseDisable(a, a.unit, a.unit.home.getPackageName()) != null);
        assertTrue(LocalAdb.refuseDisable(a, a.unit, "x; reboot").contains("not a package name"));
    }

    @Test
    public void disablesChecksAndRestores() throws Exception {
        MainActivity a = unit();
        LocalAdb.useForTests(TestAdbd.connect(this::pm), "port 5555");
        String optional = first(a, Rules.OPTIONAL), prot = first(a, Rules.PROTECTED);
        LocalAdb.disable(a, a.unit, optional);
        assertTrue(disabled.contains(optional));
        try {
            LocalAdb.disable(a, a.unit, prot);
            fail("disabled a Protected app");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Protected"));
        }
        assertFalse("nothing was sent for the Protected app", commands.contains("pm disable-user --user 0 " + prot));
        LocalAdb.enable(a, optional);
        assertFalse(disabled.contains(optional));
    }

    @Test
    public void aDisableTheFirmwareIgnoresIsReported() throws Exception {
        MainActivity a = unit();
        LocalAdb.useForTests(TestAdbd.connect(this::pm), "port 5555");
        firmwareIgnoresDisable = true;
        try {
            LocalAdb.disable(a, a.unit, first(a, Rules.OPTIONAL));
            fail("claimed success");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("didn't disable"));
        }
    }

    @Test
    public void screensOfferDirectChangesOnlyWhenConnected() throws Exception {
        MainActivity a = unit();
        a.show("diagnostics");
        String t = ScreensTest.all(a);
        assertTrue(t, t.contains("Direct changes (ADB on this unit)"));
        assertTrue(t.contains("Not connected"));
        String optional = first(a, Rules.OPTIONAL);
        Rules.Verdict v = a.unit.rules(a).classify(optional, a.unit.packages.get(optional).system);
        assertFalse(String.join("\n", ScreensTest.texts(Screens.Debloater.detail(a, v))).contains("Disable now"));

        LocalAdb.useForTests(TestAdbd.connect(this::pm), "port 5555");
        a.show("diagnostics");
        assertTrue(ScreensTest.all(a).contains("Connected over port 5555"));
        assertTrue(String.join("\n", ScreensTest.texts(Screens.Debloater.detail(a, v))).contains("Disable now"));
        String prot = first(a, Rules.PROTECTED);
        Rules.Verdict pv = a.unit.rules(a).classify(prot, a.unit.packages.get(prot).system);
        assertFalse("Protected apps never get Disable now", String.join("\n", ScreensTest.texts(Screens.Debloater.detail(a, pv))).contains("Disable now"));
    }

    @Test
    public void consoleRunsCommandsAndAsksBeforeTouchingProtectedApps() throws Exception {
        MainActivity a = unit();
        String optional = first(a, Rules.OPTIONAL), prot = first(a, Rules.PROTECTED);
        disabled.add(optional);
        LocalAdb.useForTests(TestAdbd.connect(this::pm), "port 5555");
        Direct.console(a);
        android.app.Dialog d = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        View root = d.getWindow().getDecorView();
        android.widget.EditText in = find(root, android.widget.EditText.class, null);
        in.setText("pm list packages -d");
        find(root, TextView.class, "Run").performClick();
        String out = waitFor(root, "package:" + optional);
        assertTrue(out, out.contains("$ pm list packages -d"));
        assertTrue(commands.contains("pm list packages -d"));

        in.setText("pm disable-user --user 0 " + prot);
        find(root, TextView.class, "Run").performClick();
        android.app.AlertDialog ask = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull("asks first", ask);
        assertTrue(ask.isShowing());
        assertFalse("nothing sent before the owner agrees", commands.contains("pm disable-user --user 0 " + prot));
        assertTrue(new java.io.File(a.getFilesDir(), "console-log.txt").exists());
    }

    static <T extends View> T find(View v, Class<T> type, String text) {
        if (type.isInstance(v) && (text == null || text.contentEquals(((TextView) v).getText()))) return type.cast(v);
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                T t = find(g.getChildAt(i), type, text);
                if (t != null) return t;
            }
        }
        return null;
    }

    /** The console runs commands on its own thread and posts the output to the main looper. */
    static String waitFor(View root, String want) throws InterruptedException {
        String all = "";
        for (int i = 0; i < 100; i++) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            all = String.join("\n", ScreensTest.texts(root));
            if (all.contains(want)) return all;
            Thread.sleep(50);
        }
        return all;
    }
}
