package com.archetypes.client;

import com.archetypes.Deadeye;
import com.archetypes.Tuning;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

/**
 * Concentration, while the Deadeye stance holds: the screen darkens at its
 * edges and stays clear in the middle. On MISC_OVERLAYS like
 * {@link SunBlindOverlay}, so it washes the world but draws under the hotbar
 * and the bars.
 *
 * <p>Drawn as nested bands rather than blitted from a texture. A vignette is
 * four one-dimensional ramps, {@link #BANDS} fills describe them exactly, and
 * a hand-drawn PNG for a shape this simple would be an asset to keep in step
 * with {@link Tuning#DEADEYE_VIGNETTE_ALPHA} for no gain.
 *
 * <p>{@link Tuning#DEADEYE_VIGNETTE_ALPHA} is the design constraint, not a
 * taste knob: fifteen percent at the very edge has to read as focus, not as
 * damage. The stance ending snaps it off rather than fading it — the same rule
 * the sun bloom follows, because a lingering wash reads as a stuck overlay.
 */
public final class DeadeyeOverlay {
	/** Steps in each edge ramp. Twelve is under a fifth of a hotbar's height
	 * and already past the point where the banding is visible. */
	private static final int BANDS = 12;
	/** How far in the darkening reaches, as a share of the shorter screen
	 * axis. A narrow vignette (the design's word), not a tunnel. */
	private static final float DEPTH = 0.18F;
	/** Per-second exponential approach. Slower in than the sun bloom: the
	 * stance is entered on purpose and the edges should close, not slam. */
	private static final float FADE_RATE = 4.0F;

	private static float alpha;
	private static long lastFrameMs = Util.getMillis();

	private DeadeyeOverlay() {
	}

	public static void render(final GuiGraphicsExtractor graphics, final DeltaTracker delta) {
		long now = Util.getMillis();
		float dt = Math.min((now - lastFrameMs) / 1000.0F, 0.1F);
		lastFrameMs = now;

		Player player = Minecraft.getInstance().player;

		if (player == null || !Deadeye.isActive(player)) {
			alpha = 0.0F;
			return;
		}

		alpha += (Tuning.DEADEYE_VIGNETTE_ALPHA - alpha) * Math.min(1.0F, FADE_RATE * dt);

		if (alpha <= 0.005F) {
			return;
		}

		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int depth = Math.max(1, Math.round(Math.min(width, height) * DEPTH));

		for (int band = 0; band < BANDS; band++) {
			// Squared ramp: nothing at the inner edge, full strength at the
			// screen's rim, with the darkening piling up only in the last few
			// pixels the way a lens falls off.
			// The bands do not overlap (each is its own ring at its own inset),
			// so each carries its full share rather than a fraction of one.
			float t = (BANDS - band) / (float) BANDS;
			int colour = ARGB.colorFromFloat(alpha * t * t, 0.0F, 0.0F, 0.0F);
			int inset = Math.round(depth * band / (float) BANDS);
			int thickness = Math.max(1, Math.round(depth / (float) BANDS));

			graphics.fill(0, inset, width, inset + thickness, colour);
			graphics.fill(0, height - inset - thickness, width, height - inset, colour);
			graphics.fill(inset, 0, inset + thickness, height, colour);
			graphics.fill(width - inset - thickness, 0, width - inset, height, colour);
		}
	}
}
