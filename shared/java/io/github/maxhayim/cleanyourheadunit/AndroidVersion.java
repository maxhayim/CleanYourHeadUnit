package io.github.maxhayim.cleanyourheadunit;

import java.util.Map;
import java.util.TreeMap;

/**
 * The Android version a unit really runs, from its API level. Many budget head units edit the
 * version string in Settings ("13") while the system underneath is older (API 27, which is 8.1).
 * The API level can't be faked without breaking apps, so it's the one to trust.
 */
public final class AndroidVersion {
    private AndroidVersion() {}

    static final Map<Integer, String> NAMES = new TreeMap<>();

    static {
        String[] v = {"19=4.4", "20=4.4W", "21=5.0", "22=5.1", "23=6.0", "24=7.0", "25=7.1", "26=8.0", "27=8.1",
                "28=9", "29=10", "30=11", "31=12", "32=12L", "33=13", "34=14", "35=15", "36=16"};
        for (String s : v) NAMES.put(Integer.parseInt(s.split("=")[0]), s.split("=")[1]);
    }

    /** The real version for an API level, or the claimed one when the level is unknown. */
    public static String real(int sdk, String claimed) {
        String n = NAMES.get(sdk);
        if (n != null) return n;
        return claimed == null || claimed.isEmpty() ? "?" : claimed;
    }

    /** True when the claimed version's major number differs from the real one (e.g. "13" on API 27). */
    public static boolean spoofed(int sdk, String claimed) {
        String real = NAMES.get(sdk);
        if (real == null || claimed == null || claimed.trim().isEmpty()) return false;
        String rm = real.split("\\.")[0].replace("L", "").replace("W", "");
        String cm = claimed.trim().split("\\.")[0];
        return !rm.equals(cm);
    }
}
