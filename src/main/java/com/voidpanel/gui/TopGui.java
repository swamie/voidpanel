package com.voidpanel.gui;

import com.voidpanel.data.DataStore;
import com.voidpanel.data.PlayerData;
import com.voidpanel.feature.Stats;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** /top: leaderboards for playtime, kills and deaths. */
public final class TopGui extends Gui {
	private enum Board {
		PLAYTIME("Playtime", Items.CLOCK, d -> d.playtimeSeconds, v -> Stats.playtime(v)),
		MOBS("Mob kills", Items.IRON_SWORD, d -> d.mobKills, String::valueOf),
		PVP("Player kills", Items.DIAMOND_SWORD, d -> d.playerKills, String::valueOf),
		DEATHS("Deaths", Items.SKELETON_SKULL, d -> d.deaths, String::valueOf),
		STREAK("Daily streak", Items.BLAZE_POWDER, d -> d.dailyStreak, String::valueOf);

		final String title;
		final Item icon;
		final ToLongFunction<PlayerData> value;
		final Function<Long, String> format;

		Board(String title, Item icon, ToLongFunction<PlayerData> value, Function<Long, String> format) {
			this.title = title;
			this.icon = icon;
			this.value = value;
			this.format = format;
		}
	}

	private Board board = Board.PLAYTIME;

	public TopGui(ServerPlayer player) {
		super(player, 6, "&5&l✦ &8Leaderboards");
	}

	@Override
	protected void build() {
		for (int i = 0; i < 9; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		Board[] boards = Board.values();
		for (int i = 0; i < boards.length; i++) {
			Board b = boards[i];
			boolean active = b == board;
			set(2 + i, ItemBuilder.of(b.icon).name((active ? "&a&l" : "&7") + b.title).glint(active).clean().lore(active ? "&8Showing" : "&e▶ Click to show").build(), c -> {
				Msg.click(player);
				board = b;
				refresh();
			});
		}

		List<Map.Entry<String, PlayerData>> ranked = new ArrayList<>(DataStore.root().players.entrySet());
		ranked.removeIf(e -> e.getValue().name == null);
		ranked.sort(Comparator.comparingLong((Map.Entry<String, PlayerData> e) -> board.value.applyAsLong(e.getValue())).reversed());
		int shown = Math.min(36, ranked.size());
		for (int i = 0; i < shown; i++) {
			var e = ranked.get(i);
			UUID id = UUID.fromString(e.getKey());
			PlayerData d = e.getValue();
			String medal = i == 0 ? "&6&l#1 " : i == 1 ? "&7&l#2 " : i == 2 ? "&c&l#3 " : "&8#" + (i + 1) + " ";
			set(9 + i, ItemBuilder.head(id, d.name).name(medal + "&f" + d.name).count(i + 1)
				.lore("&7" + board.title + ": &e" + board.format.apply(board.value.applyAsLong(d)), "", "&e▶ Click for profile").build(),
				c -> new ProfileGui(player, new NameAndId(id, d.name)).open());
		}
		for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		backButton(45, new MainGui(player));
		closeButton(49);
	}
}
