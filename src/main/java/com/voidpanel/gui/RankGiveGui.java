package com.voidpanel.gui;

import com.voidpanel.data.DataStore;
import com.voidpanel.data.DataStore.Rank;
import com.voidpanel.feature.ChatPrompts;
import com.voidpanel.feature.Moderation;
import com.voidpanel.feature.Ranks;
import com.voidpanel.feature.Vanish;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import com.voidpanel.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.Items;

/** Click a player's head to give them a rank (then pick how long). */
public final class RankGiveGui extends Gui {
	private final Rank rank;
	private final Gui parent;
	private boolean showOffline;
	private int page;

	public RankGiveGui(ServerPlayer player, Rank rank, Gui parent) {
		super(player, 6, "&5&l✦ &8Give " + Text.strip(rank.display) + " to...");
		this.rank = rank;
		this.parent = parent;
	}

	@Override
	protected void build() {
		var server = player.level().getServer();
		List<NameAndId> people = new ArrayList<>();
		List<UUID> online = new ArrayList<>();
		server.getPlayerList().getPlayers().stream()
			.filter(p -> Vanish.canSee(player, p))
			.sorted(Comparator.comparing(p -> p.getGameProfile().name()))
			.forEach(p -> {
				people.add(new NameAndId(p.getGameProfile()));
				online.add(p.getUUID());
			});
		if (showOffline) {
			for (var e : DataStore.root().players.entrySet()) {
				UUID id = UUID.fromString(e.getKey());
				if (!online.contains(id) && e.getValue().name != null) people.add(new NameAndId(id, e.getValue().name));
			}
		}

		int pages = page(people, 0, 45, page, (slot, who) -> {
			boolean isOnline = online.contains(who.id());
			Rank current = Ranks.of(who.id());
			ServerPlayer live = server.getPlayerList().getPlayer(who.id());
			ItemBuilder head = live != null ? ItemBuilder.head(live) : ItemBuilder.head(who.id(), who.name());
			String expiry = Ranks.expiryNote(who.id());
			set(slot, head.name((isOnline ? "&a" : "&7") + who.name()).lore(
				isOnline ? "&a● Online" : "&8● Offline",
				"&7Current rank: " + current.display + (expiry.isEmpty() ? "" : " " + expiry),
				"",
				current == rank ? "&eAlready this rank &7(click to change length)" : "&e▶ Click to give " + rank.display).build(),
				c -> new RankDurationGui(player, rank, who, this).open());
		});
		if (people.isEmpty()) set(22, ItemBuilder.of(Items.CLOCK).name("&7Nobody to show").build());

		for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		pageButtons(45, -1, 53, page, pages, p -> {
			page = p;
			refresh();
		});
		backButton(46, parent);
		set(48, ItemBuilder.of(showOffline ? Items.ENDER_EYE : Items.ENDER_PEARL).name(showOffline ? "&b&lShowing: everyone" : "&b&lShowing: online only")
			.lore("&e▶ Click to " + (showOffline ? "only show online players" : "include offline players")).build(), c -> {
				showOffline = !showOffline;
				page = 0;
				Msg.click(player);
				refresh();
			});
		set(50, ItemBuilder.of(Items.SPYGLASS).name("&b&lFind by name").lore("&7For someone who's never been", "&7seen by VoidPanel.", "", "&e▶ Click, then type a name").build(), c ->
			ChatPrompts.ask(player, "Type the player's name:", null, name -> Moderation.resolve(server, name, r -> {
				if (r.isEmpty()) {
					Msg.err(player, "No player called &f" + name + "&c.");
					open();
				} else {
					new RankDurationGui(player, rank, r.get(), this).open();
				}
			})));
		closeButton(52);
	}
}
