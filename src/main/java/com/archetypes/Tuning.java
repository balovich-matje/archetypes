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
	/**
	 * The bash's cone, as the dot of the look vector with the flat direction to
	 * a target: 0.5 is 60 degrees off centre, so a 120-degree arc in front.
	 * Shield Sweep replaces this number rather than scaling it — see
	 * {@link #SHIELD_SWEEP_CONE_DOT}.
	 */
	public static final double BASH_CONE_DOT = 0.5;
	/** Base horizontal shove. Placeholder push physics, see ShieldBash. */
	public static final double BASH_KNOCKBACK = 0.5;

	/**
	 * The cooldown is still two layers conceptually, but shown as one number:
	 *
	 * <p><b>Swing</b> — 16 ticks, a cadence floor the bash can never beat.
	 * Bashing also resets the melee attack timer, so a bash always costs a sword
	 * swing — otherwise a fast bash weaves between sword hits as free extra DPS.
	 *
	 * <p><b>Ability</b> — 6 seconds on top. Quick Recovery removes a share of
	 * <em>this layer only</em> per rank (3 ranks), so even at −100% the swing
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
	 * Quick Recovery's cut of the ability layer, per rank.
	 *
	 * <p>The chain was four nodes shaving a fifth each; it is three nodes now,
	 * and this is 4/15 so that a full chain still lands on exactly the same
	 * −80%. The node kept its ceiling and gave up a point — that point paid for
	 * the node that took the cell the fourth rank used to hold.
	 * Deliberately not rounded to 0.27: the endpoint has to be the old one to
	 * the tick, or every published bash cadence shifts.
	 */
	public static final float RECOVERY_PER_RANK = 4.0F / 15.0F;

	/**
	 * Total cooldown ticks: swing floor + the modified ability layer. Even at
	 * full Recovery ~1.2s of the ability layer remains on top of the swing.
	 */
	public static int bashCooldownTicks(final int slamRank, final int recoveryRank) {
		float factor = 1.0F + slamRank / 3.0F - recoveryRank * RECOVERY_PER_RANK;
		return BASH_SWING_TICKS + Math.max(0, Math.round(BASH_ABILITY_TICKS * factor));
	}

	/** Braced: each blocked hit shaves this off the bash's remaining cooldown. */
	public static final int BRACED_REFUND_TICKS = 20;

	/**
	 * Reinforced Straps: extra Unbreaking levels on a shield this player holds,
	 * and the ceiling on the TOTAL that produces.
	 *
	 * <p>The cap is on the sum, not on the node: a shield already at Unbreaking
	 * III reads as IV and stops. What one level is worth depends entirely on
	 * where it lands, because vanilla's chance to ignore a point of damage is
	 * {@code level / (level + 1)} — 0→1 halves the wear, 3→4 takes another 20%
	 * off what is left. So the node is at its best on the plain shield somebody
	 * is actually blocking a mob pack with, and nearly nothing on a fully
	 * enchanted one, which is the right shape for a single point spent on a
	 * defensive tree's centre column.
	 */
	public static final int REINFORCED_STRAPS_LEVELS = 1;
	public static final int REINFORCED_STRAPS_LEVEL_CAP = 4;

	/** Taunt: holding the shield up enrages every monster within this radius. */
	public static final double TAUNT_RADIUS = 8.0;

	/**
	 * How often the taunt sweep re-asserts itself, in ticks.
	 *
	 * <p>Half a second, not every tick: a forced target only has to be forced
	 * again when something has un-forced it, and the sweep is an AABB query per
	 * blocking Protector. Mobs already on the taunter are skipped inside the
	 * query, so the common case — a pack that is already coming — costs the
	 * query and nothing else.
	 */
	public static final int TAUNT_PERIOD_TICKS = 10;

	// --- Shield Sweep (the GROUND_SLAM capstone) ---

	/**
	 * Shield Sweep's cone, in the same units as {@link #BASH_CONE_DOT}: zero is
	 * 90 degrees off centre, i.e. the whole half-disc in front of the player.
	 *
	 * <p>A swing that starts at the block position and finishes out to the side
	 * covers everything the player is facing, and the capstone's first promise
	 * is exactly "a wider cone". Nothing BEHIND the swing is touched — a full
	 * circle is what the node used to be, back when it was a shockwave, and
	 * losing that was the point of the rework.
	 */
	public static final double SHIELD_SWEEP_CONE_DOT = 0.0;

	/**
	 * Shield Sweep's reach in front of the caster: the bash's 3 plus this, plus
	 * one more per Wide Swings rank.
	 *
	 * <p>4 / 5 / 6 blocks — the same three numbers the capstone had before the
	 * rework, so a Wide Swings holder's reach is unchanged and only what fills
	 * the arc moved. Wide keeps feeding the capstone as reach rather than as
	 * the fraction secondary targets take, because a sweep does not have
	 * secondary targets: everything the arc covers is hit by the same swing.
	 */
	public static final double SHIELD_SWEEP_REACH_BONUS = 1.0;

	public static double shieldSweepRange(final int wideRank) {
		return BASH_RANGE + SHIELD_SWEEP_REACH_BONUS + wideRank;
	}

	/**
	 * How much of the held weapon Shield Sweep adds to the bash, as a share of
	 * {@code ATTACK_DAMAGE}.
	 *
	 * <p>1.0 is the whole of it — the item, Strength and every tree bonus
	 * together, which is the same number the Slayer's own capstone reads. That
	 * is the node's pitch rather than a multiplier pulled to taste: the caster
	 * gave up Bulwark for it, and the bash was deliberately never worth more
	 * than a sword swing on its own. Everything past this is the funnel's
	 * business — armour, Blade Master and Specialities' combat multiplier all
	 * apply after it, because the blow goes through MeleeSwing like any swing.
	 */
	public static final float SHIELD_SWEEP_WEAPON_SHARE = 1.0F;

	/**
	 * A shield in BOTH hands: the whole sweep, times this.
	 *
	 * <p>It reads enormous and is not, which is the trade the node is made of.
	 * A second shield costs the weapon term ({@code ATTACK_DAMAGE} with no
	 * weapon is a fist's 1.0), the off-hand slot a Protector otherwise fills
	 * with the shield they block with, and every non-bash attack they own — a
	 * dual-shield Protector has no melee outside this button. x4 on
	 * (bash + fist) is roughly what the sweep is worth with a good sword in
	 * hand, so the two loadouts meet rather than one replacing the other.
	 */
	public static final float SHIELD_SWEEP_DUAL_SHIELD_MULTIPLIER = 4.0F;

	/** Arc-particle density: points drawn across the sweep's cone, so the
	 * player can read how far it actually reached. */
	public static final int SHIELD_SWEEP_ARC_POINTS = 9;

	/**
	 * Reflection: how much of its bite a returned arrow keeps, by rank. Rank 2
	 * sends the shot back whole — a skeleton that shoots a Protector at rank 2
	 * is shooting itself, which is the point of a two-point node on the top of
	 * the rim. Rank 1 is the old single-rank value unchanged, so nobody's
	 * existing Reflection got quietly better or worse the day it grew a rank.
	 */
	public static double reflectDamageFactor(final int rank) {
		return rank >= 2 ? 1.0 : REFLECT_DAMAGE_FACTOR;
	}

	/** A parried spell's arrow keeps half its bite — Spell Reflect's own number,
	 * kept apart from the Protector node's rank table on purpose: the Colossus
	 * Slayer's parry has nothing to do with how far up the shield rim someone
	 * bought. */
	public static final double REFLECT_DAMAGE_FACTOR = 0.5;
	/** Floor on the return flight — a mid-drawn-bow launch, enough to carry
	 * the shot back to a skeleton at normal firing range. */
	public static final double REFLECT_RETURN_SPEED = 1.6;

	/** Rush: impulse per lunge block, and its own anti-exploit cooldown. */
	public static final double RUSH_IMPULSE_PER_BLOCK = 0.45;
	public static final int RUSH_COOLDOWN_TICKS = 60;

	/** Shield Rush lunge distance in blocks: 2, 4, 6 by rank. */
	public static int rushBlocks(final int rank) {
		return 2 * rank;
	}

	/**
	 * Sure Footing: the share of the blocking movement penalty handed back, by
	 * rank. Vanilla blocks at a fifth of walking speed
	 * ({@code UseEffects.DEFAULT.speedMultiplier()}), so rank 3 is the whole
	 * penalty and a full-rank Protector advances behind a raised shield at
	 * walking pace. The sprint gate is not part of this and stays.
	 */
	public static float sureFootingRelief(final int rank) {
		return switch (Math.max(Math.min(rank, 3), 0)) {
			case 1 -> 0.33F;
			case 2 -> 0.66F;
			case 3 -> 1.0F;
			default -> 0.0F;
		};
	}

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

	// --- Slayer ---

	/** Hamstring: Slowness (rank-1 amplifier) for this long on melee hits. */
	public static final int SLOWNESS_TICKS = 60;

	/** Taste of Blood: health restored per rank on a melee kill (1.0 = half a heart). */
	public static final float TASTE_OF_BLOOD_HEAL_PER_RANK = 1.0F;

	/** Lunge: a full block per rank along the look vector, 2s between hops. */
	public static final double LUNGE_BLOCKS_PER_RANK = 1.0;
	public static final int LUNGE_COOLDOWN_TICKS = 40;

	/** Blade Dance: chance for a manual sword strike to lash out at another
	 * nearby foe — any direction, the back included. */
	/** Blade Dance's lash chance PER RANK — 25% then 50%. Two ranks since the
	 * 20260720 sketch folded Flurry's cell into it. */
	public static final float BLADE_DANCE_CHANCE = 0.25F;
	public static final double BLADE_DANCE_RANGE = 3.5;

	/** Immovable: knockback resistance per rank while a greatsword is held. */
	public static final double KBRES_PER_RANK = 0.3;

	/** Rend: damage per second per rank, for three seconds. */
	public static final int BLEED_DURATION_TICKS = 60;

	/** Heavy Blows: damage up and swing speed down by this much per rank. */
	public static final float HEAVY_PER_RANK = 0.10F;

	/** First Blood: bonus vs full-health targets. Single rank — the opener. */
	public static final float FIRSTBLOOD_PER_RANK = 0.40F;

	/** Executioner: greatsword hits finish targets below this health fraction. */
	public static final float EXECUTE_THRESHOLD = 0.15F;

	/** Bloodlust: Speed I for this long after any melee kill. */
	public static final int BLOODLUST_TICKS = 60;

	/** Decimate: a telegraphed execution, 30s cooldown, a wide tilted arc in
	 * front. Only instant-break blocks (torches, grass, fire...) are swept away
	 * — anything sturdier survives, so a swing in your own base clears clutter
	 * without eating the walls.
	 *
	 * <p>The damage is the attacker's, not the victim's: {@code ATTACK_DAMAGE x
	 * MULTIPLIER} on an ordinary {@code player_attack}, dealt inside a
	 * {@link MeleeSwing} so the greatsword's own chain rides it — Heavy Blows,
	 * First Blood, the Executioner clamp, and Blade Master's armour penetration.
	 * That last one is why the wrapper is back: the author's answer to heavy
	 * armour is now a NODE rather than a damage type, and a capstone that did
	 * not ride the node would be the one greatsword blow a full suit still
	 * stopped dead.
	 *
	 * <p>The multiplier itself. 2.0 was the number before the percent-of-max-HP
	 * detour, and it was too small to be a capstone: 24 raw x 1.30 x 1.40 =
	 * 43.68 on a 30s cooldown is 1.6 normal swings and about 5% of the Slayer's
	 * single-target output. 3.0 is chosen against three fences rather than one
	 * DPS target, because a 30s AoE button is priced as burst, not as DPS:
	 * <ul>
	 * <li>36 raw x 1.30 Heavy Blows x 1.40 First Blood = 65.52 on an unarmoured
	 *     40 HP player. That is a decisive one-shot with enough headroom that a
	 *     golden apple, a Stalwart tick or Magic Armor 1's ten temporary hearts
	 *     cannot repeal it — the guarantee the percent-of-max-HP version was
	 *     reaching for, kept, without the true-damage type.</li>
	 * <li>Against a full Colossus (netherite + Protection IV + Ironclad) with
	 *     Blade Master 2 it lands 34.5 of 40: "almost a oneshot that can be
	 *     followed up with a single swing", now landing on the ARMOURED case
	 *     instead of reading the same as the naked one. 4.0 would have killed
	 *     him outright, which is the outcome this revert is stepping back from.</li>
	 * <li>PvE is held flat rather than raised: 65.52 is 13% of a Warden, against
	 *     the 14% the reverted version's 70-damage ceiling allowed, so
	 *     nothing about the revert is a boss-damage buff.</li>
	 * </ul> */
	public static final float DECIMATE_DAMAGE_MULTIPLIER = 3.0F;
	/** The wind-up, in ticks, between the cast and the blow landing. One full
	 * second: the entire counterplay budget, and the only reason "you don't
	 * want to be near it when it's cast" describes a decision rather than a
	 * dice roll. The parry's riposte skips it — a parry is already a reaction
	 * and does not owe a second one. */
	public static final int DECIMATE_WINDUP_TICKS = 20;
	/** Slowness amplifier worn by the CASTER for the wind-up. 1 = Slowness II.
	 * Over 20 ticks Slowness I costs a sprinting caster about 0.8 blocks of
	 * pursuit and Slowness II about 1.7; the arc is 3.5 deep, so II is the one
	 * that makes "stepped out of the arc" a thing a fleeing target can actually
	 * do against a caster who commits. */
	public static final int DECIMATE_WINDUP_SLOWNESS_AMPLIFIER = 1;
	/** The parry riposte's own clock, separate from {@link
	 * #DECIMATE_COOLDOWN_TICKS} so the author's rule still holds — "automatically
	 * cast Decimates will not incur a cooldown, and can happen while the skill
	 * is already on cooldown". What it stops is the other thing: a landed parry
	 * refunds itself (ColossusSlayer.pay), so without a clock of its own the
	 * riposte is an unbounded true-damage nuke whose rate is set by how often
	 * you are attacked. 10s caps it at 6 free casts a minute. */
	public static final int DECIMATE_FREE_COOLDOWN_TICKS = 200;
	public static final int DECIMATE_COOLDOWN_TICKS = 600;
	public static final double DECIMATE_RANGE = 3.5;
	public static final int DECIMATE_MAX_BLOCKS = 48;

	// --- Crusher ---

	/** Clinch: bare fists give and take this much less knockback per rank.
	 * Received: KNOCKBACK_RESISTANCE is a 0..1 RangedAttribute, so stacking
	 * with armor can never push knockback negative. */
	public static final float CLINCH_KNOCKBACK_REDUCTION_PER_RANK = 0.5F;

	/** Sunder: virtual Breach levels (rank for mace, doubled for fists). Each
	 * level claws back 15% of what the victim's armor absorbed — approximated
	 * as bonus damage from the armor's ~4%/point reduction, additive with the
	 * real Breach enchantment. */
	public static final float SUNDER_PER_LEVEL = 0.15F;

	/** Bare-Knuckle, the day-one handle: fists +1 heart per rank (the 1.0
	 * fist ends at 9.0 — past an iron sword, the tree's whole opening
	 * fantasy), the mace +0.5 hearts per rank. */
	public static final float BARE_KNUCKLE_FIST_PER_RANK = 2.0F;
	public static final float BARE_KNUCKLE_MACE_PER_RANK = 1.0F;

	/** Iron Skin: armor and toughness per rank while the hands are bare. */
	public static final float IRON_SKIN_ARMOR_PER_RANK = 1.0F;
	public static final float IRON_SKIN_TOUGHNESS_PER_RANK = 1.0F;

	/** Haymaker: one enormous punch — multiplied attack damage, a stun, and
	 * Knockback II's worth of send-off. */
	public static final float HAYMAKER_DAMAGE_MULTIPLIER = 4.0F;
	public static final double HAYMAKER_KNOCKBACK = 1.3;
	public static final int HAYMAKER_STUN_TICKS = 30;
	public static final int HAYMAKER_STUN_AMPLIFIER = 5;
	public static final int HAYMAKER_COOLDOWN_TICKS = 400;
	public static final double HAYMAKER_RANGE = 3.0;

	/** Meteor: Density by another name — bonus smash damage per fallen block
	 * per rank, on mace hits landed while falling. */
	public static final float METEOR_PER_BLOCK_PER_RANK = 0.5F;
	public static final float SMASH_MIN_FALL = 1.5F;

	/** Shockwave: a falling mace hit splashes its damage to everything within
	 * 2 blocks per rank of the victim. */
	public static final int SHOCKWAVE_RADIUS_PER_RANK = 2;

	/** Earth Shatterer: a Quake that meets no flesh refunds a third of its
	 * cooldown per rank and shatters the ground instead — anything up to
	 * stone hardness, 2 blocks of radius per rank, one mace durability per
	 * block broken. */
	/** An exact third: rank 3 refunds the whole cooldown, not 99% of it. */
	public static final float EARTH_SHATTER_REFUND_PER_RANK = 1.0F / 3.0F;
	public static final int EARTH_SHATTER_RADIUS_PER_RANK = 2;
	public static final float EARTH_SHATTER_MAX_HARDNESS = 1.5F;

	/** Quake: charge for 1.5s (knockback-immune, mace rising), then slam —
	 * multiplied attack damage in the radius, hostiles launched skyward. */
	public static final int QUAKE_CHARGE_TICKS = 30;
	public static final float QUAKE_DAMAGE_MULTIPLIER = 1.5F;
	/** Density feeds the slam: bonus damage per enchant level, plus per
	 * Meteor rank — Density V with full Meteor one-shots a fresh zombie. */
	public static final float QUAKE_DENSITY_BONUS = 1.5F;
	public static final float QUAKE_METEOR_BONUS = 2.0F;
	public static final double QUAKE_RADIUS = 3.0;
	public static final double QUAKE_LAUNCH = 0.95;
	public static final int QUAKE_COOLDOWN_TICKS = 600;

	/** Battle Trance: absorption per landed hit (doubled for fists), capped
	 * per rank, draining once the fight goes quiet. */
	public static final float TRANCE_ABSORPTION_PER_HIT = 1.0F;
	public static final float TRANCE_CAP_PER_RANK = 2.0F;
	public static final int TRANCE_DECAY_DELAY_TICKS = 100;

	/** Relentless (tip): both capstone cooldowns drop by 15 seconds. */
	public static final int RELENTLESS_REDUCTION_TICKS = 300;

	/** Bladestorm: six half-damage volleys over three seconds, 45s cooldown. */
	public static final int BLADESTORM_COOLDOWN_TICKS = 900;
	public static final int BLADESTORM_CHANNEL_TICKS = 60;
	public static final int BLADESTORM_VOLLEY_PERIOD = 10;
	public static final float BLADESTORM_DAMAGE_FACTOR = 0.5F;
	public static final double BLADESTORM_RADIUS = 3.0;

	private Tuning() {
	}
	// --- Agility: True Shot ---
	/** Cooldown starts on arming, not on the shot. */
	public static final int TRUE_SHOT_COOLDOWN_TICKS = 400;
	public static final float TRUE_SHOT_MULTIPLIER = 2.0F;
	/** The Seeker Arrow capstone trades the bonus damage away for homing —
	 * and casts twice as often; with Focus it can chain indefinitely. */
	public static final float TRUE_SHOT_HOMING_MULTIPLIER = 1.0F;
	public static final int TRUE_SHOT_SEEKER_COOLDOWN_TICKS = 200;
	/** How far the Seeker Arrow looks for its own target at launch. */
	public static final double SEEKER_AIM_RANGE = 24.0;
	public static final float TRUE_SHOT_SNAP_MULTIPLIER = 4.0F;
	public static final float TRUE_SHOT_SNAP_SPEED = 3.0F;
	/** Gravity-free arrows fly forever; these quietly stop existing here. */
	public static final double TRUE_SHOT_RANGE_BLOCKS = 64.0;
	public static final double TRUE_SHOT_HOMING_RADIUS = 16.0;

	// --- Marksman passives ---
	public static final float CONSERVATION_PER_RANK = 0.125F;
	public static final int PINNING_TICKS = 60;
	/** Damage-neutral: velocity up by this, base damage down to match. */
	public static final float SWIFT_FLIGHT_PER_RANK = 0.5F;
	public static final double ACROBATICS_BLOCKS_PER_RANK = 2.0;
	public static final int DISENGAGE_COOLDOWN_TICKS = 160;
	/** A third of the draw slowdown back per rank; rank 3 walks free. */
	public static final float NIMBLE_DRAW_PER_RANK = 1.0F / 3.0F;
	public static final float RAPID_RELOAD_PER_RANK = 0.25F;
	public static final double COMBUSTION_RADIUS = 3.0;
	public static final int FOCUS_REFUND_TICKS = 200;
	/** Piercing Tips: how many armor points ranged shots pretend away. */
	public static final float PIERCING_TIPS_ARMOR = 2.0F;
	/**
	 * The damage fraction one armour point eats, used to hand that fraction
	 * back so a shot lands as if the armour were not there — Piercing Tips
	 * pretends away {@link #PIERCING_TIPS_ARMOR} points, Punch Through
	 * {@link #PUNCH_THROUGH_ARMOUR_IGNORE} of the target's. An approximation of
	 * vanilla's own curve
	 * ({@code CombatRules.getDamageAfterAbsorb}), which is exactly 4% per point
	 * only at zero armour toughness; against toughness the compensation
	 * slightly overshoots, and {@link #DEADEYE_MAX_MULTIPLIER} is the fence
	 * around the product.
	 */
	public static final float ARMOUR_POINT_DAMAGE_FRACTION = 0.04F;

	// --- Agility: Invisibility ---
	public static final int INVIS_TICKS = 160;
	public static final int INVIS_COOLDOWN_TICKS = 600;
	public static final int CHEAT_DEATH_IMMUNE_TICKS = 40;
	/** Shared by the invis active and the cheat-death passive after it fires. */
	public static final int CHEAT_DEATH_COOLDOWN_TICKS = 3600;

	// --- Shadow passives ---
	/** Swift Shadow: vanilla sneaking moves at 0.3x; each rank refunds half
	 * the penalty (0.65x, then full speed). */
	public static final float SWIFT_SHADOW_SNEAK_REFUND_PER_RANK = 0.35F;
	public static final float DARK_MENDING_HEAL = 2.0F;
	public static final float DIM_PRESENCE_PER_RANK = 0.20F;
	/**
	 * First Strike: a summand in the ambush box, per rank, while the player is
	 * invisible — NOT a Strength grant any more.
	 *
	 * <h2>Why it stopped being Strength</h2>
	 * {@code MobEffects.STRENGTH} is +3.0 ADD_VALUE per amplifier level, so rank
	 * 2 was +6.0 on a netherite dagger whose whole ATTACK_DAMAGE is 4.8. It sat
	 * BELOW the box on the attribute, so the box multiplied it: at the ambush
	 * box's ceiling a flat +6 was worth +6 x 7.55 = +45 raw, and it ate ~44% of
	 * the PvE damage budget before a single Nemesis node fired. A summand costs
	 * its own face value, which is the entire argument the box exists to make
	 * (see {@link #COUP_DE_GRACE_PLAYER_BONUS}).
	 *
	 * <p>0.25/rank puts it level with {@link #HEADHUNTER_PER_RANK}, which is
	 * what makes the node description writable as a number.
	 */
	public static final float FIRST_STRIKE_PER_RANK = 0.25F;
	/**
	 * Bloodrush: a summand in the ambush box, per rank, for
	 * {@link #BLOODRUSH_TICKS} after a kill made from inside the dark. It was a
	 * Strength I/II grant and went the same way First Strike did, for the same
	 * reason and by the same arithmetic — it is the identical construct on the
	 * identical arc, and leaving it as Strength would have meant the box's
	 * declared ceilings were not ceilings at all: with Strength II live the
	 * amount entering the funnel is 13.8 rather than 7.8, and the crouched
	 * opener reaches 320 raw, which ONE-SHOTS the 300 HP Wither the whole PvE
	 * window is drawn to exclude.
	 *
	 * <p>Smaller per rank than {@link #FIRST_STRIKE_PER_RANK} on purpose, and
	 * the reason is a ceiling rather than a judgement about the node: Bloodrush
	 * is strictly additive ON TOP of First Strike (both want invisibility;
	 * Bloodrush merely also wants a fresh kill), so the number the PvE ceiling
	 * has to hold is the pair. +0.20 is what the Ender Dragon's head leaves —
	 * see the window arithmetic on {@link #COUP_DE_GRACE_PLAYER_BONUS}.
	 */
	public static final float BLOODRUSH_PER_RANK = 0.10F;
	/** How long Bloodrush's window stays open after a kill from the dark. */
	public static final int BLOODRUSH_TICKS = 80;
	public static final float REAPER_HEAL = 4.0F;
	public static final float STILLNESS_DURATION_PER_RANK = 0.5F;
	/** Umbral Sight's highlight reach, per rank: 8 blocks then 16. */
	public static final double UMBRAL_SIGHT_RADIUS = 8.0;
	/** Night Stalker's effects, re-asserted each tick and left to lapse when
	 * the hunt ends — short so teardown reads as immediate, and expiring
	 * (never removeEffect) lets vanilla restore any beacon/potion buff ours
	 * was layered over instead of discarding it. */
	public static final int NIGHT_STALKER_TICKS = 5;

	// --- Agility: Shadow Step ---
	public static final double SHADOW_STEP_RANGE = 16.0;
	public static final int SHADOW_STEP_COOLDOWN_TICKS = 300;
	public static final int SHADOW_STEP_FLURRY_COOLDOWN_TICKS = 600;
	/**
	 * The flurry capstone: one strike, several daggers' worth. Additive into
	 * the ambush box (see {@link #COUP_DE_GRACE_PLAYER_BONUS}), not a
	 * multiplier of its own — it is the box's largest single term, which is
	 * the relative standing the old x3.0 had among the step multipliers.
	 *
	 * <p>Was 4.5F. It is the single largest summand and therefore where the
	 * bulk of the retune landed: together with First Strike's Strength grant
	 * leaving the attribute, this is the /3.2 the PvE opener needed to stop
	 * one-shotting a Wither. It stays the box's biggest term.
	 *
	 * <p>Known consequence, not fixed here: Shadow Flurry doubles the Shadow
	 * Step cooldown ({@link #SHADOW_STEP_FLURRY_COOLDOWN_TICKS}). At 4.5 it took
	 * the mob box 3.00 -> 7.50, x2.50 damage for x0.5 uptime — a clear buy. At
	 * 1.5 it takes 2.65 -> 4.15, x1.57 for x0.5 uptime, i.e. a net LOSS for
	 * anyone who steps more than once a fight. Cutting the flurry cooldown to
	 * ~400 ticks puts it back on the right side and costs neither target
	 * anything; it is left alone here because it is a pacing decision.
	 */
	public static final float SHADOW_FLURRY_BONUS = 1.5F;
	/** Daggers shove half as hard as a sword would. */
	public static final float DAGGER_KNOCKBACK_FACTOR = 0.5F;

	// --- Assassin passives ---
	public static final float LIGHTFOOT_PER_RANK = 0.10F;
	/** Sidestep is two ranks since the 20260720 sketch, not three, so the per-rank
	 * share went up to keep the node worth its second point: 10/20%. */
	public static final float SIDESTEP_PER_RANK = 0.10F;
	/** Crippling Poison: Slowness I/II riding every dagger hit, 4s. */
	public static final int CRIPPLING_SLOW_TICKS = 80;
	public static final float RAZOR_EDGE_PER_RANK = 0.08F;
	public static final float EXPOSE_PER_RANK = 0.10F;
	public static final int VENOM_TICKS = 80;
	public static final int BLIGHT_TICKS = 60;
	/**
	 * Flense: the fraction of the target's ARMOUR the blow behaves as if were
	 * not there, per rank — rank 2 ignores 60% of it. Denominated in armour,
	 * not in "absorption", and resolved against
	 * {@code CombatRules.getDamageAfterAbsorb} itself
	 * ({@link com.archetypes.ArmourMath}) rather than against a flat 4%-a-point
	 * stand-in.
	 *
	 * <p>The old constant was 0.5 meaning "fraction of armour's absorption
	 * clawed back", which reached its x5.0 ceiling at 20 armour — plain diamond
	 * — and paid back mitigation vanilla was not applying, because vanilla
	 * DEGRADES armour against big hits. That made Flense a switch worth more
	 * than every other Assassin multiplier combined, and worth most against a
	 * hit that armour had already stopped mattering to. As an armour-ignore it
	 * is a curve: worth ~x2.1 on an ordinary stab into netherite and ~x1.11 on
	 * the Shadow Step opener, where the armour it would ignore is already shred
	 * to the 20%-of-total floor.
	 */
	public static final float FLENSE_ARMOUR_IGNORE_PER_RANK = 0.30F;
	/** Twin Fangs: the off-hand dagger joins the step strike, additively into
	 * the ambush box, scaled by its damage against the main hand's — identical
	 * daggers give the whole term. Was 1.25F; kept at roughly the same share of
	 * {@link #SHADOW_FLURRY_BONUS} (28% -> 27%) so the two step-gated nodes hold
	 * their relative worth through the retune. */
	public static final float TWIN_FANGS_OFFHAND_BONUS = 0.40F;

	// --- Mana (the Seeker's resource; Spellcasting skill in Specialities) ---
	public static final float MANA_BASE = 100.0F;
	public static final float MANA_REGEN_BASE_PER_SECOND = 1.0F;
	public static final float MANA_PER_SPELLCASTING_LEVEL = 1.0F;
	public static final int MANA_REGEN_LEVELS_PER_POINT = 25;
	public static final float XP_PER_MANA = 1.0F;
	/** The potions: Mana Restore gives a chunk per level, Mana Regeneration
	 * a bonus stream per level on top of the natural one. */
	public static final float MANA_RESTORE_PER_LEVEL = 50.0F;
	public static final float MANA_REGEN_POTION_PER_LEVEL_PER_SECOND = 2.0F;

	// --- Seeker: Fireball / Meteorite / Flamethrower ---
	public static final float FIREBALL_COST = 50.0F;
	public static final float FIREBALL_DAMAGE = 5.0F;
	public static final int FIREBALL_FIRE_SECONDS = 5;
	public static final float FIREBALL_SPEED = 1.2F;
	public static final float METEOR_MIN_MANA = 100.0F;
	public static final int METEOR_HEIGHT = 16;
	public static final float METEOR_SPEED = 1.4F;
	/** Everything scales with m = effective mana / 100 (user formula): at
	 * 100 mana x1.0, at 250 x2.5 — damage, area, particles, loudness and
	 * the rock's rendered size all together. */
	public static final float METEOR_BASE_DAMAGE = 16.0F;
	public static final float METEOR_BASE_RADIUS = 2.5F;
	/** The rock's render/sound scale is capped so a maxed pool stays loud,
	 * not absurd; damage and area stay uncapped. */
	public static final float METEOR_FX_SCALE_CAP = 4.0F;
	public static final double METEOR_TARGET_RANGE = 32.0;
	public static final float FLAME_START_COST = 50.0F;
	/** 25 mana/second, paid per channel tick. */
	public static final float FLAME_COST_PER_TICK = 1.25F;
	public static final float FLAME_BOLT_DAMAGE = 2.0F;
	public static final int FLAME_BOLT_FIRE_SECONDS = 3;
	public static final float FLAME_BOLT_SPEED = 1.2F;
	public static final int FLAME_BOLT_PERIOD_TICKS = 2;
	/**
	 * A bolt is one tick of a stream, not a shot, so it burns out on its own:
	 * 16 blocks from the muzzle (straight line, not summed per tick) or two
	 * seconds, whichever comes first. Both caps are needed — a bolt fired into
	 * water keeps almost none of its speed and never collides with anything,
	 * so it neither travels its range nor dies, and a lake fills up with
	 * stalled fire clusters (user report).
	 */
	public static final double FLAME_BOLT_MAX_DISTANCE = 16.0;
	public static final int FLAME_BOLT_MAX_TICKS = 40;
	/**
	 * Vaporize boils this many water blocks per projectile, then the bolt is
	 * spent and goes away. The node used to sweep the whole 3x3x2 around the
	 * projectile every tick, which drained ponds off one bolt.
	 */
	public static final int VAPORIZE_MAX_BLOCKS = 1;

	/** Amnesia I's price: the fraction of earned levels KEPT after the
	 * respec (user: "maybe 33% or even 50% shaved" — starting at a third
	 * shaved, tune on the server). Amnesia II keeps nothing. */
	public static final float AMNESIA_LEVEL_KEEP = 2.0F / 3.0F;

	// --- Wands (see ModItems; every spell needs one in the main hand) ---
	public static final float WAND_APPRENTICE_DISCOUNT = 10.0F;
	public static final float WAND_SPECIALIST_DISCOUNT = 15.0F;
	public static final float WAND_SPECIALIST_POWER = 1.5F;
	public static final float WAND_HOLY_HEAL_FACTOR = 1.5F;
	/**
	 * The Oracle's Wand: the specialists' x1.5, but owed to no school — every
	 * spell in the mod, not just the one element the wand was cut for. It is
	 * the only wand whose bonus is unconditional, which is what the nether
	 * star buys.
	 */
	public static final float ORACLE_WAND_POWER = 1.5F;
	/**
	 * ...and a tenth off every price. A FRACTION, not the flat mana the older
	 * wands subtract: a flat cut would be a rounding error on the 150-mana
	 * epic actives and half the price of a cheap missile, so the universal
	 * wand scales with the spell instead of flattening the cost curve.
	 */
	public static final float ORACLE_WAND_DISCOUNT = 0.10F;

	// --- Elementalist: ice + element passives ---
	public static final float ICE_BLAST_COST = 50.0F;
	public static final float ICE_BLAST_DAMAGE = 4.0F;
	public static final float ICE_BLAST_SPEED = 1.2F;
	/** Slowness III for 4s at base; Frostbite adds a level and a second. */
	public static final int ICE_SLOW_AMP = 2;
	public static final int ICE_SLOW_TICKS = 80;
	/** Blizzard, the Meteorite's AOE opposite: a called storm raking its
	 * 5x5 ground for the full damage over the full duration, one pulse a
	 * second. Cost is a guess pending playtest. */
	public static final float BLIZZARD_COST = 75.0F;
	public static final float BLIZZARD_TOTAL_DAMAGE = 20.0F;
	public static final int BLIZZARD_DURATION_TICKS = 160;
	public static final int BLIZZARD_PULSE_TICKS = 20;
	/** An icicle-impact sound lands this often — twice per damage pulse. */
	public static final int BLIZZARD_SOUND_TICKS = 10;
	public static final double BLIZZARD_HALF_WIDTH = 2.5;
	/** Glacial Spike, the ice finisher: x2 cold, x10 against the chilled —
	 * prime with the AOE blast, execute with the spike. */
	/** Priced above Ice Blast: the x10 execute stays, but spamming it means
	 * drinking through mana potions, not idling on regen. */
	public static final float GLACIAL_COST = 75.0F;
	public static final float GLACIAL_BASE_MULTIPLIER = 2.0F;
	public static final float GLACIAL_CHILLED_MULTIPLIER = 10.0F;
	public static final int GLACIAL_FREEZE_TICKS = 240;
	public static final float KINDLING_DISCOUNT_PER_RANK = 5.0F;
	public static final float CHILL_DISCOUNT_PER_RANK = 5.0F;
	public static final float SCORCH_PER_RANK = 2.0F;
	public static final int IGNITION_SECONDS_PER_RANK = 3;
	public static final float SHATTER_PER_RANK = 0.15F;
	public static final float SPELLWEAVER_FACTOR = 0.9F;
	public static final float ARCANE_POWER_FACTOR = 1.2F;
	public static final float FOCUSED_MIND_REGEN = 0.5F;

	// --- Seeker: Magic Missile ---
	public static final float MISSILE_COST = 25.0F;
	public static final float MISSILE_DAMAGE = 6.0F;
	public static final double MISSILE_RANGE = 16.0;
	public static final float MISSILE_SPEED = 1.5F;
	public static final float MISSILE_HOMING_SPEED_FACTOR = 0.67F;
	public static final double MISSILE_HOMING_RADIUS = 12.0;
	public static final double MISSILE_PIERCE_INFLATE = 0.75;
	/** The missile's violet, faint mote and bright core. Shared with the
	 * Spellbow's conjured arrow (see SPELLBOW_ARROW_TRAIL_PERIOD_TICKS): both
	 * must read as one school of magic, so the colours live here rather than in
	 * either effect's own class. */
	public static final int MISSILE_DUST_COLOR = 0x7E5CBF;
	public static final int MISSILE_DUST_BRIGHT_COLOR = 0xB38EF3;

	// --- Wizard tree ---
	/** Fraction of incoming damage the (single-rank) shield converts to mana. */
	public static final float MANA_SHIELD_ABSORB = 0.5F;
	public static final float MANA_SHIELD_MANA_PER_DAMAGE = 2.0F;
	public static final float FORCE_PER_RANK = 1.0F;
	public static final float CLARITY_DISCOUNT = 5.0F;
	public static final float SIPHON_REFUND = 15.0F;
	public static final float ECHO_CHANCE = 0.25F;
	public static final double RANGE_PER_RANK = 8.0;
	public static final float ARCANE_ORB_MANA = 25.0F;
	public static final float VELOCITY_FACTOR = 1.3F;
	public static final float OVERWHELM_BONUS = 0.2F;
	public static final int CONCUSSION_WEAKNESS_TICKS = 60;
	public static final float SHATTERPOINT_BONUS = 0.3F;
	/** Mind Well: every Nth missile leaves empowered, +1.5 hearts. */
	public static final int MIND_WELL_EVERY_RANK_1 = 8;
	public static final int MIND_WELL_EVERY_RANK_2 = 4;
	public static final float MIND_WELL_EMPOWER_BONUS = 3.0F;
	/** The 200ms breath between missile casts. */
	public static final int MISSILE_CAST_GAP_TICKS = 4;
	public static final float FLOW_REGEN_PER_RANK = 0.5F;
	public static final float ARCHMAGE_FACTOR = 1.2F;

	// --- Priest tree ---
	/** Lumen raises both sides of the burst; Mercy/Wrath one side each. */
	public static final float LUMEN_PER_RANK = 1.0F;
	public static final float MERCY_PER_RANK = 2.0F;
	public static final float WRATH_PER_RANK = 2.0F;
	public static final float GRACE_DISCOUNT = 10.0F;
	public static final double RADIANCE_BONUS = 1.5;
	public static final float DEVOTION_REGEN = 0.5F;
	public static final float FERVENT_FACTOR = 1.5F;
	public static final float BEACON_MANA = 25.0F;
	/** Aegis shells the caster, Sanctuary the friends nearby — same shell. */
	public static final int AEGIS_TICKS = 600;
	/** Immolation's fire and Judgement's weakness, laid on the undead only. */
	public static final int IMMOLATION_FIRE_SECONDS_PER_RANK = 3;
	public static final int JUDGEMENT_WEAKNESS_TICKS = 120;
	public static final float ASCENDANT_FACTOR = 1.25F;

	/** Fireball bursts in a 3x3: half the edge of the cube centred on the
	 * impact point, so 1.5 IS "3x3" and 2.0 would be "4x4". */
	public static final double ELEMENT_BURST_RADIUS = 1.5;
	/** Ice Blast bursts in a 4x4 (user call, 2026-08-01) — its own number
	 * because only the ice half was widened; Fireball stays at 3x3. */
	public static final double ICE_BURST_RADIUS = 2.0;

	// --- Oracle (epic) actives ---
	/** Lightning Strike's flat mana price. Display-only for now — the effect is
	 * a stub — but the cooldown bar prices its tile with it. */
	public static final float LIGHTNING_STRIKE_COST = 150.0F;
	/** Lightning Strike's per-bolt damage: 40 = 20 hearts of indirect magic. */
	public static final float LIGHTNING_STRIKE_DAMAGE = 40.0F;
	/** How far the strike reaches for its target, blocks. */
	public static final double LIGHTNING_STRIKE_RANGE = 32.0;
	/** Overcharge: x2.0 Lightning Strike damage. */
	public static final float LIGHTNING_OVERCHARGE_FACTOR = 2.0F;
	/** Recurrence: each rank lands one more strike on the target, the extras
	 * this many ticks apart so they read as successive bolts, not one number. */
	public static final int LIGHTNING_RECURRENCE_DELAY_TICKS = 4;
	/** Chain Reaction: each hop reaches this many blocks, measured from the
	 * previous victim — the arc walks the horde, it does not fan out from the
	 * primary. */
	public static final double LIGHTNING_CHAIN_RANGE = 8.0;
	/** Chain Reaction: ticks the arc spends travelling between two victims. Low
	 * enough to feel like lightning, high enough that the eye can follow the
	 * jump instead of seeing one simultaneous multi-strike. */
	public static final int LIGHTNING_CHAIN_HOP_DELAY_TICKS = 3;
	/** Chain arc: particle segments drawn per block of hop length, clamped by
	 * the segment ceiling. Both are deliberately modest — a Tempest into a
	 * horde can schedule many hops and every segment is a client packet. */
	public static final double LIGHTNING_ARC_SEGMENTS_PER_BLOCK = 2.0;
	public static final int LIGHTNING_ARC_MAX_SEGMENTS = 20;
	/** Chain arc: how far, in blocks, a segment may be kicked off the straight
	 * line between the two victims, so the arc reads as a jagged bolt. The
	 * offset tapers to zero at both ends so the arc still touches its
	 * entities. */
	public static final double LIGHTNING_ARC_JITTER = 0.45;
	/** Chain arc: particles emitted per segment. Keep at 1 — the segment count
	 * is the density knob. */
	public static final int LIGHTNING_ARC_PARTICLES_PER_SEGMENT = 1;
	/** Tempest: the area-targeted strike catches every hostile within this
	 * radius of the aim point. */
	public static final double LIGHTNING_TEMPEST_RADIUS = 5.0;
	/** Tempest: mana spent for each hostile caught beyond the first; if the
	 * pool runs short the strike covers only what it can pay for. */
	public static final float LIGHTNING_TEMPEST_MANA_PER_EXTRA = 25.0F;
	/** Ceiling on the bolts one cast may schedule (targets x strikes x
	 * chains), so a Tempest into a horde can't freeze the server. */
	public static final int LIGHTNING_MAX_BOLTS = 64;
	/** Oracle's Wisdom: +50% max mana per rank, on the whole pool. */
	public static final float ORACLE_WISDOM_PER_RANK = 0.5F;
	/** Oracle's Focus: regenerate 2.5% of max mana per second per rank. */
	public static final float ORACLE_FOCUS_REGEN_PER_RANK = 0.025F;
	/** Magic Armaments' opening mana price to start the channel. */
	public static final float MAGIC_ARMAMENTS_COST = 50.0F;
	/** Channel upkeep per second — the whole price now that Mind over Matter is
	 * a single node and charges nothing extra. It is charged as this / 20 every
	 * tick, not as a once-a-second lump, so the mana bar drains smoothly; the
	 * rate is identical. A tick the pool cannot pay ends the channel. */
	public static final float MAGIC_ARMAMENTS_UPKEEP_PER_SECOND = 10.0F;
	/** The conjured sword's Sharpness, flat: vanilla adds 1 + 0.5 x (level - 1),
	 * so the sword's 7 melee hits for 12.5 and the bow matches it at full draw
	 * (see MAGIC_BOW_ARROW_SHARPNESS_SHARE). Mind over Matter no longer moves
	 * this — it multiplies the finished hit instead. */
	public static final int MAGIC_ARMAMENTS_SHARPNESS = 10;
	/** Mind over Matter: the conjured weapon's damage, x2, applied to the
	 * finished hit in the damage funnel rather than folded into Sharpness or the
	 * arrow's base. A multiplier is the one form that means the same thing on
	 * both weapons — an additive bonus on the arrow's base is tripled by the
	 * full-draw velocity (see MAGIC_BOW_ARROW_SHARPNESS_SHARE), a multiplier is
	 * not. */
	public static final float MIND_OVER_MATTER_DAMAGE = 2.0F;
	/** Mind over Matter's armor bypass, as virtual Breach levels stamped on the
	 * conjured weapon. Armor effectiveness is clamped to [0, 1] after the
	 * enchantment shifts it and Breach subtracts 0.15 per level, so anything
	 * past 6 zeroes out any armor value; 7 is that with a level to spare. This
	 * is a REAL enchantment, not Sunder's virtual-Breach arithmetic: Sunder
	 * claws back a share of what armor ate and has to estimate it, while a full
	 * bypass is exactly what vanilla's own armor_effectiveness hook does — and
	 * the same stamp covers the bow, because an arrow's damage source reports
	 * the bow it was fired from as its weapon item. */
	public static final int MIND_OVER_MATTER_BREACH = 2;
	/** Magic Armor: every point of mana the channel spends banks this much
	 * absorption per rank (0.5/1.0), capped by the rank's ceiling (10/20). The
	 * cap rides on MAX_ABSORPTION, so grants past it clamp away like Battle
	 * Trance's do. */
	public static final float MAGIC_ARMOR_HP_PER_MANA_PER_RANK = 0.5F;
	public static final float MAGIC_ARMOR_CAP_PER_RANK = 10.0F;
	/** Blink: a conjured-sword swing with no hostile under the crosshair jumps
	 * this far forward, safe-landing permitting. */
	public static final double MAGIC_ARMAMENTS_BLINK_DISTANCE = 8.0;
	/** Warding scans and strips harmful effects this often (every half second is
	 * indistinguishable from instant and far cheaper than every tick). */
	public static final int MAGIC_ARMAMENTS_WARD_PERIOD_TICKS = 10;
	/** Spellbow: the conjured bow's arrow base damage before Sharpness. Velocity
	 * (3x at full charge) multiplies it, landing a full shot near the sword's ~7. */
	public static final float MAGIC_BOW_ARROW_BASE_DAMAGE = 2.5F;
	/** Sharpness does nothing on a bow, so the arrow adds this share of the
	 * sword's Sharpness bonus to its base instead. 1/3 inverts the full-draw 3x
	 * velocity, so a point of sword damage is a point of arrow damage and the two
	 * variants stay even (13 against the sword's 12.5).
	 *
	 * <p>A real Power enchantment on the stack CANNOT replace this, however
	 * identical the two curves look on paper: vanilla adds Power to the arrow's
	 * BASE in {@code AbstractArrow.onHitEntity} and only then multiplies by the
	 * draw velocity, so the same level pays out three times over on a full draw
	 * (46 at rank 3, not 20.5) while Sharpness on the sword stays flat. */
	public static final float MAGIC_BOW_ARROW_SHARPNESS_SHARE = 1.0F / 3.0F;
	/** Spellbow: the draw is this much shorter, so full draw lands in a quarter
	 * of vanilla's 20 ticks. It does NOT move damage — power still caps at 1.0
	 * and velocity at 3x; only the time to get there changes. The same factor
	 * must drive the release power and the client pull animation, or the bow
	 * fires at a power its model has not finished drawing. */
	public static final float SPELLBOW_DRAW_TIME_REDUCTION = 0.75F;
	/** Specialities' Archery draw-speed bonus adds to the reduction above; the
	 * sum is clamped here, so no combination of skill and node draws in under a
	 * tenth of the normal time. */
	public static final float SPELLBOW_DRAW_TIME_REDUCTION_CAP = 0.90F;
	/** Spellbow: conjured arrows fall at this share of an arrow's 0.05/tick
	 * gravity. Applied on the arrow's own gravity hook, never by inflating
	 * launch velocity — velocity IS the damage multiplier (see
	 * MAGIC_BOW_ARROW_SHARPNESS_SHARE), so a velocity hack would also be a
	 * silent damage buff. */
	public static final float SPELLBOW_ARROW_GRAVITY_FACTOR = 0.25F;
	/** Spellbow flight FX, the Magic Missile's signature on an arrow: a violet
	 * mote every N ticks, an END_ROD sparkle every M, a chime every C. All three
	 * run sparser than SpellProjectile's because a bow drawing in five ticks
	 * puts far more projectiles in the air than a missile cast does. */
	public static final int SPELLBOW_ARROW_TRAIL_PERIOD_TICKS = 2;
	public static final int SPELLBOW_ARROW_SPARKLE_PERIOD_TICKS = 4;
	public static final int SPELLBOW_ARROW_CHIME_PERIOD_TICKS = 6;
	/** Mana Siphon: a Spellbow arrow that draws blood pays this much mana back.
	 * It is the whole reason the bow branch can outlive its own upkeep — five
	 * seconds of channel per landed shot — so it is deliberately gated on a hit
	 * landing on a living target, never on the shot leaving the bow. */
	public static final float MANA_SIPHON_PER_HIT = 50.0F;

	// --- Oracle Priest (epic): Aura of Radiance ---
	/** How long the aura burns off one Holy Light cast, and what Beacon of
	 * Light raises it to. Both are whole seconds and both are multiples of the
	 * pulse period, so no pulse is ever clipped short at the end. */
	public static final int RADIANCE_AURA_TICKS = 200;
	public static final int RADIANCE_BEACON_TICKS = 600;
	/** The aura reaches this far, blocks — the same number for the harm and
	 * the heal, because the tooltip promises one radius. */
	public static final double RADIANCE_AURA_RADIUS = 8.0;
	/** One pulse a second. A faster cadence was tried and reverted: in a crowd
	 * of undead every pulse fires each victim's hurt sound, and four a second
	 * across a dozen mobs is a wall of noise. Must divide RADIANCE_AURA_TICKS
	 * and RADIANCE_BEACON_TICKS exactly. */
	public static final int RADIANCE_PULSE_TICKS = 20;
	/** Damage to the undead and healing to friends per SECOND, health points:
	 * the bare aura, then Brilliance rank 1 and 2 (0.5 / 1 / 2 hearts). A
	 * pulse pays RADIANCE_PULSE_TICKS/20 of this, so the advertised per-second
	 * number is what the clock actually delivers whatever the cadence. The
	 * rungs SET the number rather than adding to it — the author's spec reads
	 * "increased to 1/2 hearts", so rank 2 is 2 hearts, not 2.5. */
	public static final float RADIANCE_AURA_AMOUNT = 1.0F;
	public static final float RADIANCE_BRILLIANCE_AMOUNT_RANK_1 = 2.0F;
	public static final float RADIANCE_BRILLIANCE_AMOUNT_RANK_2 = 4.0F;
	/** Owning Aura of Radiance multiplies every Holy Light cast's mana price,
	 * discounts and all — the aura is paid for at the cast, not on a clock. */
	public static final float RADIANCE_HOLY_COST_FACTOR = 2.0F;
	/** Blinding Light's Weakness/Slowness and Retribution's Strength/Speed are
	 * both level II, and both are re-laid every pulse for twice the pulse
	 * period: long enough that a pulse never lapses mid-aura, short enough that
	 * leaving the aura (or its ending) strips them within a second. */
	public static final int RADIANCE_EFFECT_AMPLIFIER = 1;
	public static final int RADIANCE_EFFECT_TICKS = 40;
	/** The halo FX: this many motes traced around the aura's rim, redrawn this
	 * often, the ring turning a full circle every RADIANCE_HALO_TURN_TICKS so
	 * it reads as a sweep rather than a strobe. */
	public static final int RADIANCE_HALO_POINTS = 12;
	public static final int RADIANCE_HALO_PERIOD_TICKS = 4;
	public static final int RADIANCE_HALO_TURN_TICKS = 80;
	/** The block-light level the caster emits while the aura is up (the
	 * author's number; glowstone is 15). Client-side only — see
	 * {@code RadianceLight} — so nothing about this reaches saved data. */
	public static final int RADIANCE_LIGHT_LEVEL = 14;

	// --- Nemesis Shadow (epic): the Dark Ritual and the night form ---
	/** The channel, in ticks. Ten seconds of standing perfectly still is the
	 * node's real cost; the mana-less Cutpurse pays in exposure, not resource. */
	public static final int DARK_RITUAL_CHANNEL_TICKS = 200;
	/** How long the channel ignores the swing/use interrupts after it starts.
	 * A swing animation runs six ticks and an eat thirty-two, so half a second
	 * lets the press that FOLLOWS a fight land while an eat still fails. Hits
	 * are not graced: they are tested by a hurtTime delta, which cannot mistake
	 * the tail of an older hit for a new one. */
	public static final int DARK_RITUAL_GRACE_TICKS = 10;
	/** The night form is a TOGGLE, not a timer: it lasts until the player ends
	 * it. This is the lockout — how long after transforming the revert press is
	 * refused. One real-time hour, 20 ticks/second (author's spec). */
	public static final int NIGHT_FORM_LOCKOUT_TICKS = 72_000;
	/** A kill while transformed restores this share of the victim's MAXIMUM
	 * health. Max, not remaining: the sketch says "25% of target creature's
	 * health", and remaining health at the moment of death is ~0. */
	public static final float NIGHT_FORM_KILL_HEAL_SHARE = 0.25F;
	/** Sunlight ignites an unhelmeted night-form player for this long, the
	 * same number vanilla's Mob.burnUndead uses. */
	public static final float NIGHT_FORM_SUN_BURN_SECONDS = 8.0F;
	/** Night vision and slow falling are re-asserted every tick at this
	 * duration and simply left to lapse when the form ends — long enough that
	 * no tick gap shows, past the 200-tick mark where vanilla night vision
	 * starts strobing. */
	public static final int NIGHT_FORM_EFFECT_TICKS = 300;

	// The night form does not touch the Cutpurse actives. True Shot and Shadow
	// Step hit for what they hit for, transformed or not: the Dark Ritual was
	// carrying an x1.5 on the arrow and a 1.25 term in the ambush box, and
	// both are gone (author's call — the Cutpurse chain had too many
	// multipliers, and this mod's are the ones being cut).

	/** Extra Sensory Perception's reach in blocks, and how often the roster is
	 * rebuilt. Twice a second is well inside a walking creature's stride and
	 * costs one AABB query per transformed player. */
	public static final double ESP_RADIUS = 32.0;
	public static final int ESP_REFRESH_TICKS = 10;

	/** Ghost Form: chance to void an incoming hit outright, per rank (25/50/75%).
	 * Rolled on the victim's intake, so it voids the WHOLE hit, DoTs and
	 * environment included — at rank 3 the form is three-quarters untouchable,
	 * which is what an hour-long commitment at the top of an epic tree buys. */
	public static final float GHOST_FORM_NEGATE_PER_RANK = 0.25F;
	/** Ghost Form's sneak-dash: blocks travelled per rank (2/4/6), and the
	 * clock that keeps it from being a flight mode. */
	public static final double GHOST_DASH_BLOCKS_PER_RANK = 2.0;
	public static final int GHOST_DASH_COOLDOWN_TICKS = 40;

	/** Feast: health points bled per second, per rank (1/2 hearts), for
	 * FEAST_TICKS, healing the attacker the same. Re-applying refreshes the
	 * bleed rather than stacking it, so the ceiling is 2/4 HP per second no
	 * matter how fast the attacks come. */
	public static final float FEAST_HP_PER_SECOND_PER_RANK = 2.0F;
	public static final int FEAST_TICKS = 80;
	/** The bleed resolves once a second. Must stay at or above 11 ticks, or
	 * LivingEntity.hurtServer's invulnerableTime gate swallows the repeat and
	 * the bleed pays out at half its advertised rate. (The aura goes faster
	 * than that only because it lends its victims a zero i-frame counter for
	 * the length of one call; the bleed deliberately does not.) */
	public static final int FEAST_PULSE_TICKS = 20;

	// --- Seeker: Holy Light ---
	public static final float HOLY_COST = 50.0F;
	public static final float HOLY_AMOUNT = 5.0F;
	public static final double HOLY_RADIUS = 4.0;
	public static final float HOLY_SPEED = 0.8F;
	public static final int HOLY_EFFECT_TICKS = 200;

	// --- Nemesis Marksman (epic): Deadeye and its two branches ---
	/** The stance, in ticks, and with Long Watch. Fifteen seconds of
	 * draw-free fire is worth about four to six ordinary shots' extra output;
	 * Long Watch's +67% is priced against a capstone. */
	public static final int DEADEYE_TICKS = 300;
	public static final int DEADEYE_LONG_WATCH_TICKS = 500;
	/** 90 seconds from the press, so the stance never overlaps itself even at
	 * Long Watch's 25. The one dial to turn first if Deadeye reads as
	 * mandatory rather than special. */
	public static final int DEADEYE_COOLDOWN_TICKS = 1800;
	/** Slowness II (and Fleet's Speed II) are re-asserted every tick at this
	 * duration and left to lapse, never removed — the NightForm.tickForm
	 * idiom, which cannot eat an effect the player had from elsewhere. */
	public static final int DEADEYE_EFFECT_TICKS = 40;
	/** Amplifier 1 is the numeral II: vanilla's -30% and +40%. Fleet is a net
	 * swing of about 70% movement, which is what makes it a point on its own. */
	public static final int DEADEYE_SLOWNESS_AMPLIFIER = 1;
	public static final int FLEET_SPEED_AMPLIFIER = 1;
	/** The speed a fully-drawn bow arrow leaves at: BowItem passes
	 * {@code pow * 3.0F} to shootFromRotation and pow tops out at 1. An
	 * underdrawn Deadeye arrow is scaled UP to this; a crossbow's 3.15 is
	 * already past it and is left alone. Damage follows, because
	 * AbstractArrow.onHitEntity multiplies baseDamage by the impact speed. */
	public static final double DEADEYE_FULL_DRAW_SPEED = 3.0;

	/** Long Shot: extra damage per block flown, per rank, capped at
	 * DEADEYE_LONG_SHOT_CAP_BLOCKS — x2.0 at rank 1 and x3.0 at rank 2, both
	 * reached at 50 blocks. That is inside the 64-block despawn but outside
	 * any mob's aggro range, so the payout has to be chosen. */
	public static final float LONG_SHOT_PER_BLOCK_PER_RANK = 0.02F;
	public static final double LONG_SHOT_CAP_BLOCKS = 50.0;
	/** Punch Through: vanilla's pierce level, i.e. two creatures passed
	 * through before the third stops the arrow (AbstractArrow discards once
	 * pierceLevel + 1 entities are in its ignore set). One below Piercing IV,
	 * so an enchanted crossbow still does something the tree does not. */
	public static final byte PUNCH_THROUGH_PIERCE_LEVEL = 2;
	/** Punch Through: the share of the target's armour the arrow ignores.
	 * Half, not all — the node used to hand back every armour point's worth
	 * ({@link #ARMOUR_POINT_DAMAGE_FRACTION} x armour) and against a netherite
	 * target that was an 80% damage bonus on a stream of arrows. */
	public static final float PUNCH_THROUGH_ARMOUR_IGNORE = 0.5F;
	/** Siege: the planted multiplier, and how long standing still takes to
	 * arm it. Snap Shot's x4.0 halved, because unlike Snap Shot it applies to
	 * every arrow. */
	public static final float SIEGE_MULTIPLIER = 2.0F;
	public static final int SIEGE_ARM_TICKS = 20;
	/** How far the server-side position may drift between ticks and still
	 * count as standing still. The client is authoritative about movement, so
	 * this is a position delta, not getDeltaMovement — a stationary player
	 * still jitters by a fraction of a block. */
	public static final double SIEGE_STILL_TOLERANCE = 0.003;
	/** The ceiling on the product of everything Deadeye contributes to one
	 * arrow (Long Shot x Siege x Punch Through's armour compensation). Long
	 * Shot and Siege already refuse True Shot arrows; this is the second
	 * fence, so no combination of nodes can reach the x24 the design's own
	 * balance pass warned about. */
	public static final float DEADEYE_MAX_MULTIPLIER = 6.0F;

	/** Vault: Acrobatics' roll becomes a flat eight blocks on a three-second
	 * clock, replacing 2-per-rank on eight seconds. */
	public static final double VAULT_BLOCKS = 8.0;
	public static final int VAULT_COOLDOWN_TICKS = 60;
	/** On the Wing: seconds off Acrobatics per arrow that hits — Focus's
	 * ratio (10s off 20s) against Vault's three-second clock, i.e. hitting
	 * keeps you rolling. */
	public static final int ON_THE_WING_REFUND_TICKS = 40;

	/** Deadeye's arrows leave a crit trail every this many ticks of flight —
	 * the Spellbow's per-tick hook, at a quarter of its density. */
	public static final int DEADEYE_TRAIL_PERIOD_TICKS = 2;
	/** The concentration vignette's peak alpha at the screen edge. Fifteen
	 * percent: it must read as focus, not as damage. */
	public static final float DEADEYE_VIGNETTE_ALPHA = 0.15F;

	// --- Nemesis Assassin (epic): Death Mark and its two branches ---
	/** How far a mark can be named, and how far a marked Shadow Step reaches.
	 * One number for both: double SHADOW_STEP_RANGE, and the same 32 Extra
	 * Sensory Perception senses at, so a mark can never sit outside the range
	 * you could already have seen it at. */
	public static final double DEATH_MARK_RANGE = 32.0;
	/** The mark's own minute, and the 45 seconds before another can be named.
	 * The mark deliberately OUTLIVES its cooldown by 15s: an assassin who keeps
	 * killing never waits, one who lets the mark walk eats the full clock. */
	public static final int DEATH_MARK_TICKS = 1200;
	public static final int DEATH_MARK_COOLDOWN_TICKS = 900;
	/** The root's own dagger bonus on the mark, and Headhunter's per rank.
	 * Both are terms in the ambush box now rather than two separate
	 * multiplications, so at rank 2 the Hunt line prices a marked stab at
	 * x1.75 instead of 1.25 x 1.5 = x1.875 — the sustained number barely
	 * moves, and it is only the opener the collapse takes apart.
	 * Raise either and the epic Hunt line passes the Oracle actives. */
	public static final float DEATH_MARK_DAMAGE_FACTOR = 0.25F;
	public static final float HEADHUNTER_PER_RANK = 0.25F;
	/**
	 * Coup de Grace's execute threshold. Deliberately NOT
	 * {@link #EXECUTE_THRESHOLD}: the Slayer's Executioner finishes at 15% and
	 * the author's draft says 30% for this one, so reusing the constant would
	 * silently halve the node and contradict its own description.
	 */
	public static final float COUP_DE_GRACE_THRESHOLD = 0.30F;
	/**
	 * What a marked PLAYER takes instead of the execute. A guaranteed delete on
	 * a 45s clock is not a skill node (the rule Executioner already follows),
	 * so the execute pays out as extra weight on the blow instead.
	 *
	 * <h2>The ambush box</h2>
	 * This term, {@link #SHADOW_FLURRY_BONUS}, {@link #TWIN_FANGS_OFFHAND_BONUS},
	 * {@link #DEATH_MARK_DAMAGE_FACTOR}, {@link #HEADHUNTER_PER_RANK},
	 * {@link #FIRST_STRIKE_PER_RANK} and {@link #BLOODRUSH_PER_RANK} are summed
	 * into ONE multiplier — the ambush box — rather than multiplied one over the
	 * next. Nine multiplicative sources with no ceiling are what turned one bad
	 * Flense constant into a 9.4x overkill: the product of the old step chain
	 * was x25.3, and every node added to a sum costs its own face value instead
	 * of its face value times everything already in the box. Coup de Grace lives
	 * INSIDE the box deliberately; it is the only player-exclusive term and
	 * leaving it outside is what let the collapse be undone by the one build
	 * that takes the capstone.
	 *
	 * <p><b>Nothing flat may sit below the box.</b> That is the rule the two
	 * deleted Strength grants (First Strike, Bloodrush) broke: a +6 on the
	 * ATTACK_DAMAGE attribute is worth 6 x box, so while either was live the
	 * ceilings below were fiction. Every term is now a summand or it is nothing.
	 *
	 * <h2>The declared ceilings</h2>
	 * With every node maxed, a live mark, invisibility, a Shadow Step and (for
	 * the "+kill" rows) Bloodrush's window open:
	 * <pre>
	 *   vs a mob     1 + 0.25 DM + 0.50 HH + 0.50 FS + 1.50 SF + 0.40 TF        = 4.15
	 *                                                     + 0.20 Bloodrush      = 4.35
	 *   vs a player  the same, + 3.40 Coup de Grace                             = 7.55
	 *                                                     + 0.20 Bloodrush      = 7.75
	 *   sustained    marked stab, visible   1 + 0.25 + 0.50                     = 1.75
	 *                marked stab, invisible                     + 0.50          = 2.25
	 * </pre>
	 *
	 * <h2>How 3.40 was solved (and how far it may move)</h2>
	 * Target: a maxed opener leaves a full-netherite, Protection IV, Defence 100
	 * player (40 max HP) alive on 2-5 HP. That chain is affine in the box —
	 * {@code landed = box x 4.88861} — because Flense lands in
	 * {@code ArmourMath} branch 3, whose ratio is damage-independent. So:
	 * <pre>
	 *   5 HP left  -> box 7.1595 -> CG 3.01
	 *   2 HP left  -> box 7.7732 -> CG 3.62
	 *   a kill     -> box 8.1823 -> CG 4.03
	 * </pre>
	 * 3.40 sits near the centre: 36.91 landed, 3.09 HP left, or 37.89 / 2.11 HP
	 * with Bloodrush also lit — so the whole reachable band is inside the
	 * window, and there is no state in which the opener kills. Every 0.10 of
	 * this constant is worth 0.49 HP.
	 *
	 * <h2>The PvE window this is solved against</h2>
	 * The mob box, times the fixed prefix 7.800 x 1.24 = 9.672 and Skill
	 * Proficiencies' crouched x4.5, must one-shot a 100 HP Ravager and must not
	 * one-shot the 300 HP Wither:
	 * <pre>
	 *   Ravager floor            box >= 2.30
	 *   Ender Dragon head (200)  box &lt;  4.60   &lt;- the operative ceiling
	 *   Wither ceiling (300)     box &lt;  6.98
	 *   Warden  (500)            box &lt; 11.49
	 * </pre>
	 * 4.35 all-lit leaves the dragon's head 10.7 HP (5.3%). It is the DRAGON and
	 * not the Wither that binds, and anyone topping a summand up should solve
	 * against 4.60. An Iron Golem is byte-identical to a Ravager (100 HP, no
	 * armour), so one-shotting one one-shots the other; that is accepted.
	 *
	 * <h2>Two things outside this mod that move these numbers</h2>
	 * The 3.09 HP residual assumes Skill Proficiencies at its default
	 * {@code combatDamageMaxBonus = 0.5} (Combat 100 = x1.5). Their config
	 * permits up to 5.0, and their own javadoc invites 1.0 — at x2.0 the same
	 * opener is lethal. That one is stated rather than defended against,
	 * because defending against a sibling mod's config means no node can state
	 * a number in its description. Vanilla's critical hit, by contrast, IS
	 * defended against since the ambush-bucket session:
	 * {@code AgilityActives.strike} calls {@code resetFallDistance()}
	 * immediately before the attack, so a Shadow Step can never crit — the
	 * 10.2-base lethal case is unreachable through the step. A MANUAL falling
	 * dagger crit still exists but has no Shadow Flurry (+1.50 needs a step
	 * strike), so its box is 6.05 and it lands ~38.7 — alive on ~1.3, tight
	 * but not lethal.
	 */
	public static final float COUP_DE_GRACE_PLAYER_BONUS = 3.40F;
	/** Stalk: beyond this, a sneaking hunter is dropped by their own mark. Eight
	 * blocks is inside a dagger's opening range, so the node hides the approach
	 * and never the kill. */
	public static final double STALK_UNAWARE_BLOCKS = 8.0;
	/** Contagion's reach for the hop. Half the cast range: the mark travels
	 * through a pack, not across the field. */
	public static final double CONTAGION_HOP_RADIUS = 16.0;
	/** Carrier's spread radius and beat. Aura of Radiance's eight blocks, once
	 * a second — copying effects is free, so the beat is about readability. */
	public static final double CARRIER_RADIUS = 8.0;
	public static final int CARRIER_PERIOD_TICKS = 20;
	/** Vanishing Act: four seconds of Invisibility and Speed II on the kill,
	 * the mobility twin of the Shadow tree's Bloodrush. Amplifier 1 is the
	 * numeral II. */
	public static final int VANISHING_ACT_TICKS = 80;
	public static final int VANISHING_ACT_SPEED_AMPLIFIER = 1;
	/** Death's Head: the detonation's radius and its 5 hearts. Ten damage is
	 * about one un-multiplied Shadow Flurry strike — worth chaining, not worth
	 * building around on its own. */
	public static final double DEATHS_HEAD_RADIUS = 8.0;
	public static final float DEATHS_HEAD_DAMAGE = 10.0F;
	/** How often the mark drips smoke over its head. Twice a second: the tell
	 * is load-bearing in PvP and must read as continuous, not as a proc. */
	public static final int DEATH_MARK_SMOKE_PERIOD_TICKS = 10;

	// --- Colossus Crusher (epic): Titan's Leap and its two columns ---

	/**
	 * The leap's upward impulse, blocks per tick. NOT 15 — vertical travel is
	 * gravity plus per-tick drag, so the impulse that reaches the advertised 15
	 * blocks has to be solved for, not scaled. Simulating vanilla's own air
	 * step ({@code LivingEntity.travelInAir}: move by dy, then
	 * {@code dy = (dy - 0.08) * 0.98}) peaks at 14.96 blocks from 1.69.
	 * {@link #RUSH_IMPULSE_PER_BLOCK} is a HORIZONTAL, on-the-ground
	 * approximation and does not apply here.
	 */
	public static final double TITAN_LEAP_UP_IMPULSE = 1.69;
	/**
	 * The leap's forward impulse, blocks per tick along the flat look vector.
	 * Solved the same way against vanilla's 0.91 horizontal air drag over the
	 * 40 ticks the leap spends off the ground: 0.55 carries 5.97 blocks, i.e.
	 * the advertised 6. Player input during the flight adds to this.
	 */
	public static final double TITAN_LEAP_FORWARD_IMPULSE = 0.55;
	/** Exactly Quake's clock ({@link #QUAKE_COOLDOWN_TICKS}). The two mace
	 * actives must not run on visibly different timers. */
	public static final int TITAN_LEAP_COOLDOWN_TICKS = 600;
	/** Ticks after take-off before the landing test is allowed to fire, so the
	 * tick the player is still standing on the ground does not count as a
	 * landing. Two ticks clears the ground at 1.69 blocks/tick. */
	public static final int TITAN_LEAP_LAUNCH_GRACE_TICKS = 2;
	/** The flight's leash. The arc itself is 40 ticks; anything still in the
	 * air after ten seconds got there by terrain, and a leap that never lands
	 * somewhere the ticker can see it would leave the fall-damage waiver
	 * standing forever. */
	public static final int TITAN_LEAP_MAX_FLIGHT_TICKS = 200;

	/**
	 * The bare-fisted landing's reach, in blocks. Six — between Aftershock's
	 * rank 1 and rank 2 ({@link #AFTERSHOCK_RADIUS_BASE}), because the two
	 * landings are the same drop paid out differently and neither column should
	 * out-reach the other on the way in. Applied as a real radius: the box
	 * query is inflated by it and then clamped by distance, because a box that
	 * covers a 6-block disc reaches 8.5 blocks at its corners.
	 */
	public static final double TITAN_LEAP_STOMP_RADIUS = 6.0;
	/**
	 * Slowness <b>II</b>, i.e. amplifier 1. Deliberately far below Haymaker's
	 * stun ({@link #HAYMAKER_STUN_AMPLIFIER} = 5, a Slowness VI that is a stun
	 * in all but name): that one lands on ONE jaw the player had to aim at,
	 * this one lands on a whole ring for eight seconds. A ring-wide stun would
	 * be the epic tree deciding fights on its own.
	 */
	public static final int TITAN_LEAP_STOMP_SLOW_AMPLIFIER = 1;
	/**
	 * The bare-fisted landing's one clock, 160 ticks = <b>8 seconds</b>, spent
	 * on both halves: how long the ring stays slowed AND how long the fists hit
	 * harder. One constant on purpose — the tooltip says "for 8 seconds" once
	 * and there is no reading of it under which the two numbers differ, so two
	 * constants could only ever drift apart into a tooltip that lies.
	 */
	public static final int TITAN_LEAP_STOMP_TICKS = 160;
	/**
	 * What the landing adds to unarmed damage: 4.0, i.e. <b>2 hearts</b>.
	 * Two ranks of Bare-Knuckle ({@link #BARE_KNUCKLE_FIST_PER_RANK}) for eight
	 * seconds once every thirty, and it rides that node's own channel — a
	 * second ATTACK_DAMAGE modifier asserted by {@code CrusherTicker} under the
	 * same {@code hands} gate — rather than opening a new entry on the damage
	 * funnel. A funnel entry would have had to answer for thorns, DoTs and
	 * every non-swing the shapers already fight about; an attribute answers for
	 * none of that, and it shows up in the player's own tooltip besides.
	 */
	public static final float TITAN_LEAP_STOMP_DAMAGE = 4.0F;

	/**
	 * Aftershock's radius: 4/6/8 blocks. Quake's own is 3
	 * ({@link #QUAKE_RADIUS}) and Earth Shatterer's climbs 2/4/6, so rank 3
	 * sits one notch above the base tree's ceiling — correct for epic.
	 */
	public static final double AFTERSHOCK_RADIUS_BASE = 2.0;
	public static final double AFTERSHOCK_RADIUS_PER_RANK = 2.0;
	/** Lifted verbatim from {@link #QUAKE_DAMAGE_MULTIPLIER} so the landing
	 * reads as the same slam and needs no second mental model. */
	public static final float AFTERSHOCK_DAMAGE_MULTIPLIER = 1.5F;
	/** Health per block fallen, per rank — 0.25/0.5/0.75 hearts. Meteor's own
	 * curve ({@link #METEOR_PER_BLOCK_PER_RANK}) one rank longer, but paid to a
	 * whole radius, only on a leap, and only once per 30 seconds. */
	public static final float AFTERSHOCK_PER_BLOCK_PER_RANK = 0.5F;
	/** Blocks of fall that count. Without a cap a Colossus who leaps off a
	 * mountain rather than off the leap gets unbounded AoE; 20 is above the
	 * leap's own 15, so it only ever bites on terrain abuse. */
	public static final float AFTERSHOCK_MAX_FALL = 20.0F;
	/** The send-off, matching Quake's ({@link #QUAKE_LAUNCH}). */
	public static final double AFTERSHOCK_LAUNCH = 0.95;

	/**
	 * Hardened: armour per plate, by what was in hand when the hit landed. The
	 * fists number is double the mace's for the tree's usual reason — a mace
	 * Colossus is already being paid by Aftershock and Sunder, and bare hands
	 * have to buy something back.
	 */
	public static final int HARDENED_MACE_ARMOR = 1;
	public static final int HARDENED_UNARMED_ARMOR = 2;
	/**
	 * A plate's life, per rank: 2 seconds at rank 1, 4 at rank 2. Rank is a
	 * multiplier on the DURATION and not on the amount, so the second rank pays
	 * by letting plates overlap twice as deep rather than by handing out more
	 * armour per hit — which is what makes the "stacks independently, new
	 * stacks don't refresh duration" rule the node's whole mechanic.
	 */
	public static final int HARDENED_DURATION_TICKS_PER_RANK = 40;

	/**
	 * Bulwark: health added to Battle Trance's ceiling per rank — <b>3.5/7
	 * hearts</b>. The base cap is {@link #TRANCE_CAP_PER_RANK} x 3 = 3 hearts,
	 * so rank 1 more than doubles the bank and rank 2 more than triples it.
	 *
	 * <p>Deliberately the same 7.0 the deleted MAX_HEALTH modifier carried, and
	 * that is the whole point of the rework: the node still moves the same
	 * number of hearts, but they are hearts the player has to punch something
	 * to get and that drain when the fight stops.
	 *
	 * <h2>What the node is NOT any more</h2>
	 * Two designs have been through this constant's neighbourhood and both are
	 * gone:
	 * <ul>
	 * <li>A flat 20%-per-rank damage reduction on the {@code hurtServer} funnel.
	 *     It was a victim-side {@code ModifyVariable} at HEAD, i.e.
	 *     <b>pre-armour</b>: cutting the raw number there does not merely take
	 *     its advertised 40%, it also drops the hit far enough that vanilla
	 *     stops shredding the victim's armour by {@code damage/t}, so the armour
	 *     stage silently paid a second dividend on top — biggest against the
	 *     biggest hits, and multiplicative with Instinctive Guard and Mana
	 *     Shield besides.</li>
	 * <li>A flat {@code +7.0} MAX_HEALTH a rank, asserted by
	 *     {@code CrusherTicker}. It fixed the funnel problem and kept the other
	 *     one: it was zero-input. Two points bought a second health bar that
	 *     was there whether the player did anything or not, and it read as the
	 *     Protector's kind of node rather than the Crusher's.</li>
	 * </ul>
	 * What is left is the half that was always conditional on play: Battle
	 * Trance has to be <b>earned</b>, hit by hit, and Bulwark only says how
	 * high the earning can go — plus {@link #TRANCE_DECAY_DELAY_TICKS}'s clock
	 * being held open while the fists are bare, which is a second thing the
	 * player has to choose. A ceiling nobody banks against is worth nothing,
	 * which is exactly the property the two dead designs lacked.
	 *
	 * <p>Denominated in HEALTH, not hearts, because that is what
	 * {@code Attributes.MAX_ABSORPTION} is denominated in; the tooltip does the
	 * halving.
	 */
	public static final float COLOSSUS_BULWARK_TRANCE_CAP_PER_RANK = 7.0F;

	/** Unstoppable Force: seconds a shield raised against a mace or a bare fist
	 * is knocked aside for. The Warden's own number
	 * ({@code Warden.getSecondsToDisableBlocking} returns 5.0F), applied
	 * through the same {@code BlocksAttacks.disable} vanilla routes it to. */
	public static final float UNSTOPPABLE_DISABLE_SECONDS = 5.0F;

	/**
	 * The clash: an Unstoppable Force landing on an Immovable Object. Neither
	 * node wins — the blow is voided, the shield holds, and the meeting point
	 * detonates. A 10x10x3 box of everything stone-soft or softer, centred
	 * between the two of them.
	 */
	public static final int CLASH_RADIUS = 5;
	public static final int CLASH_HEIGHT = 3;
	/** Stone's own hardness: the ceiling on what the blast takes with it, so
	 * dirt, wood and stone go and obsidian, ore and iron stay. Negative
	 * hardness (bedrock, portal frame) is unbreakable and never counted. */
	public static final float CLASH_MAX_HARDNESS = 1.5F;
	/** Damage to BOTH players, before armour and resistance — the author's
	 * "moderate, around 5 hearts". */
	public static final float CLASH_DAMAGE = 10.0F;
	/**
	 * The shove, as a velocity written straight onto both players rather than
	 * a {@code knockback} call: the whole point is that it ignores knockback
	 * resistance, and both nodes' owners are exactly the builds that have it.
	 *
	 * <p>Horizontal drag is 0.91 a tick, so an opening 0.5 carries about
	 * {@code 0.5 x 0.91 / (1 - 0.91)} = 5 blocks. The lift is vanilla's own
	 * jump velocity, enough to break ground contact so the drag figure holds.
	 */
	public static final double CLASH_PUSH = 0.5;
	public static final double CLASH_LIFT = 0.42;

	/** The Protector's Immovable Object cue: at most one note per second, so a
	 * node whose whole effect is that nothing happened announces itself the
	 * first time without droning under a mob pack. (The Crusher's own Immovable
	 * shared this period until the node was replaced by Hardened.) */
	public static final int IMMOVABLE_CUE_PERIOD_TICKS = 20;

	/**
	 * Ironclad: +50% armour and armour toughness, as an
	 * {@code ADD_MULTIPLIED_TOTAL} amount, so the number here IS the "final
	 * multiplier" the sketch asked for.
	 *
	 * <p>Both attributes are {@code RangedAttribute}s with hard ceilings —
	 * armour 30, toughness 20 — and full netherite is exactly 20 armour and 12
	 * toughness. So this lands a netherite Colossus on 30 armour, the game's
	 * own maximum, and 18 toughness. That is the node's real shape: it does not
	 * break the armour ceiling, it puts the ceiling within reach of gear that
	 * never got there, and iron (15 armour, 0 toughness) gains more from it
	 * than diamond does.
	 */
	public static final double IRONCLAD_ARMOUR_BONUS = 0.50;

	/**
	 * Well Fed: 37.5% off the time a bite of food takes, per rank.
	 *
	 * <p>Rank 2 is 75% off — a steak in 8 ticks rather than 32, which is fast
	 * enough to be a real answer to being caught low mid-fight rather than a
	 * convenience. The floor of one tick in {@code ItemStackMixin} is still not
	 * reachable at these ranks (the shortest vanilla food is 16 ticks, so 4 at
	 * full rank), and it is there because a zero would send
	 * {@code Consumable.startConsuming} down the instant-use branch and change
	 * what eating IS.
	 */
	public static final float WELL_FED_EAT_SPEED_PER_RANK = 0.375F;
	/** Well Fed: how much further the hunger bar fills, per rank, as a share of
	 * vanilla's 20. Rank 1 is 30, rank 2 is 40 — two full bars.
	 *
	 * <p>The regeneration thresholds are untouched (see {@link
	 * ColossusProtector#hungerCeiling}), so the bank buys time, not a rate: at
	 * 40 the player simply stays above vanilla's 18-point regen line for twice
	 * as long before food matters again. Saturation is still capped at
	 * {@code FoodConstants.MAX_SATURATION}; letting it ride the raised ceiling
	 * would have doubled the fast regen too, which is a rate. */
	public static final float WELL_FED_BANK_PER_RANK = 0.50F;

	/** Hearty Meal: eight minutes, the sketch's number, in ticks. Long enough
	 * that a fed Colossus is buffed for a whole expedition and short enough
	 * that it is not simply permanent — a Beacon's own range is 11 seconds. */
	public static final int HEARTY_MEAL_TICKS = 9600;
	/** Strength II and Speed II are amplifier 1; Regeneration I is amplifier 0. */
	public static final int HEARTY_MEAL_MEAT_AMPLIFIER = 1;
	public static final int HEARTY_MEAL_FRUIT_AMPLIFIER = 1;
	public static final int HEARTY_MEAL_MILK_AMPLIFIER = 0;

	/**
	 * Instinctive Guard: the share of a would-be block a carried shield
	 * actually stops, per rank — 25% then 50%.
	 *
	 * <p>A vanilla shield blocks 100% of what it blocks at all, so rank 2 is
	 * half of every non-bypassing hit, always, with no button and no facing.
	 * Below 1.21.11 that 100% is vanilla's own all-or-nothing blocking branch
	 * rather than a resolved {@code damage_reductions} number; the two come out
	 * at the same figure for a vanilla shield, which is why the node ports at
	 * full strength (see {@link ColossusProtector#instinctiveGuard}).
	 * That is two of this tree's five points and it is fenced by the shield
	 * itself: the durability charged is the whole block, not the quarter or
	 * half kept ({@link ColossusProtector#instinctiveGuard}), so a Colossus who
	 * stands in a mob pack goes through shields.
	 */
	public static final float INSTINCTIVE_GUARD_PER_RANK = 0.25F;

	// --- Colossus Slayer (epic) ---

	/**
	 * The parry window: 0.4 seconds from the ability key, the author's
	 * "generous window". Eight ticks is generous by fighting-game standards and
	 * still under the ~100ms round trip a hit takes to arrive, so a parry
	 * timed against a mob's wind-up lands and a mashed one does not.
	 *
	 * <p>Two ticks wider than the attack+block combo it replaced, because the
	 * price of a wrong guess is no longer one slow swing but
	 * {@link #PARRY_COOLDOWN_TICKS}.
	 */
	public static final int PARRY_WINDOW_TICKS = 8;

	/**
	 * What a missed parry costs: eight seconds before the key answers again.
	 * Reading the wind-up right is still supposed to let you read the next one,
	 * so a landed parry costs a fraction of this — but no longer nothing at all.
	 * See {@link #PARRY_SUCCESS_GREATSWORD_COOLDOWN_TICKS}.
	 */
	public static final int PARRY_COOLDOWN_TICKS = 160;

	/**
	 * What a LANDED parry costs with a greatsword: two seconds.
	 *
	 * <h2>Why a landed parry costs anything</h2>
	 * Because refunding the key outright made the node self-sustaining in both
	 * directions, and neither one is a fight:
	 * <ul>
	 * <li><b>PvE.</b> A parry that pays for itself is answered by being attacked,
	 *     so a Colossus standing in a mob pack could re-open the window on every
	 *     incoming blow and take none of them — near-invulnerability bought with
	 *     one key, and the more enemies there were the safer it got.</li>
	 * <li><b>PvP.</b> Two Slayers with the node parry-locked each other: each
	 *     landed parry hands the key back AND fills the swing timer
	 *     ({@code ColossusSlayer.pay}), so both players could answer every swing
	 *     the other threw forever and neither fight ever resolved.</li>
	 * </ul>
	 *
	 * <p>So the reward is now the two things a parry was always FOR — the free
	 * swing and the riposte — rather than the key as well. The clock is short on
	 * purpose: long enough that the window cannot be re-opened inside the same
	 * exchange, short enough that a player who reads three wind-ups in a row is
	 * still rewarded for two of them.
	 *
	 * <p>Twice the sword's, because a greatsword parry is worth more: it answers
	 * with a free instant Decimate ({@code SlayerActives.decimate(player, true)}),
	 * which the sword's answer cannot match. That reward is unchanged — this
	 * constant fences how OFTEN it can be earned, next to
	 * {@link #DECIMATE_FREE_COOLDOWN_TICKS}, which fences the cast itself.
	 */
	public static final int PARRY_SUCCESS_GREATSWORD_COOLDOWN_TICKS = 40;

	/**
	 * What a LANDED parry costs with a sword: one second — half the greatsword's,
	 * for the reason named on {@link #PARRY_SUCCESS_GREATSWORD_COOLDOWN_TICKS}.
	 * A sword parry reflects the blow and follows with a normal attack; it is the
	 * cheaper answer, so it comes back sooner.
	 */
	public static final int PARRY_SUCCESS_SWORD_COOLDOWN_TICKS = 20;

	/**
	 * Barbarian: 37.5% of magical damage AND magical healing cut per rank, so
	 * rank 2 is the author's 75%. It is deliberately symmetrical — the node
	 * that shrugs off a Seeker also shrugs off a Priest, which is what stops
	 * it being a free 75% resistance in a party.
	 *
	 * <p>What counts as magical is the {@code archetypes:magical} damage-type
	 * tag and, for healing, {@link ColossusSlayer#barbarianHealing}.
	 */
	public static final float BARBARIAN_MAGIC_CUT_PER_RANK = 0.375F;

	/**
	 * Blade Master: <b>-20% greatsword ATTACK_SPEED per rank</b>, so rank 2 is
	 * -40%. The sign is the whole change — the node used to hand the same 20% a
	 * rank the other way, as a swing-time CUT.
	 *
	 * <h2>Why it inverted</h2>
	 * Because the node was paying twice for one point. Blade Master already
	 * carries the tree's armour lane ({@link #BLADE_MASTER_ARMOUR_IGNORE_PER_RANK}
	 * and {@link #BLADE_MASTER_PROTECTION_BITE_PER_RANK}), and the speed half on
	 * top of it did not stack with the rest of a Brawler's kit so much as cancel
	 * it: Heavy Blows is a deliberate damage-for-speed trade, and Blade Master
	 * rank 2 handed the speed back with interest, so the build that took both
	 * paid nothing for either. Measured on the greatsword's own 0.8 swings/s
	 * (25.0 ticks to full charge), with Skill Proficiencies' Arms Mastery 100
	 * multiplying the finished RECOVERY TIME by 0.7 after the attributes resolve:
	 * <ul>
	 * <li>bare greatsword — 25.0 ticks;</li>
	 * <li>Heavy Blows 3 (-30% speed) — 35.7 ticks;</li>
	 * <li>+ old Blade Master 2 (+66.7% rate) — 21.4, and <b>15.0</b> with Arms
	 *     Mastery 100. A two-hander swinging faster than it does bare-handed, and
	 *     the author's complaint;</li>
	 * <li>+ this constant instead (-40% speed) — 59.5, and <b>41.7</b> with Arms
	 *     Mastery 100.</li>
	 * </ul>
	 *
	 * <h2>Why an attribute, and why denominated in speed</h2>
	 * An attribute because it has to be one: {@code
	 * getCurrentItemAttackStrengthDelay} reads {@code ATTACK_SPEED} and nothing
	 * else. Denominated in attack speed, as a raw {@code ADD_MULTIPLIED_TOTAL}
	 * amount, because that is {@link #HEAVY_PER_RANK}'s idiom in this mod and the
	 * two modifiers sit on the same attribute — vanilla applies each
	 * {@code ADD_MULTIPLIED_TOTAL} in turn, so the two multiply cleanly and the
	 * description's "20/40% slower" reads the same way Heavy Blows' does.
	 *
	 * <p>Greatsword only. The armour lane now reaches ordinary swords too (see
	 * {@code ColossusSlayer.bladeMasterFactor}); this penalty deliberately does
	 * not — a sword's speed is the reason to carry one.
	 */
	public static final float BLADE_MASTER_SWING_PENALTY_PER_RANK = 0.20F;

	/**
	 * Blade Master: the share of a victim's ARMOUR a greatsword hit ignores, per
	 * rank. Rank 2 is half of it.
	 *
	 * <p>The node's other half used to be +20% sword attack damage per rank. It
	 * is gone, and this is what replaced it: the tree's two feet are now one
	 * lane each — Barbarian answers magic, Blade Master answers plate — and a
	 * flat ATTACK_DAMAGE modifier was the wrong shape for the second, because it
	 * fed Bladestorm and Blade Dance through the attribute and gave the least
	 * where it was needed most (against 30 armour, +40% of a blow that keeps a
	 * fifth of itself is +40% of nothing much).
	 *
	 * <p>Resolved in front of vanilla's curve rather than inside it, the way
	 * Flense is: see {@code ColossusSlayer.bladeMasterFactor}. A real
	 * {@code armor_effectiveness} enchantment would be the vanilla instrument,
	 * but it lives on the ITEM, and this is a node — a player's own greatsword
	 * is not ours to stamp, and the stamp would follow the sword into another
	 * player's hands.
	 *
	 * <p>Why not 100%. Full penetration against a capped Colossus is a x5.0
	 * multiplier on the armour stage alone, which is the same magnitude as the
	 * bypassing damage type this reverts. Half leaves the suit worth roughly
	 * what a diamond one is worth against an unpenetrated blow — a real cut, and
	 * still a reason to wear it. */
	public static final float BLADE_MASTER_ARMOUR_IGNORE_PER_RANK = 0.25F;

	/**
	 * Blade Master, <b>against mobs only</b>: the points of NEGATIVE armour a
	 * full share of the node's ignore is worth. The share is
	 * {@link #BLADE_MASTER_ARMOUR_IGNORE_PER_RANK} x rank — 0.25 at rank 1, 0.50
	 * at rank 2 — so the bite it actually takes is 5 points at rank 1 and 10 at
	 * rank 2, and the victim's effective armour becomes
	 * <pre>
	 *   (1 - s) x armour  -  s x BLADE_MASTER_PVE_BITE_REF
	 * </pre>
	 * which can and is meant to go below zero.
	 *
	 * <h2>The problem it answers</h2>
	 * A share of the victim's armour is worth exactly nothing against a victim
	 * with no armour, and most of what a Brawler swings at is a zombie. Read
	 * strictly, Blade Master rank 2 was a dead node for the entire early game
	 * and a dead node in every cave, and only came alive against the one player
	 * on the server wearing netherite. So past zero the share keeps cutting: the
	 * blade does not stop at the last plate.
	 *
	 * <h2>Why 20, i.e. -10 at rank 2</h2>
	 * Vanilla's armour term is {@code 1 - realArmour / 25} and it is signed —
	 * feeding it a negative number amplifies instead of mitigating, on the same
	 * curve and in the same unit, which is why the bite is denominated in armour
	 * points rather than as a second multiplier. -10 armour is {@code 1 + 10/25
	 * = x1.40}: <b>+40% on a naked mob at rank 2</b>, +20% at rank 1. That is a
	 * real reason to own the node in PvE without being a general damage buff of
	 * the size the node's PvP half is worth, and it lands on the SAME arithmetic
	 * as the ignore rather than beside it, so there is one number to reason
	 * about and {@code ArmourMath} inverts both at once.
	 *
	 * <p><b>The bite is denominated per FULL share (s = 1), not per rank.</b>
	 * Halve this constant to 10.0F if the bite should be 5 points at rank 2
	 * rather than 10 — that is the only knob, and nothing else moves with it.
	 *
	 * <h2>Mobs only, and PvP untouched</h2>
	 * The gate is {@code !(victim instanceof Player)} and it is checked in
	 * {@code ColossusSlayer.bladeMasterFactor} before the effective armour is
	 * computed, so a blow landing on a player runs the pre-existing expression
	 * unchanged — the PvP arithmetic this node was tuned around is the same
	 * arithmetic to the last float operation. Armour ignore is a PvP node; this
	 * is the PvE half bolted to the same lever, and the two must not leak into
	 * each other.
	 *
	 * <p>Bounded by {@code ArmourMath}'s own floor on negative armour (-20, the
	 * mirror of vanilla's 20-point ceiling), so no future rank count or retune
	 * can drive the amplification past x1.8.
	 */
	public static final float BLADE_MASTER_PVE_BITE_REF = 20.0F;

	/**
	 * Blade Master: enchantment protection points (EPF) a greatsword hit bites
	 * off the victim, per rank. Rank 2 is 8 — exactly half of a full Protection
	 * IV suit's 16.
	 *
	 * <p>This constant exists because armour penetration alone does not answer
	 * the author's sentence. "Full enchanted netherite" is TWO mitigations:
	 * {@code CombatRules.getDamageAfterAbsorb}, which armour ignore reaches, and
	 * {@code getDamageAfterMagicAbsorb}, a flat x0.36 for Protection IV in four
	 * slots that no amount of armour ignore touches — vanilla only lets a damage
	 * type out of that stage, never a weapon or an attacker. Against ARMOR 30 /
	 * TOUGHNESS 20 the armour stage is x0.20 and Protection is x0.36; ignoring
	 * ALL of the armour still leaves the blow at 36% of itself.
	 *
	 * <p>So the node bites the EPF too, and the number is denominated in EPF
	 * points rather than a percentage so that it reads in the same unit
	 * Protection itself does: 8 points is two Protection IV pieces' worth, or
	 * the whole suit halved. It is a subtraction and not a bypass on purpose —
	 * a lightly-enchanted target loses less in absolute terms than a fully
	 * enchanted one loses, and a target with no Protection at all loses nothing,
	 * so the node is worth exactly zero against the unarmoured and cannot become
	 * a general damage buff.
	 *
	 * <p>Vanilla clamps EPF to [0, 20] before it divides by 25, so the bite is
	 * taken against the clamped value: a target stacked past 20 does not get to
	 * spend the overflow soaking this node. */
	public static final float BLADE_MASTER_PROTECTION_BITE_PER_RANK = 4.0F;

	/** Riposte: two seconds of Strength off a successful parry. */
	public static final int RIPOSTE_STRENGTH_TICKS = 40;
	/** Riposte: Strength V at rank 1, X at rank 2 — amplifier {@code 5 x rank
	 * - 1}. Two seconds is the fence: it is one follow-up swing, not a buff. */
	public static final int RIPOSTE_AMPLIFIER_STEP = 5;

	/** Stalwart: eight seconds of temporary hearts off a successful parry. */
	public static final int STALWART_TICKS = 160;
	/** Stalwart: Absorption's own step is 4 health per amplifier, i.e. exactly
	 * the author's 2 hearts a parry. */
	public static final float STALWART_ABSORPTION_STEP = 4.0F;
	/** Stalwart: amplifier 4 is 20 health — the author's 10-heart ceiling. */
	public static final int STALWART_MAX_AMPLIFIER = 4;

}
