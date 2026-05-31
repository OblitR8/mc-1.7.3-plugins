package com.discordroles.storage;

import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe store for pending (unconfirmed) account links.
 *
 * Keyed two ways for O(1) lookup from both sides:
 *   minecraftName (lowercase) → PendingLink   (used when player types /discord confirm)
 *   discordId                 → PendingLink   (used to prevent duplicate /discordlink calls)
 *
 * Uses ConcurrentHashMap so JDA threads (Discord) and the Bukkit server
 * thread (Minecraft) can both access it safely without explicit locking.
 *
 * Expired entries are pruned lazily on every write, and explicitly via
 * purgeExpired() which should be called on a periodic Bukkit scheduler task.
 */
public final class PendingLinkManager {

    // Cryptographically secure random for code generation
    private static final SecureRandom RANDOM = new SecureRandom();

    // Primary map: lowercase minecraft name → pending link
    private final ConcurrentHashMap<String, PendingLink> byMinecraft
            = new ConcurrentHashMap<String, PendingLink>();

    // Reverse map: discord id → pending link (same objects, two references)
    private final ConcurrentHashMap<String, PendingLink> byDiscord
            = new ConcurrentHashMap<String, PendingLink>();

    // ---------------------------------------------------------------
    //  Code generation
    // ---------------------------------------------------------------

    /**
     * Generates a random 6-digit numeric code, zero-padded.
     * e.g. "048293"
     * Using SecureRandom makes codes unpredictable.
     */
    public String generateCode() {
        int value = RANDOM.nextInt(900000) + 100000; // 100000–999999
        return String.valueOf(value);
    }

    // ---------------------------------------------------------------
    //  Write operations
    // ---------------------------------------------------------------

    /**
     * Creates and stores a new pending link.
     * Any previous pending link for the same Discord user is replaced.
     *
     * @return The newly created PendingLink (contains the code).
     */
    public PendingLink create(String discordId, String discordTag, String minecraftName) {
        purgeExpired();

        // Remove any pre-existing pending entry for this discord user
        PendingLink existing = byDiscord.get(discordId);
        if (existing != null) {
            byMinecraft.remove(existing.getMinecraftName().toLowerCase());
            byDiscord.remove(discordId);
        }

        // Also remove any pre-existing pending entry for this minecraft name
        // (different discord user may have tried to claim the same name)
        PendingLink nameConflict = byMinecraft.get(minecraftName.toLowerCase());
        if (nameConflict != null) {
            byDiscord.remove(nameConflict.getDiscordId());
            byMinecraft.remove(minecraftName.toLowerCase());
        }

        PendingLink link = new PendingLink(discordId, discordTag, minecraftName, generateCode());
        byMinecraft.put(minecraftName.toLowerCase(), link);
        byDiscord.put(discordId, link);
        return link;
    }

    /**
     * Removes a pending link by Minecraft name after it has been confirmed
     * or cancelled. Safe to call with a name that has no pending entry.
     */
    public void remove(String minecraftName) {
        PendingLink link = byMinecraft.remove(minecraftName.toLowerCase());
        if (link != null) {
            byDiscord.remove(link.getDiscordId());
        }
    }

    /**
     * Removes all expired entries. Called periodically from the Bukkit scheduler.
     */
    public void purgeExpired() {
        Iterator<Map.Entry<String, PendingLink>> it = byMinecraft.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PendingLink> entry = it.next();
            if (entry.getValue().isExpired()) {
                byDiscord.remove(entry.getValue().getDiscordId());
                it.remove();
            }
        }
    }

    // ---------------------------------------------------------------
    //  Read operations
    // ---------------------------------------------------------------

    /**
     * Looks up a pending link by Minecraft username.
     * Returns null if none exists or if it has expired.
     */
    public PendingLink getByMinecraft(String minecraftName) {
        PendingLink link = byMinecraft.get(minecraftName.toLowerCase());
        if (link == null) return null;
        if (link.isExpired()) {
            remove(minecraftName);
            return null;
        }
        return link;
    }

    /**
     * Looks up a pending link by Discord user ID.
     * Returns null if none exists or if it has expired.
     */
    public PendingLink getByDiscord(String discordId) {
        PendingLink link = byDiscord.get(discordId);
        if (link == null) return null;
        if (link.isExpired()) {
            remove(link.getMinecraftName());
            return null;
        }
        return link;
    }

    /** Returns true if this Discord user has a pending (non-expired) link request. */
    public boolean hasPendingByDiscord(String discordId) {
        return getByDiscord(discordId) != null;
    }

    /** Returns the number of currently pending (non-expired) entries. */
    public int size() {
        purgeExpired();
        return byMinecraft.size();
    }
}
