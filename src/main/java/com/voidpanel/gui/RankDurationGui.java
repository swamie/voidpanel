package com.voidpanel.gui;

import com.voidpanel.data.DataStore;
import com.voidpanel.data.DataStore.Rank;
import com.voidpanel.feature.ChatPrompts;
import com.voidpanel.feature.Moderation;
import com.voidpanel.feature.Ranks;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import com.voidpanel.util.Text;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** How long should they have the rank? Presets, a custom length, or permanent. */
public final class RankDurationGui extends Gui {
	private static final Object[][] PRESETS = {
		{"1 hour", "1h", Items.CLOCK},
		{"12 hours", "12h", Items.CLOCK},
		{"1 day", "1d", Items.SUNFLOWER},
		{"3 days", "3d", Items.SUNFLOWER},
		{"7 days", "7d", Items.GOLD_INGOT},
		{"14 days", "14d", Items.GOLD_INGOT},
		{"30 days", "30d", Items.DIAMOND},
		{"90 days", "90d", Items.DIAMOND},
	};

	private final Rank rank;
	private final NameAndId target;
	private final Gui parent;

	public RankDurationGui(ServerPlayer player, Rank rank, NameAndId target, Gui parent) {
		super(player, 4, "&5&l✦ &8How long?");
		this.rank = rank;
		this.target = target;
		this.parent = parent;
	}

	@Override
	protected void build() {
		var server = player.level().getServer();
		for (int i = 0; i < 9; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.purple()));
		ServerPlayer live = server.getPlayerList().getPlayer(target.id());
		ItemBuilder head = live != null ? ItemBuilder.head(live) : ItemBuilder.head(target.id(), target.name());
		String prev = DataStore.find(target.id()).map(d -> d.rankExpires > 0 && d.previousRank != null ? d.previousRank : d.rank).orElse(null);
		Rank fallback = prev != null && DataStore.root().ranks.containsKey(prev) ? DataStore.root().ranks.get(prev) : Ranks.defaultRank();
		set(3, head.name("&f&l" + target.name()).lore("&7Now: " + Ranks.of(target.id()).display + " " + Ranks.expiryNote(target.id())).build());
		set(5, ItemBuilder.of(RanksGui.icon(rank)).name(Text.of(rank.display)).glint(true)
			.lore("&7Timed ranks go back to", "&7" + Text.strip(fallback.display) + " &7when they run out.").build());

		for (int i = 0; i < PRESETS.length; i++) {
			String label = (String) PRESETS[i][0];
			long millis = Moderation.parseDuration((String) PRESETS[i][1]);
			set(9 + i + (i >= 4 ? 1 : 0), ItemBuilder.of((Item) PRESETS[i][2]).count(i + 1).name("&e&l" + label)
				.lore("&e▶ Click to give " + rank.display + " &efor " + label).build(), c -> give(millis));
		}
		set(22, ItemBuilder.of(Items.NETHER_STAR).name("&a&lPermanent").glint(true).lore("&7No expiry.", "", "&e▶ Click to give forever").build(), c -> give(0));
		set(20, ItemBuilder.of(Items.WRITABLE_BOOK).name("&b&lCustom length").lore(
			"&7Type any length, e.g.",
			"&f45m&7, &f6h&7, &f2d&7, &f3w",
			"",
			"&e▶ Click, then type it").build(), c ->
				ChatPrompts.ask(player, "How long? &8(e.g. 45m, 6h, 2d, 3w, or perm)", null, input -> {
					long millis = Moderation.parseDuration(input);
					if (millis < 0) {
						Msg.err(player, "Couldn't read that. Try 30m, 12h, 5d or 2w.");
						open();
						return;
					}
					give(millis);
				}));

		for (int i = 27; i < 36; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		backButton(27, parent);
		closeButton(35);
		fill(Items.STAINED_GLASS_PANE.gray());
	}

	private void give(long millis) {
		var server = player.level().getServer();
		Ranks.set(server, target.id(), target.name(), rank, player.getGameProfile().name(), millis);
		Msg.ok(player, target.name() + " is now " + Text.strip(rank.display) + (millis > 0 ? " for " + Moderation.formatDuration(millis) : " permanently") + ".");
		parent.open();
	}
}
