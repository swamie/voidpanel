package com.voidpanel.feature;

import com.voidpanel.config.Config;
import com.voidpanel.data.Loc;
import com.voidpanel.util.Msg;
import com.voidpanel.util.Text;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/** /tpa requests. Target -> (requester -> expiry time), newest last. */
public final class Tpa {
	private static final Map<UUID, LinkedHashMap<UUID, Request>> INBOX = new HashMap<>();

	/** here = the target comes to the requester (/tpahere) instead of the other way round. */
	private record Request(long expires, boolean here) {}

	private Tpa() {}

	public static void request(ServerPlayer from, ServerPlayer to) {
		request(from, to, false);
	}

	public static void request(ServerPlayer from, ServerPlayer to, boolean here) {
		if (from == to) {
			Msg.err(from, "You can't teleport to yourself.");
			return;
		}
		if (Ignore.ignores(to, from)) {
			Msg.err(from, name(to) + " isn't accepting requests from you.");
			return;
		}
		var inbox = INBOX.computeIfAbsent(to.getUUID(), k -> new LinkedHashMap<>());
		if (inbox.containsKey(from.getUUID())) {
			Msg.err(from, "You already have a pending request to &f" + name(to) + "&c.");
			return;
		}
		inbox.put(from.getUUID(), new Request(System.currentTimeMillis() + Config.get().tpaExpireSeconds * 1000L, here));

		String fromName = from.getGameProfile().name();
		to.sendSystemMessage(Component.empty());
		to.sendSystemMessage(Msg.prefixed(Component.empty().append(from.getDisplayName()).append(Text.of(here ? " &7wants &fyou &7to teleport &fto them&7." : " &7wants to teleport &fto you&7."))));
		to.sendSystemMessage(Msg.prefixed(Component.empty()
			.append(Msg.button("&a&l[✔ ACCEPT]", "/tpaccept " + fromName, "&aLet " + fromName + " teleport to you"))
			.append(Component.literal("  "))
			.append(Msg.button("&c&l[✘ DENY]", "/tpdeny " + fromName, "&cDecline the request"))
			.append(Text.of("  &8expires in " + Config.get().tpaExpireSeconds + "s"))));
		to.sendSystemMessage(Component.empty());
		Msg.sound(to, SoundEvents.NOTE_BLOCK_PLING, 0.8f, 1.5f);
		Msg.actionBar(to, "&d✉ &fTeleport request from &d" + name(from));

		from.sendSystemMessage(Msg.prefixed(Text.of("&7Request sent to ").append(to.getDisplayName()).append(Text.of("&7. "))
			.append(Msg.button("&c[✘ Cancel]", "/tpacancel " + to.getGameProfile().name(), "&7Withdraw the request"))));
		Msg.sound(from, SoundEvents.NOTE_BLOCK_CHIME, 0.6f, 1.2f);
	}

	/** @param fromName null = most recent request */
	public static void accept(ServerPlayer target, String fromName) {
		Request[] req = new Request[1];
		ServerPlayer from = pop(target, fromName, req);
		if (from == null) return;
		from.sendSystemMessage(Msg.prefixed(Component.empty().append(target.getDisplayName()).append(Text.of(" &aaccepted your request."))));
		MinecraftServer server = target.level().getServer();
		// Whoever is moving stands still for the warmup; the destination is read when it ends.
		ServerPlayer mover = req[0].here() ? target : from;
		ServerPlayer anchor = req[0].here() ? from : target;
		UUID anchorId = anchor.getUUID();
		Msg.ok(target, req[0].here() ? "Accepted! Teleporting you to &f" + name(from) + "&a." : "Accepted! &f" + name(from) + " &awill arrive shortly.");
		Teleports.start(mover, name(anchor), () -> {
			ServerPlayer t = server.getPlayerList().getPlayer(anchorId);
			if (t == null) {
				Msg.err(mover, "They went offline.");
				return null;
			}
			return Loc.of(t);
		});
	}

	public static void deny(ServerPlayer target, String fromName) {
		ServerPlayer from = pop(target, fromName, new Request[1]);
		if (from == null) return;
		Msg.info(target, "Denied the request from &f" + name(from) + "&7.");
		Msg.err(from, name(target) + " denied your teleport request.");
	}

	public static void cancel(ServerPlayer from, ServerPlayer to) {
		var inbox = INBOX.get(to.getUUID());
		if (inbox == null || inbox.remove(from.getUUID()) == null) {
			Msg.err(from, "You have no pending request to that player.");
			return;
		}
		Msg.info(from, "Request cancelled.");
		Msg.info(to, name(from) + " cancelled their teleport request.");
	}

	private static ServerPlayer pop(ServerPlayer target, String fromName, Request[] out) {
		var inbox = INBOX.get(target.getUUID());
		MinecraftServer server = target.level().getServer();
		if (inbox == null || inbox.isEmpty()) {
			Msg.err(target, "You have no pending teleport requests.");
			return null;
		}
		UUID chosen = null;
		if (fromName == null) {
			for (UUID id : inbox.keySet()) chosen = id; // newest
		} else {
			ServerPlayer named = server.getPlayerList().getPlayerByName(fromName);
			if (named != null && inbox.containsKey(named.getUUID())) chosen = named.getUUID();
		}
		if (chosen == null) {
			Msg.err(target, "No request from &f" + fromName + "&c.");
			return null;
		}
		out[0] = inbox.remove(chosen);
		ServerPlayer from = server.getPlayerList().getPlayer(chosen);
		if (from == null) Msg.err(target, "That player is offline.");
		return from;
	}

	public static java.util.List<String> pendingNames(ServerPlayer target) {
		var inbox = INBOX.get(target.getUUID());
		if (inbox == null) return java.util.List.of();
		MinecraftServer server = target.level().getServer();
		return inbox.keySet().stream().map(id -> server.getPlayerList().getPlayer(id)).filter(p -> p != null)
			.map(p -> p.getGameProfile().name()).toList();
	}

	public static void tick(MinecraftServer server) {
		long now = System.currentTimeMillis();
		for (var box : INBOX.entrySet()) {
			Iterator<Map.Entry<UUID, Request>> it = box.getValue().entrySet().iterator();
			while (it.hasNext()) {
				var req = it.next();
				if (req.getValue().expires() > now) continue;
				it.remove();
				ServerPlayer from = server.getPlayerList().getPlayer(req.getKey());
				ServerPlayer to = server.getPlayerList().getPlayer(box.getKey());
				if (from != null) Msg.info(from, "Your teleport request" + (to != null ? " to &f" + name(to) + "&7" : "") + " expired.");
			}
		}
	}

	public static void forget(UUID id) {
		INBOX.remove(id);
		INBOX.values().forEach(m -> m.remove(id));
	}

	private static String name(ServerPlayer p) {
		return p.getDisplayName().getString();
	}
}
