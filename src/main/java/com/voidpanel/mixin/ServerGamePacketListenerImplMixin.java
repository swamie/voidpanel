package com.voidpanel.mixin;

import com.voidpanel.feature.Sit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stand players up before they're saved, so a seat never ends up in their player data. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "removePlayerFromWorld", at = @At("HEAD"))
	private void voidpanel$standUp(CallbackInfo ci) {
		Sit.stand(this.player);
	}
}
