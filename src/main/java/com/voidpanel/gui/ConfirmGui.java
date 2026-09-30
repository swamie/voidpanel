package com.voidpanel.gui;

import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Yes / no dialog. */
public final class ConfirmGui extends Gui {
	private final ItemStack subject;
	private final Runnable onYes;
	private final Runnable onNo;

	public ConfirmGui(ServerPlayer player, String title, ItemStack subject, Runnable onYes, Runnable onNo) {
		super(player, 3, title);
		this.subject = subject;
		this.onYes = onYes;
		this.onNo = onNo;
	}

	@Override
	protected void build() {
		fill(Items.STAINED_GLASS_PANE.gray());
		for (int i : new int[] {10, 11, 12}) {
			set(i, ItemBuilder.of(Items.CONCRETE.lime()).name("&a&l✔ Confirm").build(), c -> {
				Msg.click(player);
				close();
				onYes.run();
			});
		}
		set(13, subject);
		for (int i : new int[] {14, 15, 16}) {
			set(i, ItemBuilder.of(Items.CONCRETE.red()).name("&c&l✘ Cancel").build(), c -> {
				Msg.click(player);
				if (onNo != null) onNo.run();
				else close();
			});
		}
	}
}
