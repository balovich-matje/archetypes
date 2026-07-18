package com.archetypes.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A short creative-style bookmark riding the survival inventory's top edge —
 * the square buttons used to sit to the panel's right, on top of the effect
 * list vanilla draws there. Same palette as {@link VanillaUi#window}, but
 * the bottom edge stays open so the window's own outline closes the shape,
 * the way an unselected creative tab reads. Built on {@link AbstractWidget}
 * directly: {@code AbstractButton} finalizes its render pass around the
 * vanilla button sprite, which is exactly the look this replaces.
 */
final class BookmarkTab extends AbstractWidget {
	static final int HEIGHT = 15;
	private static final int PAD = 6;

	private final Runnable onPress;

	BookmarkTab(final Component label, final Runnable onPress) {
		super(0, 0, widthFor(label), HEIGHT, label);
		this.onPress = onPress;
	}

	/**
	 * Width from label text. Specialities' tab uses this same formula by
	 * convention, so the two mods can sit side by side on the edge without
	 * ever seeing each other's widgets.
	 */
	static int widthFor(final Component label) {
		return Minecraft.getInstance().font.width(label) + 2 * PAD;
	}

	@Override
	public void onClick(final MouseButtonEvent event, final boolean doubleClick) {
		this.playDownSound(Minecraft.getInstance().getSoundManager());
		this.onPress.run();
	}

	@Override
	protected void updateWidgetNarration(final NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}

	@Override
	protected void extractWidgetRenderState(final GuiGraphicsExtractor graphics, final int mouseX,
			final int mouseY, final float partialTick) {
		int x = this.getX();
		int y = this.getY();
		int w = this.getWidth();
		int h = this.getHeight();

		graphics.fill(x + 2, y + 2, x + w - 2, y + h, this.isHovered() ? 0xFFD7D7D7 : VanillaUi.WINDOW_BODY);
		graphics.fill(x + 1, y + 1, x + w - 2, y + 2, 0xFFFFFFFF);
		graphics.fill(x + 1, y + 2, x + 2, y + h, 0xFFFFFFFF);
		graphics.fill(x + w - 2, y + 2, x + w - 1, y + h, 0xFF555555);

		// Outline with the corner pixels skipped for the rounded look.
		graphics.fill(x + 1, y, x + w - 1, y + 1, 0xFF000000);
		graphics.fill(x, y + 1, x + 1, y + h, 0xFF000000);
		graphics.fill(x + w - 1, y + 1, x + w, y + h, 0xFF000000);

		// Plain labels use the flat dark ink of vanilla titles; a styled
		// (gold) label gets a drop shadow instead — colored text on the grey
		// body washes out without one, same lesson as the picker's names.
		boolean styled = this.getMessage().getStyle().getColor() != null;
		var font = Minecraft.getInstance().font;
		graphics.text(font, this.getMessage(), x + (w - font.width(this.getMessage())) / 2,
				y + (h - font.lineHeight) / 2 + 1, styled ? 0xFFFFFFFF : VanillaUi.LABEL, styled);
	}
}
