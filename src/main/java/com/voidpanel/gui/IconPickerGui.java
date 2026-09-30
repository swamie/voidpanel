package com.voidpanel.gui;

import com.voidpanel.data.DataStore;
import com.voidpanel.data.PlayerData.Home;
import com.voidpanel.feature.Homes;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Double chest of every block: left-click one to make it the home's icon. */
public final class IconPickerGui extends Gui {
	private static final int PER_PAGE = 45;
	private static List<Item> blocks;

	private final String label;
	private final Item current;
	private final Gui parent;
	private final java.util.function.Consumer<Item> onPick;
	private int page;

	public IconPickerGui(ServerPlayer player, String label, Item current, Gui parent, java.util.function.Consumer<Item> onPick) {
		super(player, 6, "&5&l✦ &8Pick an icon: " + label);
		this.label = label;
		this.current = current;
		this.parent = parent;
		this.onPick = onPick;
	}

	/** Home icons: save and go back to /homes. */
	public static IconPickerGui forHome(ServerPlayer player, Home home, Gui parent) {
		return new IconPickerGui(player, home.name, Homes.icon(home), parent, item -> {
			home.icon = BuiltInRegistries.ITEM.getKey(item).toString();
			DataStore.markDirty();
			Msg.ok(player, "Icon updated.");
			new HomesGui(player).open();
		});
	}

	private static List<Item> blocks() {
		if (blocks == null) {
			blocks = BuiltInRegistries.ITEM.stream()
				.filter(i -> i instanceof BlockItem && i != Items.AIR)
				.toList();
		}
		return blocks;
	}

	@Override
	protected void build() {
		int pages = page(blocks(), 0, PER_PAGE, page, (slot, item) -> set(slot,
			ItemBuilder.of(item).clean().lore("", "&e▶ Click to use this icon").build(), c -> choose(item)));

		for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		pageButtons(45, 49, 53, page, pages, p -> {
			page = p;
			refresh();
		});
		// Jump 5 pages at a time, there are a lot of blocks.
		if (page + 5 < pages) set(52, ItemBuilder.of(Items.SPECTRAL_ARROW).name("&eSkip 5 pages »").count(5).build(), c -> {
			Msg.click(player);
			page += 5;
			refresh();
		});
		if (page >= 5) set(46, ItemBuilder.of(Items.SPECTRAL_ARROW).name("&e« Back 5 pages").count(5).build(), c -> {
			Msg.click(player);
			page -= 5;
			refresh();
		});

		ItemStack held = player.getMainHandItem();
		if (!held.isEmpty()) {
			set(47, ItemBuilder.of(held.getItem()).name("&bUse held item").clean().lore("&7Use the item in your hand", "&7as the icon instead.").build(), c -> choose(held.getItem()));
		}
		set(48, ItemBuilder.of(current).name("&7Current: &f" + label).clean().build());
		backButton(50, parent);
		closeButton(51);
	}

	private void choose(Item item) {
		onPick.accept(item);
	}
}
