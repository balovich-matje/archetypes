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
	/** Base horizontal shove. Placeholder push physics, see ShieldBash. */
	public static final double BASH_KNOCKBACK = 0.5;

	/**
	 * The cooldown is still two layers conceptually, but shown as one number:
	 *
	 * <p><b>Swing</b> — 16 ticks, a cadence floor the bash can never beat.
	 * Bashing also resets the melee attack timer, so a bash always costs a sword
	 * swing — otherwise a fast bash weaves between sword hits as free extra DPS.
	 *
	 * <p><b>Ability</b> — 6 seconds on top. Quick Recovery removes a quarter of
	 * <em>this layer only</em> per rank (4 ranks), so even at −100% the swing
	 * cadence holds.
	 *
	 * <p>There is no grey sweep: both layers fold into the one countdown on the
	 * shield's slot, so the display is a single number rather than two effects
	 * disagreeing about when the bash comes back.
	 */
	public static final int BASH_SWING_TICKS = 16;
	public static final int BASH_ABILITY_TICKS = 120;

	/**
	 * Shield Slam: damage climbs a third per rank, and the ability layer grows
	 * to match. The penalty went away once and playtesting brought it back: on
	 * paper the bash never rivals a sword, but in hand a hard-hitting bash is
	 * damage dealt from safety, and safe damage that is also fast crowds the
	 * sword out. 7s base, 13s at full Slam, back to 7 with full Recovery.
	 */
	public static float slamMultiplier(final int rank) {
		return 1.0F + rank / 3.0F;
	}

	/**
	 * Total cooldown ticks: swing floor + the modified ability layer. Recovery
	 * shaves a fifth per rank (nerfed from a quarter), so even at full rank
	 * ~1.2s of the ability layer remains on top of the swing.
	 */
	public static int bashCooldownTicks(final int slamRank, final int recoveryRank) {
		float factor = 1.0F + slamRank / 3.0F - recoveryRank / 5.0F;
		return BASH_SWING_TICKS + Math.max(0, Math.round(BASH_ABILITY_TICKS * factor));
	}

	/** Braced: each blocked hit shaves this off the bash's remaining cooldown. */
	public static final int BRACED_REFUND_TICKS = 20;

	/** Taunt: bashing enrages every monster within this radius. */
	public static final double TAUNT_RADIUS = 8.0;

	/**
	 * Ground Slam: the bash hits everything within this ring — 2 blocks at
	 * baseline, and each rank of Wide Swings adds one (4 at full rank), so the
	 * cleave node feeds the capstone instead of being made redundant by it.
	 */
	public static double groundSlamRadius(final int wideRank) {
		return 2.0 + wideRank;
	}

	/** Reflection: a returned projectile keeps half its bite. */
	public static final double REFLECT_DAMAGE_FACTOR = 0.5;

	/** Rush: impulse per lunge block, and its own anti-exploit cooldown. */
	public static final double RUSH_IMPULSE_PER_BLOCK = 0.45;
	public static final int RUSH_COOLDOWN_TICKS = 60;

	/** Shield Rush lunge distance in blocks: 2, 4, 6 by rank. */
	public static int rushBlocks(final int rank) {
		return 2 * rank;
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

	/** Reinforced Straps = Unbreaking I: half of durability hits are ignored. */
	public static final float STRAPS_SKIP_CHANCE = 0.5F;

	private Tuning() {
	}
}
