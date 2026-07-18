package com.archetypes;

import net.fabricmc.fabric.api.registry.FabricPotionBrewingBuilder;
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
			new Potion("amnesia", new MobEffectInstance(AMNESIA_EFFECT, 1)));

	public static final Holder<Potion> STRONG_AMNESIA = register("strong_amnesia",
			new Potion("amnesia", new MobEffectInstance(AMNESIA_EFFECT, 1, 1)));

	private AmnesiaPotions() {
	}

	private static Holder<Potion> register(final String path, final Potion potion) {
		return Registry.registerForHolder(BuiltInRegistries.POTION,
				ResourceKey.create(Registries.POTION, Archetypes.id(path)), potion);
	}

	public static void initialize() {
		FabricPotionBrewingBuilder.BUILD.register(builder -> {
			builder.registerPotionRecipe(Potions.AWKWARD, Ingredient.of(Items.RED_MUSHROOM), AMNESIA);
			builder.registerPotionRecipe(AMNESIA, Ingredient.of(Items.GLOWSTONE_DUST), STRONG_AMNESIA);
		});
	}

	private static final class AmnesiaEffect extends InstantaneousMobEffect {
		private AmnesiaEffect(final MobEffectCategory category, final int color) {
			super(category, color);
		}

		@Override
		public void applyInstantaneousEffect(final ServerLevel level, final Entity source,
				final Entity indirectSource, final LivingEntity target, final int amplifier,
				final double factor) {
			// Drinking passes the drinker as both source and target; every
			// projectile path passes the projectile. Only the drink counts.
			if (source != target || !(target instanceof ServerPlayer player)) {
				return;
			}

			if (amplifier >= 1) {
				ModAttachments.forgetArchetype(player);
				Archetypes.LOGGER.info("{} drank Amnesia II — archetype and levels forgotten",
						player.getName().getString());
			} else {
				ModAttachments.forgetNodes(player);
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
