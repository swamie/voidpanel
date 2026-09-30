package com.voidpanel.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public class PlayerData {
	public String name;
	public String lastIp;
	/** Raw nickname including & codes, or null. */
	public String nick;
	public LinkedHashMap<String, Home> homes = new LinkedHashMap<>();
	/** Per-player permission overrides: node -> allowed. Missing = default. */
	public Map<String, Boolean> perms = new HashMap<>();
	/** Per-player home limit, or null for the config default. */
	public Integer homeLimit;
	public Loc back;
	public long firstJoin;
	public long lastSeen;
	public boolean socialSpy;
	public boolean emotesOff;
	/** Player chose normal item drops instead of graves. */
	public boolean gravesOff;
	/** Rank id, or null for the default rank. */
	public String rank;
	/** When a timed rank runs out (millis), 0 = permanent. */
	public long rankExpires;
	/** The rank to go back to when a timed rank runs out (null = default rank). */
	public String previousRank;
	/** Selected chat tag id, or null. */
	public String tag;
	public List<String> unlockedTags = new ArrayList<>();
	public List<String> ignored = new ArrayList<>();
	public boolean vanished;
	public boolean staffChat;
	// Stats
	public long playtimeSeconds;
	public int deaths;
	public int mobKills;
	public int playerKills;
	// Daily rewards
	public int dailyStreak;
	public long lastDailyDay = -1;
	// Moderation
	public long mutedUntil;
	public String muteReason;
	public List<Punishment> punishments = new ArrayList<>();

	public static class Punishment {
		public String type;
		public String reason;
		public String by;
		public long time;
		/** 0 = permanent / not applicable. */
		public long until;
		public boolean revoked;
	}
	/** Last known location of this player's tamed pets, by pet UUID. */
	public LinkedHashMap<String, Pet> pets = new LinkedHashMap<>();

	public static class Pet {
		public String type;
		public String name;
		public Loc loc;
		public long seen;
	}

	public static class Home {
		public String name;
		public Loc loc;
		/** Item id used as the icon in /homes. */
		public String icon;

		public Home() {}

		public Home(String name, Loc loc, String icon) {
			this.name = name;
			this.loc = loc;
			this.icon = icon;
		}
	}
}
