package com.voidpanel.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public final class Give {
	private Give() {}

	/** Put an item in the player's inventory, dropping whatever doesn't fit at their feet. */
	public static void item(ServerPlayer player, ItemStack stack) {
		if (player.getInventory().add(stack) || stack.isEmpty()) return;
		// Spawned by hand rather than Player.drop, whose signature differs between 26.2 and 26.3.
		ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack);
		item.setDefaultPickUpDelay();
		player.level().addFreshEntity(item);
	}
}
