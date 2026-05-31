package com.discordroles;

import com.discordroles.discord.DiscordBotManager;
import com.discordroles.listeners.ChatListener;
import com.discordroles.listeners.CommandHandler;
import com.discordroles.storage.DatabaseManager;
import com.discordroles.storage.PendingLinkManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * DiscordRolesPlugin — main entry point.
 * Targets Project Poseidon (CB1060 Bukkit API).
 *
 * CB1060 scheduler API uses plain Runnable via:
 *   getServer().getScheduler().scheduleSyncRepeatingTask(plugin, runnable, delay, period)
 * BukkitRunnable does NOT exist in this version.
 */
public class DiscordRolesPlugin extends JavaPlugin {

    private DatabaseManager    db;
    private PendingLinkManager pending;
    private DiscordBotManager  bot;
    private CommandHandler     commandHandler;

    // ---------------------------------------------------------------
    //  Lifecycle
    // ---------------------------------------------------------------

    @Override
    public void onEnable() {
        // 1. Config
        saveDefaultConfig();

        // 2. Persistent storage
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        db = new DatabaseManager(this);
        db.initialise();

        // 3. Pending link store (in-memory, thread-safe)
        pending = new PendingLinkManager();

        // 4. Discord bot
        bot = new DiscordBotManager(this, db, pending);
        bot.start();

        // 5. Command handler and chat listener
        commandHandler = new CommandHandler(this, db, pending);
        getServer().getPluginManager().registerEvents(
                new ChatListener(this, bot), this
        );

        // 6. Purge expired pending codes every 2 minutes (2400 ticks @ 20tps)
        //    CB1060 API: scheduleSyncRepeatingTask(plugin, runnable, delay, period)
        final PendingLinkManager pendingRef = pending;
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                pendingRef.purgeExpired();
            }
        }, 2400L, 2400L);

        log("DiscordRolesPlugin enabled.");
    }

    @Override
    public void onDisable() {
        // Cancel all tasks registered by this plugin
        getServer().getScheduler().cancelTasks(this);
        if (bot != null) bot.stop();
        if (db  != null) db.close();
        log("DiscordRolesPlugin disabled.");
    }

    // ---------------------------------------------------------------
    //  Command routing — override onCommand directly on JavaPlugin,
    //  the most reliable approach for CB1060.
    // ---------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (commandHandler == null) return false;
        return commandHandler.onCommand(sender, command, label, args);
    }

    // ---------------------------------------------------------------
    //  Config helpers  (CB1060 lacks saveDefaultConfig / reloadConfig)
    // ---------------------------------------------------------------

    /**
     * Copies the bundled config.yml to the data folder if absent.
     */
    public void saveDefaultConfig() {
        File file = new File(getDataFolder(), "config.yml");
        if (file.exists()) return;
        if (!getDataFolder().exists()) getDataFolder().mkdirs();

        try (InputStream     in  = getClass().getResourceAsStream("/config.yml");
             FileOutputStream out = new FileOutputStream(file)) {
            if (in == null) { log("WARNING: Bundled config.yml not found in JAR."); return; }
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        } catch (IOException e) {
            log("ERROR: Could not save default config.yml — " + e.getMessage());
        }
    }

    /**
     * Reloads config from disk and fully restarts the Discord bot.
     */
    public void reloadPluginConfig() {
        getConfiguration().load();
        if (bot != null) bot.stop();
        bot = new DiscordBotManager(this, db, pending);
        bot.start();
        getServer().getPluginManager().registerEvents(
                new ChatListener(this, bot), this
        );
        log("Configuration reloaded and bot restarted.");
    }

    // ---------------------------------------------------------------
    //  Accessors
    // ---------------------------------------------------------------

    /** CB1060-compatible config accessor (wraps getConfiguration()). */
    public Configuration cfg() {
        return getConfiguration();
    }

    /** Logs a message prefixed with [DiscordRoles]. */
    public void log(String msg) {
        getServer().getLogger().info("[DiscordRoles] " + msg);
    }

    public DatabaseManager    getDb()      { return db; }
    public PendingLinkManager getPending() { return pending; }
    public DiscordBotManager  getBot()     { return bot; }
}
