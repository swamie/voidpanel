package com.voidpanel;

import com.voidpanel.command.Cmds;
import com.voidpanel.config.Config;
import com.voidpanel.data.DataStore;
import com.voidpanel.feature.AntiDupe;
import com.voidpanel.feature.Afk;
import com.voidpanel.feature.Graves;
import com.voidpanel.feature.Ranks;
import com.voidpanel.feature.Stats;
import com.voidpanel.feature.Vanish;
import com.voidpanel.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionResult;
import com.voidpanel.feature.Chat;
import com.voidpanel.feature.Messages;
import com.voidpanel.feature.Pets;
import com.voidpanel.feature.ChatPrompts;
import com.voidpanel.feature.Combat;
import com.voidpanel.feature.Spawn;
import com.voidpanel.feature.Teleports;
import com.voidpanel.feature.Tpa;
import com.voidpanel.gui.Gui;
import com.voidpanel.util.Scheduler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VoidPanel implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("VoidPanel");
	private static int ticks;

	@Override
	public void onInitialize() {
		Config.load();

		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> Cmds.register(dispatcher));

		ServerLifecycleEvents.SERVER_STARTED.register(DataStore::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			DataStore.save();
			Scheduler.clear();
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Scheduler.tick();
			Gui.tickAll();
			Combat.tick(server);
			Teleports.tick(server);
			if (++ticks % 5 == 0) AntiDupe.tick(server);
			if (ticks % 20 == 0) {
				Tpa.tick(server);
				Afk.tick(server);
				Stats.tick(server);
			}
			if (ticks % 40 == 0) Vanish.tick(server);
			if (ticks % 200 == 0) Ranks.checkExpiry(server);
			if (ticks % 1200 == 0) Ranks.checkPromotions(server);
			if (ticks % 200 == 0) Pets.scan(server);
			if (ticks % 600 == 0) DataStore.saveIfDirty();
		});

		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> Chat.onChat(message, sender));
		ServerMessageEvents.ALLOW_GAME_MESSAGE.register((server, message, overlay) -> Chat.allowGameMessage(message));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Chat.onJoin(handler.getPlayer(), server));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer player = handler.getPlayer();
			Chat.onLeave(player, server);
			Teleports.clear(player);
			Combat.clear(player);
			ChatPrompts.cancel(player);
			Tpa.forget(player.getUUID());
			Messages.forget(player.getUUID());
			Afk.forget(player.getUUID());
		});

		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			AntiDupe.onEntityLoad(entity);
			Pets.record(entity);
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> Pets.onUnload(entity));

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> Combat.onDamage(entity, source, taken));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				Spawn.onDeath(player);
				Stats.onDeath(player);
			}
		});
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, killer, killed, source) -> Stats.onKill(killer, killed));

		// Graves: right-click to recover, and nobody else can break them.
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			var grave = Graves.at(level, hit.getBlockPos());
			if (grave == null) return InteractionResult.PASS;
			if (Graves.canOpen(sp, grave)) Graves.claim(sp, grave);
			else Msg.err(sp, "This is &f" + grave.ownerName + "&c's grave.");
			return InteractionResult.SUCCESS;
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return true;
			var grave = Graves.at(level, pos);
			if (grave == null) return true;
			if (Graves.canOpen(sp, grave)) Graves.claim(sp, grave);
			else Msg.err(sp, "This is &f" + grave.ownerName + "&c's grave.");
			return false;
		});

		LOGGER.info("VoidPanel loaded");
	}
}
