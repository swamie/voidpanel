package com.voidpanel.config;

import com.voidpanel.perm.Perm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Every VoidPanel feature that can be switched off. Stored in config/voidpanel.json under "features". */
public enum Feature {
	PANEL("panel", "Main Menu", Items.NETHER_STAR, "The /voidpanel menu."),
	HOMES("homes", "Homes", Items.RED_BED, "/sethome, /home, /homes, /delhome."),
	WARPS("warps", "Warps", Items.END_PORTAL_FRAME, "/warp, /warps, /setwarp, /delwarp."),
	TPA("tpa", "Teleport Requests", Items.ENDER_EYE, "/tpa, /tpahere, /tpaccept, /tpdeny."),
	RTP("rtp", "Random Teleport", Items.FILLED_MAP, "/rtp and its crate spin."),
	SPAWN("spawn", "Spawn and Back", Items.COMPASS, "/spawn, /setspawn, /back, first-join spawn."),
	PETS("pets", "Pet Teleport", Items.BONE, "/pets and tracking of tamed pets."),
	GRAVES("graves", "Graves", Items.CHEST, "Locked grave chests on death. Same as gravesEnabled."),
	MESSAGES("messages", "Private Messages", Items.WRITABLE_BOOK, "/msg, /r and /socialspy."),
	IGNORE("ignore", "Ignore", Items.BARRIER, "/ignore."),
	EMOTES("emotes", "Emotes", Items.GOLD_NUGGET, "Chat emotes. Same as emotesEnabled."),
	CHAT_ITEM("chatItem", "[item] in Chat", Items.ITEM_FRAME, "Show the held item with [item]."),
	CHAT_COLOR("chatColor", "Coloured Chat", Items.PINK_DYE, "& colour codes in chat."),
	NICKNAMES("nicknames", "Nicknames", Items.NAME_TAG, "/nick, /setnick and nickname display."),
	GRADIENT("gradient", "Gradient Maker", Items.LIME_WOOL, "/gradient."),
	TAGS("tags", "Chat Tags", Items.PAPER, "/tags and tag management."),
	PROFILES("profiles", "Profiles", Items.PLAYER_HEAD, "/profile and /players."),
	AFK("afk", "AFK", Items.CLOCK, "/afk, auto-AFK and AFK kick."),
	HAT("hat", "Hat", Items.LEATHER_HELMET, "/hat."),
	SIT("sit", "Sit", Items.OAK_STAIRS, "/sit."),
	DAILY("daily", "Daily Rewards", Items.CHEST_MINECART, "/daily and the join reminder."),
	TOP("top", "Leaderboards", Items.GOLDEN_HELMET, "/top."),
	PUNISH("punish", "Punishments", Items.ANVIL, "/punish, /mute, /warn, /kick, /tempban, /history. Mutes stop applying."),
	BANS("bans", "Bans and IP Blacklist", Items.IRON_BARS, "/ban, /unban, /blacklist, /unblacklist."),
	WHITELIST_PANEL("whitelistPanel", "Whitelist Panel", Items.MAP, "/wlpanel and denied-login tracking."),
	INVSEE("invsee", "Invsee and Endersee", Items.ENDER_CHEST, "/invsee and /endersee."),
	VANISH("vanish", "Vanish", Items.GLASS, "/vanish."),
	STAFF_CHAT("staffChat", "Staff Chat", Items.REDSTONE_TORCH, "/sc and #message."),
	RANKS("ranks", "Ranks", Items.DIAMOND, "/ranks, rank prefixes, expiry and auto-promotion."),
	PERMISSIONS_EDITOR("permissionsEditor", "Permissions Editor", Items.COMMAND_BLOCK, "/permissions. Rules already set keep working."),
	CHAT_FORMAT("chatFormat", "Chat, Join and Leave Formats", Items.OAK_SIGN, "VoidPanel chat layout and join/leave lines. Off = vanilla chat."),
	TAB_LIST("tabList", "Tab List Names", Items.LECTERN, "Rank, nickname and AFK in the tab list."),
	COMBAT_TAG("combatTag", "Combat Tag", Items.IRON_SWORD, "Blocks teleports shortly after being hit."),
	TELEPORT_WARMUP("teleportWarmup", "Teleport Warmup", Items.ENDER_PEARL, "Warmup before teleports. Off = instant."),
	ANTI_DUPE("antiDupe", "Anti-Dupe Sweep", Items.TNT, "Removes leaked GUI items from inventories."),
	STATS("stats", "Stats Tracking", Items.BOOK, "Playtime, kills and deaths."),
	GOLDEN_APPLE_RECIPE("goldenAppleRecipe", "Enchanted Golden Apple Recipe", Items.ENCHANTED_GOLDEN_APPLE, "The extra crafting recipe. Needs a restart or /reload.");

	public final String key;
	public final String title;
	public final Item icon;
	public final String description;

	Feature(String key, String title, Item icon, String description) {
		this.key = key;
		this.title = title;
		this.icon = icon;
		this.description = description;
	}

	public boolean enabled() {
		Config c = Config.get();
		if (this == GRAVES) return c.gravesEnabled;
		if (this == EMOTES) return c.emotesEnabled;
		return c.features.getOrDefault(key, true);
	}

	public void set(boolean on) {
		Config c = Config.get();
		if (this == GRAVES) c.gravesEnabled = on;
		else if (this == EMOTES) c.emotesEnabled = on;
		else c.features.put(key, on);
	}

	/** The feature a permission belongs to, or null if it is always available. */
	public static Feature of(Perm perm) {
		return switch (perm) {
			case PANEL -> PANEL;
			case SETHOME, HOMES, HOME -> HOMES;
			case WARP, SETWARP -> WARPS;
			case TPA, TPAHERE, TPACCEPT, TPDENY -> TPA;
			case RTP -> RTP;
			case BACK, SPAWN, SETSPAWN -> SPAWN;
			case PETS, PETS_OTHERS -> PETS;
			case GRAVES, GRAVES_OTHERS -> GRAVES;
			case MSG, SOCIALSPY -> MESSAGES;
			case IGNORE -> IGNORE;
			case EMOTES -> EMOTES;
			case CHAT_ITEM -> CHAT_ITEM;
			case CHAT_COLOR -> CHAT_COLOR;
			case NICK, NICK_COLOR, NICK_OTHERS -> NICKNAMES;
			case GRADIENT -> GRADIENT;
			case TAGS, TAGS_ADMIN -> TAGS;
			case PROFILE -> PROFILES;
			case AFK, AFK_BYPASS -> AFK;
			case HAT -> HAT;
			case SIT -> SIT;
			case DAILY, DAILY_EDIT -> DAILY;
			case TOP -> TOP;
			case PUNISH -> PUNISH;
			case BAN, BLACKLIST -> BANS;
			case WHITELIST -> WHITELIST_PANEL;
			case INVSEE, ENDERSEE -> INVSEE;
			case VANISH -> VANISH;
			case STAFFCHAT -> STAFF_CHAT;
			case RANKS -> RANKS;
			case PERMISSIONS -> PERMISSIONS_EDITOR;
			case CHAT_ADMIN -> CHAT_FORMAT;
			case GRAVES_TOGGLE -> null;
		};
	}
}
