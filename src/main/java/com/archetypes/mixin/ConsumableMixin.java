package com.archetypes.mixin;

import com.archetypes.ColossusProtector;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
//? if >=1.21.11 {
import net.minecraft.world.item.component.Consumable;
//?}
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
// RE-ROOTED, NOT EXCISED, BELOW 1.21.11 (conventions §5a: the annotation forks, the effect
// never does). The `Consumable` component is where 1.21.11 put "one method finishes any
// consumable"; below it that method is `ItemStack.finishUsingItem`, which food, milk and
// potions all pass through (`LivingEntity.completeUsingItem` calls it and nothing else
// does — read out of the 1.21.1 bytecode).
//
// R-20 — the substitute reproduces the CONTRACT, not merely a plausible place:
//   * AFTER the item's own effects, so milk's clear-everything cannot wipe the
//     Regeneration the milk just earned. `@WrapOperation` calling `original` first is what
//     buys that ordering; a HEAD inject would have the opposite one.
//   * with the stack STILL IDENTIFIABLE. On 1.21.1 `ItemStack.getItem()` returns
//     `Items.AIR` once the count reaches zero (measured), so a last bite would match no
//     tag if the stack were read afterwards. The copy is taken before the call.
//   * SERVER ONLY. The modern host is reached on both sides and carries the same guard;
//     the legacy arm keeps it verbatim.
//? if >=1.21.11 {
@Mixin(Consumable.class)
public abstract class ConsumableMixin {
	@Inject(method = "onConsume(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V"))
	private void archetypes$heartyMeal(final Level level, final LivingEntity user,
			final ItemStack stack, final CallbackInfoReturnable<ItemStack> cir) {
		if (!level.isClientSide() && user instanceof ServerPlayer player) {
			ColossusProtector.heartyMeal(player, stack);
		}
	}
}
//?} else {
/*@Mixin(net.minecraft.world.item.ItemStack.class)
public abstract class ConsumableMixin {
	@com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
			method = "finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/item/Item;finishUsingItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack archetypes$heartyMeal(final net.minecraft.world.item.Item item,
			final ItemStack stack, final Level level, final LivingEntity user,
			final com.llamalad7.mixinextras.injector.wrapoperation.Operation<ItemStack> original) {
		ItemStack eaten = stack.copy();
		ItemStack result = original.call(item, stack, level, user);

		if (!level.isClientSide() && user instanceof ServerPlayer player) {
			ColossusProtector.heartyMeal(player, eaten);
		}

		return result;
	}
}
*///?}
