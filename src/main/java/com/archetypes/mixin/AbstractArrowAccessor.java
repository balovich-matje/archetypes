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
}
