package com.discordroles.storage;

import com.discordroles.DiscordRolesPlugin;
import org.bukkit.util.config.Configuration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores Minecraft ↔ Discord account links in a YAML flat file.
 *
 * Uses Poseidon's built-in org.bukkit.util.config.Configuration so there
 * are ZERO extra dependencies and ZERO native libraries — no SQLite needed.
 *
 * File: plugins/DiscordRolesPlugin/links.yml
 * Format:
 *   links:
 *     Steve: "123456789012345678"
 *     Alex:  "987654321098765432"
 */
public class DatabaseManager {

    private final DiscordRolesPlugin plugin;
    private Configuration storage;
    private File storageFile;

    // In-memory cache so role lookups on every chat message are instant
    // key = lowercase minecraft name, value = discord snowflake ID
    private final Map<String, String> mcToDiscord = new HashMap<String, String>();
    // reverse map for fast discord-id → mc-name lookups
    private final Map<String, String> discordToMc = new HashMap<String, String>();

    public DatabaseManager(DiscordRolesPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------
    //  Lifecycle
    // ---------------------------------------------------------------

    /**
     * Loads links.yml from disk into memory.
     * Called once during onEnable().
     */
    public void initialise() {
        storageFile = new File(plugin.getDataFolder(), "links.yml");

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        storage = new Configuration(storageFile);

        if (storageFile.exists()) {
            storage.load();
            // Populate in-memory maps from file
            java.util.List<String> keys = storage.getKeys("links");
            if (keys != null) {
                for (String mcName : keys) {
                    String discordId = storage.getString("links." + mcName, null);
                    if (discordId != null && !discordId.isEmpty()) {
                        mcToDiscord.put(mcName.toLowerCase(), discordId);
                        discordToMc.put(discordId, mcName);
                    }
                }
            }
            plugin.log("Loaded " + mcToDiscord.size() + " linked account(s) from links.yml");
        } else {
            plugin.log("No links.yml found — starting fresh.");
        }
    }

    /** Closes/flushes — nothing async here, saves are immediate. */
    public void close() {
        // Nothing to close for YAML file storage
    }

    // ---------------------------------------------------------------
    //  Public API
    // ---------------------------------------------------------------

    /**
     * Links a Minecraft username to a Discord user ID.
     * Persists immediately to disk.
     *
     * @return true on success
     */
    public boolean linkAccount(String minecraftName, String discordId) {
        // Remove any old entry for this discord ID first (clean slate)
        String oldMc = discordToMc.get(discordId);
        if (oldMc != null) {
            mcToDiscord.remove(oldMc.toLowerCase());
            storage.removeProperty("links." + oldMc);
        }

        mcToDiscord.put(minecraftName.toLowerCase(), discordId);
        discordToMc.put(discordId, minecraftName);

        storage.setProperty("links." + minecraftName, discordId);
        boolean ok = storage.save();
        if (!ok) {
            plugin.log("WARNING: Failed to save links.yml after linking " + minecraftName);
        }
        return ok;
    }

    /**
     * Removes a linked account by Minecraft username.
     *
     * @return true if an entry was removed
     */
    public boolean unlinkByMinecraftName(String minecraftName) {
        String discordId = mcToDiscord.remove(minecraftName.toLowerCase());
        if (discordId == null) return false;

        discordToMc.remove(discordId);
        storage.removeProperty("links." + minecraftName);
        boolean ok = storage.save();
        if (!ok) {
            plugin.log("WARNING: Failed to save links.yml after unlinking " + minecraftName);
        }
        return true;
    }

    /**
     * Removes a linked account by Discord ID.
     * Used when a user resets their link from Discord.
     *
     * @return true if an entry was removed
     */
    public boolean unlinkByDiscordId(String discordId) {
        String mcName = discordToMc.remove(discordId);
        if (mcName == null) return false;

        mcToDiscord.remove(mcName.toLowerCase());
        storage.removeProperty("links." + mcName);
        boolean ok = storage.save();
        if (!ok) {
            plugin.log("WARNING: Failed to save links.yml after unlinking discord:" + discordId);
        }
        return true;
    }

    /**
     * Returns the Discord snowflake ID for a Minecraft username, or null.
     */
    public String getDiscordId(String minecraftName) {
        return mcToDiscord.get(minecraftName.toLowerCase());
    }

    /**
     * Returns the Minecraft username for a Discord ID, or null.
     */
    public String getMinecraftName(String discordId) {
        return discordToMc.get(discordId);
    }

    /** Returns true if the Minecraft username is linked. */
    public boolean isLinked(String minecraftName) {
        return mcToDiscord.containsKey(minecraftName.toLowerCase());
    }

    /** Returns true if the Discord ID is linked to any Minecraft account. */
    public boolean isDiscordLinked(String discordId) {
        return discordToMc.containsKey(discordId);
    }
}
