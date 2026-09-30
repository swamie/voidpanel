package com.voidpanel.feature;

import com.voidpanel.data.DataStore;
import com.voidpanel.data.DataStore.Rank;
import com.voidpanel.data.PlayerData;
import com.voidpanel.perm.Perm;
import com.voidpanel.util.Msg;
import com.voidpanel.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Ranks: a prefix, a name colour, a set of permissions and an optional playtime
 * auto-promotion. Every player without a rank is in the default rank.
 */
public final class Ranks {
	private Ranks() {}

	/** First start: a sensible ladder that shows off every feature. */
	public static void ensureDefaults() {
		var ranks = DataStore.root().ranks;
		if (!ranks.isEmpty()) {
			if (!ranks.containsKey(DataStore.root().defaultRank)) DataStore.root().defaultRank = ranks.keySet().iterator().next();
			return;
		}
		put(new Rank("member", "&7Member", "", "&7", "minecraft:iron_ingot", 0, 0));
		put(new Rank("regular", "&aRegular", "&8[&aRegular&8] ", "&f", "minecraft:emerald", 5, 10));
		Rank vip = put(new Rank("vip", "&b&lVIP", "&8[&b&lVIP&8] ", "&b", "minecraft:diamond", 10, 0));
		vip.homeLimit = 5;
		vip.perms.put(Perm.CHAT_COLOR.node, true);
		vip.perms.put(Perm.NICK_COLOR.node, true);
		Rank mvp = put(new Rank("mvp", "&6&lMVP&c&l+", "&8[&6&lMVP&c&l+&8] ", "&6", "minecraft:gold_block", 20, 0));
		mvp.homeLimit = 10;
		mvp.perms.put(Perm.CHAT_COLOR.node, true);
		mvp.perms.put(Perm.NICK_COLOR.node, true);
		mvp.perms.put(Perm.AFK_BYPASS.node, true);
		Rank mod = put(new Rank("mod", "&2&lMOD", "&8[&2&lMOD&8] ", "&a", "minecraft:iron_sword", 50, 0));
		mod.homeLimit = 10;
		for (Perm p : List.of(Perm.PUNISH, Perm.BAN, Perm.WHITELIST, Perm.INVSEE, Perm.ENDERSEE, Perm.VANISH, Perm.STAFFCHAT,
			Perm.SOCIALSPY, Perm.CHAT_COLOR, Perm.NICK_COLOR, Perm.AFK_BYPASS, Perm.GRAVES_OTHERS, Perm.PETS_OTHERS)) {
			mod.perms.put(p.node, true);
		}
		Rank admin = put(new Rank("admin", "&c&lADMIN", "&8[&c&lADMIN&8] ", "&c", "minecraft:netherite_block", 100, 0));
		admin.homeLimit = 25;
		for (Perm p : Perm.values()) admin.perms.put(p.node, true);
		DataStore.root().defaultRank = "member";
		DataStore.markDirty();
	}

	private static Rank put(Rank r) {
		DataStore.root().ranks.put(r.id, r);
		return r;
	}

	public static Rank defaultRank() {
		var root = DataStore.root();
		Rank r = root.ranks.get(root.defaultRank);
		if (r == null) {
			ensureDefaults();
			r = root.ranks.values().iterator().next();
			root.defaultRank = r.id;
		}
		return r;
	}

	public static Rank of(UUID player) {
		String id = DataStore.find(player).map(d -> d.rank).orElse(null);
		Rank r = id == null ? null : DataStore.root().ranks.get(id);
		return r != null ? r : defaultRank();
	}

	public static Rank get(String id) {
		return id == null ? null : DataStore.root().ranks.get(id.toLowerCase());
	}

	/** Ranks, most important first. */
	public static List<Rank> sorted() {
		List<Rank> list = new ArrayList<>(DataStore.root().ranks.values());
		list.sort(Comparator.comparingInt((Rank r) -> r.weight).reversed());
		return list;
	}

	/** Rank permission, falling back to the default rank. null = neither says anything. */
	public static Boolean perm(UUID player, Perm perm) {
		Rank r = of(player);
		Boolean v = r.perms.get(perm.node);
		if (v != null) return v;
		Rank def = defaultRank();
		return r == def ? null : def.perms.get(perm.node);
	}

	public static Integer homeLimit(UUID player) {
		Rank r = of(player);
		if (r.homeLimit != null) return r.homeLimit;
		return defaultRank().homeLimit;
	}

	public static void set(MinecraftServer server, UUID player, String playerName, Rank rank, String by) {
		set(server, player, playerName, rank, by, 0);
	}

	/**
	 * @param duration millis; 0 = permanent. A timed rank remembers the rank underneath it
	 *                 and puts the player back when it runs out.
	 */
	public static void set(MinecraftServer server, UUID player, String playerName, Rank rank, String by, long duration) {
		PlayerData data = DataStore.get(player);
		if (data.name == null) data.name = playerName;
		String newId = rank == defaultRank() ? null : rank.id;
		if (duration > 0) {
			// Stacking timed ranks keeps the original rank to return to.
			if (data.rankExpires == 0) data.previousRank = data.rank;
			data.rankExpires = System.currentTimeMillis() + duration;
		} else {
			data.rankExpires = 0;
			data.previousRank = null;
		}
		data.rank = newId;
		DataStore.markDirty();
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online != null) {
			refresh(online, server);
			String length = duration > 0 ? " &7for &f" + Moderation.formatDuration(duration) : "";
			online.sendSystemMessage(Msg.prefixed(Text.of("&7Your rank is now ").append(Text.of(rank.display))
				.append(Text.of(length + (by != null ? " &8(set by " + by + ")" : "")))));
			Msg.sound(online, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.2f);
		}
	}

	private static void refresh(ServerPlayer online, MinecraftServer server) {
		server.getCommands().sendCommands(online);
		Chat.refreshTabName(online, server);
	}

	/** Put players whose timed rank ran out back on their old rank. */
	public static void checkExpiry(MinecraftServer server) {
		long now = System.currentTimeMillis();
		for (var e : DataStore.root().players.entrySet()) {
			PlayerData d = e.getValue();
			if (d.rankExpires == 0 || d.rankExpires > now) continue;
			Rank expired = get(d.rank);
			d.rank = d.previousRank != null && DataStore.root().ranks.containsKey(d.previousRank) ? d.previousRank : null;
			d.previousRank = null;
			d.rankExpires = 0;
			DataStore.markDirty();
			ServerPlayer online = server.getPlayerList().getPlayer(UUID.fromString(e.getKey()));
			if (online != null) {
				refresh(online, server);
				online.sendSystemMessage(Msg.prefixed(Text.of("&7Your ").append(Text.of(expired != null ? expired.display : "timed"))
					.append(Text.of(" &7rank has expired. You're now ")).append(Text.of(of(online.getUUID()).display)).append(Text.of("&7."))));
			}
		}
	}

	/** "&7(expires in 2d 4h)" or "" for permanent ranks. */
	public static String expiryNote(UUID player) {
		long exp = DataStore.find(player).map(d -> d.rankExpires).orElse(0L);
		if (exp == 0) return "";
		return "&7(expires in &f" + Moderation.formatDuration(exp - System.currentTimeMillis()) + "&7)";
	}

	/** "[VIP] " as a component, or empty. Always shown (menus, profiles). */
	public static Component prefix(UUID player) {
		String p = of(player).prefix;
		return p == null || p.isEmpty() ? Component.empty() : Text.of(p);
	}

	/** Prefix for chat lines, respecting the rank's "show in chat" toggle. */
	public static Component chatPrefix(UUID player) {
		return of(player).showInChat ? prefix(player) : Component.empty();
	}

	/** Prefix for the tab list, respecting the rank's "show in tab" toggle. */
	public static Component tabPrefix(UUID player) {
		return of(player).showInTab ? prefix(player) : Component.empty();
	}

	/** The player's display name wrapped in their rank's name colour. */
	public static MutableComponent coloredName(ServerPlayer player) {
		Style style = Text.styleOf(of(player.getUUID()).nameColor);
		return Component.empty().withStyle(style).append(player.getDisplayName());
	}

	public static int members(Rank rank) {
		boolean isDefault = rank == defaultRank();
		int n = 0;
		for (PlayerData d : DataStore.root().players.values()) {
			if (rank.id.equals(d.rank) || (isDefault && (d.rank == null || !DataStore.root().ranks.containsKey(d.rank)))) n++;
		}
		return n;
	}

	/** Playtime promotions. Only ever moves players up. */
	public static void checkPromotions(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			PlayerData d = DataStore.get(p.getUUID());
			Rank current = of(p.getUUID());
			Rank best = null;
			for (Rank r : DataStore.root().ranks.values()) {
				if (r.autoHours <= 0 || r.weight <= current.weight) continue;
				if (d.playtimeSeconds < r.autoHours * 3600L) continue;
				if (best == null || r.weight > best.weight) best = r;
			}
			if (best != null && d.rankExpires == 0) {
				set(server, p.getUUID(), p.getGameProfile().name(), best, null);
				server.getPlayerList().broadcastSystemMessage(Msg.prefixed(Text.of("&d&l✦ ").append(coloredName(p))
					.append(Text.of(" &7ranked up to ")).append(Text.of(best.display)).append(Text.of(" &7for playing &f" + best.autoHours + "h&7!"))), false);
			}
		}
	}
}
