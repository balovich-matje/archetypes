package com.archetypes.client;

import com.archetypes.Archetype;
import com.archetypes.PickArchetypePayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Pick your archetype. Each is a square frame split by a diagonal: the start
 * tier (what you are now) top-left, the peak tier (what you become) bottom-right.
 * Hovering a half highlights it.
 *
 * <p>Art is placeholder — the real thing is a rendered player figure per tier
 * (copper armor + iron sword for Brawler; netherite + trim + dual enchanted
 * swords for Colossus), growing out of the frame on hover.
 */
public class ArchetypePickerScreen extends Screen {
	private static final int FRAME = 96;
	private static final int GAP = 12;
	private static final int TOTAL_WIDTH = FRAME * 3 + GAP * 2;

	private final @Nullable Screen parent;

	public ArchetypePickerScreen(final @Nullable Screen parent) {
		super(Component.translatable("screen.archetypes.picker"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
				.bounds(this.width / 2 - 60, this.height - 32, 120, 20)
				.build());
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	private int framesLeft() {
		return (this.width - TOTAL_WIDTH) / 2;
	}

	private int framesTop() {
		return this.height / 2 - FRAME / 2 - 10;
	}

	private @Nullable Archetype frameAt(final double mouseX, final double mouseY) {
		int top = this.framesTop();

		if (mouseY < top || mouseY >= top + FRAME) {
			return null;
		}

		for (int i = 0; i < Archetype.values().length; i++) {
			int left = this.framesLeft() + i * (FRAME + GAP);

			if (mouseX >= left && mouseX < left + FRAME) {
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
						this.minecraft.gui.setScreen(this.parent);
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

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);

		graphics.text(this.font, this.title, (this.width - this.font.width(this.title)) / 2, 24, 0xFFFFFFFF, true);

		Component prompt = Component.translatable("screen.archetypes.picker.prompt");
		graphics.text(this.font, prompt, (this.width - this.font.width(prompt)) / 2, 38, 0xFFAAAAAA, false);

		int top = this.framesTop();
		Archetype hovered = this.frameAt(mouseX, mouseY);

		for (int i = 0; i < Archetype.values().length; i++) {
			Archetype archetype = Archetype.values()[i];
			int left = this.framesLeft() + i * (FRAME + GAP);
			boolean isHovered = hovered == archetype;

			// Frame.
			graphics.fill(left, top, left + FRAME, top + FRAME, isHovered ? 0xDD1A1A1A : 0xAA000000);
			int border = isHovered ? archetype.color() : ARGB.color(0x88, archetype.color());
			graphics.fill(left, top, left + FRAME, top + 1, border);
			graphics.fill(left, top + FRAME - 1, left + FRAME, top + FRAME, border);
			graphics.fill(left, top, left + 1, top + FRAME, border);
			graphics.fill(left + FRAME - 1, top, left + FRAME, top + FRAME, border);

			// The diagonal: start tier above it, peak tier below.
			drawDiagonal(graphics, left, top, FRAME, ARGB.color(0x66, 0xFFFFFF));

			// Placeholder art: the archetype's icon in each half.
			graphics.fakeItem(new ItemStack(archetype.icon()), left + 10, top + 26);
			graphics.fakeItem(new ItemStack(archetype.icon()), left + FRAME - 26, top + FRAME - 42);

			graphics.text(this.font, archetype.tierName(0), left + 6, top + 6, archetype.color(), true);
			Component peak = archetype.tierName(1);
			graphics.text(this.font, peak, left + FRAME - 6 - this.font.width(peak), top + FRAME - 16,
					archetype.color(), true);
		}

		// Blurb for whatever is hovered, under the frames.
		if (hovered != null) {
			Component blurb = hovered.blurb();
			graphics.text(this.font, blurb, (this.width - this.font.width(blurb)) / 2, top + FRAME + 10,
					0xFFEEEEEE, false);
		}
	}

	/**
	 * The "/" diagonal from the bottom-left corner to the top-right, splitting the
	 * frame into a start half (above-left) and a peak half (below-right).
	 */
	private static void drawDiagonal(final GuiGraphicsExtractor graphics, final int left, final int top,
			final int size, final int color) {
		for (int i = 0; i < size; i++) {
			graphics.fill(left + i, top + size - 1 - i, left + i + 1, top + size - i, color);
		}
	}
}
