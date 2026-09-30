package com.voidpanel.feature;

import com.voidpanel.data.DataStore;
import com.voidpanel.data.DataStore.Warp;
import com.voidpanel.data.Loc;
import com.voidpanel.util.Msg;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Server-wide warps set by admins. */
public final class Warps {
	private Warps() {}

	public static Warp get(String name) {
		return DataStore.root().warps.get(name.toLowerCase());
	}

	public static void set(ServerPlayer player, String name) {
		if (!Homes.VALID_NAME.matcher(name).matches()) {
			Msg.err(player, "Warp names can use letters, numbers, - and _ (max 16).");
			return;
		}
		Warp w = get(name);
		boolean moved = w != null;
		if (w == null) {
			w = new Warp();
			w.name = name;
			w.createdBy = player.getGameProfile().name();
			DataStore.root().warps.put(name.toLowerCase(), w);
		}
		w.loc = Loc.of(player);
		DataStore.markDirty();
		Msg.ok(player, (moved ? "Moved warp &f" : "Created warp &f") + w.name + "&a.");
	}

	public static void delete(ServerPlayer player, Warp w) {
		DataStore.root().warps.remove(w.name.toLowerCase());
		DataStore.markDirty();
		Msg.ok(player, "Deleted warp &f" + w.name + "&a.");
	}

	public static void go(ServerPlayer player, Warp w) {
		Teleports.start(player, w.name, () -> w.loc);
	}

	public static Item icon(Warp w) {
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(w.icon));
		return item == null || item == Items.AIR ? Items.ENDER_PEARL : item;
	}
}
