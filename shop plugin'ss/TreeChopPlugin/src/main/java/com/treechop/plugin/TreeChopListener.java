package com.treechop.plugin;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Handles tree-chopping for buffed players holding a Golden Axe.
 *
 * Beta 1.7.3 / CraftBukkit b1060 (Project Poseidon) compatible:
 *  - No EventPriority (not available on b1060)
 *  - No ChatColor.BOLD/RESET (not available on b1060)
 *  - Smoke particles via world.playEffect (silently skipped if unavailable)
 *  - Time-remaining message rate-limited to once per 10 minutes
 *  - lastChopMsg cleaned up on player quit to prevent memory leak
 */
public class TreeChopListener implements Listener {

    private static final int  LOG_ID        = 17;  // Oak/Spruce/Birch/Jungle log
    private static final int  GOLDEN_AXE_ID = 286;
    private static final int  MAX_LOGS      = 500; // Lag safety cap
    private static final int  RADIUS_XZ     = 8;   // Max horizontal spread from break point
    private static final int  RADIUS_Y_UP   = 20;  // Max logs above break point
    private static final int  RADIUS_Y_DOWN = 5;   // Max logs below break point (fixed from 2)

    // Chop message shown at most once per 10 minutes per player
    private static final long CHOP_MSG_INTERVAL_MS = 10L * 60L * 1000L;

    /** Last time (epoch ms) each player saw the "time remaining" chop message. */
    private final Map<String, Long> lastChopMsg = new HashMap<String, Long>();

    private final TreeChopPlugin plugin;
    private final BuffManager    buffManager;

    public TreeChopListener(TreeChopPlugin plugin, BuffManager buffManager) {
        this.plugin      = plugin;
        this.buffManager = buffManager;
    }

    // ------------------------------------------------------------------
    //  Tree chop
    // ------------------------------------------------------------------

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        if (!buffManager.hasBuff(player.getName())) return;

        ItemStack held = player.getItemInHand();
        if (held == null || held.getTypeId() != GOLDEN_AXE_ID) return;

        Block broken = event.getBlock();
        if (broken.getTypeId() != LOG_ID) return;

        // Cancel vanilla break — we handle drops and block removal ourselves
        event.setCancelled(true);

        List<Block> tree = findTree(broken);

        for (Block log : tree) {
            // Drop with correct wood-type data byte (oak/spruce/birch/jungle)
            broken.getWorld().dropItemNaturally(
                log.getLocation(),
                new ItemStack(LOG_ID, 1, (short) 0, log.getData())
            );

            // Smoke puff visual — silently ignored if Effect API unavailable on this build
            try {
                log.getWorld().playEffect(log.getLocation(), org.bukkit.Effect.SMOKE, 4);
            } catch (Exception ignored) { }

            // Remove the log (set to air)
            log.setTypeId(0);
        }

        // Show time-remaining only for multi-log chops, rate-limited to every 10 min
        if (tree.size() > 1) {
            maybeShowTimeRemaining(player, tree.size());
        }
    }

    // ------------------------------------------------------------------
    //  Player quit — clean up memory
    // ------------------------------------------------------------------

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastChopMsg.remove(event.getPlayer().getName().toLowerCase());
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private void maybeShowTimeRemaining(Player player, int count) {
        String key  = player.getName().toLowerCase();
        long   now  = System.currentTimeMillis();
        Long   last = lastChopMsg.get(key);

        if (last == null || (now - last) >= CHOP_MSG_INTERVAL_MS) {
            lastChopMsg.put(key, now);
            long ms = buffManager.msRemaining(player.getName());
            player.sendMessage(
                ChatColor.GREEN + "[TreeChop] " +
                ChatColor.YELLOW + "Chopped " + ChatColor.WHITE + count +
                ChatColor.YELLOW + " logs. Buff: " +
                ChatColor.GOLD + BuffManager.BOLD + BuffManager.formatMs(ms) +
                BuffManager.RESET + ChatColor.YELLOW + " remaining."
            );
        }
    }

    /**
     * BFS from the broken log to collect all connected log blocks.
     * Searches 3x3x3 neighbourhood so diagonal jungle/large-oak logs are included.
     * Bounded by RADIUS_XZ horizontally and RADIUS_Y_UP/DOWN vertically from the
     * origin block (wherever the player hit), not from the tree base.
     */
    private List<Block> findTree(Block origin) {
        List<Block>  logs    = new ArrayList<Block>();
        Set<String>  visited = new HashSet<String>();
        Queue<Block> queue   = new LinkedList<Block>();

        visited.add(key(origin));
        queue.add(origin);

        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();

        while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
            Block cur = queue.poll();
            logs.add(cur);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        int nx = cur.getX() + dx;
                        int ny = cur.getY() + dy;
                        int nz = cur.getZ() + dz;

                        // Enforce search bounds from the original broken block
                        if (Math.abs(nx - ox) > RADIUS_XZ) continue;
                        if (ny - oy > RADIUS_Y_UP)          continue;
                        if (oy - ny > RADIUS_Y_DOWN)        continue;
                        if (Math.abs(nz - oz) > RADIUS_XZ) continue;

                        Block nb = origin.getWorld().getBlockAt(nx, ny, nz);
                        String k = key(nb);
                        if (!visited.contains(k) && nb.getTypeId() == LOG_ID) {
                            visited.add(k);
                            queue.add(nb);
                        }
                    }
                }
            }
        }

        return logs;
    }

    private static String key(Block b) {
        return b.getX() + "," + b.getY() + "," + b.getZ();
    }
}
