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

	/**
	 * Spearwall pays the shield's movement price.
	 *
	 * <p>Vanilla scales input by the USE item's multiplier, and under Spearwall
	 * the use item is the spear — which declares
	 * {@code new UseEffects(true, false, 1.0F)}, i.e. no slowdown and sprinting
	 * allowed, because a braced spear is meant to be carried at a run. The
	 * synthesised guard was therefore a free shield. See
	 * {@link com.archetypes.Spearwall#guardSpeedMultiplier}.
	 *
	 * <p>Local-player only, and that is correct rather than a shortcut: players
	 * are movement-authoritative, so this IS the slowdown. It asks the real
	 * {@code guardingShield} rather than the published flag, because the owner's
	 * own client can and should answer without a tick of latency.
	 */
	@ModifyReturnValue(method = "itemUseSpeedMultiplier", at = @At("RETURN"))
	private float archetypes$spearwallGuardIsHeavy(final float original) {
		return com.archetypes.Spearwall.guardSpeedMultiplier((LocalPlayer) (Object) this, original);
	}

	/** The other half of the same stance: vanilla gates sprinting on the use
	 * item's {@code canSprint}, and a spear's is true. A guard you can sprint
	 * behind is not a guard. */
	@ModifyReturnValue(method = "isSlowDueToUsingItem", at = @At("RETURN"))
	private boolean archetypes$spearwallGuardStopsSprint(final boolean slow) {
		return slow || com.archetypes.Spearwall.guardStopsSprint((LocalPlayer) (Object) this);
	}
}
