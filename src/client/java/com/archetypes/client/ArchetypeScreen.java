package com.archetypes.client;

import java.util.List;
import java.util.Optional;

import com.archetypes.Archetype;
import com.archetypes.Constellation;
import com.archetypes.ResetArchetypePayload;
import com.archetypes.SubTree;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The archetype's skill tree: a vanilla window covering ~90% of the screen,
 * its canvas split into three sections by thin dividers, one per sub-tree.
 * Each section is headed by its name and holds a constellation — the sub-tree's
 * symbol drawn in nodes (shield, sword, mace, ...), rooted at the bottom and
 * growing upward.
 *
 * <p>Every node is a placeholder ("+1% damage", no effect); the shapes are what
 * is being judged. Class-fantasy background art comes later, per class, and is
 * deliberately unrelated to these layouts — the section headers are drawn faint
 * so they read as part of that art once it lands.
 */
public class ArchetypeScreen extends Screen {
	/** Fraction of the screen the window covers. */
	private static final int PANEL_PERCENT = 90;
	private static final int PAD = 8;
	private static final int HEADER = 22;
	/** Strip below the canvas holding the Back / Reset buttons. */
	private static final int FOOTER = 28;
	/** Room at the top of each section for its name, drawn at 1.5x. */
	private static final int SECTION_HEADER = 22;

	private static final int NODE = 18;
	private static final int MIN_SPACING = NODE + 2;
	private static final int MAX_SPACING = 30;
	private static final float SECTION_TITLE_SCALE = 1.5F;
	private static final int BUTTON_WIDTH = 96;
	private static final int BUTTON_HEIGHT = 20;

	/** Native size of the backdrop textures; they are scaled to the canvas. */
	private static final int ART_WIDTH = 1024;
	private static final int ART_HEIGHT = 576;

	private final @Nullable Screen parent;
	private final Archetype archetype;
	private final List<SubTree> subTrees;

	public ArchetypeScreen(final @Nullable Screen parent, final Archetype archetype) {
		super(Component.translatable("screen.archetypes.tree.title",
				archetype.tierName(0).copy().withStyle(style -> style.withColor(archetype.color() & 0xFFFFFF))));
		this.parent = parent;
		this.archetype = archetype;
		this.subTrees = SubTree.of(archetype);
	}

	@Override
	protected void init() {
		// Anchored to the panel's bottom corners, so Back does not drift when the
		// creative-only Reset is absent.
		int buttonY = this.panelTop() + this.panelHeight() - PAD - BUTTON_HEIGHT;

		this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose())
				.bounds(this.panelLeft() + PAD, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());

		// Creative-only testing affordance: undo the "permanent" choice. The
		// server re-checks game mode; this button just hides the option.
		if (this.minecraft.player == null || !this.minecraft.player.isCreative()) {
			return;
		}

		this.addRenderableWidget(Button.builder(Component.translatable("screen.archetypes.tree.reset"), button -> {
					ClientPlayNetworking.send(new ResetArchetypePayload());
					this.minecraft.gui.setScreen(new ArchetypePickerScreen(this.parent));
				})
				.bounds(this.panelLeft() + this.panelWidth() - PAD - BUTTON_WIDTH, buttonY,
						BUTTON_WIDTH, BUTTON_HEIGHT)
				.tooltip(Tooltip.create(Component.translatable("screen.archetypes.tree.reset.tooltip")))
				.build());
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	private int panelWidth() {
		return this.width * PANEL_PERCENT / 100;
	}

	private int panelHeight() {
		return this.height * PANEL_PERCENT / 100;
	}

	private int panelLeft() {
		return (this.width - this.panelWidth()) / 2;
	}

	private int panelTop() {
		return (this.height - this.panelHeight()) / 2;
	}

	private int canvasLeft() {
		return this.panelLeft() + PAD;
	}

	private int canvasTop() {
		return this.panelTop() + HEADER;
	}

	private int canvasWidth() {
		return this.panelWidth() - PAD * 2;
	}

	private int canvasBottom() {
		return this.panelTop() + this.panelHeight() - FOOTER;
	}

	private int sectionWidth() {
		return this.canvasWidth() / 3;
	}

	/**
	 * Grid spacing for one constellation: the largest that fits its section in
	 * both axes, so shapes of different grid sizes all fill their space.
	 */
	private int spacing(final Constellation shape) {
		int usableWidth = this.sectionWidth() - PAD * 2 - NODE;
		int usableHeight = this.canvasBottom() - this.canvasTop() - SECTION_HEADER - PAD * 2 - NODE;
		int byWidth = shape.width() > 1 ? usableWidth / (shape.width() - 1) : MAX_SPACING;
		int byHeight = shape.height() > 1 ? usableHeight / (shape.height() - 1) : MAX_SPACING;
		return Mth.clamp(Math.min(byWidth, byHeight), MIN_SPACING, MAX_SPACING);
	}

	private int sectionCenter(final int section) {
		return this.canvasLeft() + this.sectionWidth() * section + this.sectionWidth() / 2;
	}

	/** Top-left of a node, given its section and the spacing for that shape. */
	private int nodeX(final int section, final Constellation shape, final Constellation.Node node,
			final int spacing) {
		int shapeWidth = (shape.width() - 1) * spacing;
		return this.sectionCenter(section) - shapeWidth / 2 + node.col() * spacing - NODE / 2;
	}

	private int nodeY(final Constellation shape, final Constellation.Node node, final int spacing) {
		// Roots sit just above the canvas floor; row grows upward from there.
		int rootTop = this.canvasBottom() - PAD - NODE;
		return rootTop - node.row() * spacing;
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		int panelLeft = this.panelLeft();
		int panelTop = this.panelTop();

		VanillaUi.window(graphics, panelLeft, panelTop, this.panelWidth(), this.panelHeight());

		graphics.text(this.font, this.title, panelLeft + PAD, panelTop + 8, VanillaUi.LABEL, false);

		Component preview = Component.translatable("screen.archetypes.tree.preview");
		graphics.text(this.font, preview, panelLeft + this.panelWidth() - PAD - this.font.width(preview),
				panelTop + 8, VanillaUi.LABEL_FAINT, false);

		int canvasHeight = this.canvasBottom() - this.canvasTop();

		// The backdrop fills the canvas; its dim and vignette are baked in, so the
		// nodes go straight on top without any per-frame gradient work.
		graphics.blit(RenderPipelines.GUI_TEXTURED, this.archetype.treeBackground(),
				this.canvasLeft(), this.canvasTop(), 0.0F, 0.0F,
				this.canvasWidth(), canvasHeight, ART_WIDTH, ART_HEIGHT, ART_WIDTH, ART_HEIGHT);
		VanillaUi.insetBorder(graphics, this.canvasLeft(), this.canvasTop(),
				this.canvasWidth(), canvasHeight);

		boolean tooltip = false;

		for (int section = 0; section < this.subTrees.size(); section++) {
			SubTree tree = this.subTrees.get(section);
			Constellation shape = tree.constellation();
			int spacing = this.spacing(shape);

			this.sectionTitle(graphics, tree, section);

			// Connections first, so nodes sit on top of the line ends.
			for (int[] edge : shape.edges()) {
				Constellation.Node from = shape.nodes().get(edge[0]);
				Constellation.Node to = shape.nodes().get(edge[1]);
				VanillaUi.line(graphics,
						this.nodeX(section, shape, from, spacing) + NODE / 2,
						this.nodeY(shape, from, spacing) + NODE / 2,
						this.nodeX(section, shape, to, spacing) + NODE / 2,
						this.nodeY(shape, to, spacing) + NODE / 2,
						VanillaUi.INSET_BODY);
			}

			for (Constellation.Node node : shape.nodes()) {
				int x = this.nodeX(section, shape, node, spacing);
				int y = this.nodeY(shape, node, spacing);
				boolean hovered = mouseX >= x && mouseX < x + NODE && mouseY >= y && mouseY < y + NODE;

				VanillaUi.slot(graphics, x, y);

				if (hovered) {
					graphics.fill(x + 1, y + 1, x + NODE - 1, y + NODE - 1, VanillaUi.INSET_BODY_HOVERED);
					tooltip = true;
				}
			}

			if (section < this.subTrees.size() - 1) {
				VanillaUi.verticalDivider(graphics,
						this.canvasLeft() + this.sectionWidth() * (section + 1) - 1,
						this.canvasTop() + 4, this.canvasBottom() - 4);
			}
		}

		// Widgets last: Screen.extractRenderState only walks the renderables, so
		// anything drawn after it covers the buttons.
		super.extractRenderState(graphics, mouseX, mouseY, a);

		if (tooltip) {
			graphics.setTooltipForNextFrame(this.font,
					List.of(Component.translatable("node.archetypes.placeholder")),
					Optional.empty(), mouseX, mouseY);
		}
	}

	/**
	 * The sub-tree's name across the top of its section: bold and 1.5x, in a
	 * washed-out tone so it sits into the backdrop art rather than competing with
	 * the nodes.
	 */
	private void sectionTitle(final GuiGraphicsExtractor graphics, final SubTree tree, final int section) {
		Component label = tree.displayName().copy().withStyle(ChatFormatting.BOLD);
		float x = this.sectionCenter(section) - this.font.width(label) * SECTION_TITLE_SCALE / 2.0F;
		float y = this.canvasTop() + 6;

		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(SECTION_TITLE_SCALE, SECTION_TITLE_SCALE);
		graphics.text(this.font, label, 0, 0, VanillaUi.SECTION_TITLE, false);
		graphics.pose().popMatrix();
	}
}
