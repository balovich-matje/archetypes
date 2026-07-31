package com.archetypes.platform;

// STAGE 6 — THE ONE NEW SHARED TYPE THE LOADER AXIS NEEDED, and it exists to stop a table of
// CONTENT from being copied a fourth and fifth time.
//
// Brewing already forks three ways by VERSION inside ManaPotions/AmnesiaPotions (design §4.1's
// three-way row): a `FabricPotionBrewingBuilder.BUILD` consumer at `>=26.1`, a
// `FabricBrewingRecipeRegistryBuilder.BUILD` consumer at `>=1.20.5`, and bare static
// `FabricBrewingRecipeRegistry` calls below that. Each of those arms already carries its own
// copy of the same four (ManaPotions) and two (AmnesiaPotions) mixes, because the vocabulary
// differs in every one.
//
// Writing a `neoforge` arm and a `forge` arm the same way would make FIVE copies of a recipe
// table per file. That is precisely the drift R-20 exists to catch — a mix whose ingredient
// silently differs on one node is a balance divergence no build can see — so the loader axis
// gets ONE arm's worth of data instead: each potions class exposes a single `brew(BrewingSink)`
// method, gated off on Fabric, and both loader arms are a one-line method reference to it.
//
// The interface is spelled in nothing but vanilla types, so it says the same thing on both
// loaders. `RegisterBrewingRecipesEvent.getBuilder().addMix(from, ingredient, to)` (NeoForge)
// and `BrewingRecipeRegistry.addRecipe(...)` (LexForge) are both reachable from it; adapting is
// the helper's job, not the caller's.
//
// The whole compilation unit is loader-axis-only (conventions §4's whole-file form), so on the
// five Fabric nodes this file holds nothing but its package statement and produces no `.class`
// — which is what keeps those five jars byte-identical while this lands.
//
// Class doc kept as LINE comments on purpose: a `*/` inside a disabled `//?` branch would close
// Stonecutter's own comment early (this repo's Stage-2 finding).
//? if fabric {
//?} else {
/*import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;

/^*
 * One brewing-stand mix: a base potion, the ingredient laid on top, and what comes out.
 *
 * <p>Deliberately NOT an `Ingredient`: every mix this mod declares is a single item, and
 * `Ingredient`'s own factory moved with the data-component rewrite. An `Item` says the same
 * thing on every node in range and needs no fork of its own.
 ^/
@FunctionalInterface
public interface BrewingSink {
	void mix(Holder<Potion> from, Item ingredient, Holder<Potion> to);
}
*///?}
