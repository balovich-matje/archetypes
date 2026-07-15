package com.archetypes.mixin;

import com.archetypes.NodePurchases;
import com.archetypes.ProtectorNodes;
import com.archetypes.SubTree;
import com.archetypes.Tuning;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlocksAttacks.class)
public abstract class BlocksAttacksMixin {
	/**
	 * Reinforced Straps: Unbreaking I baked into whatever the player blocks with.
	 * {@code hurtBlockingItem} is the single place blocking costs durability, so
	 * skipping half its calls is exactly the enchant's 1/(level+1) survival rate.
	 */
	@Inject(method = "hurtBlockingItem", at = @At("HEAD"), cancellable = true)
	private void archetypes$reinforcedStraps(final Level level, final ItemStack stack,
			final LivingEntity entity, final InteractionHand hand, final float damage,
			final CallbackInfo ci) {
		if (!(entity instanceof ServerPlayer player)) {
			return;
		}

		int rank = ProtectorNodes.rank(SubTree.PROTECTOR,
				NodePurchases.owned(player, SubTree.PROTECTOR), ProtectorNodes.Family.UNBREAKING);

		if (rank > 0 && player.getRandom().nextFloat() < Tuning.STRAPS_SKIP_CHANCE) {
			ci.cancel();
		}
	}
}
