package uk.home_hosting_network.servertools.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import uk.home_hosting_network.servertools.ServerTools;
import uk.home_hosting_network.servertools.util.ColorUtil;

import java.util.List;

/**
 * /rules — Displays server rules loaded from config.yml.
 */
public class RulesCommand implements CommandExecutor {

    private final ServerTools plugin;

    public RulesCommand(ServerTools plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<String> rules = plugin.getPluginConfig().getStringList("rules");

        sender.sendMessage(ColorUtil.header("Server Rules"));

        if (rules.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No rules have been configured yet.");
        } else {
            for (String rule : rules) {
                sender.sendMessage(ColorUtil.colorize(rule));
            }
        }

        return true;
    }
}
