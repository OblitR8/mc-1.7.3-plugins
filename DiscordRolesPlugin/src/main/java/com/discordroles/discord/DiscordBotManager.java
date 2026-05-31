package com.discordroles.discord;

import com.discordroles.DiscordRolesPlugin;
import com.discordroles.storage.DatabaseManager;
import com.discordroles.storage.PendingLink;
import com.discordroles.storage.PendingLinkManager;
import com.discordroles.utils.ColorUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import javax.annotation.Nonnull;
import javax.security.auth.login.LoginException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the JDA Discord bot.
 *
 * Slash commands registered to the guild:
 *   /discordlink <username>  - Step 1: generate a confirmation code
 *   /resetlink               - Remove the link so a new one can be made
 *
 * Security flow:
 *   1. User runs /discordlink Steve  → ephemeral reply with a 6-digit code
 *   2. Player joins Minecraft and runs /discord confirm <code>
 *   3. Code matches  → account linked permanently in links.yml
 *   4. Code wrong/expired → rejected with clear feedback
 */
public class DiscordBotManager extends ListenerAdapter {

    private final DiscordRolesPlugin  plugin;
    private final DatabaseManager     db;
    private final PendingLinkManager  pending;
    private JDA jda;

    private String guildId;
    private String linkChannelId;

    public DiscordBotManager(DiscordRolesPlugin plugin,
                             DatabaseManager db,
                             PendingLinkManager pending) {
        this.plugin  = plugin;
        this.db      = db;
        this.pending = pending;
    }

    // ---------------------------------------------------------------
    //  Lifecycle
    // ---------------------------------------------------------------

    public void start() {
        String token  = plugin.cfg().getString("bot-token", "");
        guildId       = plugin.cfg().getString("guild-id", "");
        linkChannelId = plugin.cfg().getString("link-channel-id", "");

        if (token.isEmpty() || token.equals("YOUR_BOT_TOKEN_HERE")) {
            plugin.log("No bot token configured. Discord integration disabled.");
            return;
        }
        if (guildId.isEmpty() || guildId.equals("YOUR_GUILD_ID_HERE")) {
            plugin.log("No guild-id configured. Discord integration disabled.");
            return;
        }
        if (linkChannelId.isEmpty() || linkChannelId.equals("YOUR_LINK_CHANNEL_ID_HERE")) {
            plugin.log("No link-channel-id configured. Discord integration disabled.");
            return;
        }

        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .addEventListeners(this)
                    .build();

            jda.awaitReady();
            plugin.log("Discord bot online: " + jda.getSelfUser().getAsTag());

        } catch (LoginException e) {
            plugin.log("ERROR: Invalid bot token — " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.log("ERROR: Bot startup interrupted.");
        } catch (IllegalStateException e) {
            plugin.log("ERROR: Bot failed to start — " + e.getMessage());
        }
    }

    public void stop() {
        if (jda != null) {
            jda.shutdown();
            jda = null;
            plugin.log("Discord bot disconnected.");
        }
    }

    // ---------------------------------------------------------------
    //  Guild ready — register slash commands + post welcome message
    // ---------------------------------------------------------------

    @Override
    public void onGuildReady(@Nonnull GuildReadyEvent event) {
        if (!event.getGuild().getId().equals(guildId)) return;

        Guild guild = event.getGuild();

        // /discordlink <username>
        guild.upsertCommand(
                new CommandData("discordlink", "Link your Minecraft account to this Discord server")
                        .addOptions(new OptionData(OptionType.STRING, "username",
                                "Your exact Minecraft username", true))
        ).queue(
                cmd -> plugin.log("Registered slash command: /discordlink"),
                err -> plugin.log("Failed to register /discordlink: " + err.getMessage())
        );

        // /resetlink
        guild.upsertCommand(
                new CommandData("resetlink",
                        "Remove your Minecraft account link so you can re-link")
        ).queue(
                cmd -> plugin.log("Registered slash command: /resetlink"),
                err -> plugin.log("Failed to register /resetlink: " + err.getMessage())
        );

        // Welcome message
        postWelcomeMessage(guild);
    }

    private void postWelcomeMessage(Guild guild) {
        TextChannel channel = guild.getTextChannelById(linkChannelId);
        if (channel == null) {
            plugin.log("WARNING: Link channel not found (id=" + linkChannelId
                    + "). Check link-channel-id in config.yml");
            return;
        }

        String msg =
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
              + "🔗  **Minecraft Account Linking**\n"
              + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n"
              + "Connect your Discord account to Minecraft so your roles appear in chat.\n\n"
              + "**Step 1 — Run this command here:**\n"
              + "> `/discordlink <your Minecraft username>`\n"
              + "> *Example:* `/discordlink Steve`\n\n"
              + "**Step 2 — Join the Minecraft server and type:**\n"
              + "> `/discord confirm <code>`\n"
              + "> *(You will receive your personal code after Step 1)*\n\n"
              + "**Made a mistake or need to re-link?**\n"
              + "> Use `/resetlink` to remove your current link.\n\n"
              + "⏱ Codes expire after **10 minutes**.\n"
              + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

        channel.sendMessage(msg).queue(
                ok  -> plugin.log("Welcome message posted in link channel."),
                err -> plugin.log("Could not post welcome message: " + err.getMessage())
        );
    }

    // ---------------------------------------------------------------
    //  Slash command dispatcher
    // ---------------------------------------------------------------

    @Override
    public void onSlashCommand(@Nonnull SlashCommandEvent event) {
        // Must be in our guild
        if (event.getGuild() == null || !event.getGuild().getId().equals(guildId)) {
            event.reply("❌ This command can only be used in the correct server.")
                    .setEphemeral(true).queue();
            return;
        }

        // Must be in the link channel
        if (!event.getChannel().getId().equals(linkChannelId)) {
            event.reply("❌ Please use this command in the **account-link** channel.")
                    .setEphemeral(true).queue();
            return;
        }

        switch (event.getName()) {
            case "discordlink": handleDiscordLink(event); break;
            case "resetlink":   handleResetLink(event);   break;
        }
    }

    // ---------------------------------------------------------------
    //  /discordlink <username>
    // ---------------------------------------------------------------

    private void handleDiscordLink(SlashCommandEvent event) {
        String discordId  = event.getUser().getId();
        String discordTag = event.getUser().getAsTag();

        // Retrieve the username option safely
        OptionMapping opt = event.getOption("username");
        if (opt == null) {
            event.reply("❌ Please provide your Minecraft username.\nUsage: `/discordlink <username>`")
                    .setEphemeral(true).queue();
            return;
        }
        String mcName = opt.getAsString().trim();

        // Validate Minecraft username format
        if (mcName.isEmpty() || mcName.length() > 16 || !mcName.matches("[A-Za-z0-9_]+")) {
            event.reply("❌ **Invalid username.** Minecraft usernames are 1–16 characters and contain only letters, numbers, and underscores.")
                    .setEphemeral(true).queue();
            return;
        }

        // Already permanently linked?
        if (db.isDiscordLinked(discordId)) {
            String current = db.getMinecraftName(discordId);
            event.reply("⚠️ Your Discord is already linked to **" + current + "**.\n"
                      + "Use `/resetlink` if you need to change it.")
                    .setEphemeral(true).queue();
            return;
        }

        // Minecraft name already taken by a different Discord account?
        if (db.isLinked(mcName)) {
            event.reply("❌ **" + mcName + "** is already linked to a different Discord account.\n"
                      + "If this is your account, contact an admin.")
                    .setEphemeral(true).queue();
            return;
        }

        // Create (or replace) a pending link and generate a fresh code
        PendingLink link = pending.create(discordId, discordTag, mcName);

        plugin.log("Pending link created: Discord=" + discordTag
                + " → MC=" + mcName + " code=" + link.getCode());

        // Reply is ephemeral — only the user can see it
        event.reply(
                "✅ Almost there! A confirmation code has been generated.\n\n"
              + "**Join the Minecraft server and type:**\n"
              + "```\n/discord confirm " + link.getCode() + "\n```\n"
              + "⏱ This code expires in **10 minutes**.\n"
              + "🔒 *Only you can see this message.*"
        ).setEphemeral(true).queue();
    }

    // ---------------------------------------------------------------
    //  /resetlink
    // ---------------------------------------------------------------

    private void handleResetLink(SlashCommandEvent event) {
        String discordId = event.getUser().getId();

        // Remove any pending link first
        PendingLink pend = pending.getByDiscord(discordId);
        if (pend != null) {
            pending.remove(pend.getMinecraftName());
        }

        // Remove any confirmed link
        if (!db.isDiscordLinked(discordId)) {
            event.reply("ℹ️ You don't have a linked Minecraft account.\n"
                      + "Use `/discordlink <username>` to link one.")
                    .setEphemeral(true).queue();
            return;
        }

        String oldName = db.getMinecraftName(discordId);
        db.unlinkByDiscordId(discordId);

        plugin.log("Link reset by Discord user: " + event.getUser().getAsTag()
                + " (was: " + oldName + ")");

        event.reply("🔄 Your link to **" + oldName + "** has been removed.\n"
                  + "Use `/discordlink <username>` to link a new account.")
                .setEphemeral(true).queue();
    }

    // ---------------------------------------------------------------
    //  Role lookup — called by ChatListener (Bukkit thread)
    // ---------------------------------------------------------------

    /**
     * Returns the highest-priority Discord role name for a Minecraft player.
     * Returns null if the player is not linked or has no qualifying roles.
     *
     * This method makes a blocking REST call (retrieveMemberById) and must
     * NOT be called on the main Bukkit thread in a production high-traffic
     * server. For a small community server this is acceptable; for scale,
     * move to async with a role-cache updated on member role change events.
     */
    public String getRoleForPlayer(String minecraftName) {
        if (jda == null) return null;

        String discordId = db.getDiscordId(minecraftName);
        if (discordId == null) return null;

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return null;

        Member member = guild.getMemberById(discordId);
        if (member == null) {
            // Avoid blocking the Bukkit thread by never falling back to REST here.
            return null;
        }

        List<String> ignoredRoles  = plugin.cfg().getStringList("ignored-roles",  new ArrayList<String>());
        List<String> priorityRoles = plugin.cfg().getStringList("role-priority",   new ArrayList<String>());
        List<Role>   memberRoles   = member.getRoles();

        // Walk priority list — first match wins
        for (String priorityRole : priorityRoles) {
            for (Role role : memberRoles) {
                if (role.getName().equalsIgnoreCase(priorityRole)
                        && !ColorUtil.isIgnoredRole(role.getName(), ignoredRoles)) {
                    return role.getName();
                }
            }
        }
        // Fallback — first non-ignored role
        for (Role role : memberRoles) {
            if (!ColorUtil.isIgnoredRole(role.getName(), ignoredRoles)) {
                return role.getName();
            }
        }
        return null;
    }

    public boolean isConnected() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }
}
