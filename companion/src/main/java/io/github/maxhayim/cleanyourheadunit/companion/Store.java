package io.github.maxhayim.cleanyourheadunit.companion;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashSet;
import java.util.Set;

/** Small settings kept on the phone: the last Wi-Fi address and the firmware declared per unit. */
final class Store {
    private Store() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("companion", Context.MODE_PRIVATE);
    }

    static String lastAddress(Context c) {
        return sp(c).getString("lastAddress", "");
    }

    static void setLastAddress(Context c, String a) {
        sp(c).edit().putString("lastAddress", a).apply();
    }

    static Set<String> declared(Context c, String unitKey) {
        return new LinkedHashSet<>(sp(c).getStringSet("declared." + unitKey, new LinkedHashSet<String>()));
    }

    static void setDeclared(Context c, String unitKey, String profileId, boolean on) {
        Set<String> s = declared(c, unitKey);
        if (on) s.add(profileId);
        else s.remove(profileId);
        sp(c).edit().putStringSet("declared." + unitKey, s).apply();
    }
}
