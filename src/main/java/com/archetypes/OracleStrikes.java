package com.archetypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * The mechanism behind Lightning Strike (see {@link OracleSpells#lightningStrike}):
 * how one bolt lands, arcs onward to nearby hostiles (Chain Reaction), and the
 * delayed-strike scheduler that lets Recurrence read as successive bolts
 * rather than one inflated number. Scheduled work is transient — a restart
 * or dimension hop drops anything in flight, which for sub-second follow-ups
 * nobody will miss (same stance as {@link BlizzardZones}). Damage is always
 * ours (indirect magic); the vanilla LightningBolt is spawned visual-only so it
 * never adds its own hit or fire.
 *
 * <p>Presentation contract: only the <em>primary</em> target gets a sky bolt.
 * Chain victims are reached by an arc drawn from the previous victim, one hop
 * per {@link Tuning#LIGHTNING_CHAIN_HOP_DELAY_TICKS}, so the cast reads as
 * lightning jumping down a line rather than a simultaneous multi-strike.
 */
public final class OracleStrikes {
	/** Work queued for a later tick, keyed off its level's game time. */
	private sealed interface Pending permits Recurrence, Hop {
		ServerLevel level();

		long fireTick();
	}

	/** A recurrence waiting to land: the same target, chains and damage as its
	 * opening strike, held until its level's game time reaches {@code fireTick}. */
	private record Recurrence(ServerLevel level, ServerPlayer caster, LivingEntity target,
			float damage, int chains, long fireTick) implements Pending {
	}

	/** One chain hop in flight. The next victim is chosen when the hop
	 * <em>lands</em>, searching from {@code from}'s position at that moment, so
	 * the arc follows a moving horde. {@code struck} is the running set of
	 * everything this chain has already hit, so it never doubles back. */
	private record Hop(ServerLevel level, ServerPlayer caster, LivingEntity from, float damage,
			int remaining, Set<LivingEntity> struck, long fireTick) implements Pending {
	}

	private static final List<Pending> PENDING = new ArrayList<>();

	private OracleStrikes() {
	}

	public static void initialize() {
		// The list is static but a schedule is per-server: entries must not
		// survive into the next singleplayer world and fire on a stale level.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING
				.register(server -> PENDING.clear());

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (PENDING.isEmpty()) {
				return;
			}

			// A landing hop appends its successor, so this tick's due work must
			// be drained before any of it runs.
			List<Pending> due = new ArrayList<>();

			for (Iterator<Pending> it = PENDING.iterator(); it.hasNext();) {
				Pending p = it.next();

				if (p.level().getGameTime() >= p.fireTick()) {
					it.remove();
					due.add(p);
				}
			}

			for (Pending p : due) {
				if (p instanceof Recurrence r) {
					fire(r);
				} else if (p instanceof Hop h) {
					fire(h);
				}
			}
		});
	}

	/** Queue a follow-up strike on the same target for a later tick. */
	public static void schedule(final ServerLevel level, final ServerPlayer caster,
			final LivingEntity target, final float damage, final int chains, final long fireTick) {
		PENDING.add(new Recurrence(level, caster, target, damage, chains, fireTick));
	}

	/** One strike: the sky bolt on the primary target, then Chain Reaction's
	 * first hop, which walks victim to victim from there. */
	public static void strike(final ServerLevel level, final ServerPlayer caster,
			final LivingEntity target, final float damage, final int chains) {
		skyStrike(level, caster, target, damage);

		if (chains <= 0) {
			return;
		}

		// Identity, not equality: two distinct mobs must never collapse into
		// one entry of the already-struck set.
		Set<LivingEntity> struck = Collections.newSetFromMap(new IdentityHashMap<>());
		struck.add(target);
		PENDING.add(new Hop(level, caster, target, damage, chains, struck,
				level.getGameTime() + Tuning.LIGHTNING_CHAIN_HOP_DELAY_TICKS));
	}

	/** A recurrence fizzles if the caster left or the target died in the gap —
	 * it never re-picks a fresh victim. */
	private static void fire(final Recurrence r) {
		if (r.caster().isRemoved() || !r.target().isAlive() || r.target().isRemoved()) {
			return;
		}

		strike(r.level(), r.caster(), r.target(), r.damage(), r.chains());
	}

	/** A hop lands: pick the nearest untouched hostile around its source, draw
	 * the arc, hit, then queue the next hop from the new victim. The chain ends
	 * quietly if the caster left or nothing new is in reach. */
	private static void fire(final Hop h) {
		if (h.caster().isRemoved()) {
			return;
		}

		LivingEntity next = nearestHostile(h.level(), h.caster(), h.from(), h.struck(),
				Tuning.LIGHTNING_CHAIN_RANGE);

		if (next == null) {
			return;
		}

		// The source may have died to the previous hit; its position is still
		// where the arc leaves from.
		drawArc(h.level(), h.from().getEyePosition(), next.getEyePosition());
		chainHit(h.level(), h.caster(), next, h.damage());
		h.struck().add(next);

		if (h.remaining() > 1) {
			PENDING.add(new Hop(h.level(), h.caster(), next, h.damage(), h.remaining() - 1,
					h.struck(), h.level().getGameTime() + Tuning.LIGHTNING_CHAIN_HOP_DELAY_TICKS));
		}
	}

	/** The primary target only: our damage lands, then a cosmetic sky bolt and
	 * thunder sell it. */
	private static void skyStrike(final ServerLevel level, final ServerPlayer caster,
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

	/** A chain arrival: same damage, no sky bolt — the incoming arc and a local
	 * crackle are the whole tell. */
	private static void chainHit(final ServerLevel level, final ServerPlayer caster,
			final LivingEntity victim, final float damage) {
		victim.hurtServer(level, level.damageSources().indirectMagic(caster, caster), damage);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, victim.getX(),
				victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 8, 0.2, 0.3, 0.2, 0.05);
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
				SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6F, 1.8F);
	}

	/** Spark particles along {@code a}→{@code b}, each one kicked off the line
	 * on the two axes perpendicular to it so the run reads as a jagged bolt
	 * rather than a beam. The kick tapers to zero at both ends so the arc still
	 * starts and finishes on its entities. */
	private static void drawArc(final ServerLevel level, final Vec3 a, final Vec3 b) {
		Vec3 delta = b.subtract(a);
		double length = delta.length();

		if (length < 1.0E-4) {
			return;
		}

		int segments = Mth.clamp((int) Math.ceil(length * Tuning.LIGHTNING_ARC_SEGMENTS_PER_BLOCK),
				2, Tuning.LIGHTNING_ARC_MAX_SEGMENTS);
		Vec3 dir = delta.scale(1.0 / length);
		// Any vector not parallel to dir seeds the perpendicular basis; a near
		// vertical hop needs the non-Y seed or the cross product degenerates.
		Vec3 seed = Math.abs(dir.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
		Vec3 side = dir.cross(seed).normalize();
		Vec3 up = dir.cross(side).normalize();
		RandomSource random = level.getRandom();

		for (int i = 0; i <= segments; i++) {
			double t = (double) i / segments;
			double kick = Math.sin(Math.PI * t) * Tuning.LIGHTNING_ARC_JITTER;
			Vec3 p = a.add(delta.scale(t))
					.add(side.scale((random.nextDouble() * 2.0 - 1.0) * kick))
					.add(up.scale((random.nextDouble() * 2.0 - 1.0) * kick));
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z,
					Tuning.LIGHTNING_ARC_PARTICLES_PER_SEGMENT, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** The nearest live hostile within {@code range} of {@code around} that this
	 * chain has not hit yet — Enemy-only, never the caster. */
	private static LivingEntity nearestHostile(final ServerLevel level, final ServerPlayer caster,
			final LivingEntity around, final Set<LivingEntity> struck, final double range) {
		double rangeSq = range * range;
		List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class,
				around.getBoundingBox().inflate(range),
				e -> e != caster && e.isAlive() && e instanceof Enemy && !struck.contains(e)
						&& e.distanceToSqr(around) <= rangeSq);

		return found.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(around)))
				.orElse(null);
	}
}
