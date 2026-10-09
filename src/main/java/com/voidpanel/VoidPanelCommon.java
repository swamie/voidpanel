package com.voidpanel;

import com.voidpanel.config.Config;
import com.voidpanel.data.DataStore;
import com.voidpanel.feature.Afk;
import com.voidpanel.feature.AntiDupe;
import com.voidpanel.feature.ChatPrompts;
import com.voidpanel.feature.Chat;
import com.voidpanel.feature.Combat;
import com.voidpanel.feature.Graves;
import com.voidpanel.feature.Messages;
import com.voidpanel.feature.Pets;
import com.voidpanel.feature.Ranks;
import com.voidpanel.feature.Spawn;
import com.voidpanel.feature.Stats;
import com.voidpanel.feature.Teleports;
import com.voidpanel.feature.Tpa;
import com.voidpanel.feature.Vanish;
import com.voidpanel.gui.Gui;
import com.voidpanel.util.Msg;
import com.voidpanel.util.Scheduler;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Everything the loader entrypoints (Fabric and NeoForge) hook into. */
public final class VoidPanelCommon {
	public static final Logger LOGGER = LoggerFactory.getLogger("VoidPanel");
	private static int ticks;

	private VoidPanelCommon() {}

	public static void init(Path configDir) {
		Config.load(configDir);
	}

	public static void serverStarted(MinecraftServer server) {
		DataStore.load(server);
	}

	public static void serverStopping(MinecraftServer server) {
		DataStore.save();
		Scheduler.clear();
	}

	public static void serverTick(MinecraftServer server) {
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
	}

	public static void playerJoin(ServerPlayer player, MinecraftServer server) {
		Chat.onJoin(player, server);
	}

	public static void playerLeave(ServerPlayer player, MinecraftServer server) {
		Chat.onLeave(player, server);
		Teleports.clear(player);
		Combat.clear(player);
		ChatPrompts.cancel(player);
		Tpa.forget(player.getUUID());
		Messages.forget(player.getUUID());
		Afk.forget(player.getUUID());
	}

	public static void entityLoad(Entity entity) {
		AntiDupe.onEntityLoad(entity);
		Pets.record(entity);
	}

	public static void entityUnload(Entity entity) {
		Pets.onUnload(entity);
	}

	public static void afterDamage(LivingEntity entity, DamageSource source, float taken) {
		Combat.onDamage(entity, source, taken);
	}

	public static void afterDeath(LivingEntity entity, DamageSource source) {
		if (entity instanceof ServerPlayer player) {
			Spawn.onDeath(player);
			Stats.onDeath(player);
		}
	}

	public static void afterKill(Entity killer, LivingEntity killed) {
		Stats.onKill(killer, killed);
	}

	/**
	 * Graves: right-clicking or breaking one recovers it, and nobody else can touch it.
	 * Returns true if the position was a grave and the vanilla action should be stopped.
	 */
	public static boolean graveInteract(Player player, Level level, BlockPos pos) {
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return false;
		var grave = Graves.at(level, pos);
		if (grave == null) return false;
		if (Graves.canOpen(sp, grave)) Graves.claim(sp, grave);
		else Msg.err(sp, "This is &f" + grave.ownerName + "&c's grave.");
		return true;
	}
}
