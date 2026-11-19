package com.voidpanel.mixin;

import com.voidpanel.config.Feature;
import com.voidpanel.data.DataStore;
import java.net.SocketAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remember who got turned away by the whitelist so the panel can offer to add them. */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
	@Inject(method = "canPlayerLogin", at = @At("HEAD"))
	private void voidpanel$closeOfflineEditors(SocketAddress address, GameProfile who, CallbackInfoReturnable<Component> cir) {
		com.voidpanel.feature.Invsee.onLoginAttempt(who.getId());
	}

	@Inject(method = "canPlayerLogin", at = @At("RETURN"))
	private void voidpanel$recordDenied(SocketAddress address, GameProfile who, CallbackInfoReturnable<Component> cir) {
		Component reason = cir.getReturnValue();
		if (reason != null && reason.getContents() instanceof TranslatableContents t && t.getKey().equals("multiplayer.disconnect.not_whitelisted")) {
			if (com.voidpanel.config.Feature.WHITELIST_PANEL.enabled()) DataStore.recordDenied(who.getName(), who.getId());
		}
	}
}
