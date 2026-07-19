package com.archetypes.client.mixin;

import com.archetypes.client.ExtraSensoryPerception;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Extra Sensory Perception paints its outlines through vanilla's own glowing
 * machinery: {@code EntityRenderState.outlineColor} is the single field that
 * decides both whether an entity joins the outline pass and what colour it
 * comes out, and the tail of extraction is where vanilla itself writes it
 * (from {@code Minecraft.shouldEntityAppearGlowing}). Writing after vanilla
 * means a genuinely glowing entity keeps its team colour unless it is sensed.
 *
 * <p>Client-only and read off the LOCAL player's own roster, so one player's
 * senses can never tint another player's view.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;"
			+ "Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
			at = @At("TAIL"))
	private void archetypes$senseOutline(final Entity entity, final EntityRenderState state,
			final float partialTicks, final CallbackInfo ci) {
		int color = ExtraSensoryPerception.outlineColor(entity);

		if (color != EntityRenderState.NO_OUTLINE) {
			state.outlineColor = color;
		}
	}
}
