package com.discordroles;

import com.discordroles.discord.DiscordBotManager;
import com.discordroles.listeners.ChatListener;
import com.discordroles.listeners.CommandHandler;
import com.discordroles.storage.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * DiscordRolesPlugin — Main entry point.
 *
 * NOTE: This targets Project Poseidon (CB1060 Bukkit API).
 * Key differences from modern Bukkit:
 *   - getConfiguration() instead of getConfig()
 *   - getServer().getLogger() instead of getLogger()
 *   - No saveDefaultConfig() / reloadConfig() — implemented manually below
 *   - @EventHandler uses Event.Priority, not EventPriority
 */
public class DiscordRolesPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private DiscordBotManager botManager;

    // Convenience logger field so other classes can call plugin.log()
    private Logger logger;

    // ---------------------------------------------------------------
    //  Plugin lifecycle
    // ---------------------------------------------------------------

    @Override
    public void onEnable() {
        logger = getServer().getLogger();

        // 1. Config — save default config.yml if it doesn't exist yet
        saveDefaultConfig();

        // 2. Database
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        databaseManager = new DatabaseManager(this);
        databaseManager.initialise();

        // 3. Discord bot
        botManager = new DiscordBotManager(this, databaseManager);
        botManager.start();

        // 4. Listeners & commands
        CommandHandler cmdHandler = new CommandHandler(this, databaseManager);
        getCommand("discordlink").setExecutor(cmdHandler);
        getCommand("discordunlink").setExecutor(cmdHandler);
        getCommand("discordreload").setExecutor(cmdHandler);

        getServer().getPluginManager().registerEvents(
                new ChatListener(this, botManager), this
        );

        log("DiscordRolesPlugin enabled!");
    }

    @Override
    public void onDisable() {
        if (botManager != null) {
            botManager.stop();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        log("DiscordRolesPlugin disabled.");
    }

    // ---------------------------------------------------------------
    //  Config helpers (manual, since CB1060 JavaPlugin lacks these)
    // ---------------------------------------------------------------

    /**
     * Copies config.yml from the JAR to the data folder if it doesn't exist.
     * Replaces the modern saveDefaultConfig().
     */
    public void saveDefaultConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (configFile.exists()) return;

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        try (InputStream in = getClass().getResourceAsStream("/config.yml");
             FileOutputStream out = new FileOutputStream(configFile)) {
            if (in == null) {
                log("WARNING: No default config.yml found in JAR!");
                return;
            }
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } catch (IOException e) {
            log("ERROR: Could not save default config.yml: " + e.getMessage());
        }
    }

    /**
     * Reloads config.yml from disk.
     * Replaces the modern reloadConfig().
     */
    public void reloadPluginConfig() {
        getConfiguration().load();

        // Restart bot with potentially new token / channel IDs
        if (botManager != null) {
            botManager.stop();
        }
        botManager = new DiscordBotManager(this, databaseManager);
        botManager.start();

        // Re-register chat listener with the new bot reference
        getServer().getPluginManager().registerEvents(
                new ChatListener(this, botManager), this
        );
        log("DiscordRoles config reloaded and bot restarted.");
    }

    // ---------------------------------------------------------------
    //  Accessors
    // ---------------------------------------------------------------

    /** Returns the loaded Configuration object (old CB1060 API). */
    public Configuration cfg() {
        Configuration c = getConfiguration();
        return c;
    }

    /** Convenience logger so other classes don't need getServer().getLogger(). */
    public Logger log() {
        return logger != null ? logger : getServer().getLogger();
    }

    /** Log an info message, prefixed with the plugin name. */
    public void log(String msg) {
        getServer().getLogger().info("[DiscordRoles] " + msg);
    }

    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public DiscordBotManager getBotManager()     { return botManager; }
}
