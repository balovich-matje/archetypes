package com.archetypes.mixin;

import com.archetypes.ColossusProtector;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Well Fed's faster eating.
 *
 * <p>On {@code ItemStack} rather than {@code Item} because this is the call
 * every consumer actually makes — {@code LivingEntity.startUsingItem} sets the
 * countdown from it and {@code getTicksUsingItem} measures against it — so
 * scaling it here catches an item that overrides {@code getUseDuration} as well
 * as one that reads its {@code consume_seconds}.
 *
 * <p>Gated on the FOOD component, so it is food that gets faster: potions,
 * milk and a drawn bow keep their own timing. The floor of one tick is not
 * reachable at the shipped ranks (a 25%/50% cut) but is there because a zero
 * would make {@code Consumable.startConsuming} take the instant-use branch and
 * change what eating IS.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@ModifyReturnValue(method = "getUseDuration", at = @At("RETURN"))
	private int archetypes$wellFed(final int original, final LivingEntity user) {
		if (original <= 0 || !(user instanceof Player player)
				|| !((ItemStack) (Object) this).has(DataComponents.FOOD)) {
			return original;
		}

		float factor = ColossusProtector.eatSpeedFactor(player);
		return factor >= 1.0F ? original : Math.max(1, Math.round(original * factor));
	}
}
