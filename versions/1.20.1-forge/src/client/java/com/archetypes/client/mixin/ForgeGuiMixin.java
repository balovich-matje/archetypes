package com.archetypes.client.mixin;

import com.archetypes.client.BankedHungerHud;
import com.archetypes.client.CooldownBarHud;
import com.archetypes.client.DeadeyeOverlay;
import com.archetypes.client.ManaHud;
import com.archetypes.client.ProcIndicatorHud;
import com.archetypes.client.SunBlindOverlay;

import net.minecraftforge.client.gui.overlay.ForgeGui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * THE R-11 ANSWER FOR {@code 1.20.1-forge}. A PER-NODE OVERRIDE source file — it lives under
 * {@code versions/1.20.1-forge/src/}, so it exists on this node and nowhere else, which is why
 * it can name {@code net.minecraftforge} without any exclusion glob (the globs in the three
 * node scripts cover {@code com/archetypes/client/Forge*.java}, one package up).
 *
 * <p><b>WHY THE SHARED {@code GuiMixin} CANNOT BE USED HERE, measured.</b>
 * {@code net.minecraftforge.client.gui.overlay.ForgeGui extends Gui} and Forge installs it as
 * the live {@code Minecraft.gui} — read straight out of
 * {@code patches/net/minecraft/client/Minecraft.java.patch}:
 * {@code this.gui = new net.minecraftforge.client.gui.overlay.ForgeGui(this);}. That class
 * OVERRIDES {@code Gui.render(GuiGraphics,float)} and never calls {@code super} on it: the
 * override throws vanilla's whole HUD body away and dispatches
 * {@code GuiOverlayManager.getOverlays()} instead. The entire class contains four
 * {@code super.} calls and {@code render} is not one of them.
 *
 * <p>{@code GuiMixin}'s {@code <1.21} arm is a single {@code @Inject} at TAIL of
 * {@code Gui.render(GuiGraphics,F)V}. On this node that mixin would APPLY CLEANLY AND NEVER
 * RUN — all six of this mod's HUD elements silently absent, with no error anywhere and a
 * green mixin audit. That is why this node's {@code archetypes.client.mixins.json} override
 * does not list {@code GuiMixin} and lists this class instead. {@code GuiMixin}'s body is
 * version-gated rather than loader-gated, so its class is still compiled into the jar; it is
 * simply in no config, so Mixin never loads it.
 *
 * <p><b>WHY THE SIX DRAWS AND NOT SEVEN OR FIVE.</b> This is exactly the body of
 * {@code GuiMixin}'s {@code <1.21} arm, in the same order, calling the same six shared render
 * methods with the same {@code partialTick}: washes first, bars second — the order the newer
 * nodes draw them in, preserved among ours. The {@code hideGui} early-out is kept for the same
 * reason it is there: F1 must hide the mod's HUD too. Conventions §5l — the gate goes at the
 * shared funnel, and every one of those six methods is the same one all seven nodes call.
 *
 * <p><b>WHY THERE IS NOTHING ELSE IN THIS FILE.</b> Skill Proficiencies' {@code ForgeGuiMixin}
 * additionally wraps seven {@code ForgeGui} methods to RAISE the vanilla HUD by its
 * {@code HUD_SHIFT}. That is its job and not this mod's: Archetypes raises no vanilla element,
 * it READS the shift through {@code SpecialitiesBridge.hudShift()} so its own rows sit above
 * whatever Skill Proficiencies did. Duplicating the raise here would double it.
 *
 * <p>{@code @At("TAIL")} takes the LAST return of {@code ForgeGui.render}, which has two — the
 * early one is where {@code RenderGuiEvent.Pre} was cancelled — so a HUD another mod cancelled
 * correctly hides this mod's elements as well.
 *
 * <p><b>UNVERIFIED AT RUNTIME at the time of writing.</b> A dedicated server never loads
 * {@code ForgeGui}, so the boot smoke cannot see this file at all. What IS verified is static:
 * the target's descriptor against {@code forge-1.20.1-47.4.22-universal.jar}, and the mixin's
 * presence in the shipped jar's transformed-class export. First in-game launch on this node is
 * the real gate and it is the user's.
 */
@Mixin(ForgeGui.class)
public abstract class ForgeGuiMixin {
	// The partial tick is `render`'s own argument here — no DeltaTracker to unwrap, and no
	// `Minecraft.getTimer()` to ask for one. Identical to GuiMixin's `<1.21` arm.
	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("TAIL"))
	private void archetypes$hud(final GuiGraphics graphics, final float partialTick,
			final CallbackInfo ci) {
		if (Minecraft.getInstance().options.hideGui) {
			return;
		}

		SunBlindOverlay.render(graphics, partialTick);
		DeadeyeOverlay.render(graphics, partialTick);
		CooldownBarHud.render(graphics, partialTick);
		ProcIndicatorHud.render(graphics, partialTick);
		ManaHud.render(graphics, partialTick);
		BankedHungerHud.render(graphics, partialTick);
	}
}
