package com.archetypes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * The Bulwark aura: ghost copies of the player's own shield orbiting them
 * while they block, faces outward — the WoW divine-protection read. True alpha
 * is not available through the item submit path (the trailing int is an
 * outline colour, not a tint), so "ghost" is approximated with sub-scale
 * copies and speed.
 */
public class BulwarkShieldLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final int GHOSTS = 3;
	private static final float RADIUS = 1.15F;
	private static final float DEGREES_PER_TICK = 9.0F;
	private static final float SCALE = 0.8F;

	public BulwarkShieldLayer(final RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(final PoseStack pose, final SubmitNodeCollector collector, final int light,
			final AvatarRenderState state, final float yRot, final float xRot) {
		FabricRenderState fabricState = (FabricRenderState) state;
		Boolean active = fabricState.getData(BulwarkRenderData.ACTIVE);
		ItemStackRenderState ghost = fabricState.getData(BulwarkRenderData.GHOST);

		if (active == null || !active || ghost == null) {
			return;
		}

		float base = state.ageInTicks * DEGREES_PER_TICK;

		for (int i = 0; i < GHOSTS; i++) {
			float angle = base + i * (360.0F / GHOSTS);

			pose.pushPose();
			// Living renderers set the pose up y-flipped; 0.75 here is chest
			// height. Spin the frame, step out to the orbit, then face the
			// shield away from the player.
			pose.mulPose(Axis.YP.rotationDegrees(-angle));
			pose.translate(0.0F, 0.75F, RADIUS);
			pose.mulPose(Axis.YP.rotationDegrees(180.0F));
			pose.scale(SCALE, SCALE, SCALE);
			ghost.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}
	}
}
