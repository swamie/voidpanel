package com.voidpanel.feature;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.voidpanel.VoidPanel;
import com.voidpanel.config.Config;
import com.voidpanel.data.DataStore;
import com.voidpanel.data.DataStore.Grave;
import com.voidpanel.perm.Perm;
import com.voidpanel.perm.Perms;
import com.voidpanel.util.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Death graves: instead of spilling, your items go into a chest at your death spot that
 * only you (or staff) can open. The chest itself is empty; the items are stored safely here.
 */
public final class Graves {
	private Graves() {}

	/** Called from the dropEquipment mixin. Returns true if the items were taken into a grave. */
	public static boolean onDeath(ServerPlayer player) {
		if (!Config.get().gravesEnabled || !Perms.has(player, Perm.GRAVES)) return false;
		if (DataStore.find(player.getUUID()).map(d -> d.gravesOff).orElse(false)) return false;
		Inventory inv = player.getInventory();
		if (inv.isEmpty()) return false;
		ServerLevel level = player.level();
		BlockPos pos = findSpot(level, player.blockPosition());
		if (pos == null) return false;

		List<JsonElement> items = new ArrayList<>();
		var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.isEmpty()) continue;
			var encoded = ItemStack.CODEC.encodeStart(ops, stack);
			if (encoded.isError()) {
				VoidPanel.LOGGER.warn("Couldn't store {} in a grave, dropping it", stack);
				continue;
			}
			items.add(encoded.getOrThrow());
			inv.removeItemNoUpdate(i);
		}
		if (items.isEmpty()) return false;

		Grave g = new Grave();
		g.id = UUID.randomUUID().toString().substring(0, 8);
		g.owner = player.getStringUUID();
		g.ownerName = player.getGameProfile().name();
		g.dim = level.dimension().identifier().toString();
		g.x = pos.getX();
		g.y = pos.getY();
		g.z = pos.getZ();
		g.created = System.currentTimeMillis();
		g.items = items;
		DataStore.root().graves.add(g);
		DataStore.markDirty();
		level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
		player.sendSystemMessage(Msg.prefixed(com.voidpanel.util.Text.of("&7Your items are safe in a grave at &f" + g.x + ", " + g.y + ", " + g.z + " &7(" + items.size() + " stacks). ")
			.append(Msg.button("&e[/back]", "/back", "&7Teleport back to where you died"))));
		return true;
	}

	/** Nearest free space at or above the death spot. */
	private static BlockPos findSpot(ServerLevel level, BlockPos death) {
		int y = Math.max(level.getMinY() + 1, Math.min(level.getMaxY() - 1, death.getY()));
		BlockPos start = new BlockPos(death.getX(), y, death.getZ());
		for (int dy = 0; dy < 24; dy++) {
			BlockPos p = start.above(dy);
			if (p.getY() >= level.getMaxY()) break;
			BlockState s = level.getBlockState(p);
			if ((s.isAir() || s.canBeReplaced()) && at(level, p) == null) return p;
		}
		return null;
	}

	public static Grave at(Level level, BlockPos pos) {
		String dim = level.dimension().identifier().toString();
		for (Grave g : DataStore.root().graves) {
			if (g.x == pos.getX() && g.y == pos.getY() && g.z == pos.getZ() && g.dim.equals(dim)) return g;
		}
		return null;
	}

	public static boolean canOpen(ServerPlayer player, Grave g) {
		return g.owner.equals(player.getStringUUID()) || Perms.has(player, Perm.GRAVES_OTHERS);
	}

	/** Give the items back and remove the grave. */
	public static void claim(ServerPlayer player, Grave g) {
		MinecraftServer server = player.level().getServer();
		var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
		int given = 0;
		for (JsonElement json : g.items) {
			var decoded = ItemStack.CODEC.parse(ops, json);
			if (decoded.isError()) continue;
			ItemStack stack = decoded.getOrThrow();
			com.voidpanel.util.Give.item(player, stack);
			given++;
		}
		DataStore.root().graves.remove(g);
		DataStore.markDirty();
		ServerLevel level = level(server, g);
		if (level != null) {
			BlockPos pos = new BlockPos(g.x, g.y, g.z);
			if (level.getBlockState(pos).is(Blocks.CHEST)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		}
		Msg.sound(player, SoundEvents.CHEST_OPEN, 0.8f, 1.0f);
		Msg.ok(player, "Recovered " + given + " stacks from " + (g.owner.equals(player.getStringUUID()) ? "your" : g.ownerName + "'s") + " grave.");
	}

	public static ServerLevel level(MinecraftServer server, Grave g) {
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(g.dim)));
	}

	/** Is the chest block still there? If not (exploded, etc.) the owner can claim from /graves. */
	public static boolean markerIntact(MinecraftServer server, Grave g) {
		ServerLevel level = level(server, g);
		if (level == null) return false;
		BlockPos pos = new BlockPos(g.x, g.y, g.z);
		if (level.getChunkSource().getChunkNow(g.x >> 4, g.z >> 4) == null) return true;
		return level.getBlockState(pos).is(Blocks.CHEST);
	}

	/** Server-wide switch. Existing graves stay claimable either way. */
	public static void toggleServer(ServerPlayer admin) {
		Config.get().gravesEnabled = !Config.get().gravesEnabled;
		Config.save();
		Msg.ok(admin, "Graves are now " + (Config.get().gravesEnabled ? "&aON" : "&cOFF") + " &afor the whole server.");
	}

	/** Personal switch: graves or normal item drops. */
	public static void togglePersonal(ServerPlayer player) {
		var d = DataStore.get(player.getUUID());
		d.gravesOff = !d.gravesOff;
		DataStore.markDirty();
		Msg.ok(player, d.gravesOff ? "Graves off for you. Your items will drop normally when you die." : "Graves on. Your items will be kept safe in a grave.");
	}

	public static List<Grave> of(UUID owner) {
		return DataStore.root().graves.stream().filter(g -> g.owner.equals(owner.toString())).toList();
	}
}
