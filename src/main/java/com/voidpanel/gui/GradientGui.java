package com.voidpanel.gui;

import com.voidpanel.data.DataStore;
import com.voidpanel.feature.ChatPrompts;
import com.voidpanel.perm.Perm;
import com.voidpanel.perm.Perms;
import com.voidpanel.util.ItemBuilder;
import com.voidpanel.util.Msg;
import com.voidpanel.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Build a colour gradient out of blocks: click wool (or any block in your inventory) to add
 * colour stops left to right, then copy the result or apply it as your nickname.
 */
public final class GradientGui extends Gui {
	private static final int MAX_STOPS = 7;
	private static final int FIRST_STOP = 10;
	/** Palette blocks and the colour each one stands for. */
	private static final Map<Item, Integer> PALETTE = new LinkedHashMap<>();

	static {
		PALETTE.put(Items.WOOL.white(), 0xFFFFFF);
		PALETTE.put(Items.WOOL.lightGray(), 0xC8C8C8);
		PALETTE.put(Items.WOOL.gray(), 0x7A7A7A);
		PALETTE.put(Items.WOOL.black(), 0x2B2B2B);
		PALETTE.put(Items.WOOL.brown(), 0x8B5A2B);
		PALETTE.put(Items.WOOL.red(), 0xFF3B30);
		PALETTE.put(Items.WOOL.orange(), 0xFF8C1A);
		PALETTE.put(Items.WOOL.yellow(), 0xFFE135);
		PALETTE.put(Items.WOOL.lime(), 0x7CFC00);
		PALETTE.put(Items.WOOL.green(), 0x2E8B3A);
		PALETTE.put(Items.WOOL.cyan(), 0x17C3B2);
		PALETTE.put(Items.WOOL.lightBlue(), 0x5AC8FA);
		PALETTE.put(Items.WOOL.blue(), 0x3D5AFE);
		PALETTE.put(Items.WOOL.purple(), 0x9B4DFF);
		PALETTE.put(Items.WOOL.magenta(), 0xE040FB);
		PALETTE.put(Items.WOOL.pink(), 0xFF8FC8);
		PALETTE.put(Items.GOLD_BLOCK, 0xFFD700);
		PALETTE.put(Items.DIAMOND_BLOCK, 0x4EE2EC);
		PALETTE.put(Items.EMERALD_BLOCK, 0x17DD62);
		PALETTE.put(Items.LAPIS_BLOCK, 0x1F4FBF);
		PALETTE.put(Items.REDSTONE_BLOCK, 0xD11A1A);
		PALETTE.put(Items.AMETHYST_BLOCK, 0xA66BFF);
		PALETTE.put(Items.QUARTZ_BLOCK, 0xF4F0E8);
		PALETTE.put(Items.PRISMARINE, 0x63B7A6);
		PALETTE.put(Items.GLOWSTONE, 0xFFD27A);
		PALETTE.put(Items.CHERRY_LEAVES, 0xFFB7D5);
		PALETTE.put(Items.CRYING_OBSIDIAN, 0x6A0DAD);
	}

	private record Stop(Item item, int rgb) {}

	private final List<Stop> stops = new ArrayList<>();
	private String text;
	private boolean bold;

	public GradientGui(ServerPlayer player) {
		super(player, 6, "&5&l✦ &8Gradient Maker");
		String nick = DataStore.get(player.getUUID()).nick;
		this.text = nick != null ? Text.strip(nick) : player.getGameProfile().name();
	}

	@Override
	protected void build() {
		for (int i = 0; i < 9; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		set(4, ItemBuilder.of(Items.NAME_TAG).name(stops.isEmpty() ? Text.of("&f" + text) : Text.of(gradient()))
			.lore("&7Preview of your gradient.", "", "&8Stops: " + stops.size() + "/" + MAX_STOPS).glint(!stops.isEmpty()).build());

		// Row 1: colour stops, left to right.
		set(9, ItemBuilder.filler(Items.STAINED_GLASS_PANE.gray()));
		set(17, ItemBuilder.filler(Items.STAINED_GLASS_PANE.gray()));
		for (int i = 0; i < MAX_STOPS; i++) {
			int slot = FIRST_STOP + i;
			if (i < stops.size()) {
				Stop s = stops.get(i);
				int index = i;
				set(slot, ItemBuilder.of(s.item).name(Text.of("&#" + hex(s.rgb) + "&lStop " + (i + 1))).clean()
					.lore("&7Colour: " + "&#" + hex(s.rgb) + "#" + hex(s.rgb).toUpperCase(), "", "&c▶ Click to remove").build(), c -> {
						stops.remove(index);
						Msg.sound(player, SoundEvents.NOTE_BLOCK_BASS, 0.6f, 0.8f);
						refresh();
					});
			} else {
				set(slot, ItemBuilder.of(Items.STAINED_GLASS_PANE.lightGray()).name("&7Empty stop " + (i + 1))
					.lore("&7Click a block below, or any", "&7block in your inventory.").build());
			}
		}

		// Rows 2-4: palette.
		int slot = 18;
		for (var e : PALETTE.entrySet()) {
			Item item = e.getKey();
			int rgb = e.getValue();
			set(slot++, ItemBuilder.of(item).name(Text.of("&#" + hex(rgb) + "■ #" + hex(rgb).toUpperCase())).clean()
				.lore("&e▶ Click to add as the next stop").build(), c -> add(item, rgb));
		}

		// Row 5: controls.
		for (int i = 45; i < 54; i++) set(i, ItemBuilder.filler(Items.STAINED_GLASS_PANE.black()));
		backButton(45, new MainGui(player));
		set(46, ItemBuilder.of(Items.WRITABLE_BOOK).name("&e&lText: &f" + text).lore("&7The text to colour.", "", "&e▶ Click to change").build(), c ->
			ChatPrompts.ask(player, "Type the text for your gradient &8(letters, numbers, _ and spaces)&7:", text, input -> {
				String clean = input.replaceAll("[^A-Za-z0-9_ ]", "");
				if (clean.isEmpty() || clean.length() > 24) Msg.err(player, "Use 1-24 letters, numbers, _ or spaces.");
				else text = clean;
				open();
			}));
		set(47, ItemBuilder.of(bold ? Items.ANVIL : Items.FEATHER).name(bold ? "&f&lBold: ON" : "&fBold: OFF").lore("&e▶ Click to toggle").build(), c -> {
			bold = !bold;
			Msg.click(player);
			refresh();
		});
		set(48, ItemBuilder.of(Items.SPONGE).name("&c&lClear stops").build(), c -> {
			stops.clear();
			Msg.click(player);
			refresh();
		});
		set(49, ItemBuilder.of(stops.isEmpty() ? Items.DYE.gray() : Items.EMERALD).name(stops.isEmpty() ? "&7Add colours first" : "&a&l✔ Generate")
			.lore("&7Sends the code to your chat with", "&7a &e[Copy] &7button" + (Perms.has(player, Perm.NICK_COLOR) ? " and &a[Use as nick]&7." : "."))
			.glint(!stops.isEmpty()).build(), c -> {
				if (stops.isEmpty()) {
					Msg.err(player, "Add at least one colour stop.");
					return;
				}
				generate();
			});
		set(51, ItemBuilder.of(Items.BOOK).name("&d&lHow to use").lore(
			"&71. Click blocks to add colour stops,",
			"&7   left to right (up to " + MAX_STOPS + ").",
			"&72. Any block in &fyour inventory &7works",
			"&7   too, using its map colour.",
			"&73. Hit &aGenerate &7and copy the code.").build());
		closeButton(53);
	}

	@Override
	protected void onInventoryClick(ItemStack stack, Click click) {
		if (stack.isEmpty()) return;
		Integer rgb = PALETTE.get(stack.getItem());
		if (rgb == null && stack.getItem() instanceof BlockItem block) rgb = block.getBlock().defaultMapColor().col;
		if (rgb == null || rgb == 0) {
			Msg.err(player, "That item has no colour, try a block.");
			return;
		}
		add(stack.getItem(), rgb);
	}

	private void add(Item item, int rgb) {
		if (stops.size() >= MAX_STOPS) {
			Msg.err(player, "That's the maximum of " + MAX_STOPS + " stops.");
			return;
		}
		stops.add(new Stop(item, rgb));
		Msg.sound(player, SoundEvents.NOTE_BLOCK_PLING, 0.5f, 0.8f + stops.size() * 0.15f);
		refresh();
	}

	private void generate() {
		String code = gradient();
		close();
		player.sendSystemMessage(Component.empty());
		player.sendSystemMessage(Msg.prefixed(Text.of("&7Your gradient: ").append(Text.of(code))));
		MutableComponent buttons = Msg.prefixed(Component.empty());
		buttons.append(Text.of("&e&l[Copy]").withStyle(s -> s
			.withClickEvent(new ClickEvent.CopyToClipboard(code))
			.withHoverEvent(new HoverEvent.ShowText(Text.of("&7Copy the code to your clipboard:\n&f" + code)))));
		if (Perms.has(player, Perm.NICK) && Perms.has(player, Perm.NICK_COLOR) && !text.contains(" ")) {
			buttons.append(Component.literal("  "));
			buttons.append(Msg.button("&a&l[Use as nick]", "/nick " + code, "&7Set this as your nickname"));
		}
		buttons.append(Component.literal("  "));
		buttons.append(Msg.button("&d[Edit]", "/gradient", "&7Back to the gradient maker"));
		player.sendSystemMessage(buttons);
		Msg.sound(player, SoundEvents.PLAYER_LEVELUP, 0.5f, 1.5f);
	}

	/** "&#rrggbbV&#rrggbbo..." with each character coloured along the stops. */
	private String gradient() {
		StringBuilder sb = new StringBuilder();
		int n = text.length();
		for (int i = 0; i < n; i++) {
			char ch = text.charAt(i);
			if (ch == ' ') {
				sb.append(' ');
				continue;
			}
			double t = n <= 1 ? 0 : (double) i / (n - 1);
			sb.append("&#").append(hex(colorAt(t)));
			if (bold) sb.append("&l");
			sb.append(ch);
		}
		return sb.toString();
	}

	private int colorAt(double t) {
		if (stops.size() == 1) return stops.get(0).rgb;
		double pos = t * (stops.size() - 1);
		int a = Math.min((int) Math.floor(pos), stops.size() - 2);
		double f = pos - a;
		int c1 = stops.get(a).rgb, c2 = stops.get(a + 1).rgb;
		int r = (int) Math.round(((c1 >> 16) & 255) * (1 - f) + ((c2 >> 16) & 255) * f);
		int g = (int) Math.round(((c1 >> 8) & 255) * (1 - f) + ((c2 >> 8) & 255) * f);
		int b = (int) Math.round((c1 & 255) * (1 - f) + (c2 & 255) * f);
		return (r << 16) | (g << 8) | b;
	}

	private static String hex(int rgb) {
		return String.format("%06x", rgb & 0xFFFFFF);
	}
}
