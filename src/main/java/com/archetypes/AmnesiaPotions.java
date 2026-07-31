package com.archetypes;

// The same rename as ManaPotions' — see the note there.
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.InstantaneousMobEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The SMP's respec path, priced (user call): Amnesia I (awkward + red
 * mushroom) refunds every node but shaves a third of your levels; Amnesia
 * II (I + glowstone) forgets the archetype choice AND every level — a full
 * restart. The creative Reset button remains the free testing wipe.
 *
 * <p>Strictly drinkable by design: the drink path applies the instant
 * effect with the drinker as its own source, so that is what the effect
 * requires. The splash/lingering/arrow forms vanilla brewing derives for
 * free are inert — nobody gets their build wiped by a thrown bottle.
 */
public final class AmnesiaPotions {
	public static final Holder<MobEffect> AMNESIA_EFFECT = Registry.registerForHolder(
			BuiltInRegistries.MOB_EFFECT,
			ResourceKey.create(Registries.MOB_EFFECT, Archetypes.id("amnesia")),
			new AmnesiaEffect(MobEffectCategory.NEUTRAL, 0xC7A0E8));

	public static final Holder<Potion> AMNESIA = register("amnesia",
			/*? if >=1.21 {*/new Potion("amnesia", new MobEffectInstance(AMNESIA_EFFECT, 1)));
			/*?} else *///new Potion("amnesia", new MobEffectInstance(AMNESIA_EFFECT.value(), 1)));

	public static final Holder<Potion> STRONG_AMNESIA = register("strong_amnesia",
			/*? if >=1.21 {*/new Potion("amnesia", new MobEffectInstance(AMNESIA_EFFECT, 1, 1)));
			/*?} else *///new Potion("amnesia", new MobEffectInstance(AMNESIA_EFFECT.value(), 1, 1)));

	private AmnesiaPotions() {
	}

	private static Holder<Potion> register(final String path, final Potion potion) {
		return Registry.registerForHolder(BuiltInRegistries.POTION,
				ResourceKey.create(Registries.POTION, Archetypes.id(path)), potion);
	}

	public static void initialize() {
		// The third arm — see ManaPotions for the measurement.
		// STAGE 6 — the fourth arm; see ManaPotions for why the loader axis shares one copy of
		// the table through `platform/BrewingSink`.
		//? if fabric && >=26.1 {
		FabricPotionBrewingBuilder.BUILD.register(builder -> {
			builder.registerPotionRecipe(Potions.AWKWARD, Ingredient.of(Items.RED_MUSHROOM), AMNESIA);
			builder.registerPotionRecipe(AMNESIA, Ingredient.of(Items.GLOWSTONE_DUST), STRONG_AMNESIA);
		});
		//?} elif fabric && >=1.20.5 {
		/*FabricBrewingRecipeRegistryBuilder.BUILD.register(builder -> {
			builder.registerPotionRecipe(Potions.AWKWARD, Ingredient.of(Items.RED_MUSHROOM), AMNESIA);
			builder.registerPotionRecipe(AMNESIA, Ingredient.of(Items.GLOWSTONE_DUST), STRONG_AMNESIA);
		});
		*///?} elif neoforge {
		/*NeoForgeEvents.brewingRecipes(AmnesiaPotions::brew);
		*///?} elif forge {
		/*ForgeEvents.brewingRecipes(AmnesiaPotions::brew);
		*///?} else {
		/*FabricBrewingRecipeRegistry.registerPotionRecipe(
				Potions.AWKWARD, Ingredient.of(Items.RED_MUSHROOM), AMNESIA.value());
		FabricBrewingRecipeRegistry.registerPotionRecipe(
				AMNESIA.value(), Ingredient.of(Items.GLOWSTONE_DUST), STRONG_AMNESIA.value());
		*///?}
	}

	// The two mixes, once, for the whole loader axis. Keep in step with the Fabric arms above.
	//? if fabric {
	//?} else {
	/*public static void brew(final com.archetypes.platform.BrewingSink out) {
		out.mix(Potions.AWKWARD, Items.RED_MUSHROOM, AMNESIA);
		out.mix(AMNESIA, Items.GLOWSTONE_DUST, STRONG_AMNESIA);
	}
	*///?}

	private static final class AmnesiaEffect extends InstantaneousMobEffect {
		private AmnesiaEffect(final MobEffectCategory category, final int color) {
			super(category, color);
		}

		// The ServerLevel parameter — see ManaEffects for the measurement note.
		//? if >=1.21.11 {
		@Override
		public void applyInstantaneousEffect(final ServerLevel level, final Entity source,
				final Entity indirectSource, final LivingEntity target, final int amplifier,
				final double factor) {
		//?} else {
		/*@Override
		public void applyInstantaneousEffect(final Entity source,
				final Entity indirectSource, final LivingEntity target, final int amplifier,
				final double factor) {
			// The level arrives as a parameter above and is derived here — the same shape
			// Stage 0-D used for every `hurtServer` impl. An instantaneous effect is only
			// ever applied server-side, on both versions.
			final ServerLevel level = (ServerLevel) target.level();
		*///?}
			// Drinking passes the drinker as both source and target; every
			// projectile path passes the projectile. Only the drink counts.
			if (source != target || !(target instanceof ServerPlayer player)) {
				return;
			}

			if (amplifier >= 1) {
				ModState.forgetArchetype(player);
				Archetypes.LOGGER.info("{} drank Amnesia II — archetype and levels forgotten",
						player.getName().getString());
			} else {
				ModState.forgetNodes(player);
				SkillPoints.shaveLevels(player, Tuning.AMNESIA_LEVEL_KEEP);
				Archetypes.LOGGER.info("{} drank Amnesia I — nodes refunded, a third of levels paid",
						player.getName().getString());
			}

			level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
					player.getX(), player.getY() + 1.5, player.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.6F);
		}
	}
}
