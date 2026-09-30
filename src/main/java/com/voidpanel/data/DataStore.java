package com.voidpanel.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import java.util.HashMap;
import com.google.gson.reflect.TypeToken;
import com.voidpanel.VoidPanel;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Everything VoidPanel remembers, stored in <world>/voidpanel/data.json. */
public final class DataStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static Root root = new Root();
	private static Path file;
	private static boolean dirty;

	private DataStore() {}

	public static class Root {
		public Loc spawn;
		public String defaultRank = "member";
		public LinkedHashMap<String, Rank> ranks = new LinkedHashMap<>();
		public LinkedHashMap<String, Warp> warps = new LinkedHashMap<>();
		public LinkedHashMap<String, Tag> tags = new LinkedHashMap<>();
		public List<Grave> graves = new ArrayList<>();
		public Map<String, PlayerData> players = new LinkedHashMap<>();
		/** Players who tried to join while not whitelisted, newest first. */
		public List<DeniedLogin> denied = new ArrayList<>();
	}

	public static class Rank {
		public String id;
		/** Shown in menus, e.g. "&b&lVIP". */
		public String display;
		/** Put before names in chat and tab, e.g. "&8[&bVIP&8] ". */
		public String prefix = "";
		/** Colour codes applied to the player's name, e.g. "&b". */
		public String nameColor = "&f";
		public String icon = "minecraft:iron_ingot";
		/** Higher = more important. Sorts the tab list. */
		public int weight;
		/** Permission node -> allowed. Missing = inherit from the default rank. */
		public Map<String, Boolean> perms = new HashMap<>();
		public Integer homeLimit;
		/** Auto-promote players to this rank after this many hours of playtime. 0 = off. */
		public int autoHours;
		/** Show the prefix before names in chat. */
		public boolean showInChat = true;
		/** Show the prefix before names in the tab list. */
		public boolean showInTab = true;

		public Rank() {}

		public Rank(String id, String display, String prefix, String nameColor, String icon, int weight, int autoHours) {
			this.id = id;
			this.display = display;
			this.prefix = prefix;
			this.nameColor = nameColor;
			this.icon = icon;
			this.weight = weight;
			this.autoHours = autoHours;
		}
	}

	public static class Warp {
		public String name;
		public Loc loc;
		public String icon = "minecraft:ender_pearl";
		public String createdBy;
	}

	public static class Tag {
		public String id;
		public String display;
		public String icon = "minecraft:name_tag";
		/** Public tags can be used by everyone; others must be unlocked. */
		public boolean everyone;
	}

	public static class Grave {
		public String id;
		public String owner;
		public String ownerName;
		public String dim;
		public int x, y, z;
		public long created;
		/** Items serialised with the vanilla item codec. */
		public List<JsonElement> items = new ArrayList<>();
	}

	public static class DeniedLogin {
		public String name;
		public String uuid;
		public long time;

		public DeniedLogin() {}

		public DeniedLogin(String name, UUID uuid, long time) {
			this.name = name;
			this.uuid = uuid.toString();
			this.time = time;
		}
	}

	public static void load(MinecraftServer server) {
		file = server.getWorldPath(LevelResource.ROOT).resolve("voidpanel").resolve("data.json");
		root = new Root();
		if (Files.exists(file)) {
			try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				Root loaded = GSON.fromJson(r, new TypeToken<Root>() {}.getType());
				if (loaded != null) root = loaded;
			} catch (Exception e) {
				VoidPanel.LOGGER.error("Could not read {}", file, e);
			}
		}
		if (root.players == null) root.players = new LinkedHashMap<>();
		if (root.denied == null) root.denied = new ArrayList<>();
		if (root.ranks == null) root.ranks = new LinkedHashMap<>();
		if (root.warps == null) root.warps = new LinkedHashMap<>();
		if (root.tags == null) root.tags = new LinkedHashMap<>();
		if (root.graves == null) root.graves = new ArrayList<>();
		com.voidpanel.feature.Ranks.ensureDefaults();
		com.voidpanel.feature.Tags.ensureDefaults();
		dirty = false;
	}

	public static void markDirty() {
		dirty = true;
	}

	public static void saveIfDirty() {
		if (dirty) save();
	}

	public static void save() {
		if (file == null) return;
		dirty = false;
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling("data.json.tmp");
			try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(root, w);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			VoidPanel.LOGGER.error("Could not save {}", file, e);
		}
	}

	public static Root root() {
		return root;
	}

	public static boolean has(UUID id) {
		return root.players.containsKey(id.toString());
	}

	public static PlayerData get(UUID id) {
		return root.players.computeIfAbsent(id.toString(), k -> new PlayerData());
	}

	public static Optional<PlayerData> find(UUID id) {
		return Optional.ofNullable(root.players.get(id.toString()));
	}

	/** Look up a known player by their last seen name (case-insensitive). */
	public static Optional<Map.Entry<UUID, PlayerData>> byName(String name) {
		for (var e : root.players.entrySet()) {
			if (e.getValue().name != null && e.getValue().name.equalsIgnoreCase(name)) {
				return Optional.of(Map.entry(UUID.fromString(e.getKey()), e.getValue()));
			}
		}
		return Optional.empty();
	}

	public static synchronized void recordDenied(String name, UUID uuid) {
		root.denied.removeIf(d -> d.uuid.equals(uuid.toString()));
		root.denied.add(0, new DeniedLogin(name, uuid, System.currentTimeMillis()));
		while (root.denied.size() > 45) root.denied.remove(root.denied.size() - 1);
		dirty = true;
	}
}
