package com.voidpanel.gui;

import com.voidpanel.data.DataStore;
import com.voidpanel.data.PlayerData;
import com.voidpanel.data.PlayerData.Home;
import com.voidpanel.feature.ChatPrompts;
import com.voidpanel.feature.Homes;
import com.voidpanel.perm.Perm;
import com.voidpanel.perm.Perms;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** /homes: left-click to warp, right-click to change the icon, shift-click to rename/delete. */
public final class HomesGui extends Gui {
	private static final int PER_PAGE = 27;
	private int page;

	public HomesGui(ServerPlayer player) {
		this(player, 0);
	}

	public HomesGui(ServerPlayer player, int page) {
		super(player, 4, "&5&l✦ &8Your Homes");
		this.page = page;
	}

	@Override
	protected void build() {
		PlayerData data = DataStore.get(player.getUUID());
		List<Home> homes = List.copyOf(data.homes.values());
		int limit = Perms.homeLimit(player.getUUID());

		int pages = page(homes, 0, PER_PAGE, page, (slot, home) -> set(slot, homeItem(home), c -> {
			if (c.shift() && c.right()) {
				Msg.click(player);
				new ConfirmGui(player, "&4Delete " + home.name + "?", ItemBuilder.of(Homes.icon(home)).name("&f" + home.name).build(), () -> {
					Homes.delete(player, home);
					new HomesGui(player, page).open();
				}, () -> new HomesGui(player, page).open()).open();
			} else if (c.shift()) {
				ChatPrompts.ask(player, "Type a new name for &f" + home.name + "&7:", home.name, input -> {
					Homes.rename(player, home, input);
					new HomesGui(player, page).open();
				});
			} else if (c.right()) {
				Msg.click(player);
				IconPickerGui.forHome(player, home, this).open();
			} else {
				close();
				Homes.go(player, home);
			}
		}));

		for (int i = 27; i < 36; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		if (homes.isEmpty()) {
			set(13, ItemBuilder.of(Items.CLOCK).name("&7No homes yet").lore("&7Use &f/sethome <name> &7or the", "&7bed button below to make one.").build());
		}
		pageButtons(27, -1, 35, page, pages, p -> new HomesGui(player, p).open());

		boolean full = homes.size() >= limit;
		if (Perms.has(player, Perm.SETHOME)) {
			set(30, ItemBuilder.of(full ? Items.BED.gray() : Items.BED.lime()).name(full ? "&7Home limit reached" : "&a&l+ Set home here")
				.lore(full ? new String[] {"&7Delete a home to make room."} : new String[] {"&7Saves your current position.", "", "&e▶ Click, then type a name"}).build(), c -> {
					if (full) {
						Msg.err(player, "You've reached your home limit.");
						return;
					}
					ChatPrompts.ask(player, "Type a name for your new home:", null, input -> {
						Homes.set(player, input);
						new HomesGui(player, page).open();
					});
				});
		}
		set(31, ItemBuilder.of(Items.BOOK).name("&dHomes &f" + homes.size() + "&7/&f" + limit).count(Math.max(1, homes.size())).lore(
			"&eLeft-click &7a home to teleport",
			"&eRight-click &7to change its icon",
			"&eShift-left &7to rename it",
			"&cShift-right &7to delete it").build());
		closeButton(32);
	}

	private net.minecraft.world.item.ItemStack homeItem(Home home) {
		return ItemBuilder.of(Homes.icon(home)).name("&a&l" + home.name).clean().lore(
			"&7World: " + home.loc.dimColor() + home.loc.dimName(),
			"&7Location: &f" + home.loc.coords(),
			"",
			"&e▶ Left-click &7to teleport",
			"&e▶ Right-click &7to change icon",
			"&e▶ Shift-left &7to rename",
			"&c▶ Shift-right &7to delete").build();
	}
}
