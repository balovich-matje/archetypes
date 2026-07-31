package com.archetypes.client.mixin;

// STAGE 4 — the render-state collapse, seen from the renderer side (design §4.3).
//
// The class this attaches to is renamed at exactly 1.21.11: `AvatarRenderer` below is
// `PlayerRenderer`, and the entity it renders is `AbstractClientPlayer` rather than `Avatar`.
// Its `<init>(EntityRendererProvider$Context, Z)V` is identical on both, so the layer
// registration — the only thing this mixin still does below the boundary — needs the
// annotation forked and nothing else.
//
// EVERYTHING ELSE IN THIS FILE IS >=1.21.11 ONLY, and that is the collapse rather than a
// deletion. `extractRenderState` is the moment the newer nodes have both the entity and its
// state, so all four handoffs were made there. Below 1.21.11 the layers are handed the entity
// at DRAW time, so each of them makes its own read where it needs it:
//
//   BULWARK_ACTIVE + the blocked-with shield -> BulwarkShieldLayer.render
//   NightEyesLayer.GLOW                      -> NightEyesLayer.render calls glowFor itself
//   BLADESTORM_END + the main-hand blade     -> BladestormLayer.render
//   ARMOR_HIDDEN (Ghost Armor)               -> GhostArmorMixin, and it HAS to move rather
//                                               than collapse: below the boundary there are no
//                                               `state.*Equipment` fields to blank, so the
//                                               three vanilla layers that read the equipment
//                                               off the entity are cancelled instead.
//
// The per-node client mixin config at versions/1.21.1-fabric/src/client/resources/ carries the
// consequence: GhostArmorMixin is listed there and nowhere else.
//? if >=1.21.11 {
import com.archetypes.ModState;
import com.archetypes.client.BulwarkRenderData;
//?}
import com.archetypes.client.BulwarkShieldLayer;
//? if >=1.21.11 {

import com.archetypes.platform.ArchetypeStore;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemDisplayContext;
//?} else {
/*import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=1.21.11 {
@Mixin(AvatarRenderer.class)
//?} else {
/*@Mixin(PlayerRenderer.class)
*///?}
public abstract class AvatarRendererMixin {
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V",
			at = @At("TAIL"))
	private void archetypes$addBulwarkLayer(final EntityRendererProvider.Context context,
			final boolean slim, final CallbackInfo ci) {
		//? if >=1.21.11 {
		((LivingEntityRendererAccessor) this)
				.archetypes$addLayer(new BulwarkShieldLayer((AvatarRenderer) (Object) this));
		((LivingEntityRendererAccessor) this)
				.archetypes$addLayer(new com.archetypes.client.BladestormLayer((AvatarRenderer) (Object) this));
		((LivingEntityRendererAccessor) this).archetypes$addLayer(
				new com.archetypes.client.NightEyesLayer((AvatarRenderer) (Object) this));
		//?} else {
		/*((LivingEntityRendererAccessor) this)
				.archetypes$addLayer(new BulwarkShieldLayer((PlayerRenderer) (Object) this));
		((LivingEntityRendererAccessor) this)
				.archetypes$addLayer(new com.archetypes.client.BladestormLayer((PlayerRenderer) (Object) this));
		((LivingEntityRendererAccessor) this).archetypes$addLayer(
				new com.archetypes.client.NightEyesLayer((PlayerRenderer) (Object) this));
		*///?}
	}

	//? if >=1.21.11 {
	/**
	 * Extraction is the only moment both the entity and its render state exist,
	 * so the Bulwark flag (server-synced attachment) and a resolved model of the
	 * very shield being blocked with are stashed on the state here.
	 */
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;"
			+ "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void archetypes$extractBulwark(final Avatar entity, final AvatarRenderState state,
			final float partialTick, final CallbackInfo ci) {
		Boolean active = ArchetypeStore.INSTANCE.get(entity, ModState.BULWARK_ACTIVE);
		boolean on = active != null && active && entity.isBlocking();
		FabricRenderState fabricState = (FabricRenderState) state;
		fabricState.setData(BulwarkRenderData.ACTIVE, on);

		if (on) {
			ItemStackRenderState ghost = new ItemStackRenderState();
			Minecraft.getInstance().getItemModelResolver().updateForLiving(
					ghost, entity.getItemBlockingWith(), ItemDisplayContext.FIXED, entity);
			fabricState.setData(BulwarkRenderData.GHOST, ghost);
		}

		// Ghost Armor: an invisible Shadow's armor vanishes too — the state's
		// equipment fields are what the armor and head layers render from.
		if (Boolean.TRUE.equals(ArchetypeStore.INSTANCE.get(entity, ModState.ARMOR_HIDDEN))) {
			state.headEquipment = net.minecraft.world.item.ItemStack.EMPTY;
			state.chestEquipment = net.minecraft.world.item.ItemStack.EMPTY;
			state.legsEquipment = net.minecraft.world.item.ItemStack.EMPTY;
			state.feetEquipment = net.minecraft.world.item.ItemStack.EMPTY;
		}

		// The night form's eye glow, same handoff. Extraction is the only place
		// the entity can still be asked whether it is invisible and whether its
		// Death Mark is out, so the whole three-state decision is made here (in
		// NightEyesLayer.glowFor) and the layer only paints what it is told.
		// Layers are NOT skipped for an invisible player — AvatarRenderer's
		// shouldRenderLayers only excuses spectators — so the invisibility rule
		// is ours to enforce, and glowFor is the one place it is written.
		fabricState.setData(com.archetypes.client.NightEyesLayer.GLOW,
				com.archetypes.client.NightEyesLayer.glowFor(entity));

		// Bladestorm: same handoff, keyed on the synced channel-end timestamp.
		Long stormEnd = ArchetypeStore.INSTANCE.get(entity, ModState.BLADESTORM_END);
		boolean storming = stormEnd != null && stormEnd > entity.level().getGameTime();
		fabricState.setData(com.archetypes.client.BladestormLayer.ACTIVE, storming);

		if (storming) {
			ItemStackRenderState blade = new ItemStackRenderState();
			Minecraft.getInstance().getItemModelResolver().updateForLiving(
					blade, entity.getMainHandItem(), ItemDisplayContext.FIXED, entity);
			fabricState.setData(com.archetypes.client.BladestormLayer.GHOST, blade);
		}
	}
	//?}
}
