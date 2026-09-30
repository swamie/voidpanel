package com.voidpanel.data;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** A saved position in any dimension. */
public class Loc {
	public String dim;
	public double x, y, z;
	public float yaw, pitch;

	public Loc() {}

	public Loc(String dim, double x, double y, double z, float yaw, float pitch) {
		this.dim = dim;
		this.x = x;
		this.y = y;
		this.z = z;
		this.yaw = yaw;
		this.pitch = pitch;
	}

	public static Loc of(ServerPlayer p) {
		return new Loc(p.level().dimension().identifier().toString(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
	}

	public ServerLevel level(MinecraftServer server) {
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dim)));
	}

	public String dimName() {
		return switch (dim) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "The End";
			default -> dim;
		};
	}

	public String dimColor() {
		return switch (dim) {
			case "minecraft:overworld" -> "&a";
			case "minecraft:the_nether" -> "&c";
			case "minecraft:the_end" -> "&e";
			default -> "&b";
		};
	}

	public String coords() {
		return (int) Math.floor(x) + ", " + (int) Math.floor(y) + ", " + (int) Math.floor(z);
	}

	public boolean isOverworld() {
		return Level.OVERWORLD.identifier().toString().equals(dim);
	}
}
