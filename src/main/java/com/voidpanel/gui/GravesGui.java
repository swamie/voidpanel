package com.voidpanel.gui;

import com.voidpanel.data.DataStore.Grave;
import com.voidpanel.data.Loc;
import com.voidpanel.feature.Graves;
import com.voidpanel.feature.Teleports;
import com.voidpanel.util.ItemBuilder;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** /graves: where your graves are, with click-to-teleport (and claim if the chest was destroyed). */
public final class GravesGui extends Gui {
	public GravesGui(ServerPlayer player) {
		super(player, 4, "&5&l✦ &8Your Graves");
	}

	@Override
	protected void build() {
		var server = player.level().getServer();
		List<Grave> graves = Graves.of(player.getUUID());
		page(graves, 0, 27, 0, (slot, g) -> {
			boolean intact = Graves.markerIntact(server, g);
			Loc loc = new Loc(g.dim, g.x + 0.5, g.y + 1, g.z + 0.5, 0, 0);
			set(slot, ItemBuilder.of(intact ? Items.CHEST : Items.SKELETON_SKULL).name("&6&lGrave &8#" + g.id).count(Math.min(64, g.items.size())).lore(
				"&7World: " + loc.dimColor() + loc.dimName(),
				"&7Location: &f" + g.x + ", " + g.y + ", " + g.z,
				"&7Items: &f" + g.items.size() + " stacks",
				"&7Died: &f" + WhitelistGui.ago(g.created),
				"",
				intact ? "&e▶ Click to teleport there" : "&c The chest was destroyed.",
				intact ? "&7Right-click the chest to get your items." : "&a▶ Click to recover your items here").build(), c -> {
					close();
					if (intact) Teleports.start(player, "your grave", () -> loc);
					else Graves.claim(player, g);
				});
		});
		if (graves.isEmpty()) set(13, ItemBuilder.of(Items.TOTEM_OF_UNDYING).name("&aNo graves").lore("&7You haven't died with items", "&7since graves were turned on.").build());
		for (int i = 27; i < 36; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		backButton(27, new MainGui(player));

		boolean serverOn = com.voidpanel.config.Config.get().gravesEnabled;
		boolean mineOn = !com.voidpanel.data.DataStore.get(player.getUUID()).gravesOff;
		set(30, ItemBuilder.of(mineOn ? Items.CONCRETE.lime() : Items.CONCRETE.red()).name(mineOn ? "&a&lMy graves: ON" : "&c&lMy graves: OFF").lore(
			mineOn ? "&7Your items go into a grave" : "&7Your items drop on the ground",
			mineOn ? "&7when you die." : "&7like normal when you die.",
			serverOn ? "" : "&c(Graves are off for the whole server)",
			"",
			"&e▶ Click to turn " + (mineOn ? "off" : "on")).build(), c -> {
				Graves.togglePersonal(player);
				refresh();
			});
		if (com.voidpanel.perm.Perms.has(player, com.voidpanel.perm.Perm.GRAVES_TOGGLE)) {
			set(32, ItemBuilder.of(serverOn ? Items.LEVER : Items.REDSTONE_TORCH).name(serverOn ? "&a&lServer graves: ON" : "&c&lServer graves: OFF").glint(serverOn).lore(
				"&7Admin switch for everyone.",
				"&7Existing graves can still be",
				"&7claimed while this is off.",
				"",
				"&e▶ Click to turn " + (serverOn ? "off" : "on")).build(), c -> {
					Graves.toggleServer(player);
					refresh();
				});
		}
		closeButton(35);
	}
}
