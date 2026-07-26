package com.archetypes.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * What the Spear Phalanx spears are, and which display transform poses them.
 * Private in the shipped class for the same reason the rest of {@link Display}
 * is — see {@link DisplayAccessor} for why an {@code @Invoker} is used even
 * though the compile classpath shows these widened.
 *
 * <p>{@code THIRD_PERSON_RIGHT_HAND} is the transform that matters, and it is
 * not a taste pick: {@code spear_in_hand.json} declares
 * {@code rotation [5, 270, -40]} there, which composes to exactly "stand the
 * sprite's 45-degree diagonal up along +Y". That known base pose is what
 * {@code SpearPhalanx.pose} rotates down to the horizon and past it; every
 * other context either has no entry for a spear or falls back to the flat
 * {@code minecraft:item/iron_spear} sprite.
 */
@Mixin(Display.ItemDisplay.class)
public interface ItemDisplayAccessor {
	@Invoker("setItemStack")
	void archetypes$setItemStack(ItemStack stack);

	@Invoker("setItemTransform")
	void archetypes$setItemTransform(ItemDisplayContext context);
}
