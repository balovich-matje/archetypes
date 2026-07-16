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

	/** Quake: start the charge — the ticker lands the slam when it ends. */
	public static void quake(final ServerPlayer player) {
		var owned = NodePurchases.owned(player, SubTree.CRUSHER);

		if (CrusherNodes.rank(SubTree.CRUSHER, owned, CrusherNodes.Family.QUAKE) == 0
				|| WeaponClass.of(player) != WeaponClass.MACE) {
			return;
		}

		AttachmentTarget target = (AttachmentTarget) player;
		long now = player.level().getGameTime();
		Long readyAt = target.getAttached(ModAttachments.QUAKE_READY_AT);
		Long charging = target.getAttached(ModAttachments.QUAKE_CHARGE_END);

		if ((readyAt != null && now < readyAt) || (charging != null && charging > now)) {
			return;
		}

		target.setAttached(ModAttachments.QUAKE_READY_AT, now + Tuning.QUAKE_COOLDOWN_TICKS);
		target.setAttached(ModAttachments.QUAKE_CHARGE_END, now + Tuning.QUAKE_CHARGE_TICKS);
		((ServerLevel) player.level()).playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 0.8F, 0.5F);
	}

	/** The slam itself, fired by the ticker as the charge ends: multiplied
	 * attack damage to every enemy in the ring, hostiles launched skyward,
	 * and the ground telling everyone about it. */
	public static void quakeSlam(final ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE)
				* Tuning.QUAKE_DAMAGE_MULTIPLIER);

		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(Tuning.QUAKE_RADIUS, 1.5, Tuning.QUAKE_RADIUS),
				entity -> entity != player && entity.isAlive() && !entity.isSpectator())) {
			victim.hurtServer(level, player.damageSources().playerAttack(player), damage);

			if (victim instanceof net.minecraft.world.entity.monster.Monster) {
				victim.push(0.0, Tuning.QUAKE_LAUNCH, 0.0);
				victim.hurtMarked = true;
			}
		}

		// The earth answers: a ring of this ground's own debris plus the
		// heavy smash — the closest vanilla has to rock breaking rock.
		var ground = player.getBlockStateOn();

		if (!ground.isAir()) {
			for (int i = 0; i < 40; i++) {
				double angle = Math.PI * 2.0 * i / 40.0;
				level.sendParticles(
						new net.minecraft.core.particles.BlockParticleOption(
								net.minecraft.core.particles.ParticleTypes.BLOCK, ground),
						player.getX() + Math.cos(angle) * Tuning.QUAKE_RADIUS * 0.8,
						player.getY() + 0.2,
						player.getZ() + Math.sin(angle) * Tuning.QUAKE_RADIUS * 0.8,
						3, 0.15, 0.25, 0.15, 0.1);
			}
		}

		level.sendParticles(ParticleTypes.EXPLOSION,
				player.getX(), player.getY() + 0.3, player.getZ(), 3, 0.8, 0.2, 0.8, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.5F, 0.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6F, 0.6F);
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

		// Knockback II's worth of send-off.
		Vec3 away = victim.position().subtract(player.position());
		Vec3 push = new Vec3(away.x, 0.0, away.z).normalize().scale(Tuning.HAYMAKER_KNOCKBACK);
		victim.push(push.x, 0.4, push.z);

		level.sendParticles(ParticleTypes.CRIT,
				victim.getX(), victim.getY(0.7), victim.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
				SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 1.0F, 0.7F);
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
				SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.6F);
	}
}
