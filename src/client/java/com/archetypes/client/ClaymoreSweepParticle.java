package com.archetypes.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * Vanilla's AttackSweepParticle scaled to a claymore: same eight sweep frames,
 * fullbright and camera-facing, but ~2.6x the quad and two extra frames of
 * life so the bigger flash actually registers. The x-velocity is a shrink
 * factor (0 = full size), mirroring vanilla's convention, so a swing can taper
 * toward its edges.
 */
public class ClaymoreSweepParticle extends SingleQuadParticle {
	private static final float BASE_SIZE = 2.6F;

	private final SpriteSet sprites;

	private ClaymoreSweepParticle(final ClientLevel level, final double x, final double y,
			final double z, final double shrink, final SpriteSet sprites) {
		super(level, x, y, z, 0.0, 0.0, 0.0, sprites.first());
		this.sprites = sprites;
		this.lifetime = 6;
		float grey = this.random.nextFloat() * 0.3F + 0.7F;
		this.rCol = grey;
		this.gCol = grey;
		this.bCol = grey;
		this.quadSize = BASE_SIZE * (1.0F - (float) shrink * 0.5F);
		this.setSpriteFromAge(sprites);
	}

	@Override
	public int getLightCoords(final float partialTick) {
		return 15728880;
	}

	@Override
	public Layer getLayer() {
		return Layer.OPAQUE;
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;

		if (++this.age >= this.lifetime) {
			this.remove();
		} else {
			this.setSpriteFromAge(this.sprites);
		}
	}

	public static class Provider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public Provider(final SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(final SimpleParticleType type, final ClientLevel level,
				final double x, final double y, final double z,
				final double xd, final double yd, final double zd, final RandomSource random) {
			return new ClaymoreSweepParticle(level, x, y, z, xd, this.sprites);
		}
	}
}
