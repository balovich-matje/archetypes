package com.archetypes.client.mixin;

// STAGE 4 — THE ONE PIECE OF THE EXTRACTION HOOK THAT COULD NOT COLLAPSE, so it moved here.
//
// On 26.x and 1.21.11, Ghost Armor is four assignments in AvatarRendererMixin's
// `extractRenderState`: blank `state.headEquipment` / `chestEquipment` / `legsEquipment` /
// `feetEquipment` and the armor, head and elytra layers, which render FROM those fields, draw
// nothing. Below 1.21.11 there are no such fields — every layer reads the equipment off the
// entity with `getItemBySlot` at draw time — so there is nothing to blank and the layers
// themselves have to be stopped.
//
// WHY THE LAYER AND NOT `getItemBySlot`: a client-side `@ModifyReturnValue` on
// `LivingEntity.getItemBySlot` would blank the armor everywhere the client asks — the
// inventory screen, the tooltip, the durability bar, the armor HUD row. The three layers are
// exactly the four assignments' reach and nothing more.
//
// ONE MIXIN, THREE TARGETS, and that is measured rather than lucky: all three layers declare
// the same erased descriptor for the method that draws,
//   (Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I
//    Lnet/minecraft/world/entity/LivingEntity;FFFFFF)V
// because all three bound their entity parameter at `LivingEntity` (`javap -p -s` on
// HumanoidArmorLayer, CustomHeadLayer and ElytraLayer in the 1.21.1 mojmap jar). Each also
// carries an `Entity`-typed bridge from `RenderLayer<T extends Entity>`; the full descriptor
// is what picks the real one out, per conventions §5h.
//
// The whole file is below-1.21.11 only and is listed ONLY in
// versions/1.21.1-fabric/src/client/resources/archetypes.client.mixins.json. A mixin named in
// a config whose class is not in the jar is a hard boot failure, which is why the shared
// config must never gain this name.
//? if <1.21.11 {
/*import com.archetypes.ModState;

import com.archetypes.platform.ArchetypeStore;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * Ghost Armor, below 1.21.11: the three vanilla layers that would paint a
 * Shadow's gear are cancelled outright while the perk is up.
 *
 * <p>The flag is a server-synced attachment on the wearer, so every onlooker's
 * client agrees about who is bare — the same channel the newer nodes read at
 * extraction.
 ^/
@Mixin({ HumanoidArmorLayer.class, CustomHeadLayer.class, ElytraLayer.class })
public abstract class GhostArmorMixin {
	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/MultiBufferSource;I"
			+ "Lnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
			at = @At("HEAD"), cancellable = true)
	private void archetypes$hideGhostArmor(final PoseStack pose, final MultiBufferSource buffers,
			final int light, final LivingEntity entity, final float limbSwing,
			final float limbSwingAmount, final float partialTicks, final float ageInTicks,
			final float netHeadYaw, final float headPitch, final CallbackInfo ci) {
		if (entity instanceof Player
				&& Boolean.TRUE.equals(ArchetypeStore.INSTANCE.get(entity, ModState.ARMOR_HIDDEN))) {
			ci.cancel();
		}
	}
}
*///?}
