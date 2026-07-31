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
//? if >=1.21.11 {
import net.minecraft.world.item.component.Consumable;
//?}
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
// RE-ROOTED, NOT EXCISED, BELOW 1.21.11. The WRAPPED CALL is the same one on every node —
// `FoodData.eat(FoodProperties)` — so the invariant this hook exists for (vanilla's clamped
// add, then the bar topped back up to the raised ceiling) is preserved exactly; only the
// method that HOSTS that call moves. 1.21.11 put it in `FoodProperties.onConsume`; below the
// boundary it is `Player.eat(Level, ItemStack, FoodProperties)`, whose first instruction is
// `getFoodData().eat(foodProperties)` (read out of the 1.21.1 bytecode, offset 5).
//
// The eater arrives differently and that is the whole of the shell's difference: a parameter
// above, `this` below. Everything from `int before` down is one implementation.
//? if >=1.21.11 {
@Mixin(FoodProperties.class)
//?} else {
/*@Mixin(net.minecraft.world.entity.player.Player.class)
*///?}
public abstract class FoodPropertiesMixin {
	//? if >=1.20.5 {
	//? if >=1.21.11 {
	@WrapOperation(method = "onConsume(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/component/Consumable;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V"))
	private void archetypes$bankHunger(final FoodData data, final FoodProperties properties,
			final Operation<Void> original, final Level level, final LivingEntity user,
			final ItemStack stack, final Consumable consumable) {
	//?} else {
	/*@WrapOperation(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V"))
	private void archetypes$bankHunger(final FoodData data, final FoodProperties properties,
			final Operation<Void> original) {
		final LivingEntity user = (LivingEntity) (Object) this;
	*///?}
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
	//?}

	// ---------------------------------------------------------------------------
	// STAGE 5 — THE ONE HANDLER IN THIS PORT WHOSE BODY IS WRITTEN TWICE, and the reason is
	// the byte-identity gate rather than the API: below 1.20.5 `FoodProperties` is a plain
	// class whose accessors are `getNutrition()` / `getSaturationModifier()` — a MODIFIER,
	// not an absolute saturation — and `FoodData.eat(FoodProperties)` does not exist at all;
	// `Player.eat(Level, ItemStack)` calls `FoodData.eat(Item, ItemStack)` instead. Hoisting
	// the two accessor reads into locals so the tail could stay shared would rewrite the
	// modern arms' bytecode, which the per-stage gate forbids (§5.9 gate 1). So the shell
	// AND the four lines below it are duplicated here, and the two must be edited together.
	//
	// The arithmetic is the same statement in a different vocabulary, and the conversion is
	// vanilla's own: `FoodData.eat(int, float)` adds `nutrition * modifier * 2` to
	// saturation, which is exactly what the record's `saturation()` carries from 1.20.5 up.
	// The ceiling, the "did vanilla already reach it" early-out and the saturation clamp are
	// character for character the same.
	//? if >=1.20.5 {
	//?} else {
	/*@WrapOperation(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/item/Item;"
							+ "Lnet/minecraft/world/item/ItemStack;)V"))
	private void archetypes$bankHunger(final FoodData data, final net.minecraft.world.item.Item item,
			final ItemStack food, final Operation<Void> original) {
		final LivingEntity user = (LivingEntity) (Object) this;
		final FoodProperties properties = item.getFoodProperties();
		int before = data.getFoodLevel();
		float saturationBefore = data.getSaturationLevel();

		original.call(data, item, food);

		if (properties == null || !(user instanceof Player player)) {
			return;
		}

		int ceiling = ColossusProtector.hungerCeiling(player);
		int wanted = Math.min(before + properties.getNutrition(), ceiling);

		if (wanted <= data.getFoodLevel()) {
			return;
		}

		data.setFoodLevel(wanted);
		data.setSaturation(Math.max(data.getSaturationLevel(),
				Math.min(saturationBefore
								+ properties.getNutrition() * properties.getSaturationModifier() * 2.0F,
						Math.min(wanted, FoodConstants.MAX_SATURATION))));
	}
	*///?}
}
