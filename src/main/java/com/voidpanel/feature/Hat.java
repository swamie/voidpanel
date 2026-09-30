package com.voidpanel.feature;

import com.voidpanel.util.Msg;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public final class Hat {
	private Hat() {}

	public static void wear(ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		if (held.isEmpty()) {
			Msg.err(player, "Hold the item you want to wear.");
			return;
		}
		if (!player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
			Msg.err(player, "Your head slot needs to be empty first.");
			return;
		}
		ItemStack hat = held.split(1);
		player.setItemSlot(EquipmentSlot.HEAD, hat);
		Msg.sound(player, SoundEvents.ARMOR_EQUIP_LEATHER, 0.8f, 1.0f);
		Msg.ok(player, "Looking sharp! You're wearing &f" + hat.getHoverName().getString() + "&a.");
	}
}
