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

	/** Decimate: double damage, 30s cooldown, a wide tilted arc in front.
	 * Only instant-break blocks (torches, grass, fire...) are swept away —
	 * anything sturdier survives, so a swing in your own base clears clutter
	 * without eating the walls. */
	public static final float DECIMATE_DAMAGE_MULTIPLIER = 2.0F;
	public static final int DECIMATE_COOLDOWN_TICKS = 600;
	public static final double DECIMATE_RANGE = 3.5;
	public static final int DECIMATE_MAX_BLOCKS = 48;

	// --- Crusher ---

	/** Adrenaline: landing a mace or fist hit grants attack speed for a few
	 * seconds — per rank, doubled for bare fists. */
	public static final float ADRENALINE_SPEED_PER_RANK = 0.05F;
	public static final int ADRENALINE_TICKS = 60;

	/** Sunder: virtual Breach levels (rank for mace, doubled for fists). Each
	 * level claws back 15% of what the victim's armor absorbed — approximated
	 * as bonus damage from the armor's ~4%/point reduction, additive with the
	 * real Breach enchantment. */
	public static final float SUNDER_PER_LEVEL = 0.15F;

	/** Bare-Knuckle: unarmed damage per rank. Four ranks take the 1.0 fist to
	 * 3.0 — with the fist's 4/s recovery and hurt-invulnerability capping real
	 * exchanges, sustained damage lands near an iron sword's. */
	public static final float BARE_KNUCKLE_PER_RANK = 0.5F;

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
	public static final float EARTH_SHATTER_REFUND_PER_RANK = 0.33F;
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
	public static final float DIM_PRESENCE_PER_RANK = 0.15F;
	public static final float FIRST_STRIKE_PER_RANK = 0.30F;
	/** Bloodrush: Strength I/II for this long, on kills while invisible. */
	public static final int BLOODRUSH_TICKS = 80;
	public static final float REAPER_HEAL = 4.0F;
	public static final float STILLNESS_DURATION_PER_RANK = 0.5F;
	public static final double UMBRAL_SIGHT_RADIUS = 8.0;
	/** Kept above 210 so vanilla's low-timer flicker never shows. */
	public static final int NIGHT_EYES_TICKS = 250;

	// --- Agility: Shadow Step ---
	public static final double SHADOW_STEP_RANGE = 16.0;
	public static final int SHADOW_STEP_COOLDOWN_TICKS = 300;
	public static final int SHADOW_STEP_FLURRY_COOLDOWN_TICKS = 600;
	/** The flurry capstone: one strike, three daggers' worth. */
	public static final float SHADOW_FLURRY_MULTIPLIER = 3.0F;
	/** Daggers shove half as hard as a sword would. */
	public static final float DAGGER_KNOCKBACK_FACTOR = 0.5F;

	// --- Assassin passives ---
	public static final float LIGHTFOOT_PER_RANK = 0.10F;
	public static final float SIDESTEP_PER_RANK = 0.07F;
	public static final int ADRENALINE_RUSH_TICKS = 60;
	public static final int OPPORTUNIST_REFUND_TICKS = 60;
	public static final float RAZOR_EDGE_PER_RANK = 0.08F;
	public static final float EXPOSE_PER_RANK = 0.10F;
	public static final int VENOM_TICKS = 80;
	public static final int BLIGHT_TICKS = 60;
	/** Fraction of armor's absorption clawed back per rank; rank 2 = all. */
	public static final float FLENSE_PER_RANK = 0.5F;
	public static final float DEATHBLOW_MULTIPLIER = 1.5F;

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
	/** Impact numbers scale with every point of mana poured in. */
	public static final float METEOR_DAMAGE_PER_MANA = 0.1F;
	public static final float METEOR_RADIUS_BASE = 3.0F;
	public static final float METEOR_RADIUS_PER_EXTRA_MANA = 0.02F;
	public static final double METEOR_TARGET_RANGE = 32.0;
	public static final float FLAME_START_COST = 50.0F;
	/** 25 mana/second, paid per channel tick. */
	public static final float FLAME_COST_PER_TICK = 1.25F;
	public static final float FLAME_BOLT_DAMAGE = 2.0F;
	public static final int FLAME_BOLT_FIRE_SECONDS = 3;
	public static final float FLAME_BOLT_SPEED = 1.2F;
	public static final int FLAME_BOLT_PERIOD_TICKS = 2;

	// --- Wands (see ModItems; every spell needs one in the main hand) ---
	public static final float WAND_APPRENTICE_DISCOUNT = 10.0F;
	public static final float WAND_SPECIALIST_DISCOUNT = 15.0F;
	public static final float WAND_SPECIALIST_POWER = 1.5F;
	public static final float WAND_HOLY_HEAL_FACTOR = 1.5F;

	// --- Elementalist: ice + element passives ---
	public static final float ICE_BLAST_COST = 50.0F;
	public static final float ICE_BLAST_DAMAGE = 4.0F;
	public static final float ICE_BLAST_SPEED = 1.2F;
	/** Slowness III for 4s at base; Frostbite adds a level and a second. */
	public static final int ICE_SLOW_AMP = 2;
	public static final int ICE_SLOW_TICKS = 80;
	public static final float SNOW_BOLT_DAMAGE = 2.0F;
	public static final int SNOW_BOLT_SLOW_TICKS = 40;
	public static final float GLACIAL_MULTIPLIER = 2.5F;
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

	// --- Wizard tree ---
	/** Fraction of incoming damage per rank the shield converts to mana. */
	public static final float MANA_SHIELD_ABSORB_PER_RANK = 0.25F;
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
	public static final float VITALITY_HEALTH_PER_RANK = 2.0F;
	public static final int AEGIS_TICKS = 600;
	public static final float MIRACLE_CHANCE_PER_RANK = 0.10F;
	public static final float ASCENDANT_FACTOR = 1.25F;

	// --- Seeker: Holy Light ---
	public static final float HOLY_COST = 50.0F;
	public static final float HOLY_AMOUNT = 5.0F;
	public static final double HOLY_RADIUS = 4.0;
	public static final float HOLY_SPEED = 0.8F;
	public static final int HOLY_EFFECT_TICKS = 200;

}
