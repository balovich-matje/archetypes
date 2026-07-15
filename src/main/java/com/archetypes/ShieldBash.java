package com.archetypes;

import java.util.List;
import java.util.Set;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
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
		int taunt = ProtectorNodes.rank(SubTree.PROTECTOR, owned, ProtectorNodes.Family.TAUNT);
		int groundSlam = ProtectorNodes.rank(SubTree.PROTECTOR, owned, ProtectorNodes.Family.GROUND_SLAM);

		float damage = Tuning.BASH_DAMAGE
				* Tuning.slamMultiplier(slam)
				* (1.0F - Tuning.KNOCKBACK_DAMAGE_PENALTY * knockback);
		double shove = Tuning.BASH_KNOCKBACK + Tuning.KNOCKBACK_PER_RANK * knockback;

		ServerLevel level = (ServerLevel) player.level();
		Vec3 look = player.getLookAngle();
		AABB reach = player.getBoundingBox()
				.expandTowards(look.scale(Tuning.BASH_RANGE))
				.inflate(0.6);

		// Ground Slam turns the shove into a ring around the player; otherwise
		// candidates are in front only — a bash is a shove, not a spin.
		double slamRadius = Tuning.groundSlamRadius(wide);
		List<LivingEntity> targets = groundSlam > 0
				? level.getEntitiesOfClass(LivingEntity.class,
						player.getBoundingBox().inflate(slamRadius, 1.0, slamRadius),
						entity -> entity != player && entity.isAlive() && !entity.isSpectator())
				: level.getEntitiesOfClass(LivingEntity.class, reach,
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
		// Slam ranks deepen the thunk; Ground Slam gets the mace's crash.
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				groundSlam > 0 ? SoundEvents.MACE_SMASH_GROUND : SoundEvents.SHIELD_BLOCK.value(),
				SoundSource.PLAYERS, 1.0F, groundSlam > 0 ? 1.0F : 0.65F - 0.05F * slam);

		if (groundSlam > 0) {
			slamDebris(level, player, slamRadius, wide);
		}

		// One visible timer: swing floor + ability layer folded together (see
		// Tuning) — no grey sweep. Bashing also spends the melee attack timer,
		// so it replaces a sword swing instead of stacking on top of one.
		((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).setAttached(
				ModAttachments.BASH_READY_AT, now + Tuning.bashCooldownTicks(slam, recovery));
		player.resetAttackStrengthTicker();

		// Taunt: the bash is also a challenge — every monster in earshot drops
		// what it is doing and comes for you. Vanilla target AI does the rest;
		// no custom goals involved.
		if (taunt > 0) {
			for (Mob mob : level.getEntitiesOfClass(Mob.class,
					player.getBoundingBox().inflate(Tuning.TAUNT_RADIUS),
					mob -> mob instanceof Enemy && mob.isAlive())) {
				mob.setTarget(player);
				level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
						mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(),
						1, 0.1, 0.1, 0.1, 0.0);
			}
		}

		if (primary == null) {
			// Whiffed: a sweep in front is the feedback that the swing happened.
			if (groundSlam <= 0) {
				Vec3 front = player.position().add(look.x * 1.5, player.getBbHeight() * 0.5, look.z * 1.5);
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, front.x, front.y, front.z,
						1, 0.0, 0.0, 0.0, 0.0);
			}

			return;
		}

		float secondaryFraction = Tuning.wideSecondaryFraction(wide);

		for (LivingEntity target : targets) {
			boolean isPrimary = target == primary;

			// Ground Slam hits its whole ring at full strength; otherwise Wide
			// Swings decides what the extra targets take.
			if (groundSlam <= 0 && !isPrimary && secondaryFraction <= 0.0F) {
				continue;
			}

			float dealt = isPrimary || groundSlam > 0 ? damage : damage * secondaryFraction;
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

	/**
	 * The slam's visual, in three layers so the exact range is legible at a
	 * glance. Count 0 turns sendParticles' offsets into a velocity.
	 *
	 * <ul>
	 * <li><b>Edge ring</b> — evenly spaced dust in the ground block's own map
	 * colour, sitting exactly on the radius. Dust takes a scale, so the ring's
	 * particles literally grow with Wide Swings.</li>
	 * <li><b>Shockwave</b> — clouds pushed outward from the player toward the
	 * edge, sweeping the area the hit covers.</li>
	 * <li><b>Debris</b> — pieces of the block underfoot popped upward and left
	 * to fall; more and livelier per Wide rank.</li>
	 * </ul>
	 */
	private static void slamDebris(final ServerLevel level, final ServerPlayer player,
			final double radius, final int wide) {
		BlockState ground = player.getBlockStateOn();

		if (ground.isAir()) {
			return;
		}

		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();

		// Edge ring: the range marker.
		int dustColor = ground.getMapColor(level, player.getOnPos()).col;
		DustParticleOptions dust = new DustParticleOptions(dustColor, 1.2F + 0.6F * wide);
		int ringPoints = 20 + 10 * wide;

		for (int i = 0; i < ringPoints; i++) {
			double angle = i * (Math.PI * 2.0 / ringPoints);
			level.sendParticles(dust,
					x + Math.cos(angle) * radius, y + 0.15, z + Math.sin(angle) * radius,
					0, 0.0, 0.05, 0.0, 1.0);
		}

		// Shockwave: clouds racing from the centre to the edge.
		for (int i = 0; i < 12; i++) {
			double angle = i * (Math.PI * 2.0 / 12);
			double push = 0.12 * radius;
			level.sendParticles(ParticleTypes.CLOUD,
					x + Math.cos(angle) * 0.4, y + 0.1, z + Math.sin(angle) * 0.4,
					0, Math.cos(angle) * push, 0.02, Math.sin(angle) * push, 1.0);
		}

		// Debris: the ground itself, thrown up and falling back.
		// Interior sits at the edge count plus about half of it, so the area
		// reads filled without drowning out the range ring.
		BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, ground);
		int pieces = 30 + 18 * wide;

		for (int i = 0; i < pieces; i++) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
			double distance = 0.6 + level.getRandom().nextDouble() * (radius - 0.5);
			level.sendParticles(debris,
					x + Math.cos(angle) * distance, y + 0.1, z + Math.sin(angle) * distance,
					0, 0.0, 0.25 + level.getRandom().nextDouble() * (0.25 + 0.1 * wide), 0.0, 1.0);
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
