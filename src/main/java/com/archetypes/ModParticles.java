package com.archetypes;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModParticles {
	/**
	 * Vanilla's sweep flash, greatsword-sized. Reuses minecraft's own sweep
	 * sprites (see particles/greatsword_sweep.json) so it reads as the same
	 * effect, just swung by something much heavier. The x-velocity channel is
	 * borrowed as a shrink factor, exactly like vanilla's sweep does.
	 */
	public static final SimpleParticleType GREATSWORD_SWEEP = Registry.register(
			BuiltInRegistries.PARTICLE_TYPE,
			Archetypes.id("greatsword_sweep"),
			FabricParticleTypes.simple());

	private ModParticles() {
	}

	public static void initialize() {
		// Forces static initialization at mod init time.
	}
}
