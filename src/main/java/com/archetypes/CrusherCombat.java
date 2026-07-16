package com.archetypes;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Crusher's on-hit passives. Weapon scoping is the tree's premise: the
 * shared families serve both the mace and bare fists, with fists at double
 * effect — the compensation for bringing knuckles to a sword fight.
 */
public final class CrusherCombat {
	/** True while a Shockwave splash is being dealt, so the splash's own
	 * damage events can never cascade. */
	private static boolean splashing;

	private CrusherCombat() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, taken, blocked) -> {
			if (blocked || taken <= 0 || !(source.getDirectEntity() instanceof ServerPlayer player)) {
				return;
			}

			WeaponClass weapon = WeaponClass.of(player);

			if (weapon != WeaponClass.MACE && weapon != WeaponClass.HANDS) {
				return;
			}

			var owned = NodePurchases.owned(player, SubTree.CRUSHER);
			AttachmentTarget target = (AttachmentTarget) player;
			long now = player.level().getGameTime();

			// Adrenaline: landing blows keeps the frenzy window open; the
			// ticker turns the window into an attack-speed modifier.
			if (CrusherNodes.rank(SubTree.CRUSHER, owned, CrusherNodes.Family.ADRENALINE) > 0) {
				target.setAttached(ModAttachments.ADRENALINE_UNTIL, now + Tuning.ADRENALINE_TICKS);
			}

			// Shockwave: a falling mace blow rings outward — the same damage
			// to everything within rank blocks of the one you actually hit.
			int shockwave = CrusherNodes.rank(SubTree.CRUSHER, owned, CrusherNodes.Family.SHOCKWAVE);

			Long smashedAt = target.getAttached(ModAttachments.SMASH_AT);

			// XXX debug, remove once Shockwave is confirmed: one line per mace
			// hit with every gate value.
			if (weapon == WeaponClass.MACE) {
				Archetypes.LOGGER.info(
						"[shockwave debug] rank={} smashedAt={} now={} fresh={} splashing={} fallDistance={}",
						shockwave, smashedAt, now,
						smashedAt != null && now - smashedAt <= 3, splashing, player.fallDistance);
			}

			if (shockwave > 0 && weapon == WeaponClass.MACE && !splashing
					&& smashedAt != null && now - smashedAt <= 3) {
				int radius = shockwave * Tuning.SHOCKWAVE_RADIUS_PER_RANK;
				var level = (net.minecraft.server.level.ServerLevel) player.level();
				splashing = true;
				try {
					for (var other : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
							entity.getBoundingBox().inflate(radius, 1.0, radius),
							o -> o != player && o != entity && o.isAlive() && !o.isSpectator())) {
						other.hurtServer(level, player.damageSources().playerAttack(player), taken);
					}
				} finally {
					splashing = false;
				}
				Archetypes.LOGGER.info("[shockwave debug] splash fired, radius={}", radius);
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
						entity.getX(), entity.getY(0.3), entity.getZ(), 2, radius * 0.4, 0.1,
						radius * 0.4, 0.0);
				ProcIndicators.send(player, SubTree.CRUSHER, CrusherNodes.Family.SHOCKWAVE);
			}

			// Battle Trance: every landed hit banks absorption, capped by
			// rank, doubled for fists. The ticker drains it once idle.
			int trance = CrusherNodes.rank(SubTree.CRUSHER, owned, CrusherNodes.Family.BATTLE_TRANCE);

			if (trance > 0) {
				float gain = Tuning.TRANCE_ABSORPTION_PER_HIT * (weapon == WeaponClass.HANDS ? 2.0F : 1.0F);
				float cap = Tuning.TRANCE_CAP_PER_RANK * trance;
				player.setAbsorptionAmount(Math.min(cap,
						Math.max(player.getAbsorptionAmount(), 0.0F) + gain));
				target.setAttached(ModAttachments.TRANCE_HIT_AT, now);
				ProcIndicators.send(player, SubTree.CRUSHER, CrusherNodes.Family.BATTLE_TRANCE);
			}
		});
	}
}
