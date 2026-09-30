package com.voidpanel.feature;

import com.voidpanel.data.DataStore;
import com.voidpanel.data.Loc;
import com.voidpanel.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelData;

/** /spawn, /setspawn and /back. */
public final class Spawn {
	private Spawn() {}

	public static Loc location(MinecraftServer server) {
		Loc custom = DataStore.root().spawn;
		if (custom != null) return custom;
		LevelData.RespawnData data = server.getRespawnData();
		BlockPos pos = data.pos();
		return new Loc(data.dimension().identifier().toString(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, data.yaw(), data.pitch());
	}

	public static void go(ServerPlayer player) {
		Loc loc = location(player.level().getServer());
		Teleports.start(player, "spawn", () -> loc);
	}

	public static void set(ServerPlayer player) {
		Loc loc = Loc.of(player);
		DataStore.root().spawn = loc;
		DataStore.markDirty();
		// Keep vanilla's world spawn in sync so compasses and first spawns agree.
		player.level().getServer().setRespawnData(LevelData.RespawnData.of(player.level().dimension(), player.blockPosition(), player.getYRot(), player.getXRot()));
		Msg.ok(player, "Spawn set to &f" + loc.coords() + " &ain " + loc.dimName() + ".");
	}

	public static void back(ServerPlayer player) {
		Loc back = DataStore.get(player.getUUID()).back;
		if (back == null) {
			Msg.err(player, "You have nowhere to go back to.");
			return;
		}
		Teleports.start(player, "your last location", () -> back);
	}

	public static void onDeath(ServerPlayer player) {
		DataStore.get(player.getUUID()).back = Loc.of(player);
		DataStore.markDirty();
		Teleports.clear(player);
		Combat.clear(player);
	}
}
