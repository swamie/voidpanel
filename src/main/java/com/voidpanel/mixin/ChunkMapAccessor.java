package com.voidpanel.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanish needs to re-check who can see a player right away. */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
	@SuppressWarnings("rawtypes")
	@Accessor("entityMap")
	Int2ObjectMap voidpanel$entityMap();
}
