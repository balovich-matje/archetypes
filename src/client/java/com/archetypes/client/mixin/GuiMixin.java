package com.archetypes.client.mixin;

// STAGE 4 — THE HUD REGISTRATION PATH, below 1.21.11. This whole file exists only there, where
// fabric-rendering-v1 has no `hud` package (0.116.14+1.21.1 ships fabric-rendering-v1 3.x) and
// there is therefore no `HudElementRegistry` to attach an element to or to wrap a vanilla one
// with. Skill Proficiencies hit this first (its design §5h / R-11) and its `GuiMixin` is the
// model; this one differs in WHAT it does, because Archetypes raises no vanilla element — that
// is Skill Proficiencies' job and its HUD_SHIFT is read live through SpecialitiesBridge.
//
// The eight `ArchetypesClient` calls, mapped one for one onto vanilla methods, each verified in
// the 1.21.1 mojmap jar by full descriptor (conventions §5h — `Gui` has ~20 overloads named `a`
// after remap, so a bare name is a coin toss):
//
//   attachElementAfter(HOTBAR, cooldown_bar | proc_indicators | mana_bar)
//       -> TAIL of renderItemHotbar(GuiGraphics, DeltaTracker)V
//          fabric's HOTBAR element IS this method; attaching after it means drawing after the
//          icons and before the jump meter / experience bar, which is what the three bars do
//          on the newer nodes.
//   attachElementAfter(FOOD_BAR, banked_hunger)  +  replaceElement(FOOD_BAR, hide when undead)
//       -> ONE @WrapMethod on renderFood(GuiGraphics, Player, II)V
//          The two are deliberately one handler rather than a wrap plus a TAIL inject: a
//          @WrapMethod renames its target, so a separate injector into the same method would
//          be binding to whichever copy the transformer left it, and the ordering of that is
//          not something to rely on. Written as one handler the semantics are exactly the
//          newer nodes': the row is suppressed, and the halo still draws after it either way.
//          BankedHungerHud's own first gate is `UndeadHud.active()`, so "either way" is not a
//          divergence — it is the same early return the 26.x element makes.
//   attachElementAfter(MISC_OVERLAYS, sun_blind | deadeye_focus)
//       -> TAIL of renderCameraOverlays(GuiGraphics, DeltaTracker)V
//          which is vignette + spyglass + pumpkin + frost + portal, i.e. exactly the ids
//          fabric groups as MISC_OVERLAYS. Anchoring here and not at the tail of Gui.render is
//          the whole point: these two wash the WORLD and must stay UNDER the bars.
//   replaceElement(AIR_BAR, shift up 10 while the mana row is showing)
//       -> @WrapOperation on GuiGraphics.blitSprite(Identifier,IIII)V inside
//          renderPlayerHealth(GuiGraphics)V
//          There is no air-bubble METHOD to wrap below the boundary: the bubbles are drawn
//          inline in renderPlayerHealth, under the "air" profiler section, and those two
//          blitSprite calls (offsets 582 and 609, `javap -c`) are the ONLY two in that method
//          — armour, hearts and food are drawn by renderArmor / renderHearts / renderFood.
//          Subtracting the shift from the blit's y is arithmetically the same as translating
//          the element by it, which is what the 26.x arm does, and it cannot leave an
//          unbalanced pose stack the way a push/pop pair split across two injection points
//          could. The amount and the gate both come from `ManaHud.airBarShift()`, declared in
//          the matching half of that class's own fork, because `visible()` is package-private
//          and this class is not in that package.
//
// Listed only in versions/1.21.1-fabric/src/client/resources/archetypes.client.mixins.json.
// HudMixin already targets `Gui` below 26.2 for the night form's grey hearts; two mixins on one
// target class is ordinary, and they touch different methods.
//
// ─── STAGE 5: THE SAME PATH AGAIN ON 1.20.1, ON A `Gui` THAT IS SHAPED DIFFERENTLY ────────
// Three of the four anchors above do not exist there (`javap -p -s` on the 1.20.1 mojmap
// jar): no `renderItemHotbar`, no `renderCameraOverlays`, no `renderFood` — the hotbar is
// `renderHotbar(F, GuiGraphics)V`, the overlays are inline in `render(GuiGraphics, F)V`, and
// the food row is drawn inside the private `renderPlayerHealth(GuiGraphics)V` along with
// hearts, armour and the air bubbles. What that costs, stated per element:
//
//   * the three BARS and the two WORLD WASHES all land at TAIL of `render(GuiGraphics,F)V`,
//     in the order the newer nodes draw them (washes first, then bars), so their order
//     RELATIVE TO EACH OTHER is preserved. What is lost is their order relative to VANILLA:
//     the two washes are drawn over the hotbar here instead of under it. `renderVignette` was
//     the tempting anchor and is refused — it is skipped entirely when the vignette is off,
//     which would make the two overlays flicker with an unrelated setting.
//   * BANKED HUNGER draws from the same TAIL rather than after the food row.
//   * THE UNDEAD FOOD-ROW SUPPRESSION IS EXCISED. There is no food method to wrap, and
//     wrapping `renderPlayerHealth` would take the hearts and the armour with it.
//   * THE AIR-BAR SHIFT IS EXCISED for the same reason one level down: the bubbles are drawn
//     with the same `blit` overload as the hearts, the armour and the food, so there is no
//     call to wrap that is only theirs. The mana row can overlap the bubbles underwater on
//     this node.
//
// All four are display-only and none of them touches a number. They are named here rather
// than in a changelog because the next person to read this file will ask.
//? if <1.21.11 && >=1.21 {
/*import com.archetypes.client.BankedHungerHud;
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
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * Archetypes' six HUD elements and two vanilla replacements, on the versions
 * that have no HudElementRegistry to register them with.
 *
 * <p>Every draw goes through the same six shared render methods the newer nodes
 * call; only the registration lives here (conventions §5l — the gate goes at
 * the shared funnel).
 ^/
@Mixin(Gui.class)
public abstract class GuiMixin {
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
	// for the reason the header gives.
	@WrapMethod(method = "renderFood(Lnet/minecraft/client/gui/GuiGraphics;"
			+ "Lnet/minecraft/world/entity/player/Player;II)V")
	private void archetypes$foodRow(final GuiGraphics graphics, final Player player, final int y,
			final int x, final Operation<Void> original) {
		if (!UndeadHud.active()) {
			original.call(graphics, player, y, x);
		}

		BankedHungerHud.render(graphics, Minecraft.getInstance().getTimer());
	}

	// replaceElement(AIR_BAR)
	@WrapOperation(method = "renderPlayerHealth(Lnet/minecraft/client/gui/GuiGraphics;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite("
							+ "Lnet/minecraft/resources/Identifier;IIII)V"))
	private void archetypes$raiseAirBar(final GuiGraphics graphics, final Identifier sprite,
			final int x, final int y, final int width, final int height,
			final Operation<Void> original) {
		original.call(graphics, sprite, x, y - ManaHud.airBarShift(), width, height);
	}
}
*///?} elif <1.21 {
/*import com.archetypes.client.BankedHungerHud;
import com.archetypes.client.CooldownBarHud;
import com.archetypes.client.DeadeyeOverlay;
import com.archetypes.client.ManaHud;
import com.archetypes.client.ProcIndicatorHud;
import com.archetypes.client.SunBlindOverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * Archetypes' six HUD elements on 1.20.1, where the only anchor that exists is the whole
 * frame. See this file's header for what each of the four Stage-4 anchors became and what
 * the two excised ones cost.
 ^/
@Mixin(Gui.class)
public abstract class GuiMixin {
	// The partial tick is `render`'s own argument here — no DeltaTracker to unwrap, and no
	// `Minecraft.getTimer()` to ask for one.
	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("TAIL"))
	private void archetypes$hud(final GuiGraphics graphics, final float partialTick,
			final CallbackInfo ci) {
		if (Minecraft.getInstance().options.hideGui) {
			return;
		}

		// Washes first, bars second — the newer nodes' own order, preserved among ours.
		SunBlindOverlay.render(graphics, partialTick);
		DeadeyeOverlay.render(graphics, partialTick);
		CooldownBarHud.render(graphics, partialTick);
		ProcIndicatorHud.render(graphics, partialTick);
		ManaHud.render(graphics, partialTick);
		BankedHungerHud.render(graphics, partialTick);
	}
}
*///?}
