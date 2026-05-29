package com.miner3x3;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

public class MinerListener implements Listener {

    private static final int BEDROCK_ID = 7;

    private final Miner3x3Plugin plugin;

    // Recursion guard: prevents the 8 surrounding breakBlockWithDrops calls
    // from re-triggering onBlockBreak.
    private boolean isMining = false;

    public MinerListener(Miner3x3Plugin plugin) {
        this.plugin = plugin;
    }

    // ── 3x3 mining ───────────────────────────────────────────────────────────
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (isMining) return;

        Player player = event.getPlayer();

        // Only activate for registered holders
        if (!plugin.minerPickaxeHolders.contains(player.getUniqueId())) return;

        // Must be holding a diamond pickaxe
        ItemStack item = player.getItemInHand();
        if (item == null || item.getType() != Material.DIAMOND_PICKAXE) return;

        // Indestructible: reset durability on every break
        item.setDurability((short) 0);
        player.setItemInHand(item);

        Block origin = event.getBlock();
        int[][] offsets = getOffsets(player);

        isMining = true;
        try {
            for (int[] offset : offsets) {
                Block neighbour = origin.getWorld().getBlockAt(
                    origin.getX() + offset[0],
                    origin.getY() + offset[1],
                    origin.getZ() + offset[2]
                );

                // Never touch bedrock or air
                if (neighbour.getTypeId() == BEDROCK_ID) continue;
                if (neighbour.getType() == Material.AIR) continue;

                breakBlockWithDrops(neighbour);
                spawnSmoke(neighbour.getLocation());
            }
        } finally {
            isMining = false;
        }

        // Smoke on the originally broken block too
        spawnSmoke(origin.getLocation());

        // Keep pickaxe indestructible after the main break as well
        item.setDurability((short) 0);
        player.setItemInHand(item);
    }

    // ── NMS block break with natural drops ───────────────────────────────────
    // Beta 1.7.3 Bukkit has no Block.breakNaturally(), so we use NMS directly.
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
                // g() → dropNaturally(world, x, y, z, metadata, dropRate=1.0F)
                nmsBlock.g(nmsWorld, x, y, z, data);
            }

            // Remove the block from the world
            block.setTypeId(0);

        } catch (Exception e) {
            try { block.setTypeId(0); } catch (Exception ignored) {}
        }
    }

    // ── 3x3 plane selection from player look direction ────────────────────────
    private int[][] getOffsets(Player player) {
        float pitch = player.getLocation().getPitch();
        float yaw   = ((player.getLocation().getYaw() % 360) + 360) % 360;

        if (Math.abs(pitch) > 45f) {
            return new int[][] {
                {-1, 0, -1}, {0, 0, -1}, {1, 0, -1},
                {-1, 0,  0},             {1, 0,  0},
                {-1, 0,  1}, {0, 0,  1}, {1, 0,  1}
            };
        }

        if ((yaw >= 315f || yaw < 45f) || (yaw >= 135f && yaw < 225f)) {
            return new int[][] {
                {-1, -1, 0}, {0, -1, 0}, {1, -1, 0},
                {-1,  0, 0},             {1,  0, 0},
                {-1,  1, 0}, {0,  1, 0}, {1,  1, 0}
            };
        }

        return new int[][] {
            {0, -1, -1}, {0, -1, 0}, {0, -1, 1},
            {0,  0, -1},             {0,  0,  1},
            {0,  1, -1}, {0,  1, 0}, {0,  1,  1}
        };
    }

    // ── Smoke particle ────────────────────────────────────────────────────────
    private void spawnSmoke(Location loc) {
        Location centre = loc.clone().add(0.5, 0.5, 0.5);
        centre.getWorld().playEffect(centre, Effect.SMOKE, 4);
    }
}
