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
	 */
	@Inject(method = "deflect", at = @At("RETURN"))
	private void archetypes$reflect(final ProjectileDeflection deflection, final Entity deflector,
			final EntityReference<Entity> newOwner, final boolean fromAttack,
			final CallbackInfoReturnable<Boolean> cir) {
		Projectile self = (Projectile) (Object) this;

		if (!cir.getReturnValue() || self.level().isClientSide()
				|| !(deflector instanceof ServerPlayer player)) {
			return;
		}

		if (ProtectorNodes.rank(SubTree.PROTECTOR, NodePurchases.owned(player, SubTree.PROTECTOR),
				ProtectorNodes.Family.REFLECT) <= 0) {
			return;
		}

		Entity shooter = self.getOwner();

		if (!(shooter instanceof LivingEntity) || shooter == player) {
			return;
		}

		Vec3 aim = shooter.getEyePosition().subtract(self.position()).normalize();
		double speed = Math.max(self.getDeltaMovement().length(), 0.75);
		self.setDeltaMovement(aim.scale(speed));
		self.setOwner(player);

		ServerLevel level = (ServerLevel) self.level();
		level.sendParticles(ParticleTypes.CRIT, self.getX(), self.getY(), self.getZ(),
				6, 0.1, 0.1, 0.1, 0.05);
		level.playSound(null, self.getX(), self.getY(), self.getZ(),
				SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.8F, 1.5F);

		if (self instanceof AbstractArrow arrow) {
			arrow.setBaseDamage(((AbstractArrowAccessor) arrow).archetypes$getBaseDamage()
					* Tuning.REFLECT_DAMAGE_FACTOR);
		}
	}
}
