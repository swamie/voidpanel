package com.voidpanel.feature;

import com.voidpanel.data.DataStore;
import com.voidpanel.perm.Perm;
import com.voidpanel.perm.Perms;
import com.voidpanel.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Turns a raw chat message into a component: [item], @mentions, emotes and colours. */
public final class ChatRender {
	private static final Pattern ITEM = Pattern.compile("(?i)\\[(item|i|hand)]");

	private ChatRender() {}

	/**
	 * @param viewer the player this copy is for (mentions of them get highlighted), or null
	 */
	public static MutableComponent render(ServerPlayer sender, String raw, ServerPlayer viewer) {
		boolean items = Perms.has(sender, Perm.CHAT_ITEM);
		List<String> names = viewer == null ? List.of() : mentionNames(viewer);
		MutableComponent out = Component.empty();

		// Build one alternation of everything special, then walk the message.
		List<String> parts = new ArrayList<>();
		if (items) parts.add(ITEM.pattern());
		for (String n : names) parts.add("(?i)(?<![A-Za-z0-9_])@?" + Pattern.quote(n) + "(?![A-Za-z0-9_])");
		if (parts.isEmpty()) return Emotes.render(sender, raw);

		Matcher m = Pattern.compile(String.join("|", parts)).matcher(raw);
		int last = 0;
		boolean itemUsed = false;
		while (m.find()) {
			if (m.start() > last) out.append(Emotes.render(sender, raw.substring(last, m.start())));
			String hit = m.group();
			if (items && ITEM.matcher(hit).matches()) {
				ItemStack held = sender.getMainHandItem();
				if (held.isEmpty() || itemUsed) out.append(Component.literal(hit));
				else out.append(held.getDisplayName());
				itemUsed = true;
			} else {
				out.append(Text.of("&e&l" + hit));
			}
			last = m.end();
		}
		if (last < raw.length()) out.append(Emotes.render(sender, raw.substring(last)));
		return out;
	}

	/** Does this message mention the viewer (name or nickname)? */
	public static boolean mentions(String raw, ServerPlayer viewer) {
		for (String n : mentionNames(viewer)) {
			if (Pattern.compile("(?i)(?<![A-Za-z0-9_])@?" + Pattern.quote(n) + "(?![A-Za-z0-9_])").matcher(raw).find()) return true;
		}
		return false;
	}

	private static List<String> mentionNames(ServerPlayer viewer) {
		List<String> names = new ArrayList<>();
		names.add(viewer.getGameProfile().name());
		String nick = DataStore.find(viewer.getUUID()).map(d -> d.nick).orElse(null);
		if (nick != null) {
			String plain = Text.strip(nick);
			if (plain.length() >= 3 && !plain.equalsIgnoreCase(viewer.getGameProfile().name())) names.add(plain);
		}
		return names;
	}
}
