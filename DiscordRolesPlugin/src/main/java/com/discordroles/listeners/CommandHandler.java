package com.discordroles.listeners;

import com.discordroles.DiscordRolesPlugin;
import com.discordroles.storage.DatabaseManager;
import com.discordroles.storage.PendingLink;
import com.discordroles.storage.PendingLinkManager;
import com.discordroles.utils.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles all in-game slash commands for DiscordRolesPlugin.
 *
 * Commands declared in plugin.yml:
 *   /discord confirm <code>  - Confirm a pending account link (main security step)
 *   /discordlink             - Prints linking instructions
 *   /discordunlink           - Removes the player's confirmed link
 *   /discordreload           - (OP) Reloads config and restarts bot
 *
 * Wired via JavaPlugin.onCommand() override for CB1060 compatibility.
 */
public class CommandHandler implements CommandExecutor {

    private final DiscordRolesPlugin plugin;
    private final DatabaseManager    db;
    private final PendingLinkManager pending;

    public CommandHandler(DiscordRolesPlugin plugin,
                          DatabaseManager db,
                          PendingLinkManager pending) {
        this.plugin  = plugin;
        this.db      = db;
        this.pending = pending;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Resolve command name from either command or label (CB1060 safety)
        String name = command.getName();
        if (name == null || name.isEmpty()) name = label;
        if (name == null) return false;
        name = name.toLowerCase().trim();

        switch (name) {
            case "discord":       return handleDiscord(sender, args);
            case "discordlink":   handleLink(sender);    return true;
            case "discordunlink": handleUnlink(sender);  return true;
            case "discordreload": handleReload(sender);  return true;
            default:              return false;
        }
    }

    // ---------------------------------------------------------------
    //  /discord confirm <code>   (the main security confirmation step)
    // ---------------------------------------------------------------

    private boolean handleDiscord(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }

        // Must be: /discord confirm <code>
        if (args.length < 2 || !args[0].equalsIgnoreCase("confirm")) {
            sender.sendMessage(ColorUtil.colorize(
                    "&cUsage: &f/discord confirm <code>"));
            return true;
        }

        Player player  = (Player) sender;
        String mcName  = player.getName();
        String entered = args[1].trim();

        // Already linked?
        if (db.isLinked(mcName)) {
            player.sendMessage(ColorUtil.colorize(
                    "&eYour account is already linked! "
                  + "Use &f/discordunlink &eif you need to change it."));
            return true;
        }

        // Is there a pending link for this Minecraft username?
        PendingLink link = pending.getByMinecraft(mcName);
        if (link == null) {
            player.sendMessage(ColorUtil.colorize(
                    "&cNo pending link found for your username."));
            player.sendMessage(ColorUtil.colorize(
                    "&7Go to the &f#link &7channel in our Discord and run &f/discordlink " + mcName));
            return true;
        }

        // Check the code — constant-time comparison to prevent timing attacks
        if (!constantTimeEquals(link.getCode(), entered)) {
            player.sendMessage(ColorUtil.colorize(
                    "&cIncorrect code. Please check the code sent to you in Discord."));
            player.sendMessage(ColorUtil.colorize(
                    "&7Your code expires in &f" + link.secondsRemaining() + "s&7."));
            return true;
        }

        // Code matches — check it hasn't expired (belt-and-suspenders, getByMinecraft
        // already returns null for expired, but check again after code comparison)
        if (link.isExpired()) {
            pending.remove(mcName);
            player.sendMessage(ColorUtil.colorize(
                    "&cYour confirmation code has expired."));
            player.sendMessage(ColorUtil.colorize(
                    "&7Please run &f/discordlink " + mcName + " &7in Discord again to get a new code."));
            return true;
        }

        // Final check: is the discord ID still not permanently linked?
        // (Handles edge case where two pending links race)
        if (db.isDiscordLinked(link.getDiscordId())) {
            pending.remove(mcName);
            player.sendMessage(ColorUtil.colorize(
                    "&cYour Discord account was already linked to another Minecraft name. "
                  + "Please run &f/resetlink &cin Discord first."));
            return true;
        }

        // All checks passed — commit the link
        boolean ok = db.linkAccount(mcName, link.getDiscordId());
        pending.remove(mcName);

        if (ok) {
            player.sendMessage(ColorUtil.colorize(
                    "&aSuccessfully linked! &7Your Discord roles will now show in chat."));
            plugin.log("Account linked: MC=" + mcName
                    + " ↔ Discord=" + link.getDiscordTag()
                    + " (" + link.getDiscordId() + ")");
        } else {
            player.sendMessage(ColorUtil.colorize(
                    "&cA storage error occurred. Please try again or contact an admin."));
            plugin.log("ERROR: Failed to save link for MC=" + mcName);
        }
        return true;
    }

    // ---------------------------------------------------------------
    //  /discordlink  (tells the player what to do in Discord)
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
                    "&eYour account is already linked! "
                  + "Use &f/discordunlink &eif you want to remove it."));
            return;
        }

        player.sendMessage(ColorUtil.colorize("&6&lDiscord Account Linking"));
        player.sendMessage(ColorUtil.colorize(
                "&71. Go to the &f#link &7channel in our Discord"));
        player.sendMessage(ColorUtil.colorize(
                "&72. Run: &f/discordlink " + name));
        player.sendMessage(ColorUtil.colorize(
                "&73. Come back here and run: &f/discord confirm <code>"));
    }

    // ---------------------------------------------------------------
    //  /discordunlink
    // ---------------------------------------------------------------

    private void handleUnlink(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used in-game.");
            return;
        }
        Player player = (Player) sender;
        String name   = player.getName();

        // Also clear any pending link
        pending.remove(name);

        if (!db.isLinked(name)) {
            player.sendMessage(ColorUtil.colorize(
                    "&eYou don't have a linked Discord account."));
            return;
        }

        db.unlinkByMinecraftName(name);
        plugin.log("Player " + name + " unlinked their Discord account in-game.");
        player.sendMessage(ColorUtil.colorize(
                "&aYour Discord account has been unlinked."));
    }

    // ---------------------------------------------------------------
    //  /discordreload  (OP only)
    // ---------------------------------------------------------------

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("discordroles.admin")) {
            sender.sendMessage(ColorUtil.colorize("&cYou don't have permission to do that."));
            return;
        }
        plugin.reloadPluginConfig();
        sender.sendMessage(ColorUtil.colorize("&aDiscordRoles reloaded."));
    }

    // ---------------------------------------------------------------
    //  Security helper
    // ---------------------------------------------------------------

    /**
     * Compares two strings in constant time to prevent timing-based attacks.
     * Even though a 6-digit code is not highly sensitive, it's good practice.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
