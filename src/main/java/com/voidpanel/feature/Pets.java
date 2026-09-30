package com.voidpanel.feature;

import com.voidpanel.data.DataStore;
import com.voidpanel.data.Loc;
import com.voidpanel.data.PlayerData;
import com.voidpanel.data.PlayerData.Pet;
import com.voidpanel.util.Msg;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.ChunkPos;

/**
 * Remembers where every tamed pet was last seen (so we can find it in unloaded chunks)
 * and force-teleports pets to their owner.
 */
public final class Pets {
	private Pets() {}

	/** Tamed animals only (wolves, cats, parrots, horses, llamas...), not vexes. */
	public static UUID ownerOf(Entity e) {
		if (!(e instanceof Animal) || !(e instanceof OwnableEntity pet)) return null;
		if (e instanceof TamableAnimal t && !t.isTame()) return null;
		var ref = pet.getOwnerReference();
		return ref == null ? null : ref.getUUID();
	}

	public static void record(Entity e) {
		UUID owner = ownerOf(e);
		if (owner == null || !(e.level() instanceof ServerLevel level)) return;
		PlayerData data = DataStore.get(owner);
		Pet pet = data.pets.computeIfAbsent(e.getStringUUID(), k -> new Pet());
		pet.type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
		pet.name = e.hasCustomName() ? e.getCustomName().getString() : null;
		pet.loc = new Loc(level.dimension().identifier().toString(), e.getX(), e.getY(), e.getZ(), e.getYRot(), e.getXRot());
		pet.seen = System.currentTimeMillis();
		DataStore.markDirty();
	}

	public static void onUnload(Entity e) {
		if (ownerOf(e) == null) return;
		var reason = e.getRemovalReason();
		if (reason != null && reason.shouldDestroy()) forget(e);
		else record(e);
	}

	public static void forget(Entity e) {
		UUID owner = ownerOf(e);
		if (owner == null) return;
		DataStore.find(owner).ifPresent(d -> {
			if (d.pets.remove(e.getStringUUID()) != null) DataStore.markDirty();
		});
	}

	/** Refresh positions of every loaded pet. Cheap enough to run every few seconds. */
	public static void scan(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity e : level.getAllEntities()) {
				if (e instanceof Animal) record(e);
			}
		}
	}

	public static Entity findLoaded(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			Entity e = level.getEntity(id);
			if (e != null) return e;
		}
		return null;
	}

	/**
	 * Bring a pet to {@code to}. If its chunk isn't loaded we load it, wait for the pet
	 * to appear (entities load a moment after chunks), then teleport it.
	 */
	public static void fetch(ServerPlayer to, UUID petId, Pet pet, Consumer<Boolean> done) {
		MinecraftServer server = to.level().getServer();
		Entity loaded = findLoaded(server, petId);
		if (loaded != null) {
			done.accept(bring(loaded, to));
			return;
		}
		ServerLevel level = pet.loc.level(server);
		if (level == null) {
			done.accept(false);
			return;
		}
		ChunkPos chunk = new ChunkPos((int) Math.floor(pet.loc.x) >> 4, (int) Math.floor(pet.loc.z) >> 4);
		level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, chunk, 2);
		Msg.actionBar(to, "&7Fetching &f" + label(pet) + " &7from " + pet.loc.coords() + "...");
		poll(to, petId, level, 0, done);
	}

	private static void poll(ServerPlayer to, UUID petId, ServerLevel level, int tries, Consumer<Boolean> done) {
		com.voidpanel.util.Scheduler.later(5, () -> {
			if (to.hasDisconnected()) return;
			Entity e = level.getEntity(petId);
			if (e != null) {
				done.accept(bring(e, to));
			} else if (tries < 40) {
				poll(to, petId, level, tries + 1, done);
			} else {
				done.accept(false);
			}
		});
	}

	private static boolean bring(Entity pet, ServerPlayer to) {
		if (pet instanceof LivingEntity living && living.isDeadOrDying()) return false;
		if (pet.isPassenger()) pet.stopRiding();
		if (pet instanceof TamableAnimal t) {
			t.setOrderedToSit(false);
			t.setInSittingPose(false);
		}
		boolean ok = pet.teleportTo(to.level(), to.getX(), to.getY(), to.getZ(), Set.of(), pet.getYRot(), pet.getXRot(), false);
		if (ok) {
			Entity moved = to.level().getEntity(pet.getUUID());
			if (moved != null) record(moved);
			Msg.sound(to, SoundEvents.ENDERMAN_TELEPORT, 0.5f, 1.4f);
		}
		return ok;
	}

	public static String label(Pet pet) {
		if (pet.name != null) return pet.name;
		String path = pet.type.contains(":") ? pet.type.substring(pet.type.indexOf(':') + 1) : pet.type;
		String nice = path.replace('_', ' ');
		return Character.toUpperCase(nice.charAt(0)) + nice.substring(1);
	}
}
