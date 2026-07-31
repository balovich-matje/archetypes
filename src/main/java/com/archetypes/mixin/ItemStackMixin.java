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
//? if >=1.21.11 {
import org.jspecify.annotations.Nullable;
//?} else {
/*import org.jetbrains.annotations.Nullable;
*///?}
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
	// RE-ROOTED below 1.21.11, and it is a re-root rather than a rename because the HOST moved,
	// not the call. `ItemStack.processDurabilityChange` does not exist there; the call this
	// wraps does, unchanged, and sits at offset 27 of
	// `hurtAndBreak(I,ServerLevel,ServerPlayer,Consumer)V` — the only occurrence in the whole
	// 1.21.1 `ItemStack`.
	//
	// R-20's test is the CONTRACT, and the contract here is the javadoc's own claim: "every
	// path that damages an item reaches it". Measured on 1.21.1 rather than assumed:
	// `hurtWithoutBreaking` does not exist on that version at all, and the other
	// `hurtAndBreak(I,LivingEntity,EquipmentSlot)V` overload delegates to this one (offset 51).
	// So the four-arg `hurtAndBreak` IS the single funnel there, which is exactly the role the
	// private method plays above the boundary. Nothing is lost and nothing new is covered.
	//
	// `@Local(argsOnly = true) ServerPlayer` still resolves for the same reason as above: it is
	// slot 3 of the new host, the only ServerPlayer argument, and null whenever a non-player
	// wears an item down.
	//
	// The boundary is `>=1.21.11` because that is the pair actually measured (present on
	// 1.21.11, absent on 1.21.1). If a node ever lands between 1.21.2 and 1.21.10 the legacy
	// arm is the safer bet of the two — and if the extraction had already happened there,
	// `injectors.defaultRequire: 1` says so at that node's first boot instead of silently
	// dropping the node.
	//? if >=1.21.11 {
	@WrapOperation(method = "processDurabilityChange(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;)I",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;"
							+ "processDurabilityChange(Lnet/minecraft/server/level/ServerLevel;"
							+ "Lnet/minecraft/world/item/ItemStack;I)I"))
	//?} else {
	/*@WrapOperation(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;"
			+ "Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;"
							+ "processDurabilityChange(Lnet/minecraft/server/level/ServerLevel;"
							+ "Lnet/minecraft/world/item/ItemStack;I)I"))
	*///?}
	private int archetypes$reinforcedStraps(final ServerLevel level, final ItemStack stack,
			final int amount, final Operation<Integer> original,
			@Local(argsOnly = true) final @Nullable ServerPlayer player) {
		return original.call(level,
				player == null ? stack : ReinforcedStraps.forDurabilityRoll(level, player, stack),
				amount);
	}
}
