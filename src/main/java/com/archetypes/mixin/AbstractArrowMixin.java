package com.archetypes.mixin;

import com.archetypes.ModAttachments;
import com.archetypes.Tuning;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Arrow flight rules for two marks.
 *
 * <p>True Shot: a gravity-free arrow never lands, so it quietly despawns 64
 * blocks from where it left the bow; the Seeker Arrow capstone additionally
 * bends each tick toward the nearest living thing.
 *
 * <p>Spellbow: quarter gravity and the Magic Missile's trail. The mark lives on
 * the arrow, not the bow, so an arrow already in flight keeps both after the
 * conjured bow that fired it is dismissed.
 */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin {
	/**
	 * The Seeker Arrow only exists for hostiles: everything else is a ghost
	 * it flies straight through — no accidental pet or villager casualties
	 * from a shot that aims itself.
	 */
	@Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
	private void archetypes$seekerPassesThrough(final net.minecraft.world.entity.Entity entity,
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		AbstractArrow arrow = (AbstractArrow) (Object) this;

		if (Boolean.TRUE.equals(((AttachmentTarget) arrow).getAttached(ModAttachments.TRUE_SHOT_HOMING))
				&& !(entity instanceof net.minecraft.world.entity.monster.Enemy)) {
			cir.setReturnValue(false);
		}
	}

	/**
	 * Spellbow arrows fly nearly flat. Shaped on the arrow's own gravity rather
	 * than by inflating its launch velocity, because velocity is what
	 * {@code onHitEntity} multiplies the base damage by — a velocity hack would
	 * buff the damage too. {@code getGravity} is final; {@code getDefaultGravity}
	 * is the overridable half, and vanilla still short-circuits it to 0 for a
	 * no-gravity arrow (a True Shot), so the two marks compose correctly.
	 */
	@ModifyReturnValue(method = "getDefaultGravity", at = @At("RETURN"))
	private double archetypes$spellbowGravity(final double original) {
		AbstractArrow arrow = (AbstractArrow) (Object) this;

		return Boolean.TRUE.equals(((AttachmentTarget) arrow).getAttached(ModAttachments.SPELLBOW_ARROW))
				? original * Tuning.SPELLBOW_ARROW_GRAVITY_FACTOR
				: original;
	}

	/** The Spellbow's arrow carries the Wizard's missile signature down range. */
	@Inject(method = "tick", at = @At("HEAD"))
	private void archetypes$spellbowFlightFx(final CallbackInfo ci) {
		AbstractArrow arrow = (AbstractArrow) (Object) this;

		if (!(arrow.level() instanceof net.minecraft.server.level.ServerLevel level)
				|| !Boolean.TRUE.equals(
						((AttachmentTarget) arrow).getAttached(ModAttachments.SPELLBOW_ARROW))) {
			return;
		}

		// A stopped arrow is stuck or spent; the trail ends with the flight.
		if (arrow.getDeltaMovement().lengthSqr() < 0.01) {
			return;
		}

		com.archetypes.items.MagicBowItem.flightFx(level, arrow, arrow.tickCount);
	}

	@Inject(method = "onHitEntity", at = @At("TAIL"))
	private void archetypes$spellbowImpactFx(final net.minecraft.world.phys.EntityHitResult hit,
			final CallbackInfo ci) {
		AbstractArrow arrow = (AbstractArrow) (Object) this;

		if (arrow.level() instanceof net.minecraft.server.level.ServerLevel level
				&& Boolean.TRUE.equals(
						((AttachmentTarget) arrow).getAttached(ModAttachments.SPELLBOW_ARROW))) {
			com.archetypes.items.MagicBowItem.impactFx(level, arrow);
		}
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void archetypes$trueShotFlight(final CallbackInfo ci) {
		AbstractArrow arrow = (AbstractArrow) (Object) this;

		if (arrow.level().isClientSide()) {
			return;
		}

		AttachmentTarget target = (AttachmentTarget) arrow;
		Vec3 origin = target.getAttached(ModAttachments.TRUE_SHOT_ORIGIN);

		if (origin == null) {
			return;
		}

		if (arrow.position().distanceToSqr(origin) > Tuning.TRUE_SHOT_RANGE_BLOCKS
				* Tuning.TRUE_SHOT_RANGE_BLOCKS) {
			arrow.discard();
			return;
		}

		if (!Boolean.TRUE.equals(target.getAttached(ModAttachments.TRUE_SHOT_HOMING))) {
			return;
		}

		// A stuck arrow stops steering — its flight is over.
		if (arrow.getDeltaMovement().lengthSqr() < 0.01) {
			return;
		}

		LivingEntity quarry = com.archetypes.Homing.pickTarget(arrow, Tuning.TRUE_SHOT_HOMING_RADIUS,
				living -> living instanceof net.minecraft.world.entity.monster.Enemy
						&& living != arrow.getOwner());

		if (quarry != null) {
			com.archetypes.Homing.steer(arrow, quarry);
		}
	}

	/**
	 * Reflection, second half (see ProjectileMixin): the blocked-arrow branch
	 * of {@code onHitEntity} ends with a {@code scale(0.2)} drop AFTER its
	 * deflect call, so the return-to-sender velocity stashed there is applied
	 * only once the whole hit handler has had its say.
	 */
	@Inject(method = "onHitEntity", at = @At("TAIL"))
	private void archetypes$applyReflectAim(final net.minecraft.world.phys.EntityHitResult hit,
			final CallbackInfo ci) {
		AbstractArrow arrow = (AbstractArrow) (Object) this;

		if (arrow.level().isClientSide()) {
			return;
		}

		Vec3 aim = ((AttachmentTarget) arrow).removeAttached(ModAttachments.REFLECT_AIM);

		if (aim != null && !arrow.isRemoved()) {
			arrow.setDeltaMovement(aim);
			arrow.hurtMarked = true;
		}
	}
}
