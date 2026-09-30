package com.voidpanel.mixin;

import com.voidpanel.feature.Vanish;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanished players aren't sent to people who can't see them. */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
	private void voidpanel$vanish(ServerPlayer viewer, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ServerPlayer self && !Vanish.canSee(viewer, self)) cir.setReturnValue(false);
	}
}
