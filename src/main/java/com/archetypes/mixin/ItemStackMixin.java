package com.archetypes.mixin;

import com.archetypes.ColossusProtector;
import com.archetypes.ReinforcedStraps;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
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
	@ModifyReturnValue(method = "getUseDuration(Lnet/minecraft/world/entity/LivingEntity;)I", at = @At("RETURN"))
	private int archetypes$wellFed(final int original, final LivingEntity user) {
		if (original <= 0 || !(user instanceof Player player)
				|| !((ItemStack) (Object) this).has(DataComponents.FOOD)) {
			return original;
		}

		float factor = ColossusProtector.eatSpeedFactor(player);
		return factor >= 1.0F ? original : Math.max(1, Math.round(original * factor));
	}

	/**
	 * Reinforced Straps: a held shield rolls its durability at one more level of
	 * Unbreaking than it carries.
	 *
	 * <p>This one call is where every enchantment in the game gets to shrink a
	 * durability loss, and every path that damages an item reaches it —
	 * {@code hurtAndBreak} and {@code hurtWithoutBreaking} both go through the
	 * private method being injected. Wrapping it therefore covers the shield
	 * block, Instinctive Guard's passive block, and anything that learns to cost
	 * a shield durability later, without a list.
	 *
	 * <p>What is handed to the original operation is a BOOSTED COPY of the stack
	 * rather than a shrunken amount, so the arithmetic stays vanilla's own
	 * binomial at the raised level. See {@link ReinforcedStraps} for why the
	 * distinction matters and why the copy must not be the real stack.
	 *
	 * <p>The player arrives by {@code @Local(argsOnly = true)} because a
	 * {@code @WrapOperation} handler cannot capture the target method's
	 * arguments; it is the only {@code ServerPlayer} parameter, and it is null
	 * whenever a non-player wears an item down.
	 */
	@WrapOperation(method = "processDurabilityChange(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;)I",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;"
							+ "processDurabilityChange(Lnet/minecraft/server/level/ServerLevel;"
							+ "Lnet/minecraft/world/item/ItemStack;I)I"))
	private int archetypes$reinforcedStraps(final ServerLevel level, final ItemStack stack,
			final int amount, final Operation<Integer> original,
			@Local(argsOnly = true) final @Nullable ServerPlayer player) {
		return original.call(level,
				player == null ? stack : ReinforcedStraps.forDurabilityRoll(level, player, stack),
				amount);
	}
}
