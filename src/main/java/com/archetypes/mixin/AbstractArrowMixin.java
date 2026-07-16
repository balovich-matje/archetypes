package com.archetypes.mixin;

import com.archetypes.ModAttachments;
import com.archetypes.Tuning;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * True Shot's flight rules. A gravity-free arrow never lands, so it quietly
 * despawns 64 blocks from where it left the bow; the Seeker Arrow capstone
 * additionally bends each tick toward the nearest living thing.
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
}
