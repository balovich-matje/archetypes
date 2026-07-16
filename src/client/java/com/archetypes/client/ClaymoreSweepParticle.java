package com.archetypes.client;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;

/**
 * One claymore-wide sweep flash. The 26.2 particle pipeline only carries a
 * single square quad size per particle, so the stretch is composited: this
 * particle extracts three overlapping camera-facing quads spread along the
 * swing's tangent (passed in through the velocity channel), stepping downward
 * across the spread to carry the cleave's ~25-degree tilt. The sprite's
 * feathered ends make the overlap read as one long flash. Vanilla's eight
 * sweep frames, fullbright, six ticks.
 */
public class ClaymoreSweepParticle extends SingleQuadParticle {
	private static final float QUAD_SIZE = 2.6F;
	/** Neighbour quad offset, in quad sizes; < 1 so the trio overlaps. */
	private static final float SPREAD = 0.55F;
	/** Downward y per block of tangent offset — the baked-in swing tilt. */
	private static final float TILT_DROP = 0.30F;

	private final SpriteSet sprites;
	private final float tangentX;
	private final float tangentZ;

	private ClaymoreSweepParticle(final ClientLevel level, final double x, final double y,
			final double z, final double tx, final double tz, final SpriteSet sprites) {
		super(level, x, y, z, 0.0, 0.0, 0.0, sprites.first());
		this.sprites = sprites;
		this.xd = 0.0;
		this.yd = 0.0;
		this.zd = 0.0;

		double length = Math.sqrt(tx * tx + tz * tz);
		this.tangentX = length > 1.0E-4 ? (float) (tx / length) : 1.0F;
		this.tangentZ = length > 1.0E-4 ? (float) (tz / length) : 0.0F;

		this.lifetime = 6;
		float grey = this.random.nextFloat() * 0.3F + 0.7F;
		this.rCol = grey;
		this.gCol = grey;
		this.bCol = grey;
		this.quadSize = QUAD_SIZE;
		this.setSpriteFromAge(sprites);
	}

	@Override
	protected void extractRotatedQuad(final QuadParticleRenderState state, final Camera camera,
			final Quaternionf rotation, final float partialTicks) {
		float fx = (float) (Mth.lerp(partialTicks, this.xo, this.x) - camera.position().x());
		float fy = (float) (Mth.lerp(partialTicks, this.yo, this.y) - camera.position().y());
		float fz = (float) (Mth.lerp(partialTicks, this.zo, this.z) - camera.position().z());

		for (int i = -1; i <= 1; i++) {
			float offset = i * SPREAD * this.quadSize;
			this.extractRotatedQuad(state, rotation,
					fx + this.tangentX * offset,
					fy - TILT_DROP * offset,
					fz + this.tangentZ * offset,
					partialTicks);
		}
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
			return new ClaymoreSweepParticle(level, x, y, z, xd, zd, this.sprites);
		}
	}
}
