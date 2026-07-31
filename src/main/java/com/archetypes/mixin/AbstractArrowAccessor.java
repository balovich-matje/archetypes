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

	// ---- The arrow's refundable stack is a FIELD from 1.21 and a METHOD below it. ----
	//
	// MEASURED (`javap -p` on both mojmap `AbstractArrow`s), and this is the whole delta:
	//
	//   1.21.1   `private ItemStack pickupItemStack` — a real field, carrying the components
	//            of the stack the arrow was fired from; `getPickupItem()` is its reader.
	//   1.20.1   NO such field at all. There is only `protected abstract ItemStack
	//            getPickupItem()`, implemented per arrow type (a plain arrow answers one
	//            `minecraft:arrow`, a tipped arrow answers one carrying its potion).
	//
	// So the ACCESSOR becomes an INVOKER and the two call sites — Deadeye's and Marksman's
	// arrow refunds, both of which `.copy()` what they get — are untouched. The legacy method
	// is the same value by construction: on 1.21.1 `getPickupItem()` returns exactly the field
	// this accessor reads.
	//
	// Found by the 1.20.1 boot, not by the build: `remapJar` printed `Cannot remap
	// pickupItemStack because it does not exist in any of the targets` as a WARNING and
	// shipped the jar anyway, and the mod then died in `Apply Accessors` with
	// `InvalidAccessorException: No candidates were found matching pickupItemStack`. A remap
	// warning on a mixin member name is a boot failure that has not happened yet.
	//? if >=1.21 {
	@Accessor("pickupItemStack")
	net.minecraft.world.item.ItemStack archetypes$getPickupItemStack();
	//?} else {
	/*@org.spongepowered.asm.mixin.gen.Invoker("getPickupItem")
	net.minecraft.world.item.ItemStack archetypes$getPickupItemStack();
	*///?}

	/** {@code setPierceLevel} is private in 26.2 (the getter is public), and
	 * Punch Through wants vanilla's own piercing rather than a parallel
	 * hit-counter: the ignore set, the pierced-and-killed list the
	 * KILLED_BY_ARROW criterion reads, and the synced entity-data field all
	 * come with it. */
	@org.spongepowered.asm.mixin.gen.Invoker("setPierceLevel")
	void archetypes$setPierceLevel(byte level);
}
