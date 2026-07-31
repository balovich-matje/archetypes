package com.archetypes.client.mixin;

import com.archetypes.client.UndeadHud;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
// STAGE 4 CORRECTION, and it is the kind of bug this whole gate exists to catch. Stage 4-B1
// wrote the below-26.1 arm with the SIX-argument blitSprite — pipeline first — because that is
// what 1.21.11 has. 1.21.1 does not: `GuiGraphics` there declares
// `blitSprite(ResourceLocation, IIII)V` and no pipeline-taking overload at all, and its
// `Gui.renderHeart` calls exactly that (`javap -c`, offset 21). A mixin `target =` is a STRING,
// so javac saw nothing and the node compiled clean; it would have failed at apply time, at
// boot, on the client only. So the chain gains a FOURTH arm at 1.21.11 and the handler's
// parameter list forks with it.
//
// `com.mojang.blaze3d.pipeline.RenderPipeline` is a trap of its own on this node: the name
// EXISTS on 1.21.1 but is a different class entirely (the old render-call recorder, with
// `renderCalls`/`isRecording` fields). Importing it below the boundary would compile and mean
// nothing, so the import is gated too.
//? if >=1.21.11 {
import com.mojang.blaze3d.pipeline.RenderPipeline;
//?}

// THE HEART DRAW IS THE MOD'S MOST FORKED SINGLE HOOK, and after Stage 3 it is a THREE-arm
// chain on two independent boundaries that happen to meet in one descriptor:
//
//   >=26.2   `net.minecraft.client.gui.Hud.extractHeart(GuiGraphicsExtractor, Hud$HeartType, IIZZZ)V`
//   >=26.1   `net.minecraft.client.gui.Gui.extractHeart(GuiGraphicsExtractor, Gui$HeartType, IIZZZ)V`
//   else     `net.minecraft.client.gui.Gui.renderHeart(GuiGraphics,          Gui$HeartType, IIZZZ)V`
//
//   * the OWNER moves at 26.2, which split the old `Gui` in two — the HUD renderer became
//     `Hud`, `Gui` kept screen/toast management. Both class files exist on 26.2, so this is a
//     move of the heart drawing rather than a rename of one class.
//   * the METHOD and its first parameter move at 26.1, the extract-vs-immediate boundary.
//
// What does NOT move, and it is what keeps one handler body serving every node: the wrapped
// call is `blitSprite(RenderPipeline, Identifier, IIII)V` on BOTH graphics types.
// Read out of the 1.21.11 `Gui.renderHeart` bytecode rather than assumed —
//     21: invokevirtual  // Method …/GuiGraphics.blitSprite:(L…/RenderPipeline;L…/Identifier;IIII)V
// — the same six-arg overload 26.x's extractHeart calls, in the same position, taking the
// sprite the `HeartType.getSprite(ZZZ)` immediately above it produced. So conventions §5a
// holds in its strongest form here: three annotations, one implementation.
//
// A NOTE ON THE DESCRIPTOR, because this is the first REMAPPED node (conventions §5h): every
// `method =` and `target =` here is a full descriptor, which is what makes the below-26.1
// arm resolvable at all. `Gui` after remap has ~20 overloads named `a`; a bare `renderHeart`
// would be a coin toss, not a lookup.
//? if >=26.2 {
import net.minecraft.client.gui.Hud;
//?} else {
/*import net.minecraft.client.gui.Gui;
*///?}
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The night form's health bar: every heart it draws is swapped for our own
 * GREY sprite — vanilla's heart geometry with its red ramp replaced, so the
 * silhouette and the socket alignment are unchanged. Vanilla's withered set is
 * deliberately left alone: it means the Wither, and only the Wither.
 *
 * <p>The swap happens at the blit rather than at {@code HeartType.forPlayer}
 * because that enum is private to the HUD class and cannot be named from here.
 * Wrapping the sprite id instead needs no access widener, and it catches the
 * absorption and poisoned rows too, which the type alone would not.
 */
//? if >=26.2 {
@Mixin(Hud.class)
//?} else {
/*@Mixin(Gui.class)
*///?}
public abstract class HudMixin {
	// Conventions §5a: the ANNOTATION forks, the handler body never does.
	//? if >=26.2 {
	@WrapOperation(method = "extractHeart(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Hud$HeartType;IIZZZ)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite("
							+ "Lcom/mojang/blaze3d/pipeline/RenderPipeline;"
							+ "Lnet/minecraft/resources/Identifier;IIII)V"))
	//?} elif >=26.1 {
	/*@WrapOperation(method = "extractHeart(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Gui$HeartType;IIZZZ)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite("
							+ "Lcom/mojang/blaze3d/pipeline/RenderPipeline;"
							+ "Lnet/minecraft/resources/Identifier;IIII)V"))
	*///?} elif >=1.21.11 {
	/*@WrapOperation(method = "renderHeart(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Gui$HeartType;IIZZZ)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite("
							+ "Lcom/mojang/blaze3d/pipeline/RenderPipeline;"
							+ "Lnet/minecraft/resources/Identifier;IIII)V"))
	*///?} else {
	/*@WrapOperation(method = "renderHeart(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Gui$HeartType;IIZZZ)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite("
							+ "Lnet/minecraft/resources/Identifier;IIII)V"))
	*///?}
	//? if >=26.1 {
	private void archetypes$undeadHearts(final GuiGraphicsExtractor graphics,
			final RenderPipeline pipeline, final Identifier sprite, final int x, final int y,
			final int width, final int height, final Operation<Void> original) {
		original.call(graphics, pipeline, UndeadHud.drain(sprite), x, y, width, height);
	}
	//?} elif >=1.21.11 {
	/*private void archetypes$undeadHearts(final GuiGraphics graphics,
			final RenderPipeline pipeline, final Identifier sprite, final int x, final int y,
			final int width, final int height, final Operation<Void> original) {
		original.call(graphics, pipeline, UndeadHud.drain(sprite), x, y, width, height);
	}
	*///?} else {
	/*private void archetypes$undeadHearts(final GuiGraphics graphics,
			final Identifier sprite, final int x, final int y,
			final int width, final int height, final Operation<Void> original) {
		original.call(graphics, UndeadHud.drain(sprite), x, y, width, height);
	}
	*///?}
}
