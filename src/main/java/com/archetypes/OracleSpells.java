package com.archetypes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The epic Oracle actives. No cooldowns — mana is the only gate, same as the
 * base Seeker casts. The lightning mechanism (bolts, chains, recurrence) lives
 * in {@link OracleStrikes}; this class resolves ownership, targets and price.
 */
public final class OracleSpells {
	private OracleSpells() {
	}

	/**
	 * Lightning Strike — a targeted 40-damage (20-heart) bolt of indirect magic
	 * to a hostile under the crosshair up to 32 blocks off, for a flat 150 mana.
	 * The tree shapes it: Overcharge doubles the damage, Recurrence lands extra
	 * strikes on a short delay so they read as successive bolts, Chain Reaction
	 * arcs every strike to nearby hostiles, and Tempest turns it area-targeted —
	 * every hostile near the aim point struck, extra mana per target beyond the
	 * first, striking only as far as the pool covers. Total bolts are capped so
	 * a Tempest into a horde can't freeze the server.
	 */
	public static void lightningStrike(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.ORACLE_ELEMENTALIST);

		if (OracleElementalistNodes.rank(SubTree.ORACLE_ELEMENTALIST, owned,
				OracleElementalistNodes.Family.LIGHTNING_STRIKE) <= 0
				|| !ModItems.isWand(player.getMainHandItem())) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();

		float damage = Tuning.LIGHTNING_STRIKE_DAMAGE;

		if (OracleElementalistNodes.rank(SubTree.ORACLE_ELEMENTALIST, owned,
				OracleElementalistNodes.Family.OVERCHARGE) > 0) {
			damage *= Tuning.LIGHTNING_OVERCHARGE_FACTOR;
		}

		int repeats = OracleElementalistNodes.rank(SubTree.ORACLE_ELEMENTALIST, owned,
				OracleElementalistNodes.Family.RECURRENCE);
		int chains = OracleElementalistNodes.rank(SubTree.ORACLE_ELEMENTALIST, owned,
				OracleElementalistNodes.Family.CHAIN);
		boolean tempest = OracleElementalistNodes.rank(SubTree.ORACLE_ELEMENTALIST, owned,
				OracleElementalistNodes.Family.TEMPEST) > 0;

		// Aim: the eye ray, clamped to the first wall so nothing is struck
		// through terrain.
		Vec3 from = player.getEyePosition();
		Vec3 to = from.add(player.getLookAngle().scale(Tuning.LIGHTNING_STRIKE_RANGE));
		HitResult blockHit = player.pick(Tuning.LIGHTNING_STRIKE_RANGE, 1.0F, false);

		if (blockHit.getType() == HitResult.Type.BLOCK) {
			to = blockHit.getLocation();
		}

		EntityHitResult aimed = ProjectileUtil.getEntityHitResult(level, player, from, to,
				player.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0),
				e -> e instanceof LivingEntity living && living.isAlive() && living instanceof Enemy
						&& living != player, 0.5F);

		List<LivingEntity> primaries = new ArrayList<>();

		if (tempest) {
			// The aim point is the struck entity if the crosshair rests on one,
			// else where the ray meets the world.
			final Vec3 point = aimed != null ? aimed.getLocation() : to;
			double radiusSq = Tuning.LIGHTNING_TEMPEST_RADIUS * Tuning.LIGHTNING_TEMPEST_RADIUS;
			AABB box = new AABB(point.x - Tuning.LIGHTNING_TEMPEST_RADIUS,
					point.y - Tuning.LIGHTNING_TEMPEST_RADIUS, point.z - Tuning.LIGHTNING_TEMPEST_RADIUS,
					point.x + Tuning.LIGHTNING_TEMPEST_RADIUS, point.y + Tuning.LIGHTNING_TEMPEST_RADIUS,
					point.z + Tuning.LIGHTNING_TEMPEST_RADIUS);
			primaries.addAll(level.getEntitiesOfClass(LivingEntity.class, box,
					e -> e.isAlive() && e instanceof Enemy && e != player
							&& e.position().distanceToSqr(point) <= radiusSq));
			primaries.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(point)));
		} else if (aimed != null && aimed.getEntity() instanceof LivingEntity victim) {
			primaries.add(victim);
		}

		if (primaries.isEmpty()) {
			return;
		}

		// Cap the total bolts one cast can schedule: strikes-per-target times
		// (self + chains), trimmed to LIGHTNING_MAX_BOLTS worth of primaries.
		int boltsPerPrimary = (1 + repeats) * (1 + chains);
		int maxPrimaries = Math.max(1, Tuning.LIGHTNING_MAX_BOLTS / boltsPerPrimary);

		if (primaries.size() > maxPrimaries) {
			primaries = primaries.subList(0, maxPrimaries);
		}

		int targets = primaries.size();

		// Price: flat for a single target; Tempest adds per extra target and
		// strikes only as many as the pool covers.
		if (tempest) {
			int affordableExtra = (int) Math.floor(
					(Mana.current(player) - Tuning.LIGHTNING_STRIKE_COST)
							/ Tuning.LIGHTNING_TEMPEST_MANA_PER_EXTRA);

			if (affordableExtra < 0) {
				return;
			}

			targets = Math.min(targets, 1 + affordableExtra);

			if (!Mana.spend(player, Tuning.LIGHTNING_STRIKE_COST
					+ Tuning.LIGHTNING_TEMPEST_MANA_PER_EXTRA * (targets - 1))) {
				return;
			}
		} else if (!Mana.spend(player, Tuning.LIGHTNING_STRIKE_COST)) {
			return;
		}

		player.swing(InteractionHand.MAIN_HAND, true);

		long now = level.getGameTime();

		for (int t = 0; t < targets; t++) {
			LivingEntity primary = primaries.get(t);
			// First strike now; each recurrence a short beat later, chaining too.
			OracleStrikes.strike(level, player, primary, damage, chains);

			for (int r = 1; r <= repeats; r++) {
				OracleStrikes.schedule(level, player, primary, damage, chains,
						now + (long) r * Tuning.LIGHTNING_RECURRENCE_DELAY_TICKS);
			}
		}
	}

	/**
	 * Oracle Wizard active: Magic Armaments. The Ability-6 press toggles the
	 * conjured-weapon channel on or off; the mechanism lives in
	 * {@link MagicArmaments}.
	 */
	public static void magicArmaments(final ServerPlayer player) {
		MagicArmaments.toggle(player);
	}
}
