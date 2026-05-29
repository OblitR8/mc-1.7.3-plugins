package com.spectate;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * SpectatePlugin — silent OP moderation spectator for Project Poseidon (b1.7.3/ CB1060).
 *
 * Scheduler tasks:
 *
 *   mainTask  — every 1 tick
 *               • Resets floatingTicks to 0 for each spectator (prevents fly-kick)
 *               • Teleports spectators to behind-and-beside their target (follow)
 *
 *   godTask   — every 40 ticks (2 s)
 *               • Re-applies isInvulnerable for spectators with god ON
 *               • CB1060 can clear this on damage/respawn events
 */
public class SpectatePlugin extends JavaPlugin {

    private static final Logger log = Logger.getLogger("SpectatePlugin");

    private SpectateManager spectateManager;

    @Override
    public void onEnable() {
        spectateManager = new SpectateManager(this);

        getCommand("sp").setExecutor(new SpectateCommand(this, spectateManager));

        getServer().getPluginManager().registerEvents(
                new SpectateListener(spectateManager), this);

        final SpectateManager mgr = spectateManager;

        // Main task: anti-kick + follow, every tick
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            public void run() {
                mgr.tickFollowAndAntiKick();
            }
        }, SpectateManager.TICK_INTERVAL, SpectateManager.TICK_INTERVAL);

        // God-mode refresh, every 2 s
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            public void run() {
                mgr.tickGod();
            }
        }, SpectateManager.GOD_INTERVAL, SpectateManager.GOD_INTERVAL);

        log.info("[SpectatePlugin] v5 enabled. /sp help for commands.");
    }

    @Override
    public void onDisable() {
        if (spectateManager != null) {
            spectateManager.restoreAll();
        }
        log.info("[SpectatePlugin] Disabled. All spectator sessions restored.");
    }
}
