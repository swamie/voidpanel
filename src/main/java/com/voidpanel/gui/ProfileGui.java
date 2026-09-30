package com.voidpanel.gui;

import com.voidpanel.data.DataStore;
import com.voidpanel.feature.Afk;
import com.voidpanel.feature.Ignore;
import com.voidpanel.feature.Invsee;
import com.voidpanel.feature.Ranks;
import com.voidpanel.feature.Stats;
import com.voidpanel.feature.Tpa;
import com.voidpanel.feature.Vanish;
import com.voidpanel.perm.Perm;
import com.voidpanel.perm.Perms;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import com.voidpanel.util.Text;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.Items;

/** A player's profile: stats up top, actions below, staff tools at the bottom. */
public final class ProfileGui extends Gui {
	private static final SimpleDateFormat DATE = new SimpleDateFormat("d MMM yyyy", Locale.ROOT);
	private final NameAndId target;

	public ProfileGui(ServerPlayer viewer, NameAndId target) {
		super(viewer, 6, "&5&l✦ &8Profile: " + target.name());
		this.target = target;
	}

	@Override
	protected void build() {
		var server = player.level().getServer();
		ServerPlayer online = server.getPlayerList().getPlayer(target.id());
		if (online != null && !Vanish.canSee(player, online)) online = null;
		var d = DataStore.get(target.id());
		var rank = Ranks.of(target.id());
		boolean self = target.id().equals(player.getUUID());

		for (int i = 0; i < 9; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.purple()));
		ItemBuilder head = online != null ? ItemBuilder.head(online) : ItemBuilder.head(target.id(), target.name());
		set(4, head.name(online != null ? Text.of("").append(Ranks.prefix(target.id())).append(Ranks.coloredName(online)) : Text.of(rank.prefix + rank.nameColor + target.name()))
			.lore("&7Rank: " + rank.display + " " + Ranks.expiryNote(target.id()),
				"&7Nickname: " + (d.nick != null ? "&f" + d.nick : "&8none"),
				online != null ? (Afk.isAfk(online) ? "&e● AFK" : "&a● Online") : "&8● Offline &7(seen " + WhitelistGui.ago(d.lastSeen) + ")").build());

		set(19, ItemBuilder.of(Items.CLOCK).name("&e&lPlaytime").lore("&f" + Stats.playtime(d.playtimeSeconds)).build());
		set(20, ItemBuilder.of(Items.CAKE).name("&d&lJoined").lore("&f" + (d.firstJoin > 0 ? DATE.format(new Date(d.firstJoin)) : "unknown")).build());
		set(21, ItemBuilder.of(Items.IRON_SWORD).name("&a&lMob kills").lore("&f" + d.mobKills).clean().build());
		set(22, ItemBuilder.of(Items.DIAMOND_SWORD).name("&c&lPlayer kills").lore("&f" + d.playerKills).clean().build());
		set(23, ItemBuilder.of(Items.SKELETON_SKULL).name("&7&lDeaths").lore("&f" + d.deaths).build());
		set(24, ItemBuilder.of(Items.BED.red()).name("&b&lHomes").lore("&f" + d.homes.size() + "&7/&f" + Perms.homeLimit(target.id())).build());
		set(25, ItemBuilder.of(Items.BLAZE_POWDER).name("&6&lDaily streak").lore("&f" + d.dailyStreak).build());

		ServerPlayer live = online;
		if (!self && live != null) {
			if (Perms.has(player, Perm.MSG)) set(29, ItemBuilder.of(Items.WRITABLE_BOOK).name("&d&lMessage").lore("&e▶ Click to start a /msg").build(), c -> {
				close();
				player.sendSystemMessage(Msg.prefixed(Msg.suggest("&e[Click to message " + target.name() + "]", "/msg " + target.name() + " ", "&7Fills in /msg")));
			});
			if (Perms.has(player, Perm.TPA)) set(30, ItemBuilder.of(Items.ENDER_PEARL).name("&5&lTeleport to them").lore("&e▶ Send a /tpa request").build(), c -> {
				close();
				Tpa.request(player, live, false);
			});
			if (Perms.has(player, Perm.TPAHERE)) set(31, ItemBuilder.of(Items.CHORUS_FRUIT).name("&5&lBring them here").lore("&e▶ Send a /tpahere request").build(), c -> {
				close();
				Tpa.request(player, live, true);
			});
		}
		if (!self && Perms.has(player, Perm.IGNORE)) {
			boolean ignoring = Ignore.isIgnoring(player.getUUID(), target.id());
			set(33, ItemBuilder.of(ignoring ? Items.DYE.lime() : Items.BARRIER).name(ignoring ? "&a&lStop ignoring" : "&c&lIgnore").build(), c -> {
				Ignore.toggle(player, target);
				refresh();
			});
		}

		// Staff row
		for (int i = 36; i < 45; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.gray()));
		if (Perms.has(player, Perm.PUNISH)) {
			set(37, ItemBuilder.of(Items.ANVIL).name("&c&lPunish").lore("&7Warn, mute, kick or ban.").build(), c -> new PunishGui(player, target).open());
			set(38, ItemBuilder.of(Items.BOOK).name("&6&lHistory").lore("&f" + d.punishments.size() + " &7records").build(), c -> new HistoryGui(player, target).open());
		}
		if (Perms.has(player, Perm.INVSEE)) set(39, ItemBuilder.of(Items.CHEST).name("&e&lInventory").lore("&7View and edit.").build(), c -> {
			close();
			com.voidpanel.util.Scheduler.later(2, () -> Invsee.openInventory(player, target));
		});
		if (Perms.has(player, Perm.ENDERSEE)) set(40, ItemBuilder.of(Items.ENDER_CHEST).name("&5&lEnder chest").lore("&7View and edit.").build(), c -> {
			close();
			com.voidpanel.util.Scheduler.later(2, () -> Invsee.openEnder(player, target));
		});
		if (Perms.has(player, Perm.PETS_OTHERS)) set(41, ItemBuilder.of(Items.BONE).name("&6&lPets").build(), c -> new PetsGui(player, target.id(), target.name()).open());
		if (Perms.has(player, Perm.RANKS)) set(42, ItemBuilder.of(Items.DIAMOND).name("&b&lSet rank").lore("&7Current: " + rank.display).build(), c -> new RankPickerGui(player, target, this).open());
		if (Perms.has(player, Perm.PERMISSIONS)) set(43, ItemBuilder.of(Items.COMMAND_BLOCK).name("&c&lPermissions").build(), c -> new PermissionEditorGui(player, target).open());

		for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		backButton(45, new PlayersGui(player, false));
		closeButton(49);
		fill(Items.STAINED_GLASS_PANE.gray());
	}
}
