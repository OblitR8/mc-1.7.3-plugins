# DiscordRolesPlugin

Discord role → Minecraft chat integration for **Beta 1.7.3** (Project Poseidon).

---

## What it does

- Players link their Minecraft account to Discord via a simple `!link` command in your Discord server
- When they chat in-game, their **highest Discord role** appears as a prefix:
  - `[Admin] Steve: hello everyone`
  - `[Member] Alex: hey all`
  - `[player] Notch: I have no roles`
- Players who mistyped their name can run `!resetlink` in Discord and re-link
- Roles are coloured in chat based on your config

---

## Requirements

- Java 8+
- Maven 3.6+
- Project Poseidon (Beta 1.7.3 server software)
- A Discord bot token

---

## Building

```bash
cd DiscordRolesPlugin
mvn clean package -DskipTests
```

The shaded (fat) JAR will be at:
```
target/DiscordRolesPlugin-1.0.0.jar
```

Drop this into your server's `plugins/` folder.

---

## Discord Bot Setup

1. Go to https://discord.com/developers/applications
2. Create a new Application → Bot
3. Under **Bot**, copy your **Token**
4. Under **Privileged Gateway Intents**, enable:
   - **Server Members Intent**
   - **Message Content Intent**
5. Invite the bot to your server with these permissions:
   - Read Messages / View Channels
   - Send Messages
   - Read Message History
   - (Optionally) Manage Roles if you want future role-assignment features

Invite URL format:
```
https://discord.com/api/oauth2/authorize?client_id=YOUR_CLIENT_ID&permissions=68608&scope=bot
```

---

## Configuration (`plugins/DiscordRolesPlugin/config.yml`)

```yaml
bot-token: "your-bot-token-here"
guild-id: "your-server-id"           # Right-click your server → Copy ID (Developer Mode on)
link-channel-id: "your-channel-id"   # The channel where !link commands are processed
```

### Role priority

Roles higher in the `role-priority` list take precedence if a player has multiple roles:

```yaml
role-priority:
  - "Owner"
  - "Admin"
  - "Moderator"
  - "Member"
```

### Chat format placeholders

| Placeholder  | Replaced with                        |
|--------------|--------------------------------------|
| `{role}`     | Discord role name (with colour)      |
| `{player}`   | Minecraft username                   |
| `{message}`  | The chat message                     |

---

## In-game Commands

| Command           | Description                          | Permission          |
|-------------------|--------------------------------------|---------------------|
| `/discordlink`    | Shows how to link your account       | All players         |
| `/discordunlink`  | Unlinks your Discord account         | All players         |
| `/discordreload`  | Reloads config and restarts bot      | `discordroles.admin` (OP) |

---

## Discord Commands (in link channel)

| Command                | Description                              |
|------------------------|------------------------------------------|
| `!link <MCUsername>`   | Links your Discord to a Minecraft name   |
| `!resetlink`           | Clears your link so you can re-do it     |

---

## Data Storage

Account links are stored in `plugins/DiscordRolesPlugin/links.yml` (plain YAML).
No native libraries or database engine required — Poseidon's built-in YAML loader handles everything.
Back this file up regularly!

---

## Troubleshooting

**Bot doesn't respond in Discord**
- Check the bot has permission to read and send messages in the link channel
- Ensure Message Content Intent is enabled in the Developer Portal

**Roles not showing in chat**
- Make sure the player has run `!link` in Discord first
- Check `guild-id` and `link-channel-id` in config.yml are correct
- Run `/discordreload` after any config changes

**"Invalid bot token" on startup**
- Regenerate your token in the Developer Portal and update config.yml
- Run `/discordreload`

**Project Poseidon dependency not resolving**
- You may need to manually install Poseidon's API JAR into your local Maven repo:
  ```bash
  mvn install:install-file \
    -Dfile=poseidon-api.jar \
    -DgroupId=net.minecraft.server \
    -DartifactId=poseidon \
    -Dversion=1.7.3-R0.1-SNAPSHOT \
    -Dpackaging=jar
  ```
  Then re-run `mvn clean package`.
