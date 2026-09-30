package com.voidpanel.feature;

import com.voidpanel.config.Config;
import com.voidpanel.data.DataStore;
import com.voidpanel.perm.Perm;
import com.voidpanel.perm.Perms;
import com.voidpanel.util.Msg;
import com.voidpanel.util.Text;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/** Staff-only chat: "#message" or /sc to toggle. */
public final class StaffChat {
	private StaffChat() {}

	public static void send(ServerPlayer sender, String raw) {
		if (raw.isBlank()) return;
		MinecraftServer server = sender.level().getServer();
		Component line = Text.format(Config.get().staffChatFormat, Chat.placeholders(sender, Map.of("message", Text.of(raw))));
		server.sendSystemMessage(line);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (Perms.has(p, Perm.STAFFCHAT)) {
				p.sendSystemMessage(line);
				if (p != sender) Msg.sound(p, SoundEvents.NOTE_BLOCK_BIT, 0.5f, 1.8f);
			}
		}
	}

	public static void toggle(ServerPlayer player) {
		var d = DataStore.get(player.getUUID());
		d.staffChat = !d.staffChat;
		DataStore.markDirty();
		Msg.ok(player, d.staffChat ? "Staff chat on: everything you type goes to staff." : "Staff chat off. Tip: start a message with # to send one.");
	}
}
