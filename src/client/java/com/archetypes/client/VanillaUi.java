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
	public static final int WINDOW_BODY = 0xFFC6C6C6;
	public static final int INSET_BODY = 0xFF8B8B8B;
	public static final int INSET_BODY_HOVERED = 0xFFA3A3A3;
	public static final int INSET_DARK = 0xFF373737;

	private static final int OUTLINE = 0xFF000000;
	private static final int HIGHLIGHT = 0xFFFFFFFF;
	private static final int SHADOW = 0xFF555555;

	private VanillaUi() {
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
