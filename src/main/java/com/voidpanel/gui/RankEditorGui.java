package com.voidpanel.gui;

import com.voidpanel.data.DataStore;
import com.voidpanel.data.DataStore.Rank;
import com.voidpanel.feature.Chat;
import com.voidpanel.feature.ChatPrompts;
import com.voidpanel.feature.Ranks;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import com.voidpanel.util.Text;
import java.util.function.BiConsumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Style and configure one rank. */
public final class RankEditorGui extends Gui {
	private final Rank rank;

	public RankEditorGui(ServerPlayer player, Rank rank) {
		super(player, 5, "&5&l✦ &8Rank: " + rank.id);
		this.rank = rank;
	}

	@Override
	protected void build() {
		boolean isDefault = rank == Ranks.defaultRank();
		for (int i = 0; i < 9; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		set(4, ItemBuilder.of(RanksGui.icon(rank)).name(Text.of(rank.display)).glint(true).lore(
			"&7In chat: " + rank.prefix + rank.nameColor + player.getGameProfile().name() + "&7: &fHello!",
			"&7Members: &f" + Ranks.members(rank)).build());

		text(10, Items.NAME_TAG, "Display name", rank.display, "Shown in menus and rank-up messages.", (r, v) -> r.display = v);
		text(11, Items.OAK_SIGN, "Prefix", rank.prefix, "Goes before names. Tip: make one in the gradient maker.", (r, v) -> r.prefix = v.isEmpty() ? "" : v + (v.endsWith(" ") ? "" : " "));
		text(12, Items.DYE.cyan(), "Name colour", rank.nameColor, "Colour codes for the name, e.g. &b or &#55ffff", (r, v) -> r.nameColor = v);
		set(13, ItemBuilder.of(RanksGui.icon(rank)).name("&e&lIcon").lore("&e▶ Click to pick a block").build(), c ->
			new IconPickerGui(player, rank.id, RanksGui.icon(rank), this, item -> {
				rank.icon = BuiltInRegistries.ITEM.getKey(item).toString();
				saved();
				new RankEditorGui(player, rank).open();
			}).open());

		number(19, Items.ANVIL, "Weight", rank.weight, "Higher = more important. Sorts the tab list", "and decides who ranks above who.", v -> rank.weight = v, false);
		Integer hl = rank.homeLimit;
		set(20, ItemBuilder.of(Items.BED.red()).name("&e&lHome limit: &f" + (hl != null ? hl : "default")).count(hl != null ? Math.max(1, Math.min(64, hl)) : 1).lore(
			"&eLeft &7+1  &eRight &7-1", "&eShift-click &7use the default").build(), c -> {
				if (c.shift()) rank.homeLimit = null;
				else rank.homeLimit = Math.max(0, Math.min(54, (hl != null ? hl : 3) + (c.right() ? -1 : 1)));
				saved();
				refresh();
			});
		number(21, Items.CLOCK, "Auto-promote hours", rank.autoHours, "Give this rank automatically after", "this much playtime. 0 = off.", v -> rank.autoHours = Math.max(0, v), true);
		set(22, ItemBuilder.of(isDefault ? Items.NETHER_STAR : Items.FIREWORK_STAR).name(isDefault ? "&a&l★ Default rank" : "&7Make default rank")
			.lore(isDefault ? "&7New players get this rank." : "&e▶ Click to give this rank to everyone", isDefault ? "" : "&7who doesn't have one (joined rank).").glint(isDefault).build(), c -> {
				if (isDefault) return;
				DataStore.root().defaultRank = rank.id;
				saved();
				Msg.ok(player, "New players now join as " + Text.strip(rank.display) + ".");
				refresh();
			});

		set(24, ItemBuilder.of(Items.COMMAND_BLOCK).name("&c&lPermissions").lore("&7" + rank.perms.size() + " custom rules", "", "&e▶ Click to set this rank's permissions").build(),
			c -> new RankPermsGui(player, rank).open());
		set(25, ItemBuilder.of(Items.PLAYER_HEAD).name("&b&lGive to a player").lore("&7Click a player's head, then", "&7choose how long they keep it.", "", "&e▶ Click").build(),
			c -> new RankGiveGui(player, rank, this).open());

		toggle(15, "Prefix in chat", rank.showInChat, "Show " + rank.prefix + "&7before names in chat.", () -> rank.showInChat = !rank.showInChat);
		toggle(16, "Prefix in tab list", rank.showInTab, "Show " + rank.prefix + "&7before names in the tab list.", () -> rank.showInTab = !rank.showInTab);

		for (int i = 36; i < 45; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		backButton(36, new RanksGui(player));
		if (!isDefault) {
			set(44, ItemBuilder.of(Items.LAVA_BUCKET).name("&c&lDelete rank").lore("&7Members fall back to the default rank.").build(), c ->
				new ConfirmGui(player, "&4Delete rank " + rank.id + "?", ItemBuilder.of(RanksGui.icon(rank)).name(Text.of(rank.display)).build(), () -> {
					DataStore.root().ranks.remove(rank.id);
					for (var d : DataStore.root().players.values()) if (rank.id.equals(d.rank)) d.rank = null;
					saved();
					Msg.ok(player, "Deleted rank " + rank.id + ".");
					new RanksGui(player).open();
				}, () -> new RankEditorGui(player, rank).open()).open());
		}
		closeButton(40);
		fill(Items.STAINED_GLASS_PANE.gray());
	}

	private void text(int slot, Item icon, String label, String current, String help, BiConsumer<Rank, String> setter) {
		set(slot, ItemBuilder.of(icon).name("&e&l" + label)
			.lore(Text.of("&7Now: ").append(current.isEmpty() ? Text.of("&8none") : Text.of(current)))
			.lore(net.minecraft.network.chat.Component.literal("Code: " + (current.isEmpty() ? "-" : current)).withStyle(s -> s.withColor(0x555555)))
			.lore("")
			.lore(net.minecraft.network.chat.Component.literal(help).withStyle(s -> s.withColor(0xAAAAAA)))
			.lore("", "&e▶ Click to change").build(), c ->
			ChatPrompts.ask(player, "Type the new " + label.toLowerCase() + " &8(& colours work, 'none' to clear)&7:", current, input -> {
				setter.accept(rank, input.equalsIgnoreCase("none") ? "" : input);
				saved();
				new RankEditorGui(player, rank).open();
			}));
	}

	private void toggle(int slot, String label, boolean on, String help, Runnable flip) {
		set(slot, ItemBuilder.of(on ? Items.CONCRETE.lime() : Items.CONCRETE.red()).name((on ? "&a&l" : "&c&l") + label + ": " + (on ? "YES" : "NO"))
			.lore("&7" + help, "", "&e▶ Click to turn " + (on ? "off" : "on")).build(), c -> {
				flip.run();
				saved();
				Msg.click(player);
				refresh();
			});
	}

	private void number(int slot, Item icon, String label, int value, String help1, String help2, java.util.function.IntConsumer setter, boolean hours) {
		set(slot, ItemBuilder.of(icon).name("&e&l" + label + ": &f" + value + (hours && value > 0 ? "h" : "")).count(Math.max(1, Math.min(64, value)))
			.lore("&7" + help1, "&7" + help2, "", "&eLeft &7+1  &eRight &7-1", "&eShift &7±10").build(), c -> {
				int step = c.shift() ? 10 : 1;
				setter.accept(value + (c.right() ? -step : step));
				saved();
				refresh();
			});
	}

	private void saved() {
		DataStore.markDirty();
		var server = player.level().getServer();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (Ranks.of(p.getUUID()) == rank) {
				Chat.refreshTabName(p, server);
				server.getCommands().sendCommands(p);
			}
		}
	}
}
