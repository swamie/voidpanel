package com.voidpanel.mixin;

import com.voidpanel.feature.Chat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tab list: rank prefix, nickname, AFK tag, and rank ordering. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
	private void voidpanel$tabName(CallbackInfoReturnable<Component> cir) {
		cir.setReturnValue(Chat.tabName((ServerPlayer) (Object) this));
	}

	/** Higher ranks at the top of the tab list. */
	@Inject(method = "getTabListOrder", at = @At("HEAD"), cancellable = true)
	private void voidpanel$tabOrder(CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(com.voidpanel.feature.Ranks.of(((ServerPlayer) (Object) this).getUUID()).weight);
	}
}
