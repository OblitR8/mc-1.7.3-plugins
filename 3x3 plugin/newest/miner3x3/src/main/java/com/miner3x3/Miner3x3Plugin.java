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

    // CB1060 JavaPlugin has no getLogger() — use the Minecraft server logger directly
    public static final Logger log = Logger.getLogger("Minecraft");

    // UUID set of players who have been granted the 3x3 pickaxe
    // Persisted to config.yml so it survives server restarts
    public final Set<UUID> minerPickaxeHolders = new HashSet<>();

    private static final String CONFIG_KEY = "holders";

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        loadHolders();

        getServer().getPluginManager().registerEvents(new MinerListener(this), this);
        getCommand("3x3").setExecutor(new MinerCommand(this));

        log.info("[Miner3x3] Enabled — " + minerPickaxeHolders.size() + " holder(s) loaded.");
    }

    @Override
    public void onDisable() {
        saveHolders();
        log.info("[Miner3x3] Disabled — " + minerPickaxeHolders.size() + " holder(s) saved.");
    }

    /** Save UUID list to config.yml */
    public void saveHolders() {
        Configuration config = getConfiguration();
        List<String> list = new ArrayList<>();
        for (UUID uuid : minerPickaxeHolders) {
            list.add(uuid.toString());
        }
        config.setProperty(CONFIG_KEY, list);
        config.save();
    }

    /** Load UUID list from config.yml */
    private void loadHolders() {
        Configuration config = getConfiguration();
        List<String> list = config.getStringList(CONFIG_KEY, new ArrayList<String>());
        minerPickaxeHolders.clear();
        for (String s : list) {
            try {
                minerPickaxeHolders.add(UUID.fromString(s.trim()));
            } catch (IllegalArgumentException ignored) {
                log.warning("[Miner3x3] Skipping malformed UUID in config: " + s);
            }
        }
    }
}
