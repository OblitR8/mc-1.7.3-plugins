package uk.co.homehosting.discordwebhook;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Sends JSON payloads to a Discord webhook URL.
 *
 * - No file / base64 logic — avatar is a plain https:// URL in config.yml.
 * - allowed_mentions: {"parse":[]} on every payload so @everyone / @here /
 *   @role / @user text passes through visually but fires zero notifications.
 * - Zero-width space injected after every @ as a second-layer defuse.
 * - All HTTP calls run on a daemon thread — main thread is never blocked.
 * - No external dependencies; only java.net / java.io.
 */
public class WebhookClient {

    private final DiscordWebhookPlugin plugin;

    public WebhookClient(DiscordWebhookPlugin plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public void sendChat(String playerName, String message) {
        PluginConfig cfg = plugin.getDiscordConfig();
        String content = cfg.getChatFormat()
                .replace("{player}", escapeMarkdown(playerName))
                .replace("{message}", sanitiseMentions(escapeMarkdown(message)));
        dispatch(cfg, content, cfg.getEmbedColorChat());
    }

    public void sendJoin(String playerName) {
        PluginConfig cfg = plugin.getDiscordConfig();
        String content = cfg.getJoinFormat()
                .replace("{player}", escapeMarkdown(playerName));
        dispatch(cfg, content, cfg.getEmbedColorJoin());
    }

    public void sendLeave(String playerName) {
        PluginConfig cfg = plugin.getDiscordConfig();
        String content = cfg.getLeaveFormat()
                .replace("{player}", escapeMarkdown(playerName));
        dispatch(cfg, content, cfg.getEmbedColorLeave());
    }

    // -----------------------------------------------------------------------
    // Dispatch
    // -----------------------------------------------------------------------

    private void dispatch(PluginConfig cfg, String content, int embedColor) {
        String webhookUrl = cfg.getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isEmpty()
                || webhookUrl.contains("YOUR_WEBHOOK")) {
            plugin.logWarning("Webhook URL is not configured — edit config.yml.");
            return;
        }

        final String json = cfg.isUseEmbeds()
                ? buildEmbedPayload(cfg.getBotUsername(), cfg.getBotAvatarUrl(), content, embedColor)
                : buildMessagePayload(cfg.getBotUsername(), cfg.getBotAvatarUrl(), content);

        final String url = webhookUrl;

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    post(url, json);
                } catch (Exception e) {
                    plugin.logWarning("Failed to send Discord webhook: " + e.getMessage());
                }
            }
        });
        t.setDaemon(true);
        t.setName("DiscordWebhook-Sender");
        t.start();
    }

    private void post(String webhookUrl, String json) throws Exception {
        URL url = new URL(webhookUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("User-Agent", "DiscordWebhook-B173/1.0");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));

        OutputStream os = conn.getOutputStream();
        os.write(bytes);
        os.flush();
        os.close();

        int code = conn.getResponseCode();
        if (code == 429) {
            plugin.logWarning("Discord rate limit hit (HTTP 429) — message dropped.");
        } else if (code < 200 || code >= 300) {
            plugin.logWarning("Discord webhook returned HTTP " + code + ".");
        }
        conn.disconnect();
    }

    // -----------------------------------------------------------------------
    // JSON builders
    // -----------------------------------------------------------------------

    /**
     * Plain-text message.
     * allowed_mentions with parse:[] lets @-text render but fires no pings.
     */
    private String buildMessagePayload(String username, String avatarUrl, String content) {
        StringBuilder sb = new StringBuilder("{");
        appendKV(sb, "username", username);
        sb.append(",");
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            appendKV(sb, "avatar_url", avatarUrl);
            sb.append(",");
        }
        appendKV(sb, "content", content);
        sb.append(",");
        sb.append(allowedMentions());
        sb.append("}");
        return sb.toString();
    }

    /** Embed message. */
    private String buildEmbedPayload(String username, String avatarUrl,
                                      String description, int color) {
        StringBuilder sb = new StringBuilder("{");
        appendKV(sb, "username", username);
        sb.append(",");
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            appendKV(sb, "avatar_url", avatarUrl);
            sb.append(",");
        }
        sb.append("\"embeds\":[{");
        appendKV(sb, "description", description);
        sb.append(",\"color\":").append(color);
        sb.append("}],");
        sb.append(allowedMentions());
        sb.append("}");
        return sb.toString();
    }

    /** Discord API field: suppress all mention notifications. */
    private String allowedMentions() {
        return "\"allowed_mentions\":{\"parse\":[]}";
    }

    /** Append a JSON string key:value pair. */
    private void appendKV(StringBuilder sb, String key, String value) {
        sb.append("\"").append(jsonEscape(key)).append("\":")
          .append("\"").append(jsonEscape(value)).append("\"");
    }

    // -----------------------------------------------------------------------
    // Text sanitisation
    // -----------------------------------------------------------------------

    /**
     * Insert a zero-width space (U+200B) after every @ as a visual defuse
     * on top of allowed_mentions. Invisible to readers, breaks the mention token.
     */
    private String sanitiseMentions(String s) {
        if (s == null) return "";
        return s.replace("@", "@\u200B");
    }

    /** Strip Minecraft colour/formatting codes (§x). */
    private String stripColor(String s) {
        if (s == null) return "";
        return s.replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "");
    }

    /**
     * Escape Discord markdown characters so player names/messages don't
     * accidentally render as bold/italic/spoiler/etc.
     */
    private String escapeMarkdown(String s) {
        if (s == null) return "";
        s = stripColor(s);
        return s.replace("\\", "\\\\")
                .replace("*",  "\\*")
                .replace("_",  "\\_")
                .replace("~",  "\\~")
                .replace("`",  "\\`")
                .replace("|",  "\\|")
                .replace(">",  "\\>");
    }

    /** Escape characters that would break a JSON string literal. */
    private String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
