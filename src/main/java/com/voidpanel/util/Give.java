package com.voidpanel.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;

public final class Give {
	private Give() {}

	/** Put an item in the player's inventory, dropping whatever doesn't fit at their feet. */
	public static void item(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false, Prediction.SERVER_ONLY);
	}
}
