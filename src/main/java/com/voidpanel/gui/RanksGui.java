package com.voidpanel.gui;

import com.voidpanel.data.DataStore;
import com.voidpanel.data.DataStore.Rank;
import com.voidpanel.feature.ChatPrompts;
import com.voidpanel.feature.Ranks;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import com.voidpanel.util.Text;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** /ranks: every rank, most important first. Click one to edit it. */
public final class RanksGui extends Gui {
	public RanksGui(ServerPlayer player) {
		super(player, 5, "&5&l✦ &8Ranks");
	}

	static Item icon(Rank r) {
		Item i = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(r.icon));
		return i == null || i == Items.AIR ? Items.IRON_INGOT : i;
	}

	@Override
	protected void build() {
		for (int i = 0; i < 9; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		set(4, ItemBuilder.of(Items.KNOWLEDGE_BOOK).name("&d&lHow ranks work").lore(
			"&7Everyone starts in the &fdefault &7rank.",
			"&7A rank's permissions override the",
			"&7default rank; a player's own rules",
			"&7(in /permissions) override both.",
			"",
			"&7Ranks with &fauto-promote hours &7are",
			"&7given automatically once a player",
			"&7has played that long.").build());
		List<Rank> ranks = Ranks.sorted();
		Rank def = Ranks.defaultRank();
		page(ranks, 9, 27, 0, (slot, r) -> set(slot, ItemBuilder.of(icon(r)).name(Text.of(r.display)).glint(r == def).clean().lore(
			"&7Prefix: " + (r.prefix.isEmpty() ? "&8none" : r.prefix + "&7" + player.getGameProfile().name()),
			"&7Weight: &f" + r.weight,
			"&7Members: &f" + Ranks.members(r),
			"&7Home limit: &f" + (r.homeLimit != null ? r.homeLimit : "default"),
			"&7Custom permissions: &f" + r.perms.size(),
			r.autoHours > 0 ? "&7Auto-promote after &f" + r.autoHours + "h &7playtime" : "&8No auto-promote",
			"&7Prefix in chat: " + (r.showInChat ? "&aYES" : "&cNO") + " &8| &7tab: " + (r.showInTab ? "&aYES" : "&cNO"),
			r == def ? "&a★ Default rank (new players)" : "",
			"",
			"&e▶ Click to edit").build(), c -> new RankEditorGui(player, r).open()));

		for (int i = 36; i < 45; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		backButton(36, new MainGui(player));
		set(40, ItemBuilder.of(Items.EMERALD).name("&a&l+ New rank").lore("&e▶ Click, then type an id (e.g. &fbuilder&e)").build(), c ->
			ChatPrompts.ask(player, "Type an id for the new rank (letters/numbers):", null, id -> {
				String clean = id.toLowerCase().replaceAll("[^a-z0-9_]", "");
				if (clean.isEmpty() || DataStore.root().ranks.containsKey(clean)) {
					Msg.err(player, "That id is empty or already taken.");
					new RanksGui(player).open();
					return;
				}
				Rank r = new Rank(clean, "&f" + clean, "&8[&f" + clean + "&8] ", "&f", "minecraft:iron_ingot", 1, 0);
				DataStore.root().ranks.put(clean, r);
				DataStore.markDirty();
				Msg.ok(player, "Created rank " + clean + ". Now style it!");
				new RankEditorGui(player, r).open();
			}));
		closeButton(44);
	}
}
