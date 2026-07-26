package com.archetypes.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * What the Ground Slam phantoms are holding, and how it is posed.
 *
 * <p>{@code THIRD_PERSON_RIGHT_HAND} is the transform that matters: it is the
 * one the game already uses to draw a spear in a hand, so the phantom's spear
 * sits at the angle a carried spear sits at instead of the flat-on-the-floor
 * {@code NONE} an ItemDisplay defaults to.
 */
@Mixin(Display.ItemDisplay.class)
public interface ItemDisplayAccessor {
	@Invoker("setItemStack")
	void archetypes$setItemStack(ItemStack stack);

	@Invoker("setItemTransform")
	void archetypes$setItemTransform(ItemDisplayContext context);
}
