package com.archetypes;

import java.util.List;
import java.util.Set;

import com.archetypes.platform.ArchetypeStore;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Protector's root active, and the capstone that reshapes it.
 *
 * <p>Server-authoritative: the client only reports the keypress, and everything
 * — node ownership, the shield in hand, the cooldown — is checked here.
 *
 * <h2>Shield Sweep (the {@code GROUND_SLAM} capstone)</h2>
 * The capstone is not a second ability. It is three changes to this one, and
 * that is deliberate: the node opposite it is Bulwark, and a capstone that
 * played its own animation on its own timer would have made the tree's whole
 * left column optional.
 *
 * <ol>
 * <li><b>A wider cone.</b> The bash's 120-degree arc becomes the whole half
 * disc in front ({@link Tuning#SHIELD_SWEEP_CONE_DOT}), over
 * {@link Tuning#shieldSweepRange} instead of {@link Tuning#BASH_RANGE}, and
 * everything in it takes the same blow. Wide Swings' secondary FRACTION stops
 * applying, because a sweep has no secondary targets — Wide feeds the capstone
 * as a block of reach instead.</li>
 * <li><b>The weapon comes with it.</b> The whole of {@code ATTACK_DAMAGE} is
 * added to the bash's own number, so a Protector's sword finally matters to
 * the button they actually press.</li>
 * <li><b>Two shields quadruple it.</b> A shield in both hands has no weapon
 * term to add and no melee outside this button; see
 * {@link Tuning#SHIELD_SWEEP_DUAL_SHIELD_MULTIPLIER}.</li>
 * </ol>
 *
 * <p>The sweep's victims go through {@link MeleeSwing}, opened ONCE around all
 * of them, for the reason {@code SlayerActives.resolve} does the same: a
 * capstone blow outside the funnel would be the one attack in the tree armour
 * treated differently, Specialities' combat multiplier rides that funnel too,
 * and one swing per victim would let a per-swing passive fire once per body.
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
		final Entity target = player;
		Long readyAt = ArchetypeStore.INSTANCE.get(target, ModState.BASH_READY_AT);

		if (readyAt != null && now < readyAt) {
			return;
		}

		int slam = ProtectorNodes.rank(SubTree.PROTECTOR, owned, ProtectorNodes.Family.SLAM);
		int recovery = ProtectorNodes.rank(SubTree.PROTECTOR, owned, ProtectorNodes.Family.COOLDOWN);
		int wide = ProtectorNodes.rank(SubTree.PROTECTOR, owned, ProtectorNodes.Family.WIDE);
		boolean sweep = ProtectorNodes.rank(SubTree.PROTECTOR, owned,
				ProtectorNodes.Family.GROUND_SLAM) > 0;
		boolean dualShield = player.getMainHandItem().is(Items.SHIELD)
				&& player.getOffhandItem().is(Items.SHIELD);

		// The bash's shove is flat now. The node that used to buy more of it
		// (Concussive Blow) is Sure Footing, which is about moving while the
		// shield is up and never touches this ability at all.
		float damage = Tuning.BASH_DAMAGE * Tuning.slamMultiplier(slam);
		double shove = Tuning.BASH_KNOCKBACK;

		if (sweep) {
			// ATTACK_DAMAGE is the item, Strength and every tree bonus together
			// — the same reading the Slayer's capstone takes. With two shields
			// there is no weapon in it and the multiplier below is the trade.
			damage += (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE)
					* Tuning.SHIELD_SWEEP_WEAPON_SHARE;

			if (dualShield) {
				damage *= Tuning.SHIELD_SWEEP_DUAL_SHIELD_MULTIPLIER;
			}
		}

		double range = sweep ? Tuning.shieldSweepRange(wide) : Tuning.BASH_RANGE;
		double cone = sweep ? Tuning.SHIELD_SWEEP_CONE_DOT : Tuning.BASH_CONE_DOT;
		// The plain bash is bounded by its box alone, exactly as it always was.
		// The sweep needs a radial clamp on top, because a box inflated to cover
		// a half disc also covers its corners — 8.5 blocks out at the diagonal
		// of a 6-block arc, which is not what the description promises.
		double rangeSqr = sweep ? range * range : Double.MAX_VALUE;

		ServerLevel level = (ServerLevel) player.level();
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize();
		AABB reach = player.getBoundingBox()
				.expandTowards(look.scale(range))
				.inflate(sweep ? range : 0.6, 0.6, sweep ? range : 0.6);

		List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, reach,
				entity -> entity != player && entity.isAlive() && !entity.isSpectator()
						&& entity.distanceToSqr(player) <= rangeSqr
						&& facing(player, entity, flat, cone));

		LivingEntity primary = null;
		double best = Double.MAX_VALUE;

		for (LivingEntity candidate : targets) {
			double distance = candidate.distanceToSqr(player);

			if (distance < best) {
				best = distance;
				primary = candidate;
			}
		}

		// The swing happens whether or not it lands — whiffing is feedback too.
		player.swing(hand, true);
		// Slam ranks deepen the thunk; the sweep gets a whoosh over it.
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				//? if >=1.21.11 {
				SoundEvents.SHIELD_BLOCK.value(),
				//?} else {
				/*SoundEvents.SHIELD_BLOCK,
				*///?}
				SoundSource.PLAYERS, 1.0F, 0.65F - 0.05F * slam);

		if (sweep) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
					1.0F, dualShield ? 0.75F : 0.9F);
			// The stamp the client's pose reads. Written before the damage, so
			// a blow that kills its victim still animates.
			ArchetypeStore.INSTANCE.set(target, ModState.SHIELD_SWEEP_AT, now);
			arc(level, player, flat, range);
		}

		// One visible timer: swing floor + ability layer folded together (see
		// Tuning) — no grey sweep. Bashing also spends the melee attack timer,
		// so it replaces a sword swing instead of stacking on top of one.
		ArchetypeStore.INSTANCE.set(target, ModState.BASH_READY_AT,
				now + Tuning.bashCooldownTicks(slam, recovery));
		player.resetAttackStrengthTicker();

		if (primary == null) {
			if (!sweep) {
				// Whiffed: a sweep in front is the feedback that the swing
				// happened. The capstone already drew its whole arc.
				Vec3 front = player.position()
						.add(look.x * 1.5, player.getBbHeight() * 0.5, look.z * 1.5);
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, front.x, front.y, front.z,
						1, 0.0, 0.0, 0.0, 0.0);
			}

			return;
		}

		if (sweep) {
			resolveSweep(player, level, targets, damage, shove);
			return;
		}

		float secondaryFraction = Tuning.wideSecondaryFraction(wide);

		for (LivingEntity victim : targets) {
			boolean isPrimary = victim == primary;

			// Wide Swings decides what the extra targets take.
			if (!isPrimary && secondaryFraction <= 0.0F) {
				continue;
			}

			strike(player, level, victim, isPrimary ? damage : damage * secondaryFraction, shove);
		}
	}

	/**
	 * The capstone's blow: every body in the arc, at the full number, inside a
	 * single {@link MeleeSwing} window.
	 *
	 * <p>Opened once around ALL victims rather than once each — three bodies
	 * caught by one swing are one swing, and a per-victim window would let
	 * every per-swing passive in the mod fire once per body.
	 */
	private static void resolveSweep(final ServerPlayer player, final ServerLevel level,
			final List<LivingEntity> victims, final float damage, final double shove) {
		Entity previousSwing = MeleeSwing.begin(player);

		try {
			for (LivingEntity victim : victims) {
				strike(player, level, victim, damage, shove);
			}
		} finally {
			MeleeSwing.end(previousSwing);
		}
	}

	private static void strike(final ServerPlayer player, final ServerLevel level,
			final LivingEntity victim, final float damage, final double shove) {
		//? if >=1.21.2 {
		victim.hurtServer(level, player.damageSources().playerAttack(player), damage);
		//?} else {
		/*victim.hurt(player.damageSources().playerAttack(player), damage);
		*///?}

		// Placeholder knockback: plain push away from the player. TODO: switch
		// to LivingEntity.knockback once its 26.2 argument semantics are pinned
		// down, so knockback resistance applies.
		Vec3 away = victim.position().subtract(player.position());
		Vec3 flat = new Vec3(away.x, 0.0, away.z).normalize();
		victim.push(flat.x * shove, 0.15, flat.z * shove);

		level.sendParticles(ParticleTypes.SWEEP_ATTACK,
				victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(),
				1, 0.0, 0.0, 0.0, 0.0);
	}

	/**
	 * Draws the sweep's reach as a fan of crit points along the arc's rim.
	 *
	 * <p>The particles ARE the hitbox as far as a player reading the ability
	 * goes, so they are laid out from the same range the victim query used and
	 * across the same half-disc — a cloud that stopped short would be a lie
	 * about where the swing lands.
	 */
	private static void arc(final ServerLevel level, final ServerPlayer player,
			final Vec3 flat, final double range) {
		double y = player.getY() + player.getBbHeight() * 0.55;
		double facing = Math.atan2(flat.z, flat.x);

		for (int i = 0; i < Tuning.SHIELD_SWEEP_ARC_POINTS; i++) {
			// -90 to +90 degrees off the look vector, which is exactly the cone
			// SHIELD_SWEEP_CONE_DOT = 0 admits.
			double offset = Math.PI * (i / (double) (Tuning.SHIELD_SWEEP_ARC_POINTS - 1) - 0.5);
			double angle = facing + offset;

			level.sendParticles(ParticleTypes.SWEEP_ATTACK,
					player.getX() + Math.cos(angle) * range, y,
					player.getZ() + Math.sin(angle) * range,
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

	/** In front of the player: dot of the flat look vector with the flat
	 * direction to the target, against the ability's own cone. */
	private static boolean facing(final ServerPlayer player, final Entity target,
			final Vec3 flat, final double cone) {
		Vec3 toTarget = target.position().subtract(player.position());
		Vec3 level = new Vec3(toTarget.x, 0.0, toTarget.z);

		if (level.lengthSqr() < 1.0E-4) {
			return true;
		}

		return flat.dot(level.normalize()) > cone;
	}
}
