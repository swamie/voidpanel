package com.voidpanel.perm;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Every permission VoidPanel knows about, with the text shown in the permission editors. */
public enum Perm {
	// ---------------------------------------------------------------- travel
	PANEL(Cat.TRAVEL, "panel", "Void Panel", Items.NETHER_STAR, false, "Open the main menu with /voidpanel."),
	SETHOME(Cat.TRAVEL, "sethome", "Set Home", Items.BED.white(), false, "Save homes with /sethome [name]."),
	HOMES(Cat.TRAVEL, "homes", "Homes Menu", Items.BED.red(), false, "Open the /homes menu, change icons,", "rename and delete homes."),
	HOME(Cat.TRAVEL, "home", "Teleport Home", Items.ENDER_PEARL, false, "Warp home with /home [name]."),
	WARP(Cat.TRAVEL, "warp", "Warps", Items.END_PORTAL_FRAME, false, "Use server warps with /warp and /warps."),
	TPA(Cat.TRAVEL, "tpa", "Send TPA", Items.ENDER_EYE, false, "Ask to teleport to someone with /tpa."),
	TPAHERE(Cat.TRAVEL, "tpahere", "Send TPA Here", Items.CHORUS_FRUIT, false, "Ask someone to teleport to you", "with /tpahere."),
	TPACCEPT(Cat.TRAVEL, "tpaccept", "Accept TPA", Items.DYE.lime(), false, "Accept teleport requests with /tpaccept."),
	TPDENY(Cat.TRAVEL, "tpdeny", "Deny TPA", Items.DYE.red(), false, "Deny teleport requests with /tpdeny."),
	RTP(Cat.TRAVEL, "rtp", "Random Teleport", Items.FILLED_MAP, false, "Spin the /rtp crate to land somewhere random."),
	BACK(Cat.TRAVEL, "back", "Back", Items.RECOVERY_COMPASS, false, "Return to your last location or", "death point with /back."),
	SPAWN(Cat.TRAVEL, "spawn", "Spawn", Items.COMPASS, false, "Teleport to spawn with /spawn."),
	PETS(Cat.TRAVEL, "pets", "Pet Teleport", Items.BONE, false, "Force-teleport your pets to you", "with /pets, even from unloaded chunks."),
	GRAVES(Cat.TRAVEL, "graves", "Graves", Items.CHEST, false, "Your items go into a locked grave", "when you die. See them with /graves."),

	// ---------------------------------------------------------------- social
	MSG(Cat.SOCIAL, "msg", "Private Messages", Items.WRITABLE_BOOK, false, "Send private messages with /msg and /r."),
	IGNORE(Cat.SOCIAL, "ignore", "Ignore", Items.BARRIER, false, "Hide someone's chat, messages and", "requests with /ignore."),
	EMOTES(Cat.SOCIAL, "chat.emotes", "Chat Emotes", Items.GOLD_NUGGET, false, "Use emotes like o/ :shrug: <3 in chat.", "(Hypixel MVP++ style)"),
	CHAT_ITEM(Cat.SOCIAL, "chat.item", "[item] in Chat", Items.ITEM_FRAME, false, "Show off the held item by typing", "[item] in chat."),
	CHAT_COLOR(Cat.SOCIAL, "chat.color", "Coloured Chat", Items.DYE.pink(), true, "Use & colour codes in chat messages."),
	NICK(Cat.SOCIAL, "nick", "Nickname", Items.NAME_TAG, false, "Change your display name with /nick."),
	NICK_COLOR(Cat.SOCIAL, "nick.color", "Coloured Nickname", Items.DYE.magenta(), true, "Use & colour codes in /nick."),
	GRADIENT(Cat.SOCIAL, "gradient", "Gradient Maker", Items.WOOL.lime(), false, "Build colour gradients from blocks", "with /gradient."),
	TAGS(Cat.SOCIAL, "tags", "Chat Tags", Items.PAPER, false, "Pick a chat tag with /tags."),
	PROFILE(Cat.SOCIAL, "profile", "Profiles", Items.PLAYER_HEAD, false, "View player profiles with /profile", "and /players."),
	AFK(Cat.SOCIAL, "afk", "AFK", Items.CLOCK, false, "Mark yourself away with /afk."),

	// ---------------------------------------------------------------- fun
	HAT(Cat.FUN, "hat", "Hat", Items.LEATHER_HELMET, false, "Wear the held item on your head with /hat."),
	SIT(Cat.FUN, "sit", "Sit", Items.OAK_STAIRS, false, "Sit down anywhere with /sit."),
	DAILY(Cat.FUN, "daily", "Daily Rewards", Items.CHEST_MINECART, false, "Claim a daily reward with /daily."),
	TOP(Cat.FUN, "top", "Leaderboards", Items.GOLDEN_HELMET, false, "See the top players with /top."),

	// ---------------------------------------------------------------- moderation
	PUNISH(Cat.MOD, "punish", "Punish", Items.ANVIL, true, "Warn, mute, kick and tempban", "with /punish and friends."),
	BAN(Cat.MOD, "ban", "Ban", Items.IRON_BARS, true, "Ban and unban players with /ban and /unban."),
	BLACKLIST(Cat.MOD, "blacklist", "IP Blacklist", Items.STRUCTURE_VOID, true, "Blacklist IP addresses with /blacklist."),
	WHITELIST(Cat.MOD, "whitelist", "Whitelist Panel", Items.MAP, true, "Manage the whitelist with /whitelist panel", "or /wlpanel."),
	INVSEE(Cat.MOD, "invsee", "Invsee", Items.CHEST, true, "View and edit anyone's inventory,", "even offline, with /invsee."),
	ENDERSEE(Cat.MOD, "endersee", "Endersee", Items.ENDER_CHEST, true, "View and edit anyone's ender chest", "with /endersee."),
	VANISH(Cat.MOD, "vanish", "Vanish", Items.GLASS, true, "Become invisible with /vanish.", "Also lets you see vanished staff."),
	STAFFCHAT(Cat.MOD, "staffchat", "Staff Chat", Items.REDSTONE_TORCH, true, "Talk privately with staff: start a", "message with # or use /sc."),
	SOCIALSPY(Cat.MOD, "socialspy", "Social Spy", Items.SPYGLASS, true, "See everyone's private messages", "with /socialspy."),
	NICK_OTHERS(Cat.MOD, "nick.others", "Nick Others", Items.WRITABLE_BOOK, true, "Change other players' nicknames", "with /setnick."),
	PETS_OTHERS(Cat.MOD, "pets.others", "Other Players' Pets", Items.LEAD, true, "Open other players' pets", "with /pets <player>."),
	GRAVES_OTHERS(Cat.MOD, "graves.others", "Open Any Grave", Items.SKELETON_SKULL, true, "Open graves that belong to others."),
	GRAVES_TOGGLE(Cat.ADMIN, "graves.toggle", "Toggle Graves", Items.LEVER, true, "Turn graves on or off for the", "whole server."),
	AFK_BYPASS(Cat.MOD, "afk.bypass", "AFK Kick Bypass", Items.FEATHER, true, "Never get kicked for being AFK."),

	// ---------------------------------------------------------------- admin
	SETSPAWN(Cat.ADMIN, "setspawn", "Set Spawn", Items.LODESTONE, true, "Move the server spawn with /setspawn."),
	SETWARP(Cat.ADMIN, "setwarp", "Manage Warps", Items.END_CRYSTAL, true, "Create, edit and delete warps."),
	CHAT_ADMIN(Cat.ADMIN, "chatformat", "Chat Formatting", Items.OAK_SIGN, true, "Edit chat, join, leave and", "message formats."),
	TAGS_ADMIN(Cat.ADMIN, "tags.admin", "Manage Tags", Items.WRITABLE_BOOK, true, "Create tags and give them to players."),
	DAILY_EDIT(Cat.ADMIN, "daily.edit", "Edit Daily Rewards", Items.CHEST, true, "Change what /daily gives out."),
	RANKS(Cat.ADMIN, "ranks", "Ranks", Items.DIAMOND, true, "Create and edit ranks, set their", "permissions and assign them."),
	PERMISSIONS(Cat.ADMIN, "permissions", "Permissions", Items.COMMAND_BLOCK, true, "Edit player permissions with /permissions.", "Admins always keep this one.");

	public enum Cat {
		TRAVEL("Travel", Items.COMPASS, "&a"),
		SOCIAL("Social", Items.WRITABLE_BOOK, "&d"),
		FUN("Fun", Items.CAKE, "&e"),
		MOD("Moderation", Items.IRON_SWORD, "&c"),
		ADMIN("Admin", Items.COMMAND_BLOCK, "&4");

		public final String title;
		public final Item icon;
		public final String color;

		Cat(String title, Item icon, String color) {
			this.title = title;
			this.icon = icon;
			this.color = color;
		}

		public List<Perm> perms() {
			return java.util.Arrays.stream(Perm.values()).filter(p -> p.cat == this).toList();
		}
	}

	public final Cat cat;
	public final String node;
	public final String title;
	public final Item icon;
	/** True = only admins (op level 3+) get it by default. */
	public final boolean adminOnly;
	public final List<String> description;

	Perm(Cat cat, String node, String title, Item icon, boolean adminOnly, String... description) {
		this.cat = cat;
		this.node = node;
		this.title = title;
		this.icon = icon;
		this.adminOnly = adminOnly;
		this.description = List.of(description);
	}
}
