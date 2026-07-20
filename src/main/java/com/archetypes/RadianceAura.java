package com.archetypes;

import java.util.Set;

import com.archetypes.mixin.LivingEntityAccessor;

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
import net.minecraft.world.entity.player.Player;

/**
 * Aura of Radiance, the Oracle Priest's root: Holy Light leaves the caster
 * burning for ten seconds, harming the undead and healing everything friendly
 * within eight blocks, once a second. Not key-bound — there is no ability slot
 * and no cooldown; {@link SeekerSpells#castHolyLight} arms it and the price is
 * the doubled mana that cast already paid.
 *
 * <p>The aura is a pure server effect and its only persistent trace is the
 * attachment holding its end tick, which is transient by design (see
 * {@link ModAttachments#RADIANCE_END}). Nothing is ever placed in the server's
 * world: the glow is drawn client-side from that same stamp (see
 * {@code RadianceLight}), and the halo is particles.
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

	/**
	 * Whether this player is wreathed in light right now. Safe on either side
	 * and on any {@link Player}: {@code RADIANCE_END} syncs to every client, so
	 * this is the whole contract the client-side glow reads.
	 */
	public static boolean isActive(final Player player) {
		Long end = ((AttachmentTarget) player).getAttached(ModAttachments.RADIANCE_END);
		return end != null && player.level().getGameTime() < end;
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
		// +1, the pad BlizzardZones needs for the same reason: the cast runs
		// from the packet queue, so by the first tick-end this ticker observes
		// game time may already have advanced once. Without the pad the head
		// pulse (remaining == ticks) could be missed and the aura would deliver
		// one pulse short of the advertised total. With it the observed
		// remaining runs ticks..1 or ticks+1..1 and both yield exactly
		// ticks/RADIANCE_PULSE_TICKS pulses.
		((AttachmentTarget) player).setAttached(ModAttachments.RADIANCE_END,
				player.level().getGameTime() + ticks + 1);
		RadianceEffect.show(player, ticks);

		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 1.6F);
	}

	/**
	 * Drop the aura and everything it holds. The ticker cannot be relied on for
	 * this: it only runs for players whose archetype is still INTELLECT, so a
	 * respec that drops the archetype (Amnesia II, the creative reset) would
	 * strand a live Steadfast modifier until the next relog. Safe on a player
	 * with no aura.
	 */
	public static void end(final ServerPlayer player) {
		((AttachmentTarget) player).removeAttached(ModAttachments.RADIANCE_END);
		steadfast(player, false);
		RadianceEffect.hide(player);
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
				// The badge outlives the stamp only when the aura is cut short
				// (a respec mid-aura); on a natural lapse its own duration has
				// already run out and this is a no-op.
				RadianceEffect.hide(player);
			}

			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		long remaining = end - now;

		if (remaining % Tuning.RADIANCE_PULSE_TICKS == 0) {
			// Damage and healing pulse four times a second; the status effects
			// stay on the old once-a-second beat. Re-laying a 40-tick Weakness
			// four times a second would resync the whole effect to every
			// tracking client four times a second for no visible gain.
			pulse(level, player, owned, remaining % 20 == 0);
		}

		if (remaining % Tuning.RADIANCE_HALO_PERIOD_TICKS == 0) {
			halo(level, player);
		}
	}

	/**
	 * One pulse of aura — a RADIANCE_PULSE_TICKS share of a second's worth.
	 * The undead branch comes first and is exhaustive for the undead, exactly
	 * as Holy Light's own burst resolves them, so a non-hostile undead (a
	 * skeleton horse) is burned rather than healed.
	 */
	private static void pulse(final ServerLevel level, final ServerPlayer player,
			final Set<Integer> owned, final boolean layEffects) {
		int brilliance = OraclePriestNodes.rank(SubTree.ORACLE_PRIEST, owned,
				OraclePriestNodes.Family.BRILLIANCE);
		float perSecond = switch (brilliance) {
			case 0 -> Tuning.RADIANCE_AURA_AMOUNT;
			case 1 -> Tuning.RADIANCE_BRILLIANCE_AMOUNT_RANK_1;
			default -> Tuning.RADIANCE_BRILLIANCE_AMOUNT_RANK_2;
		};
		// The Tuning numbers are per second; a pulse pays its share. The
		// duration constants are whole multiples of the period, so the shares
		// add back up to the advertised total exactly.
		float amount = perSecond * Tuning.RADIANCE_PULSE_TICKS / 20.0F;
		boolean blinding = layEffects && OraclePriestNodes.rank(SubTree.ORACLE_PRIEST, owned,
				OraclePriestNodes.Family.BLINDING_LIGHT) > 0;
		double radiusSq = Tuning.RADIANCE_AURA_RADIUS * Tuning.RADIANCE_AURA_RADIUS;

		pulsing = true;

		try {
			for (LivingEntity creature : level.getEntitiesOfClass(LivingEntity.class,
					player.getBoundingBox().inflate(Tuning.RADIANCE_AURA_RADIUS),
					living -> living.isAlive() && living.distanceToSqr(player) <= radiusSq)) {
				if (creature.isInvertedHealAndHarm()) {
					hurt(level, creature, player, amount);

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

		if (layEffects && OraclePriestNodes.rank(SubTree.ORACLE_PRIEST, owned,
				OraclePriestNodes.Family.RETRIBUTION) > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.STRENGTH,
					Tuning.RADIANCE_EFFECT_TICKS, Tuning.RADIANCE_EFFECT_AMPLIFIER));
			player.addEffect(new MobEffectInstance(MobEffects.SPEED,
					Tuning.RADIANCE_EFFECT_TICKS, Tuning.RADIANCE_EFFECT_AMPLIFIER));
		}
	}

	/**
	 * One victim's share of a pulse.
	 *
	 * <p>{@code LivingEntity.hurtServer} swallows a repeat outright while
	 * {@code invulnerableTime > 10} and the new damage is no larger than
	 * {@code lastHurt} — at a five-tick cadence that is every pulse after the
	 * first, so a naive call would pay out a quarter of the advertised rate.
	 * The two ways out are both wrong: a BYPASSES_COOLDOWN damage type still
	 * stamps {@code invulnerableTime = 20} on the way through, which would keep
	 * every mob in the aura permanently inside a damage cooldown and quietly
	 * dock the caster's own sword hits; simply zeroing the counter and walking
	 * away would hand out free i-frames.
	 *
	 * <p>So the counter and {@code lastHurt} are saved, zeroed for the length
	 * of exactly one call, and put back. The pulse lands in full and the
	 * victim's cooldown state afterwards is bit-for-bit what it would have been
	 * had the aura not touched it — no other damage source sees the aura at
	 * all. Knockback is already suppressed for the duration via
	 * {@link #isPulsing}.
	 */
	private static void hurt(final ServerLevel level, final LivingEntity victim,
			final ServerPlayer player, final float amount) {
		LivingEntityAccessor accessor = (LivingEntityAccessor) victim;
		int invulnerable = victim.invulnerableTime;
		float lastHurt = accessor.archetypes$getLastHurt();

		victim.invulnerableTime = 0;
		accessor.archetypes$setLastHurt(0.0F);

		try {
			victim.hurtServer(level, level.damageSources().indirectMagic(player, player), amount);
		} finally {
			victim.invulnerableTime = invulnerable;
			accessor.archetypes$setLastHurt(lastHurt);
		}
	}

	/**
	 * The aura's only visual: a turning rim of holy motes at the eight-block
	 * edge and a column of sparks on the caster.
	 *
	 * <p>The real light the sketch asked for is NOT here and is not the
	 * server's business: planting {@code minecraft:light} in a ServerLevel
	 * cannot be made airtight, because a chunk that saves while the aura is up
	 * and a process that dies before the cleanup runs leave an invisible light
	 * block in the saved world with no way to find it again. The glow is drawn
	 * entirely inside the client's own copy of the level instead — see
	 * {@code RadianceLight} — which is thrown away at disconnect and never
	 * written to disk.
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
