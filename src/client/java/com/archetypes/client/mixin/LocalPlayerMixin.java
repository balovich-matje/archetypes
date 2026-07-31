package com.archetypes.client.mixin;

import com.archetypes.MarksmanNodes;
import com.archetypes.NodePurchases;
import com.archetypes.SubTree;
import com.archetypes.Tuning;
// STAGE 4 — A RE-ROOT, and the reason it is safe is that the two things being joined are the
// same NUMBER and not merely the same idea. `LocalPlayer.itemUseSpeedMultiplier()F` is
// `>=1.21.11`; below it there is no such method and the use-item slowdown is the bare constant
// `0.2F`, multiplied into `Input.leftImpulse` and `Input.forwardImpulse` inside
// `LocalPlayer.aiStep()V`. Measured, not assumed: `javap -c -constants` on the 1.21.1 mojmap
// jar finds `float 0.2f` at offsets 204 and 219 of `aiStep`, both immediately followed by
// `fmul` and a `putfield` on those two fields, and finds it NOWHERE ELSE IN THE WHOLE CLASS —
// so `@At("CONSTANT")` is unambiguous and reaches exactly the two multiplications the newer
// nodes' single multiplier feeds.
//
// So the ANNOTATION forks and the two handler BODIES do not (conventions §5a): `original` is
// the use-item speed multiplier in both arms, and both handlers gate themselves on what they
// care about (a drawn bow; `isBlocking`), which is why neither needs the `isUsingItem()` guard
// the vanilla branch already applies around the constant.
//
// `@ModifyExpressionValue` and NOT `@ModifyConstant`: two handlers share this one expression,
// and MixinExtras' injector is the one that composes — two `@ModifyConstant`s on the same
// constant are two redirects fighting over it. That is the same reason the arm above uses
// `@ModifyReturnValue` rather than `@Overwrite`-shaped alternatives.
//? if >=1.21.11 {
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
//?} else {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
*///?}

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Nimble Draw: hand back a third of the drawn-bow movement penalty per rank.
 * The slowdown is the USE_EFFECTS component's speed multiplier, read here on
 * the client where movement is decided — no server half needed, players are
 * movement-authoritative and a full-rank archer just walks at walking speed.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	//? if >=1.21.11 {
	@ModifyReturnValue(method = "itemUseSpeedMultiplier()F", at = @At("RETURN"))
	//?} else {
	/*@ModifyExpressionValue(method = "aiStep()V",
			at = @At(value = "CONSTANT", args = "floatValue=0.2F"))
	*///?}
	private float archetypes$nimbleDraw(final float original) {
		LocalPlayer self = (LocalPlayer) (Object) this;

		if (!self.getUseItem().is(Items.BOW)) {
			return original;
		}

		int rank = MarksmanNodes.rank(SubTree.MARKSMAN, NodePurchases.owned(self, SubTree.MARKSMAN),
				MarksmanNodes.Family.NIMBLE_DRAW);

		if (rank <= 0) {
			return original;
		}

		// The penalty is (1 - multiplier); keep only what the ranks don't buy back.
		return 1.0F - (1.0F - original) * (1.0F - Tuning.NIMBLE_DRAW_PER_RANK * rank);
	}

	/**
	 * Sure Footing: hand back a share of the blocking movement penalty.
	 *
	 * <p>Local-player only, and that is correct rather than a shortcut: players
	 * are movement-authoritative, so this IS the slowdown. The node's own gate
	 * ({@code isBlocking}) lives in {@link com.archetypes.SureFooting}.
	 */
	//? if >=1.21.11 {
	@ModifyReturnValue(method = "itemUseSpeedMultiplier()F", at = @At("RETURN"))
	//?} else {
	/*@ModifyExpressionValue(method = "aiStep()V",
			at = @At(value = "CONSTANT", args = "floatValue=0.2F"))
	*///?}
	private float archetypes$blockingMovement(final float original) {
		return com.archetypes.SureFooting.relieve((LocalPlayer) (Object) this, original);
	}
}
