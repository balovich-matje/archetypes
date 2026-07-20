package com.archetypes.mixin;

import com.archetypes.ColossusSlayer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import org.spongepowered.asm.mixin.Mixin;

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
	/**
	 * One wrap, not a HEAD/RETURN pair: a RETURN inject is skipped when the
	 * wrapped call throws, and the flag is a static — a single exception
	 * anywhere under here would leave every later heal in the server marked
	 * magical until the next potion. try/finally cannot leak.
	 */
	@WrapMethod(method = "applyInstantaneousEffect")
	private void archetypes$magicalHeal(final ServerLevel level, final Entity source,
			final Entity owner, final LivingEntity mob, final int amplification,
			final double scale, final Operation<Void> original) {
		ColossusSlayer.beginMagicalHealing();

		try {
			original.call(level, source, owner, mob, amplification, scale);
		} finally {
			ColossusSlayer.endMagicalHealing();
		}
	}
}
