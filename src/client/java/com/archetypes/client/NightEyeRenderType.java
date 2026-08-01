package com.archetypes.client;

// STAGE 7, fifth in-game finding — THE WHOLE COMPILATION UNIT IS BELOW-1.21.11 ONLY
// (conventions §4's whole-file form), because vanilla's `eyes` render type does not mean the
// same thing on both sides of that boundary. Measured off the mapped jars, not remembered:
//
//   >=1.21.11 / 26.x   RenderPipelines.EYES -> BlendFunction.TRANSLUCENT
//                      (SRC_ALPHA / ONE_MINUS_SRC_ALPHA), depth write off, cull on,
//                      shader defines EMISSIVE + NO_CARDINAL_LIGHTING.
//   <1.21.11           RenderType.eyes -> ADDITIVE_TRANSPARENCY, and that shard is
//                      `RenderSystem.blendFunc(SourceFactor.ONE, DestFactor.ONE)` —
//                      PURE additive. Source alpha is not a factor in the blend at all.
//
// `night_form_eyes.png` carries its whole shape in the alpha channel: 1272 of its 2048 texels
// are (255,255,255, a=0) and the falloff ring is white at a=1..250. Under ONE/ONE every one of
// those texels adds full white x the tint, so the 8x4 quad came out as ONE SOLID GLOWING BAND
// across the whole face instead of two eyes. The alpha dial that separates FAINT from BRIGHT
// was inert on those nodes for the same reason.
//
// No stock render type below 1.21.11 reproduces 26.2's state: `entityTranslucentEmissive`
// blends translucently but its vertex shader runs `minecraft_mix_light` (the glow would dim
// with head facing) and its fragment shader discards below alpha 0.1 (the falloff would
// harden), and nothing else in the class pairs a translucent blend with an emissive, unlit
// shader.
//
// So this takes vanilla's own `eyes` composite — all thirteen of its state shards, including
// the texture binding, the eyes shader, LEQUAL depth test, cull and COLOR_WRITE — and
// re-applies exactly ONE shard on top of it: TRANSLUCENT_TRANSPARENCY, whose setupRenderState
// overwrites the blend func the additive shard just set. Nothing is reimplemented, so nothing
// can drift out of step with a vanilla state this file does not name.
//
// `TRANSLUCENT_TRANSPARENCY` is `protected static` on `RenderStateShard`; extending
// `RenderType` (which extends it) is what makes it reachable, and that is why this is a class
// rather than three lines in NightEyesLayer. No access widener, no accessor mixin, no
// mixin-config entry on any node — and the three nodes at or above the boundary get no
// `.class` from this file at all, so their jars cannot move a byte because of it.
//? if <1.21.11 {
/*import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.Identifier;

/^*
 * Vanilla's own eye-glow pass, with the blend swapped from additive to translucent.
 ^/
public final class NightEyeRenderType extends RenderType {
	// One texture ever, so one instance ever — and it HAS to be one instance, because
	// MultiBufferSource keys its buffers on the render type and this class inherits
	// identity equality from RenderStateShard.
	private static Identifier cachedTexture;
	private static RenderType cached;

	private NightEyeRenderType(final String name, final RenderType eyes) {
		// format/mode/bufferSize are read off the delegate rather than restated: vanilla
		// declares eyes at 1536 on 1.21.1 and 256 on 1.20.1, and this way neither number is
		// written down twice. affectsCrumbling=false, sortOnUpload=true match both.
		super(name, eyes.format(), eyes.mode(), eyes.bufferSize(), false, true,
				setup(eyes), clear(eyes));
	}

	private static Runnable setup(final RenderType eyes) {
		return () -> {
			eyes.setupRenderState();
			TRANSLUCENT_TRANSPARENCY.setupRenderState();
		};
	}

	private static Runnable clear(final RenderType eyes) {
		// Reverse order, and both halves are idempotent: TRANSLUCENT's clear and ADDITIVE's
		// clear are the same two calls (disableBlend + defaultBlendFunc), so running the
		// delegate's full clear afterwards restores exactly what vanilla's eyes pass would.
		return () -> {
			TRANSLUCENT_TRANSPARENCY.clearRenderState();
			eyes.clearRenderState();
		};
	}

	public static RenderType translucentEyes(final Identifier texture) {
		if (!texture.equals(cachedTexture)) {
			cachedTexture = texture;
			cached = new NightEyeRenderType("archetypes_night_form_eyes",
					RenderType.eyes(texture));
		}

		return cached;
	}
}
*///?}
