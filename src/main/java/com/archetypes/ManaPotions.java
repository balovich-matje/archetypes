package com.archetypes;

// Brewing registration is a THREE-way split across this port and only its NAME moves at
// this boundary: `registerPotionRecipe(Holder<Potion>, Ingredient, Holder<Potion>)` and the
// `BUILD` event are shape-identical on both types (`javap` on 26.x's
// fabric-content-registries-v0 11.3.0 and 1.21.11's 10.2.14), so the lambda below is shared.
// The third arm — `FabricBrewingRecipeRegistry`, no builder at all — is 0.92.11's and lands
// at Stage 5.
// STAGE 6: `fabric &&` on the two upper arms, and THE LOADER ARMS COME BEFORE THE `else`. Both
// halves were a real bug for one build: without the scoping, 1.21.1-neoforge satisfied
// `>=1.20.5` and generated a LIVE `import …FabricBrewingRecipeRegistryBuilder` it has no
// classpath for; and an `elif` written after an `else` is dead by construction. Neither is
// visible on any Fabric node — the generated sources of the two loader nodes are the only
// place that can say so, which is why they are read line by line at every stage.
//? if fabric && >=26.1 {
import net.fabricmc.fabric.api.registry.FabricPotionBrewingBuilder;
//?} elif fabric && >=1.20.5 {
/*import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;
*///?} elif neoforge {
/*import com.archetypes.platform.NeoForgeEvents;
*///?} elif forge {
/*import com.archetypes.platform.ForgeEvents;
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
		// STAGE 6 — the loader axis takes a FOURTH arm and it carries no recipe data of its
		// own: `brew` below is the one copy of the table for both loaders, handed to a helper
		// through `platform/BrewingSink`. Read that file for why it exists rather than a fifth
		// and sixth transcription of the same mixes.
		//
		// What a loader helper owes: register these mixes ONCE, during the loader's brewing
		// registration window (`RegisterBrewingRecipesEvent` on NeoForge,
		// `BrewingRecipeRegistry.addRecipe` from a common-setup enqueue on LexForge), and treat
		// the ingredient `Item` as a single-item ingredient.
		//? if fabric && >=26.1 {
		FabricPotionBrewingBuilder.BUILD.register(builder -> {
			builder.registerPotionRecipe(Potions.AWKWARD, Ingredient.of(Items.LAPIS_LAZULI), MANA_RESTORE);
			builder.registerPotionRecipe(MANA_RESTORE, Ingredient.of(Items.GLOWSTONE_DUST), STRONG_MANA_RESTORE);
			builder.registerPotionRecipe(Potions.AWKWARD, Ingredient.of(Items.AMETHYST_SHARD), MANA_REGENERATION);
			builder.registerPotionRecipe(MANA_REGENERATION, Ingredient.of(Items.GLOWSTONE_DUST),
					STRONG_MANA_REGENERATION);
		});
		//?} elif fabric && >=1.20.5 {
		/*FabricBrewingRecipeRegistryBuilder.BUILD.register(builder -> {
			builder.registerPotionRecipe(Potions.AWKWARD, Ingredient.of(Items.LAPIS_LAZULI), MANA_RESTORE);
			builder.registerPotionRecipe(MANA_RESTORE, Ingredient.of(Items.GLOWSTONE_DUST), STRONG_MANA_RESTORE);
			builder.registerPotionRecipe(Potions.AWKWARD, Ingredient.of(Items.AMETHYST_SHARD), MANA_REGENERATION);
			builder.registerPotionRecipe(MANA_REGENERATION, Ingredient.of(Items.GLOWSTONE_DUST),
					STRONG_MANA_REGENERATION);
		});
		*///?} elif neoforge {
		/*NeoForgeEvents.brewingRecipes(ManaPotions::brew);
		*///?} elif forge {
		/*ForgeEvents.brewingRecipes(ManaPotions::brew);
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

	// The four mixes, once, for the whole loader axis. Same bases, same ingredients, same
	// order as every Fabric arm above — keep them in step, and if a fifth mix is ever added it
	// belongs in all of them.
	//? if fabric {
	//?} else {
	/*public static void brew(final com.archetypes.platform.BrewingSink out) {
		out.mix(Potions.AWKWARD, Items.LAPIS_LAZULI, MANA_RESTORE);
		out.mix(MANA_RESTORE, Items.GLOWSTONE_DUST, STRONG_MANA_RESTORE);
		out.mix(Potions.AWKWARD, Items.AMETHYST_SHARD, MANA_REGENERATION);
		out.mix(MANA_REGENERATION, Items.GLOWSTONE_DUST, STRONG_MANA_REGENERATION);
	}
	*///?}
}
