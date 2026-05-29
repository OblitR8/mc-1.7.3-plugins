package com.spectate;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Unified /sp command — all sub-commands, OP-only.
 *
 *   /sp <player>  — start spectating
 *   /sp stop      — stop spectating, return to saved location
 *   /sp follow    — toggle follow mode
 *   /sp god       — toggle god mode (invulnerability)
 *   /sp freeze    — toggle target movement freeze
 *   /sp tp        — teleport to target immediately
 *   /sp status    — show current session info
 *   /sp help      — list all commands
 */
public class SpectateCommand implements CommandExecutor {

    private final SpectatePlugin  plugin;
    private final SpectateManager mgr;

    public SpectateCommand(SpectatePlugin plugin, SpectateManager mgr) {
        this.plugin = plugin;
        this.mgr    = mgr;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        // Console cannot use this
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "[Spectate] Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        // All sub-commands require OP
        if (!player.isOp()) {
            player.sendMessage(ChatColor.RED + "[Spectate] You do not have permission.");
            return true;
        }

        // /sp with no args → help
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {

            // ----------------------------------------
            case "help":
                sendHelp(player);
                return true;

            // ----------------------------------------
            case "stop":
                if (!mgr.isSpectating(player)) {
                    player.sendMessage(ChatColor.YELLOW + "[Spectate] You are not spectating anyone.");
                } else {
                    mgr.stopSpectating(player);
                }
                return true;

            // ----------------------------------------
            case "follow":
                if (requireSession(player)) mgr.toggleFollow(player);
                return true;

            // ----------------------------------------
            case "god":
                if (requireSession(player)) mgr.toggleGod(player);
                return true;

            // ----------------------------------------
            case "freeze":
                if (requireSession(player)) mgr.toggleFreeze(player);
                return true;

            // ----------------------------------------
            case "tp":
                if (requireSession(player)) mgr.teleportToTarget(player);
                return true;

            // ----------------------------------------
            case "status":
                mgr.showStatus(player);
                return true;

            // ----------------------------------------
            default:
                // Treat the argument as a player name → start spectating
                startSpectate(player, args[0]);
                return true;
        }
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private void startSpectate(Player player, String targetName) {
        if (player.getName().equalsIgnoreCase(targetName)) {
            player.sendMessage(ChatColor.RED + "[Spectate] You cannot spectate yourself.");
            return;
        }

        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            player.sendMessage(ChatColor.RED + "[Spectate] Player '" + targetName + "' is not online.");
            return;
        }

        mgr.startSpectating(player, target);
    }

    /**
     * Checks the player has an active session.
     * Sends a message and returns false if not.
     */
    private boolean requireSession(Player player) {
        if (!mgr.isSpectating(player)) {
            player.sendMessage(ChatColor.YELLOW + "[Spectate] You are not spectating anyone. Use /sp <player> first.");
            return false;
        }
        return true;
    }

    private void sendHelp(Player p) {
        String H = ChatColor.GOLD.toString();
        String C = ChatColor.YELLOW.toString();
        String D = ChatColor.WHITE.toString();
        String G = ChatColor.GRAY.toString();
        p.sendMessage(H + "========= Spectate (OP only) =========");
        p.sendMessage(C + "/sp <player>  " + D + "Silently spectate a player");
        p.sendMessage(C + "/sp stop      " + D + "Stop spectating, return to saved location");
        p.sendMessage(C + "/sp follow    " + D + "Toggle follow mode " + G + "(tracks target automatically)");
        p.sendMessage(C + "/sp god       " + D + "Toggle god mode "   + G + "(invulnerability)");
        p.sendMessage(C + "/sp freeze    " + D + "Toggle target freeze " + G + "(locks their position)");
        p.sendMessage(C + "/sp tp        " + D + "Teleport to target now");
        p.sendMessage(C + "/sp status    " + D + "Show current session flags");
        p.sendMessage(C + "/sp help      " + D + "Show this message");
        p.sendMessage(H + "Defaults: Follow " + ChatColor.GREEN + "ON" + H + "  God " + ChatColor.GREEN + "ON" + H + "  Freeze " + ChatColor.RED + "OFF");
    }
}
