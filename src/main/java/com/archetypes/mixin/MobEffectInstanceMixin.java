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
	// `tickServer` is `tick` below 1.21.2, minus the level — and the difference is NOT only
	// the name. `tickServer` is server-only by construction; `tick` is reached from
	// `LivingEntity.baseTick` through `tickEffects`, which runs on BOTH logical sides
	// (measured: `baseTick` calls `tickEffects()` at offset 765 of the 1.21.1 `LivingEntity`,
	// under no `isClientSide` guard). `ColossusSlayer.magicalHealing` is a plain static
	// boolean, so a client-thread pair would race the server thread's in singleplayer —
	// KnockbackSource's problem exactly. Both arms therefore early-out on a client level, and
	// they early-out on the SAME condition, so the HEAD/RETURN pair cannot fall out of step.
	//? if >=1.21.2 {
	@Inject(method = "tickServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/Runnable;)Z", at = @At("HEAD"))
	private void archetypes$magicalHealBegin(final ServerLevel level, final LivingEntity target,
			final Runnable onEffectUpdate, final CallbackInfoReturnable<Boolean> cir) {
	//?} else {
	/*@Inject(method = "tick(Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/Runnable;)Z", at = @At("HEAD"))
	private void archetypes$magicalHealBegin(final LivingEntity target,
			final Runnable onEffectUpdate, final CallbackInfoReturnable<Boolean> cir) {
		if (target.level().isClientSide()) {
			return;
		}

	*///?}
		ColossusSlayer.beginMagicalHealing();
	}

	//? if >=1.21.2 {
	@Inject(method = "tickServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/Runnable;)Z", at = @At("RETURN"))
	private void archetypes$magicalHealEnd(final ServerLevel level, final LivingEntity target,
			final Runnable onEffectUpdate, final CallbackInfoReturnable<Boolean> cir) {
	//?} else {
	/*@Inject(method = "tick(Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/Runnable;)Z", at = @At("RETURN"))
	private void archetypes$magicalHealEnd(final LivingEntity target,
			final Runnable onEffectUpdate, final CallbackInfoReturnable<Boolean> cir) {
		if (target.level().isClientSide()) {
			return;
		}

	*///?}
		ColossusSlayer.endMagicalHealing();
	}
}
