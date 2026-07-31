package com.archetypes.mixin;

import com.archetypes.ColossusProtector;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlocksAttacks.class)
public abstract class BlocksAttacksMixin {
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
	@Inject(method = "disable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
	private void archetypes$immovableObject(final ServerLevel level, final LivingEntity entity,
			final float seconds, final ItemStack stack, final CallbackInfo ci) {
		if (entity instanceof ServerPlayer player
				&& ColossusProtector.immovableObject(player, level)) {
			ci.cancel();
		}
	}
}
