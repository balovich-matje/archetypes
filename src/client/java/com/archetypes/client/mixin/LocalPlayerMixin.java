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

	/**
	 * Swift Shadow, on the nodes that have no {@code SNEAKING_SPEED} attribute
	 * to hang it on. See {@code ShadowTicker} for the arm this replaces.
	 */
	// ---- STAGE 5, AND THE SECOND HALF OF AN R-20 RE-ROOT. ----
	//
	// `Attributes.SNEAKING_SPEED` is `>=1.21`. Below it the sneak factor is not an attribute
	// at all, so the ADD_VALUE modifier `ShadowTicker` applies on every other node has no
	// host and the whole node would be silently inert here — which is precisely the class of
	// regression R-20 exists to catch, and the reason this is a MOVE and not an excision.
	//
	// MEASURED on both sides, because "the same idea" is not the bar; the same NUMBER is:
	//
	//   1.21.1  `LocalPlayer.aiStep` reads `getAttributeValue(SNEAKING_SPEED)` (offsets
	//           145-153) and feeds it straight to `Input.tick(ZF)` at 165. The cap is the
	//           ATTRIBUTE's: `RangedAttribute("…player.sneaking_speed", 0.3, 0.0, 1.0)`.
	//   1.20.1  the same method computes `Mth.clamp(0.3F + EnchantmentHelper
	//           .getSneakingSpeedBonus(this), 0.0F, 1.0F)` (offsets 115-124) and feeds THAT
	//           to `Input.tick(ZF)` at 139.
	//
	// Base 0.3, additive bonuses, clamped to [0,1], consumed by the same call — the legacy
	// expression is the modern attribute's whole contract written out longhand, cap included.
	//
	// WHY `@ModifyArg` ON ARG 0 AND NOT `@ModifyExpressionValue` ON THE CLAMP'S RESULT: the
	// refund has to land where the enchantment bonus lands, INSIDE the clamp, so that
	// vanilla's own `Mth.clamp` is what caps it at 1.0. Modifying the result instead would
	// need a hand-written re-clamp — a second copy of a bound that vanilla already owns on
	// both sides of the boundary, and the copy is what drifts. Rank 2 (+0.70 on 0.30) lands
	// at exactly 1.0 either way; a Swift Shadow wearing a sneak-speed enchantment is where
	// they differ, and only the arg form matches what `SNEAKING_SPEED` does with the same pair.
	//
	// The anchor is unambiguous and that is measured too: `aiStep` contains exactly ONE
	// `Mth.clamp(FFF)F` (the other two clamps in the method are the `(III)I` overload), so no
	// ordinal is needed and `injectors.defaultRequire: 1` guards it.
	//
	// Client-only is correct rather than convenient, for the same reason the two arms above
	// are: the player is movement-authoritative, this IS the slowdown, and the server never
	// computes a sneak factor at all. The rank comes off the same synced purchases the Nimble
	// Draw arm reads, on the same client tick.
	//? if <1.21 {
	/*@org.spongepowered.asm.mixin.injection.ModifyArg(method = "aiStep()V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F"),
			index = 0)
	private float archetypes$swiftShadow(final float sneakFactor) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		int rank = com.archetypes.ShadowNodes.rank(SubTree.SHADOW,
				NodePurchases.owned(self, SubTree.SHADOW),
				com.archetypes.ShadowNodes.Family.SWIFT_SHADOW);

		return rank <= 0 ? sneakFactor
				: sneakFactor + Tuning.SWIFT_SHADOW_SNEAK_REFUND_PER_RANK * rank;
	}
	*///?}
}
