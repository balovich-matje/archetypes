package com.archetypes;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Vanilla's armour curve, and its inverse.
 *
 * <h2>Why the inverse</h2>
 * A node that says "this blow ignores 60% of the target's armour" has to be
 * resolved somewhere, and the only two honest places are inside
 * {@code CombatRules.getDamageAfterAbsorb} — which needs a real
 * {@code armor_effectiveness} enchantment on the weapon — or in front of it,
 * by handing vanilla a bigger number chosen so that vanilla's own arithmetic
 * lands where the reduced armour would have. This class is the second route,
 * and it is exact rather than approximate: {@link #afterArmour} is 26.2's
 * {@code CombatRules.getDamageAfterAbsorb} transcribed, and
 * {@link #rawForAfterArmour} solves that same expression for its input.
 *
 * <h2>What it replaces</h2>
 * The mod used to model armour as {@code min(0.8, ARMOR * 0.04)} — a flat 4%
 * a point, capped at 80%. That is only vanilla's curve for a hit small enough
 * that the {@code - damage/toughness} shred term does nothing, and it saturates
 * at 20 armour, i.e. plain diamond. Compensating against it therefore paid back
 * mitigation the game was never going to apply, and paid it back hardest on
 * exactly the hits vanilla already lets through: against a 100-damage blow a
 * full-netherite target keeps only 4 of its 20 armour points, so it is stopping
 * 16%, not 80%, and a node compensating for 80% is a x5.0 damage multiplier
 * wearing an armour-penetration label.
 *
 * <h2>The curve</h2>
 * <pre>
 *   t          = 2 + toughness / 4
 *   realArmour = clamp(armour - damage / t, armour * 0.2, 20)
 *   landed     = damage * (1 - realArmour / 25)
 * </pre>
 * {@code landed} is strictly increasing in {@code damage}, so the inverse is
 * single-valued. It is piecewise: realArmour pins at 20 for small hits when the
 * target carries more than 20 points, is linear in {@code damage} through the
 * middle, and pins at a fifth of the total once the shred bottoms out. The
 * middle branch is a quadratic in {@code damage} and is solved in closed form;
 * the two pinned branches are straight divisions. Which branch applies is
 * decided by testing each candidate against the range that produced it, which
 * works precisely because the function is monotone.
 *
 * <p>Server-side only in practice, but nothing here touches the world — it is
 * arithmetic over two attribute reads.
 */
public final class ArmourMath {
	/** {@code CombatRules.MAX_ARMOR}. */
	private static final float MAX_ARMOUR = 20.0F;

	/** {@code CombatRules.ARMOR_PROTECTION_DIVIDER}. */
	private static final float DIVIDER = 25.0F;

	/** {@code CombatRules.BASE_ARMOR_TOUGHNESS}. */
	private static final float BASE_TOUGHNESS = 2.0F;

	/** {@code CombatRules.MIN_ARMOR_RATIO}. */
	private static final float MIN_RATIO = 0.2F;

	private ArmourMath() {
	}

	/**
	 * The victim's armour points, as {@code getArmorValue} would report them —
	 * and it reports them FLOORED ({@code LivingEntity.getArmorValue} is
	 * {@code Mth.floor(getAttributeValue(ARMOR))}), which is the number vanilla
	 * hands {@code CombatRules.getDamageAfterAbsorb}.
	 *
	 * <p>The floor was missing and is worth nothing on any vanilla mob or armour
	 * set, all of which carry integral armour. It matters for a modded or
	 * equipment-bearing victim with a fractional attribute: this class's whole
	 * job is to predict vanilla's own arithmetic, and predicting it from an
	 * input vanilla does not use makes the trace's mismatch alarm fire on a
	 * stage that is arithmetically fine.
	 */
	public static float armour(final LivingEntity victim) {
		return Mth.floor(victim.getAttributeValue(Attributes.ARMOR));
	}

	/** The divisor the shred term uses: {@code 2 + toughness / 4}. */
	public static float toughnessTerm(final LivingEntity victim) {
		return BASE_TOUGHNESS
				+ (float) victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS) / 4.0F;
	}

	/**
	 * {@code CombatRules.getDamageAfterAbsorb}, minus the
	 * {@code armor_effectiveness} enchantment hook — Breach and any other real
	 * modifier compose on top of this inside vanilla and must not be
	 * double-counted here.
	 */
	public static float afterArmour(final float damage, final float armour, final float t) {
		if (armour <= 0.0F || damage <= 0.0F) {
			return damage;
		}

		float realArmour = Mth.clamp(armour - damage / t, armour * MIN_RATIO, MAX_ARMOUR);
		return damage * (1.0F - realArmour / DIVIDER);
	}

	/**
	 * The raw damage that {@link #afterArmour} turns into {@code target}. The
	 * inverse of the expression above, branch by branch.
	 */
	public static float rawForAfterArmour(final float target, final float armour, final float t) {
		if (armour <= 0.0F || target <= 0.0F) {
			return target;
		}

		// Branch 1: realArmour pinned at the 20-point ceiling. Only reachable
		// when the target wears more than 20, and only under this much damage.
		float ceilingUntil = Math.max(0.0F, (armour - MAX_ARMOUR) * t);

		if (ceilingUntil > 0.0F) {
			float pinned = target / (1.0F - MAX_ARMOUR / DIVIDER);

			if (pinned <= ceilingUntil) {
				return pinned;
			}
		}

		// Branch 2: the shred is live, so landed = d - d*(armour - d/t)/25,
		// i.e. d^2/(25t) + d*(1 - armour/25) - target = 0.
		float k = 1.0F - armour / DIVIDER;
		float root = (float) Math.sqrt(k * k + 4.0F * target / (DIVIDER * t));
		float shredded = 0.5F * DIVIDER * t * (root - k);
		float floorFrom = (1.0F - MIN_RATIO) * armour * t;

		if (shredded <= floorFrom) {
			return shredded;
		}

		// Branch 3: realArmour pinned at a fifth of the total; a flat ratio.
		return target / (1.0F - MIN_RATIO * armour / DIVIDER);
	}

	/**
	 * The multiplier that makes a blow land as though the victim wore
	 * {@code 1 - ignore} of their armour.
	 *
	 * <p>{@code damage} is the damage the blow would carry WITHOUT this node —
	 * every other shaper's work, and nothing of this one's. That choice is the
	 * whole reason the number is well defined: the fraction of armour vanilla
	 * will actually apply depends on how big the hit is, so feeding the
	 * post-compensation damage back in would be circular, and feeding the raw
	 * {@code hurtServer} argument in would price the armour of a hit an order of
	 * magnitude smaller than the one being dealt. Pre-node damage is the one
	 * value that is single-valued, causally prior, and the same on both sides of
	 * the trace mirror.
	 *
	 * <p>The residual is small and points the right way. Because the returned
	 * multiplier raises the damage, vanilla shreds the victim's armour slightly
	 * further than {@code damage} predicted, so the blow lands a little above
	 * the reduced-armour ideal rather than below it — and the gap closes to
	 * nothing exactly where it would matter most, on a hit already big enough to
	 * bottom the armour out.
	 */
	public static float ignoreArmourFactor(final LivingEntity victim, final float damage,
			final float ignore) {
		float armour = armour(victim);

		if (armour <= 0.0F || ignore <= 0.0F || damage <= 0.0F) {
			return 1.0F;
		}

		float t = toughnessTerm(victim);
		float reduced = armour * (1.0F - Math.min(1.0F, ignore));
		float ideal = afterArmour(damage, reduced, t);
		return rawForAfterArmour(ideal, armour, t) / damage;
	}

	/**
	 * The fraction of {@code damage} this victim's armour eats at that
	 * magnitude — for the trace, so a reader can see the real curve next to the
	 * ignored share instead of a constant.
	 */
	public static float mitigation(final LivingEntity victim, final float damage,
			final float armourPoints) {
		if (armourPoints <= 0.0F || damage <= 0.0F) {
			return 0.0F;
		}

		return 1.0F - afterArmour(damage, armourPoints, toughnessTerm(victim)) / damage;
	}
}
