package com.archetypes.mixin;

import com.archetypes.NodePurchases;
import com.archetypes.ProtectorNodes;
import com.archetypes.SubTree;
import com.archetypes.Tuning;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Projectile.class)
public abstract class ProjectileMixin {
	/**
	 * Reflection: vanilla already bounces a blocked projectile off the shield
	 * ({@code hitTargetOrDeflectSelf} → {@code deflect}, keeping the original
	 * owner). When the deflector is a player with the node, the bounce becomes a
	 * return: re-aim at the shooter, hand ownership to the player so it can hurt
	 * its old owner, and halve an arrow's base damage — sent back, but not a
	 * free execute. Vanilla's {@code lastDeflectedBy} already prevents the
	 * returned shot from clipping the player on the way out.
	 *
	 * <p>The velocity is NOT set here: {@code AbstractArrow.onHitEntity} follows
	 * its deflect call with a {@code scale(0.2)} drop that would stomp it (the
	 * arrow used to flop at the blocker's feet). The aim is stashed on the arrow
	 * and applied by {@code AbstractArrowMixin} after the hit handler finishes.
	 */
	/**
	 * Incorporeal: projectiles pass straight through a transformed player.
	 * {@code canHitEntity} is the one gate every projectile's own move-vector
	 * sweep consults, and the subclasses that override it
	 * ({@code ShulkerBullet}, {@code FishingHook}, …) all fold {@code super}'s
	 * answer into their own — so refusing here refuses everywhere that matters,
	 * without touching a single projectile type by name.
	 */
	@Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
	private void archetypes$incorporeal(final Entity entity,
			final CallbackInfoReturnable<Boolean> cir) {
		if (!(entity instanceof ServerPlayer player)) {
			return;
		}

		if (com.archetypes.NightForm.isIncorporeal(player)) {
			cir.setReturnValue(false);
			return;
		}

		// Evasion: the second predicate on the same gate. The puff is drawn at
		// most once per projectile, not once per collision sweep — canHitEntity
		// is asked several times a tick per projectile, and an unmarked puff
		// was a smoke column rather than a pass-through.
		if (com.archetypes.Deadeye.isEvading(player)) {
			Projectile self = (Projectile) (Object) this;
			var onProjectile = (net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) self;

			if (self.level() instanceof ServerLevel level
					&& !Boolean.TRUE.equals(onProjectile.getAttached(
							com.archetypes.ModAttachments.DEADEYE_PHASED))) {
				onProjectile.setAttached(com.archetypes.ModAttachments.DEADEYE_PHASED, true);
				level.sendParticles(ParticleTypes.CLOUD,
						self.getX(), self.getY(), self.getZ(), 6, 0.15, 0.15, 0.15, 0.01);
			}

			cir.setReturnValue(false);
		}
	}

	@Inject(method = "deflect", at = @At("RETURN"))
	private void archetypes$reflect(final ProjectileDeflection deflection, final Entity deflector,
			final EntityReference<Entity> newOwner, final boolean fromAttack,
			final CallbackInfoReturnable<Boolean> cir) {
		Projectile self = (Projectile) (Object) this;

		if (!cir.getReturnValue() || self.level().isClientSide()
				|| !(deflector instanceof ServerPlayer player)) {
			return;
		}

		// Two nodes reach this hook and they agree about what a returned shot
		// is: the Protector's Reflection off a raised shield, and the Colossus
		// Slayer's Spell Reflect off an open parry window (EntityMixin answers
		// vanilla's deflection test so the spell is deflected in the first
		// place). Only the aftermath differs.
		boolean reflect = ProtectorNodes.rank(SubTree.PROTECTOR,
				NodePurchases.owned(player, SubTree.PROTECTOR),
				ProtectorNodes.Family.REFLECT) > 0;
		boolean parried = com.archetypes.ColossusSlayer.parriesSpell(player, self);

		if (!reflect && !parried) {
			return;
		}

		Entity shooter = self.getOwner();

		if (!(shooter instanceof LivingEntity) || shooter == player) {
			return;
		}

		// The REVERSE deflection just halved the arrival speed, so double it
		// back, with a floor that carries the shot to a normal skeleton range.
		Vec3 aim = shooter.getEyePosition().subtract(self.position()).normalize();
		double speed = Math.max(self.getDeltaMovement().length() * 2.0, Tuning.REFLECT_RETURN_SPEED);
		((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) self)
				.setAttached(com.archetypes.ModAttachments.REFLECT_AIM, aim.scale(speed));
		self.setOwner(player);

		// A parried spell is deflected BEFORE its hit handler runs, so there is
		// no post-deflect drop to dodge and the aim can simply be flown — the
		// attachment above is an arrow's contract with AbstractArrowMixin and
		// nothing else consumes it.
		if (parried && !(self instanceof AbstractArrow)) {
			self.setDeltaMovement(aim.scale(speed));
			self.needsSync = true;
		}

		ServerLevel level = (ServerLevel) self.level();
		level.sendParticles(ParticleTypes.CRIT, self.getX(), self.getY(), self.getZ(),
				6, 0.1, 0.1, 0.1, 0.05);
		level.playSound(null, self.getX(), self.getY(), self.getZ(),
				SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.8F, 1.5F);

		if (self instanceof AbstractArrow arrow) {
			arrow.setBaseDamage(((AbstractArrowAccessor) arrow).archetypes$getBaseDamage()
					* Tuning.REFLECT_DAMAGE_FACTOR);
		}

		if (reflect) {
			com.archetypes.ProcIndicators.send(player, SubTree.PROTECTOR,
					ProtectorNodes.Family.REFLECT);
		}

		if (parried) {
			com.archetypes.ColossusSlayer.onSpellParried(player, level);
		}
	}
}
