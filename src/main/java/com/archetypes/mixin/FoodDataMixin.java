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
	@Inject(method = "tick",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V"),
			cancellable = true)
	private void archetypes$nightFormHaltsRegen(final ServerPlayer player, final CallbackInfo ci) {
		if (NightForm.isActive(player)) {
			ci.cancel();
		}
	}
}
