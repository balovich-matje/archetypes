package com.archetypes;

import net.fabricmc.fabric.api.registry.FabricPotionBrewingBuilder;
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
			new Potion("mana_restore", new MobEffectInstance(ManaEffects.MANA_RESTORE, 1)));

	public static final Holder<Potion> STRONG_MANA_RESTORE = register("strong_mana_restore",
			new Potion("mana_restore", new MobEffectInstance(ManaEffects.MANA_RESTORE, 1, 1)));

	/** Durations mirror vanilla Regeneration: 45s, halved at level II. */
	public static final Holder<Potion> MANA_REGENERATION = register("mana_regeneration",
			new Potion("mana_regeneration", new MobEffectInstance(ManaEffects.MANA_REGENERATION, 900)));

	public static final Holder<Potion> STRONG_MANA_REGENERATION = register("strong_mana_regeneration",
			new Potion("mana_regeneration", new MobEffectInstance(ManaEffects.MANA_REGENERATION, 450, 1)));

	private ManaPotions() {
	}

	private static Holder<Potion> register(final String path, final Potion potion) {
		return Registry.registerForHolder(BuiltInRegistries.POTION,
				ResourceKey.create(Registries.POTION, Archetypes.id(path)), potion);
	}

	public static void initialize() {
		FabricPotionBrewingBuilder.BUILD.register(builder -> {
			builder.registerPotionRecipe(Potions.AWKWARD, Ingredient.of(Items.LAPIS_LAZULI), MANA_RESTORE);
			builder.registerPotionRecipe(MANA_RESTORE, Ingredient.of(Items.GLOWSTONE_DUST), STRONG_MANA_RESTORE);
			builder.registerPotionRecipe(Potions.AWKWARD, Ingredient.of(Items.AMETHYST_SHARD), MANA_REGENERATION);
			builder.registerPotionRecipe(MANA_REGENERATION, Ingredient.of(Items.GLOWSTONE_DUST),
					STRONG_MANA_REGENERATION);
		});
	}
}
