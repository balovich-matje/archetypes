package com.archetypes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

// STAGE 4 — THE RENDER-STATE COLLAPSE (design §4.3). At exactly 1.21.11 a RenderLayer stops
// being handed an extracted state and is handed the ENTITY again:
//
//   >=1.21.11  submit(PoseStack, SubmitNodeCollector, int, AvatarRenderState, float, float)
//   below      render(PoseStack, MultiBufferSource, int, T, float x6)   T = AbstractClientPlayer
//
// so the whole extraction handoff — BulwarkRenderData's two RenderStateDataKeys, the
// FabricRenderState cast, the ItemStackRenderState resolved at extract time — has nothing to
// carry and is simply not there below the boundary. The layer asks the entity directly. That
// is a COLLAPSE of the indirection, not a reimplementation of it, and it is why the below arm
// needs no partner class despite forking the draw call as well as the signature.
//
// WHY THE ORBIT MATH IS DUPLICATED RATHER THAN HOISTED, since conventions §5b would normally
// forbid that: the hard gate on this port is that the three nodes already landed stay
// INSTRUCTION-IDENTICAL, and hoisting the loop into a shared helper would rewrite the 26.x
// method that already ships. So the numbers are shared — GHOSTS, RADIUS, DEGREES_PER_TICK and
// SCALE are declared once above both arms, and retuning the aura is still one edit for seven
// nodes — while the loop that consumes them is written twice. Read the two loops side by side
// when either changes; they are meant to stay the same shape.
//
// The two draw calls, verified against the mapped jars rather than remembered:
//   >=1.21.11  ItemStackRenderState.submit(PoseStack, SubmitNodeCollector, int, int, int)
//   1.21.1     ItemRenderer.renderStatic(LivingEntity, ItemStack, ItemDisplayContext, boolean,
//                  PoseStack, MultiBufferSource, Level, int, int, int)
// and the blocking item, which 1.21.11 spells `LivingEntity.getItemBlockingWith()` and 1.21.1
// does not have at all — there it is `getUseItem()`, which while `isBlocking()` IS the shield
// (`javap -p` on both jars: getItemBlockingWith is absent below 1.21.11).
//? if >=1.21.11 {
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
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
 * The Bulwark aura: ghost copies of the player's own shield orbiting them
 * while they block, faces outward — the WoW divine-protection read. True alpha
 * is not available through the item submit path (the trailing int is an
 * outline colour, not a tint), so "ghost" is approximated with sub-scale
 * copies and speed.
 */
//? if >=1.21.11 {
public class BulwarkShieldLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
//?} else {
/*public class BulwarkShieldLayer
		extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
*///?}
	private static final int GHOSTS = 3;
	private static final float RADIUS = 1.15F;
	private static final float DEGREES_PER_TICK = 9.0F;
	private static final float SCALE = 0.8F;

	//? if >=1.21.11 {
	public BulwarkShieldLayer(final RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
	//?} else {
	/*public BulwarkShieldLayer(
			final RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
	*///?}
		super(parent);
	}

	//? if >=1.21.11 {
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
			// height. Spin the frame, step out to the orbit; the FIXED shield
			// model already faces outward from there (a 180 turned it inward —
			// confirmed by screenshot).
			pose.mulPose(Axis.YP.rotationDegrees(-angle));
			pose.translate(0.0F, 0.75F, RADIUS);
			pose.scale(SCALE, SCALE, SCALE);
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
		// The two reads the extraction hook made on the newer nodes, made here
		// instead — this is the whole of the collapse. `isBlocking()` stands in
		// for the extractor's own `entity.isBlocking()` gate, and the shield is
		// the item the block is being made WITH.
		Boolean active = ArchetypeStore.INSTANCE.get(entity, ModState.BULWARK_ACTIVE);

		if (active == null || !active || !entity.isBlocking()) {
			return;
		}

		ItemStack shield = entity.getUseItem();

		if (shield.isEmpty()) {
			return;
		}

		// `ageInTicks` is the render() parameter the newer nodes read off the
		// state as `state.ageInTicks` — same quantity, same units.
		float base = ageInTicks * DEGREES_PER_TICK;

		for (int i = 0; i < GHOSTS; i++) {
			float angle = base + i * (360.0F / GHOSTS);

			pose.pushPose();
			// Living renderers set the pose up y-flipped; 0.75 here is chest
			// height. Spin the frame, step out to the orbit; the FIXED shield
			// model already faces outward from there (a 180 turned it inward —
			// confirmed by screenshot).
			pose.mulPose(Axis.YP.rotationDegrees(-angle));
			pose.translate(0.0F, 0.75F, RADIUS);
			pose.scale(SCALE, SCALE, SCALE);
			Minecraft.getInstance().getItemRenderer().renderStatic(entity, shield,
					ItemDisplayContext.FIXED, false, pose, buffers, entity.level(), light,
					OverlayTexture.NO_OVERLAY, entity.getId());
			pose.popPose();
		}
	}
	*///?}
}
