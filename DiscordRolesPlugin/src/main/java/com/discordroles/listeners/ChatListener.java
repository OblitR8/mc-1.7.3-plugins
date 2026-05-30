package com.discordroles.listeners;

import com.discordroles.DiscordRolesPlugin;
import com.discordroles.discord.DiscordBotManager;
import com.discordroles.utils.ColorUtil;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChatEvent;

import java.util.ArrayList;

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

    public ChatListener(DiscordRolesPlugin plugin, DiscordBotManager botManager) {
        this.plugin     = plugin;
        this.botManager = botManager;
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
            String template = plugin.cfg().getString(
                    "role-chat-format",
                    "&8[&b{role}&8] &f{player}&7: &f{message}"
            );
            formatted = ColorUtil.colorize(
                    template
                        .replace("{role}",    roleColour + roleName)
                        .replace("{player}",  playerName)
                        .replace("{message}", message)
            );
        } else {
            String template = plugin.cfg().getString(
                    "no-role-chat-format",
                    "&8[&7player&8] &f{player}&7: &f{message}"
            );
            formatted = ColorUtil.colorize(
                    template
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
        // In the old Configuration API, nested keys are accessed via dot notation
        String colourVal = plugin.cfg().getString("role-colours." + roleName, null);
        if (colourVal != null) {
            return ColorUtil.colorize(colourVal);
        }
        return ColorUtil.colorize(
                plugin.cfg().getString("default-role-colour", "&b")
        );
    }
}
