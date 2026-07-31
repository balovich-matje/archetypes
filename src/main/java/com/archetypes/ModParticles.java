package com.archetypes;

// STAGE 6 — `FabricParticleTypes.simple()` is fabric-api's shorthand for a
// `SimpleParticleType` with `alwaysShow = false`; both loaders construct one directly and the
// constructor is public there. Only the ARGUMENT forks; the registration around it does not.
//? if fabric {
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
//?}
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
			// UNVERIFIED ON EITHER LOADER, and stated so rather than asserted: vanilla's
			// `SimpleParticleType(boolean)` constructor is PROTECTED, which is why fabric-api
			// ships `simple()` at all. NeoForge widens a great many vanilla constructors with
			// its own access transformers and LexForge widens fewer; whether this one is among
			// them is the node agent's first compile. If it is not, the answer is an access
			// transformer (NeoForge) or an accessor mixin (Forge) — NOT a different particle
			// type, and not `alwaysShow = true`, which would change how the sweep draws.
			//
			// The failure is loud either way: a protected constructor is a compile error on the
			// node, not a silent difference.
			//? if fabric {
			FabricParticleTypes.simple());
			//?} else {
			/*new SimpleParticleType(false));
			*///?}

	private ModParticles() {
	}

	public static void initialize() {
		// Forces static initialization at mod init time.
	}
}
