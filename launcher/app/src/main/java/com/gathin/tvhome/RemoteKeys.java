package com.gathin.tvhome;

import java.util.HashMap;
import java.util.Map;

/**
 * Remote buttons that only the stock launcher answered, for InputKeyService: config.xml app_keys maps a
 * keycode to the app it opens ("190:com.google.android.youtube.tv").
 */
final class RemoteKeys {
    private RemoteKeys() { }

    /** keycode -> package. Entries without a colon, with a keycode that is not a number or with no package are skipped. */
    static Map<Integer, String> parseAppKeys(String[] entries) {
        Map<Integer, String> keys = new HashMap<>();
        for (String entry : entries) {
            String[] parts = entry.split(":", 2);
            if (parts.length != 2 || parts[1].trim().isEmpty()) continue;
            try {
                keys.put(Integer.parseInt(parts[0].trim()), parts[1].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return keys;
    }
}
