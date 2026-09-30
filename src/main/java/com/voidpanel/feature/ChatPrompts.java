package com.voidpanel.feature;

import com.voidpanel.util.Msg;
import com.voidpanel.util.Scheduler;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** "Type it in chat" input for GUIs. The next chat message is captured instead of broadcast. */
public final class ChatPrompts {
	private static final Map<UUID, Prompt> PROMPTS = new HashMap<>();
	private static final long TIMEOUT_MS = 90_000;

	private ChatPrompts() {}

	private record Prompt(Consumer<String> onInput, long expires) {}

	/**
	 * @param current optional existing value, offered as a click-to-edit button
	 */
	public static void ask(ServerPlayer player, String question, String current, Consumer<String> onInput) {
		PROMPTS.put(player.getUUID(), new Prompt(onInput, System.currentTimeMillis() + TIMEOUT_MS));
		Scheduler.next(player::closeContainer);
		player.sendSystemMessage(Component.empty());
		Msg.info(player, question);
		var line = Component.empty().append(Msg.prefixed(Component.empty()));
		if (current != null && !current.isEmpty()) {
			line.append(Msg.suggest("&e[✎ Edit current]", current, "&7Puts the current value in your chat box\n&7so you can tweak it."));
			line.append(Component.literal(" "));
		}
		line.append(Msg.button("&c[✘ Cancel]", "/vpcancel", "&7Stop waiting for input"));
		player.sendSystemMessage(line);
		Msg.actionBar(player, "&e✎ Type your answer in chat &8(or 'cancel')");
	}

	/** @return true if the message was consumed by a prompt */
	public static boolean handle(ServerPlayer player, String message) {
		Prompt prompt = PROMPTS.remove(player.getUUID());
		if (prompt == null) return false;
		if (System.currentTimeMillis() > prompt.expires()) return false;
		if (message.trim().equalsIgnoreCase("cancel")) {
			Msg.info(player, "Cancelled.");
			return true;
		}
		Scheduler.next(() -> prompt.onInput().accept(message.trim()));
		return true;
	}

	public static boolean cancel(ServerPlayer player) {
		return PROMPTS.remove(player.getUUID()) != null;
	}
}
