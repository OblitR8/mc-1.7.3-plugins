package com.discordroles.utils;

import java.util.List;

/**
 * Small helper utilities used across the plugin.
 */
public final class ColorUtil {

    private ColorUtil() {}

    /**
     * Translates '&' colour codes into Minecraft's § format codes.
     */
    public static String colorize(String input) {
        if (input == null) return "";
        return input.replace("&", "\u00A7");
    }

    /**
     * Strips all § colour codes from a string.
     */
    public static String strip(String input) {
        if (input == null) return "";
        return input.replaceAll("\u00A7[0-9a-fk-orA-FK-OR]", "");
    }

    /**
     * Checks whether the given role name should be treated as "no role".
     */
    public static boolean isIgnoredRole(String roleName, List<String> ignoredRoles) {
        for (String ignored : ignoredRoles) {
            if (ignored.equalsIgnoreCase(roleName)) return true;
        }
        return false;
    }
}
