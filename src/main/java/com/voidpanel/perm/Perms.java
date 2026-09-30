package com.voidpanel.perm;

import com.voidpanel.config.Config;
import com.voidpanel.data.DataStore;
import com.voidpanel.data.PlayerData;
import com.voidpanel.feature.Ranks;
import com.voidpanel.util.Text;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;

public final class Perms {
	private Perms() {}

	/** Command requirement: console / command blocks always pass. */
	public static boolean check(CommandSourceStack source, Perm perm) {
		ServerPlayer player = source.getPlayer();
		if (player == null) return true;
		return has(player, perm);
	}

	public static boolean has(ServerPlayer player, Perm perm) {
		return resolve(player.getUUID(), isAdmin(player), perm);
	}

	/** Player override, then rank, then default rank, then the built-in default. */
	private static boolean resolve(UUID id, boolean admin, Perm perm) {
		if (perm == Perm.PERMISSIONS && admin) return true;
		Boolean override = DataStore.find(id).map(d -> d.perms.get(perm.node)).orElse(null);
		if (override != null) return override;
		Boolean rank = Ranks.perm(id, perm);
		if (rank != null) return rank;
		return !perm.adminOnly || admin;
	}

	/** Where a permission value comes from, for the editor lore. */
	public static String source(UUID id, Perm perm) {
		if (DataStore.find(id).map(d -> d.perms.containsKey(perm.node)).orElse(false)) return "player rule";
		var rank = Ranks.of(id);
		if (rank.perms.containsKey(perm.node)) return "rank " + Text.strip(rank.display);
		var def = Ranks.defaultRank();
		if (def.perms.containsKey(perm.node)) return "default rank";
		return perm.adminOnly ? "built-in: admins only" : "built-in: everyone";
	}

	/** Permission state for a player who may be offline. */
	public static boolean has(MinecraftServer server, UUID id, String name, Perm perm) {
		ServerPlayer online = server.getPlayerList().getPlayer(id);
		if (online != null) return has(online, perm);
		boolean admin = server.getProfilePermissions(new NameAndId(id, name)).hasPermission(Permissions.COMMANDS_ADMIN);
		return resolve(id, admin, perm);
	}

	public static boolean isOverridden(UUID id, Perm perm) {
		return DataStore.find(id).map(d -> d.perms.containsKey(perm.node)).orElse(false);
	}

	public static boolean isAdmin(ServerPlayer player) {
		return player.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
	}

	public static int homeLimit(UUID id) {
		PlayerData d = DataStore.find(id).orElse(null);
		if (d != null && d.homeLimit != null) return d.homeLimit;
		Integer rank = Ranks.homeLimit(id);
		return rank != null ? rank : Config.get().defaultHomeLimit;
	}
}
