package com.archetypes.client.mixin;

// STAGE 4 — THE ESP OUTLINE, REBUILT FROM TWO CHANNELS. This whole file is below-1.21.11 only
// and it is EntityRendererMixin's replacement, not its companion; exactly one of the two is
// compiled into any jar.
//
// From 1.21.11 up, `EntityRenderState.outlineColor` is a single field that is BOTH the ticket
// into the outline pass and the colour drawn, so one TAIL injection settles both questions at
// once. Below the boundary there is no render state at all and the two questions are asked
// separately, by two calls `LevelRenderer.renderLevel` makes back to back — read out of the
// 1.21.1 mojmap jar with `javap -c`, not remembered:
//
//     965: invokevirtual  shouldShowEntityOutlines:()Z
//     977: invokevirtual  net/minecraft/client/Minecraft.shouldEntityAppearGlowing:(L…/Entity;)Z
//    1001: invokevirtual  net/minecraft/world/entity/Entity.getTeamColor:()I
//    1010..1023  FastColor$ARGB32.red/green/blue, then 255
//    1026: invokevirtual  net/minecraft/client/renderer/OutlineBufferSource.setColor:(IIII)V
//
// so membership is `shouldEntityAppearGlowing` and colour is `getTeamColor`. BOTH ARE WRAPPED
// SCOPED TO `renderLevel` rather than globally, and that is the whole design decision here:
//
//   * `Minecraft.shouldEntityAppearGlowing` has four other callers on this version —
//     `LivingEntityRenderer` and the mushroom/sheep/slime/snow-golem layers — where it decides
//     whether an INVISIBLE entity is drawn as an outline-only silhouette instead of not at
//     all. A global hook would make every sensed invisible mob paint a silhouette in the
//     world, which is not what the newer nodes do; the render-state write there is read only
//     by the outline collector.
//   * `Entity.getTeamColor` is likewise reachable from `Display`.
//
// `@WrapOperation` and not `@ModifyExpressionValue` because the wrap handler is handed the
// RECEIVER, and the receiver is the entity — `renderLevel` is a thousand-instruction method
// and capturing the right `Entity` local out of it with `@Local` would be a guess. Each target
// occurs exactly ONCE in the whole class (`grep -c` on the disassembly: 1 and 1), so
// `injectors.defaultRequire: 1` resolves them unambiguously and would fail loudly if either
// moved.
//
// The precedence ExtraSensoryPerception documents is preserved exactly: our colour REPLACES
// the team colour when we have one, membership is OR'd so nothing vanilla used to outline
// stops being outlined, and an entity we neither mark nor sense is left completely alone.
//
// Listed only in versions/1.21.1-fabric/src/client/resources/archetypes.client.mixins.json.
//? if <1.21.11 {
/*import com.archetypes.client.ExtraSensoryPerception;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/^*
 * The Death Mark's red and Extra Sensory Perception's outlines, painted through
 * vanilla's own glowing machinery on versions that have no entity render state.
 *
 * <p>Client-only and read off the LOCAL player's own mark and rosters, so one
 * player's hunt can never tint another player's view.
 ^/
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	// Both handlers ask ExtraSensoryPerception the SAME question — conventions §5a: the
	// annotation is what forks between versions, the decision never does. `0` is
	// `EntityRenderState.NO_OUTLINE`'s value, spelled as a literal here because the class it
	// is declared on does not exist on this node (see that file's fork).
	// STAGE 5: `renderLevel`'s parameter list is 1.21's. On 1.20.1 it is
	// `(PoseStack, float partialTick, long finishNano, boolean outline, Camera, GameRenderer,
	// LightTexture, Matrix4f)` — measured with `javap -s`. The two CALLS this wraps are
	// unchanged and still inside it, so only the descriptor moves.
	//? if >=1.21 {
	@WrapOperation(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;Z"
			+ "Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;"
			+ "Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/Minecraft;shouldEntityAppearGlowing("
							+ "Lnet/minecraft/world/entity/Entity;)Z"))
	//?} else {
	/^@WrapOperation(method = "renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZ"
			+ "Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;"
			+ "Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/Minecraft;shouldEntityAppearGlowing("
							+ "Lnet/minecraft/world/entity/Entity;)Z"))
	^///?}
	private boolean archetypes$senseGlowing(final Minecraft client, final Entity entity,
			final Operation<Boolean> original) {
		return original.call(client, entity)
				|| ExtraSensoryPerception.outlineColor(entity) != 0;
	}

	//? if >=1.21 {
	@WrapOperation(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;Z"
			+ "Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;"
			+ "Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;getTeamColor()I"))
	//?} else {
	/^@WrapOperation(method = "renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZ"
			+ "Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;"
			+ "Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;getTeamColor()I"))
	^///?}
	private int archetypes$senseOutlineColor(final Entity entity, final Operation<Integer> original) {
		int color = ExtraSensoryPerception.outlineColor(entity);

		return color != 0 ? color : original.call(entity);
	}
}
*///?}
