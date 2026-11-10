package io.github.maxhayim.cleanyourheadunit.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** The ADB console's command handling: quoting, the time limit, output, and what asks first. */
public class ConsoleTest {
    static final Console.Guard GUARD = pkg -> pkg.equals("com.example.canbus") ? "Protected (vehicle bus)"
            : pkg.equals("com.example.mystery") ? "Unknown" : pkg.equals("android") ? "Protected (the Android framework)" : null;

    /** The quoted command, run by a real sh -c, prints exactly what the command itself prints. */
    @Test
    public void quotingSurvivesARealShell() throws Exception {
        String[] cmds = {"echo \"it's\"; echo \"double\" 'single' $((1+2))", "printf '%s|' a 'b c' \"d'e\"", "echo a && echo b || echo c"};
        for (String c : cmds) assertEquals(c, sh(c), sh("sh -c " + Console.quote(c)));
    }

    private static String sh(String script) throws Exception {
        Process p = new ProcessBuilder("/bin/sh", "-c", script).redirectErrorStream(true).start();
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        }
        p.waitFor();
        return b.toString("UTF-8");
    }

    @Test
    public void timeLimitOnlyWhenTheUnitHasTimeout() {
        assertEquals("timeout 30 sh -c 'pm list packages -d'", Console.wrap("pm list packages -d", true));
        assertEquals("pm list packages -d", Console.wrap("pm list packages -d", false));
    }

    @Test
    public void outputNotesTimeoutsAndErrorsAndIsCapped() {
        assertTrue(Console.present(AdbConnection.Result.parse("x\n" + AdbConnection.RC_MARK + "124\n"), true).contains("stopped after 30 seconds"));
        assertTrue(Console.present(AdbConnection.Result.parse("nope\n" + AdbConnection.RC_MARK + "1\n"), true).contains("(exit code 1)"));
        StringBuilder big = new StringBuilder();
        while (big.length() < Console.MAX_OUTPUT + 1000) big.append("0123456789");
        String shown = Console.present(AdbConnection.Result.parse(big + AdbConnection.RC_MARK + "0\n"), true);
        assertTrue(shown.length() < Console.MAX_OUTPUT + 100);
    }

    @Test
    public void asksBeforeChangingProtectedOrUnknownApps() {
        List<String> r = Console.risky("pm disable-user --user 0 com.example.canbus", GUARD);
        assertEquals(1, r.size());
        assertTrue(r.get(0), r.get(0).contains("Protected"));
        assertEquals(2, Console.risky("pm uninstall -k --user 0 com.example.mystery; cmd package clear com.example.canbus", GUARD).size());
        assertEquals(1, Console.risky("pm hide 'com.example.canbus'", GUARD).size());
        assertEquals("a package name without a dot", 1, Console.risky("pm disable-user --user 0 android", GUARD).size());
        // Reading, and changing apps the rules allow, don't ask.
        assertTrue(Console.risky("pm list packages -d", GUARD).isEmpty());
        assertTrue(Console.risky("dumpsys package com.example.canbus", GUARD).isEmpty());
        assertTrue(Console.risky("pm disable-user --user 0 com.android.egg", GUARD).isEmpty());
        assertTrue(Console.risky("pm enable com.example.canbus", GUARD).isEmpty());
    }
}
