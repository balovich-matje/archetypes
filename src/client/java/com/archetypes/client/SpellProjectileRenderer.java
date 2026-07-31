package com.archetypes.client;

import com.archetypes.SpellProjectile;
import com.mojang.blaze3d.vertex.PoseStack;

// STAGE 4 — the render-state collapse again, this time on an ENTITY RENDERER rather than a
// layer (design §4.3). From 1.21.11 up `ThrownItemRenderer` is state-based: create a state,
// extract into it, submit it. On 1.21.1 it is one method against the entity —
//     public void render(T, float entityYaw, float partialTicks, PoseStack, MultiBufferSource, int)
// (`javap -p` on the 1.21.1 mojmap client jar) — so the private `State` subclass, the scale
// slot it carried and the two hooks that filled it all disappear. The scale is read straight
// off the entity at draw time, which is where it came from in the first place.
//
// `ThrownItemRenderer<T extends Entity & ItemSupplier>` on 1.21.1 and `SpellProjectile extends
// ThrowableItemProjectile` (which implements `ItemSupplier` there) satisfy each other, and the
// one-argument `(EntityRendererProvider$Context)` constructor exists on both, so the class
// header and the constructor are shared.
//? if >=1.21.11 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
*///?}
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
//? if >=1.21.11 {
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
//?}
// The same 26.1 package move as GreatswordSweepParticle's `QuadParticleRenderState` — see
// the note there. `SubmitNodeCollector`, `ThrownItemRenderState`, `createRenderState`,
// `extractRenderState` and `submit` all keep their 26.x names and shapes on 1.21.11, so the
// render-state architecture this class is built on survives THAT node intact — and only that
// node. It is gone one step lower.
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.CameraRenderState;
//?} elif >=1.21.11 {
/*import net.minecraft.client.renderer.state.CameraRenderState;
*///?}

/**
 * The vanilla thrown-item renderer, except Mind Well's empowered missile is
 * drawn half again bigger — the flag rides the entity's synced data, so this
 * is the only client-side piece the empowerment needs.
 */
public final class SpellProjectileRenderer extends ThrownItemRenderer<SpellProjectile> {
	private static final float EMPOWERED_SCALE = 1.5F;

	public SpellProjectileRenderer(final EntityRendererProvider.Context context) {
		super(context);
	}

	//? if >=1.21.11 {
	@Override
	public ThrownItemRenderState createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(final SpellProjectile entity, final ThrownItemRenderState state,
			final float delta) {
		super.extractRenderState(entity, state, delta);

		if (state instanceof State ours) {
			// Empowered missiles and mana-fed meteors share one scale slot.
			ours.scale = entity.isEmpowered() ? EMPOWERED_SCALE : entity.visualScale();
		}
	}

	@Override
	public void submit(final ThrownItemRenderState state, final PoseStack pose,
			final SubmitNodeCollector collector, final CameraRenderState camera) {
		float scale = state instanceof State ours ? ours.scale : 1.0F;

		if (scale != 1.0F) {
			pose.pushPose();
			pose.scale(scale, scale, scale);
		}

		super.submit(state, pose, collector, camera);

		if (scale != 1.0F) {
			pose.popPose();
		}
	}

	private static final class State extends ThrownItemRenderState {
		private float scale = 1.0F;
	}
	//?} else {
	/*// Same scale slot, one step earlier in the pipeline: the entity is still in hand here,
	// so the value is read where the extract hook above would have stashed it. The
	// push/scale/pop bracket around super is character for character the arm above's.
	@Override
	public void render(final SpellProjectile entity, final float entityYaw, final float partialTicks,
			final PoseStack pose, final MultiBufferSource buffers, final int light) {
		float scale = entity.isEmpowered() ? EMPOWERED_SCALE : entity.visualScale();

		if (scale != 1.0F) {
			pose.pushPose();
			pose.scale(scale, scale, scale);
		}

		super.render(entity, entityYaw, partialTicks, pose, buffers, light);

		if (scale != 1.0F) {
			pose.popPose();
		}
	}
	*///?}
}
