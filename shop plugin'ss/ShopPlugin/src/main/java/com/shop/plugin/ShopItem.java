package com.shop.plugin;

/**
 * Represents a single item available for purchase in the shop.
 */
public class ShopItem {

    private final String displayName;   // Human-readable name shown in chat menu
    private final int itemId;           // Minecraft item/block ID (Beta 1.7.3)
    private final int quantity;         // Amount of item given per purchase
    private final int diamondCost;      // How many diamonds it costs

    public ShopItem(String displayName, int itemId, int quantity, int diamondCost) {
        this.displayName = displayName;
        this.itemId = itemId;
        this.quantity = quantity;
        this.diamondCost = diamondCost;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getItemId() {
        return itemId;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getDiamondCost() {
        return diamondCost;
    }
}
