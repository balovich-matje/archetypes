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
 * on the targeted ground that rakes its 3x3 with icicles for eight seconds
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
		ZONES.put(caster.getUUID(), new Zone(level, center,
				level.getGameTime() + Tuning.BLIZZARD_DURATION_TICKS, totalDamage / pulses));
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

				if ((zone.endTick() - level.getGameTime()) % Tuning.BLIZZARD_PULSE_TICKS == 0) {
					pulse(level, zone, caster);
				}
			}
		});
	}

	/** Quick-falling icicles, all storm no fluff: ice crumbs dropped from
	 * above at speed, with a thin snowflake haze for weather. */
	private static void icicles(final ServerLevel level, final Vec3 center) {
		var random = level.getRandom();

		for (int i = 0; i < 3; i++) {
			double x = center.x + (random.nextDouble() - 0.5) * Tuning.BLIZZARD_HALF_WIDTH * 2.0;
			double z = center.z + (random.nextDouble() - 0.5) * Tuning.BLIZZARD_HALF_WIDTH * 2.0;
			// Count 0 makes the offsets a velocity: straight down, fast.
			level.sendParticles(ICE_SHARD, x, center.y + 3.5 + random.nextDouble(), z,
					0, 0.0, -1.0, 0.0, 1.5 + random.nextDouble() * 0.5);
		}

		level.sendParticles(ParticleTypes.SNOWFLAKE, center.x, center.y + 2.5, center.z,
				2, Tuning.BLIZZARD_HALF_WIDTH * 0.8, 1.0, Tuning.BLIZZARD_HALF_WIDTH * 0.8, 0.02);
	}

	private static void pulse(final ServerLevel level, final Zone zone, final ServerPlayer caster) {
		Vec3 c = zone.center();
		AABB box = new AABB(c.x - Tuning.BLIZZARD_HALF_WIDTH, c.y - 0.5, c.z - Tuning.BLIZZARD_HALF_WIDTH,
				c.x + Tuning.BLIZZARD_HALF_WIDTH, c.y + 3.0, c.z + Tuning.BLIZZARD_HALF_WIDTH);

		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box,
				living -> living.isAlive() && living instanceof Enemy)) {
			victim.hurtServer(level, level.damageSources().indirectMagic(caster, caster),
					zone.damagePerPulse());
		}

		// The dripstone land IS a falling icicle hitting ground — with ice
		// crumbs bursting at the floor, the pulse reads without being loud.
		level.sendParticles(ICE_SHARD, c.x, c.y + 0.2, c.z,
				10, Tuning.BLIZZARD_HALF_WIDTH * 0.8, 0.2, Tuning.BLIZZARD_HALF_WIDTH * 0.8, 0.15);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.POINTED_DRIPSTONE_LAND,
				SoundSource.PLAYERS, 0.9F, 1.1F + level.getRandom().nextFloat() * 0.3F);
	}
}
