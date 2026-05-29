package com.shop.plugin;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the shop's item listings and purchase transactions.
 * Currency: Diamonds (item ID 264).
 *
 * Beta 1.7.3 / CraftBukkit b1060 (Project Poseidon) compatible.
 * No ChatColor.BOLD/RESET — raw §l/§r format codes used instead.
 */
public class ShopManager {

    private static final int DIAMOND_ID    = 264;
    private static final int GOLDEN_AXE_ID = 286;

    // Raw format codes — ChatColor.BOLD/RESET not in b1060
    private static final String BOLD  = "\u00a7l";
    private static final String RESET = "\u00a7r";

    private final ShopPlugin      plugin;
    private final List<ShopItem>  items;

    public ShopManager(ShopPlugin plugin) {
        this.plugin = plugin;
        this.items  = new ArrayList<ShopItem>();
        registerItems();
    }

    private void registerItems() {
        items.add(new ShopItem("Wool x64",                          35,  64,  8));
        items.add(new ShopItem("Golden Axe Tree-Chopper (1 hour)", -1,   1, 32));
        items.add(new ShopItem("Steak x16",                   320, 6, 2));
        items.add(new ShopItem("TNT x16",             46,  64, 24));
    }

    // ------------------------------------------------------------------
    //  Commands
    // ------------------------------------------------------------------

    public void openShop(Player player) {
        int diamonds = countDiamonds(player);
        player.sendMessage(ChatColor.GOLD + "══════════ " + BOLD + "Diamond Shop" + RESET +
            ChatColor.GOLD + " ══════════");
        player.sendMessage(ChatColor.GRAY + "  Use " + ChatColor.WHITE + "/shop buy <#>" +
            ChatColor.GRAY + " to purchase.");
        player.sendMessage(ChatColor.GRAY + "  Your diamonds: " +
            ChatColor.AQUA + BOLD + diamonds + RESET);
        player.sendMessage("");

        for (int i = 0; i < items.size(); i++) {
            ShopItem item = items.get(i);
            String   note = (item.getItemId() == -1)
                ? ChatColor.DARK_GRAY + " (gives Golden Axe + 1hr tree-chop buff)"
                : "";
            player.sendMessage(
                ChatColor.YELLOW + "  [" + (i + 1) + "] " +
                ChatColor.WHITE  + item.getDisplayName() +
                ChatColor.GRAY   + " \u2014 " +
                ChatColor.AQUA   + item.getDiamondCost() + " \u25c6" +
                note
            );
        }

        player.sendMessage(ChatColor.GOLD + "══════════════════════════════");
    }

    public void handleBuy(Player player, int slot) {
        int index = slot - 1;

        if (index < 0 || index >= items.size()) {
            player.sendMessage(ChatColor.RED + "Invalid item number. Use /shop to see the list.");
            return;
        }

        ShopItem item     = items.get(index);
        int      cost     = item.getDiamondCost();
        int      diamonds = countDiamonds(player); // count once, reuse

        if (diamonds < cost) {
            player.sendMessage(ChatColor.RED + "You need " + cost +
                " diamond(s) but only have " + diamonds + ".");
            return;
        }

        removeDiamonds(player, cost);

        if (item.getItemId() == -1) {
            handleGoldenAxeBuff(player, item, cost);
        } else {
            giveItem(player, item.getItemId(), item.getQuantity());
            player.sendMessage(ChatColor.GREEN + "Purchased " + ChatColor.YELLOW +
                item.getDisplayName() + ChatColor.GREEN + " for " +
                ChatColor.AQUA + cost + ChatColor.GREEN + " diamond(s)!");
        }
    }

    // ------------------------------------------------------------------
    //  Golden Axe buff purchase
    // ------------------------------------------------------------------

    private void handleGoldenAxeBuff(Player player, ShopItem item, int cost) {
        Plugin raw = plugin.getServer().getPluginManager().getPlugin("TreeChop");

        // Guard: plugin missing
        if (raw == null || !raw.isEnabled()) {
            player.sendMessage(ChatColor.RED +
                "[Shop] TreeChop plugin is not loaded! Refunding diamonds. Contact an admin.");
            giveItem(player, DIAMOND_ID, cost);
            return;
        }

        // Guard: wrong plugin class (name collision with another plugin)
        if (!(raw instanceof com.treechop.plugin.TreeChopPlugin)) {
            player.sendMessage(ChatColor.RED +
                "[Shop] TreeChop plugin conflict detected! Refunding diamonds. Contact an admin.");
            giveItem(player, DIAMOND_ID, cost);
            return;
        }

        com.treechop.plugin.TreeChopPlugin tcp = (com.treechop.plugin.TreeChopPlugin) raw;
        tcp.getBuffManager().grantBuff(player);

        if (!hasGoldenAxe(player)) {
            giveItem(player, GOLDEN_AXE_ID, 1);
            player.sendMessage(ChatColor.YELLOW + "A Golden Axe has been added to your inventory.");
        } else {
            player.sendMessage(ChatColor.YELLOW +
                "You already have a Golden Axe \u2014 buff applies to all of them.");
        }
    }

    // ------------------------------------------------------------------
    //  Inventory helpers
    // ------------------------------------------------------------------

    private int countDiamonds(Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getTypeId() == DIAMOND_ID) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void removeDiamonds(Player player, int amount) {
        int         remaining = amount;
        ItemStack[] contents  = player.getInventory().getContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack != null && stack.getTypeId() == DIAMOND_ID) {
                int inStack = stack.getAmount();
                if (inStack <= remaining) {
                    remaining -= inStack;
                    contents[i] = null;
                } else {
                    stack.setAmount(inStack - remaining);
                    remaining = 0;
                }
            }
        }

        player.getInventory().setContents(contents);
        player.updateInventory();
    }

    private void giveItem(Player player, int itemId, int quantity) {
        PlayerInventory inv       = player.getInventory();
        int             remaining = quantity;

        while (remaining > 0) {
            int stackSize = Math.min(remaining, 64);
            java.util.Map<Integer, ItemStack> leftover =
                inv.addItem(new ItemStack(itemId, stackSize));
            if (!leftover.isEmpty()) {
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
            remaining -= stackSize;
        }

        player.updateInventory();
    }

    private boolean hasGoldenAxe(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getTypeId() == GOLDEN_AXE_ID) return true;
        }
        return false;
    }

    public List<ShopItem> getItems() {
        return items;
    }
}
