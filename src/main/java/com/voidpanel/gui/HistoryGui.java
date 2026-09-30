package com.voidpanel.gui;

import com.voidpanel.data.DataStore;
import com.voidpanel.data.PlayerData.Punishment;
import com.voidpanel.feature.Moderation;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Punishment history. Click an active mute or ban to lift it. */
public final class HistoryGui extends Gui {
	private static final SimpleDateFormat DATE = new SimpleDateFormat("d MMM yyyy HH:mm", Locale.ROOT);
	private final NameAndId target;

	public HistoryGui(ServerPlayer player, NameAndId target) {
		super(player, 6, "&5&l✦ &8History: " + target.name());
		this.target = target;
	}

	@Override
	protected void build() {
		var server = player.level().getServer();
		List<Punishment> list = DataStore.get(target.id()).punishments;
		page(list, 0, 45, 0, (slot, p) -> {
			boolean active = isActive(server, p);
			List<String> lore = new ArrayList<>(List.of(
				"&7Reason: &f" + p.reason,
				"&7By: &f" + p.by,
				"&7When: &f" + DATE.format(new Date(p.time))));
			if (p.type.equals("mute") || p.type.equals("ban")) {
				lore.add("&7Length: &f" + (p.until == 0 ? "permanent" : Moderation.formatDuration(p.until - p.time)));
				lore.add(active ? "&c● Active" : p.revoked ? "&a● Lifted" : "&8● Expired");
				if (active) lore.addAll(List.of("", "&a▶ Click to lift"));
			}
			set(slot, ItemBuilder.of(icon(p.type)).name((active ? "&c&l" : "&7&l") + p.type.toUpperCase()).glint(active).clean().lore(lore).build(), c -> {
				if (!active) return;
				if (p.type.equals("mute")) Moderation.unmute(server, target);
				else Moderation.unban(server, target);
				Msg.ok(player, "Lifted the " + p.type + " on " + target.name() + ".");
				refresh();
			});
		});
		if (list.isEmpty()) set(22, ItemBuilder.of(Items.DYE.lime()).name("&aClean record").build());
		for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		backButton(45, new PunishGui(player, target));
		closeButton(49);
	}

	private boolean isActive(net.minecraft.server.MinecraftServer server, Punishment p) {
		if (p.revoked) return false;
		if (p.until != 0 && p.until < System.currentTimeMillis()) return false;
		return switch (p.type) {
			case "mute" -> DataStore.get(target.id()).mutedUntil != 0;
			case "ban" -> server.getPlayerList().getBans().isBanned(target);
			default -> false;
		};
	}

	private static Item icon(String type) {
		return switch (type) {
			case "warn" -> Items.BELL;
			case "mute" -> Items.PAPER;
			case "kick" -> Items.LEATHER_BOOTS;
			case "ban" -> Items.IRON_BARS;
			default -> Items.BOOK;
		};
	}
}
