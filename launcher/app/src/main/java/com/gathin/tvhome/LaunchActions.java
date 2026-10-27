package com.gathin.tvhome;

import java.util.Map;
import java.util.function.BiFunction;

/** Selects a configured, package-scoped action only when it resolves; otherwise keeps the normal launch. */
final class LaunchActions {
    private LaunchActions() {}

    static <T> T select(String pkg, Map<String, String> actions, T fallback,
                        BiFunction<String, String, T> resolver) {
        String action = actions.get(pkg);
        if (action == null) return fallback;
        T custom = resolver.apply(pkg, action);
        return custom != null ? custom : fallback;
    }

    private static final String INPUT_PREFIX = "input:";

    /** The TV input id from an "input:<id>" launch_actions value, or null when the value isn't one. */
    static String inputId(String action) {
        return action != null && action.startsWith(INPUT_PREFIX) ? action.substring(INPUT_PREFIX.length()).trim() : null;
    }
}
