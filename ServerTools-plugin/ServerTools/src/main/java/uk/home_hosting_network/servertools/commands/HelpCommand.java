package uk.home_hosting_network.servertools.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import uk.home_hosting_network.servertools.ServerTools;
import uk.home_hosting_network.servertools.util.ColorUtil;

/**
 * /help — Lists all commands provided by ServerTools.
 */
public class HelpCommand implements CommandExecutor {

    private final ServerTools plugin;

    public HelpCommand(ServerTools plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(ColorUtil.header("Server Commands"));
        sender.sendMessage(ChatColor.YELLOW + "/players" + ChatColor.WHITE + " - Show all online players");
        sender.sendMessage(ChatColor.YELLOW + "/rules"   + ChatColor.WHITE + " - Show the server rules");
        sender.sendMessage(ChatColor.YELLOW + "/ticks"   + ChatColor.WHITE + " - Show current server TPS");
        sender.sendMessage(ChatColor.YELLOW + "/help"    + ChatColor.WHITE + " - Show this help message");
        return true;
    }
}
