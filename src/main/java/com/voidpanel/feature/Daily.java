package com.voidpanel.feature;

import com.voidpanel.config.Config;
import com.voidpanel.data.DataStore;
import com.voidpanel.data.PlayerData;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A 7-day reward calendar. Claiming on consecutive days builds a streak. */
public final class Daily {
	public static final int DAYS = 7;

	private Daily() {}

	public static long today() {
		return LocalDate.now().toEpochDay();
	}

	public static boolean canClaim(ServerPlayer player) {
		return DataStore.get(player.getUUID()).lastDailyDay < today();
	}

	/** Which day of the calendar (0-6) the next claim is. */
	public static int nextIndex(ServerPlayer player) {
		PlayerData d = DataStore.get(player.getUUID());
		int streak = d.lastDailyDay == today() - 1 || d.lastDailyDay == today() ? d.dailyStreak : 0;
		if (d.lastDailyDay == today()) return (streak - 1) % DAYS;
		return streak % DAYS;
	}

	/** The streak the player would have after claiming today. */
	public static int streakAfterClaim(ServerPlayer player) {
		PlayerData d = DataStore.get(player.getUUID());
		return d.lastDailyDay == today() - 1 ? d.dailyStreak + 1 : 1;
	}

	/** Mark today's reward as claimed and return the items. */
	public static List<ItemStack> claim(ServerPlayer player) {
		PlayerData d = DataStore.get(player.getUUID());
		int index = nextIndex(player);
		d.dailyStreak = streakAfterClaim(player);
		d.lastDailyDay = today();
		DataStore.markDirty();
		return rewards(index);
	}

	public static List<ItemStack> rewards(int day) {
		List<ItemStack> out = new ArrayList<>();
		var all = Config.get().dailyRewards;
		if (all == null || day >= all.size()) return out;
		for (String entry : all.get(day)) {
			ItemStack s = parse(entry);
			if (!s.isEmpty()) out.add(s);
		}
		return out;
	}

	/** "minecraft:diamond 3" -> 3 diamonds. */
	public static ItemStack parse(String entry) {
		String[] parts = entry.trim().split("\\s+");
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(parts[0]));
		if (item == null || item == Items.AIR) return ItemStack.EMPTY;
		int count = 1;
		if (parts.length > 1) {
			try {
				count = Integer.parseInt(parts[1]);
			} catch (NumberFormatException ignored) {
			}
		}
		return new ItemStack(item, Math.max(1, Math.min(count, item.getDefaultMaxStackSize() * 4)));
	}

	public static String format(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()) + " " + stack.getCount();
	}

	public static List<List<String>> defaults() {
		return new ArrayList<>(List.of(
			new ArrayList<>(List.of("minecraft:bread 16", "minecraft:torch 32")),
			new ArrayList<>(List.of("minecraft:iron_ingot 8", "minecraft:cooked_beef 8")),
			new ArrayList<>(List.of("minecraft:gold_ingot 6", "minecraft:experience_bottle 8")),
			new ArrayList<>(List.of("minecraft:lapis_lazuli 16", "minecraft:redstone 16")),
			new ArrayList<>(List.of("minecraft:diamond 2", "minecraft:golden_carrot 16")),
			new ArrayList<>(List.of("minecraft:emerald 8", "minecraft:golden_apple 2")),
			new ArrayList<>(List.of("minecraft:diamond 5", "minecraft:enchanted_golden_apple 1", "minecraft:experience_bottle 32"))));
	}
}
