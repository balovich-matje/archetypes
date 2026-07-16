package com.archetypes;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Crusher's on-hit passives. Called from the damage-shaping mixin, NOT
 * from AFTER_DAMAGE: Fabric gates that event on the victim not dying, so a
 * one-shot smash — the whole point of a Meteor/Density build — would never
 * proc anything. The shaping hook runs before death resolution and knows the
 * exact damage dealt.
 *
 * <p>Weapon scoping is the tree's premise: the shared families serve both the
 * mace and bare fists, with fists at double effect.
 */
public final class CrusherCombat {
	/** True while a Shockwave splash is being dealt, so the splash's own hits
	 * can never proc a second wave or bank extra trance. */
	private static boolean splashing;

	private CrusherCombat() {
	}

	/** Everything that procs on a landed mace/fist hit. {@code damage} is the
	 * shaped amount the victim is about to take. */
	public static void onCrusherHit(final ServerPlayer player, final LivingEntity victim,
			final ServerLevel level, final float damage, final WeaponClass weapon,
			final java.util.Set<Integer> owned, final boolean smashing) {
		if (splashing) {
			return;
		}

		AttachmentTarget target = (AttachmentTarget) player;
		long now = level.getGameTime();

		// Adrenaline: landing blows keeps the frenzy window open; the ticker
		// turns the window into an attack-speed modifier.
		if (CrusherNodes.rank(SubTree.CRUSHER, owned, CrusherNodes.Family.ADRENALINE) > 0) {
			target.setAttached(ModAttachments.ADRENALINE_UNTIL, now + Tuning.ADRENALINE_TICKS);
		}

		// Shockwave: a falling mace blow rings outward — the same damage to
		// everything within 2 blocks per rank of the one actually struck.
		int shockwave = CrusherNodes.rank(SubTree.CRUSHER, owned, CrusherNodes.Family.SHOCKWAVE);

		if (shockwave > 0 && weapon == WeaponClass.MACE && smashing) {
			int radius = shockwave * Tuning.SHOCKWAVE_RADIUS_PER_RANK;
			splashing = true;
			try {
				for (var other : level.getEntitiesOfClass(LivingEntity.class,
						victim.getBoundingBox().inflate(radius, 1.0, radius),
						o -> o != player && o != victim && o.isAlive() && !o.isSpectator())) {
					other.hurtServer(level, player.damageSources().playerAttack(player), damage);
				}
			} finally {
				splashing = false;
			}
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
					victim.getX(), victim.getY(0.3), victim.getZ(), 2, radius * 0.4, 0.1,
					radius * 0.4, 0.0);
			ProcIndicators.send(player, SubTree.CRUSHER, CrusherNodes.Family.SHOCKWAVE);
		}

		// Battle Trance: every landed hit banks absorption, capped by rank
		// (the cap lives in the MAX_ABSORPTION attribute, see CrusherTicker),
		// doubled for fists. The ticker drains it once the fight goes quiet.
		int trance = CrusherNodes.rank(SubTree.CRUSHER, owned, CrusherNodes.Family.BATTLE_TRANCE);

		if (trance > 0) {
			float gain = Tuning.TRANCE_ABSORPTION_PER_HIT * (weapon == WeaponClass.HANDS ? 2.0F : 1.0F);
			float cap = Tuning.TRANCE_CAP_PER_RANK * trance;
			player.setAbsorptionAmount(Math.min(cap,
					Math.max(player.getAbsorptionAmount(), 0.0F) + gain));
			target.setAttached(ModAttachments.TRANCE_HIT_AT, now);
			ProcIndicators.send(player, SubTree.CRUSHER, CrusherNodes.Family.BATTLE_TRANCE);
		}
	}
}
