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
// STAGE 5: `HealOrHarmMobEffect` does not exist below 1.21 — instant health and instant
// damage are plain `InstantenousMobEffect` instances there and the behaviour they share lives
// in `MobEffect.applyInstantenousEffect` itself, which is the method this wraps either way.
// So the TARGET CLASS moves up to the base class and the method string does not move at all.
//
// The hook is WIDER on that node and the widening is inert: it now brackets every
// instantaneous effect rather than the two healing ones, but the flag it sets is read only by
// `ColossusSlayer`'s healing multiplier, and instant health is the only vanilla
// instantaneous effect that heals. A third-party instant effect that heals would be counted
// magical here and not on the newer nodes — which is the more defensible answer of the two,
// and is recorded rather than hidden.
//? if >=1.21 {
@Mixin(targets = "net.minecraft.world.effect.HealOrHarmMobEffect")
//?} else {
/*@Mixin(targets = "net.minecraft.world.effect.MobEffect")
*///?}
public abstract class HealOrHarmMobEffectMixin {
	/**
	 * One wrap, not a HEAD/RETURN pair: a RETURN inject is skipped when the
	 * wrapped call throws, and the flag is a static — a single exception
	 * anywhere under here would leave every later heal in the server marked
	 * magical until the next potion. try/finally cannot leak.
	 */
	// TWO deltas below 1.21.2, and the first is a SPELLING: the method is
	// `applyInstantenousEffect` there — Mojang's own misspelling, which 1.21.2 fixed at the
	// same time it added the `ServerLevel`. Read out of the 1.21.1 jar with `javap -s`, not
	// guessed; a rename that looks like a typo is exactly the kind a resolver has to be shown.
	//
	// ⚠ THE ONLY WHOLE-METHOD FORK IN THIS PASS. `@WrapMethod`'s handler descriptor IS the
	// target's, so the parameter list and the `original.call` argument list move together and
	// the body cannot stay outside the block. Nothing balance-bearing is duplicated — the flag
	// is a boolean and the arithmetic it gates lives in ColossusSlayer — but the try/finally
	// now exists twice and has to be edited twice.
	//
	// The legacy arm early-outs on a client level for MobEffectInstanceMixin's reason: the
	// `ServerLevel` parameter is what makes the arm above server-only, and below it nothing
	// does, while `ColossusSlayer.magicalHealing` is a plain static.
	//? if >=1.21.2 {
	@WrapMethod(method = "applyInstantaneousEffect(Lnet/minecraft/server/level/ServerLevel;"
			+ "Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;"
			+ "Lnet/minecraft/world/entity/LivingEntity;ID)V")
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
	//?} else {
	/*@WrapMethod(method = "applyInstantenousEffect(Lnet/minecraft/world/entity/Entity;"
			+ "Lnet/minecraft/world/entity/Entity;"
			+ "Lnet/minecraft/world/entity/LivingEntity;ID)V")
	private void archetypes$magicalHeal(final Entity source, final Entity owner,
			final LivingEntity mob, final int amplification, final double scale,
			final Operation<Void> original) {
		if (mob.level().isClientSide()) {
			original.call(source, owner, mob, amplification, scale);
			return;
		}

		ColossusSlayer.beginMagicalHealing();

		try {
			original.call(source, owner, mob, amplification, scale);
		} finally {
			ColossusSlayer.endMagicalHealing();
		}
	}
	*///?}
}
