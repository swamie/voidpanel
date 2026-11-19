package com.voidpanel.gui;

import com.voidpanel.config.Config;
import com.voidpanel.config.Feature;
import com.voidpanel.util.DyeItems;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** Admin menu: switch any VoidPanel feature on or off. Saved to config/voidpanel.json. */
public final class FeaturesGui extends Gui {
	private static final int PER_PAGE = 36;
	private final Gui parent;
	private int page;

	public FeaturesGui(ServerPlayer player, Gui parent) {
		super(player, 6, "&5&l✦ &8Feature Toggles");
		this.parent = parent;
	}

	@Override
	protected void build() {
		for (int i = 0; i < 9; i++) set(i, ItemBuilder.filler(DyeItems.STAINED_GLASS_PANE.purple()));
		set(4, ItemBuilder.of(Items.COMPARATOR).name("&d&lFeature Toggles").lore(
			"&7Click a feature to switch it on or off.",
			"&7Off means its commands, menus and",
			"&7background behaviour all stop.",
			"",
			"&8Same switches as \"features\" in",
			"&8config/voidpanel.json.").build());

		List<Feature> all = List.of(Feature.values());
		int pages = page(all, 9, PER_PAGE, page, (slot, f) -> {
			boolean on = f.enabled();
			List<String> lore = new ArrayList<>();
			lore.add("&7" + f.description);
			lore.add("");
			lore.add(on ? "&aEnabled" : "&cDisabled");
			lore.add("&e▶ Click to turn " + (on ? "off" : "on"));
			set(slot, ItemBuilder.of(on ? f.icon : Items.GRAY_DYE).name((on ? "&a&l" : "&7&l") + f.title).glint(on).clean().lore(lore).build(), c -> {
				f.set(!on);
				Config.save();
				Msg.click(player);
				player.level().getServer().getPlayerList().getPlayers().forEach(p -> player.level().getServer().getCommands().sendCommands(p));
				refresh();
			});
		});
		for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(DyeItems.STAINED_GLASS_PANE.black()));
		pageButtons(46, 48, 50, page, pages, p -> {
			page = p;
			refresh();
		});
		if (parent != null) backButton(47, parent);
		closeButton(49);
		fill(DyeItems.STAINED_GLASS_PANE.gray());
	}
}
