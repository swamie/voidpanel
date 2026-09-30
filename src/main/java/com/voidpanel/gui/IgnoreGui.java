package com.voidpanel.gui;

import com.voidpanel.data.DataStore;
import com.voidpanel.feature.ChatPrompts;
import com.voidpanel.feature.Ignore;
import com.voidpanel.feature.Moderation;
import com.voidpanel.feature.Vanish;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.Items;

/** /ignore: who you're ignoring (top) and who's online (bottom). */
public final class IgnoreGui extends Gui {
	public IgnoreGui(ServerPlayer player) {
		super(player, 6, "&5&l✦ &8Ignore List");
	}

	@Override
	protected void build() {
		var server = player.level().getServer();
		List<String> ignored = List.copyOf(DataStore.get(player.getUUID()).ignored);
		set(4, ItemBuilder.of(Items.BARRIER).name("&c&lIgnored players").lore("&7You won't see their chat, private", "&7messages or teleport requests.", "&7Staff can't be ignored.").build());
		page(ignored, 9, 18, 0, (slot, id) -> {
			UUID uuid = UUID.fromString(id);
			String name = DataStore.find(uuid).map(d -> d.name).orElse("Unknown");
			set(slot, ItemBuilder.head(uuid, name).name("&c" + name).lore("&a▶ Click to stop ignoring").build(), c -> {
				Ignore.toggle(player, new NameAndId(uuid, name));
				refresh();
			});
		});
		if (ignored.isEmpty()) set(13, ItemBuilder.of(Items.DYE.lime()).name("&aYou're not ignoring anyone").build());

		for (int i = 27; i < 36; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.gray()));
		set(31, ItemBuilder.of(Items.PLAYER_HEAD).name("&7Online players &8(click to ignore)").build());
		List<ServerPlayer> online = server.getPlayerList().getPlayers().stream()
			.filter(p -> p != player && Vanish.canSee(player, p) && !ignored.contains(p.getStringUUID())).toList();
		page(online, 36, 9, 0, (slot, p) -> set(slot, ItemBuilder.head(p).name(p.getDisplayName()).lore("&c▶ Click to ignore").build(), c -> {
			Ignore.toggle(player, new NameAndId(p.getGameProfile()));
			refresh();
		}));

		for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		backButton(45, new MainGui(player));
		set(49, ItemBuilder.of(Items.NAME_TAG).name("&e&lIgnore by name").lore("&7For someone who's offline.", "", "&e▶ Click, then type a name").build(), c ->
			ChatPrompts.ask(player, "Type the name of the player to ignore:", null, name -> Moderation.resolve(server, name, r -> {
				if (r.isEmpty()) Msg.err(player, "No player called &f" + name + "&c.");
				else Ignore.toggle(player, r.get());
				new IgnoreGui(player).open();
			})));
		closeButton(53);
	}
}
