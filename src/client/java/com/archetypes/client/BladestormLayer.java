package com.archetypes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Bladestorm's whirl: four copies of the channelling player's own sword, laid
 * near-flat and spun hard at two heights — fast enough that the player is
 * half-hidden behind steel, which is the WoW read. Same pipeline as the
 * Bulwark ghosts.
 */
public class BladestormLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public static final RenderStateDataKey<Boolean> ACTIVE = RenderStateDataKey.create();
	public static final RenderStateDataKey<ItemStackRenderState> GHOST = RenderStateDataKey.create();

	private static final int GHOSTS = 7;
	private static final float RADIUS = 1.35F;
	private static final float DEGREES_PER_TICK = 50.0F;

	public BladestormLayer(final RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(final PoseStack pose, final SubmitNodeCollector collector, final int light,
			final AvatarRenderState state, final float yRot, final float xRot) {
		FabricRenderState fabricState = (FabricRenderState) state;
		Boolean active = fabricState.getData(ACTIVE);
		ItemStackRenderState ghost = fabricState.getData(GHOST);

		if (active == null || !active || ghost == null) {
			return;
		}

		float base = state.ageInTicks * DEGREES_PER_TICK;

		for (int i = 0; i < GHOSTS; i++) {
			float angle = base + i * (360.0F / GHOSTS);
			// Two bands of blades, alternating heights.
			float height = i % 2 == 0 ? 0.65F : 0.95F;

			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(-angle));
			pose.translate(0.0F, height, RADIUS);
			// The FIXED sword sprite lies in a vertical plane, blade running
			// its 45-degree diagonal. Straighten the diagonal onto one axis,
			// then fold the plane flat — the blades sweep tangentially like a
			// saw instead of standing up like scythes (screenshot-verified
			// failure of the first guess).
			pose.mulPose(Axis.ZP.rotationDegrees(-45.0F));
			pose.mulPose(Axis.XP.rotationDegrees(90.0F));
			ghost.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}
	}
}
