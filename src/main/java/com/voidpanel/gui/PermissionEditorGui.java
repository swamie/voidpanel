package com.voidpanel.gui;

import com.voidpanel.config.Config;
import com.voidpanel.data.DataStore;
import com.voidpanel.data.PlayerData;
import com.voidpanel.feature.Ranks;
import com.voidpanel.perm.Perm;
import com.voidpanel.perm.Perms;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;

/** One player's permissions, by category. Green = enabled, red = disabled. */
public final class PermissionEditorGui extends Gui {
	private final NameAndId target;
	private Perm.Cat cat = Perm.Cat.TRAVEL;

	public PermissionEditorGui(ServerPlayer player, NameAndId target) {
		super(player, 6, "&5&l✦ &8Permissions: " + target.name());
		this.target = target;
	}

	@Override
	protected void build() {
		MinecraftServer server = player.level().getServer();
		ServerPlayer online = server.getPlayerList().getPlayer(target.id());
		PlayerData data = DataStore.get(target.id());
		if (data.name == null) data.name = target.name();

		RankPermsGui.categoryTabs(this, cat, c -> {
			cat = c;
			refresh();
		});
		List<Perm> perms = cat.perms();
		for (int i = 0; i < perms.size() && i < 36; i++) {
			Perm perm = perms.get(i);
			set(9 + i, permItem(server, perm), c -> {
				if (c.shift()) {
					data.perms.remove(perm.node);
					Msg.sound(player, SoundEvents.NOTE_BLOCK_BASS, 0.6f, 1.0f);
				} else {
					boolean now = Perms.has(server, target.id(), target.name(), perm);
					data.perms.put(perm.node, !now);
					Msg.sound(player, SoundEvents.NOTE_BLOCK_PLING, 0.6f, now ? 0.7f : 1.6f);
				}
				changed(online);
			});
		}

		for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		backButton(45, new PermissionsGui(player));
		ItemBuilder head = online != null ? ItemBuilder.head(online) : ItemBuilder.head(target.id(), target.name());
		set(46, head.name("&d&l" + target.name()).lore(
			online != null ? "&a● Online" : "&8● Offline",
			"&7Rank: " + Ranks.of(target.id()).display,
			"&7Admin (op 3+): " + (server.getProfilePermissions(target).hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_ADMIN) ? "&ayes" : "&7no"),
			"",
			"&7Order: &fplayer rule &7> &frank &7> &fdefault rank",
			"&7> &fbuilt-in default").build());

		if (Perms.has(player, Perm.RANKS)) {
			set(48, ItemBuilder.of(Items.DIAMOND).name("&b&lRank").lore("&7" + Ranks.of(target.id()).display, "", "&e▶ Click to change").build(),
				c -> new RankPickerGui(player, target, this).open());
		}

		int limit = Perms.homeLimit(target.id());
		boolean customLimit = data.homeLimit != null;
		set(49, ItemBuilder.of(Items.BED.red()).name("&d&lHome limit: &f" + limit).count(Math.max(1, Math.min(64, limit))).lore(
			customLimit ? "&7Custom value &8(default " + Config.get().defaultHomeLimit + ")" : "&8From rank / default",
			"",
			"&eLeft-click &7+1",
			"&eRight-click &7-1",
			"&eShift-click &7reset").build(), c -> {
				if (c.shift()) data.homeLimit = null;
				else data.homeLimit = Math.max(0, Math.min(54, limit + (c.right() ? -1 : 1)));
				Msg.click(player);
				changed(online);
			});

		set(51, ItemBuilder.of(Items.TNT).name("&c&lReset player rules").lore("&7Remove every custom rule for this", "&7player (their rank still applies).").build(), c ->
			new ConfirmGui(player, "&4Reset " + target.name() + "?", ItemBuilder.of(Items.TNT).name("&cReset all player rules").build(), () -> {
				data.perms.clear();
				data.homeLimit = null;
				DataStore.markDirty();
				if (online != null) server.getCommands().sendCommands(online);
				Msg.ok(player, "Reset " + target.name() + "'s permissions.");
				new PermissionEditorGui(player, target).open();
			}, () -> new PermissionEditorGui(player, target).open()).open());
		closeButton(53);
	}

	private void changed(ServerPlayer online) {
		DataStore.markDirty();
		if (online != null) player.level().getServer().getCommands().sendCommands(online);
		refresh();
	}

	private net.minecraft.world.item.ItemStack permItem(MinecraftServer server, Perm perm) {
		boolean on = Perms.has(server, target.id(), target.name(), perm);
		boolean custom = Perms.isOverridden(target.id(), perm);
		List<String> lore = new ArrayList<>();
		for (String line : perm.description) lore.add("&7" + line);
		lore.add("");
		lore.add(on ? "&a&l✔ ENABLED" : "&c&l✘ DISABLED");
		lore.add("&8(from " + Perms.source(target.id(), perm) + ")");
		lore.add("");
		lore.add("&eClick &7to " + (on ? "disable" : "enable") + " for this player");
		if (custom) lore.add("&eShift-click &7to remove the player rule");
		return ItemBuilder.of(perm.icon).name((on ? "&a" : "&c") + "&l" + perm.title + " &8(" + perm.node + ")").glint(on).clean().lore(lore).build();
	}
}
