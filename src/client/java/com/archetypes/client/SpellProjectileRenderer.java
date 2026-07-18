package com.archetypes.client;

import com.archetypes.SpellProjectile;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

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
}
