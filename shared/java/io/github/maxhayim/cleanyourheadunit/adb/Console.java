package io.github.maxhayim.cleanyourheadunit.adb;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ADB console in both apps: the owner types a shell command, as with `adb shell` from a
 * computer. Commands get a time limit (so `logcat` without -d or `top` can't hang the connection)
 * and output is capped. Commands that would disable, remove or clear an app the rules call
 * Protected or Unknown are caught first, so the app can ask before running them.
 */
public final class Console {
    private Console() {}

    public static final int TIMEOUT_SECONDS = 30;
    public static final int MAX_OUTPUT = 256 * 1024;

    /** Says why a package matters (Protected, Unknown, this app, Home), or null if it doesn't. */
    public interface Guard {
        String why(String pkg);
    }

    /** Quick commands offered as buttons: label, command. */
    public static final String[][] QUICK = {
            {"Device", "getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.display.id"},
            {"Disabled apps", "pm list packages -d"},
            {"Wi-Fi address", "ip -f inet addr show wlan0"},
            {"Memory", "cat /proc/meminfo | head -5"},
            {"Storage", "df -h /data"},
            {"Recent log", "logcat -d -t 300"},
    };

    /** The command as sent: inside `timeout` when the unit has it (toybox, Android 6+). */
    public static String wrap(String command, boolean haveTimeout) {
        if (!haveTimeout) return command;
        return "timeout " + TIMEOUT_SECONDS + " sh -c " + quote(command);
    }

    public static String quote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    /** Asked once per connection: true when `timeout` works on the unit. */
    public static final String PROBE = "timeout 2 true";

    /** Output for the screen: capped, and a note when the time limit ended the command. */
    public static String present(AdbConnection.Result r, boolean wrapped) {
        String out = r.out;
        if (out.length() > MAX_OUTPUT) out = "… (only the last " + (MAX_OUTPUT / 1024) + " KB)\n" + out.substring(out.length() - MAX_OUTPUT);
        StringBuilder sb = new StringBuilder(out);
        if (wrapped && r.rc == 124) sb.append("\n(stopped after ").append(TIMEOUT_SECONDS).append(" seconds; for logs use logcat -d)");
        else if (r.rc != 0) sb.append("\n(exit code ").append(r.rc).append(')');
        return sb.toString();
    }

    private static final Pattern PKG_CHANGE = Pattern.compile(
            "\\b(?:pm|cmd\\s+package)\\s+(disable|disable-user|disable-until-used|uninstall|hide|suspend|clear|set-installer|default-state)\\b([^;&|\\n]*)");
    private static final Pattern PKG_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*$");   // "android" has no dot

    /**
     * The packages this command would disable, remove or clear that the guard says matter, with
     * the reason, one per line; empty when there are none.
     */
    public static List<String> risky(String command, Guard guard) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Matcher m = PKG_CHANGE.matcher(command);
        while (m.find()) {
            String action = m.group(1).toLowerCase(Locale.ROOT);
            for (String tok : m.group(2).trim().split("\\s+")) {
                tok = tok.replace("'", "").replace("\"", "");
                if (tok.startsWith("-") || !PKG_NAME.matcher(tok).matches() || !seen.add(tok)) continue;
                String why = guard.why(tok);
                if (why != null) out.add(action + " " + tok + ": " + why);
            }
        }
        return out;
    }
}
