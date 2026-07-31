package com.archetypes.client;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
// STAGE 4 — THE SUPERCLASS MOVES DOWN A STEP, at exactly 1.21.11 and NOT at 26.1. From
// 1.21.11 up this particle can extend `SingleQuadParticle` directly, because there the class
// takes a sprite in its constructor and supplies `setSpriteFromAge` and the four UV accessors
// itself. On 1.21.1 it does none of that: `SingleQuadParticle` declares
// `getU0/getU1/getV0/getV1` ABSTRACT, has no sprite in either constructor and has no
// `setSpriteFromAge` — all of it lives on `TextureSheetParticle`, its subclass (`javap -p` on
// the 1.21.1 and 1.21.11 mojmap client jars, side by side). So below the boundary the parent
// is `TextureSheetParticle` and the constructor loses its sprite argument, which is free:
// `setSpriteFromAge(sprites)` runs a few lines later anyway.
//
// FIVE THINGS MOVE AT THAT ONE BOUNDARY and they are listed together because collapsing any of
// them into the neighbouring 26.1 predicate is exactly the §5k bug: the superclass, the sprite
// constructor argument (`SpriteSet.first()` does not exist below 1.21.11), the draw pair
// (`extractRotatedQuad` -> `renderRotatedQuad`), the layer selector
// (`SingleQuadParticle$Layer getLayer()` -> `ParticleRenderType getRenderType()`) and
// `ParticleProvider.createParticle`, which loses its trailing `RandomSource`.
//? if >=1.21.11 {
import net.minecraft.client.particle.SingleQuadParticle;
//?} else {
/*import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
*///?}
import net.minecraft.client.particle.SpriteSet;
// A PACKAGE MOVE at exactly 26.1, and the surprise is what did NOT move with it: the
// extract-based particle pipeline already exists on 1.21.11. `SingleQuadParticle` declares
// `extract(QuadParticleRenderState, Camera, float)` and BOTH `extractRotatedQuad` overloads
// there, byte for byte the same shapes as 26.1's (`javap -p` on both jars) — only the render
// state's package differs:
//     26.x      net.minecraft.client.renderer.state.level.QuadParticleRenderState
//     1.21.11   net.minecraft.client.renderer.state.QuadParticleRenderState
// So this whole particle needs one import fork and nothing else. The design's plan for it
// (`render(VertexConsumer, Camera, float)`, a rewrite of the draw) is the PRE-1.21.11 shape
// and lands at Stage 4 — the arms below.
//
// The pre-1.21.11 draw, verified against the mapped jar rather than remembered, and the point
// is how LITTLE of it is a rewrite: the two `renderRotatedQuad` overloads are the same shapes
// as the two `extractRotatedQuad` ones with a `VertexConsumer` where the render state was, so
// the three-quad composite and its tangent/tilt arithmetic are copied across unchanged.
//     >=26.1  protected void extractRotatedQuad(QuadParticleRenderState, Camera, Quaternionf, float)
//             protected void extractRotatedQuad(QuadParticleRenderState, Quaternionf, F, F, F, F)
//     1.21.1  protected void renderRotatedQuad(VertexConsumer, Camera, Quaternionf, float)
//             protected void renderRotatedQuad(VertexConsumer, Quaternionf, F, F, F, F)
// `Camera.position()` is `getPosition()` there, and the layer selector is
// `ParticleRenderType getRenderType()` rather than `Layer getLayer()`.
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
//?} elif >=1.21.11 {
/*import net.minecraft.client.renderer.state.QuadParticleRenderState;
*///?} else {
/*import com.mojang.blaze3d.vertex.VertexConsumer;
*///?}
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;

/**
 * One greatsword-wide sweep flash. The 26.2 particle pipeline only carries a
 * single square quad size per particle, so the stretch is composited: this
 * particle extracts three overlapping camera-facing quads spread along the
 * swing's tangent (passed in through the velocity channel), stepping downward
 * across the spread to carry the cleave's ~25-degree tilt. The sprite's
 * feathered ends make the overlap read as one long flash. Vanilla's eight
 * sweep frames, fullbright, six ticks.
 */
//? if >=1.21.11 {
public class GreatswordSweepParticle extends SingleQuadParticle {
//?} else {
/*public class GreatswordSweepParticle extends TextureSheetParticle {
*///?}
	private static final float QUAD_SIZE = 2.6F;
	/** Neighbour quad offset, in quad sizes; < 1 so the trio overlaps. */
	private static final float SPREAD = 0.55F;
	/** Downward y per block of tangent offset — the baked-in swing tilt. */
	private static final float TILT_DROP = 0.30F;

	private final SpriteSet sprites;
	private final float tangentX;
	private final float tangentZ;

	private GreatswordSweepParticle(final ClientLevel level, final double x, final double y,
			final double z, final double tx, final double tz, final SpriteSet sprites) {
		//? if >=1.21.11 {
		super(level, x, y, z, 0.0, 0.0, 0.0, sprites.first());
		//?} else {
		/*super(level, x, y, z, 0.0, 0.0, 0.0);
		*///?}
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

	//? if >=1.21.11 {
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
	//?} else {
	/*@Override
	protected void renderRotatedQuad(final VertexConsumer buffer, final Camera camera,
			final Quaternionf rotation, final float partialTicks) {
		float fx = (float) (Mth.lerp(partialTicks, this.xo, this.x) - camera.getPosition().x());
		float fy = (float) (Mth.lerp(partialTicks, this.yo, this.y) - camera.getPosition().y());
		float fz = (float) (Mth.lerp(partialTicks, this.zo, this.z) - camera.getPosition().z());

		for (int i = -1; i <= 1; i++) {
			float offset = i * SPREAD * this.quadSize;
			this.renderRotatedQuad(buffer, rotation,
					fx + this.tangentX * offset,
					fy - TILT_DROP * offset,
					fz + this.tangentZ * offset,
					partialTicks);
		}
	}
	*///?}

	// The one other 26.1 rename this particle meets: `Particle.getLightColor(float)` became
	// `getLightCoords(float)`. Protected on both; widening it to public here is legal on both.
	@Override
	//? if >=26.1 {
	public int getLightCoords(final float partialTick) {
	//?} else {
	/*public int getLightColor(final float partialTick) {
	*///?}
		return 15728880;
	}

	//? if >=1.21.11 {
	@Override
	public Layer getLayer() {
		return Layer.OPAQUE;
	}
	//?} else {
	/*@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
	}
	*///?}

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

		// `ParticleProvider.createParticle` gained its trailing RandomSource at 26.1; below
		// that the factory is the eight-argument shape. The body is the same call either way.
		@Override
		//? if >=1.21.11 {
		public Particle createParticle(final SimpleParticleType type, final ClientLevel level,
				final double x, final double y, final double z,
				final double xd, final double yd, final double zd, final RandomSource random) {
		//?} else {
		/*public Particle createParticle(final SimpleParticleType type, final ClientLevel level,
				final double x, final double y, final double z,
				final double xd, final double yd, final double zd) {
		*///?}
			return new GreatswordSweepParticle(level, x, y, z, xd, zd, this.sprites);
		}
	}
}
