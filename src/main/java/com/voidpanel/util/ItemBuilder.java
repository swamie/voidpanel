package com.voidpanel.util;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.TooltipDisplay;

/** Fluent helper for GUI items. Names and lore are never italic. */
public final class ItemBuilder {
	private final ItemStack stack;
	private final List<Component> lore = new ArrayList<>();

	private ItemBuilder(ItemStack stack) {
		this.stack = stack;
	}

	public static ItemBuilder of(Item item) {
		return new ItemBuilder(new ItemStack(item));
	}

	public static ItemBuilder of(ItemStack stack) {
		return new ItemBuilder(stack.copyWithCount(1));
	}

	/** An unnamed glass pane used to fill empty slots. */
	public static ItemStack filler(Item pane) {
		ItemStack s = new ItemStack(pane);
		s.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, TooltipDisplay.DEFAULT.hiddenComponents()));
		return mark(s);
	}

	public static ItemBuilder head(ServerPlayer player) {
		return head(player.getGameProfile());
	}

	public static ItemBuilder head(GameProfile profile) {
		ItemBuilder b = of(Items.PLAYER_HEAD);
		b.stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
		return b;
	}

	public static ItemBuilder head(UUID id, String name) {
		ItemBuilder b = of(Items.PLAYER_HEAD);
		b.stack.set(DataComponents.PROFILE, name != null ? ResolvableProfile.createUnresolved(name) : ResolvableProfile.createUnresolved(id));
		return b;
	}

	public ItemBuilder name(String legacy) {
		return name(Text.of(legacy));
	}

	public ItemBuilder name(Component name) {
		stack.set(DataComponents.CUSTOM_NAME, noItalic(name));
		return this;
	}

	public ItemBuilder lore(String... legacyLines) {
		for (String line : legacyLines) lore.add(noItalic(Text.of(line)));
		return this;
	}

	public ItemBuilder lore(Component line) {
		lore.add(noItalic(line));
		return this;
	}

	public ItemBuilder lore(List<String> legacyLines) {
		for (String line : legacyLines) lore.add(noItalic(Text.of(line)));
		return this;
	}

	public ItemBuilder glint(boolean glint) {
		if (glint) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return this;
	}

	public ItemBuilder count(int count) {
		stack.setCount(Math.max(1, Math.min(99, count)));
		return this;
	}

	/** Hide the vanilla extra tooltip lines (attributes, enchantments, etc). */
	public ItemBuilder clean() {
		TooltipDisplay display = TooltipDisplay.DEFAULT
			.withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true)
			.withHidden(DataComponents.ENCHANTMENTS, true)
			.withHidden(DataComponents.STORED_ENCHANTMENTS, true)
			.withHidden(DataComponents.POTION_CONTENTS, true)
			.withHidden(DataComponents.DYED_COLOR, true)
			.withHidden(DataComponents.PROFILE, true);
		stack.set(DataComponents.TOOLTIP_DISPLAY, display);
		return this;
	}

	public ItemStack build() {
		if (!lore.isEmpty()) stack.set(DataComponents.LORE, new ItemLore(List.copyOf(lore)));
		return mark(stack);
	}

	private static final String MARKER = "voidpanel_gui";

	/** Tag an item as GUI-only. Tagged items are deleted if they ever reach a real inventory. */
	public static ItemStack mark(ItemStack stack) {
		if (stack.isEmpty() || isGuiItem(stack)) return stack;
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		tag.putBoolean(MARKER, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	public static boolean isGuiItem(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(MARKER, false);
	}

	private static MutableComponent noItalic(Component c) {
		return Component.empty().withStyle(s -> s.withItalic(false)).append(c);
	}
}
