package com.voidpanel.feature;

import com.voidpanel.util.DyeItems;
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
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.voidpanel.util.NameAndId;
import net.minecraft.Util;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;

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
		for (var e : readList(server, tag, "Inventory").entrySet()) {
			int slot = e.getKey();
			int idx = slot >= 0 && slot < 36 ? slot : slot == 100 ? 36 : slot == 101 ? 37 : slot == 102 ? 38 : slot == 103 ? 39 : slot == -106 ? 40 : -1;
			if (idx >= 0) inv.setItem(idx, e.getValue());
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
		for (var e : readList(server, tag, "EnderItems").entrySet()) {
			if (e.getKey() >= 0 && e.getKey() < 27) ender.setItem(e.getKey(), e.getValue());
		}
		Runnable onClose = () -> {
			OFFLINE_SESSIONS.remove(target.id());
			var list = new ListTag();
			for (int i = 0; i < 27; i++) writeStack(server, list, i, ender.getItem(i));
			tag.put("EnderItems", list);
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
		public void clicked(int slot, int button, ClickType input, Player player) {
			if (slot >= 0 && slot < 54 && InvView.map(slot) < 0) return; // decoration slots
			if (input == ClickType.QUICK_CRAFT || input == ClickType.PICKUP_ALL) return; // keep it simple and safe
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
		private final ItemStack filler = ItemBuilder.filler(DyeItems.STAINED_GLASS_PANE.black());

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
		Path file = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(target.id() + ".dat");
		if (!Files.exists(file)) return null;
		try {
			return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
		} catch (Exception e) {
			VoidPanel.LOGGER.error("Couldn't read offline data for {}", target.name(), e);
			return null;
		}
	}

	private static Map<Integer, ItemStack> readList(MinecraftServer server, CompoundTag tag, String key) {
		Map<Integer, ItemStack> out = new HashMap<>();
		ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag c = list.getCompound(i);
			ItemStack.parse(server.registryAccess(), c).ifPresent(st -> out.put((int) c.getByte("Slot"), st));
		}
		return out;
	}

	private static void writeStack(MinecraftServer server, ListTag list, int slot, ItemStack stack) {
		if (stack.isEmpty()) return;
		Tag saved = stack.save(server.registryAccess(), new CompoundTag());
		if (saved instanceof CompoundTag c) {
			c.putByte("Slot", (byte) slot);
			list.add(c);
		}
	}

	private static void saveInventory(MinecraftServer server, NameAndId target, CompoundTag tag, SimpleContainer inv) {
		ListTag list = new ListTag();
		for (int i = 0; i < 36; i++) writeStack(server, list, i, inv.getItem(i));
		writeStack(server, list, 100, inv.getItem(36));
		writeStack(server, list, 101, inv.getItem(37));
		writeStack(server, list, 102, inv.getItem(38));
		writeStack(server, list, 103, inv.getItem(39));
		writeStack(server, list, -106, inv.getItem(40));
		tag.put("Inventory", list);
		write(server, target, tag);
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
