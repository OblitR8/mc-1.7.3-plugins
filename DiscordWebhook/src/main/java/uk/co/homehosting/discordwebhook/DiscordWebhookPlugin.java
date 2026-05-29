package uk.co.homehosting.discordwebhook;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DiscordWebhookPlugin extends JavaPlugin {

    private static final Logger LOG = Logger.getLogger("Minecraft");

    private WebhookClient webhookClient;
    private PluginConfig discordConfig;

    @Override
    public void onEnable() {
        saveDefaultConfigFile();
        reload();
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);
        log("DiscordWebhook v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        log("DiscordWebhook disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("discordwebhook")) return false;

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.isOp() && !sender.hasPermission("discordwebhook.reload")) {
                sender.sendMessage("\u00a7cYou don't have permission to do that.");
                return true;
            }
            reload();
            sender.sendMessage("\u00a7aDiscordWebhook reloaded.");
            return true;
        }

        sender.sendMessage("\u00a7eUsage: /discordwebhook reload");
        return true;
    }

    // -----------------------------------------------------------------------
    // Reload — re-reads config.yml and rebuilds WebhookClient
    // -----------------------------------------------------------------------

    private void reload() {
        discordConfig  = new PluginConfig(getDataFolder());
        webhookClient  = new WebhookClient(this);
        validateConfig();
    }

    private void validateConfig() {
        String url = discordConfig.getWebhookUrl();
        if (url == null || url.isEmpty() || url.contains("YOUR_WEBHOOK")) {
            logWarning("────────────────────────────────────────────────────");
            logWarning("  Webhook URL is not configured!");
            logWarning("  Edit plugins/DiscordWebhook/config.yml");
            logWarning("  and replace YOUR_WEBHOOK_ID / YOUR_WEBHOOK_TOKEN.");
            logWarning("────────────────────────────────────────────────────");
        }
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public WebhookClient getWebhookClient() { return webhookClient; }
    public PluginConfig  getDiscordConfig()  { return discordConfig; }

    public void log(String msg) {
        LOG.info("[DiscordWebhook] " + msg);
    }

    public void logWarning(String msg) {
        LOG.log(Level.WARNING, "[DiscordWebhook] " + msg);
    }

    // -----------------------------------------------------------------------
    // Copy bundled config.yml to data folder on first run
    // -----------------------------------------------------------------------

    private void saveDefaultConfigFile() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();

        File configFile = new File(dataFolder, "config.yml");
        if (configFile.exists()) return;

        InputStream in = getClass().getResourceAsStream("/config.yml");
        if (in == null) {
            logWarning("Bundled config.yml not found in jar — this is a build problem.");
            return;
        }
        try {
            OutputStream out = new FileOutputStream(configFile);
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            out.close();
            in.close();
        } catch (IOException e) {
            logWarning("Failed to write default config.yml: " + e.getMessage());
        }
    }
}
