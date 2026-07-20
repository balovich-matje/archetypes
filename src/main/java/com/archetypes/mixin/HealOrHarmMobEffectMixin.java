package com.archetypes.mixin;

import com.archetypes.ColossusSlayer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The other half of "which heals are magic": Instant Health. It never ticks —
 * it is applied once, from three different callers (drunk, splashed, lingering)
 * — so {@code MobEffectInstanceMixin}'s funnel misses it, and the one place all
 * three meet is this override.
 *
 * <p>{@code HealOrHarmMobEffect} is package-private, hence the string target.
 * The alternative was three mixins on three unrelated callers.
 */
@Mixin(targets = "net.minecraft.world.effect.HealOrHarmMobEffect")
public abstract class HealOrHarmMobEffectMixin {
	@Inject(method = "applyInstantaneousEffect", at = @At("HEAD"))
	private void archetypes$magicalHealBegin(final ServerLevel level, final Entity source,
			final Entity owner, final LivingEntity mob, final int amplification,
			final double scale, final CallbackInfo ci) {
		ColossusSlayer.beginMagicalHealing();
	}

	@Inject(method = "applyInstantaneousEffect", at = @At("RETURN"))
	private void archetypes$magicalHealEnd(final ServerLevel level, final Entity source,
			final Entity owner, final LivingEntity mob, final int amplification,
			final double scale, final CallbackInfo ci) {
		ColossusSlayer.endMagicalHealing();
	}
}
