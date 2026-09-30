package com.voidpanel.mixin;

import com.voidpanel.feature.Graves;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Death graves: move the inventory into a grave before vanilla spills it. */
@Mixin(Player.class)
public abstract class PlayerDropMixin {
	@Shadow
	protected abstract void destroyVanishingCursedItems();

	@Inject(method = "dropEquipment", at = @At("HEAD"))
	private void voidpanel$grave(ServerLevel level, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player && !level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
			this.destroyVanishingCursedItems();
			Graves.onDeath(player);
		}
	}
}
