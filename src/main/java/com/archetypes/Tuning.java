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
	public static final float FIRST_STRIKE_PER_RANK = 0.25F;
	/** Bloodrush: Strength I/II for this long, on kills while invisible. */
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
	/** The flurry capstone: one strike, three daggers' worth. */
	public static final float SHADOW_FLURRY_MULTIPLIER = 3.0F;
	/** Daggers shove half as hard as a sword would. */
	public static final float DAGGER_KNOCKBACK_FACTOR = 0.5F;

	// --- Assassin passives ---
	public static final float LIGHTFOOT_PER_RANK = 0.10F;
	public static final float SIDESTEP_PER_RANK = 0.07F;
	/** Crippling Poison: Slowness I/II riding every dagger hit, 4s. */
	public static final int CRIPPLING_SLOW_TICKS = 80;
	public static final float RAZOR_EDGE_PER_RANK = 0.08F;
	public static final float EXPOSE_PER_RANK = 0.10F;
	public static final int VENOM_TICKS = 80;
	public static final int BLIGHT_TICKS = 60;
	/** Fraction of armor's absorption clawed back per rank; rank 2 = all. */
	public static final float FLENSE_PER_RANK = 0.5F;
	/** Twin Fangs: the off-hand dagger joins the step strike at this
	 * weight — identical daggers reproduce old Deathblow's x1.5. */
	public static final float TWIN_FANGS_OFFHAND_FACTOR = 0.5F;

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

	/** Fireball and Ice Blast burst in a 3x3 (radius from impact point). */
	public static final double ELEMENT_BURST_RADIUS = 1.5;

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

	/** The empowered Cutpurse actives: Heart-piercing Shot and Stalker's Step
	 * are the base abilities' final damage times this. Invisibility is
	 * deliberately untouched (author's spec). */
	public static final float NIGHT_FORM_TRUE_SHOT_FACTOR = 1.5F;
	public static final float NIGHT_FORM_SHADOW_STEP_FACTOR = 1.5F;

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
	 * Together at rank 2 that is 1.25 x 1.5 = x1.875 on a finished base
	 * Assassin's ~26.8 Shadow Step strike, i.e. ~50 damage on one named
	 * target — the ceiling the design sets against Lightning Strike's 20
	 * hearts. Raise either and the epic Hunt line passes the Oracle actives. */
	public static final float DEATH_MARK_DAMAGE_FACTOR = 0.25F;
	public static final float HEADHUNTER_PER_RANK = 0.25F;
	/**
	 * Coup de Grace's execute threshold. Deliberately NOT
	 * {@link #EXECUTE_THRESHOLD}: the Slayer's Executioner finishes at 15% and
	 * the author's draft says 30% for this one, so reusing the constant would
	 * silently halve the node and contradict its own description.
	 */
	public static final float COUP_DE_GRACE_THRESHOLD = 0.30F;
	/** What a marked PLAYER takes instead of the execute. A guaranteed delete
	 * on a 45s clock is not a skill node (the rule Executioner already follows),
	 * so the execute pays out as a doubling against players. */
	public static final float COUP_DE_GRACE_PLAYER_MULTIPLIER = 2.0F;
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

}
