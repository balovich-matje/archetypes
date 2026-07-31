package com.archetypes.client.mixin;

// THIS WHOLE FILE IS 26.2-ONLY, and the reason is an absence rather than a rename:
// `net.minecraft.client.renderer.extract.LevelExtractor` does not exist below 26.2 — the
// whole `client.renderer.extract` package is missing from the 26.1.2 clientOnly jar
// (measured with `unzip -l`; the compiler agrees, "package
// net.minecraft.client.renderer.extract does not exist"). There is no equivalent hook to
// re-root onto, because extraction itself is what 26.2 introduced.
//
// So Extra Sensory Perception ships WITHOUT its wall-piercing half below 26.2. That is the
// accepted degradation (design §6 R-B3, the decision in force), NOT an approximation
// through some other chokepoint — approximating is exactly the silent-divergence class
// R-20 exists to catch. Everything else ESP does survives: the Death Mark's red outline is
// painted by EntityRendererMixin, whose target exists on every node, and
// `ExtraSensoryPerception.piercesWalls` stays compiled with no caller here.
//
// Mechanics of the excision — BOTH halves are needed (conventions §4):
//   * the file's whole body — imports, annotation, class declaration — sits inside one
//     `//?` block, so below 26.2 the compilation unit holds nothing but this package
//     statement and produces NO `.class`. Skill Proficiencies' client/mixin/GuiMixin.java
//     is the same trick pointing the other way.
//   * `//?` DOES NOTHING IN `.json`, so the mixin config cannot gate the entry. It is
//     dropped by the node script's `processResources` line-blank instead — see
//     `strippedMixinEntries` in build.fabric.gradle.kts. A listed mixin whose class is not
//     in the jar is a hard boot failure, not a warning.
//
// The class doc that used to sit here, kept as LINE comments on purpose: a `*/` inside a
// disabled `//?` branch would close Stonecutter's own comment early.
//
// Extra Sensory Perception is worth nothing underground unless sensed creatures survive
// occlusion culling: `isEntityVisible` drops any entity whose chunk section the occlusion
// graph has decided is hidden, and an entity that never reaches extraction never reaches
// the outline pass either.
//
// This hook is the whole of what Stalk buys. The Death Mark's red is painted by
// `EntityRendererMixin` for any mark, node or no node; what Stalk adds is being on the
// allowed side of THIS test, so the red keeps showing once the quarry puts a wall between
// you. Hence `ExtraSensoryPerception.piercesWalls` rather than the outline colour — asking
// "is it outlined?" here would have handed the through-wall perk to every mark for free.
//
// Only that one test is relaxed, and only for entities that earned it. The frustum check
// above it stands — an outline behind the camera is work nobody sees, the ESP roster is
// capped by a 32-block query and a mark is one entity, so this can never let more than a
// handful of extras through.
//? if >=26.2 {
import com.archetypes.client.ExtraSensoryPerception;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

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
//?}
