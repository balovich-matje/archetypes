package com.archetypes.mixin;

import com.archetypes.ColossusSlayer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
	/**
	 * Spell Reflect, first half. {@code deflection} is vanilla's own "does this
	 * body turn projectiles away", asked by {@code hitTargetOrDeflectSelf}
	 * BEFORE the hit resolves — which is the whole reason to answer it rather
	 * than to cancel damage later: a parried spell never damages anyone and
	 * never discards itself, so there is a live projectile left to send home.
	 *
	 * <p>REVERSE is the same deflection a shield gives, so the second half is
	 * the Protector's Reflection unchanged: {@code ProjectileMixin} already
	 * re-aims a reversed shot at its shooter and hands it a new owner.
	 *
	 * <p>Declared on {@code Entity} because that is where the method is; the
	 * gate inside is ServerPlayer, and the window it reads is server-side only.
	 */
	@Inject(method = "deflection", at = @At("HEAD"), cancellable = true)
	private void archetypes$spellParry(final Projectile projectile,
			final CallbackInfoReturnable<ProjectileDeflection> cir) {
		if ((Object) this instanceof ServerPlayer player
				&& ColossusSlayer.parriesSpell(player, projectile)) {
			cir.setReturnValue(ProjectileDeflection.REVERSE);
		}
	}
}
