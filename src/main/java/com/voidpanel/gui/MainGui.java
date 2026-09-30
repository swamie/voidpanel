package com.voidpanel.gui;

import com.voidpanel.config.Config;
import com.voidpanel.data.DataStore;
import com.voidpanel.data.Loc;
import com.voidpanel.feature.Afk;
import com.voidpanel.feature.Chat;
import com.voidpanel.feature.ChatPrompts;
import com.voidpanel.feature.Combat;
import com.voidpanel.feature.Daily;
import com.voidpanel.feature.Emotes;
import com.voidpanel.feature.Hat;
import com.voidpanel.feature.Ranks;
import com.voidpanel.feature.Sit;
import com.voidpanel.feature.Spawn;
import com.voidpanel.feature.StaffChat;
import com.voidpanel.feature.Stats;
import com.voidpanel.feature.Tpa;
import com.voidpanel.feature.Vanish;
import com.voidpanel.perm.Perm;
import com.voidpanel.perm.Perms;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import com.voidpanel.util.Text;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** /voidpanel: one menu for everything the player is allowed to use. */
public final class MainGui extends Gui {
	public MainGui(ServerPlayer player) {
		super(player, 6, "&5&l✦ &8Void Panel");
	}

	@Override
	protected void build() {
		var data = DataStore.get(player.getUUID());
		for (int i = 0; i < 9; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.purple()));
		set(4, ItemBuilder.head(player).name(Text.of("").append(Ranks.prefix(player.getUUID())).append(Ranks.coloredName(player))).lore(
			"&7Rank: " + Ranks.of(player.getUUID()).display,
			"&7Playtime: &f" + Stats.playtime(data.playtimeSeconds),
			"&7Homes: &f" + data.homes.size() + "&7/&f" + Perms.homeLimit(player.getUUID()),
			"&7Combat: " + (Combat.inCombat(player) ? "&c⚔ tagged (" + Msg.seconds(Combat.remaining(player)) + ")" : "&a✔ safe"),
			"",
			"&e▶ Click for your profile").build(), c -> new ProfileGui(player, new NameAndId(player.getGameProfile())).open());

		// Row 1: travel
		tool(10, Perm.HOMES, Items.BED.red(), "&a&lHomes", List.of("&7Warp to and organise your homes.", "", "&8/homes"), () -> new HomesGui(player).open());
		tool(11, Perm.WARP, Items.END_PORTAL_FRAME, "&d&lWarps", List.of("&7Server warps set by the admins.", "", "&8/warps"), () -> new WarpsGui(player).open());
		tool(12, Perm.SPAWN, Items.COMPASS, "&b&lSpawn", List.of("&7Warp to the server spawn.", "", "&8/spawn"), () -> {
			close();
			Spawn.go(player);
		});
		tool(13, Perm.BACK, Items.RECOVERY_COMPASS, "&e&lBack", backLore(data.back), () -> {
			close();
			Spawn.back(player);
		});
		tool(14, Perm.RTP, Items.FILLED_MAP, "&d&lRandom Teleport", List.of("&7Spin the crate and land", "&7somewhere new.", "", "&8/rtp"), () -> RtpGui.openFor(player));
		tool(15, Perm.TPA, Items.ENDER_EYE, "&5&lTeleport Request", List.of("&7Pick a player to teleport to,", "&7or bring to you.", "", "&8/tpa  /tpahere"), () -> new TpaGui(player).open());
		tool(16, Perm.PETS, Items.BONE, "&6&lPets", List.of("&7Force your pets to teleport", "&7to you, even from far away.", "", "&8/pets"),
			() -> new PetsGui(player, player.getUUID(), player.getGameProfile().name()).open());

		// Row 2: social
		tool(19, Perm.MSG, Items.WRITABLE_BOOK, "&d&lMessage", List.of("&7Send a private message.", "", "&8/msg <player> <message>"), () -> {
			close();
			player.sendSystemMessage(Msg.prefixed(Msg.suggest("&e[Click to start a /msg]", "/msg ", "&7Puts /msg in your chat box")));
		});
		tool(20, Perm.PROFILE, Items.PLAYER_HEAD, "&b&lPlayers", List.of("&7Everyone online. Click one", "&7for their profile.", "", "&8/players"), () -> new PlayersGui(player, false).open());
		if (Perms.has(player, Perm.EMOTES) && Config.get().emotesEnabled) {
			boolean off = data.emotesOff;
			set(21, ItemBuilder.of(off ? Items.COAL : Items.GOLD_NUGGET).name(off ? "&7&lEmotes: OFF" : "&e&lEmotes: ON").lore(
				"&7Type &fo/ &7or &f:shrug: &7in chat.", "", "&eLeft &7toggle  &eRight &7list", "", "&8/emotes").build(), c -> {
					if (c.right()) {
						close();
						Emotes.list(player);
					} else {
						Emotes.toggle(player);
						refresh();
					}
				});
		}
		tool(22, Perm.TAGS, Items.PAPER, "&f&lChat Tags", List.of("&7Pick a tag to show in chat.", "", "&8/tags"), () -> new TagsGui(player).open());
		tool(23, Perm.NICK, Items.NAME_TAG, "&f&lNickname", List.of("&7Change the name shown in", "&7chat and the tab list.", "", "&eLeft &7set  &eRight &7remove", "", "&8/nick <name|off>"), null);
		if (Perms.has(player, Perm.NICK)) {
			set(23, get(23), c -> {
				if (c.right()) {
					Chat.setNick(player, player, null);
					refresh();
				} else {
					ChatPrompts.ask(player, "Type your new nickname" + (Perms.has(player, Perm.NICK_COLOR) ? " &8(& colours allowed)" : "") + "&7:",
						data.nick, input -> Chat.setNick(player, player, input));
				}
			});
		}
		tool(24, Perm.GRADIENT, Items.WOOL.magenta(), "&#ff5f6d&lG&#ff7a5c&lr&#ff955b&la&#ffb05a&ld&#ffcb59&li&#e5d65e&le&#b8d86a&ln&#8bdb76&lt &f&lMaker",
			List.of("&7Make a colour gradient from", "&7blocks for your nickname.", "", "&8/gradient"), () -> new GradientGui(player).open());
		tool(25, Perm.IGNORE, Items.BARRIER, "&c&lIgnore", List.of("&7Hide someone's chat,", "&7messages and requests.", "", "&8/ignore"), () -> new IgnoreGui(player).open());

		// Row 3: fun & misc
		tool(28, Perm.DAILY, Daily.canClaim(player) ? Items.CHEST_MINECART : Items.MINECART, Daily.canClaim(player) ? "&e&lDaily Reward &a(ready!)" : "&e&lDaily Reward",
			List.of("&7Claim a reward every day", "&7and build a streak.", "", "&8/daily"), () -> new DailyGui(player).open());
		tool(29, Perm.TOP, Items.GOLDEN_HELMET, "&6&lLeaderboards", List.of("&7Top players by playtime,", "&7kills and more.", "", "&8/top"), () -> new TopGui(player).open());
		tool(30, Perm.HAT, Items.LEATHER_HELMET, "&6&lHat", List.of("&7Wear the item in your hand.", "", "&8/hat"), () -> {
			close();
			Hat.wear(player);
		});
		tool(31, Perm.SIT, Items.OAK_STAIRS, "&6&lSit", List.of("&7Sit down right where you are.", "", "&8/sit"), () -> {
			close();
			Sit.toggle(player);
		});
		tool(32, Perm.AFK, Items.CLOCK, Afk.isAfk(player) ? "&e&lAFK: ON" : "&7&lAFK", List.of("&7Let people know you're away.", "", "&8/afk"), () -> {
			close();
			Afk.toggle(player);
		});
		tool(33, Perm.GRAVES, Items.CHEST, "&6&lGraves", List.of("&7Where your items went", "&7when you died.", "", "&8/graves"), () -> new GravesGui(player).open());
		List<String> pending = Tpa.pendingNames(player);
		if (!pending.isEmpty() && Perms.has(player, Perm.TPACCEPT)) {
			String latest = pending.get(pending.size() - 1);
			set(34, ItemBuilder.of(Items.WRITABLE_BOOK).name("&d&l✉ Teleport requests").count(pending.size()).glint(true).lore(
				"&7From: &f" + String.join("&7, &f", pending), "", "&aLeft-click &7to accept &f" + latest, "&cRight-click &7to deny &f" + latest).build(), c -> {
					close();
					if (c.right()) Tpa.deny(player, latest);
					else Tpa.accept(player, latest);
				});
		}

		// Row 4: staff
		for (int i = 36; i < 45; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.gray()));
		tool(36, Perm.PUNISH, Items.ANVIL, "&c&lPunish", List.of("&7Warn, mute, kick or ban.", "", "&8/punish <player>"), () -> new PlayersGui(player, true).open());
		tool(37, Perm.BAN, Items.IRON_BARS, "&4&lBans", List.of("&7Review and lift bans.", "", "&8/ban  /blacklist"), () -> new BansGui(player, false).open());
		tool(38, Perm.WHITELIST, Items.MAP, "&f&lWhitelist", List.of("&7Add, remove and review", "&7whitelisted players.", "", "&8/whitelist panel"), () -> new WhitelistGui(player).open());
		if (Perms.has(player, Perm.VANISH)) {
			boolean v = Vanish.isVanished(player);
			set(39, ItemBuilder.of(v ? Items.GLASS : Items.TINTED_GLASS).name(v ? "&b&lVanish: ON" : "&7&lVanish: OFF").glint(v)
				.lore("&7Hide from players, the tab", "&7list and join messages.", "", "&8/vanish").build(), c -> {
					Vanish.toggle(player);
					refresh();
				});
		}
		if (Perms.has(player, Perm.STAFFCHAT)) {
			boolean sc = data.staffChat;
			set(40, ItemBuilder.of(Items.REDSTONE_TORCH).name(sc ? "&c&lStaff chat: ON" : "&7&lStaff chat: OFF").glint(sc)
				.lore("&7When on, everything you type", "&7goes to staff only.", "&7Or start a message with &f#&7.", "", "&8/sc").build(), c -> {
					StaffChat.toggle(player);
					refresh();
				});
		}
		tool(41, Perm.RANKS, Items.DIAMOND, "&b&lRanks", List.of("&7Create ranks, style them and", "&7set their permissions.", "", "&8/ranks"), () -> new RanksGui(player).open());
		tool(42, Perm.PERMISSIONS, Items.COMMAND_BLOCK, "&c&lPermissions", List.of("&7Per-player permission rules.", "", "&8/permissions"), () -> new PermissionsGui(player).open());
		tool(43, Perm.CHAT_ADMIN, Items.OAK_SIGN, "&e&lChat Formatting", List.of("&7Chat, join, leave, message", "&7and emote settings."), () -> new ChatFormatGui(player).open());
		tool(44, Perm.SETSPAWN, Items.LODESTONE, "&b&lSet Spawn Here", List.of("&7Move the server spawn to", "&7where you're standing.", "", "&8/setspawn"), () -> {
			close();
			Spawn.set(player);
		});

		// Row 5
		for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		if (Perms.has(player, Perm.CHAT_ADMIN) || Perms.has(player, Perm.PERMISSIONS)) {
			set(45, ItemBuilder.of(Items.REPEATER).name("&7&lReload Config").lore("&7Reload config/voidpanel.json", "&7after editing it by hand.").build(), c -> {
				Config.load();
				Msg.ok(player, "Config reloaded.");
			});
		}
		closeButton(49);
		fill(Items.STAINED_GLASS_PANE.gray());
	}

	private void tool(int slot, Perm perm, Item icon, String name, List<String> lore, Runnable action) {
		if (!Perms.has(player, perm)) return;
		ItemStack stack = ItemBuilder.of(icon).name(name).clean().lore(lore).build();
		if (action == null) {
			set(slot, stack);
			return;
		}
		set(slot, stack, c -> {
			Msg.click(player);
			action.run();
		});
	}

	private static List<String> backLore(Loc back) {
		if (back == null) return List.of("&7Return to where you last", "&7teleported from or died.", "", "&8Nowhere to go back to yet.");
		return List.of("&7Return to where you last", "&7teleported from or died.", "", "&7Last: " + back.dimColor() + back.dimName() + " &f" + back.coords(), "", "&8/back");
	}
}
