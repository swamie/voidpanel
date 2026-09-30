package com.voidpanel.feature;

import com.voidpanel.config.Config;
import com.voidpanel.perm.Perm;
import com.voidpanel.perm.Perms;
import com.voidpanel.util.Msg;
import com.voidpanel.util.Text;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;

/** /afk plus automatic AFK after being idle, with an optional kick. */
public final class Afk {
	/** Player -> when they went AFK (millis). */
	private static final Map<UUID, Long> AFK = new HashMap<>();

	private Afk() {}

	public static boolean isAfk(ServerPlayer player) {
		return AFK.containsKey(player.getUUID());
	}

	public static void toggle(ServerPlayer player) {
		set(player, !isAfk(player));
	}

	public static void set(ServerPlayer player, boolean afk) {
		if (afk == isAfk(player)) return;
		MinecraftServer server = player.level().getServer();
		if (afk) AFK.put(player.getUUID(), Util.getMillis());
		else AFK.remove(player.getUUID());
		Chat.refreshTabName(player, server);
		if (!Vanish.isVanished(player)) {
			server.getPlayerList().broadcastSystemMessage(Text.of("&7* ").append(Ranks.coloredName(player))
				.append(Text.of(afk ? " &7is now AFK" : " &7is no longer AFK")), false);
		}
	}

	/** Something the player did (chat, command) that proves they're back. */
	public static void activity(ServerPlayer player) {
		Long since = AFK.get(player.getUUID());
		if (since != null && Util.getMillis() - since > 1500) set(player, false);
	}

	public static void forget(UUID id) {
		AFK.remove(id);
	}

	public static void tick(MinecraftServer server) {
		Config cfg = Config.get();
		long now = Util.getMillis();
		for (ServerPlayer p : List.copyOf(server.getPlayerList().getPlayers())) {
			long idle = now - p.getLastActionTime();
			Long since = AFK.get(p.getUUID());
			if (since != null) {
				// Moved/acted after going AFK (with a grace period so /afk itself doesn't count).
				if (p.getLastActionTime() > since + 1500) {
					set(p, false);
					continue;
				}
				if (cfg.afkKickMinutes > 0 && idle > cfg.afkKickMinutes * 60_000L && !Perms.has(p, Perm.AFK_BYPASS)) {
					p.connection.disconnect(Text.of("&e&lYou were kicked for being AFK\n\n&7Idle for " + cfg.afkKickMinutes + " minutes."));
				} else if ((now - since) / 1000 % 30 == 0) {
					Msg.actionBar(p, "&7&oYou are AFK");
				}
			} else if (cfg.afkMinutes > 0 && idle > cfg.afkMinutes * 60_000L) {
				set(p, true);
			}
		}
	}
}
