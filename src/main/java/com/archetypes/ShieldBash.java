package com.archetypes;

import java.util.List;
import java.util.Set;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Protector's root active. Server-authoritative: the client only reports
 * the keypress, and everything — node ownership, the shield in hand, the
 * cooldown — is checked here.
 *
 * <p>Placeholder feel by design: vanilla arm swing, shield-block sound, sweep
 * particle. Real animation and sound come once the tree has proven itself.
 */
public final class ShieldBash {
	private ShieldBash() {
	}

	public static void execute(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.PROTECTOR);

		if (ProtectorNodes.rank(SubTree.PROTECTOR, owned, ProtectorNodes.Family.BASH) == 0) {
			return;
		}

		InteractionHand hand = shieldHand(player);

		if (hand == null) {
			return;
		}

		long now = player.level().getGameTime();
		Long readyAt = ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player)
				.getAttached(ModAttachments.BASH_READY_AT);

		if (readyAt != null && now < readyAt) {
			return;
		}

		int slam = ProtectorNodes.rank(SubTree.PROTECTOR, owned, ProtectorNodes.Family.SLAM);
		int recovery = ProtectorNodes.rank(SubTree.PROTECTOR, owned, ProtectorNodes.Family.COOLDOWN);
		int knockback = ProtectorNodes.rank(SubTree.PROTECTOR, owned, ProtectorNodes.Family.KNOCKBACK);
		int wide = ProtectorNodes.rank(SubTree.PROTECTOR, owned, ProtectorNodes.Family.WIDE);

		float damage = Tuning.BASH_DAMAGE
				* Tuning.slamMultiplier(slam)
				* (1.0F - Tuning.KNOCKBACK_DAMAGE_PENALTY * knockback);
		double shove = Tuning.BASH_KNOCKBACK + Tuning.KNOCKBACK_PER_RANK * knockback;

		ServerLevel level = (ServerLevel) player.level();
		Vec3 look = player.getLookAngle();
		AABB reach = player.getBoundingBox()
				.expandTowards(look.scale(Tuning.BASH_RANGE))
				.inflate(0.6);

		// Candidates in front of the player only: a bash is a shove, not a spin.
		List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, reach,
				entity -> entity != player && entity.isAlive() && !entity.isSpectator()
						&& facing(player, entity, look));

		LivingEntity primary = null;
		double best = Double.MAX_VALUE;

		for (LivingEntity target : targets) {
			double distance = target.distanceToSqr(player);

			if (distance < best) {
				best = distance;
				primary = target;
			}
		}

		// The swing happens whether or not it lands — whiffing is feedback too.
		player.swing(hand, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 0.65F);

		// One visible timer: swing floor + ability layer folded together (see
		// Tuning) — no grey sweep. Bashing also spends the melee attack timer,
		// so it replaces a sword swing instead of stacking on top of one.
		((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).setAttached(
				ModAttachments.BASH_READY_AT, now + Tuning.bashCooldownTicks(slam, recovery));
		player.resetAttackStrengthTicker();

		if (primary == null) {
			return;
		}

		float secondaryFraction = Tuning.wideSecondaryFraction(wide);

		for (LivingEntity target : targets) {
			boolean isPrimary = target == primary;

			if (!isPrimary && secondaryFraction <= 0.0F) {
				continue;
			}

			float dealt = isPrimary ? damage : damage * secondaryFraction;
			target.hurtServer(level, player.damageSources().playerAttack(player), dealt);

			// Placeholder knockback: plain push away from the player. TODO: switch
			// to LivingEntity.knockback once its 26.2 argument semantics are pinned
			// down, so knockback resistance applies.
			Vec3 away = target.position().subtract(player.position());
			Vec3 flat = new Vec3(away.x, 0.0, away.z).normalize();
			target.push(flat.x * shove, 0.15, flat.z * shove);

			level.sendParticles(ParticleTypes.SWEEP_ATTACK,
					target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
					1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	private static InteractionHand shieldHand(final ServerPlayer player) {
		if (player.getOffhandItem().is(Items.SHIELD)) {
			return InteractionHand.OFF_HAND;
		}

		if (player.getMainHandItem().is(Items.SHIELD)) {
			return InteractionHand.MAIN_HAND;
		}

		return null;
	}

	/** In front of the player: dot of look and direction-to-target above ~60 deg. */
	private static boolean facing(final ServerPlayer player, final Entity target, final Vec3 look) {
		Vec3 toTarget = target.position().subtract(player.position());
		Vec3 flat = new Vec3(toTarget.x, 0.0, toTarget.z);

		if (flat.lengthSqr() < 1.0E-4) {
			return true;
		}

		return new Vec3(look.x, 0.0, look.z).normalize().dot(flat.normalize()) > 0.5;
	}
}
