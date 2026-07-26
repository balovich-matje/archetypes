package com.archetypes.mixin;

import com.mojang.math.Transformation;

import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@link Display}'s shape setters are all private — the class is built to be
 * driven by NBT from commands, not by code — so the Ground Slam phantoms reach
 * them through here.
 *
 * <p>Only the four the phalanx actually sets. Interpolation is two calls, not
 * one: the duration and delay are read when the transformation lands, and the
 * client lerps from whatever it was showing to the new value, which is what
 * turns "set a translation" into "thrust the spear".
 */
@Mixin(Display.class)
public interface DisplayAccessor {
	@Invoker("setTransformation")
	void archetypes$setTransformation(Transformation transformation);

	@Invoker("setTransformationInterpolationDuration")
	void archetypes$setInterpolationDuration(int ticks);

	@Invoker("setTransformationInterpolationDelay")
	void archetypes$setInterpolationDelay(int ticks);

	/** Phantoms are small and brief; the default range would keep them synced
	 * to players who will never see the stab finish. */
	@Invoker("setViewRange")
	void archetypes$setViewRange(float range);
}
