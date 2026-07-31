package com.archetypes.client.mixin;

import com.archetypes.client.UndeadHud;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.client.gui.GuiGraphicsExtractor;
// 26.2 split the old `Gui` in two: the HUD renderer became `net.minecraft.client.gui.Hud`
// and `Gui` kept screen/toast management. Both class files exist on 26.2, so this is a
// MOVE of the heart drawing rather than a rename of one class, and only the owner forks —
// `extractHeart(GuiGraphicsExtractor, <owner>$HeartType, IIZZZ)V` and the
// `GuiGraphicsExtractor.blitSprite` target are otherwise identical on 26.1.2 (measured).
//? if >=26.2 {
import net.minecraft.client.gui.Hud;
//?} else {
/*import net.minecraft.client.gui.Gui;
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
 * because that enum is private to {@code Hud} and cannot be named from here.
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
	//?} else {
	/*@WrapOperation(method = "extractHeart(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Gui$HeartType;IIZZZ)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite("
							+ "Lcom/mojang/blaze3d/pipeline/RenderPipeline;"
							+ "Lnet/minecraft/resources/Identifier;IIII)V"))
	*///?}
	private void archetypes$undeadHearts(final GuiGraphicsExtractor graphics,
			final RenderPipeline pipeline, final Identifier sprite, final int x, final int y,
			final int width, final int height, final Operation<Void> original) {
		original.call(graphics, pipeline, UndeadHud.drain(sprite), x, y, width, height);
	}
}
