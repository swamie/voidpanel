package com.voidpanel.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.voidpanel.VoidPanel;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** config/voidpanel.json. Every field can be edited by hand, then /voidpanel reload. */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static Config instance = new Config();

	// ---- Messages ----
	public String prefix = "&8[&5&lV&d&lP&8] &r";

	/** Bumped when defaults change so old configs can be upgraded. */
	public int configVersion = 2;

	// ---- Chat formatting (placeholders: {rank} {tag} {display} {name} {message}) ----
	public String chatFormat = "{rank}{tag}{display}&7: &f{message}";
	/** Staff chat, sent with "#message" or /sc. */
	public String staffChatFormat = "&8[&c&lSTAFF&8] {rank}{display}&8: &b{message}";
	/** Placeholders: {display} {name} */
	public String joinFormat = "&8[&a+&8] &7{display}";
	/** Placeholders: {display} {name} */
	public String leaveFormat = "&8[&c-&8] &7{display}";
	/** Placeholders: {display} {name} {count} */
	public String firstJoinFormat = "&d&l✦ &fWelcome &d{display} &fto the server! &8(#{count})";

	/** Hypixel-style emotes (o/ :shrug: <3 ...) in chat and /msg. Server-wide switch. */
	public boolean emotesEnabled = true;

	// ---- Private messages (placeholders: {prefix} {from} {to} {message}) ----
	public String msgPrefix = "&d&l✉ &8┃ ";
	public String msgToFormat = "{prefix}&7You &8➜ &f{to}&8: &7{message}";
	public String msgFromFormat = "{prefix}&f{from} &8➜ &7You&8: &f{message}";
	public String spyFormat = "&8[&cSpy&8] &7{from} &8➜ &7{to}&8: &7{message}";

	// ---- Teleporting ----
	public int teleportWarmupSeconds = 5;
	public int combatTagSeconds = 7;
	public int tpaExpireSeconds = 60;
	public boolean sendNewPlayersToSpawn = true;

	// ---- AFK ----
	/** Go AFK automatically after this many idle minutes. 0 = off. */
	public int afkMinutes = 5;
	/** Kick players who are AFK this long (unless they have afk.bypass). 0 = never. */
	public int afkKickMinutes = 0;

	// ---- Graves ----
	/** Put items in a locked grave chest on death instead of dropping them. */
	public boolean gravesEnabled = true;

	// ---- Daily rewards: 7 days, each a list of "item_id count" ----
	public java.util.List<java.util.List<String>> dailyRewards = com.voidpanel.feature.Daily.defaults();

	// ---- Homes ----
	public int defaultHomeLimit = 3;

	// ---- Random teleport ----
	/** Players land between -rtpRange and +rtpRange on X and Z (around the centre). */
	public int rtpRange = 5000;
	public int rtpCenterX = 0;
	public int rtpCenterZ = 0;
	public int rtpCooldownSeconds = 30;
	public boolean rtpAvoidWater = true;

	// ---- Nicknames ----
	public int nickMinLength = 3;
	public int nickMaxLength = 16;

	public static Config get() {
		return instance;
	}

	public static Config defaults() {
		return new Config();
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("voidpanel.json");
	}

	public static void load() {
		Path path = path();
		if (Files.exists(path)) {
			try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				Config loaded = GSON.fromJson(r, Config.class);
				if (loaded != null) {
					instance = loaded;
					upgrade(loaded);
				}
			} catch (Exception e) {
				VoidPanel.LOGGER.error("Could not read {}, using defaults", path, e);
			}
		}
		save();
	}

	private static void upgrade(Config c) {
		// v2 added ranks and tags to the chat format. (A missing configVersion reads as the
		// current default, so upgrade by content rather than by number.)
		if ("&f{display}&7: &f{message}".equals(c.chatFormat)) c.chatFormat = "{rank}{tag}{display}&7: &f{message}";
		if (c.dailyRewards == null || c.dailyRewards.size() < com.voidpanel.feature.Daily.DAYS) c.dailyRewards = com.voidpanel.feature.Daily.defaults();
		if (c.staffChatFormat == null) c.staffChatFormat = new Config().staffChatFormat;
	}

	public static void save() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, w);
			}
		} catch (IOException e) {
			VoidPanel.LOGGER.error("Could not save {}", path, e);
		}
	}
}
