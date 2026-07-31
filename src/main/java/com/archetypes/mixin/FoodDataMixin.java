package com.archetypes.mixin;

import com.archetypes.NightForm;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The night form's halted regeneration.
 *
 * <p>Undead-ness alone cannot do this: vanilla's natural heal lives in
 * {@code FoodData.tick} and never consults
 * {@link net.minecraft.world.entity.LivingEntity#isInvertedHealAndHarm} — it
 * just calls {@code player.heal} off saturation or a full-enough bar. Both call
 * sites are cancelled here (the source was read: they are the only two heals in
 * the method, one for the saturation-fed fast regen and one for the slow one).
 *
 * <p>Cancelling AT the heal rather than at the method's head is deliberate: the
 * exhaustion bookkeeping above it still runs, so hunger keeps its normal
 * accounting under the lock {@link NightForm} pins it to and there is no
 * accumulated-exhaustion dump the moment the hour ends. The starvation branch
 * is never reached anyway — a locked-full bar cannot starve.
 */
@Mixin(FoodData.class)
public abstract class FoodDataMixin {
	// Below 1.21.2 the parameter widens to `Player`, and so does the OWNER of the wrapped
	// call: `Player.heal(F)V`, at offsets 163 and 221 of the 1.21.1 `FoodData.tick` and
	// nowhere else in the method — the same two heals the javadoc above names, the
	// saturation-fed fast one and the slow one. The owner is the part a resolver cannot warn
	// about, because `ServerPlayer` inherits `heal` and so the string resolves against the
	// class while matching no instruction in the target.
	//
	// No client early-out, and it is measured rather than assumed: `Player.aiStep` calls
	// `this.foodData.tick(this)` at offset 197, inside the `!level().isClientSide` branch that
	// opens at 186. The widened parameter is a ServerPlayer at runtime on every call.
	//? if >=1.21.2 {
	@Inject(method = "tick(Lnet/minecraft/server/level/ServerPlayer;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V"),
			cancellable = true)
	private void archetypes$nightFormHaltsRegen(final ServerPlayer player, final CallbackInfo ci) {
	//?} else {
	/*@Inject(method = "tick(Lnet/minecraft/world/entity/player/Player;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Player;heal(F)V"),
			cancellable = true)
	private void archetypes$nightFormHaltsRegen(final net.minecraft.world.entity.player.Player player,
			final CallbackInfo ci) {
	*///?}
		if (NightForm.isActive(player)) {
			ci.cancel();
		}
	}
}
