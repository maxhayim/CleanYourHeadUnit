package io.github.maxhayim.cleanyourheadunit;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;

import io.github.maxhayim.cleanyourheadunit.adb.AdbConnection;
import io.github.maxhayim.cleanyourheadunit.adb.AdbKey;
import io.github.maxhayim.cleanyourheadunit.adb.AdbPairing;
import io.github.maxhayim.cleanyourheadunit.adb.AdbTls;
import io.github.maxhayim.cleanyourheadunit.adb.TcpTransport;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.regex.Pattern;

/**
 * Direct changes: the app talks to this unit's own adbd, the way a computer would, so it can
 * disable and restore apps without Android Settings. Two ways in, both switched on by the owner:
 *
 *  - Android 11+ Wireless debugging: paired once with the six-digit code (pairing needs
 *    Android 12, which is when apps got the TLS function it needs), then connected over TLS;
 *  - port 5555 on any version: open when the firmware has "ADB over network", or after
 *    `adb tcpip 5555` from a computer or the phone companion's bridge (until the next restart).
 *
 * Either way Android shows its own approval (the pairing code, or "Allow USB debugging?"). The
 * same rules as everywhere else apply: never Protected, Unknown, the owner's own apps, the current
 * Home app or this app; every change is checked against the unit's package list and logged.
 */
final class LocalAdb {
    private LocalAdb() {}

    static final int LEGACY_PORT = 5555;
    static final String CONNECT_SERVICE = io.github.maxhayim.cleanyourheadunit.wireless.Discovery.CONNECT,
            PAIRING_SERVICE = io.github.maxhayim.cleanyourheadunit.wireless.Discovery.PAIRING;
    static final Pattern PACKAGE = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*$");

    private static AdbConnection conn;
    private static String how;

    /** Pairing with the code from the unit itself needs Android 12 (TLS keying-material export). */
    static boolean canPair() {
        return Build.VERSION.SDK_INT >= 31;
    }

    static boolean hasWirelessDebugging() {
        return Build.VERSION.SDK_INT >= 30;
    }

    static synchronized boolean connected() {
        return conn != null;
    }

    static synchronized String how() {
        return how;
    }

    /** Tests: use a connection to a simulated adbd. */
    static synchronized void useForTests(AdbConnection c, String via) {
        conn = c;
        how = via;
    }

    static synchronized void disconnect() {
        if (conn != null) conn.close();
        conn = null;
        how = null;
    }

    static AdbKey key(Context c) throws IOException, GeneralSecurityException {
        return AdbKey.loadOrCreate(new File(c.getFilesDir(), "adb"));
    }

    static String keyName() {
        return "CleanYourHeadUnit@" + Build.MODEL.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    // --- connecting -------------------------------------------------------------------------------

    /** Blocking. Wireless debugging first (if paired), then port 5555. Returns how it connected. */
    static String connect(Context c, AdbConnection.Listener l) throws IOException {
        disconnect();
        AdbKey key;
        try {
            key = key(c);
        } catch (GeneralSecurityException e) {
            throw new IOException("Couldn't make this app's ADB key: " + e.getMessage());
        }
        String wirelessProblem = null;
        if (hasWirelessDebugging()) {
            int port = findPort(c, CONNECT_SERVICE, 4000);
            if (port > 0) {
                try {
                    TcpTransport t = new TcpTransport("127.0.0.1", port, 8000).withTls(AdbTls.context(key));
                    return opened(c, AdbConnection.connect(t, key, keyName(), l), "Wireless debugging");
                } catch (IOException | GeneralSecurityException e) {
                    wirelessProblem = e.getMessage();
                }
            }
        }
        try {
            TcpTransport t = new TcpTransport("127.0.0.1", LEGACY_PORT, 3000);
            return opened(c, AdbConnection.connect(t, key, keyName(), l), "port " + LEGACY_PORT);
        } catch (ConnectException e) {
            if (wirelessProblem != null) throw new IOException(wirelessProblem);
            throw new IOException(notOpenText());
        }
    }

    private static synchronized String opened(Context c, AdbConnection a, String via) {
        conn = a;
        how = via;
        log(c, "connect", null, true, via);
        return via;
    }

    static String notOpenText() {
        if (canPair()) {
            return "Nothing to connect to yet. Turn on Developer options → Wireless debugging (the unit needs Wi-Fi), then press Pair once.";
        }
        if (hasWirelessDebugging()) {
            return "Port 5555 isn't open. Android 11 has Wireless debugging, but pairing from the unit itself needs Android 12: "
                    + "open port 5555 instead (see below).";
        }
        return "Port 5555 isn't open. This unit has no Wireless debugging (that needs Android 11): turn on \"ADB over network\" in Developer options "
                + "if the firmware has it, or run adb tcpip 5555 once from a computer or the phone companion's bridge. That lasts until the unit restarts.";
    }

    /** Blocking. Pairs this app with the unit's Wireless debugging. port 0 = find it on the network. */
    @TargetApi(31)
    static String pair(Context c, int port, String code) throws IOException {
        if (!canPair()) throw new IOException("Pairing from the unit itself needs Android 12 or newer.");
        if (port <= 0) port = findPort(c, PAIRING_SERVICE, 6000);
        if (port <= 0) {
            throw new IOException("Couldn't find the pairing service. On the Wireless debugging screen, press Pair device with pairing code and keep that window open.");
        }
        try {
            AdbTls.KeyExporter exporter = (socket, label, length) -> android.net.ssl.SSLSockets.exportKeyingMaterial(socket, label, null, length);
            String guid = AdbPairing.pair("127.0.0.1", port, code, key(c), keyName(), exporter, 15000);
            log(c, "pair", null, true, guid);
            return guid;
        } catch (GeneralSecurityException e) {
            throw new IOException("Pairing failed: " + e.getMessage());
        }
    }

    /** This unit's own Wireless debugging port (adbd announces it with mDNS); 0 if none in time. */
    static int findPort(Context c, String type, long timeoutMs) {
        return io.github.maxhayim.cleanyourheadunit.wireless.Discovery.ownPort(c, type, timeoutMs);
    }

    // --- changes ----------------------------------------------------------------------------------

    /** Why this package may not be disabled here, or null if it may. The same rules as the plan. */
    static String refuseDisable(Context c, Unit u, String pkg) {
        if (pkg == null || !PACKAGE.matcher(pkg).matches()) return "that's not a package name";
        if (pkg.equals(c.getPackageName())) return "it's CleanYourHeadUnit itself";
        if (u.home != null && pkg.equals(u.home.getPackageName())) return "it's the current Home app";
        Unit.Pkg p = u.packages.get(pkg);
        if (p == null) return "it isn't installed";
        if (!p.enabled) return "it's already disabled";
        Rules.Verdict v = u.rules(c).classify(pkg, p.system);
        switch (v.category) {
            case Rules.RECOMMENDED:
            case Rules.OPTIONAL:
            case Rules.ADVANCED:
                return null;
            case Rules.PROTECTED:
                return "it's Protected: " + v.reason;
            case Rules.UNKNOWN:
                return "it's Unknown: its purpose isn't documented, so it's never offered";
            default:
                return "it's one of your own apps: uninstall it in Settings if you don't want it";
        }
    }

    /** Blocking. Disables for user 0 and checks it. Throws with a readable message. */
    static void disable(Context c, Unit u, String pkg) throws IOException {
        String why = refuseDisable(c, u, pkg);
        if (why != null) throw new IOException("Not disabled: " + why + ".");
        AdbConnection.Result r = shell("pm disable-user --user 0 " + pkg);
        boolean ok = disabledNow().contains(pkg);
        log(c, "disable", pkg, ok, r.out);
        if (!ok) throw new IOException("The unit didn't disable it" + (r.out.trim().isEmpty() ? "." : ": " + r.out.trim()));
    }

    /** Blocking. Enables for user 0 (the safe direction: any disabled package) and checks it. */
    static void enable(Context c, String pkg) throws IOException {
        if (pkg == null || !PACKAGE.matcher(pkg).matches()) throw new IOException("Not a package name.");
        AdbConnection.Result r = shell("pm enable --user 0 " + pkg);
        boolean ok = !disabledNow().contains(pkg);
        log(c, "enable", pkg, ok, r.out);
        if (!ok) throw new IOException("The unit didn't enable it" + (r.out.trim().isEmpty() ? "." : ": " + r.out.trim()));
    }

    static Set<String> disabledNow() throws IOException {
        return parsePackages(shell("pm list packages -d").out);
    }

    static Set<String> parsePackages(String out) {
        Set<String> s = new LinkedHashSet<>();
        for (String line : out.split("\n")) {
            line = line.trim();
            if (line.startsWith("package:")) s.add(line.substring(8).trim());
        }
        return s;
    }

    /** For the ADB console: why a command touching this package should ask first, or null. */
    static String consoleGuard(Context c, Unit u, String pkg) {
        if (pkg.equals(c.getPackageName())) return "CleanYourHeadUnit itself";
        if (u.home != null && pkg.equals(u.home.getPackageName())) return "the current Home app";
        Unit.Pkg p = u.packages.get(pkg);
        if (p == null) return null;
        Rules.Verdict v = u.rules(c).classify(pkg, p.system);
        if (Rules.PROTECTED.equals(v.category)) return "Protected (" + v.reason + ")";
        if (Rules.UNKNOWN.equals(v.category)) return "Unknown (purpose not documented)";
        return null;
    }

    static AdbConnection.Result shell(String cmd) throws IOException {
        AdbConnection a;
        synchronized (LocalAdb.class) {
            a = conn;
        }
        if (a == null) throw new IOException("Not connected. Press Connect in Diagnostics → Direct changes.");
        try {
            return a.shell(cmd);
        } catch (IOException e) {
            disconnect();       // adbd went away (Wireless debugging off, Wi-Fi changed): start over next time
            throw new IOException("The connection to adbd ended (" + e.getMessage() + "). Press Connect again.");
        }
    }

    /** One line per action in the app's private files: what, which package, whether it worked. */
    static void log(Context c, String action, String pkg, boolean ok, String detail) {
        try (OutputStream o = new FileOutputStream(new File(c.getFilesDir(), "adb-log.jsonl"), true)) {
            JSONObject j = new JSONObject().put("time", System.currentTimeMillis()).put("action", action).put("ok", ok);
            if (pkg != null) j.put("package", pkg);
            if (detail != null && !detail.isEmpty()) j.put("detail", detail.length() > 300 ? detail.substring(0, 300) : detail);
            o.write((j + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }
}
