package com.archetypes.client.mixin;

import com.archetypes.MarksmanNodes;
import com.archetypes.NodePurchases;
import com.archetypes.SubTree;
import com.archetypes.Tuning;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

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
	@ModifyReturnValue(method = "itemUseSpeedMultiplier", at = @At("RETURN"))
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
}
