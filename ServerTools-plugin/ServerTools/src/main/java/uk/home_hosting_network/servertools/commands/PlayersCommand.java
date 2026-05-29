package uk.home_hosting_network.servertools.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import uk.home_hosting_network.servertools.ServerTools;
import uk.home_hosting_network.servertools.util.ColorUtil;

/**
 * /players — Lists all currently online players.
 *
 * CB1060: Server.getOnlinePlayers() returns Player[] (not Collection).
 */
public class PlayersCommand implements CommandExecutor {

    private final ServerTools plugin;

    public PlayersCommand(ServerTools plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // CB1060 — returns Player[], not Collection
        Player[] online = plugin.getServer().getOnlinePlayers();
        int count = online.length;

        sender.sendMessage(ColorUtil.header("Online Players (" + count + ")"));

        if (count == 0) {
            sender.sendMessage(ChatColor.GRAY + "No players are currently online.");
        } else {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < online.length; i++) {
                if (i > 0) names.append(ChatColor.GRAY + ", ");
                names.append(ChatColor.WHITE).append(online[i].getName());
            }
            sender.sendMessage(names.toString());
        }

        return true;
    }
}
