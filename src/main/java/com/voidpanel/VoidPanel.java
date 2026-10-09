package com.voidpanel;

import com.voidpanel.command.Cmds;
import com.voidpanel.feature.Chat;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.InteractionResult;

/** Fabric entrypoint. The shared logic lives in {@link VoidPanelCommon}. */
public class VoidPanel implements ModInitializer {
	@Override
	public void onInitialize() {
		VoidPanelCommon.init(FabricLoader.getInstance().getConfigDir());

		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> Cmds.register(dispatcher));

		ServerLifecycleEvents.SERVER_STARTED.register(VoidPanelCommon::serverStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(VoidPanelCommon::serverStopping);
		ServerTickEvents.END_SERVER_TICK.register(VoidPanelCommon::serverTick);

		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> Chat.onChat(message.signedContent(), sender));
		ServerMessageEvents.ALLOW_GAME_MESSAGE.register((server, message, overlay) -> Chat.allowGameMessage(message));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> VoidPanelCommon.playerJoin(handler.getPlayer(), server));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> VoidPanelCommon.playerLeave(handler.getPlayer(), server));

		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> VoidPanelCommon.entityLoad(entity));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> VoidPanelCommon.entityUnload(entity));

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> VoidPanelCommon.afterDamage(entity, source, taken));
		ServerLivingEntityEvents.AFTER_DEATH.register(VoidPanelCommon::afterDeath);
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, killer, killed, source) -> VoidPanelCommon.afterKill(killer, killed));

		UseBlockCallback.EVENT.register((player, level, hand, hit) ->
			VoidPanelCommon.graveInteract(player, level, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> !VoidPanelCommon.graveInteract(player, level, pos));

		VoidPanelCommon.LOGGER.info("VoidPanel loaded");
	}
}
