package com.archetypes.mixin;

import com.archetypes.ModState;
import com.archetypes.Tuning;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import com.archetypes.platform.ArchetypeStore;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
	@Inject(method = "canHitEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
	private void archetypes$seekerPassesThrough(final net.minecraft.world.entity.Entity entity,
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		archetypes$seekerPassesThroughImpl(entity, cir);
	}

	/** Shared implementation of {@link #archetypes$seekerPassesThrough}. */
	@Unique
	private void archetypes$seekerPassesThroughImpl(final net.minecraft.world.entity.Entity entity, final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		AbstractArrow arrow = (AbstractArrow) (Object) this;

		if (Boolean.TRUE.equals(ArchetypeStore.INSTANCE.get(arrow, ModState.TRUE_SHOT_HOMING))
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
	// STAGE 5 — A RE-ROOT ONTO THE CONSTANT ITSELF. `getDefaultGravity()` arrived at 1.20.5;
	// below it an arrow's gravity is the literal `0.05` subtracted from the delta inside
	// `AbstractArrow.tick()`, and that subtraction sits inside vanilla's own
	// `if (!this.isNoGravity())` — the same short-circuit the modern hook composes with, so a
	// True Shot's no-gravity arrow still ignores both marks in the same order.
	//
	// MEASURED (`javap -c -constants` on the 1.20.1 mojmap jar): `tick()` guards the
	// subtraction with `832: isNoGravity()` / `835: ifne`, then does
	// `setDeltaMovement(v.x, v.y - <const>, v.z)` across offsets 848-868 — vanilla's own
	// short-circuit, exactly as the modern arm composes with.
	//
	// AND THE CONSTANT IS NOT `0.05`. Stage 5 wrote `doubleValue = 0.05` from a reading of
	// that javap dump that stopped at the third character, and `injectors.defaultRequire: 1`
	// duly failed the node's boot with `expected 1 invocation(s) but 0 succeeded`. The
	// literal at offset 859 is `ldc2_w double 0.05000000074505806d` — vanilla writes the
	// gravity as a FLOAT and javac widens it, so the double in the constant pool is
	// `(double) 0.05F` and nothing else. Spelled that way here rather than as the 17 digits,
	// because the digits are what invites the same mistake back: this is a widened float, and
	// the annotation's own widening produces the identical bits.
	//
	// Nothing else in `tick()` can be caught by it: the only other 0.05 in the method is the
	// unrelated `float 0.05f` at 739 (the water/air inertia pair), which a `doubleValue`
	// constant cannot match, and no other `ldc2_w` in the method carries this value.
	//
	// The product it produces — `(double) 0.05F * SPELLBOW_ARROW_GRAVITY_FACTOR` — is the
	// same number the modern arm returns, because `getDefaultGravity()` returns that same
	// widened float from 1.20.5 up.
	//? if >=1.20.5 {
	@ModifyReturnValue(method = "getDefaultGravity()D", at = @At("RETURN"))
	private double archetypes$spellbowGravity(final double original) {
		return archetypes$spellbowGravityImpl(original);
	}
	//?} else {
	/*@org.spongepowered.asm.mixin.injection.ModifyConstant(method = "tick()V",
			constant = @org.spongepowered.asm.mixin.injection.Constant(doubleValue = (double) 0.05F))
	private double archetypes$spellbowGravity(final double original) {
		return archetypes$spellbowGravityImpl(original);
	}
	*///?}

	/** Shared implementation of {@link #archetypes$spellbowGravity}. */
	@Unique
	private double archetypes$spellbowGravityImpl(final double original) {
		AbstractArrow arrow = (AbstractArrow) (Object) this;

		return Boolean.TRUE.equals(ArchetypeStore.INSTANCE.get(arrow, ModState.SPELLBOW_ARROW))
				? original * Tuning.SPELLBOW_ARROW_GRAVITY_FACTOR
				: original;
	}

	/** The Spellbow's arrow carries the Wizard's missile signature down range. */
	@Inject(method = "tick()V", at = @At("HEAD"))
	private void archetypes$spellbowFlightFx(final CallbackInfo ci) {
		archetypes$spellbowFlightFxImpl();
	}

	/** Shared implementation of {@link #archetypes$spellbowFlightFx}. */
	@Unique
	private void archetypes$spellbowFlightFxImpl() {
		AbstractArrow arrow = (AbstractArrow) (Object) this;

		if (!(arrow.level() instanceof net.minecraft.server.level.ServerLevel level)
				|| !Boolean.TRUE.equals(
						ArchetypeStore.INSTANCE.get(arrow, ModState.SPELLBOW_ARROW))) {
			return;
		}

		// A stopped arrow is stuck or spent; the trail ends with the flight.
		if (arrow.getDeltaMovement().lengthSqr() < 0.01) {
			return;
		}

		com.archetypes.items.MagicBowItem.flightFx(level, arrow, arrow.tickCount);
	}

	@Inject(method = "onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V", at = @At("TAIL"))
	private void archetypes$spellbowImpactFx(final net.minecraft.world.phys.EntityHitResult hit,
			final CallbackInfo ci) {
		archetypes$spellbowImpactFxImpl();
	}

	/** Shared implementation of {@link #archetypes$spellbowImpactFx}. */
	@Unique
	private void archetypes$spellbowImpactFxImpl() {
		AbstractArrow arrow = (AbstractArrow) (Object) this;

		if (arrow.level() instanceof net.minecraft.server.level.ServerLevel level
				&& Boolean.TRUE.equals(
						ArchetypeStore.INSTANCE.get(arrow, ModState.SPELLBOW_ARROW))) {
			com.archetypes.items.MagicBowItem.impactFx(level, arrow);
		}
	}

	/**
	 * A Deadeye arrow wears a thin crit trail down range, on the same per-tick
	 * hook the Spellbow's trail uses and at a quarter of its density. Drawn
	 * from the mark on the ARROW, not from the shooter's stance: 64 blocks is
	 * over three seconds of flight and the stance can lapse mid-air.
	 */
	@Inject(method = "tick()V", at = @At("HEAD"))
	private void archetypes$deadeyeFlightFx(final CallbackInfo ci) {
		archetypes$deadeyeFlightFxImpl();
	}

	/** Shared implementation of {@link #archetypes$deadeyeFlightFx}. */
	@Unique
	private void archetypes$deadeyeFlightFxImpl() {
		AbstractArrow arrow = (AbstractArrow) (Object) this;

		if (!(arrow.level() instanceof net.minecraft.server.level.ServerLevel level)
				|| arrow.tickCount % Tuning.DEADEYE_TRAIL_PERIOD_TICKS != 0
				|| !Boolean.TRUE.equals(
						ArchetypeStore.INSTANCE.get(arrow, ModState.DEADEYE_ARROW))) {
			return;
		}

		// A stopped arrow is stuck or spent; the trail ends with the flight.
		if (arrow.getDeltaMovement().lengthSqr() < 0.01) {
			return;
		}

		level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
				arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
	}

	@Inject(method = "tick()V", at = @At("HEAD"))
	private void archetypes$trueShotFlight(final CallbackInfo ci) {
		archetypes$trueShotFlightImpl();
	}

	/** Shared implementation of {@link #archetypes$trueShotFlight}. */
	@Unique
	private void archetypes$trueShotFlightImpl() {
		AbstractArrow arrow = (AbstractArrow) (Object) this;

		if (arrow.level().isClientSide()) {
			return;
		}

		final Entity target = arrow;
		Vec3 origin = ArchetypeStore.INSTANCE.get(target, ModState.TRUE_SHOT_ORIGIN);

		if (origin == null) {
			return;
		}

		if (arrow.position().distanceToSqr(origin) > Tuning.TRUE_SHOT_RANGE_BLOCKS
				* Tuning.TRUE_SHOT_RANGE_BLOCKS) {
			arrow.discard();
			return;
		}

		if (!Boolean.TRUE.equals(ArchetypeStore.INSTANCE.get(target, ModState.TRUE_SHOT_HOMING))) {
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
	@Inject(method = "onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V", at = @At("TAIL"))
	private void archetypes$applyReflectAim(final net.minecraft.world.phys.EntityHitResult hit,
			final CallbackInfo ci) {
		archetypes$applyReflectAimImpl();
	}

	/** Shared implementation of {@link #archetypes$applyReflectAim}. */
	@Unique
	private void archetypes$applyReflectAimImpl() {
		AbstractArrow arrow = (AbstractArrow) (Object) this;

		if (arrow.level().isClientSide()) {
			return;
		}

		Vec3 aim = ArchetypeStore.INSTANCE.remove(arrow, ModState.REFLECT_AIM);

		if (aim != null && !arrow.isRemoved()) {
			arrow.setDeltaMovement(aim);
			arrow.hurtMarked = true;
		}
	}
}
