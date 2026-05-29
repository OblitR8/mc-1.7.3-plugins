package uk.home_hosting_network.servertools.util;

import org.bukkit.ChatColor;

/**
 * Utility to translate '&' colour codes into ChatColor equivalents.
 *
 * ChatColor.translateAlternateColorCodes() was added in a later Bukkit version
 * and does not exist in CB1060. This replacement covers the same job.
 */
public final class ColorUtil {

    private ColorUtil() {}

    /**
     * Replaces {@code &X} sequences (where X is a valid colour/format char)
     * with the equivalent §X sequence that Minecraft understands.
     *
     * @param text raw string possibly containing & colour codes
     * @return formatted string with § codes
     */
    public static String colorize(String text) {
        if (text == null) return "";
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRr".indexOf(chars[i + 1]) > -1) {
                chars[i] = '\u00A7';           // §
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    /**
     * Convenience: build a gold header line.
     */
    public static String header(String title) {
        return ChatColor.GOLD + "--- " + title + " ---";
    }
}
