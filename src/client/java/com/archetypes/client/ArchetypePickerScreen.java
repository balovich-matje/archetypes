package com.archetypes.client;

import com.archetypes.Archetype;
import com.archetypes.SubTree;
import com.archetypes.TreeNodes;

import com.archetypes.platform.Net;
import com.archetypes.state.WireId;

import net.minecraft.ChatFormatting;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.11 {
import net.minecraft.client.input.MouseButtonEvent;
//?}
//? if >=1.21.11 {
import net.minecraft.client.renderer.RenderPipelines;
//?}
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
//? if >=1.21.11 {
import org.jspecify.annotations.Nullable;
//?} else {
/*import org.jetbrains.annotations.Nullable;
*///?}

/**
 * Pick your archetype. A vanilla-style window (see {@link VanillaUi}) holding
 * three cards, one per archetype: the start name, a five-word role line, the
 * crest (painted collage where one exists, a three-item mini-collage of the
 * sub-tree symbols otherwise), and an always-visible row of the archetype's
 * three active abilities. Hovering a card grows the crest; hovering an
 * ability slot previews the real node tooltip — the same icon and
 * description the tree screen will show after the pick. (There used to be a
 * hover blurb under the cards; it read like ad copy and was cut.)
 *
 * <p>Unlike the tree screen this is FIXED-SIZE chrome, the way a vanilla
 * container screen is: at a bigger GUI scale it covers more of the screen and
 * that is correct. The one thing it may not do is be wider than the surface —
 * Minecraft's scale clamp guarantees a GUI-scaled area of at least 320x240 and
 * no more, and three 112px cards plus their gaps came to 380, so at the largest
 * scale a player could pick the cards ran off both edges of the window. The card
 * width is therefore a CEILING that shrinks to fit, and the ability row and the
 * crest are quoted as fractions of it so a narrowed card stays composed.
 */
public class ArchetypePickerScreen extends Screen {
	/** Card width at full size, and the floor it may shrink to on a narrow surface. */
	private static final int MAX_FRAME_W = 112;
	private static final int MIN_FRAME_W = 72;
	private static final int FRAME_H = 140;
	private static final int GAP = 12;
	private static final int PAD = 10;
	/** Breathing room between the panel's edge and the window's, when it has to shrink. */
	private static final int MARGIN = 4;
	private static final int PANEL_HEIGHT = 212;
	private static final int FRAMES_TOP = 36;
	private static final int BUTTON_TOP = 184;

	/** Per-card vertical rhythm, offsets from the card's own top. */
	private static final int ROLE_TOP = 16;
	private static final int PORTRAIT_CENTER_Y = 74;
	private static final int ABILITY_ROW_TOP = 116;
	/** The ability row's unit at full card width: an 18px slot, a 6px gap. */
	private static final int MAX_SLOT = 18;
	private static final int MIN_SLOT = 12;

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
				.bounds(this.panelLeft() + this.panelWidth() / 2 - 60, this.panelTop() + BUTTON_TOP, 120, 20)
				.build());
	}

	@Override
	public void onClose() {
		// 26.2 moved screen management off Minecraft onto the Gui object.
		//? if >=26.2 {
		this.minecraft.gui.setScreen(this.parent);
		//?} else {
		/*this.minecraft.setScreen(this.parent);
		*///?}
	}

	/**
	 * THE unit of this screen: one card's width. Its ceiling is the authored 112 —
	 * a roomy surface gets exactly the composition the cards were drawn for — and
	 * below that it is whatever three cards, two gaps and the panel's padding can
	 * have without touching the window's edges.
	 */
	private int frameWidth() {
		int room = this.width - MARGIN * 2 - PAD * 2 - GAP * 2;
		return Mth.clamp(room / 3, MIN_FRAME_W, MAX_FRAME_W);
	}

	private int panelWidth() {
		return this.frameWidth() * 3 + GAP * 2 + PAD * 2;
	}

	/** The ability row's slot, in step with the card that holds it. */
	private int slotSize() {
		return Mth.clamp(this.frameWidth() * MAX_SLOT / MAX_FRAME_W, MIN_SLOT, MAX_SLOT);
	}

	private int iconGap() {
		return Math.max(3, this.slotSize() / 3);
	}

	private int panelLeft() {
		return (this.width - this.panelWidth()) / 2;
	}

	private int panelTop() {
		return (this.height - PANEL_HEIGHT) / 2;
	}

	private int frameLeft(final int index) {
		return this.panelLeft() + PAD + index * (this.frameWidth() + GAP);
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

			if (mouseX >= left && mouseX < left + this.frameWidth()) {
				return Archetype.values()[i];
			}
		}

		return null;
	}

	/** One ability-preview slot: a single square or a fork pair, in slot units. */
	private record Slot(int x, int width, SubTree tree, java.util.List<Integer> actives) {
	}

	/** The three slots of one card, geometry shared by draw and hit-test. */
	private java.util.List<Slot> abilitySlots(final int frameIndex, final Archetype archetype) {
		var trees = SubTree.of(archetype);
		int slot = this.slotSize();
		int gap = this.iconGap();
		int[] widths = new int[3];
		int rowWidth = gap * 2;

		for (int i = 0; i < 3; i++) {
			widths[i] = TreeNodes.pickerActives(trees.get(i)).size() > 1 ? slot * 2 : slot;
			rowWidth += widths[i];
		}

		java.util.List<Slot> slots = new java.util.ArrayList<>(3);
		int x = this.frameLeft(frameIndex) + (this.frameWidth() - rowWidth) / 2;

		for (int i = 0; i < 3; i++) {
			SubTree tree = trees.get(i);
			slots.add(new Slot(x, widths[i], tree, TreeNodes.pickerActives(tree)));
			x += widths[i] + gap;
		}

		return slots;
	}

	private @Nullable Slot abilitySlotAt(final double mouseX, final double mouseY) {
		int y = this.framesTop() + ABILITY_ROW_TOP;

		if (mouseY < y || mouseY >= y + this.slotSize()) {
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
	// STAGE 4 — `MouseButtonEvent` is `>=1.21.11`; below it the callback is the three-argument
	// `mouseClicked(double, double, int)`. Header and its three reads only; the confirm dialog
	// and everything it schedules stays one implementation. Same fork as ArchetypeScreen's.
	//? if >=1.21.11 {
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}

		Archetype picked = this.frameAt(event.x(), event.y());

		if (event.button() != 0 || picked == null) {
	//?} else {
	/*public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}

		Archetype picked = this.frameAt(mouseX, mouseY);

		if (button != 0 || picked == null) {
	*///?}
			return false;
		}

		// Confirm: the choice can't be undone until Amnesia II is brewable,
		// so make them say yes.
		/*? if >=26.2 {*/this.minecraft.gui.setScreen(new ConfirmScreen(
		/*?} else *///this.minecraft.setScreen(new ConfirmScreen(
				confirmed -> {
					if (confirmed) {
						Net.INSTANCE.sendToServer(WireId.PICK_ARCHETYPE, buf -> buf.writeUtf(picked.id()));
						// Straight into the new tree rather than back to the
						// inventory. Passing `picked` rather than reading the
						// attachment: the server owns it and the sync has not
						// landed yet, but we already know what was chosen.
						//? if >=26.2 {
						this.minecraft.gui.setScreen(new ArchetypeScreen(this.parent, picked));
						//?} else {
						/*this.minecraft.setScreen(new ArchetypeScreen(this.parent, picked));
						*///?}
					} else {
						//? if >=26.2 {
						this.minecraft.gui.setScreen(this);
						//?} else {
						/*this.minecraft.setScreen(this);
						*///?}
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
	// The same `Screen` draw-method move as ArchetypeScreen's — see the note there.
	//? if >=26.1 {
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
	//?} else {
	/*public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float a) {
	*///?}
		this.advanceHover(mouseX, mouseY);

		// THE BACKGROUND HAS TO BE DOWN BEFORE ANY PANEL PIXEL. Same three bands as
		// ArchetypeScreen's — the full account is on the neutered `renderBackground` below.
		// >=1.21.11: `renderWithTooltipAndSubtitles` already drew it before entering here.
		// 1.21.1:    `Screen.render` would draw it AFTER us, blurring the panel.
		// 1.20.1:    `Screen.render` never draws one; without this there is no backdrop.
		//? if >=1.21.11 {
		//?} elif >=1.20.5 {
		/*super.renderBackground(graphics, mouseX, mouseY, a);
		*///?} else {
		/*super.renderBackground(graphics);
		*///?}

		int panelLeft = this.panelLeft();
		int panelTop = this.panelTop();

		VanillaUi.window(graphics, panelLeft, panelTop, this.panelWidth(), PANEL_HEIGHT);

		//? if >=26.1 {
		graphics.text(this.font, this.title, (this.width - this.font.width(this.title)) / 2, panelTop + 8,
		//?} else {
		/*graphics.drawString(this.font, this.title, (this.width - this.font.width(this.title)) / 2, panelTop + 8,
		*///?}
				VanillaUi.LABEL, false);

		Component prompt = Component.translatable("screen.archetypes.picker.prompt");
		//? if >=26.1 {
		graphics.text(this.font, prompt, (this.width - this.font.width(prompt)) / 2, panelTop + 21,
		//?} else {
		/*graphics.drawString(this.font, prompt, (this.width - this.font.width(prompt)) / 2, panelTop + 21,
		*///?}
				VanillaUi.LABEL_FAINT, false);

		int top = this.framesTop();
		int frameWidth = this.frameWidth();
		Archetype hovered = this.frameAt(mouseX, mouseY);

		// Frames and their resting crests first, so a grown crest from any frame
		// can overlap its neighbours rather than being painted over by them.
		for (int i = 0; i < Archetype.values().length; i++) {
			Archetype archetype = Archetype.values()[i];
			int left = this.frameLeft(i);

			VanillaUi.inset(graphics, left, top, frameWidth, FRAME_H);

			if (hovered == archetype) {
				graphics.fill(left + 1, top + 1, left + frameWidth - 1, top + FRAME_H - 1,
						VanillaUi.INSET_BODY_HOVERED);
			}

			if (this.hover[i] <= 0.0F) {
				this.figure(graphics, archetype, i, 0.0F);
			}

			Component name = archetype.tierName(0);
			//? if >=26.1 {
			graphics.text(this.font, name, left + (frameWidth - this.font.width(name)) / 2, top + 6,
			//?} else {
			/*graphics.drawString(this.font, name, left + (frameWidth - this.font.width(name)) / 2, top + 6,
			*///?}
					archetype.color(), true);

			// The role line: what you'll be doing, always visible.
			int roleY = top + ROLE_TOP;

			for (FormattedCharSequence line : this.font.split(archetype.role(), frameWidth - 8)) {
				//? if >=26.1 {
				graphics.text(this.font, line, left + (frameWidth - this.font.width(line)) / 2, roleY,
				//?} else {
				/*graphics.drawString(this.font, line, left + (frameWidth - this.font.width(line)) / 2, roleY,
				*///?}
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

		// Widgets last: Screen.extractRenderState only walks the renderables, so
		// anything drawn after it covers the buttons.
		//? if >=26.1 {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		//?} else {
		/*super.render(graphics, mouseX, mouseY, a);
		*///?}

		// The ability preview floats over everything, Cancel included.
		Slot slot = this.abilitySlotAt(mouseX, mouseY);

		if (slot != null) {
			// `setTooltipForNextFrame` is `>=1.21.11`; below it the immediate
			// `renderTooltip(Font, List<? extends FormattedCharSequence>, int, int)`. This is
			// already the last thing the draw does, so the two land the same pixels.
			//? if >=1.21.11 {
			graphics.setTooltipForNextFrame(this.font, this.abilityTooltip(slot), mouseX, mouseY);
			//?} else {
			/*graphics.renderTooltip(this.font, this.abilityTooltip(slot), mouseX, mouseY);
			*///?}
		}
	}

	// 1.21.1 ONLY — the menu-blur phase trap, measured in the 1.21.1 client jar.
	//
	// There `Screen.render` is `renderBackground(g, mouseX, mouseY, a)` and THEN the
	// renderable walk, and `Screen.renderBackground` calls `renderBlurredBackground`, which
	// is `GameRenderer.processBlurEffect` — a post-process pass over the whole main render
	// target. `GuiGraphics.fill`, `drawString`, `fillGradient` and `renderItem` all flush
	// eagerly on that version, so every panel, label and item icon this screen drew is
	// already IN the framebuffer when the blur runs, and comes back blurred.
	//
	// The draw method above is `render` below 26.1, so it draws before that call. The fix is
	// vanilla's own shape (`AbstractContainerScreen`): background first, content on top of
	// it, widgets last. The background is therefore drawn explicitly at the top of the draw,
	// and `Screen.render`'s own later call has to become a no-op — otherwise the blur runs a
	// second time, over the content, and the menu gradient darkens it twice.
	//
	// Only 1.21.1 needs it. At and above 1.21.11 the background moved out of `Screen.render`
	// into the final `renderWithTooltipAndSubtitles`, which runs it BEFORE `render`; on
	// 1.20.1 `Screen.render` walks the renderables only and there is no blur to trip over.
	// Nothing else on this screen calls `renderBackground` — on 1.21.1 `Screen.render` is its
	// only caller — so the neutered override costs exactly the duplicate pass.
	//? if >=1.21.11 {
	//?} elif >=1.20.5 {
	/*@Override
	public void renderBackground(final GuiGraphics graphics, final int mouseX, final int mouseY,
			final float a) {
		// Deliberately empty. Drawn at the top of render() instead — see above.
	}
	*///?}

	/** One card's row of active-ability previews. */
	//? if >=26.1 {
	private void abilityRow(final GuiGraphicsExtractor graphics, final int frameIndex,
	//?} else {
	/*private void abilityRow(final GuiGraphics graphics, final int frameIndex,
	*///?}
			final Archetype archetype, final int mouseX, final int mouseY) {
		int y = this.framesTop() + ABILITY_ROW_TOP;
		int slotSize = this.slotSize();

		for (Slot slot : this.abilitySlots(frameIndex, archetype)) {
			VanillaUi.inset(graphics, slot.x(), y, slot.width(), slotSize);

			boolean hoveredSlot = mouseX >= slot.x() && mouseX < slot.x() + slot.width()
					&& mouseY >= y && mouseY < y + slotSize;

			if (hoveredSlot) {
				graphics.fill(slot.x() + 1, y + 1, slot.x() + slot.width() - 1, y + slotSize - 1,
						VanillaUi.INSET_BODY_HOVERED);
			}

			// The slot's bevel inset, which at the full 18px slot is the native
			// 16x16 the tree screen draws — the two stay the same picture.
			for (int i = 0; i < slot.actives().size(); i++) {
				VanillaUi.nodeIcon(graphics, slot.tree(), slot.actives().get(i),
						slot.x() + 1 + i * slotSize, y + 1, slotSize - 2);
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
	//? if >=26.1 {
	private void figure(final GuiGraphicsExtractor graphics, final Archetype archetype,
	//?} else {
	/*private void figure(final GuiGraphics graphics, final Archetype archetype,
	*///?}
			final int index, final float progress) {
		float eased = progress * progress * (3.0F - 2.0F * progress);
		// The crest is quoted against the card, not the screen: a card that had to
		// shrink to fit the surface would otherwise keep a 60px crest (75px hovered)
		// and burst its own frame.
		float card = this.frameWidth() / (float) MAX_FRAME_W;
		int size = Math.round(PORTRAIT * card * Mth.lerp(eased, 1.0F, HOVER_SCALE));
		int centerX = this.frameLeft(index) + this.frameWidth() / 2;
		int centerY = this.framesTop() + PORTRAIT_CENTER_Y;

		Identifier portrait = archetype.portrait();

		if (portrait == null) {
			// Hand-composed crests (user layout), blooming with the same
			// ease as the painted art — and shrinking with the card on the
			// same factor as the painted one, so the two stay one screen.
			float scale = Mth.lerp(eased, 2.0F, 2.5F) * card;
			// `GuiGraphics.pose()` is a 2-D `Matrix3x2fStack` from 1.21.11 up and a 3-D
			// `PoseStack` below it (`var` absorbs the type change; push/translate/scale/pop
			// gain a z). The bloom easing and `scale` itself are computed above, outside the
			// fork — conventions §5b — and go in unchanged with a zero z and a unit z-scale.
			var pose = graphics.pose();
			//? if >=1.21.11 {
			pose.pushMatrix();
			pose.translate(centerX, centerY);
			pose.scale(scale, scale);
			//?} else {
			/*pose.pushPose();
			pose.translate((float) centerX, (float) centerY, 0.0F);
			pose.scale(scale, scale, 1.0F);
			*///?}

			if (archetype == Archetype.AGILITY) {
				// The heraldic ⚔ arrangement — judged in a bake-off against
				// tip-to-tip layouts, which is what two earlier rounds
				// accidentally were: the MIRRORED dagger goes on the LEFT so
				// both tips point up-and-OUT and the blades pass through
				// each other mid-blade (2px apart, 18px). The 20px crossbow
				// and bow lean in behind, lower halves buried.
				//? if >=1.21.11 {
				graphics.blit(RenderPipelines.GUI_TEXTURED, CROSSBOW_LEFT,
						-19, -16, 0.0F, 0.0F, 20, 20, 16, 16, 16, 16);
				graphics.blit(RenderPipelines.GUI_TEXTURED, VANILLA_BOW_DRAWN,
						-1, -16, 0.0F, 0.0F, 20, 20, 16, 16, 16, 16);
				graphics.blit(RenderPipelines.GUI_TEXTURED, DAGGER_LEFT,
						-10, -8, 0.0F, 0.0F, 18, 18, 16, 16, 16, 16);
				graphics.blit(RenderPipelines.GUI_TEXTURED, DAGGER,
						-8, -8, 0.0F, 0.0F, 18, 18, 16, 16, 16, 16);
				//?} else {
				/*graphics.blit(CROSSBOW_LEFT, -19, -16, 20, 20, 0.0F, 0.0F, 16, 16, 16, 16);
				graphics.blit(VANILLA_BOW_DRAWN, -1, -16, 20, 20, 0.0F, 0.0F, 16, 16, 16, 16);
				graphics.blit(DAGGER_LEFT, -10, -8, 18, 18, 0.0F, 0.0F, 16, 16, 16, 16);
				graphics.blit(DAGGER, -8, -8, 18, 18, 0.0F, 0.0F, 16, 16, 16, 16);
				*///?}
			} else {
				// Same ⚔ logic: mirrored spike LEFT (head up-left), original
				// flamethrower RIGHT (head up-right), shafts 5px apart so
				// they genuinely interpenetrate; the spike rides 3px higher
				// so its crystal keeps a visible neck above the crossing.
				// The mana potion hangs in front at the crossing's foot.
				//? if >=1.21.11 {
				graphics.blit(RenderPipelines.GUI_TEXTURED, SPIKE_LEFT,
						-12, -19, 0.0F, 0.0F, 20, 20, 32, 32, 32, 32);
				graphics.blit(RenderPipelines.GUI_TEXTURED, FLAME_ICON,
						-7, -16, 0.0F, 0.0F, 20, 20, 32, 32, 32, 32);
				//?} else {
				/*graphics.blit(SPIKE_LEFT, -12, -19, 20, 20, 0.0F, 0.0F, 32, 32, 32, 32);
				graphics.blit(FLAME_ICON, -7, -16, 20, 20, 0.0F, 0.0F, 32, 32, 32, 32);
				*///?}
					//? if >=26.1 {
				graphics.fakeItem(net.minecraft.world.item.alchemy.PotionContents.createItemStack(
						net.minecraft.world.item.Items.POTION,
						com.archetypes.ManaPotions.MANA_REGENERATION), -8, -1);
				//?} elif >=1.21 {
				/*graphics.renderFakeItem(net.minecraft.world.item.alchemy.PotionContents.createItemStack(
						net.minecraft.world.item.Items.POTION,
						com.archetypes.ManaPotions.MANA_REGENERATION), -8, -1);
				*///?} else {
				/*// No `PotionContents` component below 1.21 — the potion goes into the stack's
				// NBT, which is what `PotionUtils.setPotion` does (see ModItems' creative tab).
				graphics.renderFakeItem(net.minecraft.world.item.alchemy.PotionUtils.setPotion(
						new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION),
						com.archetypes.ManaPotions.MANA_REGENERATION.value()), -8, -1);
				*///?}
			}

			//? if >=1.21.11 {
			pose.popMatrix();
			//?} else {
			/*pose.popPose();
			*///?}
			return;
		}

		//? if >=1.21.11 {
		graphics.blit(RenderPipelines.GUI_TEXTURED, portrait,
				centerX - size / 2, centerY - size / 2, 0.0F, 0.0F,
				size, size, PORTRAIT_TEXTURE, PORTRAIT_TEXTURE, PORTRAIT_TEXTURE, PORTRAIT_TEXTURE);
		//?} else {
		/*graphics.blit(portrait,
				centerX - size / 2, centerY - size / 2, size, size, 0.0F, 0.0F,
				PORTRAIT_TEXTURE, PORTRAIT_TEXTURE, PORTRAIT_TEXTURE, PORTRAIT_TEXTURE);
		*///?}
	}

	/** Native size of the portrait textures. */
	private static final int PORTRAIT_TEXTURE = 256;

	/** The collage materials — mirrored variants are PRE-BAKED assets
	 * (textures/gui/collage), because a negative-scale blit flips the
	 * quad's winding and the GUI renderer culls it into invisibility. */
	private static final Identifier CROSSBOW_LEFT =
			com.archetypes.Archetypes.id("textures/gui/collage/crossbow_left.png");
	private static final Identifier VANILLA_BOW_DRAWN =
			/*? if >=1.21 {*/Identifier.fromNamespaceAndPath("minecraft", "textures/item/bow_pulling_2.png");
			/*?} else *///new Identifier("minecraft", "textures/item/bow_pulling_2.png");
	private static final Identifier DAGGER =
			com.archetypes.Archetypes.id("textures/item/iron_dagger.png");
	private static final Identifier DAGGER_LEFT =
			com.archetypes.Archetypes.id("textures/gui/collage/dagger_left.png");
	private static final Identifier SPIKE_LEFT =
			com.archetypes.Archetypes.id("textures/gui/collage/spike_left.png");
	private static final Identifier FLAME_ICON =
			com.archetypes.Archetypes.id("textures/node/elementalist/flamethrower.png");
}
