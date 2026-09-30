package com.voidpanel.feature;

import com.voidpanel.config.Config;
import com.voidpanel.util.Msg;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Combat tagging: getting hit by a mob or player tags you; every new hit resets the timer. */
public final class Combat {
	/** Player -> remaining ticks. */
	private static final Map<UUID, Integer> TAGGED = new HashMap<>();

	private Combat() {}

	public static void onDamage(LivingEntity victim, DamageSource source, float taken) {
		if (!(victim instanceof ServerPlayer player) || taken <= 0) return;
		if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == player) return;
		tag(player);
		// PvP: the attacker can't run away either.
		if (attacker instanceof ServerPlayer other) tag(other);
	}

	public static void tag(ServerPlayer player) {
		if (player.isCreative() || player.isSpectator()) return;
		boolean was = TAGGED.containsKey(player.getUUID());
		TAGGED.put(player.getUUID(), Config.get().combatTagSeconds * 20);
		if (!was) Msg.actionBar(player, "&c&l⚔ &cIn combat &8| &7teleports are blocked");
		Teleports.cancel(player, "&cTeleport cancelled, you were hit!");
	}

	public static boolean inCombat(ServerPlayer player) {
		return TAGGED.containsKey(player.getUUID());
	}

	public static int remaining(ServerPlayer player) {
		return TAGGED.getOrDefault(player.getUUID(), 0);
	}

	public static void clear(ServerPlayer player) {
		TAGGED.remove(player.getUUID());
	}

	public static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Integer>> it = TAGGED.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			int left = e.getValue() - 1;
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null || left <= 0 || p.isDeadOrDying()) {
				it.remove();
				if (p != null && !p.isDeadOrDying()) Msg.actionBar(p, "&a✔ You are no longer in combat");
				continue;
			}
			e.setValue(left);
			if (left % 10 == 0 && !Teleports.isWarmingUp(p)) {
				double frac = left / (Config.get().combatTagSeconds * 20.0);
				Msg.actionBar(p, "&c&l⚔ &cCombat &f" + Msg.seconds(left) + " " + Msg.bar(frac, "&c", "&8"));
			}
		}
	}
}
