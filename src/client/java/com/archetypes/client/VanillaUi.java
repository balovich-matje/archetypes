package com.archetypes.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The classic container look, drawn with fills. Palette sampled from 26.2's
 * {@code inventory.png} — window: 1px black outline (corner pixel skipped =
 * rounded), 2px white highlight top/left, C6C6C6 body, 2px 555555 shadow
 * bottom/right. Inset is the bevel inverted around an 8B8B8B body.
 */
public final class VanillaUi {
	public static final int LABEL = 0xFF404040;
	public static final int LABEL_FAINT = 0xFF6B6B6B;
	/** Section headers: washed out so they sit back into the canvas. */
	public static final int SECTION_TITLE = 0xFF787878;
	public static final int WINDOW_BODY = 0xFFC6C6C6;
	public static final int INSET_BODY = 0xFF8B8B8B;
	public static final int INSET_BODY_HOVERED = 0xFFA3A3A3;
	public static final int INSET_DARK = 0xFF373737;

	private static final int OUTLINE = 0xFF000000;
	private static final int HIGHLIGHT = 0xFFFFFFFF;
	private static final int SHADOW = 0xFF555555;

	/** Text on the window body should read at least this crisply — the
	 * vanilla LABEL grey itself sits at about 6:1. */
	private static final double INK_CONTRAST = 5.0;

	private static final java.util.Map<Integer, Integer> INK_CACHE = new java.util.HashMap<>();

	private VanillaUi() {
	}

	/**
	 * The archetype colors are pastels tuned for dark grounds (node fills,
	 * the progress bar's near-black trough); as TEXT on the grey window they
	 * wash out (user report). This darkens a color just until it clears
	 * readable contrast on WINDOW_BODY, keeping the hue — each color pays
	 * only the darkening it actually needs.
	 */
	public static int ink(final int argb) {
		return INK_CACHE.computeIfAbsent(argb, color -> {
			int r = (color >> 16) & 0xFF;
			int g = (color >> 8) & 0xFF;
			int b = color & 0xFF;

			while (contrastOnBody(r, g, b) < INK_CONTRAST && (r | g | b) != 0) {
				r = r * 9 / 10;
				g = g * 9 / 10;
				b = b * 9 / 10;
			}

			return 0xFF000000 | r << 16 | g << 8 | b;
		});
	}

	/** WCAG-style contrast of a text color against the window body. */
	private static double contrastOnBody(final int r, final int g, final int b) {
		double text = 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
		double body = 0.2126 * channel(0xC6) + 0.7152 * channel(0xC6) + 0.0722 * channel(0xC6);
		return (Math.max(text, body) + 0.05) / (Math.min(text, body) + 0.05);
	}

	private static double channel(final int c) {
		double s = c / 255.0;
		return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
	}

	public static void window(final GuiGraphicsExtractor graphics, final int x, final int y,
			final int w, final int h) {
		graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, WINDOW_BODY);

		graphics.fill(x + 1, y + 1, x + w - 2, y + 3, HIGHLIGHT);
		graphics.fill(x + 1, y + 1, x + 3, y + h - 2, HIGHLIGHT);
		graphics.fill(x + 2, y + h - 3, x + w - 1, y + h - 1, SHADOW);
		graphics.fill(x + w - 3, y + 2, x + w - 1, y + h - 1, SHADOW);

		graphics.fill(x + 1, y, x + w - 1, y + 1, OUTLINE);
		graphics.fill(x + 1, y + h - 1, x + w - 1, y + h, OUTLINE);
		graphics.fill(x, y + 1, x + 1, y + h - 1, OUTLINE);
		graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, OUTLINE);
	}

	/** Slot-style sunken area: dark top/left, white bottom/right, grey body. */
	public static void inset(final GuiGraphicsExtractor graphics, final int x, final int y,
			final int w, final int h) {
		graphics.fill(x, y, x + w, y + h, INSET_BODY);
		insetBorder(graphics, x, y, w, h);
	}

	/** The inset bevel alone — for sinking artwork into the page without hiding it. */
	public static void insetBorder(final GuiGraphicsExtractor graphics, final int x, final int y,
			final int w, final int h) {
		graphics.fill(x, y, x + w - 1, y + 1, INSET_DARK);
		graphics.fill(x, y + 1, x + 1, y + h - 1, INSET_DARK);
		graphics.fill(x + 1, y + h - 1, x + w, y + h, HIGHLIGHT);
		graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, HIGHLIGHT);
	}

	/** A vanilla 18x18 item slot. */
	public static void slot(final GuiGraphicsExtractor graphics, final int x, final int y) {
		inset(graphics, x, y, 18, 18);
	}

	/**
	 * Thin engraved groove, the vanilla separator: 1px dark + 1px light, 2px
	 * total — deliberately slimmer than the 2px-per-side bevel of a window.
	 */
	public static void verticalDivider(final GuiGraphicsExtractor graphics, final int x,
			final int top, final int bottom) {
		graphics.fill(x, top, x + 1, bottom, INSET_DARK);
		graphics.fill(x + 1, top, x + 2, bottom, HIGHLIGHT);
	}

	/**
	 * Sunken progress bar: an inset groove with a filled portion. {@code progress}
	 * is clamped, so a caller cannot overdraw the track by passing >1.
	 */
	public static void progressBar(final GuiGraphicsExtractor graphics, final int x, final int y,
			final int width, final int height, final float progress, final int fill) {
		graphics.fill(x, y, x + width, y + height, 0xFF2A2A2A);
		insetBorder(graphics, x, y, width, height);

		int filled = Math.round(Math.max(0.0F, Math.min(1.0F, progress)) * (width - 2));

		if (filled > 0) {
			graphics.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, fill);
		}
	}

	/** Stepped 2px-thick line between two points, for tree connections. */
	public static void line(final GuiGraphicsExtractor graphics, final int x1, final int y1,
			final int x2, final int y2, final int color) {
		int dx = Math.abs(x2 - x1);
		int dy = Math.abs(y2 - y1);
		int steps = Math.max(dx, dy);

		if (steps == 0) {
			return;
		}

		for (int i = 0; i <= steps; i++) {
			int x = x1 + (x2 - x1) * i / steps;
			int y = y1 + (y2 - y1) * i / steps;
			graphics.fill(x - 1, y - 1, x + 1, y + 1, color);
		}
	}
}
