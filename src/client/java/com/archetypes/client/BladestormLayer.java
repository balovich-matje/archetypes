package com.archetypes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

// STAGE 4 — the same render-state collapse BulwarkShieldLayer's header describes, and the same
// rule about what forks: the twelve-blade geometry is written twice because hoisting it would
// rewrite the 26.x method that already ships, but every NUMBER (GHOSTS, RADIUS,
// DEGREES_PER_TICK, the three heights and the three attitudes) is declared or written once per
// arm from the same constants. The two ACTIVE/GHOST keys are gone below the boundary for the
// reason BulwarkRenderData's header gives — there is no state to hang them on.
//? if >=1.21.11 {
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
//?} else {
/*import com.archetypes.ModState;
import com.archetypes.platform.ArchetypeStore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
*///?}

/**
 * Bladestorm's whirl: four copies of the channelling player's own sword, laid
 * near-flat and spun hard at two heights — fast enough that the player is
 * half-hidden behind steel, which is the WoW read. Same pipeline as the
 * Bulwark ghosts.
 */
//? if >=1.21.11 {
public class BladestormLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public static final RenderStateDataKey<Boolean> ACTIVE = RenderStateDataKey.create();
	public static final RenderStateDataKey<ItemStackRenderState> GHOST = RenderStateDataKey.create();

//?} else {
/*public class BladestormLayer
		extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
*///?}
	private static final int GHOSTS = 12;
	private static final float RADIUS = 1.35F;
	private static final float DEGREES_PER_TICK = 50.0F;

	//? if >=1.21.11 {
	public BladestormLayer(final RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
	//?} else {
	/*public BladestormLayer(
			final RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
	*///?}
		super(parent);
	}

	//? if >=1.21.11 {
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
			// Three heights, three attitudes, interleaved so no two neighbours
			// match — the mess is the point, it reads as a storm.
			float height = switch (i % 3) {
				case 0 -> 0.55F;
				case 1 -> 0.8F;
				default -> 1.05F;
			};

			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(-angle));

			// The FIXED sword sprite lies in a vertical plane, blade running
			// its 45-degree diagonal; ZP(-45) straightens it onto one axis.
			switch (i % 3) {
				case 0 -> {
					// Pointing outward at whatever surrounds the player, tip
					// past the orbit ring.
					pose.translate(0.0F, height, RADIUS + 0.25F);
					pose.mulPose(Axis.ZP.rotationDegrees(-45.0F));
					pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
				}
				case 1 -> {
					// Sweeping flat, tilted a touch downward (the look the
					// second pass landed on).
					pose.translate(0.0F, height, RADIUS);
					pose.mulPose(Axis.ZP.rotationDegrees(-45.0F));
					pose.mulPose(Axis.XP.rotationDegrees(105.0F));
				}
				default -> {
					// Sweeping flat, tilted a touch upward.
					pose.translate(0.0F, height, RADIUS);
					pose.mulPose(Axis.ZP.rotationDegrees(-45.0F));
					pose.mulPose(Axis.XP.rotationDegrees(75.0F));
				}
			}

			ghost.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}
	}
	//?} else {
	/*@Override
	public void render(final PoseStack pose, final MultiBufferSource buffers, final int light,
			final AbstractClientPlayer entity, final float limbSwing, final float limbSwingAmount,
			final float partialTicks, final float ageInTicks, final float netHeadYaw,
			final float headPitch) {
		// The extraction hook's read, made here instead: the channel-end stamp
		// is synced to every client, so the layer can ask the entity for it.
		Long stormEnd = ArchetypeStore.INSTANCE.get(entity, ModState.BLADESTORM_END);

		if (stormEnd == null || stormEnd <= entity.level().getGameTime()) {
			return;
		}

		ItemStack blade = entity.getMainHandItem();

		if (blade.isEmpty()) {
			return;
		}

		float base = ageInTicks * DEGREES_PER_TICK;

		for (int i = 0; i < GHOSTS; i++) {
			float angle = base + i * (360.0F / GHOSTS);
			// Three heights, three attitudes, interleaved so no two neighbours
			// match — the mess is the point, it reads as a storm.
			float height = switch (i % 3) {
				case 0 -> 0.55F;
				case 1 -> 0.8F;
				default -> 1.05F;
			};

			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(-angle));

			// The FIXED sword sprite lies in a vertical plane, blade running
			// its 45-degree diagonal; ZP(-45) straightens it onto one axis.
			switch (i % 3) {
				case 0 -> {
					// Pointing outward at whatever surrounds the player, tip
					// past the orbit ring.
					pose.translate(0.0F, height, RADIUS + 0.25F);
					pose.mulPose(Axis.ZP.rotationDegrees(-45.0F));
					pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
				}
				case 1 -> {
					// Sweeping flat, tilted a touch downward (the look the
					// second pass landed on).
					pose.translate(0.0F, height, RADIUS);
					pose.mulPose(Axis.ZP.rotationDegrees(-45.0F));
					pose.mulPose(Axis.XP.rotationDegrees(105.0F));
				}
				default -> {
					// Sweeping flat, tilted a touch upward.
					pose.translate(0.0F, height, RADIUS);
					pose.mulPose(Axis.ZP.rotationDegrees(-45.0F));
					pose.mulPose(Axis.XP.rotationDegrees(75.0F));
				}
			}

			Minecraft.getInstance().getItemRenderer().renderStatic(entity, blade,
					ItemDisplayContext.FIXED, false, pose, buffers, entity.level(), light,
					OverlayTexture.NO_OVERLAY, entity.getId());
			pose.popPose();
		}
	}
	*///?}
}
