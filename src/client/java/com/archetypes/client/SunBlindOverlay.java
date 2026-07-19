package com.archetypes.client;

import com.archetypes.Archetypes;
import com.archetypes.NightForm;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

/**
 * Daylight through a vampire's eyes: a white bloom that swells while the
 * transformed player stands in burning sun and drains away in shade.
 *
 * <p>Anchored to MISC_OVERLAYS like Specialities' StealthVignette, which is
 * the precedent for a full-screen wash — it draws under the hotbar and the
 * bars, so the health row and the cooldown tiles stay legible through it.
 *
 * <p>{@link #MAX_ALPHA} is the design constraint, not a taste knob: the author
 * asked for barely able to see, not blind, so the texture's own falloff (near
 * opaque in the middle, half that at the corners) is scaled to leave the
 * screen readable at its worst. Ending the form snaps the bloom off rather
 * than fading it: the sun stops mattering the instant you are mortal again,
 * and a lingering wash would read as a stuck overlay.
 */
public final class SunBlindOverlay {
	private static final Identifier TEXTURE = Archetypes.id("textures/misc/sun_blind.png");

	/** Peak strength at the texture's brightest point. */
	private static final float MAX_ALPHA = 0.68F;
	/** Per-second exponential approach rates. Stepping into the open blinds
	 * you over about a second; stepping into shade clears faster, because
	 * running for cover has to feel like it worked. */
	private static final float FADE_IN_RATE = 2.6F;
	private static final float FADE_OUT_RATE = 5.0F;

	private static float alpha;
	private static long lastFrameMs = Util.getMillis();

	private SunBlindOverlay() {
	}

	public static void render(final GuiGraphicsExtractor graphics, final DeltaTracker delta) {
		long now = Util.getMillis();
		float dt = Math.min((now - lastFrameMs) / 1000.0F, 0.1F);
		lastFrameMs = now;

		Player player = Minecraft.getInstance().player;

		if (player == null || !NightForm.isActive(player)) {
			alpha = 0.0F;
			return;
		}

		boolean sunlit = NightForm.isSunlit(player);
		float target = sunlit ? MAX_ALPHA : 0.0F;
		alpha += (target - alpha) * Math.min(1.0F, (sunlit ? FADE_IN_RATE : FADE_OUT_RATE) * dt);

		if (alpha <= 0.01F) {
			return;
		}

		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, 0.0F, 0.0F,
				graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(), graphics.guiHeight(),
				ARGB.colorFromFloat(alpha, 1.0F, 1.0F, 1.0F));
	}
}
