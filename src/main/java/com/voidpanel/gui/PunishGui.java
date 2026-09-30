package com.voidpanel.gui;

import com.voidpanel.data.DataStore;
import com.voidpanel.feature.ChatPrompts;
import com.voidpanel.feature.Moderation;
import com.voidpanel.perm.Perm;
import com.voidpanel.perm.Perms;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Three clicks to punish: reason -> action -> duration. */
public final class PunishGui extends Gui {
	private static final String[][] REASONS = {
		{"Hacking / cheats", "minecraft:diamond_sword"},
		{"Griefing", "minecraft:tnt"},
		{"Toxicity", "minecraft:poisonous_potato"},
		{"Spam", "minecraft:paper"},
		{"Exploiting bugs", "minecraft:command_block"},
		{"Inappropriate build", "minecraft:bricks"},
	};
	private static final String[][] DURATIONS = {
		{"1 hour", "1h"}, {"6 hours", "6h"}, {"1 day", "1d"}, {"3 days", "3d"}, {"7 days", "7d"}, {"30 days", "30d"}, {"Permanent", "perm"}};

	private enum Step { REASON, ACTION, DURATION }

	private final NameAndId target;
	private Step step = Step.REASON;
	private String reason;
	private String action;

	public PunishGui(ServerPlayer player, NameAndId target) {
		super(player, 4, "&4&l✦ &8Punish " + target.name());
		this.target = target;
	}

	@Override
	protected void build() {
		for (int i = 0; i < 9; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.red()));
		var d = DataStore.get(target.id());
		set(4, ItemBuilder.head(target.id(), target.name()).name("&c&l" + target.name()).lore(
			"&7Past punishments: &f" + d.punishments.size(),
			"&7Reason: &f" + (reason != null ? reason : "&8(pick one)"),
			"&7Action: &f" + (action != null ? action : "&8(pick one)")).build());

		switch (step) {
			case REASON -> {
				for (int i = 0; i < REASONS.length; i++) {
					String r = REASONS[i][0];
					Item icon = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(REASONS[i][1]));
					set(10 + i, ItemBuilder.of(icon).name("&e&l" + r).clean().lore("&e▶ Click to choose").build(), c -> choose(r));
				}
				set(16, ItemBuilder.of(Items.WRITABLE_BOOK).name("&f&lCustom reason").lore("&e▶ Click, then type it").build(), c ->
					ChatPrompts.ask(player, "Type the reason:", null, r -> {
						choose(r);
						open();
					}));
			}
			case ACTION -> {
				action(10, Items.BELL, "Warn", "warn", "&7Sends a warning. Recorded in history.");
				action(11, Items.PAPER, "Mute", "mute", "&7Stops them chatting and messaging.");
				action(12, Items.LEATHER_BOOTS, "Kick", "kick", "&7Disconnects them (online only).");
				action(14, Items.IRON_BARS, "Ban", "ban", "&7Temporary or permanent ban.");
				if (Perms.has(player, Perm.BLACKLIST)) action(16, Items.STRUCTURE_VOID, "IP Blacklist", "blacklist", "&7Bans their IP address permanently.");
			}
			case DURATION -> {
				for (int i = 0; i < DURATIONS.length; i++) {
					String label = DURATIONS[i][0];
					long millis = Moderation.parseDuration(DURATIONS[i][1]);
					set(10 + i, ItemBuilder.of(i == DURATIONS.length - 1 ? Items.OBSIDIAN : Items.CLOCK).count(Math.min(64, i + 1)).name("&e&l" + label)
						.lore("&e▶ Click to " + action + " for " + label.toLowerCase()).build(), c -> execute(millis));
				}
			}
		}
		for (int i = 27; i < 36; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		if (step != Step.REASON) {
			set(27, ItemBuilder.of(Items.ARROW).name("&e← Back a step").build(), c -> {
				step = step == Step.DURATION ? Step.ACTION : Step.REASON;
				refresh();
			});
		} else {
			backButton(27, new PlayersGui(player, true));
		}
		set(31, ItemBuilder.of(Items.BOOK).name("&6History").build(), c -> new HistoryGui(player, target).open());
		closeButton(35);
		fill(Items.STAINED_GLASS_PANE.gray());
	}

	private void choose(String r) {
		reason = r;
		step = Step.ACTION;
		Msg.click(player);
		refresh();
	}

	private void action(int slot, Item icon, String label, String id, String desc) {
		set(slot, ItemBuilder.of(icon).name("&c&l" + label).clean().lore(desc, "", "&e▶ Click").build(), c -> {
			action = id;
			if (id.equals("mute") || id.equals("ban")) {
				step = Step.DURATION;
				refresh();
			} else {
				execute(0);
			}
		});
	}

	private void execute(long millis) {
		var server = player.level().getServer();
		String by = player.getGameProfile().name();
		Consumer<String> fb = s -> Msg.info(player, s);
		close();
		switch (action) {
			case "warn" -> Moderation.warn(server, by, target, reason, fb);
			case "mute" -> Moderation.mute(server, by, target, millis, reason, fb);
			case "ban" -> Moderation.tempban(server, by, target, millis, reason, fb);
			case "kick" -> {
				ServerPlayer online = server.getPlayerList().getPlayer(target.id());
				if (online == null) Msg.err(player, target.name() + " isn't online.");
				else Moderation.kick(server, by, online, reason, fb);
			}
			case "blacklist" -> {
				var ip = Moderation.ipOf(server, target.name());
				if (ip.isEmpty()) Msg.err(player, "No IP known for " + target.name() + ".");
				else Moderation.blacklist(server, by, ip.get(), target.name(), reason, fb);
			}
			default -> {
			}
		}
	}
}
