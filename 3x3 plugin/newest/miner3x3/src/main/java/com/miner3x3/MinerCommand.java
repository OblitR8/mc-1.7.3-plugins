package com.miner3x3;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class MinerCommand implements CommandExecutor {

    private final Miner3x3Plugin plugin;

    public MinerCommand(Miner3x3Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "Only OPs can use this command.");
            return true;
        }

        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("give")) {
            return handleGive(sender, args);
        } else if (sub.equals("revoke")) {
            return handleRevoke(sender, args);
        } else if (sub.equals("list")) {
            return handleList(sender);
        } else {
            sendHelp(sender);
            return true;
        }
    }

    // /3x3 give <player>
    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /3x3 give <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player '" + args[1] + "' is not online.");
            return true;
        }

        if (plugin.minerPickaxeHolders.contains(target.getUniqueId())) {
            sender.sendMessage(ChatColor.YELLOW + target.getName() + " already has the Miner Pickaxe.");
            return true;
        }

        // Give a gold pickaxe at full durability
        ItemStack pickaxe = new ItemStack(Material.GOLD_PICKAXE, 1);
        pickaxe.setDurability((short) 0);
        target.getInventory().addItem(pickaxe);
        target.updateInventory();

        plugin.minerPickaxeHolders.add(target.getUniqueId());
        plugin.saveHolders();

        sender.sendMessage(
            ChatColor.GREEN + "Gave " + ChatColor.AQUA + target.getName()
            + ChatColor.GREEN + " the 3x3 Miner Pickaxe."
        );
        target.sendMessage(
            ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "Miner3x3" + ChatColor.DARK_GRAY + "] "
            + ChatColor.WHITE + "You received the "
            + ChatColor.GOLD + "3x3 Miner Pickaxe"
            + ChatColor.WHITE + "! Hold your gold pickaxe to mine 3x3. It will never break."
        );

        return true;
    }

    // /3x3 revoke <player>
    private boolean handleRevoke(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /3x3 revoke <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player '" + args[1] + "' is not online.");
            return true;
        }

        boolean wasHolder = plugin.minerPickaxeHolders.remove(target.getUniqueId());
        plugin.saveHolders();

        if (wasHolder) {
            sender.sendMessage(
                ChatColor.GREEN + "Revoked Miner Pickaxe from "
                + ChatColor.AQUA + target.getName() + ChatColor.GREEN + "."
            );
            target.sendMessage(
                ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "Miner3x3" + ChatColor.DARK_GRAY + "] "
                + ChatColor.RED + "Your 3x3 Miner Pickaxe has been revoked."
            );
        } else {
            sender.sendMessage(ChatColor.YELLOW + target.getName() + " did not have the Miner Pickaxe.");
        }

        return true;
    }

    // /3x3 list
    private boolean handleList(CommandSender sender) {
        if (plugin.minerPickaxeHolders.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "[Miner3x3] No holders registered.");
            return true;
        }

        sender.sendMessage(
            ChatColor.GOLD + "[Miner3x3] " + ChatColor.WHITE
            + "Registered holders (" + plugin.minerPickaxeHolders.size() + "):"
        );

        for (UUID uuid : plugin.minerPickaxeHolders) {
            // CB1060 has no Bukkit.getPlayer(UUID) — match manually
            Player online = null;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getUniqueId().equals(uuid)) {
                    online = p;
                    break;
                }
            }
            String name   = (online != null) ? online.getName() : uuid.toString();
            String status = (online != null)
                ? ChatColor.GREEN + " (online)"
                : ChatColor.GRAY  + " (offline)";
            sender.sendMessage(ChatColor.WHITE + "  - " + name + status);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- Miner3x3 Commands ---");
        sender.sendMessage(ChatColor.YELLOW + "/3x3 give <player>"   + ChatColor.WHITE + "   - Give the 3x3 Miner Pickaxe");
        sender.sendMessage(ChatColor.YELLOW + "/3x3 revoke <player>" + ChatColor.WHITE + " - Revoke the 3x3 Miner Pickaxe");
        sender.sendMessage(ChatColor.YELLOW + "/3x3 list"            + ChatColor.WHITE + "            - List all holders");
    }
}
