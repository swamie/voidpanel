package com.voidpanel.mixin;

import com.voidpanel.feature.Chat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Nicknames: swap the name inside getDisplayName() so chat, death messages etc. use it. */
@Mixin(Player.class)
public abstract class PlayerMixin {
	@Redirect(method = "getDisplayName", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getName()Lnet/minecraft/network/chat/Component;"))
	private Component voidpanel$nick(Player self) {
		if (self instanceof ServerPlayer sp) {
			Component nick = Chat.nick(sp);
			if (nick != null) return nick;
		}
		return self.getName();
	}
}
