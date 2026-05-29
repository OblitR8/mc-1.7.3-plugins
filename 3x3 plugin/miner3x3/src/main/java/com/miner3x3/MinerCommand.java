package com.miner3x3;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class MinerCommand implements CommandExecutor {

    private final Miner3x3Plugin plugin;

    public MinerCommand(Miner3x3Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // ── OP only ───────────────────────────────────────────────────────────
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "Only OPs can use this command.");
            return true;
        }

        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "give":
                return handleGive(sender, args);
            case "revoke":
                return handleRevoke(sender, args);
            case "list":
                return handleList(sender);
            default:
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
            sender.sendMessage(ChatColor.YELLOW + target.getName() + " already has a Miner Pickaxe.");
            return true;
        }

        // Give a fresh diamond pickaxe at full durability
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE, 1);
        pickaxe.setDurability((short) 0);
        target.getInventory().addItem(pickaxe);
        target.updateInventory();

        // Register and persist
        plugin.minerPickaxeHolders.add(target.getUniqueId());
        plugin.saveHolders();

        sender.sendMessage(
            ChatColor.GREEN + "Gave " + ChatColor.AQUA + target.getName()
            + ChatColor.GREEN + " the 3x3 Miner Pickaxe."
        );
        target.sendMessage(
            ChatColor.DARK_GRAY + "[" + ChatColor.AQUA + "Miner3x3" + ChatColor.DARK_GRAY + "] "
            + ChatColor.WHITE + "You received the "
            + ChatColor.AQUA + "3x3 Miner Pickaxe"
            + ChatColor.WHITE + "! Hold your diamond pickaxe to mine 3x3. It will never break."
        );

        return true;
    }

    // /3x3 revoke <player>
    private boolean handleRevoke(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /3x3 revoke <player>");
            return true;
        }

        // Try online player first, then search by name in the UUID set
        Player target = Bukkit.getPlayer(args[1]);

        if (target != null) {
            boolean wasHolder = plugin.minerPickaxeHolders.remove(target.getUniqueId());
            plugin.saveHolders();
            if (wasHolder) {
                sender.sendMessage(ChatColor.GREEN + "Revoked Miner Pickaxe from " + ChatColor.AQUA + target.getName() + ChatColor.GREEN + ".");
                target.sendMessage(ChatColor.RED + "[Miner3x3] Your 3x3 Miner Pickaxe has been revoked.");
            } else {
                sender.sendMessage(ChatColor.YELLOW + target.getName() + " did not have a Miner Pickaxe.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Player '" + args[1] + "' is not online. Use their UUID with /3x3 revoke-uuid <uuid> if needed.");
        }

        return true;
    }

    // /3x3 list
    private boolean handleList(CommandSender sender) {
        if (plugin.minerPickaxeHolders.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "[Miner3x3] No holders registered.");
            return true;
        }

        sender.sendMessage(ChatColor.AQUA + "[Miner3x3] Registered holders (" + plugin.minerPickaxeHolders.size() + "):");
        for (java.util.UUID uuid : plugin.minerPickaxeHolders) {
            // CB1060 has no Bukkit.getPlayer(UUID) — scan online players manually
            Player online = null;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getUniqueId().equals(uuid)) {
                    online = p;
                    break;
                }
            }
            String name = online != null ? online.getName() : uuid.toString();
            String status = online != null ? ChatColor.GREEN + " (online)" : ChatColor.GRAY + " (offline)";
            sender.sendMessage(ChatColor.WHITE + " - " + name + status);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "--- Miner3x3 Commands ---");
        sender.sendMessage(ChatColor.YELLOW + "/3x3 give <player>" + ChatColor.WHITE + "   - Give a player the miner pickaxe");
        sender.sendMessage(ChatColor.YELLOW + "/3x3 revoke <player>" + ChatColor.WHITE + " - Remove miner pickaxe from a player");
        sender.sendMessage(ChatColor.YELLOW + "/3x3 list" + ChatColor.WHITE + "            - List all registered holders");
    }
}
