package com.spectate;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Event listener for SpectatePlugin — CB1060 / Project Poseidon 1.1.8.
 *
 * Follow is NOT done here.  The PlayerMoveEvent fires 20+ times per second
 * and teleporting on every event causes severe rubberbanding.  Follow is
 * driven by a scheduler task in SpectatePlugin (tickFollow) instead.
 *
 * PlayerMoveEvent here handles ONLY the freeze mechanic.
 */
public class SpectateListener implements Listener {

    private final SpectateManager mgr;

    public SpectateListener(SpectateManager mgr) {
        this.mgr = mgr;
    }

    // ------------------------------------------------------------------
    // 1. New player joins — hide all active spectators from them
    // ------------------------------------------------------------------
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        mgr.hideAllSpectatorsFrom(event.getPlayer());
    }

    // ------------------------------------------------------------------
    // 2. Movement — ONLY used to enforce freeze
    //    Spectator movement is irrelevant (they are free to move around).
    //    Follow tracking is scheduler-based, not event-based.
    // ------------------------------------------------------------------
    @EventHandler(priority = Event.Priority.Highest)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player mover = event.getPlayer();

        // Spectators move freely — nothing to do
        if (mgr.isSpectating(mover)) return;

        // Frozen players: allow head rotation, block position changes
        if (mgr.isTargetFrozen(mover)) {
            Location from = event.getFrom();
            Location to   = event.getTo();
            if (from.getBlockX() != to.getBlockX()
                    || from.getBlockY() != to.getBlockY()
                    || from.getBlockZ() != to.getBlockZ()) {
                // Preserve yaw/pitch (head look) but lock XYZ
                from.setYaw(to.getYaw());
                from.setPitch(to.getPitch());
                event.setTo(from);
            }
        }
    }

    // ------------------------------------------------------------------
    // 3. Chat — spectators observe silently, cannot broadcast messages
    // ------------------------------------------------------------------
    @EventHandler(priority = Event.Priority.Lowest)
    public void onPlayerChat(PlayerChatEvent event) {
        if (mgr.isSpectating(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                    ChatColor.RED + "[Spectate] You cannot chat while spectating. " +
                    ChatColor.YELLOW + "/sp stop" +
                    ChatColor.RED + " to exit."
            );
        }
    }

    // ------------------------------------------------------------------
    // 4. Quit — clean up sessions when spectator or target disconnects
    // ------------------------------------------------------------------
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player quitter = event.getPlayer();

        // If they were spectating, restore their state (minus the teleport
        // since they're offline — stopSpectating handles the null gracefully
        // and the restoreAll path in onDisable does the same).
        if (mgr.isSpectating(quitter)) {
            mgr.stopSpectating(quitter);
        }

        // Handle them leaving as a target (or frozen target)
        mgr.handleDisconnect(quitter.getName());
    }

    // ------------------------------------------------------------------
    // 5. Damage — belt-and-suspenders block for god mode
    //    NmsUtil sets isInvulnerable but in CB1060 some damage sources
    //    bypass it server-side; this event catch is a guaranteed fallback.
    // ------------------------------------------------------------------
    @EventHandler(priority = Event.Priority.Highest, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player damaged = (Player) event.getEntity();
            if (mgr.isSpectating(damaged)) {
                event.setCancelled(true);
            }
        }
    }
}
