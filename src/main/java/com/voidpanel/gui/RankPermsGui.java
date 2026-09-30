package com.voidpanel.gui;

import com.voidpanel.data.DataStore;
import com.voidpanel.data.DataStore.Rank;
import com.voidpanel.feature.Ranks;
import com.voidpanel.perm.Perm;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import com.voidpanel.util.Text;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;

/**
 * A rank's permissions. Each one is ALLOW, DENY or INHERIT (use the default rank / built-in
 * default). Click to cycle.
 */
public final class RankPermsGui extends Gui {
	private final Rank rank;
	private Perm.Cat cat = Perm.Cat.TRAVEL;

	public RankPermsGui(ServerPlayer player, Rank rank) {
		super(player, 6, "&5&l✦ &8Perms: " + rank.id);
		this.rank = rank;
	}

	@Override
	protected void build() {
		boolean isDefault = rank == Ranks.defaultRank();
		categoryTabs(this, cat, c -> {
			cat = c;
			refresh();
		});
		List<Perm> perms = cat.perms();
		for (int i = 0; i < perms.size() && i < 36; i++) {
			Perm perm = perms.get(i);
			Boolean v = rank.perms.get(perm.node);
			Boolean inherited = isDefault ? null : Ranks.defaultRank().perms.get(perm.node);
			boolean effective = v != null ? v : inherited != null ? inherited : !perm.adminOnly;
			List<String> lore = new ArrayList<>();
			for (String line : perm.description) lore.add("&7" + line);
			lore.add("");
			lore.add(v == null ? "&7&l● INHERIT" : v ? "&a&l✔ ALLOW" : "&c&l✘ DENY");
			if (v == null) {
				lore.add(inherited != null ? "&8(default rank says " + (inherited ? "allow" : "deny") + ")"
					: "&8(built-in: " + (perm.adminOnly ? "admins only" : "everyone") + ")");
			}
			lore.add("");
			lore.add("&eClick &7to cycle: inherit → allow → deny");
			String color = v == null ? "&7" : v ? "&a" : "&c";
			set(9 + i, ItemBuilder.of(perm.icon).name(color + "&l" + perm.title + " &8(" + perm.node + ")").glint(v != null && v).clean().lore(lore).build(), c -> {
				if (v == null) rank.perms.put(perm.node, true);
				else if (v) rank.perms.put(perm.node, false);
				else rank.perms.remove(perm.node);
				DataStore.markDirty();
				Msg.sound(player, SoundEvents.NOTE_BLOCK_PLING, 0.5f, v == null ? 1.6f : v ? 0.7f : 1.0f);
				refreshMembers();
				refresh();
			});
		}

		for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		backButton(45, new RankEditorGui(player, rank));
		set(47, ItemBuilder.of(Items.CONCRETE.lime()).name("&a&lAllow all in " + cat.title).build(), c -> {
			for (Perm p : cat.perms()) rank.perms.put(p.node, true);
			DataStore.markDirty();
			refreshMembers();
			refresh();
		});
		set(48, ItemBuilder.of(Items.CONCRETE.gray()).name("&7&lInherit all in " + cat.title).build(), c -> {
			for (Perm p : cat.perms()) rank.perms.remove(p.node);
			DataStore.markDirty();
			refreshMembers();
			refresh();
		});
		set(49, ItemBuilder.of(RanksGui.icon(rank)).name(Text.of(rank.display)).lore("&7" + rank.perms.size() + " custom rules").build());
		closeButton(53);
	}

	private void refreshMembers() {
		var server = player.level().getServer();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) server.getCommands().sendCommands(p);
	}

	/** Shared row-0 category tabs used by both permission editors. */
	static void categoryTabs(Gui gui, Perm.Cat current, java.util.function.Consumer<Perm.Cat> pick) {
		for (int i = 0; i < 9; i++) gui.set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		Perm.Cat[] cats = Perm.Cat.values();
		for (int i = 0; i < cats.length; i++) {
			Perm.Cat c = cats[i];
			boolean active = c == current;
			gui.set(2 + i, ItemBuilder.of(c.icon).name((active ? c.color + "&l" : "&7") + c.title).glint(active).clean()
				.lore(active ? "&8Showing" : "&e▶ Click to show", "&8" + c.perms().size() + " permissions").build(), click -> pick.accept(c));
		}
	}
}
