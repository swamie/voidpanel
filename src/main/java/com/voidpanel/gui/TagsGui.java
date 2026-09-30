package com.voidpanel.gui;

import com.voidpanel.data.DataStore;
import com.voidpanel.data.DataStore.Tag;
import com.voidpanel.feature.ChatPrompts;
import com.voidpanel.feature.Tags;
import com.voidpanel.perm.Perm;
import com.voidpanel.perm.Perms;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import com.voidpanel.util.Text;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** /tags: pick a chat tag. Admins create, edit and delete tags here. */
public final class TagsGui extends Gui {
	public TagsGui(ServerPlayer player) {
		super(player, 5, "&5&l✦ &8Chat Tags");
	}

	@Override
	protected void build() {
		boolean admin = Perms.has(player, Perm.TAGS_ADMIN);
		var data = DataStore.get(player.getUUID());
		List<Tag> tags = admin ? List.copyOf(DataStore.root().tags.values()) : Tags.available(player);
		page(tags, 0, 36, 0, (slot, t) -> {
			boolean equipped = t.id.equals(data.tag);
			boolean owned = Tags.owns(player, t);
			List<String> lore = new ArrayList<>();
			lore.add("&7Preview: " + t.display + " &f" + player.getGameProfile().name());
			lore.add(t.everyone ? "&8Available to everyone" : "&8Unlockable");
			lore.add("");
			lore.add(equipped ? "&a✔ Equipped &7(click to remove)" : owned ? "&e▶ Click to equip" : "&c✘ Locked");
			if (admin) lore.addAll(List.of("", "&8Admin (id: " + t.id + "):", "&eRight-click &7edit text", "&eShift-left &7toggle everyone", "&cShift-right &7delete",
				"&7Give with &f/tags give <player> " + t.id));
			set(slot, ItemBuilder.of(icon(t)).name(Text.of(t.display)).glint(equipped).clean().lore(lore).build(), c -> {
				if (admin && c.shift() && c.right()) {
					new ConfirmGui(player, "&4Delete tag " + t.id + "?", ItemBuilder.of(icon(t)).name(Text.of(t.display)).build(), () -> {
						DataStore.root().tags.remove(t.id);
						DataStore.markDirty();
						Msg.ok(player, "Deleted tag " + t.id + ".");
						new TagsGui(player).open();
					}, () -> new TagsGui(player).open()).open();
				} else if (admin && c.shift()) {
					t.everyone = !t.everyone;
					DataStore.markDirty();
					refresh();
				} else if (admin && c.right()) {
					ChatPrompts.ask(player, "Type the new tag text (& colours and the gradient maker work):", t.display, input -> {
						t.display = input;
						DataStore.markDirty();
						new TagsGui(player).open();
					});
				} else if (owned) {
					data.tag = equipped ? null : t.id;
					DataStore.markDirty();
					Msg.ok(player, equipped ? "Tag removed." : "Tag equipped!");
					refresh();
				} else {
					Msg.err(player, "You haven't unlocked that tag.");
				}
			});
		});
		if (tags.isEmpty()) set(22, ItemBuilder.of(Items.CLOCK).name("&7No tags available yet").build());
		for (int i = 36; i < 45; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		backButton(36, new MainGui(player));
		if (data.tag != null) {
			set(38, ItemBuilder.of(Items.BUCKET).name("&7Remove my tag").build(), c -> {
				data.tag = null;
				DataStore.markDirty();
				refresh();
			});
		}
		if (admin) {
			set(40, ItemBuilder.of(Items.WRITABLE_BOOK).name("&a&l+ New tag").lore("&7Type an id, then the text.", "&7Tip: build it in the gradient maker!").build(), c ->
				ChatPrompts.ask(player, "Type an id for the tag (letters/numbers, e.g. &fbuilder&7):", null, id -> {
					String clean = id.toLowerCase().replaceAll("[^a-z0-9_]", "");
					if (clean.isEmpty() || DataStore.root().tags.containsKey(clean)) {
						Msg.err(player, "That id is empty or already used.");
						new TagsGui(player).open();
						return;
					}
					ChatPrompts.ask(player, "Now type what the tag shows, e.g. &f&8[&6Builder&8]&7:", null, display -> {
						Tag t = new Tag();
						t.id = clean;
						t.display = display;
						DataStore.root().tags.put(clean, t);
						DataStore.markDirty();
						Msg.ok(player, "Created tag " + clean + ".");
						new TagsGui(player).open();
					});
				}));
		}
		closeButton(44);
	}

	private static Item icon(Tag t) {
		Item i = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(t.icon));
		return i == null || i == Items.AIR ? Items.NAME_TAG : i;
	}
}
