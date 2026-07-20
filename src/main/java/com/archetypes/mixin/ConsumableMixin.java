package com.archetypes.mixin;

import com.archetypes.ColossusProtector;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hearty Meal, hung on the one method that finishes any consumable — food,
 * milk, potions alike.
 *
 * <p>The injection point is the {@code stack.consume} call, and both halves of
 * that matter. It is late enough that the item's own consume effects have
 * already run, so milk's clear-everything cannot wipe the Regeneration the
 * milk just earned; and it is before the shrink, so the stack still knows what
 * it is (a last bucket consumed to empty would otherwise match no tag).
 */
@Mixin(Consumable.class)
public abstract class ConsumableMixin {
	@Inject(method = "onConsume",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V"))
	private void archetypes$heartyMeal(final Level level, final LivingEntity user,
			final ItemStack stack, final CallbackInfoReturnable<ItemStack> cir) {
		if (!level.isClientSide() && user instanceof ServerPlayer player) {
			ColossusProtector.heartyMeal(player, stack);
		}
	}
}
