package com.voidpanel.feature;

import com.voidpanel.config.Config;
import com.voidpanel.data.DataStore;
import com.voidpanel.data.Loc;
import com.voidpanel.util.Msg;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/** Warmup teleports: stand still for N seconds, don't get hit, then warp. */
public final class Teleports {
	private static final Map<UUID, Warmup> WARMUPS = new HashMap<>();

	private Teleports() {}

	private static final class Warmup {
		final Vec3 start;
		final int total;
		int left;
		final String label;
		final Supplier<Loc> destination;

		Warmup(Vec3 start, int ticks, String label, Supplier<Loc> destination) {
			this.start = start;
			this.total = ticks;
			this.left = ticks;
			this.label = label;
			this.destination = destination;
		}
	}

	/**
	 * Start a warmup. {@code destination} is resolved when the timer finishes, so it can follow
	 * a moving target (TPA). Returning null from it aborts silently.
	 */
	public static boolean start(ServerPlayer player, String label, Supplier<Loc> destination) {
		if (Combat.inCombat(player)) {
			Msg.err(player, "You can't teleport while in combat! &7(" + Msg.seconds(Combat.remaining(player)) + " left)");
			return false;
		}
		int ticks = Config.get().teleportWarmupSeconds * 20;
		if (ticks <= 0 || player.isCreative() || player.isSpectator()) {
			Loc loc = destination.get();
			if (loc != null) teleport(player, loc);
			return true;
		}
		WARMUPS.put(player.getUUID(), new Warmup(player.position(), ticks, label, destination));
		Msg.info(player, "Teleporting to &d" + label + " &7in &f" + Config.get().teleportWarmupSeconds + "s&7. Don't move!");
		Msg.sound(player, SoundEvents.BEACON_ACTIVATE, 0.5f, 1.6f);
		return true;
	}

	public static boolean isWarmingUp(ServerPlayer player) {
		return WARMUPS.containsKey(player.getUUID());
	}

	public static void cancel(ServerPlayer player, String reason) {
		if (WARMUPS.remove(player.getUUID()) != null) {
			Msg.err(player, reason.replaceFirst("^&c", ""));
			Msg.actionBar(player, reason);
		}
	}

	public static void clear(ServerPlayer player) {
		WARMUPS.remove(player.getUUID());
	}

	public static void tick(MinecraftServer server) {
		for (UUID id : Set.copyOf(WARMUPS.keySet())) {
			Warmup w = WARMUPS.get(id);
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null || p.isDeadOrDying()) {
				WARMUPS.remove(id);
				continue;
			}
			if (p.position().distanceToSqr(w.start) > 0.35 * 0.35) {
				cancel(p, "&cTeleport cancelled, you moved!");
				continue;
			}
			w.left--;
			if (w.left % 4 == 0) {
				ServerLevel level = p.level();
				level.sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 6, 0.4, 0.6, 0.4, 0.2);
			}
			if (w.left % 20 == 0 && w.left > 0) {
				Msg.sound(p, SoundEvents.NOTE_BLOCK_HAT, 0.6f, 1.0f + (w.total - w.left) / (float) w.total);
			}
			if (w.left <= 0) {
				WARMUPS.remove(id);
				Loc loc = w.destination.get();
				if (loc != null) teleport(p, loc);
				continue;
			}
			double frac = 1.0 - (double) w.left / w.total;
			Msg.actionBar(p, "&dTeleporting &f" + Msg.seconds(w.left) + " " + Msg.bar(frac, "&d", "&8") + " &7don't move");
		}
	}

	/** Instant teleport that also records /back. */
	public static void teleport(ServerPlayer player, Loc loc) {
		ServerLevel level = loc.level(player.level().getServer());
		if (level == null) {
			Msg.err(player, "That world doesn't exist anymore.");
			return;
		}
		DataStore.get(player.getUUID()).back = Loc.of(player);
		DataStore.markDirty();
		if (player.isPassenger()) player.stopRiding();
		player.teleportTo(level, loc.x, loc.y, loc.z, Set.of(), loc.yaw, loc.pitch, true);
		player.fallDistance = 0;
		Msg.sound(player, SoundEvents.ENDERMAN_TELEPORT, 0.7f, 1.0f);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, loc.x, loc.y + 1, loc.z, 30, 0.4, 0.8, 0.4, 0.05);
		Msg.actionBar(player, "&a✔ Teleported");
	}
}
