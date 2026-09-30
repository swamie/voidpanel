package com.voidpanel.feature;

import com.voidpanel.data.DataStore;
import com.voidpanel.mixin.ChunkMapAccessor;
import com.voidpanel.mixin.TrackedEntityInvoker;
import com.voidpanel.perm.Perm;
import com.voidpanel.perm.Perms;
import com.voidpanel.util.Msg;
import java.util.List;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** /vanish: hidden from the world, the tab list and join/leave messages. Staff with the perm still see you. */
public final class Vanish {
	private Vanish() {}

	public static boolean isVanished(ServerPlayer player) {
		return DataStore.find(player.getUUID()).map(d -> d.vanished).orElse(false);
	}

	public static boolean canSee(ServerPlayer viewer, ServerPlayer target) {
		return viewer == target || !isVanished(target) || Perms.has(viewer, Perm.VANISH);
	}

	public static void toggle(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		var d = DataStore.get(player.getUUID());
		d.vanished = !d.vanished;
		DataStore.markDirty();
		if (d.vanished) {
			Chat.broadcastLeave(player, server);
			Msg.ok(player, "You vanished. Only staff can see you. &7(/vanish again to reappear)");
		} else {
			Chat.broadcastJoin(player, server, false);
			Msg.ok(player, "You're visible again.");
		}
		refresh(player, server);
	}

	/** Re-sync tab list entries and entity tracking for everyone. */
	public static void refresh(ServerPlayer player, MinecraftServer server) {
		for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
			if (viewer == player) continue;
			if (canSee(viewer, player)) {
				viewer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)));
			} else {
				viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
			}
		}
		Chat.refreshTabName(player, server);
		ServerLevel level = player.level();
		Object tracked = ((ChunkMapAccessor) level.getChunkSource().chunkMap).voidpanel$entityMap().get(player.getId());
		if (tracked != null) ((TrackedEntityInvoker) tracked).voidpanel$updatePlayers(level.players());
	}

	/** Hide already-vanished players from someone who just joined (and the joiner, if vanished). */
	public static void onJoin(ServerPlayer joined, MinecraftServer server) {
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (other != joined && !canSee(joined, other)) {
				joined.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(other.getUUID())));
			}
		}
		if (isVanished(joined)) {
			refresh(joined, server);
			Msg.info(joined, "&bYou are still vanished.");
		}
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (isVanished(p)) Msg.actionBar(p, "&b&lVANISHED &8| &7only staff can see you");
		}
	}
}
