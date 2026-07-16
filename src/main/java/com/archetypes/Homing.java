package com.archetypes;

import java.util.function.Predicate;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Shared projectile guidance for Seeker Arrow and Seeker Missile. The first
 * pass picked "nearest within 8 blocks" and nudged 20% a tick — at three
 * blocks per tick that meant a couple of weak corrections toward whatever
 * happened to be closest, including things already behind the projectile,
 * which read as homing that works half the time. Now: a longer sensor, a
 * forward cone so only things roughly ahead count, and a turn strong enough
 * to matter at arrow speeds.
 */
public final class Homing {
	/** Only chase what lies within ~78 degrees of the current heading. */
	private static final double MIN_DOT = 0.2;
	/** Per-tick blend toward the target direction. */
	private static final double TURN = 0.5;

	private Homing() {
	}

	/** The nearest valid target inside the forward cone, or null. */
	public static @Nullable LivingEntity pickTarget(final Entity projectile, final double radius,
			final Predicate<LivingEntity> filter) {
		Vec3 heading = projectile.getDeltaMovement();

		if (heading.lengthSqr() < 1.0E-4) {
			return null;
		}

		Vec3 direction = heading.normalize();
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;

		for (LivingEntity candidate : projectile.level().getEntitiesOfClass(LivingEntity.class,
				projectile.getBoundingBox().inflate(radius),
				living -> living.isAlive() && !living.isSpectator() && filter.test(living))) {
			Vec3 toTarget = candidate.getBoundingBox().getCenter().subtract(projectile.position());
			double distance = toTarget.length();

			if (distance < 1.0E-3 || toTarget.scale(1.0 / distance).dot(direction) < MIN_DOT) {
				continue;
			}

			if (distance < bestDistance) {
				bestDistance = distance;
				best = candidate;
			}
		}

		return best;
	}

	/** Bend the projectile toward the target, keeping its speed. */
	public static void steer(final Entity projectile, final LivingEntity target) {
		Vec3 velocity = projectile.getDeltaMovement();
		double speed = velocity.length();
		Vec3 toTarget = target.getBoundingBox().getCenter().subtract(projectile.position()).normalize();
		projectile.setDeltaMovement(velocity.normalize().scale(1.0 - TURN).add(toTarget.scale(TURN))
				.normalize().scale(speed));
		projectile.hurtMarked = true;
	}
}
