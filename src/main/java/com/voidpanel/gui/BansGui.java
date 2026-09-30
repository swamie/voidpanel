package com.voidpanel.gui;

import com.voidpanel.feature.Moderation;
import com.voidpanel.perm.Perm;
import com.voidpanel.perm.Perms;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.item.Items;

/** Banned players and blacklisted IPs, with click-to-lift. */
public final class BansGui extends Gui {
	private static final SimpleDateFormat DATE = new SimpleDateFormat("d MMM yyyy", Locale.ROOT);
	private final boolean ips;
	private int page;

	public BansGui(ServerPlayer player, boolean ips) {
		super(player, 6, ips ? "&5&l✦ &8IP Blacklist" : "&5&l✦ &8Banned Players");
		this.ips = ips;
	}

	@Override
	protected void build() {
		MinecraftServer server = player.level().getServer();
		for (int i = 0; i < 9; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		if (Perms.has(player, Perm.BAN)) {
			set(3, ItemBuilder.of(Items.IRON_BARS).name((ips ? "&7" : "&c&l") + "Banned players").glint(!ips).lore(ips ? "&e▶ Click to view" : "&8Viewing").build(), c -> {
				if (ips) new BansGui(player, false).open();
			});
		}
		if (Perms.has(player, Perm.BLACKLIST)) {
			set(5, ItemBuilder.of(Items.STRUCTURE_VOID).name((ips ? "&c&l" : "&7") + "IP blacklist").glint(ips).lore(ips ? "&8Viewing" : "&e▶ Click to view").build(), c -> {
				if (!ips) new BansGui(player, true).open();
			});
		}

		int pages;
		if (ips) {
			List<IpBanListEntry> list = Moderation.ipBans(server);
			pages = page(list, 9, 36, page, (slot, e) -> {
				String ip = e.getUser();
				set(slot, ItemBuilder.of(Items.PAPER).name("&c" + ip).lore(
					"&7Players: &f" + Moderation.playersOnIp(ip),
					"&7Reason: &f" + e.getReason(),
					"&7By: &f" + e.getSource(),
					"&7Since: &f" + DATE.format(e.getCreated()),
					"",
					"&a▶ Click to remove from blacklist").build(), c ->
						new ConfirmGui(player, "&2Unblacklist " + ip + "?", ItemBuilder.of(Items.PAPER).name("&f" + ip).build(), () -> {
							Moderation.unblacklist(server, ip);
							Msg.ok(player, "Removed &f" + ip + " &afrom the blacklist.");
							new BansGui(player, true).open();
						}, () -> new BansGui(player, true).open()).open());
			});
			if (list.isEmpty()) set(31, ItemBuilder.of(Items.CLOCK).name("&7No blacklisted IPs").lore("&8/blacklist <player|ip> [reason]").build());
		} else {
			List<UserBanListEntry> list = Moderation.bans(server);
			pages = page(list, 9, 36, page, (slot, e) -> {
				NameAndId who = e.getUser();
				if (who == null) return;
				set(slot, ItemBuilder.head(who.id(), who.name()).name("&c" + who.name()).lore(
					"&7Reason: &f" + e.getReason(),
					"&7By: &f" + e.getSource(),
					"&7Since: &f" + DATE.format(e.getCreated()),
					e.getExpires() != null ? "&7Expires: &f" + DATE.format(e.getExpires()) : "&7Expires: &cnever",
					"",
					"&a▶ Click to unban").build(), c ->
						new ConfirmGui(player, "&2Unban " + who.name() + "?", ItemBuilder.head(who.id(), who.name()).name("&f" + who.name()).build(), () -> {
							Moderation.unban(server, who);
							Msg.ok(player, "Unbanned &f" + who.name() + "&a.");
							new BansGui(player, false).open();
						}, () -> new BansGui(player, false).open()).open());
			});
			if (list.isEmpty()) set(31, ItemBuilder.of(Items.CLOCK).name("&7Nobody is banned").lore("&8/ban <player> [reason]").build());
		}

		for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		pageButtons(45, 49, 53, page, pages, p -> {
			page = p;
			refresh();
		});
		backButton(47, new MainGui(player));
		closeButton(51);
	}
}
