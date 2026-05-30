package com.discordroles.listeners;

import com.discordroles.DiscordRolesPlugin;
import com.discordroles.storage.DatabaseManager;
import com.discordroles.utils.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles in-game slash commands.
 *
 *   /discordlink    – Tells the player how to link via Discord
 *   /discordunlink  – Unlinks the player's account from Discord
 *   /discordreload  – (OP only) Reloads config and restarts the bot
 *
 * Compatible with Project Poseidon (CB1060 Bukkit API).
 * Uses plugin.cfg() instead of plugin.getConfig().
 */
public class CommandHandler implements CommandExecutor {

    private final DiscordRolesPlugin plugin;
    private final DatabaseManager db;

    public CommandHandler(DiscordRolesPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db     = db;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "discordlink":   handleLink(sender);   return true;
            case "discordunlink": handleUnlink(sender); return true;
            case "discordreload": handleReload(sender); return true;
            default:              return false;
        }
    }

    // ---------------------------------------------------------------

    private void handleLink(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used in-game.");
            return;
        }
        Player player = (Player) sender;
        String name   = player.getName();

        if (db.isLinked(name)) {
            player.sendMessage(ColorUtil.colorize(
                    plugin.cfg().getString("messages.already-linked",
                            "&eYour account is already linked! Use /discordunlink to unlink first.")
            ));
            return;
        }

        player.sendMessage(ColorUtil.colorize(
                plugin.cfg().getString("messages.link-instructions",
                        "&aGo to the #link channel in our Discord and type: !link " + name)
        ));
    }

    // ---------------------------------------------------------------

    private void handleUnlink(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used in-game.");
            return;
        }
        Player player = (Player) sender;
        String name   = player.getName();

        if (!db.isLinked(name)) {
            player.sendMessage(ColorUtil.colorize(
                    plugin.cfg().getString("messages.not-linked",
                            "&eYou don't have a linked Discord account.")
            ));
            return;
        }

        db.unlinkByMinecraftName(name);
        plugin.log("Player " + name + " unlinked their Discord account.");
        player.sendMessage(ColorUtil.colorize(
                plugin.cfg().getString("messages.unlink-success",
                        "&aYour Discord account has been successfully unlinked.")
        ));
    }

    // ---------------------------------------------------------------

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("discordroles.admin")) {
            sender.sendMessage(ColorUtil.colorize("&cYou don't have permission to do that."));
            return;
        }

        plugin.reloadPluginConfig();
        sender.sendMessage(ColorUtil.colorize(
                plugin.cfg().getString("messages.reload-success",
                        "&aDiscordRoles configuration reloaded.")
        ));
    }
}
