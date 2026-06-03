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
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;

import javax.annotation.Nonnull;
import javax.security.auth.login.LoginException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the JDA Discord bot.
 *
 * ── Discord slash commands (guild-scoped, instant registration) ──────────
 *   /discordlink <username>  Step 1 of account linking — generates a code
 *   /resetlink               Removes confirmed or pending link
 *
 * ── Security flow ─────────────────────────────────────────────────────────
 *   1. Discord user runs /discordlink Steve
 *      → ephemeral reply with a 6-digit SecureRandom code, valid 10 minutes
 *   2. Player joins Minecraft, types /discord confirm <code>
 *   3. Code matches and hasn't expired → link committed to links.yml
 *
 * ── Role lookup (chat performance) ────────────────────────────────────────
 *   getRoleForPlayer() is called on every chat message (Bukkit main thread).
 *   To avoid blocking REST calls per message, roles are cached in RoleCache
 *   and invalidated automatically when a member's roles change or they leave.
 */
public class DiscordBotManager extends ListenerAdapter {

    private final DiscordRolesPlugin plugin;
    private final DatabaseManager    db;
    private final PendingLinkManager pending;
    private final RoleCache          roleCache;

    private JDA    jda;
    private String guildId;
    private String linkChannelId;

    public DiscordBotManager(DiscordRolesPlugin plugin,
                             DatabaseManager db,
                             PendingLinkManager pending) {
        this.plugin    = plugin;
        this.db        = db;
        this.pending   = pending;
        this.roleCache = new RoleCache();
    }

    // ---------------------------------------------------------------
    //  Lifecycle
    // ---------------------------------------------------------------

    public void start() {
        String token  = plugin.cfg().getString("bot-token", "");
        guildId       = plugin.cfg().getString("guild-id", "");
        linkChannelId = plugin.cfg().getString("link-channel-id", "");

        if (token.isEmpty() || token.equals("YOUR_BOT_TOKEN_HERE")) {
            plugin.log("No bot-token configured — Discord integration disabled.");
            return;
        }
        if (guildId.isEmpty() || guildId.equals("YOUR_GUILD_ID_HERE")) {
            plugin.log("No guild-id configured — Discord integration disabled.");
            return;
        }
        if (linkChannelId.isEmpty() || linkChannelId.equals("YOUR_LINK_CHANNEL_ID_HERE")) {
            plugin.log("No link-channel-id configured — Discord integration disabled.");
            return;
        }

        roleCache.clear();

        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES)
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
            roleCache.clear();
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

        guild.upsertCommand(
                new CommandData("discordlink",
                        "Link your Minecraft account to this Discord server")
                        .addOptions(new OptionData(OptionType.STRING, "username",
                                "Your exact Minecraft username", true))
        ).queue(
                ok  -> plugin.log("Registered /discordlink"),
                err -> plugin.log("Failed to register /discordlink: " + err.getMessage())
        );

        guild.upsertCommand(
                new CommandData("resetlink",
                        "Remove your Minecraft account link so you can re-link")
        ).queue(
                ok  -> plugin.log("Registered /resetlink"),
                err -> plugin.log("Failed to register /resetlink: " + err.getMessage())
        );

        postWelcomeMessage(guild);
    }

    private void postWelcomeMessage(Guild guild) {
        TextChannel channel = guild.getTextChannelById(linkChannelId);
        if (channel == null) {
            plugin.log("WARNING: Link channel not found (id=" + linkChannelId
                    + ") — check link-channel-id in config.yml");
            return;
        }

        String msg =
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
              + "🔗  **Minecraft Account Linking**\n"
              + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n"
              + "Connect your Discord account to Minecraft so your roles appear in chat.\n\n"
              + "**Step 1 — Use this slash command here:**\n"
              + "> `/discordlink <your Minecraft username>`\n"
              + "> *Example:* `/discordlink Steve`\n\n"
              + "**Step 2 — Join the Minecraft server and type:**\n"
              + "> `/discord confirm <code>`\n"
              + "> *(Your personal code is shown after Step 1 — only you can see it)*\n\n"
              + "**Need to fix a wrong username?**\n"
              + "> Use `/resetlink` then `/discordlink` again.\n\n"
              + "⏱ Confirmation codes expire after **10 minutes**.\n"
              + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

        channel.sendMessage(msg).queue(
                ok  -> plugin.log("Welcome message posted in link channel."),
                err -> plugin.log("Could not post welcome message: " + err.getMessage())
        );
    }

    // ---------------------------------------------------------------
    //  Role cache invalidation events
    // ---------------------------------------------------------------

    /** Invalidate cache when a member gains a role. */
    @Override
    public void onGuildMemberRoleAdd(@Nonnull GuildMemberRoleAddEvent event) {
        if (!event.getGuild().getId().equals(guildId)) return;
        roleCache.invalidate(event.getMember().getId());
    }

    /** Invalidate cache when a member loses a role. */
    @Override
    public void onGuildMemberRoleRemove(@Nonnull GuildMemberRoleRemoveEvent event) {
        if (!event.getGuild().getId().equals(guildId)) return;
        roleCache.invalidate(event.getMember().getId());
    }

    /** Invalidate cache when a member leaves the guild. */
    @Override
    public void onGuildMemberRemove(@Nonnull GuildMemberRemoveEvent event) {
        if (!event.getGuild().getId().equals(guildId)) return;
        User user = event.getUser();
        if (user != null) roleCache.invalidate(user.getId());
    }

    // ---------------------------------------------------------------
    //  Slash command dispatcher
    // ---------------------------------------------------------------

    @Override
    public void onSlashCommand(@Nonnull SlashCommandEvent event) {
        if (event.getGuild() == null
                || !event.getGuild().getId().equals(guildId)) {
            event.reply("❌ This command can only be used in the correct server.")
                    .setEphemeral(true).queue();
            return;
        }
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

        OptionMapping opt = event.getOption("username");
        if (opt == null) {
            event.reply("❌ Please provide your Minecraft username.")
                    .setEphemeral(true).queue();
            return;
        }
        String mcName = opt.getAsString().trim();

        // Validate Minecraft username (1–16 chars, alphanumeric + underscore)
        if (mcName.isEmpty() || mcName.length() > 16
                || !mcName.matches("[A-Za-z0-9_]+")) {
            event.reply("❌ **Invalid username.** Minecraft usernames are 1–16 characters "
                      + "containing only letters, numbers, and underscores.")
                    .setEphemeral(true).queue();
            return;
        }

        // Already permanently linked?
        if (db.isDiscordLinked(discordId)) {
            event.reply("⚠️ Your Discord is already linked to **"
                      + db.getMinecraftName(discordId) + "**.\n"
                      + "Use `/resetlink` if you need to change it.")
                    .setEphemeral(true).queue();
            return;
        }

        // Minecraft name already claimed by a different account?
        if (db.isLinked(mcName)) {
            event.reply("❌ **" + mcName + "** is already linked to a different Discord account.\n"
                      + "If this is your account, contact an admin.")
                    .setEphemeral(true).queue();
            return;
        }

        // Generate pending link with a fresh SecureRandom code
        PendingLink link = pending.create(discordId, discordTag, mcName);
        plugin.log("Pending link: Discord=" + discordTag + " → MC=" + mcName
                + " (code hidden from log for security)");

        event.reply(
                "✅ **Almost there!** Your confirmation code has been generated.\n\n"
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

        // Clear pending link if one exists
        PendingLink pend = pending.getByDiscord(discordId);
        if (pend != null) pending.remove(pend.getMinecraftName());

        // Clear role cache entry
        roleCache.invalidate(discordId);

        if (!db.isDiscordLinked(discordId)) {
            event.reply("ℹ️ You don't have a linked Minecraft account.\n"
                      + "Use `/discordlink <username>` to link one.")
                    .setEphemeral(true).queue();
            return;
        }

        String oldName = db.getMinecraftName(discordId);
        db.unlinkByDiscordId(discordId);
        plugin.log("Link reset via Discord: " + event.getUser().getAsTag()
                + " (was MC=" + oldName + ")");

        event.reply("🔄 Your link to **" + oldName + "** has been removed.\n"
                  + "Use `/discordlink <username>` to link a new account.")
                .setEphemeral(true).queue();
    }

    // ---------------------------------------------------------------
    //  Role lookup — called by ChatListener on the Bukkit main thread
    // ---------------------------------------------------------------

    /**
     * Returns the display role name for a Minecraft player, or null.
     *
     * Uses the RoleCache to avoid blocking REST calls on the main thread.
     * On a cache miss, the lookup runs async via JDA's thread pool and the
     * result is stored in the cache — the *current* chat message shows the
     * uncached (no-role) format, but all subsequent messages are instant.
     *
     * This means a player's role appears from their *second* chat message
     * after linking (or after a role change). This is an acceptable trade-off
     * for main-thread safety on a community-sized server.
     */
    public String getRoleForPlayer(String minecraftName) {
        if (jda == null) return null;

        String discordId = db.getDiscordId(minecraftName);
        if (discordId == null) return null; // not linked

        // Cache hit — return immediately, no REST call
        String cached = roleCache.get(discordId);
        if (cached != null) {
            return cached.isEmpty() ? null : cached; // "" means "no role"
        }

        // Cache miss — fetch async, cache the result for next time
        final String discordIdFinal = discordId;
        final String minecraftNameFinal = minecraftName;

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return null;

        guild.retrieveMemberById(discordIdFinal).queue(
                member -> {
                    if (member == null) {
                        roleCache.put(discordIdFinal, null);
                        return;
                    }
                    String role = resolveRole(member);
                    roleCache.put(discordIdFinal, role);
                    if (role != null) {
                        plugin.log("Role cached for " + minecraftNameFinal + ": " + role);
                    }
                },
                err -> {
                    // Member not found or left guild — cache as "no role" to avoid
                    // hammering Discord API on every chat message
                    roleCache.put(discordIdFinal, null);
                }
        );

        return null; // Return null this message; role shows next message
    }

    /**
     * Resolves the highest-priority role name from a guild member's roles.
     * Returns null if no qualifying role is found.
     */
    private String resolveRole(Member member) {
        List<String> ignoredRoles  = plugin.cfg().getStringList("ignored-roles",
                new ArrayList<String>());
        List<String> priorityRoles = plugin.cfg().getStringList("role-priority",
                new ArrayList<String>());
        List<Role> memberRoles = member.getRoles();

        // Walk priority list first — highest configured priority wins
        for (String priority : priorityRoles) {
            for (Role role : memberRoles) {
                if (role.getName().equalsIgnoreCase(priority)
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

    /**
     * Invalidates the role cache for a given Discord ID.
     * Called externally when a player unlinks their account in-game.
     */
    public void invalidateRoleCache(String discordId) {
        roleCache.invalidate(discordId);
    }

    public boolean isConnected() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }
}
