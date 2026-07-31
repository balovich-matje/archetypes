package com.archetypes.client.mixin;

import com.archetypes.client.BankedHungerHud;
import com.archetypes.client.CooldownBarHud;
import com.archetypes.client.DeadeyeOverlay;
import com.archetypes.client.ManaHud;
import com.archetypes.client.ProcIndicatorHud;
import com.archetypes.client.SunBlindOverlay;
import com.archetypes.client.UndeadHud;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>THE HUD ON 1.21.1-NEOFORGE — design R-11, and the answer here is neither of the two
 * Skill Proficiencies found.</b> That mod's NeoForge node uses
 * {@code RegisterGuiLayersEvent.wrapLayer} and ships its {@code GuiMixin} unlisted; its Forge
 * node writes a whole per-node {@code ForgeGuiMixin}. This node does a third thing, for a
 * reason that is specific to what Archetypes' HUD actually is: <b>Archetypes RAISES NO
 * VANILLA ELEMENT</b> — that is Skill Proficiencies' job and its {@code HUD_SHIFT} is read
 * live through {@code SpecialitiesBridge} — so there is nothing here that wants a layer
 * wrapper. It draws six elements and conditions two vanilla draws, and <b>three of the shared
 * {@code GuiMixin}'s four anchors survive NeoForge's {@code Gui} patch completely
 * untouched</b>.
 *
 * <p>Measured against {@code net/minecraft/client/gui/Gui.java} in
 * {@code neoforge-21.1.243-sources.jar}, anchor by anchor:
 *
 * <table border="1">
 * <caption>The shared GuiMixin's four anchors on this loader</caption>
 * <tr><th>anchor</th><th>state on NeoForge</th></tr>
 * <tr><td>{@code renderItemHotbar(GuiGraphics,DeltaTracker)V} TAIL</td>
 *     <td><b>live</b> — called from {@code renderHotbar}, which is the {@code HOTBAR}
 *     layer ({@code :555}, registered {@code :192})</td></tr>
 * <tr><td>{@code renderCameraOverlays(GuiGraphics,DeltaTracker)V} TAIL</td>
 *     <td><b>live</b> — registered as the {@code CAMERA_OVERLAYS} layer itself
 *     ({@code :193})</td></tr>
 * <tr><td>{@code renderFood(GuiGraphics,Player,II)V} WrapMethod</td>
 *     <td><b>live</b> — called from {@code renderFoodLevel} ({@code :943}), which is the
 *     {@code FOOD_LEVEL} layer ({@code :191})</td></tr>
 * <tr><td>{@code renderPlayerHealth(GuiGraphics)V}, WrapOperation on its
 *     {@code blitSprite}</td>
 *     <td><b>DEAD, AND IT WOULD HAVE BEEN A BOOT CRASH</b></td></tr>
 * </table>
 *
 * <p>The fourth one in full, because it is the whole reason this file exists.
 * {@code renderPlayerHealth} on this loader is
 * {@code @Deprecated // Neo: Split up into different layers} ({@code :871}) and its body is
 * four forwarding calls — {@code renderHealthLevel}, {@code renderArmorLevel},
 * {@code renderFoodLevel}, {@code renderAirLevel} — with <b>no {@code blitSprite} in it at
 * all</b>, and no caller either ({@code maybeRenderPlayerHealth} is itself only reachable
 * from the deprecated {@code renderHotbarAndDecorations}, which nothing calls). So the
 * shared handler's {@code @WrapOperation} would scan zero targets, and with
 * {@code injectors.defaultRequire: 1} on every variant that is a mixin-apply failure at
 * client boot — not a silent no-op. The air bubbles moved to {@code renderAirLevel}
 * ({@code :950-976}), which is the {@code AIR_LEVEL} layer and contains <b>exactly the two
 * {@code blitSprite(ResourceLocation,IIII)V} calls</b> ({@code :966} {@code AIR_SPRITE},
 * {@code :968} {@code AIR_BURSTING_SPRITE}) and no others — a strictly better anchor than
 * the one vanilla offers, because there is nothing else in the method to catch.
 *
 * <p>Hence: this file, listed in this node's client mixin config, with the shared
 * {@code GuiMixin} present in the jar but NOT listed — Skill Proficiencies' rule verbatim,
 * because a present-but-unlisted class is silently unused whereas excluding it from the jar
 * turns a future config entry into a boot crash.
 *
 * <p><b>THE ROUTE NOT TAKEN, and why, because it looks like the obvious one.</b> Doing this
 * with {@code RegisterGuiLayersEvent} the way Skill Proficiencies does breaks on the food
 * row. Suppressing the {@code FOOD_LEVEL} LAYER while the night form is up would also skip
 * {@code renderFoodLevel}'s {@code rightHeight += 10} ({@code :945}), and {@code rightHeight}
 * is a NeoForge-only running layout cursor that {@code renderAirLevel} and
 * {@code maybeRenderSelectedItemName} both read — so the air bar and the held-item name
 * would jump ten pixels whenever a vampire went underwater. Wrapping {@code renderFood}, the
 * inner DRAW, leaves that bookkeeping alone, which is exactly what the Fabric sibling gets
 * for free (vanilla 1.21.1 has no such cursor). Reproducing the cursor's own condition by
 * hand would mean reimplementing two private {@code Gui} methods, which is the kind of
 * approximation R-20 exists to catch.
 *
 * <p>The four handlers below are the shared file's, character for character, except for the
 * fourth's {@code method =}. Every draw still goes through the same six shared render methods
 * every other node calls; only the registration lives here (conventions §5l — the gate goes
 * at the shared funnel). <b>Keep it in step with
 * {@code src/client/java/com/archetypes/client/mixin/GuiMixin.java}</b>: a change to what the
 * HUD draws has to be made in both, and this file is the copy the review has to remember.
 */
@Mixin(Gui.class)
public abstract class NeoForgeGuiMixin {
	// after VanillaHudElements.HOTBAR
	@Inject(method = "renderItemHotbar(Lnet/minecraft/client/gui/GuiGraphics;"
			+ "Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
	private void archetypes$afterHotbar(final GuiGraphics graphics, final DeltaTracker delta,
			final CallbackInfo ci) {
		CooldownBarHud.render(graphics, delta);
		ProcIndicatorHud.render(graphics, delta);
		ManaHud.render(graphics, delta);
	}

	// after VanillaHudElements.MISC_OVERLAYS
	@Inject(method = "renderCameraOverlays(Lnet/minecraft/client/gui/GuiGraphics;"
			+ "Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
	private void archetypes$afterMiscOverlays(final GuiGraphics graphics, final DeltaTracker delta,
			final CallbackInfo ci) {
		SunBlindOverlay.render(graphics, delta);
		DeadeyeOverlay.render(graphics, delta);
	}

	// replaceElement(FOOD_BAR) AND attachElementAfter(FOOD_BAR, banked_hunger), in one handler
	// for the reason the shared file's header gives: a @WrapMethod renames its target, so a
	// separate injector into the same method would bind to whichever copy the transformer left
	// it, and that ordering is not something to rely on.
	@WrapMethod(method = "renderFood(Lnet/minecraft/client/gui/GuiGraphics;"
			+ "Lnet/minecraft/world/entity/player/Player;II)V")
	private void archetypes$foodRow(final GuiGraphics graphics, final Player player, final int y,
			final int x, final Operation<Void> original) {
		if (!UndeadHud.active()) {
			original.call(graphics, player, y, x);
		}

		BankedHungerHud.render(graphics, Minecraft.getInstance().getTimer());
	}

	// replaceElement(AIR_BAR) — THE ONE LINE THAT DIFFERS FROM THE SHARED FILE. Subtracting
	// the shift from the blit's y is arithmetically the same as translating the element by
	// it, which is what the 26.x arm does, and it cannot leave an unbalanced pose stack the
	// way a push/pop pair split across two injection points could. The amount and the gate
	// both come from ManaHud.airBarShift().
	@WrapOperation(method = "renderAirLevel(Lnet/minecraft/client/gui/GuiGraphics;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite("
							+ "Lnet/minecraft/resources/ResourceLocation;IIII)V"))
	private void archetypes$raiseAirBar(final GuiGraphics graphics, final ResourceLocation sprite,
			final int x, final int y, final int width, final int height,
			final Operation<Void> original) {
		original.call(graphics, sprite, x, y - ManaHud.airBarShift(), width, height);
	}
}
