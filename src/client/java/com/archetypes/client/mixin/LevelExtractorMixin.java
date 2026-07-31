package com.archetypes.client.mixin;

import com.archetypes.client.ExtraSensoryPerception;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Extra Sensory Perception is worth nothing underground unless sensed
 * creatures survive occlusion culling: {@code isEntityVisible} drops any
 * entity whose chunk section the occlusion graph has decided is hidden, and
 * an entity that never reaches extraction never reaches the outline pass
 * either.
 *
 * <p>This hook is the whole of what Stalk buys. The Death Mark's red is
 * painted by {@code EntityRendererMixin} for any mark, node or no node; what
 * Stalk adds is being on the allowed side of THIS test, so the red keeps
 * showing once the quarry puts a wall between you. Hence
 * {@link ExtraSensoryPerception#piercesWalls} rather than the outline colour —
 * asking "is it outlined?" here would have handed the through-wall perk to
 * every mark for free.
 *
 * <p>Only that one test is relaxed, and only for entities that earned it. The
 * frustum check above it stands — an outline behind the camera is work nobody
 * sees, the ESP roster is capped by a 32-block query and a mark is one entity,
 * so this can never let more than a handful of extras through.
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
	@ModifyExpressionValue(method = "isEntityVisible(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/LevelRenderer;"
							+ "isSectionCompiledAndVisible(Lnet/minecraft/core/BlockPos;)Z"))
	private boolean archetypes$senseThroughWalls(final boolean visible, final Entity entity,
			final Frustum frustum, final double camX, final double camY, final double camZ) {
		return visible || ExtraSensoryPerception.piercesWalls(entity);
	}
}
