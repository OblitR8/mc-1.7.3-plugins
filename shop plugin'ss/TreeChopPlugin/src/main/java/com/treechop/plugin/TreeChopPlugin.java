package com.treechop.plugin;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.logging.Logger;

public class TreeChopPlugin extends JavaPlugin implements Listener {

    private static final Logger log = Logger.getLogger("Minecraft");

    private BuffManager      buffManager;
    private TreeChopListener chopListener;

    @Override
    public void onEnable() {
        this.buffManager  = new BuffManager(this);
        this.chopListener = new TreeChopListener(this, buffManager);

        getServer().getPluginManager().registerEvents(chopListener, this);
        getServer().getPluginManager().registerEvents(this, this);

        // Tick every 10 seconds: repair axes, fire reminders, expire buffs
        BukkitScheduler scheduler = getServer().getScheduler();
        scheduler.scheduleSyncRepeatingTask(this, new Runnable() {
            public void run() {
                buffManager.tick();
            }
        }, 200L, 200L);

        log.info("[TreeChop] Enabled. Buff data loaded from disk.");
    }

    @Override
    public void onDisable() {
        if (buffManager != null) buffManager.save();
        log.info("[TreeChop] Disabled. Buff data saved.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        // Delay 1 tick so the join message doesn't get buried
        getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
            public void run() {
                // Guard: player might disconnect in the same tick
                if (!player.isOnline()) return;
                if (buffManager.hasBuff(player.getName())) {
                    long ms = buffManager.msRemaining(player.getName());
                    player.sendMessage(
                        ChatColor.GREEN + "[TreeChop] " +
                        ChatColor.YELLOW + "Welcome back! Tree-Chopper buff active: " +
                        ChatColor.GOLD + BuffManager.BOLD + BuffManager.formatMs(ms) +
                        BuffManager.RESET + ChatColor.YELLOW + " remaining."
                    );
                }
            }
        }, 20L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        buffManager.onPlayerQuit(event.getPlayer().getName());
    }

    public BuffManager getBuffManager() {
        return buffManager;
    }
}
