package uk.co.homehosting.discordwebhook;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChatEvent;

public class ChatListener implements Listener {

    private final DiscordWebhookPlugin plugin;

    public ChatListener(DiscordWebhookPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Listen at MONITOR priority so we only forward chat that was not cancelled
     * by another plugin (e.g. a mute/antispam plugin).
     */
    @EventHandler(priority = Event.Priority.Monitor, ignoreCancelled = true)
    public void onPlayerChat(PlayerChatEvent event) {
        plugin.getWebhookClient().sendChat(
                event.getPlayer().getName(),
                event.getMessage()
        );
    }
}
