package com.voidpanel.gui;

import com.voidpanel.data.Loc;
import com.voidpanel.feature.Afk;
import com.voidpanel.feature.ChatPrompts;
import com.voidpanel.feature.Moderation;
import com.voidpanel.feature.Ranks;
import com.voidpanel.feature.Vanish;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import com.voidpanel.util.Text;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.Items;

/** /players: everyone online, sorted by rank. Click for their profile (or to punish them). */
public final class PlayersGui extends Gui {
	private final boolean punishMode;
	private int page;

	public PlayersGui(ServerPlayer player, boolean punishMode) {
		super(player, 6, punishMode ? "&5&l✦ &8Punish who?" : "&5&l✦ &8Online Players");
		this.punishMode = punishMode;
	}

	@Override
	protected void build() {
		var server = player.level().getServer();
		List<ServerPlayer> online = server.getPlayerList().getPlayers().stream()
			.filter(p -> Vanish.canSee(player, p))
			.sorted(Comparator.comparingInt((ServerPlayer p) -> -Ranks.of(p.getUUID()).weight).thenComparing(p -> p.getGameProfile().name()))
			.toList();
		int pages = page(online, 0, 45, page, (slot, p) -> set(slot, ItemBuilder.head(p)
			.name(Text.of("").append(Ranks.prefix(p.getUUID())).append(Ranks.coloredName(p)))
			.lore("&7Rank: " + Ranks.of(p.getUUID()).display,
				"&7World: &f" + Loc.of(p).dimName(),
				Afk.isAfk(p) ? "&e● AFK" : "&a● Active",
				"",
				punishMode ? "&c▶ Click to punish" : "&e▶ Click to view profile").build(), c -> {
					NameAndId who = new NameAndId(p.getGameProfile());
					if (punishMode) new PunishGui(player, who).open();
					else new ProfileGui(player, who).open();
				}));
		for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		pageButtons(45, -1, 53, page, pages, p -> {
			page = p;
			refresh();
		});
		set(47, ItemBuilder.of(Items.SPYGLASS).name("&b&lFind a player").lore("&7Look someone up, even offline.", "", "&e▶ Click, then type a name").build(), c ->
			ChatPrompts.ask(player, "Type a player name:", null, name -> Moderation.resolve(server, name, r -> {
				if (r.isEmpty()) {
					Msg.err(player, "No player called &f" + name + "&c.");
					return;
				}
				if (punishMode) new PunishGui(player, r.get()).open();
				else new ProfileGui(player, r.get()).open();
			})));
		set(49, ItemBuilder.of(Items.PLAYER_HEAD).name("&f" + online.size() + " &7online").count(Math.max(1, online.size())).build());
		backButton(51, new MainGui(player));
	}
}
