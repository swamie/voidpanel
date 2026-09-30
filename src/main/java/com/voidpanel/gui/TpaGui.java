package com.voidpanel.gui;

import com.voidpanel.feature.Tpa;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** Pick an online player to send a /tpa request to. */
public final class TpaGui extends Gui {
	private int page;

	public TpaGui(ServerPlayer player) {
		super(player, 6, "&5&l✦ &8Teleport to...");
	}

	@Override
	protected void build() {
		List<ServerPlayer> others = player.level().getServer().getPlayerList().getPlayers().stream()
			.filter(p -> p != player && com.voidpanel.feature.Vanish.canSee(player, p)).toList();
		int pages = page(others, 0, 45, page, (slot, target) -> set(slot, ItemBuilder.head(target).name(target.getDisplayName()).lore(
			"&7Real name: &f" + target.getGameProfile().name(),
			"&7World: &f" + com.voidpanel.data.Loc.of(target).dimName(),
			"",
			"&e▶ Left-click &7to teleport to them",
			"&e▶ Right-click &7to bring them to you").build(), c -> {
				close();
				Tpa.request(player, target, c.right());
			}));
		if (others.isEmpty()) set(22, ItemBuilder.of(Items.CLOCK).name("&7Nobody else is online").build());
		for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		pageButtons(45, 49, 53, page, pages, p -> {
			page = p;
			refresh();
		});
		backButton(47, new MainGui(player));
		closeButton(51);
	}
}
