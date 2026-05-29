package uk.home_hosting_network.servertools.util;

import org.bukkit.util.config.Configuration;
import uk.home_hosting_network.servertools.ServerTools;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin wrapper around CB1060's org.bukkit.util.config.Configuration
 * (the old YAML API — FileConfiguration did not exist yet).
 *
 * Handles saving the default config.yml from the JAR on first run.
 */
public class PluginConfig {

    private final ServerTools plugin;
    private Configuration config;

    public PluginConfig(ServerTools plugin) {
        this.plugin = plugin;
    }

    /** Load (or create) config.yml in the plugin data folder. */
    public void load() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File configFile = new File(dataFolder, "config.yml");

        // Copy default config out of the JAR if it does not already exist
        if (!configFile.exists()) {
            InputStream defaultConfig = plugin.getClass().getResourceAsStream("/config.yml");
            if (defaultConfig != null) {
                try {
                    Files.copy(defaultConfig, configFile.toPath());
                } catch (IOException e) {
                    plugin.log().warning("[ServerTools] Could not copy default config: " + e.getMessage());
                }
            }
        }

        config = new Configuration(configFile);
        config.load();
    }

    /**
     * Returns a string list from the config.
     * Falls back to an empty list if the key is absent.
     */
    public List<String> getStringList(String key) {
        List<String> result = config.getStringList(key, null);
        return result != null ? result : new ArrayList<String>();
    }

    /**
     * Returns a boolean from the config with a default fallback.
     */
    public boolean getBoolean(String key, boolean def) {
        // CB1060 Configuration.getBoolean(path, default)
        return config.getBoolean(key, def);
    }
}
