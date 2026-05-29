package uk.co.homehosting.discordwebhook;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ConnectionListener implements Listener {

    private final DiscordWebhookPlugin plugin;

    public ConnectionListener(DiscordWebhookPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = Event.Priority.Monitor)
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getWebhookClient().sendJoin(event.getPlayer().getName());
    }

    @EventHandler(priority = Event.Priority.Monitor)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getWebhookClient().sendLeave(event.getPlayer().getName());
    }
}
