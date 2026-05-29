package com.miner3x3;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public class Miner3x3Plugin extends JavaPlugin {

    // CB1060 JavaPlugin has no getLogger() — use the server logger directly
    public static final Logger log = Logger.getLogger("Minecraft");

    // UUIDs of players who hold a miner pickaxe — persisted to config.yml
    public final Set<UUID> minerPickaxeHolders = new HashSet<>();

    // Config key for the UUID list
    private static final String CONFIG_KEY = "holders";

    @Override
    public void onEnable() {
        // Ensure data folder exists so we can write config
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        loadHolders();

        getServer().getPluginManager().registerEvents(new MinerListener(this), this);
        getCommand("3x3").setExecutor(new MinerCommand(this));

        log.info("[Miner3x3] Enabled. " + minerPickaxeHolders.size() + " holder(s) loaded.");
    }

    @Override
    public void onDisable() {
        saveHolders();
        log.info("[Miner3x3] Disabled. " + minerPickaxeHolders.size() + " holder(s) saved.");
    }

    /** Persist the UUID list to config.yml */
    public void saveHolders() {
        Configuration config = getConfiguration();
        List<String> uuidStrings = new ArrayList<>();
        for (UUID uuid : minerPickaxeHolders) {
            uuidStrings.add(uuid.toString());
        }
        config.setProperty(CONFIG_KEY, uuidStrings);
        config.save();
    }

    /** Load the UUID list from config.yml */
    private void loadHolders() {
        Configuration config = getConfiguration();
        List<String> uuidStrings = config.getStringList(CONFIG_KEY, new ArrayList<String>());
        minerPickaxeHolders.clear();
        for (String s : uuidStrings) {
            try {
                minerPickaxeHolders.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed entries
            }
        }
    }
}
