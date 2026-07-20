package com.archetypes.mixin;

import com.archetypes.ColossusProtector;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Well Fed's banked hunger, at the one call that fills the bar from food.
 *
 * <p>{@code FoodData.add} clamps to a hardcoded 20 and {@code FoodData} holds
 * no reference to its owner, so the ceiling cannot be raised from inside it
 * without inventing a back-pointer. Here the owner is a parameter, so the wrap
 * simply lets vanilla do its clamped add and then tops the bar back up to what
 * the raised ceiling would have allowed.
 *
 * <p>Runs on both sides — the client predicts the meal and the server's
 * {@code ClientboundSetHealthPacket} confirms it, and both read the same
 * synced node ownership, so they agree. Cake and the Saturation effect reach
 * {@code FoodData.eat} by other routes and are left on vanilla's 20; neither
 * is a bite of food in the player's hand.
 */
@Mixin(FoodProperties.class)
public abstract class FoodPropertiesMixin {
	@WrapOperation(method = "onConsume",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V"))
	private void archetypes$bankHunger(final FoodData data, final FoodProperties properties,
			final Operation<Void> original, final Level level, final LivingEntity user,
			final ItemStack stack, final Consumable consumable) {
		int before = data.getFoodLevel();
		float saturationBefore = data.getSaturationLevel();

		original.call(data, properties);

		if (!(user instanceof Player player)) {
			return;
		}

		int ceiling = ColossusProtector.hungerCeiling(player);
		int wanted = Math.min(before + properties.nutrition(), ceiling);

		if (wanted <= data.getFoodLevel()) {
			return;
		}

		data.setFoodLevel(wanted);
		// Vanilla's own clamp, re-run against the level it should have reached
		// — except that saturation keeps its vanilla ceiling. A bank that also
		// banked saturation would double the fast regen, and the author's note
		// says the regeneration rules do not move.
		data.setSaturation(Math.max(data.getSaturationLevel(),
				Math.min(saturationBefore + properties.saturation(),
						Math.min(wanted, FoodConstants.MAX_SATURATION))));
	}
}
