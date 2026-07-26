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
		((LivingEntityRendererAccessor) this)
				.archetypes$addLayer(new com.archetypes.client.BladestormLayer((AvatarRenderer) (Object) this));
		((LivingEntityRendererAccessor) this).archetypes$addLayer(
				new com.archetypes.client.NightEyesLayer((AvatarRenderer) (Object) this));
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

		// Ghost Armor: an invisible Shadow's armor vanishes too — the state's
		// equipment fields are what the armor and head layers render from.
		if (Boolean.TRUE.equals(((AttachmentTarget) entity).getAttached(ModAttachments.ARMOR_HIDDEN))) {
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
		Long stormEnd = ((AttachmentTarget) entity).getAttached(ModAttachments.BLADESTORM_END);
		boolean storming = stormEnd != null && stormEnd > entity.level().getGameTime();
		fabricState.setData(com.archetypes.client.BladestormLayer.ACTIVE, storming);

		if (storming) {
			ItemStackRenderState blade = new ItemStackRenderState();
			Minecraft.getInstance().getItemModelResolver().updateForLiving(
					blade, entity.getMainHandItem(), ItemDisplayContext.FIXED, entity);
			fabricState.setData(com.archetypes.client.BladestormLayer.GHOST, blade);
		}
	}

	/**
	 * Spearwall's shield arm goes up.
	 *
	 * <p>{@code getArmPose} only reaches its {@code BLOCK} branch when
	 * {@code avatar.getUsedItemHand() == hand}, and under Spearwall the used
	 * hand is the spear's — so the guarding hand fell all the way through to
	 * {@code ArmPose.ITEM} and the shield hung at the player's side while the
	 * spear was planted. This is the third-person half; the shield's blocking
	 * MODEL is a separate seam ({@code IsUsingItemMixin}) because it is the
	 * item property, not the pose, that vanilla swaps the shield mesh on.
	 *
	 * <p>The spear's own arm is untouched. Writing {@code BLOCK} here is only
	 * half the job though: {@code ArmPose.SPEAR} is
	 * {@code affectsOffhandPose = true}, so {@code HumanoidModel.setupAnim}
	 * poses the spear arm and then SKIPS the guard arm entirely and this value
	 * is never read. {@link HumanoidModelMixin} is the other half and poses the
	 * guard arm off exactly this flag — do not delete one without the other.
	 *
	 * <p>Overload disambiguated by full descriptor: {@code AvatarRenderer} has
	 * two {@code getArmPose}s and the other one takes a {@code HumanoidArm}.
	 */
	@com.llamalad7.mixinextras.injector.ModifyReturnValue(
			method = "getArmPose(Lnet/minecraft/world/entity/Avatar;"
					+ "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)"
					+ "Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
			at = @At("RETURN"))
	private static net.minecraft.client.model.HumanoidModel.ArmPose archetypes$spearwallShieldArm(
			final net.minecraft.client.model.HumanoidModel.ArmPose pose, final Avatar avatar,
			final net.minecraft.world.item.ItemStack itemInHand,
			final net.minecraft.world.InteractionHand hand) {
		if (pose == net.minecraft.client.model.HumanoidModel.ArmPose.BLOCK
				|| !com.archetypes.Spearwall.isRaisedGuard(avatar, itemInHand)) {
			return pose;
		}

		return net.minecraft.client.model.HumanoidModel.ArmPose.BLOCK;
	}
}
