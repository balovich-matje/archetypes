package com.archetypes;

// Brewing registration is a THREE-way split across this port and only its NAME moves at
// this boundary: `registerPotionRecipe(Holder<Potion>, Ingredient, Holder<Potion>)` and the
// `BUILD` event are shape-identical on both types (`javap` on 26.x's
// fabric-content-registries-v0 11.3.0 and 1.21.11's 10.2.14), so the lambda below is shared.
// The third arm — `FabricBrewingRecipeRegistry`, no builder at all — is 0.92.11's and lands
// at Stage 5.
//? if >=26.1 {
import net.fabricmc.fabric.api.registry.FabricPotionBrewingBuilder;
//?} elif >=1.20.5 {
/*import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;
*///?} else {
/*import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistry;
*///?}
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Mana Restore (from lapis) and Mana Regeneration (from amethyst), shaped
 * exactly like their health twins: awkward + reagent for I, glowstone for
 * II. Splash and lingering forms come free from vanilla's gunpowder and
 * dragon's-breath container mixes, and the creative Food & Drinks tab picks
 * all four up on its own registry walk.
 */
public final class ManaPotions {
	public static final Holder<Potion> MANA_RESTORE = register("mana_restore",
			/*? if >=1.21 {*/new Potion("mana_restore", new MobEffectInstance(ManaEffects.MANA_RESTORE, 1)));
			/*?} else *///new Potion("mana_restore", new MobEffectInstance(ManaEffects.MANA_RESTORE.value(), 1)));

	public static final Holder<Potion> STRONG_MANA_RESTORE = register("strong_mana_restore",
			/*? if >=1.21 {*/new Potion("mana_restore", new MobEffectInstance(ManaEffects.MANA_RESTORE, 1, 1)));
			/*?} else *///new Potion("mana_restore", new MobEffectInstance(ManaEffects.MANA_RESTORE.value(), 1, 1)));

	/** Durations mirror vanilla Regeneration: 45s, halved at level II. */
	public static final Holder<Potion> MANA_REGENERATION = register("mana_regeneration",
			/*? if >=1.21 {*/new Potion("mana_regeneration", new MobEffectInstance(ManaEffects.MANA_REGENERATION, 900)));
			/*?} else *///new Potion("mana_regeneration", new MobEffectInstance(ManaEffects.MANA_REGENERATION.value(), 900)));

	public static final Holder<Potion> STRONG_MANA_REGENERATION = register("strong_mana_regeneration",
			/*? if >=1.21 {*/new Potion("mana_regeneration", new MobEffectInstance(ManaEffects.MANA_REGENERATION, 450, 1)));
			/*?} else *///new Potion("mana_regeneration", new MobEffectInstance(ManaEffects.MANA_REGENERATION.value(), 450, 1)));

	private ManaPotions() {
	}

	private static Holder<Potion> register(final String path, final Potion potion) {
		return Registry.registerForHolder(BuiltInRegistries.POTION,
				ResourceKey.create(Registries.POTION, Archetypes.id(path)), potion);
	}

	public static void initialize() {
		// BREWING IS THREE-WAY, and this is the third arm (design §4.1): fabric-api 0.92.11
		// has no builder and no BUILD event at all — `FabricBrewingRecipeRegistry` is a pair
		// of static methods, called at init, taking bare `Potion`s. Same four recipes, same
		// ingredients, same order; only the vocabulary moves.
		//? if >=26.1 {
		FabricPotionBrewingBuilder.BUILD.register(builder -> {
			builder.registerPotionRecipe(Potions.AWKWARD, Ingredient.of(Items.LAPIS_LAZULI), MANA_RESTORE);
			builder.registerPotionRecipe(MANA_RESTORE, Ingredient.of(Items.GLOWSTONE_DUST), STRONG_MANA_RESTORE);
			builder.registerPotionRecipe(Potions.AWKWARD, Ingredient.of(Items.AMETHYST_SHARD), MANA_REGENERATION);
			builder.registerPotionRecipe(MANA_REGENERATION, Ingredient.of(Items.GLOWSTONE_DUST),
					STRONG_MANA_REGENERATION);
		});
		//?} elif >=1.20.5 {
		/*FabricBrewingRecipeRegistryBuilder.BUILD.register(builder -> {
			builder.registerPotionRecipe(Potions.AWKWARD, Ingredient.of(Items.LAPIS_LAZULI), MANA_RESTORE);
			builder.registerPotionRecipe(MANA_RESTORE, Ingredient.of(Items.GLOWSTONE_DUST), STRONG_MANA_RESTORE);
			builder.registerPotionRecipe(Potions.AWKWARD, Ingredient.of(Items.AMETHYST_SHARD), MANA_REGENERATION);
			builder.registerPotionRecipe(MANA_REGENERATION, Ingredient.of(Items.GLOWSTONE_DUST),
					STRONG_MANA_REGENERATION);
		});
		*///?} else {
		/*FabricBrewingRecipeRegistry.registerPotionRecipe(
				Potions.AWKWARD, Ingredient.of(Items.LAPIS_LAZULI), MANA_RESTORE.value());
		FabricBrewingRecipeRegistry.registerPotionRecipe(
				MANA_RESTORE.value(), Ingredient.of(Items.GLOWSTONE_DUST), STRONG_MANA_RESTORE.value());
		FabricBrewingRecipeRegistry.registerPotionRecipe(
				Potions.AWKWARD, Ingredient.of(Items.AMETHYST_SHARD), MANA_REGENERATION.value());
		FabricBrewingRecipeRegistry.registerPotionRecipe(MANA_REGENERATION.value(),
				Ingredient.of(Items.GLOWSTONE_DUST), STRONG_MANA_REGENERATION.value());
		*///?}
	}
}
