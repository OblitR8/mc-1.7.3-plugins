package com.spectate;

import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * NMS reflection for Project Poseidon (CB1060 / b1.7.3).
 *
 * Manages two things:
 *
 *   1. isInvulnerable (god mode) — set on spectate start, restored on stop.
 *
 *   2. Anti-fly-kick suppression — CB1060's NetServerHandler tracks how many
 *      consecutive ticks a player has been "floating" (not on the ground).
 *      When it exceeds ~80 ticks the server kicks with "Flying is not enabled".
 *      We reset the counter to 0 every tick for spectators so it never fires.
 *
 *      Reflected chain:
 *        CraftPlayer  .getHandle()          → EntityPlayer
 *        EntityPlayer .playerConnection     → PlayerConnection  (CB name)
 *        PlayerConnection (actually NetServerHandler in b1.7.3 NMS):
 *                     .floatingTicks        → int  (reset to 0)
 *
 *      The field may be named "floatingTicks", "B", or similar depending on
 *      the exact Poseidon build.  We try several names and fall back gracefully.
 */
public final class NmsUtil {

    private static final Logger log = Logger.getLogger("SpectatePlugin");

    // ---- Shared init guard ----
    private static boolean initDone = false;

    // ---- Invulnerable path ----
    private static boolean invulnReady     = false;
    private static Method  getHandle       = null;
    private static Field   fAbilities      = null;
    private static Field   fInvulnerable   = null;
    private static Method  updateAbilities = null;

    // ---- Anti-kick path ----
    private static boolean antikickReady      = false;
    private static Field   fPlayerConnection  = null;  // EntityPlayer.playerConnection
    private static Field   fFloatingTicks     = null;  // NetServerHandler.floatingTicks

    // Candidate field names for the floating-ticks counter across Poseidon builds
    private static final String[] FLOATING_TICK_NAMES = {
        "floatingTicks", "ticksFloating", "floatCount", "B", "C"
    };

    // ------------------------------------------------------------------
    //  One-time init
    // ------------------------------------------------------------------
    private static synchronized void init(Player sample) {
        if (initDone) return;
        initDone = true;

        try {
            getHandle = sample.getClass().getMethod("getHandle");
            Object ep      = getHandle.invoke(sample);
            Class<?> epCls = ep.getClass();

            // ---- Invulnerable ----
            fAbilities = findField(epCls, "abilities");
            if (fAbilities != null) {
                fAbilities.setAccessible(true);
                Object ab      = fAbilities.get(ep);
                Class<?> abCls = ab.getClass();
                fInvulnerable  = findField(abCls, "isInvulnerable");
                if (fInvulnerable != null) fInvulnerable.setAccessible(true);
                updateAbilities = findMethod(epCls, "updateAbilities");
                if (updateAbilities != null) updateAbilities.setAccessible(true);
                invulnReady = (fInvulnerable != null && updateAbilities != null);
            }

            // ---- Anti-kick: find playerConnection field on EntityPlayer ----
            // In CB1060 it may be called "playerConnection" or "netServerHandler"
            fPlayerConnection = findField(epCls, "playerConnection");
            if (fPlayerConnection == null)
                fPlayerConnection = findField(epCls, "netServerHandler");

            if (fPlayerConnection != null) {
                fPlayerConnection.setAccessible(true);
                Object conn      = fPlayerConnection.get(ep);
                Class<?> connCls = conn.getClass();

                for (String name : FLOATING_TICK_NAMES) {
                    Field f = findField(connCls, name);
                    if (f != null && (f.getType() == int.class || f.getType() == Integer.class)) {
                        f.setAccessible(true);
                        fFloatingTicks = f;
                        break;
                    }
                }

                // If named fields failed, scan for an int field that looks right
                if (fFloatingTicks == null) {
                    for (Field f : connCls.getDeclaredFields()) {
                        if (f.getType() == int.class) {
                            f.setAccessible(true);
                            // Heuristic: the value should be a small non-negative number at init
                            try {
                                int v = f.getInt(conn);
                                if (v >= 0 && v < 200) {
                                    fFloatingTicks = f;
                                    log.info("[SpectatePlugin] Anti-kick: using field '" + f.getName()
                                            + "' (value=" + v + ") as floatingTicks candidate.");
                                    break;
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }

                antikickReady = (fFloatingTicks != null);
            }

            log.info("[SpectatePlugin] NMS init: invulnReady=" + invulnReady
                    + " antikickReady=" + antikickReady
                    + (fFloatingTicks != null ? " field=" + fFloatingTicks.getName() : ""));

        } catch (Exception e) {
            log.warning("[SpectatePlugin] NMS init failed: " + e);
        }
    }

    // ------------------------------------------------------------------
    //  Public API
    // ------------------------------------------------------------------

    public static boolean readInvulnerable(Player p) {
        init(p);
        if (!invulnReady) return false;
        try {
            Object ep = getHandle.invoke(p);
            Object ab = fAbilities.get(ep);
            return (Boolean) fInvulnerable.get(ab);
        } catch (Exception e) {
            log.warning("[SpectatePlugin] readInvulnerable: " + e);
            return false;
        }
    }

    public static void setInvulnerable(Player p, boolean on) {
        init(p);
        if (!invulnReady) return;
        try {
            Object ep = getHandle.invoke(p);
            Object ab = fAbilities.get(ep);
            fInvulnerable.setBoolean(ab, on);
            updateAbilities.invoke(ep);
        } catch (Exception e) {
            log.warning("[SpectatePlugin] setInvulnerable(" + on + "): " + e);
        }
    }

    /**
     * Resets the anti-fly-kick counter to 0 for this player.
     * Call every tick for spectators to prevent the "Flying is not enabled" kick.
     */
    public static void resetFloatingTicks(Player p) {
        if (!initDone) init(p);
        if (!antikickReady) return;
        try {
            Object ep   = getHandle.invoke(p);
            Object conn = fPlayerConnection.get(ep);
            fFloatingTicks.setInt(conn, 0);
        } catch (Exception e) {
            // Log only once to avoid spam
            antikickReady = false;
            log.warning("[SpectatePlugin] resetFloatingTicks failed (suppressing future calls): " + e);
        }
    }

    // ------------------------------------------------------------------
    //  Reflection helpers
    // ------------------------------------------------------------------
    private static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try { return c.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    private static Method findMethod(Class<?> cls, String name, Class<?>... params) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try { return c.getDeclaredMethod(name, params); }
            catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    private NmsUtil() {}
}
