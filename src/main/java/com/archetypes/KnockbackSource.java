package com.archetypes;

// THIS WHOLE FILE EXISTS ONLY BELOW 26.2, and on 26.2 it compiles to nothing at all — the
// package statement is the entire compilation unit there, so the 26.2 jar is unchanged.
//
// What it is for. 26.2 threads the DamageSource through the knockback path:
//
//     26.2   LivingEntity.knockback(double, double, double, DamageSource, float, boolean)
//            LivingEntity.knockback(double, double, double, DamageSource, float)
//     26.1   LivingEntity.knockback(double, double, double)          <- and nothing else
//
// `archetypes$daggerKnockback` READS that source — Blizzard/Rend/Radiance pulses shove
// nothing, Clinch and daggers shove less, Magic Missiles shove less and the Flamethrower
// not at all — so below 26.2 it cannot be ported by renaming (design §6 R-B4). This is the
// re-rooting the design prescribes: carry the source to the injection point out of band.
//
// THE CALLER CENSUS (design's E-KB-1, run before the wrap sites were chosen; method is
// R-20's — a constant-pool scan of every class in the mojmap common jar of BOTH versions,
// not a guess). Every site that reaches LivingEntity.knockback, with the source 26.2
// passes and where that same source lives on 26.1:
//
//   site (26.1 spelling)                          26.2 source        on 26.1
//   ------------------------------------------------------------------------------------
//   LivingEntity.hurtServer -> knockback           the hit's source   IN SCOPE (param)
//   LivingEntity.applyItemBlocking -> blockUsingItem -> blockedByItem -> knockback
//                                                  the hit's source   IN SCOPE at
//                                                                     applyItemBlocking
//   Player.attack -> causeExtraKnockback -> knockback
//                                                  the attack source  IN SCOPE (local 4,
//                                                                     `damageSource`,
//                                                                     live 44..351)
//   Player.doSweepAttack -> knockback              the attack source  in scope (param)
//   Mob.doHurtTarget / LivingEntity.stabAttack / Player.stabAttack /
//     ChargeAttack.dealKnockBack / RamTarget.tick  a mob's source     NOWHERE
//
// The first three are wrapped (LivingEntityMixin x2, PlayerMixin x1) and that is the whole
// of what the mod's five knockback behaviours can reach with a PLAYER source. The last two
// rows are NOT wrapped and the divergence is stated rather than hidden:
//
//   * Player.doSweepAttack — vanilla only sweeps when the held item has a sweeping damage
//     ratio, so neither a dagger nor a bare fist (Clinch) nor a spell ever gets here. The
//     only handler that could still fire is one of the three global `isPulsing()` flags.
//   * the mob-AI rows carry a MOB's damage source, which none of the five behaviours keys
//     on; again only a global `isPulsing()` flag could differ, and only if the pulse and
//     the goat happened to land in the same tick.
//
// Both residues are logged as a Stage-3 open item, because 1.21.11 / 1.21.1 / 1.20.1 land
// on the same `knockback(DDD)` shape and should settle it once for all four legacy nodes.
//
// Re-entrancy: push/pop is save-and-restore, not set/clear, and every caller uses
// try/finally — a thorns hit nested inside a hurtServer restores the outer source instead
// of blanking it. Single-threaded by construction: every writer is on the server thread.
//? if <26.2 {
/*import net.minecraft.world.damagesource.DamageSource;

public final class KnockbackSource {
	private static DamageSource current;

	private KnockbackSource() {
	}

	// The source of the knockback being applied right now, or null when the shove has no
	// damage behind it. Null is exactly what 26.2 hands the same handler for a sourceless
	// shove, so the impl's own `source == null` guard needs no version fork.
	public static DamageSource current() {
		return current;
	}

	public static DamageSource push(final DamageSource source) {
		DamageSource previous = current;
		current = source;
		return previous;
	}

	public static void pop(final DamageSource previous) {
		current = previous;
	}
}
*///?}
