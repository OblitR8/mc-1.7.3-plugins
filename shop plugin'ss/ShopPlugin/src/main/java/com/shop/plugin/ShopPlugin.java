package com.shop.plugin;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.logging.Logger;

public class ShopPlugin extends JavaPlugin {

    private static final Logger log = Logger.getLogger("Minecraft");
    private ShopManager shopManager;

    @Override
    public void onEnable() {
        this.shopManager = new ShopManager(this);
        log.info("[DiamondShop] Enabled! Type /shop to open the shop.");
    }

    @Override
    public void onDisable() {
        log.info("[DiamondShop] Disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use the shop.");
            return true;
        }

        Player player = (Player) sender;

        if (!cmd.getName().equalsIgnoreCase("shop")) {
            return false;
        }

        // /shop  — open the menu
        if (args.length == 0) {
            shopManager.openShop(player);
            return true;
        }

        // /shop buy <number>
        if (args[0].equalsIgnoreCase("buy")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /shop buy <item number>");
                return true;
            }
            try {
                int slot = Integer.parseInt(args[1]);
                shopManager.handleBuy(player, slot);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Please enter a valid number. E.g. /shop buy 1");
            }
            return true;
        }

        // Unknown sub-command — just show the menu
        shopManager.openShop(player);
        return true;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }
}
