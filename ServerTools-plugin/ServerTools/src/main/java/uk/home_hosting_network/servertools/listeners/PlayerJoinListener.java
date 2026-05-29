package uk.home_hosting_network.servertools.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import uk.home_hosting_network.servertools.ServerTools;
import uk.home_hosting_network.servertools.util.ColorUtil;

import java.util.List;

/**
 * Listens for players joining the server and sends them a private
 * welcome message pointing them to /rules and /help.
 *
 * CB1060 notes:
 *  - @EventHandler does NOT accept a priority = EventPriority.X attribute;
 *    EventPriority was an inner enum (Event.Priority) in this era, and
 *    @EventHandler with no arguments is the safe approach.
 *  - Message content is driven by config.yml via PluginConfig.
 */
public class PlayerJoinListener implements Listener {

    private final ServerTools plugin;

    public PlayerJoinListener(ServerTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getPluginConfig().getBoolean("join-message.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        List<String> lines = plugin.getPluginConfig().getStringList("join-message.lines");

        if (lines.isEmpty()) {
            // Hardcoded fallback if config is missing the lines key
            player.sendMessage("\u00A7eWelcome! Type \u00A7f/rules \u00A7eto read the server rules.");
            player.sendMessage("\u00A7aType \u00A7f/help \u00A7ato see all available commands.");
            return;
        }

        for (String line : lines) {
            player.sendMessage(ColorUtil.colorize(line));
        }
    }
}
