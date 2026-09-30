package com.voidpanel.feature;

import com.voidpanel.config.Config;
import com.voidpanel.data.DataStore;
import com.voidpanel.perm.Perm;
import com.voidpanel.perm.Perms;
import com.voidpanel.util.Msg;
import com.voidpanel.util.Text;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/** Hypixel-style chat emotes: type o/ or :shrug: and it becomes unicode. */
public final class Emotes {
	/** trigger -> coloured replacement (& codes). */
	public static final Map<String, String> EMOTES = new LinkedHashMap<>();

	static {
		EMOTES.put("<3", "&c❤");
		EMOTES.put("o/", "&d( ﾟ◡ﾟ)/");
		EMOTES.put("h/", "&eヽ(^◇^*)/");
		EMOTES.put(":star:", "&6✮");
		EMOTES.put(":yes:", "&a✔");
		EMOTES.put(":no:", "&c✖");
		EMOTES.put(":java:", "&b☕");
		EMOTES.put(":arrow:", "&e➜");
		EMOTES.put(":shrug:", "&e¯\\_(ツ)_/¯");
		EMOTES.put(":tableflip:", "&c(╯°□°）╯&f︵ &7┻━┻");
		EMOTES.put(":123:", "&a1&e2&c3");
		EMOTES.put(":totem:", "&b☉&e_&b☉");
		EMOTES.put(":typing:", "&e✎&6...");
		EMOTES.put(":maths:", "&a√&e(&aπ+x&e)&a=&cL");
		EMOTES.put(":snail:", "&e@&a'&e-&a'");
		EMOTES.put(":thinking:", "&6(&a0&6.&ao&c?&6)");
		EMOTES.put(":gimme:", "&b༼つ◕_◕༽つ");
		EMOTES.put(":wizard:", "&b('-')⊃&c━&d☆ﾟ.*･｡ﾟ");
		EMOTES.put(":pvp:", "&e⚔");
		EMOTES.put(":peace:", "&a✌");
		EMOTES.put(":oof:", "&c&lOOF");
		EMOTES.put(":puffer:", "&e<('O')>");
		EMOTES.put(":yey:", "&aヽ (◕◡◕) ﾉ");
		EMOTES.put(":cat:", "&b= &e^● ⋏ ●^ &b=");
		EMOTES.put(":dab:", "&d<o/");
		EMOTES.put(":dj:", "&9ヽ(⌐■_■)ノ♬");
		EMOTES.put(":snow:", "&b☃");
		EMOTES.put(":sloth:", "&6(・⊝・)");
		EMOTES.put(":cute:", "&e(&a✿&e◠‿◠)");
		EMOTES.put(":dog:", "&6(ᵔᴥᵔ)");
		EMOTES.put(":skull:", "&7☠");
		EMOTES.put(":sun:", "&e☀");
		EMOTES.put(":music:", "&d♫");
	}

	/** Short emotes only match as whole words so URLs and "photo/" aren't mangled. */
	private static final Pattern PATTERN;

	static {
		StringBuilder sb = new StringBuilder();
		for (String key : EMOTES.keySet()) {
			if (!sb.isEmpty()) sb.append('|');
			String q = Pattern.quote(key);
			sb.append(key.startsWith(":") ? q : "(?<!\\S)" + q + "(?!\\S)");
		}
		PATTERN = Pattern.compile(sb.toString());
	}

	private Emotes() {}

	public static boolean active(ServerPlayer player) {
		return Config.get().emotesEnabled && Perms.has(player, Perm.EMOTES)
			&& !DataStore.find(player.getUUID()).map(d -> d.emotesOff).orElse(false);
	}

	/**
	 * Turn a raw message into a component, applying & colours (if allowed) and emotes (if active).
	 */
	public static MutableComponent render(ServerPlayer sender, String raw) {
		boolean colours = Perms.has(sender, Perm.CHAT_COLOR);
		if (!active(sender)) return colours ? Text.of(raw) : Component.literal(raw);
		MutableComponent out = Component.empty();
		Matcher m = PATTERN.matcher(raw);
		int last = 0;
		while (m.find()) {
			String before = raw.substring(last, m.start());
			if (!before.isEmpty()) out.append(colours ? Text.of(before) : Component.literal(before));
			out.append(Text.of(EMOTES.get(m.group())));
			last = m.end();
		}
		String rest = raw.substring(last);
		if (!rest.isEmpty()) out.append(colours ? Text.of(rest) : Component.literal(rest));
		return out;
	}

	/** The /emotes list: every emote, click to put it in your chat box. */
	public static void list(ServerPlayer player) {
		player.sendSystemMessage(Component.empty());
		Msg.info(player, "&d&lEmotes " + (active(player) ? "&a(on)" : "&c(off)") + " &8- click one to use it");
		MutableComponent line = Msg.prefixed(Component.empty());
		int n = 0;
		for (var e : EMOTES.entrySet()) {
			line.append(Msg.suggest("&7" + e.getKey() + " &8→ " + e.getValue() + "   ", e.getKey(), "&7Type &f" + e.getKey() + "&7 in chat"));
			if (++n % 3 == 0) {
				player.sendSystemMessage(line);
				line = Msg.prefixed(Component.empty());
			}
		}
		if (n % 3 != 0) player.sendSystemMessage(line);
		player.sendSystemMessage(Msg.prefixed(Msg.button("&e[Toggle emotes for me]", "/emotes toggle", "&7Turn emote replacement on/off just for you")));
	}

	public static void toggle(ServerPlayer player) {
		var data = DataStore.get(player.getUUID());
		data.emotesOff = !data.emotesOff;
		DataStore.markDirty();
		Msg.ok(player, "Chat emotes " + (data.emotesOff ? "&cdisabled" : "enabled") + " &afor you.");
	}
}
