package io.github.maxhayim.cleanyourheadunit.companion;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;

import io.github.maxhayim.cleanyourheadunit.Data;
import io.github.maxhayim.cleanyourheadunit.Rules;
import io.github.maxhayim.cleanyourheadunit.companion.adb.AdbConnection;
import io.github.maxhayim.cleanyourheadunit.companion.adb.AdbKey;
import io.github.maxhayim.cleanyourheadunit.companion.adb.TcpTransport;
import io.github.maxhayim.cleanyourheadunit.companion.adb.UsbTransport;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The one open connection to a head unit and what we read from it. Used from one worker thread. */
public final class Session {
    public static final int BRIDGE_PORT = 5555;

    private static Session current;

    public final AdbConnection adb;
    public final String address;            // "USB" or host:port
    public HeadUnit unit;
    private Rules rules;

    private Session(AdbConnection adb, String address) {
        this.adb = adb;
        this.address = address;
    }

    public static synchronized Session current() {
        return current;
    }

    public static synchronized void close() {
        if (current != null) current.adb.close();
        current = null;
    }

    static AdbKey key(Context c) throws IOException, GeneralSecurityException {
        return AdbKey.loadOrCreate(new File(c.getFilesDir(), "adb"));
    }

    static String keyName() {
        return "CleanYourHeadUnit-Companion@" + Build.MODEL.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public static Session usb(Context c, UsbManager um, UsbDevice d, AdbConnection.Listener l) throws IOException, GeneralSecurityException {
        AdbConnection a = AdbConnection.connect(UsbTransport.open(um, d), key(c), keyName(), l);
        return open(c, a, "USB");
    }

    public static Session wifi(Context c, String host, int port, AdbConnection.Listener l) throws IOException, GeneralSecurityException {
        AdbConnection a = AdbConnection.connect(new TcpTransport(host, port, 5000), key(c), keyName(), l);
        return open(c, a, host + ":" + port);
    }

    private static Session open(Context c, AdbConnection a, String address) throws IOException {
        close();
        Session s = new Session(a, address);
        s.read(c);
        synchronized (Session.class) {
            current = s;
        }
        return s;
    }

    public HeadUnit.Shell shell() {
        return adb::shell;
    }

    /** Reads (or re-reads) the unit, with the firmware the owner declared for it. */
    public void read(Context c) throws IOException {
        HeadUnit probe = HeadUnit.identify(shell(), Data.profiles(c), new LinkedHashSet<String>());
        Set<String> declared = Store.declared(c, probe.key());
        if (!declared.isEmpty()) {
            probe.declared.addAll(declared);
            probe.matchProfiles(Data.profiles(c));
        }
        unit = probe;
        rules = null;
    }

    public Rules rules(Context c) {
        if (rules == null) {
            try {
                rules = unit.rules(Data.protectedRules(c), Data.telemetry(c));
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }
        }
        return rules;
    }

    public Records records(Context c) throws IOException {
        return new Records(new File(c.getFilesDir(), "units"), unit.key());
    }

    /** The unit's Wi-Fi IPv4 address, from its own interface list. Null when it has none. */
    public String unitWifiAddress() throws IOException {
        for (String cmd : new String[]{"ip -f inet addr show wlan0", "ip -f inet addr", "ifconfig wlan0"}) {
            AdbConnection.Result r = adb.shell(cmd);
            if (r.rc != 0) continue;
            Matcher m = Pattern.compile("inet (?:addr:)?(\\d+\\.\\d+\\.\\d+\\.\\d+)").matcher(r.out);
            while (m.find()) {
                String ip = m.group(1);
                if (!ip.startsWith("127.")) return ip;
            }
        }
        return null;
    }

    /** True when the unit's adbd doesn't ask before letting a computer in (ro.adb.secure=0). */
    public boolean adbUnsecured() {
        return "0".equals(unit.props.get("ro.adb.secure"));
    }
}
