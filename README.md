# VoidPanel

An Essentials-style server mod for Minecraft 26.3 (Fabric). Everything runs on the server, so vanilla clients can join without installing anything. Menus are chest GUIs, feedback goes to clickable chat and the action bar.

## Install

1. Fabric Loader 0.19.5+ and Java 25 on the server.
2. Put the VoidPanel jar and [Fabric API](https://modrinth.com/mod/fabric-api) (0.161.0+26.3 or newer) in `mods/`.
3. Start the server. Settings are written to `config/voidpanel.json`; player data lives in `<world>/voidpanel/data.json`.

Open the main menu with `/voidpanel` (or `/vp`).

## Features

**Travel**
- `/sethome`, `/home`, `/homes` (icon picker, rename, delete), per-player home limits
- `/warps`, `/warp`, `/setwarp`, `/delwarp`
- `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`
- `/rtp` with a crate-style spin, `/spawn`, `/setspawn`, `/back`
- 5 second warmup on teleports, cancelled by moving; blocked for 7 seconds after being hit
- `/pets` to pull tamed pets to you, even from unloaded chunks
- Graves: items go into a locked chest on death (`/graves`), with server-wide and personal toggles

**Chat**
- Configurable chat, join, leave and first-join formats with `{rank}`, `{tag}`, `{display}`, `{name}`, `{message}`
- `/msg`, `/r`, `/socialspy`, with editable prefix and formats
- Emotes (`o/`, `<3`, `:shrug:` and more), `[item]` to show the held item, @mentions
- `/nick`, `/gradient` (build a colour gradient from blocks), chat tags (`/tags`)
- `/ignore`, `/afk` with auto-AFK, staff chat (`#message` or `/sc`)

**Ranks and permissions**
- `/ranks`: prefix, name colour, icon, weight, home limit, per-rank permissions
- Default rank for new players, playtime auto-promotion, timed ranks that revert when they expire
- Separate toggles for showing a rank's prefix in chat and in the tab list
- `/permissions` for per-player rules. Order: player rule, rank, default rank, built-in default

**Moderation**
- `/punish` menu (warn, mute, kick, ban, IP blacklist, with durations) and `/history`
- `/ban`, `/unban`, `/tempban`, `/mute`, `/unmute`, `/kick`, `/warn`, `/blacklist`, `/unblacklist`
- `/whitelist panel`, `/invsee`, `/endersee` (online and offline players), `/vanish`

**Extras**
- `/daily` rewards calendar with streaks, `/profile`, `/players`, `/top` leaderboards
- `/hat`, `/sit`
- Enchanted golden apple recipe: 5 gold blocks, 1 totem of undying, 1 apple, 2 echo shards (shapeless)

## Building

```bash
./gradlew build
```

The jar is written to `build/libs/`.

## License

MIT
