package com.discordroles.discord;

import com.discordroles.DiscordRolesPlugin;
import com.discordroles.storage.DatabaseManager;
import com.discordroles.utils.ColorUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

import javax.annotation.Nonnull;
import javax.security.auth.login.LoginException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the JDA Discord bot instance.
 *
 * Discord commands (in the configured link channel):
 *   !link <MinecraftUsername>  - Links the Discord user to a Minecraft name
 *   !resetlink                 - Clears the link so the user can redo it
 *
 * Compatible with Project Poseidon (CB1060 Bukkit API).
 *
 * Intents used:
 *   GUILD_MESSAGES  - non-privileged, needed to read messages in channels
 *
 * GUILD_MEMBERS is NOT requested (it is privileged and requires manual
 * activation in the Developer Portal). Instead, retrieveMemberById() falls
 * back to a REST API call which works without the intent.
 */
public class DiscordBotManager extends ListenerAdapter {

    private final DiscordRolesPlugin plugin;
    private final DatabaseManager db;
    private JDA jda;

    private String guildId;
    private String linkChannelId;

    public DiscordBotManager(DiscordRolesPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    // ---------------------------------------------------------------
    //  Lifecycle
    // ---------------------------------------------------------------

    public void start() {
        String token  = plugin.cfg().getString("bot-token", "");
        guildId       = plugin.cfg().getString("guild-id", "");
        linkChannelId = plugin.cfg().getString("link-channel-id", "");

        if (token.isEmpty() || token.equals("YOUR_BOT_TOKEN_HERE")) {
            plugin.log("No bot token set in config.yml! Discord integration disabled.");
            return;
        }

        try {
            jda = JDABuilder.createDefault(token)
                    // GUILD_MESSAGES is the only intent needed - it is non-privileged
                    .enableIntents(GatewayIntent.GUILD_MESSAGES)
                    .addEventListeners(this)
                    .build();

            jda.awaitReady();
            plugin.log("Discord bot connected as: " + jda.getSelfUser().getAsTag());

        } catch (LoginException e) {
            plugin.log("ERROR: Invalid Discord bot token! " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.log("ERROR: Discord bot start interrupted.");
        } catch (IllegalStateException e) {
            plugin.log("ERROR: Discord bot failed to start: " + e.getMessage());
        }
    }

    public void stop() {
        if (jda != null) {
            jda.shutdown();
            jda = null;
        }
    }

    // ---------------------------------------------------------------
    //  Discord event listener
    // ---------------------------------------------------------------

    @Override
    public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!event.getChannel().getId().equals(linkChannelId)) return;

        String raw     = event.getMessage().getContentRaw().trim();
        Member member  = event.getMember();
        if (member == null) return;

        String discordId = member.getId();

        if (raw.toLowerCase().startsWith("!link ")) {
            handleLink(event, discordId, raw.substring(6).trim());
            return;
        }

        if (raw.equalsIgnoreCase("!resetlink")) {
            handleReset(event, discordId);
            return;
        }

        if (raw.startsWith("!")) {
            event.getChannel().sendMessage(buildHelp(member.getEffectiveName())).queue();
        }
    }

    // ---------------------------------------------------------------
    //  Command handlers
    // ---------------------------------------------------------------

    private void handleLink(GuildMessageReceivedEvent event, String discordId, String mcName) {
        TextChannel channel = event.getChannel();

        if (mcName.isEmpty() || mcName.length() > 16 || !mcName.matches("[A-Za-z0-9_]+")) {
            channel.sendMessage(
                    "❌ **Invalid username.** Minecraft usernames can only contain letters, numbers, and underscores (max 16 characters).\n"
                    + "Usage: `!link YourMinecraftName`"
            ).queue();
            return;
        }

        if (db.isDiscordLinked(discordId)) {
            String existing = db.getMinecraftName(discordId);
            channel.sendMessage(
                    "⚠️ Your Discord account is already linked to **" + existing + "**.\n"
                    + "Use `!resetlink` and then `!link` again with the correct name."
            ).queue();
            return;
        }

        if (db.isLinked(mcName)) {
            channel.sendMessage(
                    "❌ The Minecraft name **" + mcName + "** is already linked to a different Discord account.\n"
                    + "If this is your account please contact an admin."
            ).queue();
            return;
        }

        boolean ok = db.linkAccount(mcName, discordId);
        if (ok) {
            channel.sendMessage(
                    "✅ Successfully linked **" + mcName + "** to your Discord account!\n"
                    + "Your Discord roles will now appear in Minecraft chat. 🎉\n\n"
                    + "Made a mistake? Use `!resetlink` to unlink and try again."
            ).queue();
            plugin.log("Linked Discord " + event.getAuthor().getAsTag()
                    + " (" + discordId + ") → Minecraft: " + mcName);
        } else {
            channel.sendMessage("❌ A database error occurred. Please try again or contact an admin.").queue();
        }
    }

    private void handleReset(GuildMessageReceivedEvent event, String discordId) {
        TextChannel channel = event.getChannel();

        if (!db.isDiscordLinked(discordId)) {
            channel.sendMessage(
                    "ℹ️ Your Discord account isn't linked to any Minecraft account yet.\n"
                    + "Use `!link YourMinecraftName` to link your account."
            ).queue();
            return;
        }

        String oldName = db.getMinecraftName(discordId);
        boolean ok = db.unlinkByDiscordId(discordId);
        if (ok) {
            channel.sendMessage(
                    "🔄 Your link to **" + oldName + "** has been reset.\n"
                    + "Use `!link YourMinecraftName` to link with the correct name."
            ).queue();
            plugin.log("Reset link for Discord " + event.getAuthor().getAsTag()
                    + " (was linked to MC: " + oldName + ")");
        } else {
            channel.sendMessage("❌ A database error occurred. Please try again or contact an admin.").queue();
        }
    }

    // ---------------------------------------------------------------
    //  Role lookup — called by ChatListener on every chat message
    // ---------------------------------------------------------------

    /**
     * Returns the highest-priority Discord role name for a Minecraft player,
     * or null if not linked / no matching role.
     *
     * Uses retrieveMemberById() which makes a REST call — this does NOT
     * require the GUILD_MEMBERS privileged intent.
     */
    public String getRoleForPlayer(String minecraftName) {
        if (jda == null) return null;

        String discordId = db.getDiscordId(minecraftName);
        if (discordId == null) return null;

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            plugin.log("WARNING: Guild not found: " + guildId);
            return null;
        }

        Member member;
        try {
            // REST-based lookup — works without GUILD_MEMBERS intent
            member = guild.retrieveMemberById(discordId).complete();
        } catch (Exception e) {
            // Member left the server, or ID is invalid
            return null;
        }
        if (member == null) return null;

        List<String> ignoredRoles  = plugin.cfg().getStringList("ignored-roles",  new ArrayList<String>());
        List<String> priorityRoles = plugin.cfg().getStringList("role-priority",   new ArrayList<String>());

        List<Role> memberRoles = member.getRoles();

        // Walk the priority list — first match wins
        for (String priorityRole : priorityRoles) {
            for (Role role : memberRoles) {
                if (role.getName().equalsIgnoreCase(priorityRole)
                        && !ColorUtil.isIgnoredRole(role.getName(), ignoredRoles)) {
                    return role.getName();
                }
            }
        }

        // Fallback: first non-ignored role the player has
        for (Role role : memberRoles) {
            if (!ColorUtil.isIgnoredRole(role.getName(), ignoredRoles)) {
                return role.getName();
            }
        }

        return null;
    }

    // ---------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------

    private String buildHelp(String name) {
        return "👋 Hi **" + name + "**! Here's how to link your Minecraft account:\n\n"
                + "`!link <MinecraftUsername>` – Link your account\n"
                + "`!resetlink`               – Reset a wrong link so you can re-enter your name\n\n"
                + "Example: `!link Steve`";
    }

    public boolean isConnected() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }
}