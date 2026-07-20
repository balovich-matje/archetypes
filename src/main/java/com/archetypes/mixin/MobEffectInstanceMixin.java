package com.archetypes.mixin;

import com.archetypes.ColossusSlayer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Barbarian's healing half needs to know which heals are magic, and healing
 * carries no {@code DamageSource} to ask. Anything an effect heals is magic by
 * definition, and {@code tickServer} is the single funnel every ticking effect
 * goes through — so the flag is raised around the whole call rather than
 * around {@code RegenerationMobEffect}, which is package-private and would have
 * to be named by string.
 *
 * <p>Effects that hurt rather than heal are unaffected: the flag is only ever
 * read by the {@code heal} shaper.
 */
@Mixin(MobEffectInstance.class)
public abstract class MobEffectInstanceMixin {
	@Inject(method = "tickServer", at = @At("HEAD"))
	private void archetypes$magicalHealBegin(final ServerLevel level, final LivingEntity target,
			final Runnable onEffectUpdate, final CallbackInfoReturnable<Boolean> cir) {
		ColossusSlayer.beginMagicalHealing();
	}

	@Inject(method = "tickServer", at = @At("RETURN"))
	private void archetypes$magicalHealEnd(final ServerLevel level, final LivingEntity target,
			final Runnable onEffectUpdate, final CallbackInfoReturnable<Boolean> cir) {
		ColossusSlayer.endMagicalHealing();
	}
}
