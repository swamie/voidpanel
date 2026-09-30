package com.voidpanel.feature;

import com.voidpanel.config.Config;
import com.voidpanel.data.DataStore;
import com.voidpanel.data.PlayerData;
import com.voidpanel.perm.Perm;
import com.voidpanel.perm.Perms;
import com.voidpanel.util.Msg;
import com.voidpanel.util.Text;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/** Chat format, join/leave messages and nicknames. */
public final class Chat {
	private static final Pattern NICK_CHARS = Pattern.compile("[A-Za-z0-9_]+");

	private Chat() {}

	// ---------------------------------------------------------------- chat

	/** Replaces vanilla "<name> msg" with the configured format. Returns false to cancel vanilla. */
	public static boolean onChat(PlayerChatMessage message, ServerPlayer sender) {
		String raw = message.signedContent();
		if (ChatPrompts.handle(sender, raw)) return false;
		MinecraftServer server = sender.level().getServer();
		Afk.activity(sender);

		if (Moderation.isMuted(sender)) {
			Msg.err(sender, "You're muted" + Moderation.muteSuffix(sender));
			return false;
		}
		PlayerData data = DataStore.get(sender.getUUID());
		if (Perms.has(sender, Perm.STAFFCHAT) && (raw.startsWith("#") || data.staffChat)) {
			StaffChat.send(sender, raw.startsWith("#") ? raw.substring(1).trim() : raw);
			return false;
		}

		server.sendSystemMessage(line(sender, ChatRender.render(sender, raw, null)));
		for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
			if (Ignore.ignores(viewer, sender)) continue;
			boolean mentioned = viewer != sender && ChatRender.mentions(raw, viewer);
			viewer.sendSystemMessage(line(sender, ChatRender.render(sender, raw, viewer == sender ? null : viewer)));
			if (mentioned) {
				Msg.sound(viewer, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 0.6f);
				Msg.actionBar(viewer, "&e&l@ &f" + sender.getDisplayName().getString() + " &7mentioned you");
			}
		}
		return false;
	}

	private static Component line(ServerPlayer sender, Component body) {
		return Text.format(Config.get().chatFormat, placeholders(sender, Map.of("message", body)));
	}

	/** {rank} {tag} {display} {name} plus any extras. */
	public static Map<String, Component> placeholders(ServerPlayer player, Map<String, Component> extra) {
		Map<String, Component> map = new java.util.HashMap<>(extra);
		map.put("rank", Ranks.chatPrefix(player.getUUID()));
		map.put("tag", Tags.of(player.getUUID()));
		map.put("display", Ranks.coloredName(player));
		map.put("name", Component.literal(player.getGameProfile().name()));
		return map;
	}

	/** Swallow vanilla's yellow join/leave lines; we send our own. */
	public static boolean allowGameMessage(Component message) {
		if (message.getContents() instanceof TranslatableContents t) {
			String key = t.getKey();
			return !(key.startsWith("multiplayer.player.joined") || key.equals("multiplayer.player.left"));
		}
		return true;
	}

	public static Component preview(String template, ServerPlayer viewer) {
		return Text.format(template, placeholders(viewer, Map.of(
			"message", Component.literal("Hello world! o/"),
			"count", Component.literal("42"))));
	}

	// ---------------------------------------------------------------- join / leave

	public static void onJoin(ServerPlayer player, MinecraftServer server) {
		boolean first = !DataStore.has(player.getUUID());
		PlayerData data = DataStore.get(player.getUUID());
		data.name = player.getGameProfile().name();
		data.lastIp = player.getIpAddress();
		data.lastSeen = System.currentTimeMillis();
		if (first) data.firstJoin = data.lastSeen;
		DataStore.markDirty();

		if (!data.vanished) broadcastJoin(player, server, first);
		Vanish.onJoin(player, server);

		// Everyone's tab names (ranks, nicknames, AFK) to this player, and theirs to everyone.
		refreshTabName(player, server);
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (other != player) player.connection.send(tabUpdate(other));
		}

		if (first && Config.get().sendNewPlayersToSpawn && DataStore.root().spawn != null) {
			var spawn = DataStore.root().spawn;
			var level = spawn.level(server);
			if (level != null) player.teleportTo(level, spawn.x, spawn.y, spawn.z, java.util.Set.of(), spawn.yaw, spawn.pitch, true);
		}
		if (Daily.canClaim(player)) {
			player.sendSystemMessage(Msg.prefixed(Text.of("&eYour daily reward is ready! ")
				.append(Msg.button("&a&l[Claim]", "/daily", "&7Open /daily"))));
		}
	}

	public static void broadcastJoin(ServerPlayer player, MinecraftServer server, boolean first) {
		String template = first ? Config.get().firstJoinFormat : Config.get().joinFormat;
		Component line = Text.format(template, placeholders(player, Map.of(
			"count", Component.literal(String.valueOf(DataStore.root().players.size())))));
		server.getPlayerList().broadcastSystemMessage(line, false);
	}

	public static void broadcastLeave(ServerPlayer player, MinecraftServer server) {
		Component line = Text.format(Config.get().leaveFormat, placeholders(player, Map.of()));
		server.getPlayerList().broadcastSystemMessage(line, false);
	}

	public static void onLeave(ServerPlayer player, MinecraftServer server) {
		PlayerData data = DataStore.get(player.getUUID());
		data.lastSeen = System.currentTimeMillis();
		DataStore.markDirty();
		if (!data.vanished) broadcastLeave(player, server);
	}

	// ---------------------------------------------------------------- tab list

	/** What the tab list shows: [RANK] [tag] Name [AFK] */
	public static Component tabName(ServerPlayer player) {
		var out = Component.empty().append(Ranks.tabPrefix(player.getUUID())).append(Ranks.coloredName(player));
		if (Afk.isAfk(player)) out.append(Text.of(" &7&o[AFK]"));
		if (DataStore.find(player.getUUID()).map(d -> d.vanished).orElse(false)) out.append(Text.of(" &b[V]"));
		return out;
	}

	private static ClientboundPlayerInfoUpdatePacket tabUpdate(ServerPlayer player) {
		return new ClientboundPlayerInfoUpdatePacket(java.util.EnumSet.of(
			ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
			ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER), java.util.List.of(player));
	}

	// ---------------------------------------------------------------- nicknames

	/** The nickname component used by the display-name mixin, or null. */
	public static Component nick(ServerPlayer player) {
		String nick = DataStore.find(player.getUUID()).map(d -> d.nick).orElse(null);
		return nick == null ? null : Text.of(nick);
	}

	public static void setNick(ServerPlayer actor, ServerPlayer target, String nick) {
		PlayerData data = DataStore.get(target.getUUID());
		boolean self = actor == target;
		if (nick == null || nick.equalsIgnoreCase("off") || nick.equalsIgnoreCase("reset")) {
			data.nick = null;
			DataStore.markDirty();
			refreshTabName(target, target.level().getServer());
			Msg.ok(actor, self ? "Nickname removed." : "Removed " + target.getGameProfile().name() + "'s nickname.");
			return;
		}
		if (nick.contains("&") && !Perms.has(actor, Perm.NICK_COLOR)) {
			Msg.err(actor, "You don't have permission to use colours in nicknames.");
			return;
		}
		// Only colour/format codes, no obfuscation.
		if (nick.toLowerCase().contains("&k")) {
			Msg.err(actor, "Obfuscated nicknames aren't allowed.");
			return;
		}
		String visible = Text.strip(nick);
		Config cfg = Config.get();
		if (visible.length() < cfg.nickMinLength || visible.length() > cfg.nickMaxLength || !NICK_CHARS.matcher(visible).matches()) {
			Msg.err(actor, "Nicknames must be " + cfg.nickMinLength + "-" + cfg.nickMaxLength + " letters, numbers or _.");
			return;
		}
		MinecraftServer server = target.level().getServer();
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (other != target && other.getGameProfile().name().equalsIgnoreCase(visible)) {
				Msg.err(actor, "That's another player's real name.");
				return;
			}
		}
		for (var e : DataStore.root().players.entrySet()) {
			PlayerData d = e.getValue();
			if (d != data && d.nick != null && Text.strip(d.nick).equalsIgnoreCase(visible)) {
				Msg.err(actor, "Someone else is already using that nickname.");
				return;
			}
		}
		data.nick = nick;
		DataStore.markDirty();
		refreshTabName(target, server);
		actor.sendSystemMessage(Msg.prefixed(Text.of(self ? "&aYour nickname is now " : "&a" + target.getGameProfile().name() + " is now ").append(Text.of(data.nick))));
		if (!self) target.sendSystemMessage(Msg.prefixed(Text.of("&aYour nickname was set to ").append(Text.of(data.nick))));
	}

	public static void refreshTabName(ServerPlayer player, MinecraftServer server) {
		server.getPlayerList().broadcastAll(tabUpdate(player));
	}
}
