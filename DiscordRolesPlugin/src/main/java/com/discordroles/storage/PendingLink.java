package com.discordroles.storage;

/**
 * Represents a pending (unconfirmed) account link.
 *
 * Created when a Discord user runs /discordlink <username>.
 * Destroyed when:
 *   - The player confirms with /discord confirm <code> in Minecraft  (success)
 *   - The code expires after CODE_EXPIRY_MS milliseconds              (timeout)
 *   - The Discord user runs /discordlink again                        (replaced)
 *
 * Immutable — all fields set at construction time.
 */
public final class PendingLink {

    /** How long a code is valid for, in milliseconds (10 minutes). */
    public static final long CODE_EXPIRY_MS = 10 * 60 * 1000L;

    private final String discordId;
    private final String discordTag;      // e.g. "Steve#1234" — for logging only
    private final String minecraftName;
    private final String code;
    private final long   createdAt;       // System.currentTimeMillis()

    public PendingLink(String discordId, String discordTag,
                       String minecraftName, String code) {
        this.discordId     = discordId;
        this.discordTag    = discordTag;
        this.minecraftName = minecraftName;
        this.code          = code;
        this.createdAt     = System.currentTimeMillis();
    }

    // ---------------------------------------------------------------

    public String getDiscordId()     { return discordId; }
    public String getDiscordTag()    { return discordTag; }
    public String getMinecraftName() { return minecraftName; }
    public String getCode()          { return code; }
    public long   getCreatedAt()     { return createdAt; }

    /** Returns true if this pending link has passed its expiry time. */
    public boolean isExpired() {
        return System.currentTimeMillis() - createdAt > CODE_EXPIRY_MS;
    }

    /** Returns remaining seconds before this code expires (0 if already expired). */
    public long secondsRemaining() {
        long remaining = CODE_EXPIRY_MS - (System.currentTimeMillis() - createdAt);
        return Math.max(0L, remaining / 1000L);
    }
}
