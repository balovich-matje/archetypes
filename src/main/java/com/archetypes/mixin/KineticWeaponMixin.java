package com.archetypes.mixin;

import com.archetypes.ColossusProtector;
import com.archetypes.Tuning;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.KineticWeapon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(KineticWeapon.class)
public abstract class KineticWeaponMixin {
	/**
	 * Free Hand: a Protector's braced spear does not run out.
	 *
	 * <p>The lifetime being fought here is {@code KineticWeapon.Condition}'s,
	 * not the use item's — see {@link ColossusProtector#bracesIndefinitely}.
	 * Every effect of a brace is gated on {@code ticksUsed <= maxDurationTicks}
	 * and {@code damageEntities} derives that number from exactly one thing it
	 * is handed: {@code stack.getUseDuration(holder) - ticksRemaining}. So the
	 * node is a rewrite of that one argument — hold the clock at a fixed point
	 * inside every window instead of letting it climb out of them.
	 *
	 * <p>Freezing rather than widening is deliberate. Widening would mean
	 * reconstructing three {@code Condition}s per material and would silently
	 * disagree with any spear a data pack adds; a frozen clock is correct for
	 * whatever windows the item actually declares, as long as the freeze point
	 * is inside the shortest one — fifty ticks, which is what iron and
	 * netherite ship and what {@link Tuning#FREE_HAND_BRACE_HOLD_TICKS} is
	 * sized against.
	 *
	 * <p>The speed halves of {@code Condition.test} are untouched, so a planted
	 * spear still only hurts what runs onto it. Common, not server-side: the
	 * client ticks its own use item and would otherwise mispredict a stab it is
	 * about to be told happened.
	 */
	@ModifyVariable(method = "damageEntities", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int archetypes$freeHandHoldsTheBrace(final int ticksRemaining, final ItemStack stack,
			final int ticksRemainingArg, final LivingEntity holder, final EquipmentSlot slot) {
		if (!(holder instanceof Player player)
				|| !ColossusProtector.bracesIndefinitely(player, stack)) {
			return ticksRemaining;
		}

		int duration = stack.getUseDuration(holder);
		int used = duration - ticksRemaining;
		int held = ((KineticWeapon) (Object) this).delayTicks() + Tuning.FREE_HAND_BRACE_HOLD_TICKS;

		// Before the freeze point the brace ages normally, so the wind-up and
		// the first stabs are vanilla's exactly.
		return used <= held ? ticksRemaining : duration - held;
	}
}
