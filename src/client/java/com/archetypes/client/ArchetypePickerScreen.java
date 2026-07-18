package com.archetypes.client;

import com.archetypes.Archetype;
import com.archetypes.PickArchetypePayload;
import com.archetypes.SubTree;
import com.archetypes.TreeNodes;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
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
 * three cards, one per archetype: the start name, a five-word role line, the
 * crest (painted collage where one exists, a three-item mini-collage of the
 * sub-tree symbols otherwise), and an always-visible row of the archetype's
 * three active abilities. Hovering a card grows the crest and shows the
 * playstyle blurb; hovering an ability slot previews the real node tooltip —
 * the same icon and description the tree screen will show after the pick.
 */
public class ArchetypePickerScreen extends Screen {
	private static final int FRAME_W = 112;
	private static final int FRAME_H = 140;
	private static final int GAP = 12;
	private static final int PAD = 10;
	private static final int PANEL_WIDTH = FRAME_W * 3 + GAP * 2 + PAD * 2;
	private static final int PANEL_HEIGHT = 253;
	private static final int FRAMES_TOP = 36;
	private static final int BUTTON_TOP = 225;

	/** Per-card vertical rhythm, offsets from the card's own top. */
	private static final int ROLE_TOP = 16;
	private static final int PORTRAIT_CENTER_Y = 74;
	private static final int ABILITY_ROW_TOP = 116;
	private static final int ABILITY_ROW_H = 18;
	private static final int SLOT_SINGLE = 18;
	private static final int SLOT_FORK = 36;
	private static final int ICON_GAP = 6;

	/**
	 * Crest size at rest. Sized against the hover state, not the resting one:
	 * grown by {@link #HOVER_SCALE} it comes to 75px, just inside the 76px
	 * band between the role line and the ability row.
	 */
	private static final int PORTRAIT = 60;
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

	/** One ability-preview slot: an 18px single or a 36px fork pair. */
	private record Slot(int x, int width, SubTree tree, java.util.List<Integer> actives) {
	}

	/** The three slots of one card, geometry shared by draw and hit-test. */
	private java.util.List<Slot> abilitySlots(final int frameIndex, final Archetype archetype) {
		var trees = SubTree.of(archetype);
		int[] widths = new int[3];
		int rowWidth = ICON_GAP * 2;

		for (int i = 0; i < 3; i++) {
			widths[i] = TreeNodes.pickerActives(trees.get(i)).size() > 1 ? SLOT_FORK : SLOT_SINGLE;
			rowWidth += widths[i];
		}

		java.util.List<Slot> slots = new java.util.ArrayList<>(3);
		int x = this.frameLeft(frameIndex) + (FRAME_W - rowWidth) / 2;

		for (int i = 0; i < 3; i++) {
			SubTree tree = trees.get(i);
			slots.add(new Slot(x, widths[i], tree, TreeNodes.pickerActives(tree)));
			x += widths[i] + ICON_GAP;
		}

		return slots;
	}

	private @Nullable Slot abilitySlotAt(final double mouseX, final double mouseY) {
		int y = this.framesTop() + ABILITY_ROW_TOP;

		if (mouseY < y || mouseY >= y + ABILITY_ROW_H) {
			return null;
		}

		for (int i = 0; i < Archetype.values().length; i++) {
			for (Slot slot : this.abilitySlots(i, Archetype.values()[i])) {
				if (mouseX >= slot.x() && mouseX < slot.x() + slot.width()) {
					return slot;
				}
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

		// Confirm: the choice can't be undone until Amnesia II is brewable,
		// so make them say yes.
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

			// The role line: what you'll be doing, always visible.
			int roleY = top + ROLE_TOP;

			for (FormattedCharSequence line : this.font.split(archetype.role(), FRAME_W - 8)) {
				graphics.text(this.font, line, left + (FRAME_W - this.font.width(line)) / 2, roleY,
						VanillaUi.LABEL_FAINT, false);
				roleY += 9;
			}
		}

		// Then anything mid-animation, on top of every frame.
		for (int i = 0; i < Archetype.values().length; i++) {
			if (this.hover[i] > 0.0F) {
				this.figure(graphics, Archetype.values()[i], i, this.hover[i]);
			}
		}

		// The ability rows last of the card content, so a grown crest can
		// never paint over the icons.
		for (int i = 0; i < Archetype.values().length; i++) {
			this.abilityRow(graphics, i, Archetype.values()[i], mouseX, mouseY);
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

		// The ability preview floats over everything, Cancel included.
		Slot slot = this.abilitySlotAt(mouseX, mouseY);

		if (slot != null) {
			graphics.setTooltipForNextFrame(this.font, this.abilityTooltip(slot), mouseX, mouseY);
		}
	}

	/** One card's row of active-ability previews. */
	private void abilityRow(final GuiGraphicsExtractor graphics, final int frameIndex,
			final Archetype archetype, final int mouseX, final int mouseY) {
		int y = this.framesTop() + ABILITY_ROW_TOP;

		for (Slot slot : this.abilitySlots(frameIndex, archetype)) {
			if (slot.width() == SLOT_SINGLE) {
				VanillaUi.slot(graphics, slot.x(), y);
			} else {
				VanillaUi.inset(graphics, slot.x(), y, SLOT_FORK, ABILITY_ROW_H);
			}

			boolean hoveredSlot = mouseX >= slot.x() && mouseX < slot.x() + slot.width()
					&& mouseY >= y && mouseY < y + ABILITY_ROW_H;

			if (hoveredSlot) {
				graphics.fill(slot.x() + 1, y + 1, slot.x() + slot.width() - 1, y + ABILITY_ROW_H - 1,
						VanillaUi.INSET_BODY_HOVERED);
			}

			// Native 16x16, never scaled — the tree screen's own resolution.
			for (int i = 0; i < slot.actives().size(); i++) {
				VanillaUi.nodeIcon(graphics, slot.tree(), slot.actives().get(i),
						slot.x() + 1 + i * 18, y + 1);
			}
		}
	}

	/** The real node tooltip(s); a fork stacks both with a pick-one hint. */
	private java.util.List<FormattedCharSequence> abilityTooltip(final Slot slot) {
		java.util.List<FormattedCharSequence> lines = new java.util.ArrayList<>();

		for (int i = 0; i < slot.actives().size(); i++) {
			if (i > 0) {
				lines.add(Component.translatable("screen.archetypes.picker.fork_hint")
						.withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
			}

			int index = slot.actives().get(i);
			lines.add(Component.translatable(TreeNodes.nameKey(slot.tree(), index))
					.withStyle(ChatFormatting.WHITE).getVisualOrderText());
			lines.addAll(this.font.split(
					Component.translatable(TreeNodes.descriptionKey(slot.tree(), index))
							.withStyle(ChatFormatting.GRAY),
					VanillaUi.TOOLTIP_WIDTH));
		}

		return lines;
	}

	/**
	 * One frame's crest, grown by {@code progress} (0 at rest, 1 fully hovered),
	 * centered in the crest band between the role line and the ability row.
	 */
	private void figure(final GuiGraphicsExtractor graphics, final Archetype archetype,
			final int index, final float progress) {
		float eased = progress * progress * (3.0F - 2.0F * progress);
		int size = Math.round(PORTRAIT * Mth.lerp(eased, 1.0F, HOVER_SCALE));
		int centerX = this.frameLeft(index) + FRAME_W / 2;
		int centerY = this.framesTop() + PORTRAIT_CENTER_Y;

		Identifier portrait = archetype.portrait();

		if (portrait == null) {
			// Hand-composed crests (user layout), blooming with the same
			// ease as the painted art.
			float scale = Mth.lerp(eased, 2.0F, 2.5F);
			var pose = graphics.pose();
			pose.pushMatrix();
			pose.translate(centerX, centerY);
			pose.scale(scale, scale);

			if (archetype == Archetype.AGILITY) {
				// Crossbow aiming upper-left (mirrored), drawn bow upper-right,
				// two daggers crossed in front at the bottom.
				mirrored(graphics, VANILLA_CROSSBOW, -17, -15, 16);
				graphics.blit(RenderPipelines.GUI_TEXTURED, VANILLA_BOW_DRAWN,
						1, -15, 0.0F, 0.0F, 16, 16, 16, 16, 16, 16);
				graphics.blit(RenderPipelines.GUI_TEXTURED, DAGGER,
						-12, -3, 0.0F, 0.0F, 16, 16, 16, 16, 16, 16);
				mirrored(graphics, DAGGER, -4, -3, 16);
			} else {
				// Glacial spike upper-left (mirrored), flamethrower upper-right
				// (both wear their node icons), a mana regeneration potion in
				// front.
				mirrored(graphics, SPIKE_ICON, -17, -15, 32);
				graphics.blit(RenderPipelines.GUI_TEXTURED, FLAME_ICON,
						1, -15, 0.0F, 0.0F, 16, 16, 32, 32, 32, 32);
				graphics.fakeItem(net.minecraft.world.item.alchemy.PotionContents.createItemStack(
						net.minecraft.world.item.Items.POTION,
						com.archetypes.ManaPotions.MANA_REGENERATION), -8, -2);
			}

			pose.popMatrix();
			return;
		}

		graphics.blit(RenderPipelines.GUI_TEXTURED, portrait,
				centerX - size / 2, centerY - size / 2, 0.0F, 0.0F,
				size, size, PORTRAIT_TEXTURE, PORTRAIT_TEXTURE, PORTRAIT_TEXTURE, PORTRAIT_TEXTURE);
	}

	/** Native size of the portrait textures. */
	private static final int PORTRAIT_TEXTURE = 256;

	/** The collage materials: vanilla weapons, our dagger, two node icons. */
	private static final Identifier VANILLA_CROSSBOW =
			Identifier.fromNamespaceAndPath("minecraft", "textures/item/crossbow_standby.png");
	private static final Identifier VANILLA_BOW_DRAWN =
			Identifier.fromNamespaceAndPath("minecraft", "textures/item/bow_pulling_2.png");
	private static final Identifier DAGGER =
			com.archetypes.Archetypes.id("textures/item/iron_dagger.png");
	private static final Identifier FLAME_ICON =
			com.archetypes.Archetypes.id("textures/node/test/opus/elementalist/flamethrower.png");
	private static final Identifier SPIKE_ICON =
			com.archetypes.Archetypes.id("textures/node/test/opus/elementalist/glacial_spike.png");

	/** Blit a square sprite horizontally mirrored, drawn 16x16 at (x, y). */
	private static void mirrored(final GuiGraphicsExtractor graphics, final Identifier texture,
			final int x, final int y, final int textureSize) {
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x + 16, y);
		pose.scale(-1.0F, 1.0F);
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0.0F, 0.0F, 16, 16,
				textureSize, textureSize, textureSize, textureSize);
		pose.popMatrix();
	}
}
