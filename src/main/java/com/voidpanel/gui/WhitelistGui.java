package com.voidpanel.gui;

import com.voidpanel.data.DataStore;
import com.voidpanel.feature.ChatPrompts;
import com.voidpanel.feature.Moderation;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserWhiteListEntry;
import net.minecraft.world.item.Items;

/**
 * /whitelist panel. Shows whitelisted players (allowed) and players who recently tried
 * to join or are online without being whitelisted (disallowed). Click to flip.
 */
public final class WhitelistGui extends Gui {
	private static final int PER_PAGE = 36;
	private int page;

	private record Entry(NameAndId who, boolean allowed, String note) {}

	public WhitelistGui(ServerPlayer player) {
		super(player, 6, "&5&l✦ &8Whitelist Panel");
	}

	@Override
	protected void build() {
		MinecraftServer server = player.level().getServer();
		boolean on = server.isUsingWhitelist();

		for (int i = 0; i < 9; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		set(2, ItemBuilder.of(on ? Items.CONCRETE.lime() : Items.CONCRETE.red()).name(on ? "&a&lWhitelist: ON" : "&c&lWhitelist: OFF").lore(
			on ? "&7Only whitelisted players can join." : "&7Anyone can join right now.", "", "&e▶ Click to turn " + (on ? "off" : "on")).build(), c -> {
				server.setUsingWhitelist(!on);
				if (!on) server.kickUnlistedPlayers();
				Msg.ok(player, "Whitelist turned " + (!on ? "on" : "off") + ".");
				refresh();
			});
		set(4, ItemBuilder.of(Items.BOOK).name("&dHow it works").lore(
			"&a✔ Green lore &7= allowed (whitelisted)",
			"&c✘ Red lore &7= not allowed",
			"",
			"&7Players who get turned away show",
			"&7up here so you can let them in.",
			"&7Ops can always join.").build());
		set(6, ItemBuilder.of(Items.WRITABLE_BOOK).name("&a&l+ Add player").lore("&7Whitelist someone by name,", "&7even if they've never joined.", "", "&e▶ Click, then type their name").build(), c ->
			ChatPrompts.ask(player, "Type the name of the player to whitelist:", null, name ->
				Moderation.resolve(server, name, result -> {
					if (result.isEmpty()) {
						Msg.err(player, "No Minecraft account called &f" + name + "&c.");
					} else {
						Moderation.whitelistAdd(server, result.get());
						Msg.ok(player, "Whitelisted &f" + result.get().name() + "&a.");
					}
					new WhitelistGui(player).open();
				})));

		List<Entry> entries = entries(server);
		int pages = page(entries, 9, PER_PAGE, page, (slot, e) -> set(slot, ItemBuilder.head(e.who.id(), e.who.name())
			.name((e.allowed ? "&a" : "&c") + e.who.name())
			.lore(e.allowed ? "&a✔ Allowed" : "&c✘ Not allowed", e.note, "",
				e.allowed ? "&c▶ Click to remove from whitelist" : "&a▶ Click to add to whitelist").build(), c -> {
					if (e.allowed) {
						Moderation.whitelistRemove(server, e.who);
						Msg.ok(player, "Removed &f" + e.who.name() + " &afrom the whitelist.");
					} else {
						Moderation.whitelistAdd(server, e.who);
						Msg.ok(player, "Whitelisted &f" + e.who.name() + "&a.");
					}
					refresh();
				}));
		if (entries.isEmpty()) set(31, ItemBuilder.of(Items.CLOCK).name("&7Nobody here yet").build());

		for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		pageButtons(45, 49, 53, page, pages, p -> {
			page = p;
			refresh();
		});
		backButton(47, new MainGui(player));
		closeButton(51);
	}

	private List<Entry> entries(MinecraftServer server) {
		List<Entry> list = new ArrayList<>();
		List<UUID> seen = new ArrayList<>();
		for (UserWhiteListEntry entry : server.getPlayerList().getWhiteList().getEntries()) {
			NameAndId who = entry.getUser();
			if (who == null) continue;
			boolean online = server.getPlayerList().getPlayer(who.id()) != null;
			list.add(new Entry(who, true, online ? "&7Status: &aonline" : "&7Status: &8offline"));
			seen.add(who.id());
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (seen.contains(p.getUUID())) continue;
			list.add(new Entry(new NameAndId(p.getGameProfile()), false, "&7Online now" + (server.getPlayerList().isOp(new NameAndId(p.getGameProfile())) ? " &8(op bypass)" : "")));
			seen.add(p.getUUID());
		}
		for (var d : DataStore.root().denied) {
			UUID id = UUID.fromString(d.uuid);
			if (seen.contains(id)) continue;
			list.add(new Entry(new NameAndId(id, d.name), false, "&7Tried to join &f" + ago(d.time)));
			seen.add(id);
		}
		return list;
	}

	static String ago(long time) {
		long s = (System.currentTimeMillis() - time) / 1000;
		if (s < 60) return s + "s ago";
		if (s < 3600) return s / 60 + "m ago";
		if (s < 86400) return s / 3600 + "h ago";
		return s / 86400 + "d ago";
	}
}
