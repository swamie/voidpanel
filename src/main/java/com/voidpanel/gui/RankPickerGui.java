package com.voidpanel.gui;

import com.voidpanel.data.DataStore.Rank;
import com.voidpanel.feature.Ranks;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import com.voidpanel.util.Text;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.Items;

/** Pick a rank for one player. */
public final class RankPickerGui extends Gui {
	private final NameAndId target;
	private final Gui parent;

	public RankPickerGui(ServerPlayer player, NameAndId target, Gui parent) {
		super(player, 4, "&5&l✦ &8Rank for " + target.name());
		this.target = target;
		this.parent = parent;
	}

	@Override
	protected void build() {
		Rank current = Ranks.of(target.id());
		List<Rank> ranks = Ranks.sorted();
		page(ranks, 0, 27, 0, (slot, r) -> set(slot, ItemBuilder.of(RanksGui.icon(r)).name(Text.of(r.display)).glint(r == current).clean()
			.lore(r == current ? "&a✔ Current rank " + Ranks.expiryNote(target.id()) : "&e▶ Click, then pick how long").build(),
			c -> new RankDurationGui(player, r, target, parent).open()));
		for (int i = 27; i < 36; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		backButton(27, parent);
		closeButton(35);
	}
}
