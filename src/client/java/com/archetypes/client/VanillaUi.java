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
	/** Wrap width for node tooltips, shared by the tree and the picker. */
	public static final int TOOLTIP_WIDTH = 180;
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
		insetBorder(graphics, x, y, w, h);
	}

	/**
	 * A node's 16x16 icon exactly as the tree screen resolves it — sprite
	 * first (bake-off test sets included), else the item render with its
	 * effect layer over or under. Shared with the picker's ability previews
	 * so they stay pixel-identical to the tree.
	 */
	public static void nodeIcon(final GuiGraphicsExtractor graphics, final com.archetypes.SubTree tree,
			final int index, final int x, final int y) {
		// The night form's two renamed actives outrank every other icon rule:
		// while transformed the tree screen, the cooldown bar and the picker
		// all show the empowered art, and they resolve it in one place.
		var night = NightIdentity.sprite(tree, index);

		if (night != null) {
			graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, night,
					x, y, 0.0F, 0.0F, 16, 16, NightIdentity.SPRITE_SIZE, NightIdentity.SPRITE_SIZE,
					NightIdentity.SPRITE_SIZE, NightIdentity.SPRITE_SIZE);
			return;
		}

		var sprite = com.archetypes.TreeNodes.iconSprite(tree, index);

		if (sprite != null) {
			int tex = com.archetypes.TreeNodes.iconSpriteSize(tree, index);
			graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, sprite,
					x, y, 0.0F, 0.0F, 16, 16, tex, tex, tex, tex);
			return;
		}

		var icon = com.archetypes.TreeNodes.icon(tree, index);

		if (icon == null) {
			return;
		}

		var overlay = com.archetypes.TreeNodes.iconOverlay(tree, index);
		boolean behind = overlay != null && com.archetypes.TreeNodes.iconOverlayBehind(tree, index);

		if (behind) {
			int tex = com.archetypes.TreeNodes.iconOverlaySize(tree, index);
			graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, overlay,
					x, y, 0.0F, 0.0F, 16, 16, tex, tex, tex, tex);
		}

		graphics.fakeItem(new net.minecraft.world.item.ItemStack(icon), x, y);

		if (overlay != null && !behind) {
			int tex = com.archetypes.TreeNodes.iconOverlaySize(tree, index);
			graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, overlay,
					x, y, 0.0F, 0.0F, 16, 16, tex, tex, tex, tex);
		}
	}

	/**
	 * A bright-colored line on a small dark plate. Third attempt at
	 * archetype-colored text on the grey window (user report): darkened ink
	 * read as black, a drop shadow alone still washed out — the plate
	 * supplies the contrast so the pastel can stay itself, the way the
	 * picker's colored names read fine on the darker card body.
	 */
	public static void chipText(final GuiGraphicsExtractor graphics,
			final net.minecraft.client.gui.Font font,
			final net.minecraft.network.chat.Component text, final int x, final int y, final int color) {
		int width = font.width(text);
		graphics.fill(x - 3, y - 2, x + width + 3, y + 10, INSET_DARK);
		graphics.text(font, text, x, y, color, true);
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
