package com.voidpanel.mixin;

import com.voidpanel.config.Feature;
import com.voidpanel.feature.Chat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tab list: rank prefix, nickname and AFK tag. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
	private void voidpanel$tabName(CallbackInfoReturnable<Component> cir) {
		if (!com.voidpanel.config.Feature.TAB_LIST.enabled()) return;
		cir.setReturnValue(Chat.tabName((ServerPlayer) (Object) this));
	}
}
