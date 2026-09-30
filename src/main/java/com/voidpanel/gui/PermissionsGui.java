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
import net.minecraft.world.item.Items;

/** /permissions: pick an online player (or search an offline one) to edit. */
public final class PermissionsGui extends Gui {
	private int page;

	public PermissionsGui(ServerPlayer player) {
		super(player, 6, "&5&l✦ &8Permissions");
	}

	@Override
	protected void build() {
		MinecraftServer server = player.level().getServer();
		List<NameAndId> people = new ArrayList<>();
		List<UUID> online = new ArrayList<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			people.add(new NameAndId(p.getGameProfile()));
			online.add(p.getUUID());
		}
		// Offline players who already have custom permissions.
		for (var e : DataStore.root().players.entrySet()) {
			UUID id = UUID.fromString(e.getKey());
			if (online.contains(id) || e.getValue().name == null) continue;
			if (!e.getValue().perms.isEmpty() || e.getValue().homeLimit != null) people.add(new NameAndId(id, e.getValue().name));
		}

		int pages = page(people, 0, 45, page, (slot, who) -> {
			boolean isOnline = online.contains(who.id());
			var data = DataStore.find(who.id());
			int custom = data.map(d -> d.perms.size()).orElse(0);
			ServerPlayer op = server.getPlayerList().getPlayer(who.id());
			ItemBuilder head = op != null ? ItemBuilder.head(op) : ItemBuilder.head(who.id(), who.name());
			set(slot, head.name((isOnline ? "&a" : "&7") + who.name()).lore(
				isOnline ? "&a● Online" : "&8● Offline",
				"&7Custom rules: &f" + custom,
				"",
				"&e▶ Click to edit").build(), c -> {
					Msg.click(player);
					new PermissionEditorGui(player, who).open();
				});
		});

		for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		pageButtons(45, -1, 53, page, pages, p -> {
			page = p;
			refresh();
		});
		set(49, ItemBuilder.of(Items.SPYGLASS).name("&b&lFind offline player").lore("&7Edit someone who isn't online", "&7by typing their name.", "", "&e▶ Click, then type a name").build(), c ->
			ChatPrompts.ask(player, "Type the player's name:", null, name -> Moderation.resolve(server, name, result -> {
				if (result.isEmpty()) {
					Msg.err(player, "No Minecraft account called &f" + name + "&c.");
					new PermissionsGui(player).open();
				} else {
					new PermissionEditorGui(player, result.get()).open();
				}
			})));
		backButton(47, new MainGui(player));
		closeButton(51);
	}
}
