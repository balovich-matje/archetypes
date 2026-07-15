package com.archetypes.client;

import java.util.List;
import java.util.Optional;

import com.archetypes.Archetype;
import com.archetypes.Constellation;
import com.archetypes.ResetArchetypePayload;
import com.archetypes.SkillPoints;
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
import net.minecraft.world.entity.player.Player;
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
	/**
	 * Strip under the buttons for the two progress bars: a label row and a bar for
	 * each (10 + 5, twice, plus 8 of breathing room) — the content is 33 tall, so
	 * anything less pushes the second bar out through the panel's bottom edge.
	 */
	private static final int BAR_STRIP = 41;
	private static final int BAR_HEIGHT = 5;
	/** Room at the top of each section for its name, drawn at 1.5x. */
	private static final int SECTION_HEADER = 22;

	/** Node size at full size, and the floor it may shrink to on cramped screens. */
	private static final int MAX_NODE = 18;
	private static final int MIN_NODE = 5;
	/** Clear space between adjacent nodes; spacing is node + gap. */
	private static final int NODE_GAP = 4;
	private static final int MAX_SPACING = 30;
	private static final float SECTION_TITLE_SCALE = 1.5F;
	private static final int BUTTON_WIDTH = 96;
	private static final int BUTTON_HEIGHT = 20;
	/** Art is drawn at 85% of its fitted size, leaving a black frame. */
	private static final float ART_ZOOM = 0.85F;

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
		// creative-only Reset is absent. Sits above the bar strip.
		int buttonY = this.canvasBottom() + 4;

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
		return this.panelTop() + this.panelHeight() - FOOTER - BAR_STRIP;
	}

	private int sectionWidth() {
		return this.canvasWidth() / 3;
	}

	/** Where one constellation's nodes land: grid pitch, node size, and origin. */
	private record Layout(int spacing, int node, int centerX, int rootTop) {
	}

	/**
	 * Fit a constellation to its section and centre it there.
	 *
	 * <p>Both the pitch and the node size fall out of the space available, so a
	 * cramped screen shrinks the whole constellation instead of overflowing —
	 * there is deliberately no lower bound on spacing, only on how small a node
	 * may get. The result is then centred vertically, so a tall canvas does not
	 * leave the tree sitting on the floor.
	 */
	private Layout layout(final int section, final Constellation shape) {
		int availableWidth = this.sectionWidth() - PAD * 2;
		int top = this.canvasTop() + SECTION_HEADER;
		int availableHeight = this.canvasBottom() - top - PAD;

		// shapeSize ~= grid * spacing - NODE_GAP, so this is the pitch that fills
		// the axis exactly; the tighter axis wins.
		int byWidth = (availableWidth + NODE_GAP) / Math.max(shape.width(), 1);
		int byHeight = (availableHeight + NODE_GAP) / Math.max(shape.height(), 1);
		int spacing = Math.min(Math.min(byWidth, byHeight), MAX_SPACING);
		int node = Mth.clamp(spacing - NODE_GAP, MIN_NODE, MAX_NODE);

		// That pitch assumed node == spacing - NODE_GAP. Wherever the node clamps
		// instead — at its ceiling on roomy screens, at its floor on cramped ones —
		// the assumption breaks and the shape can run a few pixels wide, so re-fit
		// the pitch against the node size we actually got.
		if (shape.width() > 1) {
			spacing = Math.min(spacing, (availableWidth - node) / (shape.width() - 1));
		}

		if (shape.height() > 1) {
			spacing = Math.min(spacing, (availableHeight - node) / (shape.height() - 1));
		}

		spacing = Math.max(spacing, 1);
		node = Math.min(node, spacing);

		int shapeHeight = (shape.height() - 1) * spacing + node;
		int rootTop = top + (availableHeight - shapeHeight) / 2 + shapeHeight - node;

		return new Layout(spacing, node, this.sectionCenter(section), rootTop);
	}

	private int sectionCenter(final int section) {
		return this.canvasLeft() + this.sectionWidth() * section + this.sectionWidth() / 2;
	}

	/** Top-left of a node within its section. */
	private static int nodeX(final Constellation shape, final Constellation.Node node, final Layout layout) {
		int shapeWidth = (shape.width() - 1) * layout.spacing();
		return layout.centerX() - shapeWidth / 2 + node.col() * layout.spacing() - layout.node() / 2;
	}

	/** Rows grow upward from the root row. */
	private static int nodeY(final Constellation.Node node, final Layout layout) {
		return layout.rootTop() - node.row() * layout.spacing();
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

		int canvasWidth = this.canvasWidth();
		int canvasHeight = this.canvasBottom() - this.canvasTop();

		// Black behind, then the art fitted to its own aspect ratio and pulled in
		// to ART_ZOOM. Fitting rather than stretching keeps it undistorted, and the
		// smaller draw means less upscaling — both help how soft it looks. What is
		// left over reads as a deliberate black frame.
		graphics.fill(this.canvasLeft(), this.canvasTop(),
				this.canvasLeft() + canvasWidth, this.canvasBottom(), 0xFF000000);

		float scale = Math.min(canvasWidth / (float) ART_WIDTH, canvasHeight / (float) ART_HEIGHT) * ART_ZOOM;
		int artWidth = Math.round(ART_WIDTH * scale);
		int artHeight = Math.round(ART_HEIGHT * scale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, this.archetype.treeBackground(),
				this.canvasLeft() + (canvasWidth - artWidth) / 2,
				this.canvasTop() + (canvasHeight - artHeight) / 2,
				0.0F, 0.0F, artWidth, artHeight, ART_WIDTH, ART_HEIGHT, ART_WIDTH, ART_HEIGHT);

		VanillaUi.insetBorder(graphics, this.canvasLeft(), this.canvasTop(), canvasWidth, canvasHeight);

		boolean tooltip = false;

		for (int section = 0; section < this.subTrees.size(); section++) {
			SubTree tree = this.subTrees.get(section);
			Constellation shape = tree.constellation();
			Layout layout = this.layout(section, shape);
			int size = layout.node();

			this.sectionTitle(graphics, tree, section);

			// Connections first, so nodes sit on top of the line ends.
			for (int[] edge : shape.edges()) {
				Constellation.Node from = shape.nodes().get(edge[0]);
				Constellation.Node to = shape.nodes().get(edge[1]);
				VanillaUi.line(graphics,
						nodeX(shape, from, layout) + size / 2, nodeY(from, layout) + size / 2,
						nodeX(shape, to, layout) + size / 2, nodeY(to, layout) + size / 2,
						VanillaUi.INSET_BODY);
			}

			for (Constellation.Node node : shape.nodes()) {
				int x = nodeX(shape, node, layout);
				int y = nodeY(node, layout);
				boolean hovered = mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;

				VanillaUi.inset(graphics, x, y, size, size);

				if (hovered) {
					graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, VanillaUi.INSET_BODY_HOVERED);
					tooltip = true;
				}
			}

			if (section < this.subTrees.size() - 1) {
				VanillaUi.verticalDivider(graphics,
						this.canvasLeft() + this.sectionWidth() * (section + 1) - 1,
						this.canvasTop() + 4, this.canvasBottom() - 4);
			}
		}

		this.progressBars(graphics);

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
	 * Two bars under the buttons: the long road from start tier to peak, and the
	 * short one to the next point.
	 *
	 * <p>Read straight off the attachment, which syncs to its owning client, so
	 * there is no separate packet to keep in step.
	 */
	private void progressBars(final GuiGraphicsExtractor graphics) {
		if (this.minecraft.player == null) {
			return;
		}

		Player player = this.minecraft.player;
		int left = this.panelLeft() + PAD;
		int width = this.panelWidth() - PAD * 2;
		int top = this.canvasBottom() + FOOTER;

		int level = SkillPoints.level(player);
		int unspent = SkillPoints.available(player);

		// Long bar: start tier -> peak tier.
		Component road = Component.translatable("screen.archetypes.tree.bar.archetype",
				this.archetype.tierName(0), this.archetype.tierName(1));
		Component levelText = Component.translatable("screen.archetypes.tree.bar.level", level
				+ "/" + SkillPoints.MAX_LEVEL);
		graphics.text(this.font, road, left, top, VanillaUi.LABEL, false);
		graphics.text(this.font, levelText, left + width - this.font.width(levelText), top,
				VanillaUi.LABEL, false);
		VanillaUi.progressBar(graphics, left, top + 10, width, BAR_HEIGHT,
				SkillPoints.archetypeProgress(player), this.archetype.color());

		// Short bar: XP into the current level. Points you have not committed are
		// worth surfacing here — it is the only place they are visible.
		Component next = Component.literal(SkillPoints.xpIntoLevel(player)
				+ "/" + SkillPoints.XP_PER_LEVEL + " XP");
		graphics.text(this.font, next, left, top + 18, VanillaUi.LABEL_FAINT, false);

		if (unspent > 0) {
			Component spare = Component.translatable("screen.archetypes.tree.points", unspent);
			graphics.text(this.font, spare, left + width - this.font.width(spare), top + 18,
					this.archetype.color(), false);
		}

		VanillaUi.progressBar(graphics, left, top + 28, width, BAR_HEIGHT,
				SkillPoints.levelProgress(player), 0xFF7FCF5F);
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
