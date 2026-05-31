package com.discordroles.listeners;

import com.discordroles.DiscordRolesPlugin;
import com.discordroles.discord.DiscordBotManager;
import com.discordroles.utils.ColorUtil;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChatEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Reformats Minecraft player chat based on the player's linked Discord role.
 *
 * Project Poseidon (CB1060) differences:
 *   - @EventHandler uses Event.Priority (NOT EventPriority)
 *   - plugin.cfg() used instead of plugin.getConfig()
 */
@SuppressWarnings("deprecation")
public class ChatListener implements Listener {

    private final DiscordRolesPlugin plugin;
    private final DiscordBotManager botManager;
    private final String roleChatTemplate;
    private final String noRoleChatTemplate;
    private final String defaultRoleColour;
    private final Map<String, String> roleColours = new HashMap<>();

    public ChatListener(DiscordRolesPlugin plugin, DiscordBotManager botManager) {
        this.plugin     = plugin;
        this.botManager = botManager;

        this.roleChatTemplate = plugin.cfg().getString(
                "role-chat-format",
                "&8[&b{role}&8] &f{player}&7: &f{message}"
        );
        this.noRoleChatTemplate = plugin.cfg().getString(
                "no-role-chat-format",
                "&8[&7player&8] &f{player}&7: &f{message}"
        );
        this.defaultRoleColour = ColorUtil.colorize(
                plugin.cfg().getString("default-role-colour", "&b")
        );

        java.util.List<String> keys = plugin.cfg().getKeys("role-colours");
        if (keys != null) {
            for (String key : keys) {
                String value = plugin.cfg().getString("role-colours." + key, null);
                if (value != null) {
                    roleColours.put(key.toLowerCase(), ColorUtil.colorize(value));
                }
            }
        }
    }

    // Event.Priority.Highest is the CB1060 enum value (capital-case, not ALL_CAPS)
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

        // setFormat uses printf-style %1$s / %2$s placeholders in the old API
        // We pass a fully-built string and escape any % signs to avoid format errors
        event.setFormat(formatted.replace("%", "%%"));
    }

    // ---------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------

    private String getRoleColour(String roleName) {
        String colourVal = roleColours.get(roleName.toLowerCase());
        return colourVal != null ? colourVal : defaultRoleColour;
    }
}
