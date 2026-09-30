package com.voidpanel.gui;

import com.voidpanel.data.DataStore;
import com.voidpanel.data.PlayerData.Pet;
import com.voidpanel.feature.Pets;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

/** /pets: every pet you own, with click-to-fetch (works even if the pet is far away). */
public final class PetsGui extends Gui {
	private enum Filter {
		ALL("All pets", Items.LEAD, null),
		DOGS("Wolves", Items.BONE, "wolf"),
		CATS("Cats", Items.COD, "cat"),
		BIRDS("Parrots", Items.FEATHER, "parrot"),
		MOUNTS("Horses & co.", Items.SADDLE, "horse|donkey|mule|llama|camel|skeleton_horse|zombie_horse"),
		OTHER("Other", Items.NAME_TAG, "");

		final String label;
		final Item icon;
		final String match;

		Filter(String label, Item icon, String match) {
			this.label = label;
			this.icon = icon;
			this.match = match;
		}

		boolean accepts(String type) {
			if (this == ALL) return true;
			String path = type.substring(type.indexOf(':') + 1);
			if (this == OTHER) {
				for (Filter f : values()) if (f != ALL && f != OTHER && path.matches(f.match)) return false;
				return true;
			}
			return path.matches(match);
		}
	}

	private final UUID owner;
	private final String ownerName;
	private Filter filter = Filter.ALL;
	private int page;

	public PetsGui(ServerPlayer viewer, UUID owner, String ownerName) {
		super(viewer, 6, viewer.getUUID().equals(owner) ? "&5&l✦ &8Your Pets" : "&5&l✦ &8" + ownerName + "'s Pets");
		this.owner = owner;
		this.ownerName = ownerName;
	}

	@Override
	protected void build() {
		var server = player.level().getServer();
		Pets.scan(server);
		List<Map.Entry<String, Pet>> pets = new ArrayList<>();
		for (var e : DataStore.get(owner).pets.entrySet()) {
			if (e.getValue().type != null && e.getValue().loc != null && filter.accepts(e.getValue().type)) pets.add(e);
		}

		for (int i = 0; i < 9; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		set(4, ItemBuilder.of(Items.LEAD).name("&d&lPet Teleport").lore(
			"&eLeft-click &7a pet to bring it to you",
			"&eRight-click &7to make it sit / stand",
			"",
			"&7Works even when the pet is in",
			"&7an unloaded chunk far away.").build());

		int pages = page(pets, 9, 36, page, (slot, entry) -> {
			UUID id = UUID.fromString(entry.getKey());
			Pet pet = entry.getValue();
			Entity live = Pets.findLoaded(server, id);
			List<String> lore = new ArrayList<>();
			lore.add("&7Type: &f" + Pets.label(typeOnly(pet)));
			lore.add("&7World: " + pet.loc.dimColor() + pet.loc.dimName());
			lore.add("&7Location: &f" + pet.loc.coords());
			if (live instanceof LivingEntity le) {
				lore.add("&7Health: &c❤ &f" + Math.round(le.getHealth()) + "&7/&f" + Math.round(le.getMaxHealth()));
				if (live instanceof TamableAnimal t) lore.add("&7Status: " + (t.isOrderedToSit() ? "&esitting" : "&afollowing"));
				lore.add("&a● Loaded");
			} else {
				lore.add("&8● In an unloaded chunk (seen " + WhitelistGui.ago(pet.seen) + ")");
			}
			lore.add("");
			lore.add("&e▶ Left-click &7to teleport it to you");
			if (live instanceof TamableAnimal) lore.add("&e▶ Right-click &7to toggle sitting");
			set(slot, ItemBuilder.of(iconFor(pet.type)).name("&a&l" + Pets.label(pet)).clean().lore(lore).build(), c -> {
				if (c.right()) {
					if (live instanceof TamableAnimal t) {
						t.setOrderedToSit(!t.isOrderedToSit());
						t.setInSittingPose(t.isOrderedToSit());
						Msg.click(player);
						refresh();
					}
					return;
				}
				close();
				Msg.info(player, "Fetching &f" + Pets.label(pet) + "&7...");
				Pets.fetch(player, id, pet, ok -> {
					if (ok) Msg.ok(player, Pets.label(pet) + " is here!");
					else Msg.err(player, "Couldn't find &f" + Pets.label(pet) + "&c. It may have died or wandered off.");
				});
			});
		});
		if (pets.isEmpty()) {
			set(31, ItemBuilder.of(Items.CLOCK).name("&7No pets found").lore("&7Tame a wolf, cat, parrot or horse", "&7and it'll show up here.").build());
		}

		for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		pageButtons(45, -1, 53, page, pages, p -> {
			page = p;
			refresh();
		});
		set(48, ItemBuilder.of(filter.icon).name("&b&lShowing: &f" + filter.label).lore(filterLore()).build(), c -> {
			Msg.click(player);
			filter = Filter.values()[(filter.ordinal() + (c.right() ? Filter.values().length - 1 : 1)) % Filter.values().length];
			page = 0;
			refresh();
		});
		if (!pets.isEmpty()) {
			set(50, ItemBuilder.of(Items.ENDER_PEARL).name("&d&lFetch all shown").lore("&7Teleport every pet in this", "&7list to you at once.").build(), c -> {
				close();
				Msg.info(player, "Fetching " + pets.size() + " pet" + (pets.size() == 1 ? "" : "s") + "...");
				int[] counts = new int[2];
				for (var entry : pets) {
					Pets.fetch(player, UUID.fromString(entry.getKey()), entry.getValue(), ok -> {
						counts[ok ? 0 : 1]++;
						if (counts[0] + counts[1] == pets.size()) {
							Msg.ok(player, "Fetched " + counts[0] + "/" + pets.size() + " pets." + (counts[1] > 0 ? " &7(" + counts[1] + " couldn't be found)" : ""));
						}
					});
				}
			});
		}
		backButton(46, new MainGui(player));
		closeButton(52);
	}

	private List<String> filterLore() {
		List<String> lore = new ArrayList<>();
		for (Filter f : Filter.values()) lore.add((f == filter ? "&a▶ " : "&8  ") + f.label);
		lore.add("");
		lore.add("&eLeft/right-click &7to cycle");
		return lore;
	}

	private static Pet typeOnly(Pet pet) {
		Pet p = new Pet();
		p.type = pet.type;
		return p;
	}

	private static Item iconFor(String type) {
		EntityType<?> et = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.tryParse(type));
		if (et != null) {
			var egg = SpawnEggItem.byId(et);
			if (egg.isPresent()) return egg.get().value();
		}
		return Items.LEAD;
	}
}
