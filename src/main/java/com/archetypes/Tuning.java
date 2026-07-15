package com.archetypes;

/**
 * All balance constants, in one place — same convention as Specialities.
 *
 * <p>Bash baseline per the decided design: ~0.55x a diamond sword's DPS
 * (11.2), so it is never the better damage button; its edge is being usable
 * with the shield still up. 5.0 damage every 16 ticks = 6.25 DPS = 0.56x.
 */
public final class Tuning {
	/** Reach of the bash, blocks. */
	public static final double BASH_RANGE = 3.0;
	public static final float BASH_DAMAGE = 5.0F;
	public static final int BASH_COOLDOWN_TICKS = 16;
	/** Base horizontal shove. Placeholder push physics, see ShieldBash. */
	public static final double BASH_KNOCKBACK = 0.5;

	/**
	 * Shield Slam: damage and cooldown climb together by a third per rank, so
	 * DPS is unchanged — sustained damage traded for burst, not power.
	 */
	public static float slamMultiplier(final int rank) {
		return 1.0F + rank / 3.0F;
	}

	/**
	 * Cooldown reduction −20/35/50%. Deliberately short of 100%: DPS scales as
	 * 1/(1−r), so full reduction is division by zero and anything close is a
	 * spam button. At −50% over the 0.55x baseline the bash tops out ~1.1x.
	 */
	public static float cooldownMultiplier(final int rank) {
		return switch (rank) {
			case 1 -> 0.80F;
			case 2 -> 0.65F;
			case 3 -> 0.50F;
			default -> 1.0F;
		};
	}

	/** Knockback trade: each rank sheds 12% damage for ~KB-enchant-level shove. */
	public static final double KNOCKBACK_PER_RANK = 0.67;
	public static final float KNOCKBACK_DAMAGE_PENALTY = 0.12F;

	/** Wide Swings: secondary targets take 50% then 100% of the bash. */
	public static float wideSecondaryFraction(final int rank) {
		return switch (rank) {
			case 1 -> 0.5F;
			case 2 -> 1.0F;
			default -> 0.0F;
		};
	}

	/**
	 * Iron Spikes = Thorns V/X/XV by rank, vanilla's own numbers: proc chance
	 * 15% per enchant level (certain from rank 2), damage 1-4 plus (level - 10)
	 * once the level exceeds 10 — so rank 3 lands 6-9.
	 */
	public static int spikesThornsLevel(final int rank) {
		return rank * 5;
	}

	/** Vanilla thorns costs the item 2 durability per proc; spikes match it. */
	public static final int SPIKES_DURABILITY_COST = 2;

	/** Reinforced Straps = Unbreaking I: half of durability hits are ignored. */
	public static final float STRAPS_SKIP_CHANCE = 0.5F;

	private Tuning() {
	}
}
