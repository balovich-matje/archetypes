package com.archetypes;

import java.util.Comparator;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * The Crusher capstones on the shared ability key. Haymaker for bare fists;
 * the mace capstone joins when its flange gets real perks.
 */
public final class CrusherActives {
	private CrusherActives() {
	}

	/** Haymaker: one enormous punch — multiplied damage and a stun, no
	 * knockback theatrics. Whiffing costs nothing; the cooldown starts only
	 * when a jaw is actually met. */
	public static void haymaker(final ServerPlayer player) {
		var owned = NodePurchases.owned(player, SubTree.CRUSHER);

		if (CrusherNodes.rank(SubTree.CRUSHER, owned, CrusherNodes.Family.HAYMAKER) == 0
				|| WeaponClass.of(player) != WeaponClass.HANDS) {
			return;
		}

		AttachmentTarget target = (AttachmentTarget) player;
		long now = player.level().getGameTime();
		Long readyAt = target.getAttached(ModAttachments.HAYMAKER_READY_AT);

		if (readyAt != null && now < readyAt) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize();

		LivingEntity victim = level.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().expandTowards(flat.scale(Tuning.HAYMAKER_RANGE)).inflate(0.5),
				entity -> entity != player && entity.isAlive() && !entity.isSpectator()
						&& entity.position().subtract(player.position()).normalize().dot(flat) > 0.5)
				.stream()
				.min(Comparator.comparingDouble(player::distanceToSqr))
				.orElse(null);

		if (victim == null) {
			return;
		}

		target.setAttached(ModAttachments.HAYMAKER_READY_AT, now + Tuning.HAYMAKER_COOLDOWN_TICKS);
		player.swing(InteractionHand.MAIN_HAND, true);

		float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE)
				* Tuning.HAYMAKER_DAMAGE_MULTIPLIER);
		victim.hurtServer(level, player.damageSources().playerAttack(player), damage);
		victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
				Tuning.HAYMAKER_STUN_TICKS, Tuning.HAYMAKER_STUN_AMPLIFIER), player);

		level.sendParticles(ParticleTypes.CRIT,
				victim.getX(), victim.getY(0.7), victim.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
				SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 1.0F, 0.7F);
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
				SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.6F);
	}
}
