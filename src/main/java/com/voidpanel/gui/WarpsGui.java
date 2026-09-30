package com.voidpanel.gui;

import com.voidpanel.data.DataStore;
import com.voidpanel.data.DataStore.Warp;
import com.voidpanel.feature.ChatPrompts;
import com.voidpanel.feature.Warps;
import com.voidpanel.perm.Perm;
import com.voidpanel.perm.Perms;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** /warps: click to travel. Admins can also re-icon, move and delete warps here. */
public final class WarpsGui extends Gui {
	private int page;

	public WarpsGui(ServerPlayer player) {
		super(player, 5, "&5&l✦ &8Warps");
	}

	@Override
	protected void build() {
		boolean admin = Perms.has(player, Perm.SETWARP);
		List<Warp> warps = List.copyOf(DataStore.root().warps.values());
		int pages = page(warps, 0, 36, page, (slot, w) -> {
			List<String> lore = new ArrayList<>(List.of(
				"&7World: " + w.loc.dimColor() + w.loc.dimName(),
				"&7Location: &f" + w.loc.coords(),
				"",
				"&e▶ Click to warp"));
			if (admin) lore.addAll(List.of("", "&8Admin:", "&eRight-click &7change icon", "&eShift-left &7move here", "&cShift-right &7delete"));
			set(slot, ItemBuilder.of(Warps.icon(w)).name("&d&l" + w.name).clean().lore(lore).build(), c -> {
				if (admin && c.shift() && c.right()) {
					new ConfirmGui(player, "&4Delete warp " + w.name + "?", ItemBuilder.of(Warps.icon(w)).name("&f" + w.name).build(), () -> {
						Warps.delete(player, w);
						new WarpsGui(player).open();
					}, () -> new WarpsGui(player).open()).open();
				} else if (admin && c.shift()) {
					Warps.set(player, w.name);
					refresh();
				} else if (admin && c.right()) {
					new IconPickerGui(player, w.name, Warps.icon(w), this, item -> {
						w.icon = BuiltInRegistries.ITEM.getKey(item).toString();
						DataStore.markDirty();
						Msg.ok(player, "Warp icon updated.");
						new WarpsGui(player).open();
					}).open();
				} else {
					close();
					Warps.go(player, w);
				}
			});
		});
		if (warps.isEmpty()) {
			set(22, ItemBuilder.of(Items.CLOCK).name("&7No warps yet").lore(admin ? "&7Use &f/setwarp <name> &7or the button below." : "&7An admin hasn't made any warps yet.").build());
		}
		for (int i = 36; i < 45; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		pageButtons(36, -1, 44, page, pages, p -> {
			page = p;
			refresh();
		});
		backButton(38, new MainGui(player));
		if (admin) {
			set(40, ItemBuilder.of(Items.END_CRYSTAL).name("&a&l+ New warp here").lore("&7Create a warp where you're standing.", "", "&e▶ Click, then type a name").build(), c ->
				ChatPrompts.ask(player, "Type a name for the new warp:", null, name -> {
					Warps.set(player, name);
					new WarpsGui(player).open();
				}));
		}
		closeButton(42);
	}
}
