package com.miner3x3;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MinerListener implements Listener {

    private static final int BEDROCK_ID = 7;

    private final Miner3x3Plugin plugin;

    // Per-player mining lock — prevents recursion if two holders mine at the same time
    private final Set<UUID> currentlyMining = new HashSet<>();

    public MinerListener(Miner3x3Plugin plugin) {
        this.plugin = plugin;
    }

    // ── Block all durability loss on gold pickaxes for registered holders ─────
    // Fires at LOWEST priority so it runs before anything else can consume the event.
    // Cancelling here means the NMS damage() call is a no-op — the pick never wears.
    @EventHandler(priority = Event.Priority.Lowest)
    public void onItemDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        if (!plugin.minerPickaxeHolders.contains(player.getUniqueId())) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.GOLD_PICKAXE) return;

        event.setCancelled(true);
    }

    // ── 3x3 mining ───────────────────────────────────────────────────────────
    @EventHandler(priority = Event.Priority.Normal)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;

        Player player = (Player) event.getDamager();
        if (!plugin.minerPickaxeHolders.contains(player.getUniqueId())) return;

        ItemStack item = player.getItemInHand();
        if (item == null || item.getType() != Material.GOLD_PICKAXE) return;

        // Attack durability is not always fired through PlayerItemDamageEvent on this server version,
        // so refresh the pickaxe immediately after hitting an entity.
        resetDurability(player, item);
    }

    @EventHandler(priority = Event.Priority.Normal)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();

        // Not a holder
        if (!plugin.minerPickaxeHolders.contains(uid)) return;

        // Recursion guard — skip blocks broken by our own loop below
        if (currentlyMining.contains(uid)) return;

        ItemStack item = player.getItemInHand();
        if (item == null || item.getType() != Material.GOLD_PICKAXE) return;

        // Ensure full durability is shown before we start (client sync)
        resetDurability(player, item);

        Block origin = event.getBlock();
        int[][] offsets = getOffsets(player);

        currentlyMining.add(uid);
        try {
            for (int[] offset : offsets) {
                Block neighbour = origin.getWorld().getBlockAt(
                    origin.getX() + offset[0],
                    origin.getY() + offset[1],
                    origin.getZ() + offset[2]
                );

                if (neighbour.getTypeId() == BEDROCK_ID) continue;
                if (neighbour.getType() == Material.AIR) continue;

                breakBlockWithDrops(neighbour);
                spawnSmoke(neighbour.getLocation());
            }
        } finally {
            currentlyMining.remove(uid);
        }

        // Effect on the centre (originally broken) block
        spawnSmoke(origin.getLocation());

        // Re-fetch item in case the reference was invalidated, then sync client
        ItemStack held = player.getItemInHand();
        if (held != null && held.getType() == Material.GOLD_PICKAXE) {
            resetDurability(player, held);
        }
    }

    // Set durability to 0 and push the update to the client immediately
    private void resetDurability(Player player, ItemStack item) {
        item.setDurability((short) 0);
        player.setItemInHand(item);
        player.updateInventory();
    }

    // Break block via NMS so it produces correct drops (no Bukkit breakNaturally in CB1060)
    private void breakBlockWithDrops(Block block) {
        int typeId = block.getTypeId();
        if (typeId == 0) return;

        try {
            CraftWorld craftWorld = (CraftWorld) block.getWorld();
            net.minecraft.server.World nmsWorld = craftWorld.getHandle();

            int x    = block.getX();
            int y    = block.getY();
            int z    = block.getZ();
            int data = block.getData();

            net.minecraft.server.Block nmsBlock = net.minecraft.server.Block.byId[typeId];
            if (nmsBlock != null) {
                // g() = dropNaturally(world, x, y, z, metadata, 1.0F dropRate)
                nmsBlock.g(nmsWorld, x, y, z, data);
            }

            // Remove the block (set to air, no physics cascade needed)
            block.setTypeId(0);

        } catch (Exception e) {
            // Fallback: silently remove the block without drops rather than crashing
            try { block.setTypeId(0); } catch (Exception ignored) {}
        }
    }

    // Smoke puff at the centre of a broken block
    private void spawnSmoke(Location loc) {
        loc.clone().add(0.5, 0.5, 0.5).getWorld().playEffect(
            loc.clone().add(0.5, 0.5, 0.5), Effect.SMOKE, 0
        );
    }

    // Pick the 3x3 plane based on where the player is looking
    private int[][] getOffsets(Player player) {
        float pitch = player.getLocation().getPitch();
        float yaw   = ((player.getLocation().getYaw() % 360) + 360) % 360;

        // Looking mostly up or down → horizontal XZ plane
        if (Math.abs(pitch) > 45f) {
            return new int[][] {
                {-1, 0, -1}, {0, 0, -1}, {1, 0, -1},
                {-1, 0,  0},             {1, 0,  0},
                {-1, 0,  1}, {0, 0,  1}, {1, 0,  1}
            };
        }

        // Facing N or S → YX plane
        if ((yaw >= 315f || yaw < 45f) || (yaw >= 135f && yaw < 225f)) {
            return new int[][] {
                {-1, -1, 0}, {0, -1, 0}, {1, -1, 0},
                {-1,  0, 0},             {1,  0, 0},
                {-1,  1, 0}, {0,  1, 0}, {1,  1, 0}
            };
        }

        // Facing E or W → YZ plane
        return new int[][] {
            {0, -1, -1}, {0, -1, 0}, {0, -1, 1},
            {0,  0, -1},             {0,  0,  1},
            {0,  1, -1}, {0,  1, 0}, {0,  1,  1}
        };
    }
}
