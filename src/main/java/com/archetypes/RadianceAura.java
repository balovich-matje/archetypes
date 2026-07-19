package com.archetypes;

import java.util.Set;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Aura of Radiance, the Oracle Priest's root: Holy Light leaves the caster
 * burning for ten seconds, harming the undead and healing everything friendly
 * within eight blocks, once a second. Not key-bound — there is no ability slot
 * and no cooldown; {@link SeekerSpells#castHolyLight} arms it and the price is
 * the doubled mana that cast already paid.
 *
 * <p>The aura is a pure server effect and its only persistent trace is the
 * attachment holding its end tick, which is transient by design (see
 * {@link ModAttachments#RADIANCE_END}). Nothing is placed in the world: see the
 * lighting note on {@link #halo}.
 */
public final class RadianceAura {
	private static final Identifier STEADFAST_ID = Archetypes.id("radiance_steadfast");
	/** The Priest's palette, so the aura reads as the same light Holy Light
	 * throws (SpellProjectile's HOLY_DUST is the identical colour). */
	private static final DustParticleOptions HALO_DUST = new DustParticleOptions(0xFFD75E, 1.0F);

	/** True only while a pulse's damage is being dealt — the knockback mixin
	 * reads it to keep the aura from shoving the undead out of its own radius
	 * (author's explicit spec). Server-thread synchronous, so a plain flag is
	 * enough; the same shape BlizzardZones uses. */
	private static boolean pulsing;

	private RadianceAura() {
	}

	public static boolean isPulsing() {
		return pulsing;
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (ModAttachments.get(player) == Archetype.INTELLECT) {
					tick(player);
				}
			}
		});
	}

	/**
	 * Arm the aura. Called at the end of a Holy Light cast that has already
	 * been paid for; re-casting refreshes the timer rather than stacking, so
	 * the aura's strength never depends on how fast the caster spams.
	 */
	public static void begin(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.ORACLE_PRIEST);

		if (OraclePriestNodes.rank(SubTree.ORACLE_PRIEST, owned,
				OraclePriestNodes.Family.AURA_OF_RADIANCE) <= 0) {
			return;
		}

		int ticks = OraclePriestNodes.rank(SubTree.ORACLE_PRIEST, owned,
				OraclePriestNodes.Family.BEACON_OF_LIGHT) > 0
						? Tuning.RADIANCE_BEACON_TICKS : Tuning.RADIANCE_AURA_TICKS;
		((AttachmentTarget) player).setAttached(ModAttachments.RADIANCE_END,
				player.level().getGameTime() + ticks);

		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 1.6F);
	}

	private static void tick(final ServerPlayer player) {
		AttachmentTarget target = (AttachmentTarget) player;
		Long end = target.getAttached(ModAttachments.RADIANCE_END);
		long now = player.level().getGameTime();
		Set<Integer> owned = NodePurchases.owned(player, SubTree.ORACLE_PRIEST);
		// A respec mid-aura takes the aura with it, so a refunded node cannot
		// keep burning until its timer happens to run out.
		boolean active = end != null && end > now
				&& OraclePriestNodes.rank(SubTree.ORACLE_PRIEST, owned,
						OraclePriestNodes.Family.AURA_OF_RADIANCE) > 0;

		// Steadfast is asserted and revoked from the same call every tick, so
		// there is no path — aura expiring, node refunded, archetype dropped —
		// that leaves the immunity behind.
		steadfast(player, active && OraclePriestNodes.rank(SubTree.ORACLE_PRIEST, owned,
				OraclePriestNodes.Family.STEADFAST) > 0);

		if (!active) {
			if (end != null) {
				target.removeAttached(ModAttachments.RADIANCE_END);
			}

			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		long remaining = end - now;

		if (remaining % Tuning.RADIANCE_PULSE_TICKS == 0) {
			pulse(level, player, owned);
		}

		if (remaining % Tuning.RADIANCE_HALO_PERIOD_TICKS == 0) {
			halo(level, player);
		}
	}

	/**
	 * One second of aura. The undead branch comes first and is exhaustive for
	 * the undead, exactly as Holy Light's own burst resolves them, so a
	 * non-hostile undead (a skeleton horse) is burned rather than healed.
	 */
	private static void pulse(final ServerLevel level, final ServerPlayer player, final Set<Integer> owned) {
		int brilliance = OraclePriestNodes.rank(SubTree.ORACLE_PRIEST, owned,
				OraclePriestNodes.Family.BRILLIANCE);
		float amount = switch (brilliance) {
			case 0 -> Tuning.RADIANCE_AURA_AMOUNT;
			case 1 -> Tuning.RADIANCE_BRILLIANCE_AMOUNT_RANK_1;
			default -> Tuning.RADIANCE_BRILLIANCE_AMOUNT_RANK_2;
		};
		boolean blinding = OraclePriestNodes.rank(SubTree.ORACLE_PRIEST, owned,
				OraclePriestNodes.Family.BLINDING_LIGHT) > 0;
		double radiusSq = Tuning.RADIANCE_AURA_RADIUS * Tuning.RADIANCE_AURA_RADIUS;

		pulsing = true;

		try {
			for (LivingEntity creature : level.getEntitiesOfClass(LivingEntity.class,
					player.getBoundingBox().inflate(Tuning.RADIANCE_AURA_RADIUS),
					living -> living.isAlive() && living.distanceToSqr(player) <= radiusSq)) {
				if (creature.isInvertedHealAndHarm()) {
					creature.hurtServer(level,
							level.damageSources().indirectMagic(player, player), amount);

					if (blinding && creature.isAlive()) {
						creature.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
								Tuning.RADIANCE_EFFECT_TICKS, Tuning.RADIANCE_EFFECT_AMPLIFIER));
						creature.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
								Tuning.RADIANCE_EFFECT_TICKS, Tuning.RADIANCE_EFFECT_AMPLIFIER));
					}
				} else if (!(creature instanceof Enemy)) {
					// Friendly means anything not hostile — other players and
					// tamed animals included, and the caster standing at the
					// centre of their own aura.
					creature.heal(amount);
				}
			}
		} finally {
			pulsing = false;
		}

		if (OraclePriestNodes.rank(SubTree.ORACLE_PRIEST, owned,
				OraclePriestNodes.Family.RETRIBUTION) > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.STRENGTH,
					Tuning.RADIANCE_EFFECT_TICKS, Tuning.RADIANCE_EFFECT_AMPLIFIER));
			player.addEffect(new MobEffectInstance(MobEffects.SPEED,
					Tuning.RADIANCE_EFFECT_TICKS, Tuning.RADIANCE_EFFECT_AMPLIFIER));
		}
	}

	/**
	 * The aura's only visual: a turning rim of holy motes at the eight-block
	 * edge and a column of sparks on the caster.
	 *
	 * <p>The sketch asked for real glowstone-strength light on the player.
	 * Vanilla has no dynamic entity lighting, and the only way to fake it
	 * server-side is to plant and chase {@code minecraft:light} blocks — which
	 * cannot be made airtight: a chunk that saves while the aura is up and a
	 * process that dies before the cleanup runs leave an invisible light block
	 * in the player's world with no way to find it again. Particles cost
	 * nothing and can leave nothing behind, so the aura is sold with light
	 * rather than lit.
	 */
	private static void halo(final ServerLevel level, final ServerPlayer player) {
		double turn = (level.getGameTime() % Tuning.RADIANCE_HALO_TURN_TICKS)
				/ (double) Tuning.RADIANCE_HALO_TURN_TICKS * Math.PI * 2.0;

		for (int i = 0; i < Tuning.RADIANCE_HALO_POINTS; i++) {
			double angle = turn + i * (Math.PI * 2.0 / Tuning.RADIANCE_HALO_POINTS);
			level.sendParticles(HALO_DUST,
					player.getX() + Math.cos(angle) * Tuning.RADIANCE_AURA_RADIUS,
					player.getY() + 0.3,
					player.getZ() + Math.sin(angle) * Tuning.RADIANCE_AURA_RADIUS,
					1, 0.0, 0.4, 0.0, 0.0);
		}

		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(),
				2, 0.35, 0.6, 0.35, 0.01);
	}

	/** Steadfast's knockback immunity, held exactly while the aura runs.
	 * KNOCKBACK_RESISTANCE is clamped to 0..1, so a full point is immunity and
	 * cannot overshoot into a shove of its own. */
	private static void steadfast(final ServerPlayer player, final boolean should) {
		AttributeInstance attribute = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);

		if (attribute == null) {
			return;
		}

		if (should && !attribute.hasModifier(STEADFAST_ID)) {
			attribute.addTransientModifier(new AttributeModifier(STEADFAST_ID, 1.0,
					AttributeModifier.Operation.ADD_VALUE));
		} else if (!should && attribute.hasModifier(STEADFAST_ID)) {
			attribute.removeModifier(STEADFAST_ID);
		}
	}
}
