package com.archetypes;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.InstantaneousMobEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The two mana effects behind the Seeker's potions. Both no-op gracefully on
 * anyone without a real mana pool: adding to a non-Seeker's pool clamps
 * straight back to full.
 */
public final class ManaEffects {
	/** Instant, like Instant Health: a chunk of mana back per level. */
	public static final Holder<MobEffect> MANA_RESTORE = register("mana_restore",
			new ManaRestoreEffect(MobEffectCategory.BENEFICIAL, 0x2F6CDB));

	/** Over time, like Regeneration: bonus regen per second per level. */
	public static final Holder<MobEffect> MANA_REGENERATION = register("mana_regeneration",
			new ManaRegenEffect(MobEffectCategory.BENEFICIAL, 0x9F7BEA));

	private ManaEffects() {
	}

	private static Holder<MobEffect> register(final String path, final MobEffect effect) {
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
				ResourceKey.create(Registries.MOB_EFFECT, Archetypes.id(path)), effect);
	}

	public static void initialize() {
		// Forces static initialization at mod init time.
	}

	private static final class ManaRestoreEffect extends InstantaneousMobEffect {
		private ManaRestoreEffect(final MobEffectCategory category, final int color) {
			super(category, color);
		}

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
		*///?}
			if (target instanceof ServerPlayer player) {
				Mana.add(player, (float) (Tuning.MANA_RESTORE_PER_LEVEL * (amplifier + 1) * factor));
			}
		}

		// `MobEffect.applyEffectTick` gained its leading ServerLevel somewhere between
		// 1.21.1 and 1.21.11 (bracketed on the two mojmap jars; no artifact in between
		// exists on this machine to pinpoint it, and no node in range falls between them).
		// `applyInstantaneousEffect` moved in the same pass, and its NAME is handled by the
		// controller's `Instantenous` typo rule rather than here.
		//? if >=1.21.11 {
		@Override
		public boolean applyEffectTick(final ServerLevel level, final LivingEntity target, final int amplifier) {
		//?} elif >=1.21 {
		/*@Override
		public boolean applyEffectTick(final LivingEntity target, final int amplifier) {
		*///?} else {
		/*@Override
		public void applyEffectTick(final LivingEntity target, final int amplifier) {
		*///?}
			if (target instanceof ServerPlayer player) {
				Mana.add(player, Tuning.MANA_RESTORE_PER_LEVEL * (amplifier + 1));
			}

			//? if >=1.21 {
			return true;
			//?}
		}
	}

	private static final class ManaRegenEffect extends MobEffect {
		private ManaRegenEffect(final MobEffectCategory category, final int color) {
			super(category, color);
		}

		// STAGE 5: 1.21 renamed the tick GATE and gave the tick itself a return value —
		// `isDurationEffectTick(duration, amplifier)` became
		// `shouldApplyEffectTickThisTick`, and `void applyEffectTick` became `boolean`
		// (measured on the 1.20.1 and 1.21.1 mojmap jars). The boolean is vanilla's
		// "keep the effect", which every one of these already answers with `true`, so the
		// legacy arm loses a `return` and nothing else.
		//? if >=1.21 {
		@Override
		public boolean shouldApplyEffectTickThisTick(final int duration, final int amplifier) {
			return true;
		}
		//?} else {
		/*@Override
		public boolean isDurationEffectTick(final int duration, final int amplifier) {
			return true;
		}
		*///?}

		//? if >=1.21.11 {
		@Override
		public boolean applyEffectTick(final ServerLevel level, final LivingEntity target, final int amplifier) {
		//?} elif >=1.21 {
		/*@Override
		public boolean applyEffectTick(final LivingEntity target, final int amplifier) {
		*///?} else {
		/*@Override
		public void applyEffectTick(final LivingEntity target, final int amplifier) {
		*///?}
			if (target instanceof ServerPlayer player) {
				Mana.add(player,
						Tuning.MANA_REGEN_POTION_PER_LEVEL_PER_SECOND * (amplifier + 1) / 20.0F);
			}

			//? if >=1.21 {
			return true;
			//?}
		}
	}
}
