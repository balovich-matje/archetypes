package com.archetypes.mixin;

import com.archetypes.ColossusProtector;
import com.archetypes.MagicArmaments;
import com.archetypes.SkillPoints;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
	/**
	 * Shadow every XP award into the archetype's bank.
	 *
	 * <p>Injected rather than wrapped because we do not touch the amount: the
	 * player keeps all of their experience and the archetype banks a copy, so
	 * levelling the tree never costs an enchant.
	 */
	@Inject(method = "giveExperiencePoints", at = @At("TAIL"))
	private void archetypes$bankExperience(final int amount, final CallbackInfo ci) {
		Player player = (Player) (Object) this;

		if (!player.level().isClientSide()) {
			SkillPoints.bank(player, amount);
		}
	}

	/**
	 * Well Fed: a bar that can hold more must also be fillable past 20.
	 *
	 * <p>{@code canEat} rather than {@code FoodData.needsFood} because
	 * {@code FoodData} does not know whose it is, and because this is the gate
	 * both sides consult — {@code Consumable.canConsume} refuses to start the
	 * meal on the client too, so a client that still thought 20 was full would
	 * never send the use at all.
	 */
	@Inject(method = "canEat", at = @At("HEAD"), cancellable = true)
	private void archetypes$bankedHungerIsEdible(final boolean canAlwaysEat,
			final CallbackInfoReturnable<Boolean> cir) {
		Player player = (Player) (Object) this;

		if (player.getFoodData().getFoodLevel() < ColossusProtector.hungerCeiling(player)) {
			cir.setReturnValue(true);
		}
	}
}
