package com.voidpanel.feature;

import com.mojang.serialization.MapCodec;
import com.voidpanel.config.Feature;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;

/** Resource condition "voidpanel:feature_enabled" so datapack recipes can follow the feature toggles. */
public record RecipeToggle() implements ResourceCondition {
	public static final MapCodec<RecipeToggle> CODEC = MapCodec.unit(new RecipeToggle());
	public static final ResourceConditionType<RecipeToggle> TYPE =
		ResourceConditionType.create(ResourceLocation.fromNamespaceAndPath("voidpanel", "golden_apple_recipe_enabled"), CODEC);

	public static void register() {
		ResourceConditions.register(TYPE);
	}

	@Override
	public ResourceConditionType<?> getType() {
		return TYPE;
	}

	@Override
	public boolean test(HolderLookup.Provider registries) {
		return Feature.GOLDEN_APPLE_RECIPE.enabled();
	}
}
