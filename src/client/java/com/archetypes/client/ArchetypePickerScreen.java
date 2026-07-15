package com.archetypes.client;

import com.archetypes.Archetype;
import com.archetypes.PickArchetypePayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Pick your archetype. A vanilla-style window (see {@link VanillaUi}) holding
 * three square frames, each split by a diagonal: the start tier top-left, the
 * peak tier bottom-right. Hovering a half grows that tier's figure out of its
 * frame.
 *
 * <p>Figures are cut-out portraits where the art exists ({@link
 * Archetype#portrait}); the others still fall back to the archetype's item icon.
 */
public class ArchetypePickerScreen extends Screen {
	private static final int FRAME = 96;
	private static final int GAP = 12;
	private static final int PAD = 10;
	private static final int PANEL_WIDTH = FRAME * 3 + GAP * 2 + PAD * 2;
	private static final int PANEL_HEIGHT = 184;
	private static final int FRAMES_TOP = 36;
	private static final int BUTTON_TOP = 156;

	private static final int TIERS = 2;
	/** Portrait box at rest, and how far a hovered one grows past its frame. */
	private static final int PORTRAIT = 46;
	private static final float HOVER_SCALE = 1.25F;
	/** Slow to bloom, quick to settle back. */
	private static final float GROW_MILLIS = 1000.0F;
	private static final float SHRINK_MILLIS = 200.0F;

	private final @Nullable Screen parent;
	/** Per archetype, per tier: 0 = at rest, 1 = fully grown. */
	private final float[][] hover = new float[Archetype.values().length][TIERS];
	private long lastFrame = Util.getMillis();

	public ArchetypePickerScreen(final @Nullable Screen parent) {
		super(Component.translatable("screen.archetypes.picker"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
				.bounds(this.panelLeft() + PANEL_WIDTH / 2 - 60, this.panelTop() + BUTTON_TOP, 120, 20)
				.build());
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	private int panelLeft() {
		return (this.width - PANEL_WIDTH) / 2;
	}

	private int panelTop() {
		return (this.height - PANEL_HEIGHT) / 2;
	}

	private int frameLeft(final int index) {
		return this.panelLeft() + PAD + index * (FRAME + GAP);
	}

	private int framesTop() {
		return this.panelTop() + FRAMES_TOP;
	}

	private @Nullable Archetype frameAt(final double mouseX, final double mouseY) {
		int top = this.framesTop();

		if (mouseY < top || mouseY >= top + FRAME) {
			return null;
		}

		for (int i = 0; i < Archetype.values().length; i++) {
			int left = this.frameLeft(i);

			if (mouseX >= left && mouseX < left + FRAME) {
				return Archetype.values()[i];
			}
		}

		return null;
	}

	/**
	 * Which half of a frame the cursor is in, or -1 if outside every frame.
	 *
	 * <p>The split runs bottom-left to top-right, so in frame-local coordinates
	 * it is the line {@code x + y == FRAME}: above it is the start tier, below is
	 * the peak.
	 */
	private int tierAt(final double mouseX, final double mouseY) {
		Archetype frame = this.frameAt(mouseX, mouseY);

		if (frame == null) {
			return -1;
		}

		double localX = mouseX - this.frameLeft(frame.ordinal());
		double localY = mouseY - this.framesTop();
		return localX + localY < FRAME ? 0 : 1;
	}

	/** Centre of a tier's half: the centroid of that triangle. */
	private int halfCenterX(final int index, final int tier) {
		return this.frameLeft(index) + (tier == 0 ? FRAME / 3 : FRAME * 2 / 3);
	}

	private int halfCenterY(final int tier) {
		return this.framesTop() + (tier == 0 ? FRAME / 3 : FRAME * 2 / 3);
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}

		Archetype picked = this.frameAt(event.x(), event.y());

		if (event.button() != 0 || picked == null) {
			return false;
		}

		// Confirm: the choice is permanent for now, so make them say yes.
		this.minecraft.gui.setScreen(new ConfirmScreen(
				confirmed -> {
					if (confirmed) {
						ClientPlayNetworking.send(new PickArchetypePayload(picked.id()));
						// Straight into the new tree rather than back to the
						// inventory. Passing `picked` rather than reading the
						// attachment: the server owns it and the sync has not
						// landed yet, but we already know what was chosen.
						this.minecraft.gui.setScreen(new ArchetypeScreen(this.parent, picked));
					} else {
						this.minecraft.gui.setScreen(this);
					}
				},
				Component.translatable("screen.archetypes.confirm.title", picked.tierName(0)),
				Component.translatable("screen.archetypes.confirm.body", picked.tierName(0), picked.tierName(1)),
				Component.translatable("screen.archetypes.confirm.yes"),
				CommonComponents.GUI_CANCEL));
		return true;
	}

	/**
	 * Advance every half toward or away from full size.
	 *
	 * <p>Driven by wall-clock delta rather than frame count, so the timings hold
	 * at any framerate.
	 */
	private void advanceHover(final int mouseX, final int mouseY) {
		long now = Util.getMillis();
		float delta = Math.min(now - this.lastFrame, 100L);
		this.lastFrame = now;

		Archetype frame = this.frameAt(mouseX, mouseY);
		int tier = this.tierAt(mouseX, mouseY);

		for (int i = 0; i < Archetype.values().length; i++) {
			for (int t = 0; t < TIERS; t++) {
				boolean active = frame != null && frame.ordinal() == i && tier == t;
				float step = delta / (active ? GROW_MILLIS : SHRINK_MILLIS);
				this.hover[i][t] = Mth.clamp(this.hover[i][t] + (active ? step : -step), 0.0F, 1.0F);
			}
		}
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		this.advanceHover(mouseX, mouseY);

		int panelLeft = this.panelLeft();
		int panelTop = this.panelTop();

		VanillaUi.window(graphics, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);

		graphics.text(this.font, this.title, (this.width - this.font.width(this.title)) / 2, panelTop + 8,
				VanillaUi.LABEL, false);

		Component prompt = Component.translatable("screen.archetypes.picker.prompt");
		graphics.text(this.font, prompt, (this.width - this.font.width(prompt)) / 2, panelTop + 21,
				VanillaUi.LABEL_FAINT, false);

		int top = this.framesTop();
		Archetype hovered = this.frameAt(mouseX, mouseY);

		// Frames and their resting figures first, so a grown figure from any frame
		// can overlap its neighbours rather than being painted over by them.
		for (int i = 0; i < Archetype.values().length; i++) {
			Archetype archetype = Archetype.values()[i];
			int left = this.frameLeft(i);

			VanillaUi.inset(graphics, left, top, FRAME, FRAME);

			if (hovered == archetype) {
				graphics.fill(left + 1, top + 1, left + FRAME - 1, top + FRAME - 1, VanillaUi.INSET_BODY_HOVERED);
			}

			// The diagonal: start tier above it, peak tier below.
			drawDiagonal(graphics, left, top, FRAME, VanillaUi.LABEL_FAINT);

			for (int t = 0; t < TIERS; t++) {
				if (this.hover[i][t] <= 0.0F) {
					this.figure(graphics, archetype, i, t, 0.0F);
				}
			}

			graphics.text(this.font, archetype.tierName(0), left + 6, top + 6, archetype.color(), true);
			Component peak = archetype.tierName(1);
			graphics.text(this.font, peak, left + FRAME - 6 - this.font.width(peak), top + FRAME - 16,
					archetype.color(), true);
		}

		// Then anything mid-animation, on top of every frame.
		for (int i = 0; i < Archetype.values().length; i++) {
			for (int t = 0; t < TIERS; t++) {
				if (this.hover[i][t] > 0.0F) {
					this.figure(graphics, Archetype.values()[i], i, t, this.hover[i][t]);
				}
			}
		}

		// Blurb for whatever is hovered, between the frames and the button —
		// word-wrapped to the panel, each line centered.
		if (hovered != null) {
			int y = top + FRAME + 5;

			for (FormattedCharSequence line : this.font.split(hovered.blurb(), PANEL_WIDTH - PAD * 2)) {
				graphics.text(this.font, line, (this.width - this.font.width(line)) / 2, y,
						VanillaUi.LABEL, false);
				y += 9;
			}
		}

		// Widgets last: Screen.extractRenderState only walks the renderables, so
		// anything drawn after it covers the buttons.
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/**
	 * One tier's figure, grown by {@code progress} (0 at rest, 1 fully hovered).
	 * Drawn from the centre of its half so it swells evenly past the frame edge.
	 */
	private void figure(final GuiGraphicsExtractor graphics, final Archetype archetype,
			final int index, final int tier, final float progress) {
		float eased = progress * progress * (3.0F - 2.0F * progress);
		int size = Math.round(PORTRAIT * Mth.lerp(eased, 1.0F, HOVER_SCALE));
		int x = this.halfCenterX(index, tier) - size / 2;
		int y = this.halfCenterY(tier) - size / 2;

		Identifier portrait = archetype.portrait(tier);

		if (portrait == null) {
			// No art for this tier yet: the item icon stands in, at its own size.
			graphics.fakeItem(new ItemStack(archetype.icon()),
					this.halfCenterX(index, tier) - 8, this.halfCenterY(tier) - 8);
			return;
		}

		graphics.blit(RenderPipelines.GUI_TEXTURED, portrait, x, y, 0.0F, 0.0F,
				size, size, PORTRAIT_TEXTURE, PORTRAIT_TEXTURE, PORTRAIT_TEXTURE, PORTRAIT_TEXTURE);
	}

	/** Native size of the portrait textures. */
	private static final int PORTRAIT_TEXTURE = 256;

	/**
	 * The "/" diagonal from the bottom-left corner to the top-right, splitting the
	 * frame into a start half (above-left) and a peak half (below-right).
	 */
	private static void drawDiagonal(final GuiGraphicsExtractor graphics, final int left, final int top,
			final int size, final int color) {
		for (int i = 1; i < size - 1; i++) {
			graphics.fill(left + i, top + size - 1 - i, left + i + 1, top + size - i, color);
		}
	}
}
