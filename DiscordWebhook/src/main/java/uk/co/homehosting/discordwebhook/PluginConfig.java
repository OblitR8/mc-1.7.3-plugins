package uk.co.homehosting.discordwebhook;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal hand-rolled config reader for top-level key: value YAML pairs.
 *
 * Handles:
 *  - Quoted values ("..." or '...')
 *  - URLs containing colons (splits on ": " not just ":")
 *  - Inline comments after a value
 *  - Lines starting with # ignored
 */
public class PluginConfig {

    private final Map<String, String> values = new LinkedHashMap<String, String>();

    // Hardcoded defaults — used when a key is missing or blank
    private static final String  DEF_WEBHOOK     = "";
    private static final String  DEF_USERNAME    = "Minecraft";
    private static final String  DEF_AVATAR      = "";
    private static final String  DEF_CHAT_FMT    = "**{player}** \u00bb {message}";
    private static final String  DEF_JOIN_FMT    = "\u2705 **{player}** joined the server.";
    private static final String  DEF_LEAVE_FMT   = "\u274c **{player}** left the server.";
    private static final boolean DEF_EMBEDS      = false;
    private static final int     DEF_COLOR_CHAT  = 5793266;
    private static final int     DEF_COLOR_JOIN  = 5025616;
    private static final int     DEF_COLOR_LEAVE = 15158332;

    public PluginConfig(File dataFolder) {
        File f = new File(dataFolder, "config.yml");
        if (!f.exists()) return;
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                // Strip trailing whitespace / Windows \r
                line = line.trim();
                // Skip comments and blank lines
                if (line.isEmpty() || line.startsWith("#")) continue;

                // Split on the FIRST ": " (colon-space) so URLs are preserved.
                // Falls back to first ":" if no colon-space found (boolean/int values).
                int sep = line.indexOf(": ");
                if (sep < 0) sep = line.indexOf(':');
                if (sep < 0) continue;

                String key = line.substring(0, sep).trim();
                String val = line.substring(sep + (line.charAt(sep + 1) == ' ' ? 2 : 1)).trim();

                // Strip surrounding quotes
                if (val.length() >= 2) {
                    char first = val.charAt(0);
                    char last  = val.charAt(val.length() - 1);
                    if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                        val = val.substring(1, val.length() - 1);
                    }
                }

                // Strip inline comment (space + #)
                int commentIdx = val.indexOf(" #");
                if (commentIdx >= 0) {
                    val = val.substring(0, commentIdx).trim();
                }

                if (!key.isEmpty()) {
                    values.put(key, val);
                }
            }
            reader.close();
        } catch (IOException e) {
            // Leave all defaults in place — plugin will warn about missing URL
        }
    }

    // -----------------------------------------------------------------------
    // Typed accessors
    // -----------------------------------------------------------------------

    private String getString(String key, String def) {
        String v = values.get(key);
        return (v != null && !v.isEmpty()) ? v : def;
    }

    private boolean getBoolean(String key, boolean def) {
        String v = values.get(key);
        if (v == null) return def;
        return v.equalsIgnoreCase("true");
    }

    private int getInt(String key, int def) {
        String v = values.get(key);
        if (v == null) return def;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return def; }
    }

    // -----------------------------------------------------------------------
    // Config properties
    // -----------------------------------------------------------------------

    public String  getWebhookUrl()    { return getString ("webhook-url",    DEF_WEBHOOK);     }
    public String  getBotUsername()   { return getString ("bot-username",   DEF_USERNAME);    }
    public String  getBotAvatarUrl()  { return getString ("bot-avatar-url", DEF_AVATAR);      }
    public String  getChatFormat()    { return getString ("chat-format",    DEF_CHAT_FMT);    }
    public String  getJoinFormat()    { return getString ("join-format",    DEF_JOIN_FMT);    }
    public String  getLeaveFormat()   { return getString ("leave-format",   DEF_LEAVE_FMT);   }
    public boolean isUseEmbeds()      { return getBoolean("use-embeds",     DEF_EMBEDS);      }
    public int     getEmbedColorChat()  { return getInt("embed-color-chat",  DEF_COLOR_CHAT);  }
    public int     getEmbedColorJoin()  { return getInt("embed-color-join",  DEF_COLOR_JOIN);  }
    public int     getEmbedColorLeave() { return getInt("embed-color-leave", DEF_COLOR_LEAVE); }
}
