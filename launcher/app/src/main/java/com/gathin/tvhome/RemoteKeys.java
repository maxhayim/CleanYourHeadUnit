package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Remote buttons that only the stock launcher answered, for InputKeyService and HomeKeyService: app_keys
 * (config.xml or a theme) maps a keycode to the app it opens, as "190:com.google.android.youtube.tv" or
 * "KEYCODE_BUTTON_3:com.google.android.youtube.tv", or a scancode as "scan:490:com.wbd.hbomax". A "hold:"
 * prefix makes an entry the long-press action instead: "hold:KEYCODE_BUTTON_8:org.xbmc.kodi" opens Kodi when
 * that button is held for HOLD_MS, while the unprefixed entry still opens on a tap; "hold:scan:490:..." does
 * the same for a scancode. One set of maps for both services; whichever filters the key first consumes it.
 */
final class RemoteKeys {

    /** How long a button is held before its "hold:" entry wins (Android's long-press timeout). */
    static final long HOLD_MS = 500;

    private RemoteKeys() { }

    /** keycode -> package, with key names resolved by Android. */
    static Map<Integer, String> parseAppKeys(String[] entries) {
        return parseAppKeys(Arrays.asList(entries), KeyEvent::keyCodeFromString);
    }

    static Map<Integer, String> parseAppKeys(Iterable<String> entries) {
        return parseAppKeys(entries, KeyEvent::keyCodeFromString);
    }

    /**
     * keycode -> package. The key is a number or a KEYCODE_ name (resolved by {@code names}; 0, KEYCODE_UNKNOWN,
     * means it isn't one). Entries without a colon, with an unknown key or with no package are skipped; for a
     * keycode listed twice the last entry wins.
     */
    static Map<Integer, String> parseAppKeys(Iterable<String> entries, ToIntFunction<String> names) {
        Map<Integer, String> keys = new HashMap<>();
        for (String entry : entries) {
            if (entry == null) continue;
            String[] parts = entry.split(":", 2);
            if (parts.length != 2 || parts[1].trim().isEmpty()) continue;
            String key = parts[0].trim();
            int code;
            if (key.matches("[0-9]{1,6}")) {
                code = Integer.parseInt(key);
            } else if (key.matches("KEYCODE_[A-Z0-9_]+")) {
                code = names.applyAsInt(key);   // keyCodeFromString takes digits only from API 29, names always
            } else {
                continue;
            }
            if (code > 0) keys.put(code, parts[1].trim());
        }
        return keys;
    }

    /** keycode -> package from the "hold:" entries; the rest of each entry reads as it does in parseAppKeys. */
    static Map<Integer, String> parseHoldAppKeys(String[] entries) {
        return parseHoldAppKeys(Arrays.asList(entries), KeyEvent::keyCodeFromString);
    }

    static Map<Integer, String> parseHoldAppKeys(Iterable<String> entries) {
        return parseHoldAppKeys(entries, KeyEvent::keyCodeFromString);
    }

    static Map<Integer, String> parseHoldAppKeys(Iterable<String> entries, ToIntFunction<String> names) {
        return parseAppKeys(holdEntries(entries), names);
    }

    /**
     * scancode -> package, from the "scan:490:com.wbd.hbomax" entries of the same list. For remotes that send
     * several app buttons as one keycode and tell them apart by scancode (Sony BRAVIA: YouTube, Prime Video and
     * Disney+ are all keycode 196, scancodes 485, 490 and 491). Find it with `getevent -lt` while pressing the
     * button: the EV_KEY code, in hex. Other entries are skipped; for a scancode listed twice the last entry wins.
     */
    static Map<Integer, String> parseAppScancodes(Iterable<String> entries) {
        Map<Integer, String> keys = new HashMap<>();
        for (String entry : entries) {
            if (entry == null) continue;
            String[] parts = entry.split(":", 3);
            if (parts.length != 3 || !parts[0].trim().equals("scan") || parts[2].trim().isEmpty()) continue;
            String code = parts[1].trim();
            if (!code.matches("[0-9]{1,6}")) continue;
            int scan = Integer.parseInt(code);
            if (scan > 0) keys.put(scan, parts[2].trim());
        }
        return keys;
    }

    /** scancode -> package from the "hold:scan:..." entries; the rest reads as it does in parseAppScancodes. */
    static Map<Integer, String> parseHoldAppScancodes(Iterable<String> entries) {
        return parseAppScancodes(holdEntries(entries));
    }

    /** The entries with the "hold:" prefix removed, so the normal parsers can read them. */
    private static Iterable<String> holdEntries(Iterable<String> entries) {
        List<String> holds = new ArrayList<>();
        for (String entry : entries) {
            if (entry == null) continue;
            String text = entry.trim();
            if (text.startsWith("hold:")) holds.add(text.substring(5).trim());
        }
        return holds;
    }

    /**
     * Consumes a key mapped by either list. On release it opens the hold app after HOLD_MS, otherwise the normal
     * one; false means neither list has this key, so the caller can pass it on.
     */
    static boolean handle(Context ctx, KeyEvent event, String app, String holdApp) {
        if (app == null && holdApp == null) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            launch(ctx, target(app, holdApp, event.getEventTime() - event.getDownTime()));
        }
        return true;
    }

    /** The app a mapped key opens: its hold entry after HOLD_MS, otherwise its normal one. */
    static String target(String app, String holdApp, long heldMs) {
        return heldMs >= HOLD_MS && holdApp != null ? holdApp : app;
    }

    /** Opens an app from a remote button: its TV entry if it has one, else its phone entry. */
    static void launch(Context ctx, String pkg) {
        if (pkg == null) return;
        Intent open = ctx.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (open == null) open = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
        if (open == null) return;
        try {
            ctx.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) {
        }
    }
}
