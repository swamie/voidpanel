package com.voidpanel.feature;

import com.voidpanel.VoidPanel;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import com.voidpanel.util.Text;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.world.Container;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;

/**
 * /invsee and /endersee. Online players are edited live. Offline players are loaded from
 * their save file, edited, and written back when the menu closes. If they try to log in
 * while that's happening, the menu is closed and saved first so nothing is duplicated.
 */
public final class Invsee {
	/** Offline sessions by target UUID, so a login can force-save them. */
	private static final Map<UUID, ServerPlayer> OFFLINE_SESSIONS = new HashMap<>();

	private Invsee() {}

	// ---------------------------------------------------------------- entry points

	public static void openInventory(ServerPlayer viewer, NameAndId target) {
		MinecraftServer server = viewer.level().getServer();
		ServerPlayer online = server.getPlayerList().getPlayer(target.id());
		if (online != null) {
			open(viewer, new InvView(online.getInventory()), "&8Inventory: &0" + target.name(), null);
			return;
		}
		if (!claimOffline(viewer, target)) return;
		CompoundTag tag = load(server, target);
		if (tag == null) {
			OFFLINE_SESSIONS.remove(target.id());
			Msg.err(viewer, "No saved data for &f" + target.name() + "&c. Have they joined before?");
			return;
		}
		SimpleContainer inv = new SimpleContainer(41);
		try (var reporter = new ProblemReporter.ScopedCollector(VoidPanel.LOGGER)) {
			ValueInput in = TagValueInput.create(reporter, server.registryAccess(), tag);
			for (ItemStackWithSlot s : in.listOrEmpty("Inventory", ItemStackWithSlot.CODEC)) {
				if (s.slot() >= 0 && s.slot() < 36) inv.setItem(s.slot(), s.stack());
			}
			EntityEquipment eq = in.read("equipment", EntityEquipment.CODEC).orElseGet(EntityEquipment::new);
			inv.setItem(36, eq.get(EquipmentSlot.FEET));
			inv.setItem(37, eq.get(EquipmentSlot.LEGS));
			inv.setItem(38, eq.get(EquipmentSlot.CHEST));
			inv.setItem(39, eq.get(EquipmentSlot.HEAD));
			inv.setItem(40, eq.get(EquipmentSlot.OFFHAND));
		}
		Msg.info(viewer, "&7Editing &f" + target.name() + "&7's saved inventory (offline). Changes save when you close it.");
		open(viewer, new InvView(inv), "&8Inventory: &0" + target.name() + " &8(offline)", () -> {
			OFFLINE_SESSIONS.remove(target.id());
			saveInventory(server, target, tag, inv);
			Msg.ok(viewer, "Saved " + target.name() + "'s inventory.");
		});
	}

	public static void openEnder(ServerPlayer viewer, NameAndId target) {
		MinecraftServer server = viewer.level().getServer();
		ServerPlayer online = server.getPlayerList().getPlayer(target.id());
		if (online != null) {
			viewer.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv, online.getEnderChestInventory()),
				Text.of("&8Ender chest: &5" + target.name())));
			return;
		}
		if (!claimOffline(viewer, target)) return;
		CompoundTag tag = load(server, target);
		if (tag == null) {
			OFFLINE_SESSIONS.remove(target.id());
			Msg.err(viewer, "No saved data for &f" + target.name() + "&c.");
			return;
		}
		SimpleContainer ender = new SimpleContainer(27);
		try (var reporter = new ProblemReporter.ScopedCollector(VoidPanel.LOGGER)) {
			ValueInput in = TagValueInput.create(reporter, server.registryAccess(), tag);
			for (ItemStackWithSlot s : in.listOrEmpty("EnderItems", ItemStackWithSlot.CODEC)) {
				if (s.slot() >= 0 && s.slot() < 27) ender.setItem(s.slot(), s.stack());
			}
		}
		Runnable onClose = () -> {
			OFFLINE_SESSIONS.remove(target.id());
			try (var reporter = new ProblemReporter.ScopedCollector(VoidPanel.LOGGER)) {
				TagValueOutput out = TagValueOutput.createWithContext(reporter, server.registryAccess());
				var list = out.list("EnderItems", ItemStackWithSlot.CODEC);
				for (int i = 0; i < 27; i++) if (!ender.getItem(i).isEmpty()) list.add(new ItemStackWithSlot(i, ender.getItem(i)));
				putList(tag, "EnderItems", out.buildResult());
			}
			write(server, target, tag);
			Msg.ok(viewer, "Saved " + target.name() + "'s ender chest.");
		};
		viewer.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x3, id, inv, ender, 3) {
			@Override
			public void removed(Player player) {
				super.removed(player);
				onClose.run();
			}
		}, Text.of("&8Ender chest: &5" + target.name() + " &8(offline)")));
	}

	/** Called before a player's data is loaded on login. */
	public static void onLoginAttempt(UUID id) {
		ServerPlayer viewer = OFFLINE_SESSIONS.get(id);
		if (viewer != null) {
			viewer.closeContainer();
			Msg.info(viewer, "&eThey just logged in, so the offline editor was saved and closed.");
		}
	}

	private static boolean claimOffline(ServerPlayer viewer, NameAndId target) {
		if (OFFLINE_SESSIONS.containsKey(target.id())) {
			Msg.err(viewer, "Someone else is already editing " + target.name() + "'s offline data.");
			return false;
		}
		OFFLINE_SESSIONS.put(target.id(), viewer);
		return true;
	}

	// ---------------------------------------------------------------- menu

	private static void open(ServerPlayer viewer, InvView view, String title, Runnable onClose) {
		viewer.openMenu(new SimpleMenuProvider((id, inv, p) -> new InvMenu(id, inv, view, onClose), Text.of(title)));
	}

	/** 6-row chest: 3 rows main inventory, hotbar, then helmet/chest/legs/boots/offhand. */
	private static final class InvMenu extends ChestMenu {
		private final Runnable onClose;

		InvMenu(int id, Inventory viewerInv, InvView view, Runnable onClose) {
			super(MenuType.GENERIC_9x6, id, viewerInv, view, 6);
			this.onClose = onClose;
		}

		@Override
		public void clicked(int slot, int button, ContainerInput input, Player player) {
			if (slot >= 0 && slot < 54 && InvView.map(slot) < 0) return; // decoration slots
			if (input == ContainerInput.QUICK_CRAFT || input == ContainerInput.PICKUP_ALL) return; // keep it simple and safe
			super.clicked(slot, button, input, player);
		}

		@Override
		public ItemStack quickMoveStack(Player player, int index) {
			ItemStack moved = ItemStack.EMPTY;
			var slot = this.slots.get(index);
			if (slot.hasItem()) {
				ItemStack stack = slot.getItem();
				moved = stack.copy();
				if (index < 54) {
					if (!this.moveItemStackTo(stack, 54, this.slots.size(), true)) return ItemStack.EMPTY;
				} else if (!this.moveItemStackTo(stack, 0, 36, false)) {
					return ItemStack.EMPTY;
				}
				if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
				else slot.setChanged();
			}
			return moved;
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}

		@Override
		public void removed(Player player) {
			super.removed(player);
			if (onClose != null) onClose.run();
		}
	}

	/** Maps the 54 chest slots onto a 41-slot player inventory. */
	private static final class InvView implements Container {
		private final Container inv;
		private final ItemStack filler = ItemBuilder.filler(Items.STAINED_GLASS_PANE.black());

		InvView(Container inv) {
			this.inv = inv;
		}

		/** Chest slot -> inventory slot, or -1 for decoration. */
		static int map(int slot) {
			if (slot < 27) return slot + 9; // main inventory rows
			if (slot < 36) return slot - 27; // hotbar
			return switch (slot) {
				case 36 -> 39; // head
				case 37 -> 38; // chest
				case 38 -> 37; // legs
				case 39 -> 36; // feet
				case 40 -> 40; // offhand
				default -> -1;
			};
		}

		@Override
		public int getContainerSize() {
			return 54;
		}

		@Override
		public boolean isEmpty() {
			return inv.isEmpty();
		}

		@Override
		public ItemStack getItem(int slot) {
			int m = map(slot);
			return m < 0 ? filler : inv.getItem(m);
		}

		@Override
		public ItemStack removeItem(int slot, int count) {
			int m = map(slot);
			return m < 0 ? ItemStack.EMPTY : inv.removeItem(m, count);
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			int m = map(slot);
			return m < 0 ? ItemStack.EMPTY : inv.removeItemNoUpdate(m);
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			int m = map(slot);
			if (m >= 0) inv.setItem(m, stack);
		}

		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return map(slot) >= 0;
		}

		@Override
		public void setChanged() {
			inv.setChanged();
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}

		@Override
		public void clearContent() {
		}
	}

	// ---------------------------------------------------------------- offline IO

	private static CompoundTag load(MinecraftServer server, NameAndId target) {
		Optional<CompoundTag> tag = server.getPlayerList().loadPlayerData(target);
		return tag.orElse(null);
	}

	private static void saveInventory(MinecraftServer server, NameAndId target, CompoundTag tag, SimpleContainer inv) {
		try (var reporter = new ProblemReporter.ScopedCollector(VoidPanel.LOGGER)) {
			ValueInput in = TagValueInput.create(reporter, server.registryAccess(), tag);
			EntityEquipment eq = in.read("equipment", EntityEquipment.CODEC).orElseGet(EntityEquipment::new);
			eq.set(EquipmentSlot.FEET, inv.getItem(36));
			eq.set(EquipmentSlot.LEGS, inv.getItem(37));
			eq.set(EquipmentSlot.CHEST, inv.getItem(38));
			eq.set(EquipmentSlot.HEAD, inv.getItem(39));
			eq.set(EquipmentSlot.OFFHAND, inv.getItem(40));

			TagValueOutput out = TagValueOutput.createWithContext(reporter, server.registryAccess());
			var list = out.list("Inventory", ItemStackWithSlot.CODEC);
			for (int i = 0; i < 36; i++) if (!inv.getItem(i).isEmpty()) list.add(new ItemStackWithSlot(i, inv.getItem(i)));
			out.store("equipment", EntityEquipment.CODEC, eq);
			CompoundTag result = out.buildResult();
			putList(tag, "Inventory", result);
			if (result.contains("equipment")) tag.put("equipment", result.get("equipment"));
			else tag.remove("equipment");
		}
		write(server, target, tag);
	}

	private static void putList(CompoundTag tag, String key, CompoundTag result) {
		var value = result.get(key);
		tag.put(key, value != null ? value : new net.minecraft.nbt.ListTag());
	}

	private static void write(MinecraftServer server, NameAndId target, CompoundTag tag) {
		try {
			NbtUtils.addCurrentDataVersion(tag);
			Path dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
			Path tmp = Files.createTempFile(dir, target.id() + "-", ".dat");
			NbtIo.writeCompressed(tag, tmp);
			Util.safeReplaceFile(dir.resolve(target.id() + ".dat"), tmp, dir.resolve(target.id() + ".dat_old"));
		} catch (Exception e) {
			VoidPanel.LOGGER.error("Couldn't save offline data for {}", target.name(), e);
		}
	}
}
