package com.archetypes.mixin;

import com.archetypes.ColossusProtector;
import com.archetypes.NodePurchases;
import com.archetypes.ProtectorNodes;
import com.archetypes.SubTree;
import com.archetypes.Tuning;

import net.minecraft.server.level.ServerLevel;
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

	/**
	 * Immovable Object: the guard that normal means cannot break.
	 *
	 * <p>{@code disable} is the whole of "your shield is knocked aside" — the
	 * axe's disable and the Warden's both arrive by way of
	 * {@code Player.blockUsingItem} feeding it
	 * {@code getSecondsToDisableBlocking}, and this mod's own Unstoppable Force
	 * calls it directly for the reason its hook explains. Cancelling at the head
	 * of the one method they share is what makes the node a rule rather than a
	 * list of attackers, and it is why Unstoppable Force is refused too: that
	 * clash is authored on the Crusher's side, not won by omission here.
	 *
	 * <p>The second parameter is the <em>blocker</em>, not the attacker —
	 * {@code blockUsingItem} passes its own {@code this}. Verified against the
	 * bytecode, the same trap {@code LivingEntityMixin.archetypes$onShieldBlocked}
	 * documents.
	 */
	@Inject(method = "disable", at = @At("HEAD"), cancellable = true)
	private void archetypes$immovableObject(final ServerLevel level, final LivingEntity entity,
			final float seconds, final ItemStack stack, final CallbackInfo ci) {
		if (entity instanceof ServerPlayer player
				&& ColossusProtector.immovableObject(player, level)) {
			ci.cancel();
		}
	}
}
