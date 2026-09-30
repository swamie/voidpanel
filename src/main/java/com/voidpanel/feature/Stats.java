package com.voidpanel.feature;

import com.voidpanel.data.DataStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Playtime, deaths and kills, tracked by VoidPanel so they work for offline players too. */
public final class Stats {
	private Stats() {}

	/** Once a second. */
	public static void tick(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			DataStore.get(p.getUUID()).playtimeSeconds++;
		}
		DataStore.markDirty();
	}

	public static void onKill(Entity killer, LivingEntity killed) {
		if (!(killer instanceof ServerPlayer p)) return;
		var d = DataStore.get(p.getUUID());
		if (killed instanceof ServerPlayer) d.playerKills++;
		else d.mobKills++;
		DataStore.markDirty();
	}

	public static void onDeath(ServerPlayer player) {
		DataStore.get(player.getUUID()).deaths++;
		DataStore.markDirty();
	}

	public static String playtime(long seconds) {
		long h = seconds / 3600, m = (seconds % 3600) / 60;
		if (h >= 24) return (h / 24) + "d " + (h % 24) + "h";
		if (h > 0) return h + "h " + m + "m";
		return m + "m";
	}
}
