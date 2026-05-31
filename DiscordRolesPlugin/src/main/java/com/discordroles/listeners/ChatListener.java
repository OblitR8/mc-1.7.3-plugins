package com.discordroles.listeners;

import com.discordroles.DiscordRolesPlugin;
import com.discordroles.discord.DiscordBotManager;
import com.discordroles.utils.ColorUtil;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.util.config.Configuration;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("deprecation")
public class ChatListener implements Listener {

    private final DiscordRolesPlugin plugin;
    private final DiscordBotManager  botManager;
    private final String             roleChatTemplate;
    private final String             noRoleChatTemplate;
    private final String             defaultRoleColour;
    private final Map<String, String> roleColours = new HashMap<>();

    public ChatListener(DiscordRolesPlugin plugin, DiscordBotManager botManager) {
        this.plugin     = plugin;
        this.botManager = botManager;

        // Force config off disk before reading any values
        plugin.cfg().load();

        Configuration cfg = plugin.cfg();

        this.roleChatTemplate = getString(cfg,
                "role-chat-format",
                "&8[&b{role}&8] &f{player}&7: &f{message}");

        this.noRoleChatTemplate = getString(cfg,
                "no-role-chat-format",
                "&8[&7Player&8] &f{player}&7: &f{message}");

        this.defaultRoleColour = ColorUtil.colorize(
                getString(cfg, "default-role-colour", "&b"));

        // CB1060: getKeys() returns ALL top-level keys — no path argument exists.
        // We filter down to role-colours.* manually.
        java.util.List<String> allKeys = cfg.getKeys();
        if (allKeys != null) {
            final String PREFIX = "role-colours.";
            for (String key : allKeys) {
                if (key.startsWith(PREFIX)) {
                    String roleName = key.substring(PREFIX.length()).toLowerCase();
                    String value    = cfg.getString(key, null);
                    if (value != null && !roleName.isEmpty()) {
                        roleColours.put(roleName, ColorUtil.colorize(value));
                    }
                }
            }
        }
    }

    @EventHandler(priority = Event.Priority.Highest)
    public void onPlayerChat(PlayerChatEvent event) {
        if (event.isCancelled()) return;

        String playerName = event.getPlayer().getName();
        String message    = event.getMessage();
        String roleName   = botManager.getRoleForPlayer(playerName);

        String formatted;
        if (roleName != null) {
            String roleColour = getRoleColour(roleName);
            formatted = ColorUtil.colorize(
                    roleChatTemplate
                            .replace("{role}",    roleColour + roleName)
                            .replace("{player}",  playerName)
                            .replace("{message}", message)
            );
        } else {
            formatted = ColorUtil.colorize(
                    noRoleChatTemplate
                            .replace("{player}",  playerName)
                            .replace("{message}", message)
            );
        }

        event.setFormat(formatted.replace("%", "%%"));
    }

    // ---------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------

    private String getRoleColour(String roleName) {
        String colour = roleColours.get(roleName.toLowerCase());
        return colour != null ? colour : defaultRoleColour;
    }

    /**
     * CB1060 Configuration#getString can return null even when a default
     * is passed, depending on the implementation build. This wrapper
     * guarantees a non-null result.
     */
    private static String getString(Configuration cfg, String path, String fallback) {
        String val = cfg.getString(path, fallback);
        return val != null ? val : fallback;
    }
}