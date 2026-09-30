package com.voidpanel.gui;

import com.voidpanel.config.Config;
import com.voidpanel.data.DataStore;
import com.voidpanel.feature.Daily;
import com.voidpanel.perm.Perm;
import com.voidpanel.perm.Perms;
import com.voidpanel.util.Give;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** /daily: a 7-day calendar. Claiming spins a little reel, then hands out the day's rewards. */
public final class DailyGui extends Gui {
	private static final int FIRST_DAY = 10;
	private final RandomSource random = RandomSource.create();
	private boolean spinning;
	private int spinStep, wait;
	private List<ItemStack> prize = List.of();
	private final List<ItemStack> reel = new ArrayList<>();

	public DailyGui(ServerPlayer player) {
		super(player, 5, "&5&l✦ &8Daily Rewards");
	}

	@Override
	protected void build() {
		var d = DataStore.get(player.getUUID());
		boolean can = Daily.canClaim(player);
		int next = Daily.nextIndex(player);
		boolean editor = Perms.has(player, Perm.DAILY_EDIT);

		for (int i = 0; i < 9; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.purple()));
		int streak = d.lastDailyDay >= Daily.today() - 1 ? d.dailyStreak : 0;
		set(4, ItemBuilder.of(Items.BLAZE_POWDER).name("&6&lStreak: &f" + streak + " day" + (streak == 1 ? "" : "s")).count(Math.max(1, Math.min(64, streak)))
			.lore("&7Claim every day to keep it going.", "&7Miss a day and it starts over.").build());

		for (int day = 0; day < Daily.DAYS; day++) {
			int dayIndex = day;
			List<ItemStack> rewards = Daily.rewards(day);
			List<String> lore = new ArrayList<>();
			for (ItemStack r : rewards) lore.add("&8▪ &f" + r.getCount() + "x " + r.getHoverName().getString());
			lore.add("");
			Item icon;
			String name;
			boolean glint = false;
			boolean claimedToday = !can && day == next;
			if (claimedToday || (day < next)) {
				icon = Items.STAINED_GLASS_PANE.lime();
				name = "&a&lDay " + (day + 1) + " &a✔";
				lore.add("&aClaimed");
			} else if (day == next && can) {
				icon = Items.CHEST_MINECART;
				name = "&e&lDay " + (day + 1) + " &e★";
				glint = true;
				lore.add("&e▶ Click to claim!");
			} else {
				icon = Items.MINECART;
				name = "&7&lDay " + (day + 1);
				lore.add("&8Come back later");
			}
			if (editor) lore.addAll(List.of("", "&8Admin: &eshift-click &8to edit"));
			set(FIRST_DAY + day, ItemBuilder.of(icon).name(name).count(day + 1).glint(glint).lore(lore).build(), c -> {
				if (editor && c.shift()) {
					new DailyEditorGui(player, dayIndex).open();
				} else if (dayIndex == next && can && !spinning) {
					startSpin();
				}
			});
		}

		if (!can) {
			Duration left = Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay());
			set(31, ItemBuilder.of(Items.CLOCK).name("&7Next reward in &f" + left.toHours() + "h " + left.toMinutesPart() + "m").build());
		}
		for (int i = 36; i < 45; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		backButton(36, new MainGui(player));
		closeButton(44);
	}

	private void startSpin() {
		prize = Daily.claim(player);
		spinning = true;
		spinStep = 0;
		wait = 0;
		reel.clear();
		for (int i = 0; i < 7; i++) reel.add(randomReward());
	}

	@Override
	protected void tick() {
		if (!spinning) return;
		if (--wait > 0) return;
		int total = 26;
		if (spinStep >= total) {
			finish();
			return;
		}
		wait = spinStep < 14 ? 1 : spinStep < 20 ? 2 : spinStep < 24 ? 3 : 5;
		reel.remove(0);
		// The item that lands in the middle (index 3) on the last step is the real prize.
		reel.add(total - spinStep == 4 && !prize.isEmpty() ? prize.get(0).copy() : randomReward());
		spinStep++;
		for (int i = 0; i < 7; i++) set(28 + i, ItemBuilder.of(reel.get(i)).build());
		set(22, ItemBuilder.of(Items.HOPPER).name("&e▼").build());
		Msg.sound(player, SoundEvents.NOTE_BLOCK_HAT, 0.5f, 0.8f + spinStep / 26f);
	}

	@Override
	protected void onClose() {
		// Closed mid-spin: the reward is already claimed, so hand it over now.
		if (spinning) finish();
	}

	private void finish() {
		if (!spinning) return;
		spinning = false;
		for (ItemStack s : prize) Give.item(player, s.copy());
		Msg.sound(player, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.3f);
		StringBuilder list = new StringBuilder();
		for (ItemStack s : prize) list.append(list.isEmpty() ? "" : "&7, ").append("&f").append(s.getCount()).append("x ").append(s.getHoverName().getString());
		Msg.ok(player, "Daily reward claimed! &7(" + list + "&7) &6Streak: " + DataStore.get(player.getUUID()).dailyStreak);
		refresh();
	}

	private ItemStack randomReward() {
		List<ItemStack> all = new ArrayList<>();
		for (int i = 0; i < Daily.DAYS; i++) all.addAll(Daily.rewards(i));
		if (all.isEmpty()) return new ItemStack(Items.CHEST);
		return all.get(random.nextInt(all.size())).copy();
	}

	/** Admin: click items in your inventory to add them to a day, click a reward to remove it. */
	public static final class DailyEditorGui extends Gui {
		private final int day;

		public DailyEditorGui(ServerPlayer player, int day) {
			super(player, 3, "&5&l✦ &8Edit Day " + (day + 1));
			this.day = day;
		}

		@Override
		protected void build() {
			List<String> entries = Config.get().dailyRewards.get(day);
			page(entries, 0, 18, 0, (slot, entry) -> {
				ItemStack s = Daily.parse(entry);
				if (s.isEmpty()) return;
				set(slot, ItemBuilder.of(s.getItem()).count(s.getCount()).lore("&7" + entry, "", "&c▶ Click to remove").build(), c -> {
					entries.remove(entry);
					Config.save();
					refresh();
				});
			});
			for (int i = 18; i < 27; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
			set(22, ItemBuilder.of(Items.BOOK).name("&e&lHow to edit").lore("&7Click any item in &fyour inventory", "&7to add it (with its stack size).", "&7Click a reward above to remove it.").build());
			backButton(18, new DailyGui(player));
		}

		@Override
		protected void onInventoryClick(ItemStack stack, Click click) {
			if (stack.isEmpty()) return;
			List<String> entries = Config.get().dailyRewards.get(day);
			if (entries.size() >= 18) {
				Msg.err(player, "That's plenty for one day!");
				return;
			}
			entries.add(Daily.format(stack));
			Config.save();
			Msg.click(player);
			refresh();
		}
	}
}
