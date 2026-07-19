package com.archetypes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * The mechanism behind Lightning Strike (see {@link OracleSpells#lightningStrike}):
 * how one bolt lands, arcs to nearby hostiles (Chain Reaction), and the
 * delayed-strike scheduler that lets Recurrence read as successive bolts
 * rather than one inflated number. Scheduled strikes are transient — a restart
 * or dimension hop drops any in flight, which for sub-second follow-ups nobody
 * will miss (same stance as {@link BlizzardZones}). Damage is always ours
 * (indirect magic); the vanilla LightningBolt is spawned visual-only so it
 * never adds its own hit or fire.
 */
public final class OracleStrikes {
	/** A recurrence waiting to land: the same target, chains and damage as its
	 * opening strike, held until its level's game time reaches {@code fireTick}. */
	private record Pending(ServerLevel level, ServerPlayer caster, LivingEntity target,
			float damage, int chains, long fireTick) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();

	private OracleStrikes() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (PENDING.isEmpty()) {
				return;
			}

			for (Iterator<Pending> it = PENDING.iterator(); it.hasNext();) {
				Pending p = it.next();

				if (p.level().getGameTime() < p.fireTick()) {
					continue;
				}

				it.remove();

				// A recurrence fizzles if the caster left or the target died in
				// the gap — it never re-picks a fresh victim.
				if (p.caster().isRemoved() || !p.target().isAlive() || p.target().isRemoved()) {
					continue;
				}

				strike(p.level(), p.caster(), p.target(), p.damage(), p.chains());
			}
		});
	}

	/** Queue a follow-up strike on the same target for a later tick. */
	public static void schedule(final ServerLevel level, final ServerPlayer caster,
			final LivingEntity target, final float damage, final int chains, final long fireTick) {
		PENDING.add(new Pending(level, caster, target, damage, chains, fireTick));
	}

	/** One strike: the bolt on the target, then Chain Reaction's arcs to the
	 * nearest hostiles around it (chains never chain further). */
	public static void strike(final ServerLevel level, final ServerPlayer caster,
			final LivingEntity target, final float damage, final int chains) {
		boltHit(level, caster, target, damage);

		if (chains <= 0) {
			return;
		}

		for (LivingEntity extra : nearestHostiles(level, caster, target, chains,
				Tuning.LIGHTNING_CHAIN_RANGE)) {
			boltHit(level, caster, extra, damage);
		}
	}

	/** Our damage lands, then a purely cosmetic bolt and thunder sell it. */
	private static void boltHit(final ServerLevel level, final ServerPlayer caster,
			final LivingEntity victim, final float damage) {
		victim.hurtServer(level, level.damageSources().indirectMagic(caster, caster), damage);

		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);

		if (bolt != null) {
			// Visual-only: no vanilla damage, no fires — the 40 stays exactly ours.
			bolt.setVisualOnly(true);
			bolt.snapTo(victim.getX(), victim.getY(), victim.getZ());
			level.addFreshEntity(bolt);
		}

		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
				SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.7F, 1.2F);
	}

	/** The {@code count} nearest live hostiles within {@code range} of the
	 * struck target — Enemy-only, never the target itself or the caster. */
	private static List<LivingEntity> nearestHostiles(final ServerLevel level, final ServerPlayer caster,
			final LivingEntity around, final int count, final double range) {
		double rangeSq = range * range;
		List<LivingEntity> found = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class,
				around.getBoundingBox().inflate(range),
				e -> e != around && e != caster && e.isAlive() && e instanceof Enemy
						&& e.distanceToSqr(around) <= rangeSq));
		found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(around)));
		return found.subList(0, Math.min(count, found.size()));
	}
}
