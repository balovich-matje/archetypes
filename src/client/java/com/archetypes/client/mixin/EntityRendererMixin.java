package com.archetypes.client.mixin;

// STAGE 4 — THE WHOLE FILE IS ABOVE-1.21.11 ONLY. `EntityRenderer.extractRenderState` and the
// `EntityRenderState.outlineColor` field it writes are both `>=1.21.11`; below the boundary
// there is no render state and no single field to overwrite, so the two questions this mixin
// answers in one place are answered in two, by `LevelRendererMixin`. See that file's header
// and ExtraSensoryPerception's class doc for how the precedence survives the split.
//
// A compilation unit with a package declaration and no type declaration is legal and emits no
// `.class`, which is what keeps the name out of the 1.21.1 jar — and the per-node client mixin
// config at versions/1.21.1-fabric/src/client/resources/ drops the entry to match. Both halves
// are needed: a config naming an absent class is a hard boot failure, and a class present but
// unlisted is a silent no-op.
//? if >=1.21.11 {
import com.archetypes.client.ExtraSensoryPerception;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Death Mark's red and Extra Sensory Perception's outlines are painted
 * through vanilla's own glowing machinery: {@code EntityRenderState.outlineColor}
 * is the single field that decides both whether an entity joins the outline
 * pass and what colour it comes out, and the tail of extraction is where
 * vanilla itself writes it —
 * {@code state.outlineColor = appearsGlowing ? ARGB.opaque(entity.getTeamColor()) : 0},
 * the last thing {@code EntityRenderer.extractRenderState} does with it.
 *
 * <p><b>TAIL is what makes ours win.</b> Injecting after that line means our
 * colour replaces whatever the Glowing effect ({@code isCurrentlyGlowing}, via
 * {@code Minecraft.shouldEntityAppearGlowing}), a spectator's outline key, or a
 * scoreboard team colour ({@code Entity.getTeamColor}) had just put there. No
 * later stage recomputes the field: subclasses of {@code EntityRenderer} never
 * assign it, and submit-time code only reads it — {@code LivingEntityRenderer}
 * hands it straight to {@code submitModel}, where a non-zero value is the
 * ticket into the outline collector AND the colour drawn. So a marked mob that
 * is also glowing from Umbral Sight, or a marked player on a red-teamed
 * scoreboard, still comes out the mark's red.
 *
 * <p>Leaving the field alone when we have no colour is equally deliberate: an
 * entity we neither mark nor sense keeps vanilla's team-coloured glow exactly
 * as it was.
 *
 * <p>Client-only and read off the LOCAL player's own mark and rosters, so one
 * player's hunt can never tint another player's view.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;"
			+ "Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
			at = @At("TAIL"))
	private void archetypes$senseOutline(final Entity entity, final EntityRenderState state,
			final float partialTicks, final CallbackInfo ci) {
		int color = ExtraSensoryPerception.outlineColor(entity);

		if (color != EntityRenderState.NO_OUTLINE) {
			state.outlineColor = color;
		}
	}
}
//?}
