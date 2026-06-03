package com.discordroles.discord;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory cache of Discord user ID → resolved role name.
 *
 * Eliminates blocking REST calls on the Bukkit main thread during chat.
 * Entries are invalidated when:
 *   - A member's roles change   (GuildMemberRoleAdd/RemoveEvent)
 *   - A member leaves the guild (GuildMemberRemoveEvent)
 *   - An account is unlinked    (DatabaseManager.unlinkBy*)
 *   - The bot restarts          (cache starts empty, fills on demand)
 *
 * "null sentinel" pattern: we cache the String "NONE" to represent
 * "linked but no qualifying role", so we don't re-query Discord every chat
 * for members who genuinely have no roles we care about.
 */
public final class RoleCache {

    /** Stored instead of null when a player is linked but has no qualifying role. */
    static final String NONE = "\u0000NONE\u0000";

    private final ConcurrentHashMap<String, String> cache =
            new ConcurrentHashMap<String, String>();

    /** Stores a resolved role name (or NONE sentinel) for a Discord user ID. */
    public void put(String discordId, String roleName) {
        cache.put(discordId, roleName == null ? NONE : roleName);
    }

    /**
     * Returns the cached role name for a Discord user ID.
     * Returns {@code null} if not cached yet.
     * Returns {@code ""} (empty string) if cached as "no role".
     */
    public String get(String discordId) {
        String val = cache.get(discordId);
        if (val == null)    return null;  // not cached
        if (NONE.equals(val)) return "";  // cached: no role
        return val;
    }

    /** Returns true if there is a cached entry (including "no role") for this ID. */
    public boolean contains(String discordId) {
        return cache.containsKey(discordId);
    }

    /** Removes the cached entry for a Discord user ID, forcing a re-lookup next chat. */
    public void invalidate(String discordId) {
        cache.remove(discordId);
    }

    /** Clears all entries (called on bot restart). */
    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }
}
