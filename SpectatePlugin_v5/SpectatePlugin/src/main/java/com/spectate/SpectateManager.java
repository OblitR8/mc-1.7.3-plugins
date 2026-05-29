package com.spectate;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Core session management for SpectatePlugin (Project Poseidon CB1060 / b1.7.3).
 *
 * Follow design
 * -------------
 * tickFollow() runs every 1 tick (called from SpectatePlugin scheduler).
 * It places the spectator 2.5 blocks BEHIND and at eye level of the target,
 * computed from the target's yaw, so they are beside/behind the player
 * rather than above or inside them.
 *
 * A tiny movement threshold (0.01 blocks²) means the spectator tracks the
 * target smoothly even during slow walking, while still skipping the
 * teleport when neither party has moved at all (avoids zero-distance
 * teleport spam while standing still).
 *
 * Anti-kick design
 * ----------------
 * tickAntiKick() runs every tick and calls NmsUtil.resetFloatingTicks()
 * for each active spectator, keeping CB1060's fly-kick counter at 0.
 */
public class SpectateManager {

    private static final Logger log = Logger.getLogger("SpectatePlugin");

    /** Scheduler interval for follow + anti-kick tasks, in ticks. */
    static final long TICK_INTERVAL = 1L;

    /** God-mode refresh interval in ticks (every 2 seconds). */
    static final long GOD_INTERVAL = 40L;

    /**
     * Minimum squared distance the target must have moved before we
     * bother teleporting the spectator.  Very small — just enough to
     * skip the teleport when the target is truly stationary.
     */
    private static final double FOLLOW_THRESHOLD_SQ = 0.01; // ~0.1 blocks

    /**
     * How far behind the target (in blocks) the spectator is placed.
     * This uses the target's yaw so the spectator is always behind them,
     * not inside them.
     */
    private static final double BEHIND_DISTANCE = 2.5;

    /**
     * Eye-level height offset — places the spectator at roughly the same
     * eye level as the target (1.62 blocks is the standard eye height).
     */
    private static final double EYE_Y_OFFSET = 1.62;

    // ---------------------------------------------------------------
    //  State
    // ---------------------------------------------------------------

    /** Key: spectator name (lowercase) */
    private final Map<String, SpectateSession> sessions = new HashMap<String, SpectateSession>();

    /**
     * Last position we placed this spectator at during follow.
     * Used to skip teleports when the target hasn't moved.
     */
    private final Map<String, Location> lastFollowPos = new HashMap<String, Location>();

    /**
     * Frozen targets: movement events for these players are cancelled.
     * Key: target name (lowercase)
     */
    private final Map<String, String> frozenTargets = new HashMap<String, String>();

    private final SpectatePlugin plugin;

    public SpectateManager(SpectatePlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Start spectating  (/sp <player>)
    // ------------------------------------------------------------------
    public boolean startSpectating(Player spectator, Player target) {
        String key = spectator.getName().toLowerCase();

        if (sessions.containsKey(key)) {
            stopSpectating(spectator);
        }

        boolean savedInvuln = NmsUtil.readInvulnerable(spectator);

        SpectateSession session = new SpectateSession(
                spectator.getName(),
                target.getName(),
                spectator.getLocation().clone(),
                spectator.getHealth(),
                savedInvuln
        );

        sessions.put(key, session);

        for (Player p : getOnlinePlayers()) {
            p.hidePlayer(spectator);
        }

        NmsUtil.setInvulnerable(spectator, true);

        Location dest = behindLocation(target.getLocation());
        spectator.teleport(dest);
        lastFollowPos.put(key, dest.clone());

        spectator.sendMessage(
                ChatColor.GREEN  + "[Spectate] Now spectating " +
                ChatColor.YELLOW + target.getName() +
                ChatColor.GREEN  + ".  God " + on() + "  Follow " + on() + "  Freeze " + off() +
                ChatColor.GRAY   + "  —  /sp help"
        );

        log.info("[SpectatePlugin] " + spectator.getName() + " started spectating " + target.getName());
        return true;
    }

    // ------------------------------------------------------------------
    //  Stop spectating  (/sp stop)
    // ------------------------------------------------------------------
    public boolean stopSpectating(Player spectator) {
        String key = spectator.getName().toLowerCase();
        SpectateSession session = sessions.remove(key);
        lastFollowPos.remove(key);

        if (session == null) return false;

        if (session.isFreezeEnabled()) {
            frozenTargets.remove(session.getTargetName().toLowerCase());
            Player target = plugin.getServer().getPlayer(session.getTargetName());
            if (target != null && target.isOnline()) {
                target.sendMessage(ChatColor.YELLOW + "You have been unfrozen.");
            }
        }

        NmsUtil.setInvulnerable(spectator, session.getSavedIsInvulnerable());

        for (Player p : getOnlinePlayers()) {
            p.showPlayer(spectator);
        }

        spectator.teleport(session.getSavedLocation());

        int hp = Math.min(Math.max(session.getSavedHealth(), 1), 20);
        spectator.setHealth(hp);

        spectator.sendMessage(
                ChatColor.GREEN  + "[Spectate] Stopped spectating " +
                ChatColor.YELLOW + session.getTargetName() +
                ChatColor.GREEN  + ". Returned to your saved location."
        );

        log.info("[SpectatePlugin] " + spectator.getName() + " stopped spectating " + session.getTargetName());
        return true;
    }

    // ------------------------------------------------------------------
    //  Tick: follow + anti-kick  (every 1 tick)
    // ------------------------------------------------------------------
    public void tickFollowAndAntiKick() {
        for (Map.Entry<String, SpectateSession> entry : sessions.entrySet()) {
            String          key     = entry.getKey();
            SpectateSession session = entry.getValue();

            Player spectator = plugin.getServer().getPlayer(session.getSpectatorName());
            if (spectator == null || !spectator.isOnline()) continue;

            // Anti-kick: reset floating-ticks counter every tick
            NmsUtil.resetFloatingTicks(spectator);

            // Follow
            if (!session.isFollowEnabled()) continue;

            Player target = plugin.getServer().getPlayer(session.getTargetName());
            if (target == null || !target.isOnline()) continue;

            Location dest = behindLocation(target.getLocation());

            Location last = lastFollowPos.get(key);
            if (last != null
                    && dest.getWorld() != null
                    && dest.getWorld().equals(last.getWorld())
                    && dest.distanceSquared(last) < FOLLOW_THRESHOLD_SQ) {
                continue;
            }

            spectator.teleport(dest);
            lastFollowPos.put(key, dest.clone());
        }
    }

    // ------------------------------------------------------------------
    //  Tick: god-mode refresh  (every 40 ticks / 2 s)
    // ------------------------------------------------------------------
    public void tickGod() {
        for (SpectateSession session : sessions.values()) {
            if (!session.isGodEnabled()) continue;
            Player spectator = plugin.getServer().getPlayer(session.getSpectatorName());
            if (spectator != null && spectator.isOnline()) {
                NmsUtil.setInvulnerable(spectator, true);
            }
        }
    }

    // ------------------------------------------------------------------
    //  /sp follow
    // ------------------------------------------------------------------
    public void toggleFollow(Player spectator) {
        SpectateSession session = sessions.get(spectator.getName().toLowerCase());
        if (session == null) return;
        boolean now = session.toggleFollow();
        spectator.sendMessage(ChatColor.GREEN + "[Spectate] Follow " + state(now));
        if (now) {
            Player target = plugin.getServer().getPlayer(session.getTargetName());
            if (target != null && target.isOnline()) {
                Location dest = behindLocation(target.getLocation());
                spectator.teleport(dest);
                lastFollowPos.put(spectator.getName().toLowerCase(), dest.clone());
            }
        }
    }

    // ------------------------------------------------------------------
    //  /sp god
    // ------------------------------------------------------------------
    public void toggleGod(Player spectator) {
        SpectateSession session = sessions.get(spectator.getName().toLowerCase());
        if (session == null) return;
        boolean now = session.toggleGod();
        NmsUtil.setInvulnerable(spectator, now);
        spectator.sendMessage(ChatColor.GREEN + "[Spectate] God mode " + state(now));
    }

    // ------------------------------------------------------------------
    //  /sp freeze
    // ------------------------------------------------------------------
    public void toggleFreeze(Player spectator) {
        SpectateSession session = sessions.get(spectator.getName().toLowerCase());
        if (session == null) return;
        boolean now       = session.toggleFreeze();
        String  targetKey = session.getTargetName().toLowerCase();
        Player  target    = plugin.getServer().getPlayer(session.getTargetName());

        if (now) {
            frozenTargets.put(targetKey, spectator.getName().toLowerCase());
            spectator.sendMessage(
                    ChatColor.GREEN + "[Spectate] Freeze " + on() +
                    ChatColor.GRAY  + " — " + session.getTargetName() + " is locked."
            );
            if (target != null && target.isOnline()) {
                target.sendMessage(ChatColor.RED + "You have been frozen by a moderator.");
            }
        } else {
            frozenTargets.remove(targetKey);
            spectator.sendMessage(ChatColor.GREEN + "[Spectate] Freeze " + off());
            if (target != null && target.isOnline()) {
                target.sendMessage(ChatColor.YELLOW + "You have been unfrozen.");
            }
        }
    }

    // ------------------------------------------------------------------
    //  /sp tp
    // ------------------------------------------------------------------
    public void teleportToTarget(Player spectator) {
        SpectateSession session = sessions.get(spectator.getName().toLowerCase());
        if (session == null) return;
        Player target = plugin.getServer().getPlayer(session.getTargetName());
        if (target == null || !target.isOnline()) {
            spectator.sendMessage(ChatColor.RED + "[Spectate] Target is not online.");
            return;
        }
        Location dest = behindLocation(target.getLocation());
        spectator.teleport(dest);
        lastFollowPos.put(spectator.getName().toLowerCase(), dest.clone());
        spectator.sendMessage(ChatColor.GREEN + "[Spectate] Teleported to " + session.getTargetName() + ".");
    }

    // ------------------------------------------------------------------
    //  /sp status
    // ------------------------------------------------------------------
    public void showStatus(Player spectator) {
        SpectateSession s = sessions.get(spectator.getName().toLowerCase());
        if (s == null) {
            spectator.sendMessage(ChatColor.YELLOW + "[Spectate] You are not spectating anyone.");
            return;
        }
        spectator.sendMessage(ChatColor.GOLD + "======= Spectate Status =======");
        spectator.sendMessage(ChatColor.YELLOW + "Target : " + ChatColor.WHITE + s.getTargetName());
        spectator.sendMessage(ChatColor.YELLOW + "Follow : " + state(s.isFollowEnabled()));
        spectator.sendMessage(ChatColor.YELLOW + "God    : " + state(s.isGodEnabled()));
        spectator.sendMessage(ChatColor.YELLOW + "Freeze : " + state(s.isFreezeEnabled()));
    }

    // ------------------------------------------------------------------
    //  Restore all on plugin disable
    // ------------------------------------------------------------------
    public void restoreAll() {
        List<String> keys = new ArrayList<String>(sessions.keySet());
        for (String key : keys) {
            SpectateSession session = sessions.get(key);
            if (session == null) continue;
            Player spectator = plugin.getServer().getPlayer(session.getSpectatorName());
            if (spectator != null && spectator.isOnline()) {
                stopSpectating(spectator);
            } else {
                sessions.remove(key);
                lastFollowPos.remove(key);
            }
        }
        frozenTargets.clear();
    }

    // ------------------------------------------------------------------
    //  Event helpers (called from SpectateListener)
    // ------------------------------------------------------------------

    public void hideAllSpectatorsFrom(Player newPlayer) {
        for (SpectateSession s : sessions.values()) {
            Player spec = plugin.getServer().getPlayer(s.getSpectatorName());
            if (spec != null && spec.isOnline()) newPlayer.hidePlayer(spec);
        }
    }

    public void handleDisconnect(String playerName) {
        frozenTargets.remove(playerName.toLowerCase());

        for (SpectateSession s : new ArrayList<SpectateSession>(sessions.values())) {
            if (s.getTargetName().equalsIgnoreCase(playerName)) {
                Player spec = plugin.getServer().getPlayer(s.getSpectatorName());
                if (spec != null && spec.isOnline()) {
                    spec.sendMessage(ChatColor.RED + "[Spectate] " + playerName
                            + " disconnected. Returning you to your saved location.");
                    stopSpectating(spec);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  Queries
    // ------------------------------------------------------------------
    public boolean isSpectating(Player player) {
        return sessions.containsKey(player.getName().toLowerCase());
    }

    public boolean isTargetFrozen(Player player) {
        return frozenTargets.containsKey(player.getName().toLowerCase());
    }

    public SpectateSession getSession(Player spectator) {
        return sessions.get(spectator.getName().toLowerCase());
    }

    // ------------------------------------------------------------------
    //  CB1060 compat: getOnlinePlayers() may return Player[] or Collection
    // ------------------------------------------------------------------
    @SuppressWarnings("unchecked")
    public List<Player> getOnlinePlayers() {
        List<Player> list = new ArrayList<Player>();
        try {
            Object result = plugin.getServer().getClass()
                    .getMethod("getOnlinePlayers")
                    .invoke(plugin.getServer());
            if (result instanceof Player[]) {
                for (Player p : (Player[]) result) list.add(p);
            } else if (result instanceof Collection) {
                list.addAll((Collection<Player>) result);
            }
        } catch (Exception e) {
            log.warning("[SpectatePlugin] getOnlinePlayers failed: " + e.getMessage());
        }
        return list;
    }

    // ------------------------------------------------------------------
    //  Position helper — places spectator behind and at eye level of target
    // ------------------------------------------------------------------
    /**
     * Computes a location 2.5 blocks behind the target (based on their yaw)
     * and at their eye level.  The spectator faces the same direction as the
     * target so the view feels natural.
     *
     * Yaw in Minecraft: 0° = south (+Z), 90° = west (-X), 180° = north (-Z),
     * 270° = east (+X).  "Behind" is the opposite of the direction faced.
     */
    private Location behindLocation(Location targetLoc) {
        double yawRad = Math.toRadians(targetLoc.getYaw());

        // Unit vector in the direction the target is FACING
        double facingX = -Math.sin(yawRad);
        double facingZ =  Math.cos(yawRad);

        // Step back BEHIND_DISTANCE blocks in the opposite direction
        double x = targetLoc.getX() - facingX * BEHIND_DISTANCE;
        double y = targetLoc.getY() + EYE_Y_OFFSET;
        double z = targetLoc.getZ() - facingZ * BEHIND_DISTANCE;

        // Spectator looks in the same direction as the target
        return new Location(targetLoc.getWorld(), x, y, z,
                targetLoc.getYaw(), targetLoc.getPitch());
    }

    // ------------------------------------------------------------------
    //  Formatting helpers
    // ------------------------------------------------------------------
    private String state(boolean on) { return on ? on() : off(); }
    private String on()  { return ChatColor.GREEN + "ON" + ChatColor.GREEN; }
    private String off() { return ChatColor.RED   + "OFF" + ChatColor.GREEN; }
}
