package com.voidpanel.feature;

import com.voidpanel.config.Config;
import com.voidpanel.data.DataStore;
import com.voidpanel.perm.Perm;
import com.voidpanel.perm.Perms;
import com.voidpanel.util.Msg;
import com.voidpanel.util.Text;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/** /msg, /r and /socialspy with configurable formats. */
public final class Messages {
	/** Who each player last talked to, for /r. The console is stored as CONSOLE. */
	private static final Map<UUID, UUID> LAST = new HashMap<>();
	private static final UUID CONSOLE = new UUID(0, 0);

	private Messages() {}

	public static void send(CommandSourceStack source, ServerPlayer to, String raw) {
		ServerPlayer from = source.getPlayer();
		MinecraftServer server = source.getServer();
		if (from == to) {
			Msg.err(from, "Talking to yourself? Try someone else.");
			return;
		}
		if (from != null) {
			Afk.activity(from);
			if (Moderation.isMuted(from)) {
				Msg.err(from, "You're muted" + Moderation.muteSuffix(from));
				return;
			}
			if (!Vanish.canSee(from, to)) {
				Msg.err(from, to.getGameProfile().name() + " isn't online.");
				return;
			}
			if (Ignore.ignores(to, from)) {
				Msg.err(from, to.getGameProfile().name() + " isn't accepting messages from you.");
				return;
			}
		}
		Component body = from != null ? ChatRender.render(from, raw, null) : Text.of(raw);
		Component fromName = from != null ? clickable(from) : Text.of("&cServer");
		Component toName = clickable(to);
		Config cfg = Config.get();
		Component prefix = Text.of(cfg.msgPrefix);

		Component outgoing = Text.format(cfg.msgToFormat, Map.of("prefix", prefix, "from", fromName, "to", toName, "message", body));
		Component incoming = Text.format(cfg.msgFromFormat, Map.of("prefix", prefix, "from", fromName, "to", toName, "message", body));

		if (from != null) from.sendSystemMessage(outgoing);
		else source.sendSystemMessage(outgoing);
		to.sendSystemMessage(incoming);
		Msg.sound(to, SoundEvents.NOTE_BLOCK_CHIME, 0.7f, 1.6f);
		Msg.actionBar(to, "&d✉ &fNew message from &d" + (from != null ? from.getDisplayName().getString() : "Server") + " &8| &7/r to reply");

		if (Afk.isAfk(to) && from != null) Msg.info(from, "&f" + to.getGameProfile().name() + " &7is AFK and may not reply right away.");
		UUID fromId = from != null ? from.getUUID() : CONSOLE;
		LAST.put(to.getUUID(), fromId);
		LAST.put(fromId, to.getUUID());

		Component spy = Text.format(cfg.spyFormat, Map.of("from", fromName, "to", toName, "message", body));
		for (ServerPlayer admin : server.getPlayerList().getPlayers()) {
			if (admin == from || admin == to) continue;
			if (DataStore.find(admin.getUUID()).map(d -> d.socialSpy).orElse(false) && Perms.has(admin, Perm.SOCIALSPY)) {
				admin.sendSystemMessage(spy);
			}
		}
		server.sendSystemMessage(spy);
	}

	public static void reply(ServerPlayer from, String raw, CommandSourceStack source) {
		UUID target = LAST.get(from.getUUID());
		if (target == null) {
			Msg.err(from, "Nobody has messaged you yet.");
			return;
		}
		ServerPlayer to = source.getServer().getPlayerList().getPlayer(target);
		if (to == null) {
			Msg.err(from, "That player is offline.");
			return;
		}
		send(source, to, raw);
	}

	public static void toggleSpy(ServerPlayer player) {
		var d = DataStore.get(player.getUUID());
		d.socialSpy = !d.socialSpy;
		DataStore.markDirty();
		Msg.ok(player, "Social spy " + (d.socialSpy ? "enabled. You'll see private messages." : "&cdisabled&a."));
	}

	public static void forget(UUID id) {
		LAST.remove(id);
	}

	/** Name that suggests "/msg <name> " when clicked. */
	private static MutableComponent clickable(ServerPlayer p) {
		String name = p.getGameProfile().name();
		return Component.empty().append(p.getDisplayName()).withStyle(s -> s
			.withClickEvent(new ClickEvent.SuggestCommand("/msg " + name + " "))
			.withHoverEvent(new HoverEvent.ShowText(Text.of("&7Click to message &f" + name))));
	}

	public static Component preview(String template, ServerPlayer viewer) {
		Config cfg = Config.get();
		return Text.format(template, Map.of(
			"prefix", Text.of(cfg.msgPrefix),
			"from", viewer.getDisplayName(),
			"to", Component.literal("Steve"),
			"message", Component.literal("hey, wanna trade?")));
	}
}
