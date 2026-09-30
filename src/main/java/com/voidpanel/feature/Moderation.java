package com.voidpanel.feature;

import com.voidpanel.data.DataStore;
import com.voidpanel.util.Msg;
import com.voidpanel.util.Text;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.server.players.UserWhiteListEntry;
import net.minecraft.util.Util;

public final class Moderation {
	private static final Pattern IP = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$|^[0-9a-fA-F:]+:[0-9a-fA-F:]*$");

	private Moderation() {}

	/**
	 * Resolve a player name to a profile: online players first, then VoidPanel's records,
	 * then the server's name cache / Mojang (off-thread). Calls back on the server thread.
	 */
	public static void resolve(MinecraftServer server, String name, Consumer<Optional<NameAndId>> callback) {
		ServerPlayer online = server.getPlayerList().getPlayerByName(name);
		if (online != null) {
			callback.accept(Optional.of(new NameAndId(online.getGameProfile())));
			return;
		}
		var known = DataStore.byName(name);
		if (known.isPresent()) {
			callback.accept(Optional.of(new NameAndId(known.get().getKey(), known.get().getValue().name)));
			return;
		}
		CompletableFuture.supplyAsync(() -> server.services().nameToIdCache().get(name), Util.ioPool())
			.exceptionally(t -> Optional.empty())
			.thenAccept(result -> server.execute(() -> callback.accept(result)));
	}

	public static String sourceName(CommandSourceStack source) {
		return source.getTextName();
	}

	// ---------------------------------------------------------------- bans

	public static void ban(MinecraftServer server, String by, NameAndId target, String reason, Consumer<String> feedback) {
		var bans = server.getPlayerList().getBans();
		if (bans.isBanned(target)) {
			feedback.accept("&c" + target.name() + " is already banned.");
			return;
		}
		String why = reason == null || reason.isBlank() ? "Banned by an operator." : reason;
		bans.add(new UserBanListEntry(target, new Date(), by, null, why));
		record(target, "ban", why, by, 0);
		ServerPlayer online = server.getPlayerList().getPlayer(target.id());
		if (online != null) online.connection.disconnect(kickScreen("&c&lYou have been banned", why, by));
		server.getPlayerList().broadcastSystemMessage(Msg.prefixed(Text.of("&c" + target.name() + " &7was banned by &f" + by + " &8(" + why + ")")), false);
		feedback.accept("&aBanned " + target.name() + ".");
	}

	public static boolean unban(MinecraftServer server, NameAndId target) {
		var bans = server.getPlayerList().getBans();
		if (!bans.isBanned(target)) return false;
		bans.remove(target);
		DataStore.find(target.id()).ifPresent(d -> d.punishments.stream().filter(p -> p.type.equals("ban") && !p.revoked).findFirst().ifPresent(p -> p.revoked = true));
		DataStore.markDirty();
		return true;
	}

	public static List<UserBanListEntry> bans(MinecraftServer server) {
		return List.copyOf(server.getPlayerList().getBans().getEntries());
	}

	// ---------------------------------------------------------------- IP blacklist

	public static boolean looksLikeIp(String s) {
		return IP.matcher(s).matches();
	}

	/** Player name -> IP (online address, or the last one VoidPanel saw). */
	public static Optional<String> ipOf(MinecraftServer server, String nameOrIp) {
		if (looksLikeIp(nameOrIp)) return Optional.of(nameOrIp);
		ServerPlayer online = server.getPlayerList().getPlayerByName(nameOrIp);
		if (online != null) return Optional.of(online.getIpAddress());
		return DataStore.byName(nameOrIp).map(e -> e.getValue().lastIp);
	}

	public static void blacklist(MinecraftServer server, String by, String ip, String label, String reason, Consumer<String> feedback) {
		var ipBans = server.getPlayerList().getIpBans();
		if (ipBans.isBanned(ip)) {
			feedback.accept("&cThat IP is already blacklisted.");
			return;
		}
		String why = reason == null || reason.isBlank() ? "Blacklisted by an operator." : reason;
		ipBans.add(new IpBanListEntry(ip, new Date(), by, null, why));
		int kicked = 0;
		for (ServerPlayer p : List.copyOf(server.getPlayerList().getPlayers())) {
			if (ip.equals(p.getIpAddress())) {
				p.connection.disconnect(kickScreen("&4&lYour IP has been blacklisted", why, by));
				kicked++;
			}
		}
		feedback.accept("&aBlacklisted " + label + (kicked > 0 ? " &7(kicked " + kicked + " player" + (kicked == 1 ? "" : "s") + ")" : "") + ".");
	}

	public static boolean unblacklist(MinecraftServer server, String ip) {
		var ipBans = server.getPlayerList().getIpBans();
		if (!ipBans.isBanned(ip)) return false;
		ipBans.remove(ip);
		return true;
	}

	public static List<IpBanListEntry> ipBans(MinecraftServer server) {
		return List.copyOf(server.getPlayerList().getIpBans().getEntries());
	}

	/** Which known players last used this IP (for labelling blacklist entries). */
	public static String playersOnIp(String ip) {
		return DataStore.root().players.values().stream()
			.filter(d -> ip.equals(d.lastIp) && d.name != null)
			.map(d -> d.name).reduce((a, b) -> a + ", " + b).orElse("unknown");
	}

	// ---------------------------------------------------------------- whitelist

	public static boolean isWhitelisted(MinecraftServer server, NameAndId who) {
		return server.getPlayerList().getWhiteList().isWhiteListed(who);
	}

	public static void whitelistAdd(MinecraftServer server, NameAndId who) {
		server.getPlayerList().getWhiteList().add(new UserWhiteListEntry(who));
		DataStore.root().denied.removeIf(d -> d.uuid.equals(who.id().toString()));
		DataStore.markDirty();
	}

	public static void whitelistRemove(MinecraftServer server, NameAndId who) {
		server.getPlayerList().getWhiteList().remove(who);
		server.kickUnlistedPlayers();
	}

	// ---------------------------------------------------------------- punishments

	/** "30m", "2h", "1d", "1w", "perm" -> millis (0 = permanent). -1 if it can't be read. */
	public static long parseDuration(String s) {
		if (s == null) return -1;
		s = s.trim().toLowerCase();
		if (s.equals("perm") || s.equals("permanent") || s.equals("forever")) return 0;
		var m = java.util.regex.Pattern.compile("^(\\d+)([smhdw])$").matcher(s);
		if (!m.matches()) return -1;
		long n = Long.parseLong(m.group(1));
		return switch (m.group(2)) {
			case "s" -> n * 1000L;
			case "m" -> n * 60_000L;
			case "h" -> n * 3_600_000L;
			case "d" -> n * 86_400_000L;
			default -> n * 604_800_000L;
		};
	}

	public static String formatDuration(long millis) {
		if (millis <= 0) return "permanent";
		long s = millis / 1000;
		if (s < 60) return s + "s";
		if (s < 3600) return (s / 60) + "m";
		if (s < 86400) return (s / 3600) + "h" + (s % 3600 >= 60 ? " " + (s % 3600) / 60 + "m" : "");
		return (s / 86400) + "d" + (s % 86400 >= 3600 ? " " + (s % 86400) / 3600 + "h" : "");
	}

	private static void record(NameAndId target, String type, String reason, String by, long until) {
		var d = DataStore.get(target.id());
		if (d.name == null) d.name = target.name();
		var p = new com.voidpanel.data.PlayerData.Punishment();
		p.type = type;
		p.reason = reason;
		p.by = by;
		p.time = System.currentTimeMillis();
		p.until = until;
		d.punishments.add(0, p);
		DataStore.markDirty();
	}

	public static boolean isMuted(ServerPlayer player) {
		var d = DataStore.find(player.getUUID()).orElse(null);
		if (d == null || d.mutedUntil == 0) return false;
		if (d.mutedUntil > 0 && d.mutedUntil < System.currentTimeMillis()) {
			d.mutedUntil = 0;
			DataStore.markDirty();
			return false;
		}
		return true;
	}

	public static String muteSuffix(ServerPlayer player) {
		var d = DataStore.get(player.getUUID());
		String time = d.mutedUntil < 0 ? " permanently" : " for " + formatDuration(d.mutedUntil - System.currentTimeMillis());
		return time + (d.muteReason != null ? " &7(" + d.muteReason + ")" : "") + "&c.";
	}

	/** @param duration millis, 0 = permanent */
	public static void mute(MinecraftServer server, String by, NameAndId target, long duration, String reason, Consumer<String> feedback) {
		var d = DataStore.get(target.id());
		d.mutedUntil = duration <= 0 ? -1 : System.currentTimeMillis() + duration;
		d.muteReason = reason;
		record(target, "mute", reason, by, duration <= 0 ? 0 : d.mutedUntil);
		ServerPlayer online = server.getPlayerList().getPlayer(target.id());
		if (online != null) online.sendSystemMessage(Msg.prefixed(Text.of("&cYou have been muted " + (duration <= 0 ? "permanently" : "for " + formatDuration(duration))
			+ " &7(" + reason + ")")));
		broadcastStaff(server, "&c" + target.name() + " &7was muted by &f" + by + " &8(" + formatDuration(duration) + ", " + reason + ")");
		feedback.accept("&aMuted " + target.name() + ".");
	}

	public static boolean unmute(MinecraftServer server, NameAndId target) {
		var d = DataStore.get(target.id());
		if (d.mutedUntil == 0) return false;
		d.mutedUntil = 0;
		d.muteReason = null;
		d.punishments.stream().filter(p -> p.type.equals("mute") && !p.revoked).findFirst().ifPresent(p -> p.revoked = true);
		DataStore.markDirty();
		ServerPlayer online = server.getPlayerList().getPlayer(target.id());
		if (online != null) Msg.ok(online, "You've been unmuted.");
		return true;
	}

	public static void kick(MinecraftServer server, String by, ServerPlayer target, String reason, Consumer<String> feedback) {
		record(new NameAndId(target.getGameProfile()), "kick", reason, by, 0);
		target.connection.disconnect(kickScreen("&6&lYou were kicked", reason, by));
		broadcastStaff(server, "&6" + target.getGameProfile().name() + " &7was kicked by &f" + by + " &8(" + reason + ")");
		feedback.accept("&aKicked " + target.getGameProfile().name() + ".");
	}

	public static void warn(MinecraftServer server, String by, NameAndId target, String reason, Consumer<String> feedback) {
		record(target, "warn", reason, by, 0);
		ServerPlayer online = server.getPlayerList().getPlayer(target.id());
		if (online != null) {
			online.sendSystemMessage(Component.empty());
			online.sendSystemMessage(Msg.prefixed(Text.of("&e&l⚠ WARNING &7from &f" + by + "&7: &e" + reason)));
			online.sendSystemMessage(Component.empty());
			Msg.sound(online, net.minecraft.sounds.SoundEvents.ANVIL_LAND, 0.5f, 1.2f);
		}
		broadcastStaff(server, "&e" + target.name() + " &7was warned by &f" + by + " &8(" + reason + ")");
		feedback.accept("&aWarned " + target.name() + ".");
	}

	/** Ban with an optional expiry (duration 0 = permanent). */
	public static void tempban(MinecraftServer server, String by, NameAndId target, long duration, String reason, Consumer<String> feedback) {
		var bans = server.getPlayerList().getBans();
		if (bans.isBanned(target)) bans.remove(target);
		Date until = duration <= 0 ? null : new Date(System.currentTimeMillis() + duration);
		bans.add(new UserBanListEntry(target, new Date(), by, until, reason));
		record(target, "ban", reason, by, until == null ? 0 : until.getTime());
		ServerPlayer online = server.getPlayerList().getPlayer(target.id());
		String length = duration <= 0 ? "permanently" : "for " + formatDuration(duration);
		if (online != null) online.connection.disconnect(kickScreen("&c&lYou have been banned " + length, reason, by));
		server.getPlayerList().broadcastSystemMessage(Msg.prefixed(Text.of("&c" + target.name() + " &7was banned " + length + " by &f" + by + " &8(" + reason + ")")), false);
		feedback.accept("&aBanned " + target.name() + " " + length + ".");
	}

	private static void broadcastStaff(MinecraftServer server, String legacy) {
		Component line = Msg.prefixed(Text.of(legacy));
		server.sendSystemMessage(line);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (com.voidpanel.perm.Perms.has(p, com.voidpanel.perm.Perm.PUNISH)) p.sendSystemMessage(line);
		}
	}

	public static Component kickScreen(String title, String reason, String by) {
		return Text.of(title + "\n\n&7Reason: &f" + reason + "\n&7By: &f" + by + "\n\n&8VoidPanel");
	}
}
