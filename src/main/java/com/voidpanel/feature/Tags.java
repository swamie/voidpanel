package com.voidpanel.feature;

import com.voidpanel.data.DataStore;
import com.voidpanel.data.DataStore.Tag;
import com.voidpanel.data.PlayerData;
import com.voidpanel.perm.Perm;
import com.voidpanel.perm.Perms;
import com.voidpanel.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Cosmetic chat tags like [Builder] that players pick from the ones they own. */
public final class Tags {
	private Tags() {}

	public static void ensureDefaults() {
		var tags = DataStore.root().tags;
		if (!tags.isEmpty()) return;
		add("heart", "&c❤", "minecraft:red_dye", true);
		add("star", "&e✮", "minecraft:glowstone_dust", true);
		add("builder", "&8[&6Builder&8]", "minecraft:bricks", false);
		add("og", "&8[&#ff5f6dO&#ffc371G&8]", "minecraft:clock", false);
		add("void", "&8[&#8e2de2V&#9b3de6o&#a94deai&#b65dedd&8]", "minecraft:ender_eye", false);
		DataStore.markDirty();
	}

	private static void add(String id, String display, String icon, boolean everyone) {
		Tag t = new Tag();
		t.id = id;
		t.display = display;
		t.icon = icon;
		t.everyone = everyone;
		DataStore.root().tags.put(id, t);
	}

	public static boolean owns(ServerPlayer player, Tag tag) {
		if (tag.everyone || Perms.has(player, Perm.TAGS_ADMIN)) return true;
		return DataStore.get(player.getUUID()).unlockedTags.contains(tag.id);
	}

	public static List<Tag> available(ServerPlayer player) {
		List<Tag> out = new ArrayList<>();
		for (Tag t : DataStore.root().tags.values()) if (owns(player, t)) out.add(t);
		return out;
	}

	/** "[Builder] " as a component, or empty. */
	public static Component of(UUID player) {
		PlayerData d = DataStore.find(player).orElse(null);
		if (d == null || d.tag == null) return Component.empty();
		Tag t = DataStore.root().tags.get(d.tag);
		return t == null ? Component.empty() : Text.of(t.display + "&r ");
	}
}
