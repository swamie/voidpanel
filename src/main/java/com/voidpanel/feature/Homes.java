package com.voidpanel.feature;

import com.voidpanel.data.DataStore;
import com.voidpanel.data.Loc;
import com.voidpanel.data.PlayerData;
import com.voidpanel.data.PlayerData.Home;
import com.voidpanel.perm.Perms;
import com.voidpanel.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class Homes {
	private Homes() {}

	public static final java.util.regex.Pattern VALID_NAME = java.util.regex.Pattern.compile("[A-Za-z0-9_-]{1,16}");

	public static void set(ServerPlayer player, String name) {
		if (!VALID_NAME.matcher(name).matches()) {
			Msg.err(player, "Home names can use letters, numbers, - and _ (max 16).");
			return;
		}
		PlayerData data = DataStore.get(player.getUUID());
		Home existing = find(data, name);
		int limit = Perms.homeLimit(player.getUUID());
		if (existing == null && data.homes.size() >= limit) {
			Msg.err(player, "You've reached your home limit (&f" + limit + "&c). Delete one in &f/homes&c first.");
			return;
		}
		Loc loc = Loc.of(player);
		if (existing != null) {
			existing.loc = loc;
			Msg.ok(player, "Moved home &f" + existing.name + " &ato your location.");
		} else {
			data.homes.put(name.toLowerCase(), new Home(name, loc, defaultIcon(player)));
			Msg.ok(player, "Home &f" + name + " &aset! &7(" + data.homes.size() + "/" + limit + ")");
		}
		DataStore.markDirty();
	}

	public static void go(ServerPlayer player, Home home) {
		if (!Perms.has(player, com.voidpanel.perm.Perm.HOME)) {
			Msg.err(player, "You don't have permission to teleport home.");
			return;
		}
		Teleports.start(player, home.name, () -> home.loc);
	}

	public static void delete(ServerPlayer player, Home home) {
		DataStore.get(player.getUUID()).homes.remove(home.name.toLowerCase());
		DataStore.markDirty();
		Msg.ok(player, "Deleted home &f" + home.name + "&a.");
	}

	public static boolean rename(ServerPlayer player, Home home, String newName) {
		PlayerData data = DataStore.get(player.getUUID());
		if (!VALID_NAME.matcher(newName).matches()) {
			Msg.err(player, "Home names can use letters, numbers, - and _ (max 16).");
			return false;
		}
		Home clash = find(data, newName);
		if (clash != null && clash != home) {
			Msg.err(player, "You already have a home called &f" + newName + "&c.");
			return false;
		}
		// Rebuild to keep the order but change the key.
		var rebuilt = new java.util.LinkedHashMap<String, Home>();
		for (var e : data.homes.entrySet()) {
			if (e.getValue() == home) rebuilt.put(newName.toLowerCase(), home);
			else rebuilt.put(e.getKey(), e.getValue());
		}
		home.name = newName;
		data.homes = rebuilt;
		DataStore.markDirty();
		Msg.ok(player, "Renamed to &f" + newName + "&a.");
		return true;
	}

	public static Home find(PlayerData data, String name) {
		return data.homes.get(name.toLowerCase());
	}

	public static Item icon(Home home) {
		if (home.icon != null) {
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(home.icon));
			if (item != null && item != Items.AIR) return item;
		}
		return Items.BED.red();
	}

	/** The block you're standing on, so homes get a sensible icon straight away. */
	private static String defaultIcon(ServerPlayer player) {
		BlockPos below = player.blockPosition().below();
		Item item = player.level().getBlockState(below).getBlock().asItem();
		if (item == Items.AIR) item = Items.BED.red();
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}
}
