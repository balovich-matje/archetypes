package com.archetypes.client.mixin;

import com.archetypes.ModAttachments;
import com.archetypes.client.BulwarkRenderData;
import com.archetypes.client.BulwarkShieldLayer;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Inject(method = "<init>", at = @At("TAIL"))
	private void archetypes$addBulwarkLayer(final EntityRendererProvider.Context context,
			final boolean slim, final CallbackInfo ci) {
		((LivingEntityRendererAccessor) this)
				.archetypes$addLayer(new BulwarkShieldLayer((AvatarRenderer) (Object) this));
	}

	/**
	 * Extraction is the only moment both the entity and its render state exist,
	 * so the Bulwark flag (server-synced attachment) and a resolved model of the
	 * very shield being blocked with are stashed on the state here.
	 */
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;"
			+ "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void archetypes$extractBulwark(final Avatar entity, final AvatarRenderState state,
			final float partialTick, final CallbackInfo ci) {
		Boolean active = ((AttachmentTarget) entity).getAttached(ModAttachments.BULWARK_ACTIVE);
		boolean on = active != null && active && entity.isBlocking();
		FabricRenderState fabricState = (FabricRenderState) state;
		fabricState.setData(BulwarkRenderData.ACTIVE, on);

		if (on) {
			ItemStackRenderState ghost = new ItemStackRenderState();
			Minecraft.getInstance().getItemModelResolver().updateForLiving(
					ghost, entity.getItemBlockingWith(), ItemDisplayContext.FIXED, entity);
			fabricState.setData(BulwarkRenderData.GHOST, ghost);
		}
	}
}
