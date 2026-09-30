package com.voidpanel.util;

import com.voidpanel.config.Config;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/** Consistent chat, action bar and sound feedback. */
public final class Msg {
	private Msg() {}

	public static MutableComponent prefixed(Component body) {
		return Component.empty().append(Text.of(Config.get().prefix)).append(body);
	}

	public static void info(Player player, String legacy) {
		player.sendSystemMessage(prefixed(Text.of("&7" + legacy)));
	}

	public static void ok(Player player, String legacy) {
		player.sendSystemMessage(prefixed(Text.of("&a" + legacy)));
		if (player instanceof ServerPlayer sp) sound(sp, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
	}

	public static void err(Player player, String legacy) {
		player.sendSystemMessage(prefixed(Text.of("&c" + legacy)));
		if (player instanceof ServerPlayer sp) sound(sp, SoundEvents.VILLAGER_NO, 0.6f, 1.0f);
	}

	public static void send(Player player, Component component) {
		player.sendSystemMessage(component);
	}

	public static void actionBar(ServerPlayer player, String legacy) {
		player.sendSystemMessage(Text.of(legacy), true);
	}

	/** A clickable chat button like [✔ ACCEPT]. */
	public static MutableComponent button(String legacyLabel, String command, String legacyHover) {
		return Text.of(legacyLabel).withStyle(s -> s
			.withClickEvent(new ClickEvent.RunCommand(command))
			.withHoverEvent(new HoverEvent.ShowText(Text.of(legacyHover))));
	}

	/** A button that puts text into the player's chat box instead of running it. */
	public static MutableComponent suggest(String legacyLabel, String text, String legacyHover) {
		return Text.of(legacyLabel).withStyle(s -> s
			.withClickEvent(new ClickEvent.SuggestCommand(text))
			.withHoverEvent(new HoverEvent.ShowText(Text.of(legacyHover))));
	}

	public static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		sound(player, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), volume, pitch);
	}

	public static void sound(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
		player.connection.send(new ClientboundSoundPacket(sound, SoundSource.MASTER,
			player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
	}

	public static void click(ServerPlayer player) {
		sound(player, SoundEvents.UI_BUTTON_CLICK, 0.5f, 1.0f);
	}

	/** A 10-segment progress bar, e.g. ■■■■■■□□□□ */
	public static String bar(double fraction, String on, String off) {
		int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * 10);
		return on + "■".repeat(filled) + off + "■".repeat(10 - filled);
	}

	public static String seconds(int ticks) {
		return String.format("%.1fs", ticks / 20.0);
	}
}
