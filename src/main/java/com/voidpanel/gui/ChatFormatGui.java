package com.voidpanel.gui;

import com.voidpanel.config.Config;
import com.voidpanel.feature.Chat;
import com.voidpanel.feature.ChatPrompts;
import com.voidpanel.feature.Emotes;
import com.voidpanel.feature.Messages;
import com.voidpanel.util.Text;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Edit chat / join / leave / welcome formats with live previews. Saved to config/voidpanel.json. */
public final class ChatFormatGui extends Gui {
	private static final String[][] CHAT_PRESETS = {
		{"Name: message", "{rank}{tag}{display}&7: &f{message}"},
		{"Name » message", "{rank}{tag}{display} &8» &f{message}"},
		{"<Name> message", "&7<{rank}{tag}{display}&7> &f{message}"},
		{"Name ┃ message", "{rank}{tag}{display} &8┃ &7{message}"},
		{"No rank shown", "{tag}{display}&7: &f{message}"},
	};

	public ChatFormatGui(ServerPlayer player) {
		super(player, 6, "&5&l✦ &8Chat Formatting");
	}

	@Override
	protected void build() {
		Config cfg = Config.get();
		Config def = Config.defaults();
		for (int i = 0; i < 9; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		set(4, ItemBuilder.of(Items.KNOWLEDGE_BOOK).name("&d&lPlaceholders & colours").lore(
			"&f{rank} &7rank prefix  &f{tag} &7chat tag",
			"&f{display} &7nickname (or name)",
			"&f{name} &7real username",
			"&f{message} &7the message",
			"&f{count} &7player number (welcome only)",
			"&f{prefix} {from} {to} &7private messages",
			"",
			"&7Colours: &0&&00 &1&&11 &2&&22 &3&&33 &4&&44 &5&&55 &6&&66 &7&&77",
			"         &8&&88 &9&&99 &a&&aa &b&&bb &c&&cc &d&&dd &e&&ee &f&&ff",
			"&7Styles: &l&&ll&r &7&o&&oo&r &7&n&&nn&r &7&m&&mm&r &7&&rr = reset",
			"&7Hex: &f&&#RRGGBB").build());

		// Row 1: public chat formats
		Function<String, Component> chat = f -> Chat.preview(f, player);
		entry(10, Items.OAK_SIGN, "Chat message", c -> c.chatFormat, (c, v) -> c.chatFormat = v, def.chatFormat, chat);
		entry(12, Items.DYE.lime(), "Join message", c -> c.joinFormat, (c, v) -> c.joinFormat = v, def.joinFormat, chat);
		entry(14, Items.DYE.red(), "Leave message", c -> c.leaveFormat, (c, v) -> c.leaveFormat = v, def.leaveFormat, chat);
		entry(16, Items.CAKE, "First-join welcome", c -> c.firstJoinFormat, (c, v) -> c.firstJoinFormat = v, def.firstJoinFormat, chat);

		// Row 2: chat presets
		for (int i = 0; i < CHAT_PRESETS.length; i++) {
			String label = CHAT_PRESETS[i][0];
			String format = CHAT_PRESETS[i][1];
			boolean active = format.equals(cfg.chatFormat);
			set(20 + i, ItemBuilder.of(active ? Items.GLOW_ITEM_FRAME : Items.ITEM_FRAME).name((active ? "&a&l" : "&f") + "Preset: " + label).glint(active)
				.lore(Component.literal("Preview:").withStyle(s -> s.withColor(0xAAAAAA)))
				.lore(Chat.preview(format, player))
				.lore("", active ? "&aIn use" : "&e▶ Click to use for chat").build(), c -> {
					Config.get().chatFormat = format;
					Config.save();
					Msg.ok(player, "Chat format set to &f" + label + "&a.");
					refresh();
				});
		}

		// Row 3: private messages
		Function<String, Component> msg = f -> Messages.preview(f, player);
		entry(29, Items.WRITABLE_BOOK, "Message prefix", c -> c.msgPrefix, (c, v) -> c.msgPrefix = v, def.msgPrefix,
			f -> Text.of(f).append(Text.of("&7(example)")));
		entry(31, Items.PAPER, "Message sent", c -> c.msgToFormat, (c, v) -> c.msgToFormat = v, def.msgToFormat, msg);
		entry(33, Items.FILLED_MAP, "Message received", c -> c.msgFromFormat, (c, v) -> c.msgFromFormat = v, def.msgFromFormat, msg);

		// Row 4: emotes
		boolean emotes = cfg.emotesEnabled;
		set(39, ItemBuilder.of(emotes ? Items.GOLD_BLOCK : Items.COAL_BLOCK).name(emotes ? "&a&lEmotes: ON" : "&c&lEmotes: OFF").glint(emotes)
			.lore("&7Hypixel-style chat emotes, e.g.",
				"&fo/ &8→ &d( ﾟ◡ﾟ)/   &f<3 &8→ &c❤",
				"&f:shrug: &8→ &e¯\\_(ツ)_/¯",
				"",
				"&7Who can use them is set by the",
				"&fchat.emotes &7permission.",
				"",
				"&e▶ Click to turn " + (emotes ? "off" : "on") + " for everyone").build(), c -> {
				Config.get().emotesEnabled = !emotes;
				Config.save();
				Msg.ok(player, "Emotes turned " + (!emotes ? "on" : "&coff") + "&a for the server.");
				refresh();
			});
		set(41, ItemBuilder.of(Items.BOOK).name("&d&lEmote list").lore("&7Show every emote in chat.", "", "&e▶ Click").build(), c -> {
			close();
			Emotes.list(player);
		});

		for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		backButton(45, new MainGui(player));
		closeButton(49);
		fill(Items.STAINED_GLASS_PANE.gray());
	}

	private void entry(int slot, Item icon, String label, Function<Config, String> getter, BiConsumer<Config, String> setter, String fallback,
		Function<String, Component> preview) {
		String current = getter.apply(Config.get());
		set(slot, ItemBuilder.of(icon).name("&e&l" + label)
			.lore(Component.literal("Format: ").withStyle(s -> s.withColor(0xAAAAAA)).append(Component.literal(current).withStyle(s -> s.withColor(0xFFFFFF))))
			.lore(Component.literal("Preview:").withStyle(s -> s.withColor(0xAAAAAA)))
			.lore(preview.apply(current))
			.lore("", "&eLeft-click &7to edit in chat", "&eRight-click &7to reset to default").build(), c -> {
				if (c.right()) {
					setter.accept(Config.get(), fallback);
					Config.save();
					Msg.ok(player, label + " reset to default.");
					refresh();
					return;
				}
				ChatPrompts.ask(player, "Type the new &f" + label.toLowerCase() + "&7. Click &eEdit current &7to start from what's there now.", current, input -> {
					setter.accept(Config.get(), input);
					Config.save();
					player.sendSystemMessage(Msg.prefixed(Component.literal("Saved! Preview: ").withStyle(s -> s.withColor(0x55FF55))).append(preview.apply(input)));
					new ChatFormatGui(player).open();
				});
			});
	}
}
