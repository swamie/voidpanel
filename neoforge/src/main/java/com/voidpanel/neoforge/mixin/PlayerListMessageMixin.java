package com.voidpanel.neoforge.mixin;

import com.voidpanel.feature.Chat;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge has no event for broadcast system messages; this stands in for Fabric's ALLOW_GAME_MESSAGE. */
@Mixin(PlayerList.class)
public abstract class PlayerListMessageMixin {
	@Inject(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Ljava/util/function/Function;Z)V", at = @At("HEAD"), cancellable = true)
	private void voidpanel$filter(Component message, Function<ServerPlayer, Component> playerMessages, boolean overlay, CallbackInfo ci) {
		if (!Chat.allowGameMessage(message)) ci.cancel();
	}
}
