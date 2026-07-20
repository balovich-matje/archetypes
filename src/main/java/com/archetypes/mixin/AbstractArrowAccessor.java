package com.archetypes.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** {@code baseDamage} has a setter but no getter; Reflection needs to halve
 * it, and True Shot needs to multiply it in place. */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
	@Accessor("baseDamage")
	double archetypes$getBaseDamage();

	@Accessor("baseDamage")
	void archetypes$setBaseDamage(double damage);

	@Accessor("pickupItemStack")
	net.minecraft.world.item.ItemStack archetypes$getPickupItemStack();

	/** {@code setPierceLevel} is private in 26.2 (the getter is public), and
	 * Punch Through wants vanilla's own piercing rather than a parallel
	 * hit-counter: the ignore set, the pierced-and-killed list the
	 * KILLED_BY_ARROW criterion reads, and the synced entity-data field all
	 * come with it. */
	@org.spongepowered.asm.mixin.gen.Invoker("setPierceLevel")
	void archetypes$setPierceLevel(byte level);
}
