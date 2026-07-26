package com.archetypes.mixin;

import com.mojang.math.Transformation;

import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@link Display}'s shape setters are all private in the shipped class — the
 * entity is built to be driven by NBT from commands, not by code — so the Spear
 * Phalanx spears reach them through here.
 *
 * <p>They LOOK public on the compile classpath, and that is a trap worth
 * naming: {@code fabric-transitive-access-wideners-v1} widens all five, so
 * javac and the decompiler both show {@code public final} while {@code javap}
 * on the game jar shows {@code private}. Calling them straight would therefore
 * compile, and would keep working only for as long as Fabric API keeps that
 * entry in its list. An {@code @Invoker} owes nothing to that list and still
 * fails loudly at class-load if a name moves.
 *
 * <p>Only the four the phalanx actually sets. Interpolation is two calls, not
 * one: the duration and the delay are read when the transformation lands, and
 * the client lerps from whatever it was showing to the new value, which is what
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

	/** The spears are small and brief; the default range would keep them synced
	 * to players who will never see the stab finish. */
	@Invoker("setViewRange")
	void archetypes$setViewRange(float range);
}
