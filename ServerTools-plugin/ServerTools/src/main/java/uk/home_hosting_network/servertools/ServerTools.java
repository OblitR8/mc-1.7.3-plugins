package uk.home_hosting_network.servertools;

import org.bukkit.plugin.java.JavaPlugin;
import uk.home_hosting_network.servertools.commands.HelpCommand;
import uk.home_hosting_network.servertools.commands.PlayersCommand;
import uk.home_hosting_network.servertools.commands.RulesCommand;
import uk.home_hosting_network.servertools.listeners.PlayerJoinListener;
import uk.home_hosting_network.servertools.util.PluginConfig;

import java.util.logging.Logger;

/**
 * ServerTools - Essential utility plugin for Project Poseidon (Beta 1.7.3)
 *
 * Provides: /players, /help, /rules, /ticks
 * and a join message directing new players to /rules and /help.
 *
 * Compatible with the CB1060 API (poseidon-craftbukkit 1.1.8).
 */
public class ServerTools extends JavaPlugin {

    private static ServerTools instance;

    // CB1060 JavaPlugin does not have getLogger() — we hold our own.
    private Logger log;

    // CB1060 JavaPlugin does not have getConfig()/saveDefaultConfig() — we manage config ourselves.
    private PluginConfig pluginConfig;

    @Override
    public void onEnable() {
        instance = this;
        log = Logger.getLogger("Minecraft");

        // Load config from disk (creates defaults if absent)
        pluginConfig = new PluginConfig(this);
        pluginConfig.load();

        // Register commands
        getCommand("players").setExecutor(new PlayersCommand(this));
        getCommand("help").setExecutor(new HelpCommand(this));
        getCommand("rules").setExecutor(new RulesCommand(this));

        // Register listener
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        log.info("[ServerTools] v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        log.info("[ServerTools] disabled.");
        instance = null;
    }

    public static ServerTools getInstance() {
        return instance;
    }

    /** Returns our config wrapper (replacement for getConfig()). */
    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    /** Returns the plugin logger (replacement for getLogger()). */
    public Logger log() {
        return log;
    }
}
