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
 * three frames, one per archetype, each titled with the start name and filled
 * with the archetype's crest — a collage of its three sub-archetype weapons
 * ({@link Archetype#portrait}). Hovering a frame grows the crest.
 */
public class ArchetypePickerScreen extends Screen {
	private static final int FRAME_W = 112;
	private static final int FRAME_H = 96;
	private static final int GAP = 12;
	private static final int PAD = 10;
	private static final int PANEL_WIDTH = FRAME_W * 3 + GAP * 2 + PAD * 2;
	private static final int PANEL_HEIGHT = 184;
	private static final int FRAMES_TOP = 36;
	private static final int BUTTON_TOP = 156;

	/**
	 * Crest size at rest. Sized against the hover state, not the resting one:
	 * grown by {@link #HOVER_SCALE} it comes to 87px, just inside the frame's
	 * 94px interior.
	 */
	private static final int PORTRAIT = 70;
	private static final float HOVER_SCALE = 1.25F;
	/** Quick to bloom, quicker to settle back. */
	private static final float GROW_MILLIS = 400.0F;
	private static final float SHRINK_MILLIS = 200.0F;

	private final @Nullable Screen parent;
	/** Per archetype: 0 = at rest, 1 = fully grown. */
	private final float[] hover = new float[Archetype.values().length];
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
		return this.panelLeft() + PAD + index * (FRAME_W + GAP);
	}

	private int framesTop() {
		return this.panelTop() + FRAMES_TOP;
	}

	private @Nullable Archetype frameAt(final double mouseX, final double mouseY) {
		int top = this.framesTop();

		if (mouseY < top || mouseY >= top + FRAME_H) {
			return null;
		}

		for (int i = 0; i < Archetype.values().length; i++) {
			int left = this.frameLeft(i);

			if (mouseX >= left && mouseX < left + FRAME_W) {
				return Archetype.values()[i];
			}
		}

		return null;
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
	 * Advance every crest toward or away from full size.
	 *
	 * <p>Driven by wall-clock delta rather than frame count, so the timings hold
	 * at any framerate.
	 */
	private void advanceHover(final int mouseX, final int mouseY) {
		long now = Util.getMillis();
		float delta = Math.min(now - this.lastFrame, 100L);
		this.lastFrame = now;

		Archetype frame = this.frameAt(mouseX, mouseY);

		for (int i = 0; i < Archetype.values().length; i++) {
			boolean active = frame != null && frame.ordinal() == i;
			float step = delta / (active ? GROW_MILLIS : SHRINK_MILLIS);
			this.hover[i] = Mth.clamp(this.hover[i] + (active ? step : -step), 0.0F, 1.0F);
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

		// Frames and their resting crests first, so a grown crest from any frame
		// can overlap its neighbours rather than being painted over by them.
		for (int i = 0; i < Archetype.values().length; i++) {
			Archetype archetype = Archetype.values()[i];
			int left = this.frameLeft(i);

			VanillaUi.inset(graphics, left, top, FRAME_W, FRAME_H);

			if (hovered == archetype) {
				graphics.fill(left + 1, top + 1, left + FRAME_W - 1, top + FRAME_H - 1, VanillaUi.INSET_BODY_HOVERED);
			}

			if (this.hover[i] <= 0.0F) {
				this.figure(graphics, archetype, i, 0.0F);
			}

			Component name = archetype.tierName(0);
			graphics.text(this.font, name, left + (FRAME_W - this.font.width(name)) / 2, top + 6,
					archetype.color(), true);
		}

		// Then anything mid-animation, on top of every frame.
		for (int i = 0; i < Archetype.values().length; i++) {
			if (this.hover[i] > 0.0F) {
				this.figure(graphics, Archetype.values()[i], i, this.hover[i]);
			}
		}

		// Blurb for whatever is hovered, between the frames and the button —
		// word-wrapped to the panel, each line centered.
		if (hovered != null) {
			int y = top + FRAME_H + 5;

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
	 * One frame's crest, grown by {@code progress} (0 at rest, 1 fully hovered),
	 * centered in the frame below the name label.
	 */
	private void figure(final GuiGraphicsExtractor graphics, final Archetype archetype,
			final int index, final float progress) {
		float eased = progress * progress * (3.0F - 2.0F * progress);
		int size = Math.round(PORTRAIT * Mth.lerp(eased, 1.0F, HOVER_SCALE));
		int centerX = this.frameLeft(index) + FRAME_W / 2;
		int centerY = this.framesTop() + FRAME_H / 2 + 4;

		Identifier portrait = archetype.portrait();

		if (portrait == null) {
			// No art for this archetype yet: the item icon stands in, at its own size.
			graphics.fakeItem(new ItemStack(archetype.icon()), centerX - 8, centerY - 8);
			return;
		}

		graphics.blit(RenderPipelines.GUI_TEXTURED, portrait,
				centerX - size / 2, centerY - size / 2, 0.0F, 0.0F,
				size, size, PORTRAIT_TEXTURE, PORTRAIT_TEXTURE, PORTRAIT_TEXTURE, PORTRAIT_TEXTURE);
	}

	/** Native size of the portrait textures. */
	private static final int PORTRAIT_TEXTURE = 256;
}
