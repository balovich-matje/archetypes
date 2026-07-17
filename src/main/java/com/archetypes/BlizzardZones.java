package com.archetypes;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The reworked Blizzard capstone: no longer a channel — a storm called down
 * on the targeted ground that rakes its 5x5 with icicles for eight seconds
 * while the caster does something else (the Meteorite's AOE opposite; user
 * spec, WoW-mage style). One storm per caster; recasting moves it. Zones
 * are transient on purpose — a restart, logout or dimension hop clears the
 * weather, which for a seconds-long spell nobody will miss.
 */
public final class BlizzardZones {
	/** Ice crumbs falling fast read as icicles better than snowflakes do. */
	private static final BlockParticleOption ICE_SHARD =
			new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState());

	private record Zone(ServerLevel level, Vec3 center, long endTick, float damagePerPulse) {
	}

	private static final Map<UUID, Zone> ZONES = new HashMap<>();

	private BlizzardZones() {
	}

	/** Damage arrives in pulses, one a second, totalling the cast's damage. */
	public static void place(final ServerPlayer caster, final ServerLevel level, final Vec3 center,
			final float totalDamage) {
		float pulses = Tuning.BLIZZARD_DURATION_TICKS / (float) Tuning.BLIZZARD_PULSE_TICKS;
		// +1: the cast runs from the packet queue, so by the first tick-end
		// we observe, game time has already advanced once — without the pad
		// the head pulse (remaining == DURATION) could never fire and the
		// storm silently delivered 7/8 of its damage.
		ZONES.put(caster.getUUID(), new Zone(level, center,
				level.getGameTime() + Tuning.BLIZZARD_DURATION_TICKS + 1, totalDamage / pulses));
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (ZONES.isEmpty()) {
				return;
			}

			for (Iterator<Map.Entry<UUID, Zone>> it = ZONES.entrySet().iterator(); it.hasNext();) {
				Map.Entry<UUID, Zone> entry = it.next();
				Zone zone = entry.getValue();
				ServerLevel level = zone.level();
				ServerPlayer caster = server.getPlayerList().getPlayer(entry.getKey());

				if (level.getGameTime() >= zone.endTick() || caster == null || caster.level() != level) {
					it.remove();
					continue;
				}

				icicles(level, zone.center());
				long remaining = zone.endTick() - level.getGameTime();

				if (remaining % Tuning.BLIZZARD_PULSE_TICKS == 0) {
					pulse(level, zone, caster);
				} else if (remaining % Tuning.BLIZZARD_SOUND_TICKS == 0) {
					// The off-beat: an icicle lands audibly between damage
					// pulses, so the storm never goes quiet.
					impact(level, zone.center(), 4);
				}
			}
		});
	}

	/** Quick-falling icicles, all storm no fluff: ice crumbs dropped from
	 * above at speed, with a thin snowflake haze for weather. */
	private static void icicles(final ServerLevel level, final Vec3 center) {
		var random = level.getRandom();

		for (int i = 0; i < 5; i++) {
			double x = center.x + (random.nextDouble() - 0.5) * Tuning.BLIZZARD_HALF_WIDTH * 2.0;
			double z = center.z + (random.nextDouble() - 0.5) * Tuning.BLIZZARD_HALF_WIDTH * 2.0;
			// Count 0 makes the offsets a velocity: straight down, fast.
			level.sendParticles(ICE_SHARD, x, center.y + 3.5 + random.nextDouble(), z,
					0, 0.0, -1.0, 0.0, 1.5 + random.nextDouble() * 0.5);
		}

		level.sendParticles(ParticleTypes.SNOWFLAKE, center.x, center.y + 2.5, center.z,
				2, Tuning.BLIZZARD_HALF_WIDTH * 0.8, 1.0, Tuning.BLIZZARD_HALF_WIDTH * 0.8, 0.02);
	}

	/** True while a pulse's damage is being dealt — the knockback mixin
	 * reads it to keep the storm from juggling its victims out of itself
	 * (server-thread synchronous, so a plain flag is enough). */
	private static boolean pulsing;

	public static boolean isPulsing() {
		return pulsing;
	}

	private static void pulse(final ServerLevel level, final Zone zone, final ServerPlayer caster) {
		Vec3 c = zone.center();
		AABB box = new AABB(c.x - Tuning.BLIZZARD_HALF_WIDTH, c.y - 0.5, c.z - Tuning.BLIZZARD_HALF_WIDTH,
				c.x + Tuning.BLIZZARD_HALF_WIDTH, c.y + 3.0, c.z + Tuning.BLIZZARD_HALF_WIDTH);

		pulsing = true;

		try {
			for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box,
					living -> living.isAlive() && living instanceof Enemy)) {
				victim.hurtServer(level, level.damageSources().indirectMagic(caster, caster),
						zone.damagePerPulse());
			}
		} finally {
			pulsing = false;
		}

		impact(level, c, 14);
	}

	/** The dripstone land IS a falling icicle hitting ground — with ice
	 * crumbs bursting at the floor, the storm reads without being loud. */
	private static void impact(final ServerLevel level, final Vec3 c, final int crumbs) {
		level.sendParticles(ICE_SHARD, c.x, c.y + 0.2, c.z,
				crumbs, Tuning.BLIZZARD_HALF_WIDTH * 0.8, 0.2, Tuning.BLIZZARD_HALF_WIDTH * 0.8, 0.15);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.POINTED_DRIPSTONE_LAND,
				SoundSource.PLAYERS, 0.9F, 1.1F + level.getRandom().nextFloat() * 0.3F);
	}
}
