package com.voidpanel.gui;

import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import com.voidpanel.util.Scheduler;
import com.voidpanel.util.Text;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A server-side chest GUI. Items can never be taken out; clicks are routed to handlers.
 * Subclasses fill the slots in {@link #build()}.
 */
public abstract class Gui {
	private static final Set<Gui> OPEN = new HashSet<>();
	/** Last handled click per player, to swallow accidental double clicks. */
	private static final Map<java.util.UUID, Long> LAST_CLICK = new HashMap<>();
	private static final long CLICK_GAP_NANOS = 80_000_000L;

	protected final ServerPlayer player;
	protected final int rows;
	private final String title;
	private final SimpleContainer container;
	private final Map<Integer, Consumer<Click>> handlers = new HashMap<>();
	private Menu menu;
	/** Set once the menu is closing or being replaced: late clicks are ignored, so nothing runs twice. */
	private boolean dead;

	protected Gui(ServerPlayer player, int rows, String legacyTitle) {
		this.player = player;
		this.rows = rows;
		this.title = legacyTitle;
		this.container = new SimpleContainer(rows * 9);
	}

	/** Populate the slots. Called on open and on {@link #refresh()}. */
	protected abstract void build();

	/** Called every server tick while open. */
	protected void tick() {}

	protected void onClose() {}

	/** A click on the player's own inventory while this GUI is open. Nothing moves. */
	protected void onInventoryClick(ItemStack stack, Click click) {}

	public final void open() {
		Scheduler.next(() -> {
			if (player.hasDisconnected()) return;
			refresh();
			player.openMenu(new SimpleMenuProvider((id, inv, p) -> {
				menu = new Menu(id, inv, this);
				return menu;
			}, Text.of(title)));
		});
	}

	public final void refresh() {
		handlers.clear();
		container.clearContent();
		build();
	}

	protected final void close() {
		dead = true;
		Scheduler.next(() -> {
			if (player.containerMenu == menu) player.closeContainer();
		});
	}

	protected final boolean isOpen() {
		return menu != null && player.containerMenu == menu;
	}

	protected final void set(int slot, ItemStack stack) {
		container.setItem(slot, ItemBuilder.mark(stack));
		handlers.remove(slot);
	}

	protected final void set(int slot, ItemStack stack, Consumer<Click> handler) {
		container.setItem(slot, ItemBuilder.mark(stack));
		handlers.put(slot, handler);
	}

	protected final ItemStack get(int slot) {
		return container.getItem(slot);
	}

	/** Fill every empty slot with a nameless pane. */
	protected final void fill(Item pane) {
		ItemStack filler = ItemBuilder.filler(pane);
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).isEmpty()) container.setItem(i, filler.copy());
		}
	}

	protected final void fillRow(int row, Item pane) {
		ItemStack filler = ItemBuilder.filler(pane);
		for (int i = row * 9; i < row * 9 + 9; i++) set(i, filler.copy());
	}

	protected final void backButton(int slot, Gui parent) {
		set(slot, ItemBuilder.of(Items.ARROW).name("&e← Back").lore("&7Return to the previous menu.").build(), c -> {
			Msg.click(player);
			parent.open();
		});
	}

	protected final void closeButton(int slot) {
		set(slot, ItemBuilder.of(Items.BARRIER).name("&c✘ Close").build(), c -> {
			Msg.click(player);
			close();
		});
	}

	/** Place one page of entries into slots [first, first+perPage). Returns the page count. */
	protected final <T> int page(java.util.List<T> entries, int first, int perPage, int page, java.util.function.BiConsumer<Integer, T> place) {
		int pages = Math.max(1, (entries.size() + perPage - 1) / perPage);
		int p = Math.max(0, Math.min(page, pages - 1));
		for (int i = 0; i < perPage; i++) {
			int idx = p * perPage + i;
			if (idx >= entries.size()) break;
			place.accept(first + i, entries.get(idx));
		}
		return pages;
	}

	/** Previous/next arrows plus a page indicator. */
	protected final void pageButtons(int prevSlot, int infoSlot, int nextSlot, int page, int pages, java.util.function.IntConsumer goTo) {
		if (page > 0) {
			set(prevSlot, ItemBuilder.of(Items.SPECTRAL_ARROW).name("&e« Previous page").lore("&7Page " + page + " of " + pages).build(), c -> {
				Msg.click(player);
				goTo.accept(page - 1);
			});
		}
		if (page < pages - 1) {
			set(nextSlot, ItemBuilder.of(Items.SPECTRAL_ARROW).name("&eNext page »").lore("&7Page " + (page + 2) + " of " + pages).build(), c -> {
				Msg.click(player);
				goTo.accept(page + 1);
			});
		}
		if (infoSlot >= 0) {
			set(infoSlot, ItemBuilder.of(Items.PAPER).name("&fPage &d" + (page + 1) + "&7/&d" + pages).count(page + 1).build());
		}
	}

	public static void tickAll() {
		for (Gui gui : Set.copyOf(OPEN)) {
			if (gui.isOpen()) gui.tick();
			else OPEN.remove(gui);
		}
	}

	private void handle(int slot, ContainerInput input, int button) {
		if (dead) return;
		Click click = new Click(input, button);
		if (click.ignored()) return;
		long now = System.nanoTime();
		Long last = LAST_CLICK.put(player.getUUID(), now);
		if (last != null && now - last < CLICK_GAP_NANOS) return;
		if (slot >= rows * 9) {
			if (menu != null && slot < menu.slots.size()) onInventoryClick(menu.slots.get(slot).getItem().copy(), click);
			return;
		}
		Consumer<Click> h = handlers.get(slot);
		if (h != null) h.accept(click);
	}

	private static MenuType<ChestMenu> typeFor(int rows) {
		return switch (rows) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
	}

	/** What kind of click happened. */
	public record Click(ContainerInput input, int button) {
		public boolean shift() {
			return input == ContainerInput.QUICK_MOVE;
		}

		public boolean left() {
			return button == 0;
		}

		public boolean right() {
			return button == 1;
		}

		boolean ignored() {
			return input != ContainerInput.PICKUP && input != ContainerInput.QUICK_MOVE;
		}
	}

	private static final class Menu extends ChestMenu {
		private final Gui gui;

		Menu(int id, Inventory inv, Gui gui) {
			super(typeFor(gui.rows), id, inv, gui.container, gui.rows);
			this.gui = gui;
			gui.dead = false;
			OPEN.add(gui);
		}

		@Override
		public void clicked(int slot, int button, ContainerInput input, Player player) {
			// Never call super: nothing moves. The client's prediction is corrected
			// by broadcastChanges() right after this returns.
			if (slot >= 0 && slot < this.slots.size()) gui.handle(slot, input, button);
		}

		@Override
		public ItemStack quickMoveStack(Player player, int slot) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}

		@Override
		public void removed(Player player) {
			super.removed(player);
			gui.dead = true;
			OPEN.remove(gui);
			gui.onClose();
		}
	}
}
