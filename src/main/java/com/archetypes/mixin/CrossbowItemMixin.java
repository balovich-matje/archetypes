package com.archetypes.mixin;

import com.archetypes.MarksmanNodes;
import com.archetypes.ModAttachments;
import com.archetypes.NodePurchases;
import com.archetypes.SubTree;
import com.archetypes.Tuning;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rapid Reload: while a crossbow kill's prime is banked, the next charge runs
 * faster — up to instant (floored at one tick, because vanilla divides by the
 * duration). Both sides compute the same number: the prime flag and the owned
 * nodes are synced to the owning client, which predicts the draw animation.
 * Only the server clears the prime, once the load actually lands.
 */
@Mixin(CrossbowItem.class)
public abstract class CrossbowItemMixin {
	@ModifyReturnValue(method = "getChargeDuration", at = @At("RETURN"))
	private static int archetypes$rapidReload(final int original, final ItemStack stack,
			final LivingEntity entity) {
		if (!(entity instanceof Player player)
				|| !Boolean.TRUE.equals(((AttachmentTarget) player)
						.getAttached(ModAttachments.CROSSBOW_PRIMED))) {
			return original;
		}

		int rank = MarksmanNodes.rank(SubTree.MARKSMAN, NodePurchases.owned(player, SubTree.MARKSMAN),
				MarksmanNodes.Family.RAPID_RELOAD);

		if (rank <= 0) {
			return original;
		}

		return Math.max(1, Math.round(original * (1.0F - Tuning.RAPID_RELOAD_PER_RANK * rank)));
	}

	@Inject(method = "releaseUsing", at = @At("TAIL"))
	private void archetypes$consumePrime(final ItemStack stack, final Level level,
			final LivingEntity entity, final int timeLeft, final CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof ServerPlayer player && CrossbowItem.isCharged(stack)) {
			((AttachmentTarget) player).removeAttached(ModAttachments.CROSSBOW_PRIMED);
		}
	}
}
