package com.voidpanel.feature;

import com.voidpanel.VoidPanel;
import com.voidpanel.util.ItemBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Safety net for the GUIs. Menu items can't be taken in the first place, but if one
 * ever reaches a real inventory (client desync, creative middle-click, another mod)
 * it's deleted here.
 */
public final class AntiDupe {
	private AntiDupe() {}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) sweep(player);
	}

	public static void sweep(ServerPlayer player) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (ItemBuilder.isGuiItem(inv.getItem(i))) {
				inv.setItem(i, ItemStack.EMPTY);
				VoidPanel.LOGGER.warn("Removed a GUI item from {}'s inventory (slot {})", player.getGameProfile().name(), i);
			}
		}
		if (ItemBuilder.isGuiItem(player.containerMenu.getCarried())) player.containerMenu.setCarried(ItemStack.EMPTY);
		var ender = player.getEnderChestInventory();
		for (int i = 0; i < ender.getContainerSize(); i++) {
			if (ItemBuilder.isGuiItem(ender.getItem(i))) ender.setItem(i, ItemStack.EMPTY);
		}
	}

	public static void onEntityLoad(Entity entity) {
		if (entity instanceof ItemEntity item && ItemBuilder.isGuiItem(item.getItem())) item.discard();
	}
}
