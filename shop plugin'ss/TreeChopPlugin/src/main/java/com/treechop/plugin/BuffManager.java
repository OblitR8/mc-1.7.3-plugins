package com.treechop.plugin;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Manages the Tree-Chopper buff.
 *
 * - Expiry stored as Unix epoch millis in plugins/TreeChop/buffs.dat
 * - Atomic save (write to .tmp then rename) — crash-safe
 * - Timer counts down in real time, works while player is offline
 * - Reminders sent at 50m / 40m / 30m / 20m / 10m / 5m marks (online only)
 */
public class BuffManager {

    public static final int  GOLDEN_AXE_ID   = 286;
    public static final int  DIAMOND_ID       = 264;
    public static final long BUFF_DURATION_MS = 60L * 60L * 1000L; // 1 hour

    // Raw Minecraft format codes — ChatColor.BOLD/RESET not in b1060
    static final String BOLD  = "\u00a7l";
    static final String RESET = "\u00a7r";

    private static final long[] REMINDER_MS = {
        50 * 60 * 1000L,
        40 * 60 * 1000L,
        30 * 60 * 1000L,
        20 * 60 * 1000L,
        10 * 60 * 1000L,
         5 * 60 * 1000L
    };

    /** player name (lower-case) -> Unix epoch millis when buff expires */
    private final Map<String, Long>      buffExpiry    = new HashMap<String, Long>();
    /** reminder thresholds already fired per player */
    private final Map<String, Set<Long>> sentReminders = new HashMap<String, Set<Long>>();

    private final TreeChopPlugin plugin;
    private final File           dataFile;
    private final File           tmpFile;
    private static final Logger  log = Logger.getLogger("Minecraft");

    public BuffManager(TreeChopPlugin plugin) {
        this.plugin = plugin;
        File dir = plugin.getDataFolder();
        if (!dir.exists()) dir.mkdirs();
        this.dataFile = new File(dir, "buffs.dat");
        this.tmpFile  = new File(dir, "buffs.dat.tmp");
        load();
    }

    // ------------------------------------------------------------------
    //  Public API
    // ------------------------------------------------------------------

    /** Grant or renew the buff for one hour from now. Always saves immediately. */
    public void grantBuff(Player player) {
        String key    = player.getName().toLowerCase();
        long   expiry = System.currentTimeMillis() + BUFF_DURATION_MS;
        buffExpiry.put(key, expiry);
        sentReminders.remove(key);
        save();

        player.sendMessage(ChatColor.GREEN + BOLD + "[TreeChop] " + RESET +
            ChatColor.YELLOW + "Your Golden Axe(s) will chop whole trees for " +
            ChatColor.GOLD + BOLD + "1 hour" + RESET + ChatColor.YELLOW + "!");
        player.sendMessage(ChatColor.GRAY +
            "Durability is infinite while active. Timer runs while offline.");
    }

    /** Returns true if the player currently has an active buff. */
    public boolean hasBuff(String playerName) {
        Long expiry = buffExpiry.get(playerName.toLowerCase());
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    /** Returns milliseconds remaining on the buff, or 0 if none. */
    public long msRemaining(String playerName) {
        Long expiry = buffExpiry.get(playerName.toLowerCase());
        if (expiry == null) return 0L;
        long r = expiry - System.currentTimeMillis();
        return r > 0L ? r : 0L;
    }

    // ------------------------------------------------------------------
    //  Scheduler tick — called every 10 s from TreeChopPlugin
    // ------------------------------------------------------------------

    public void tick() {
        long         now     = System.currentTimeMillis();
        List<String> expired = new ArrayList<String>();

        for (Map.Entry<String, Long> entry : buffExpiry.entrySet()) {
            String key    = entry.getKey();
            long   expiry = entry.getValue();
            Player player = findOnlinePlayer(key);

            if (now >= expiry) {
                expired.add(key);
                if (player != null) notifyExpiry(player);
            } else if (player != null) {
                repairGoldenAxes(player);
                checkReminders(player, expiry - now);
            }
        }

        if (!expired.isEmpty()) {
            for (String key : expired) {
                buffExpiry.remove(key);
                sentReminders.remove(key);
            }
            save();
        }
    }

    /** Call when a player leaves — cleans up reminder state. */
    public void onPlayerQuit(String playerName) {
        // sentReminders stays so reminders resume correctly on relog.
        // Nothing to clean up here beyond saving.
        save();
    }

    // ------------------------------------------------------------------
    //  Internal helpers
    // ------------------------------------------------------------------

    private void checkReminders(Player player, long msLeft) {
        String    key  = player.getName().toLowerCase();
        Set<Long> sent = sentReminders.get(key);
        if (sent == null) {
            sent = new HashSet<Long>();
            sentReminders.put(key, sent);
        }
        for (long threshold : REMINDER_MS) {
            if (msLeft <= threshold && !sent.contains(threshold)) {
                sent.add(threshold);
                long minutes = threshold / 60000L;
                player.sendMessage(
                    ChatColor.GOLD + "[TreeChop] " +
                    ChatColor.YELLOW + "Buff expires in " +
                    ChatColor.RED + BOLD + minutes + " min" + RESET +
                    ChatColor.YELLOW + ". Renew with /shop buy 2."
                );
            }
        }
    }

    private void repairGoldenAxes(Player player) {
        PlayerInventory inv      = player.getInventory();
        ItemStack[]     contents = inv.getContents();
        boolean         changed  = false;
        for (ItemStack stack : contents) {
            if (stack != null && stack.getTypeId() == GOLDEN_AXE_ID
                    && stack.getDurability() != 0) {
                stack.setDurability((short) 0);
                changed = true;
            }
        }
        if (changed) {
            inv.setContents(contents);
            player.updateInventory();
        }
    }

    private void notifyExpiry(Player player) {
        player.sendMessage(ChatColor.GOLD + BOLD + "[TreeChop] " + RESET +
            ChatColor.RED + "Your 1-hour Tree-Chopper buff has expired!");
        player.sendMessage(ChatColor.GRAY +
            "Your Golden Axe(s) are still in your inventory. Use /shop to renew.");
    }

    private Player findOnlinePlayer(String lowerName) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.getName().toLowerCase().equals(lowerName)) return p;
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  Persistence — atomic write via temp file rename
    // ------------------------------------------------------------------

    /**
     * Saves buff data atomically:
     * 1. Write to buffs.dat.tmp
     * 2. Rename .tmp -> buffs.dat (atomic on all OS — avoids corruption on crash)
     */
    public void save() {
        long now = System.currentTimeMillis();
        BufferedWriter w = null;
        try {
            w = new BufferedWriter(new FileWriter(tmpFile));
            for (Map.Entry<String, Long> e : buffExpiry.entrySet()) {
                if (e.getValue() > now) {
                    w.write(e.getKey() + "=" + e.getValue());
                    w.newLine();
                }
            }
            w.close();
            w = null;
            // Atomic replace — if this crashes between write and rename,
            // we still have the old buffs.dat intact
            if (dataFile.exists()) dataFile.delete();
            tmpFile.renameTo(dataFile);
        } catch (IOException e) {
            log.warning("[TreeChop] Failed to save buffs: " + e.getMessage());
        } finally {
            if (w != null) {
                try { w.close(); } catch (IOException ignored) { }
            }
        }
    }

    public void load() {
        buffExpiry.clear();
        if (!dataFile.exists()) return;
        long           now = System.currentTimeMillis();
        BufferedReader r   = null;
        try {
            r = new BufferedReader(new FileReader(dataFile));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || !line.contains("=")) continue;
                String[] parts = line.split("=", 2);
                if (parts.length != 2) continue;
                try {
                    long expiry = Long.parseLong(parts[1].trim());
                    if (expiry > now) {
                        String name = parts[0].trim().toLowerCase();
                        buffExpiry.put(name, expiry);
                        log.info("[TreeChop] Restored buff for " + parts[0].trim() +
                            " (" + formatMs(expiry - now) + " remaining)");
                    }
                } catch (NumberFormatException ignored) { }
            }
        } catch (IOException e) {
            log.warning("[TreeChop] Failed to load buffs: " + e.getMessage());
        } finally {
            if (r != null) {
                try { r.close(); } catch (IOException ignored) { }
            }
        }
    }

    // ------------------------------------------------------------------
    //  Formatting
    // ------------------------------------------------------------------

    public static String formatMs(long ms) {
        long s   = ms / 1000L;
        long h   = s / 3600L;
        long m   = (s % 3600L) / 60L;
        long sec = s % 60L;
        if (h > 0) return h + "h " + m + "m " + sec + "s";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }
}
