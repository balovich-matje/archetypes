package com.archetypes.client;

import java.util.List;

import com.archetypes.Archetype;
import com.archetypes.Constellation;
import com.archetypes.NodePurchases;
import com.archetypes.ProtectorNodes;
import com.archetypes.SkillPoints;
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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.11 {
import net.minecraft.client.input.MouseButtonEvent;
//?}
//? if >=1.21.11 {
import net.minecraft.client.renderer.RenderPipelines;
//?}
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
//? if >=1.21.11 {
import org.jspecify.annotations.Nullable;
//?} else {
/*import org.jetbrains.annotations.Nullable;
*///?}

/**
 * The archetype's skill tree: a vanilla window covering ~90% of the screen,
 * its canvas split into three sections by thin dividers, one per sub-tree.
 * Each section is headed by its name and holds a constellation — the sub-tree's
 * symbol drawn in nodes (shield, sword, mace, ...), rooted at the bottom and
 * growing upward.
 *
 * <p>Nodes carry their skill's item icon and a wrapped description on hover;
 * clicking a buyable one spends a point (server re-validates). The section
 * headers are drawn faint so they sit into the backdrop art.
 *
 * <h2>What scales and what does not</h2>
 *
 * <p>Everything here is in GUI-scaled pixels, which is the unit vanilla's font
 * and widgets are drawn in — so the chrome (header, buttons, switchers, progress
 * bars, tooltips) is deliberately FIXED, exactly like every vanilla screen: at a
 * smaller GUI scale you get more room, not bigger text. The constellation is the
 * opposite kind of thing — a diagram, not a label — so it is FLUID: a single cell
 * {@link #pitch()} is computed from the section box and the largest grid this
 * archetype can put in it, and node size, halo ring, connection thickness and
 * icon size are all derived from that one unit. A tree therefore fills the same
 * fraction of its section at GUI scale 1, 2 and 3 instead of being an island of
 * fixed 18px squares on a big screen and a clipped, icon-less huddle on a small
 * one.
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

	/**
	 * The node square as a fraction of the cell it sits in; the rest of the cell is
	 * the gap. 0.6 is the reference composition read back off it — an 18px node on
	 * a 30px pitch, which is what GUI scale 2 drew before the layout became fluid.
	 */
	private static final float NODE_RATIO = 0.6F;
	/**
	 * Floor and ceiling on the node square. The floor keeps a node clickable on a
	 * 320x240 surface (the smallest Minecraft's own scale clamp will ever hand us);
	 * the ceiling stops a 4K screen at GUI scale 1 from drawing dinner plates.
	 * Between them the size is whatever the section box says.
	 */
	private static final int MIN_NODE = 5;
	private static final int MAX_NODE = 48;
	/**
	 * The node size the fixed-unit layout was authored at. Ring, connection stroke
	 * and icon inset are quoted as a fraction of it, so at that size they come out
	 * exactly as the reference drew them: 1px each.
	 */
	private static final float REFERENCE_NODE = 18.0F;
	/** Ceiling on the section title's scale; it shrinks below this to fit its section. */
	private static final float SECTION_TITLE_SCALE = 1.5F;
	private static final int BUTTON_WIDTH = 96;
	private static final int BUTTON_HEIGHT = 20;
	/** Art is drawn at 85% of its fitted size, leaving a black frame. */
	private static final float ART_ZOOM = 0.85F;

	/** Native size of the backdrop textures; they are scaled to the canvas. */
	private static final int ART_WIDTH = 1024;
	private static final int ART_HEIGHT = 576;

	/** Small vanilla-look switcher buttons in a section's top-right corner. */
	private static final int SWITCH_W = 14;
	private static final int SWITCH_H = 12;

	private final @Nullable Screen parent;
	private final Archetype archetype;
	/** The three base sub-trees, fixed — the switcher reverts to these. */
	private final List<SubTree> baseTrees;
	/** What each section currently shows: a base tree or its epic sibling. */
	private final List<SubTree> shown;

	/**
	 * The largest grid this screen can ever be asked to draw, over every sub-tree
	 * of this archetype — base AND epic.
	 *
	 * <p>The cell unit is sized against these rather than against what is on screen
	 * right now, which buys two things: nothing can overflow its section whatever a
	 * switcher is set to, and flipping one section to its (much smaller) epic tree
	 * does not resize the two beside it. Measured once, in the constructor: the
	 * shapes are compile-time constants.
	 */
	private final int gridWidth;
	private final int gridHeight;

	/** Per-section epic switchers (null where the section has no epic tree). */
	private final Button[] epicUp = new Button[3];
	private final Button[] epicDown = new Button[3];

	/** The "?" legend: a real vanilla button; clicking pins the tooltip. */
	private Button legendButton;
	private boolean legendPinned;

	public ArchetypeScreen(final @Nullable Screen parent, final Archetype archetype) {
		super(Component.translatable("screen.archetypes.tree.title",
				archetype.tierName(0).copy().withStyle(style -> style.withColor(archetype.color() & 0xFFFFFF))));
		this.parent = parent;
		this.archetype = archetype;
		this.baseTrees = SubTree.of(archetype);
		this.shown = new java.util.ArrayList<>(this.baseTrees);

		int widest = 1;
		int tallest = 1;

		for (SubTree base : this.baseTrees) {
			SubTree epic = base.epicCounterpart();

			for (SubTree tree : epic == null ? List.of(base) : List.of(base, epic)) {
				widest = Math.max(widest, tree.constellation().width());
				tallest = Math.max(tallest, tree.constellation().height());
			}
		}

		this.gridWidth = widest;
		this.gridHeight = tallest;
	}

	@Override
	protected void init() {
		// Anchored to the panel's bottom corners, so Back does not drift when the
		// creative-only Reset is absent. Sits above the bar strip.
		int buttonY = this.canvasBottom() + 4;

		this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose())
				.bounds(this.panelLeft() + PAD, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());

		// The legend rides the header as a real button; a click pins its
		// tooltip open until the next click.
		this.legendButton = this.addRenderableWidget(Button.builder(Component.literal("?"),
						button -> this.legendPinned = !this.legendPinned)
				.bounds(this.panelLeft() + this.panelWidth() - PAD - 20, this.panelTop() + 3, 20, 16)
				.build());

		// The epic switchers: a stacked up/down pair in the top-right of each
		// section whose base tree has an epic sibling. Up previews the epic
		// tree, down returns to the base. Viewing is free; buying is gated by
		// having epic points. Rendered only for eligible sections.
		for (int s = 0; s < this.baseTrees.size(); s++) {
			SubTree base = this.baseTrees.get(s);
			SubTree epic = base.epicCounterpart();

			if (epic == null) {
				continue;
			}

			final int section = s;
			int right = this.canvasLeft() + this.sectionWidth() * (s + 1);
			int switchX = right - PAD - SWITCH_W;
			int switchY = this.canvasTop() + 4;

			this.epicUp[s] = this.addRenderableWidget(Button.builder(Component.literal("^"),
							button -> this.shown.set(section, epic))
					.bounds(switchX, switchY, SWITCH_W, SWITCH_H)
					.tooltip(Tooltip.create(Component.translatable("screen.archetypes.tree.epic.to_epic")))
					.build());
			this.epicDown[s] = this.addRenderableWidget(Button.builder(Component.literal("v"),
							button -> this.shown.set(section, base))
					.bounds(switchX, switchY + SWITCH_H + 1, SWITCH_W, SWITCH_H)
					.tooltip(Tooltip.create(Component.translatable("screen.archetypes.tree.epic.to_base")))
					.build());
		}

		// Creative-only testing affordance: undo the "permanent" choice. The
		// server re-checks game mode; this button just hides the option.
		if (this.minecraft.player == null || !this.minecraft.player.isCreative()) {
			return;
		}

		this.addRenderableWidget(Button.builder(Component.translatable("screen.archetypes.tree.reset"), button -> {
					Net.INSTANCE.sendToServer(WireId.RESET_ARCHETYPE, buf -> { });
					// 26.2 moved screen management off Minecraft onto the Gui object.
					//? if >=26.2 {
					this.minecraft.gui.setScreen(new ArchetypePickerScreen(this.parent));
					//?} else {
					/*this.minecraft.setScreen(new ArchetypePickerScreen(this.parent));
					*///?}
				})
				.bounds(this.panelLeft() + this.panelWidth() - PAD - BUTTON_WIDTH, buttonY,
						BUTTON_WIDTH, BUTTON_HEIGHT)
				.tooltip(Tooltip.create(Component.translatable("screen.archetypes.tree.reset.tooltip")))
				.build());
	}

	@Override
	public void onClose() {
		//? if >=26.2 {
		this.minecraft.gui.setScreen(this.parent);
		//?} else {
		/*this.minecraft.setScreen(this.parent);
		*///?}
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

	/** The area one constellation is fitted into: the section box minus its header. */
	private int treeTop() {
		return this.canvasTop() + SECTION_HEADER;
	}

	private int treeHeight() {
		return Math.max(1, this.canvasBottom() - this.treeTop() - PAD);
	}

	private int treeWidth() {
		return Math.max(1, this.sectionWidth() - PAD * 2);
	}

	/**
	 * THE unit. One cell of the node grid, in GUI-scaled pixels, for every section
	 * of this screen.
	 *
	 * <p>It is the pitch at which the largest grid this archetype owns exactly fills
	 * the tighter axis of a section — so the constellation covers the same fraction
	 * of the panel whatever the GUI scale is, and the panel is itself a fraction of
	 * the screen. Nothing here is a fixed pixel count except the two clamps.
	 *
	 * <p>A float on purpose. An integer pitch quantises: at a pitch the section can
	 * only afford 26.7 of, rounding down to 26 throws away 0.7px per column — nine
	 * columns of a shield lose six pixels off the width, which reads as the tree
	 * having drifted off-centre. The positions round, the pitch does not.
	 */
	private float pitch() {
		return Math.min(this.treeWidth() / (float) this.gridWidth,
				this.treeHeight() / (float) this.gridHeight);
	}

	/**
	 * The node square for that pitch, clamped.
	 *
	 * <p>The clamps are on the NODE and never on the pitch: clamping the pitch up
	 * on a cramped screen would push the bottom rows out through the canvas floor,
	 * whereas a node that has stopped shrinking merely closes the gaps. The last
	 * line is that same guard from the other side — a clamped-up node may not grow
	 * past its own cell and start overlapping its neighbour.
	 */
	private int nodeSize() {
		float pitch = this.pitch();
		int node = Mth.clamp(Math.round(pitch * NODE_RATIO), MIN_NODE, MAX_NODE);
		return Math.min(node, Math.max(3, Math.round(pitch)));
	}

	/**
	 * Everything else the constellation draws, in units of the node: the halo ring,
	 * the connection stroke, the icon's inset from the node's edge. One expression
	 * so they cannot drift apart, and 1 at the reference node size — which is what
	 * makes a scale-2 screen come out pixel-for-pixel as it was authored.
	 */
	private static int stroke(final int node) {
		return Math.max(1, Math.round(node / REFERENCE_NODE));
	}

	/** Where one constellation's nodes land: grid pitch, node size, and origin. */
	private record Layout(float pitch, int node, int centerX, float rootTop) {
	}

	/**
	 * Centre a constellation in its section.
	 *
	 * <p>The pitch and the node are the screen's, not this shape's, so three
	 * sections of different grids still read as one drawing. Only the vertical
	 * origin is per-shape: a short tree is centred in the canvas rather than left
	 * sitting on the floor.
	 */
	private Layout layout(final int section, final Constellation shape) {
		float pitch = this.pitch();
		int node = this.nodeSize();
		float shapeHeight = (shape.height() - 1) * pitch + node;
		float rootTop = this.treeTop() + (this.treeHeight() - shapeHeight) / 2.0F + shapeHeight - node;

		return new Layout(pitch, node, this.sectionCenter(section), rootTop);
	}

	private int sectionCenter(final int section) {
		return this.canvasLeft() + this.sectionWidth() * section + this.sectionWidth() / 2;
	}

	/** Top-left of a node within its section. */
	private static int nodeX(final Constellation shape, final Constellation.Node node, final Layout layout) {
		float shapeWidth = (shape.width() - 1) * layout.pitch();
		return Math.round(layout.centerX() - shapeWidth / 2.0F
				+ node.col() * layout.pitch() - layout.node() / 2.0F);
	}

	/** Rows grow upward from the root row. */
	private static int nodeY(final Constellation.Node node, final Layout layout) {
		return Math.round(layout.rootTop() - node.row() * layout.pitch());
	}

	/** A node under the cursor: which sub-tree column, which node index. */
	private record Hit(int section, int index) {
	}

	private @Nullable Hit nodeAt(final double mouseX, final double mouseY) {
		for (int section = 0; section < this.shown.size(); section++) {
			Constellation shape = this.shown.get(section).constellation();
			Layout layout = this.layout(section, shape);
			int size = layout.node();

			for (int i = 0; i < shape.nodes().size(); i++) {
				int x = nodeX(shape, shape.nodes().get(i), layout);
				int y = nodeY(shape.nodes().get(i), layout);

				if (mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size) {
					return new Hit(section, i);
				}
			}
		}

		return null;
	}

	@Override
	// STAGE 4 — `MouseButtonEvent` is `>=1.21.11`; below it the callback is the three-argument
	// `mouseClicked(double, double, int)` that has been on `GuiEventListener` since forever. The
	// fork is the header plus the three reads it carries (`x()`, `y()`, `button()`), and it stops
	// there on purpose: the purchase decision and everything after it is one implementation.
	//? if >=1.21.11 {
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}

		Hit hit = this.nodeAt(event.x(), event.y());

		if (event.button() != 0 || hit == null || this.minecraft.player == null) {
	//?} else {
	/*public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}

		Hit hit = this.nodeAt(mouseX, mouseY);

		if (button != 0 || hit == null || this.minecraft.player == null) {
	*///?}
			return false;
		}

		SubTree tree = this.shown.get(hit.section());

		// Client-side check is a courtesy; the server re-runs all of it.
		if (NodePurchases.check(this.minecraft.player, tree, hit.index()) == NodePurchases.Verdict.BUYABLE) {
			Net.INSTANCE.sendToServer(WireId.BUY_NODE, buf -> {
				buf.writeUtf(tree.id());
				buf.writeVarInt(hit.index());
			});
		}

		return true;
	}

	@Override
	// `Screen`'s own draw method is the same extract-vs-immediate move as every other GUI
	// surface: `extractRenderState(GuiGraphicsExtractor, int, int, float)` is
	// `render(GuiGraphics, int, int, float)` below 26.1, super call included. The comment
	// further down about widgets keeps holding — `Screen.render` walks the renderables there
	// too. What does NOT carry across the whole legacy band is the BACKGROUND: on 1.21.1
	// `Screen.render` draws it after us, on 1.20.1 it never draws one. See the neutered
	// `renderBackground` below.
	//? if >=26.1 {
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
	//?} else {
	/*public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float a) {
	*///?}
		// THE BACKGROUND HAS TO BE DOWN BEFORE ANY PANEL PIXEL.
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

		VanillaUi.window(graphics, panelLeft, panelTop, this.panelWidth(), this.panelHeight());

		// The header wears your tier's name: Seeker on the way up, Oracle at
		// the end of the journey. Computed per frame — cheap, always current.
		int tier = this.minecraft.player == null ? 0 : SkillPoints.tier(this.minecraft.player);
		// The header rides a dark chip: bright name color, light body text.
		Component header = Component.translatable("screen.archetypes.tree.title",
				this.archetype.tierName(tier).copy()
						.withStyle(style -> style.withColor(this.archetype.color() & 0xFFFFFF)));
		VanillaUi.chipText(graphics, this.font, header, panelLeft + PAD + 3, panelTop + 8, 0xFFE8E8E8);

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
		//? if >=1.21.11 {
		graphics.blit(RenderPipelines.GUI_TEXTURED, this.archetype.treeBackground(),
				this.canvasLeft() + (canvasWidth - artWidth) / 2,
				this.canvasTop() + (canvasHeight - artHeight) / 2,
				0.0F, 0.0F, artWidth, artHeight, ART_WIDTH, ART_HEIGHT, ART_WIDTH, ART_HEIGHT);
		//?} else {
		/*graphics.blit(this.archetype.treeBackground(),
				this.canvasLeft() + (canvasWidth - artWidth) / 2,
				this.canvasTop() + (canvasHeight - artHeight) / 2,
				artWidth, artHeight, 0.0F, 0.0F, ART_WIDTH, ART_HEIGHT, ART_WIDTH, ART_HEIGHT);
		*///?}

		VanillaUi.insetBorder(graphics, this.canvasLeft(), this.canvasTop(), canvasWidth, canvasHeight);

		List<FormattedCharSequence> tooltip = null;

		for (int section = 0; section < this.shown.size(); section++) {
			SubTree tree = this.shown.get(section);
			Constellation shape = tree.constellation();
			Layout layout = this.layout(section, shape);
			int size = layout.node();
			// Ring, stroke and icon inset all come off the node — see stroke().
			int stroke = stroke(size);

			this.sectionTitle(graphics, tree, section);

			// Connections first, so nodes sit on top of the line ends. Decorative
			// edges draw identically — they exist to finish the silhouette.
			for (List<int[]> edgeSet : List.of(shape.edges(), shape.decorativeEdges())) {
				for (int[] edge : edgeSet) {
					Constellation.Node from = shape.nodes().get(edge[0]);
					Constellation.Node to = shape.nodes().get(edge[1]);
					VanillaUi.line(graphics,
							nodeX(shape, from, layout) + size / 2, nodeY(from, layout) + size / 2,
							nodeX(shape, to, layout) + size / 2, nodeY(to, layout) + size / 2,
							VanillaUi.INSET_BODY, stroke);
				}
			}

			var owned = this.minecraft.player == null
					? java.util.Set.<Integer>of()
					: NodePurchases.owned(this.minecraft.player, tree);

			for (int i = 0; i < shape.nodes().size(); i++) {
				Constellation.Node node = shape.nodes().get(i);
				int x = nodeX(shape, node, layout);
				int y = nodeY(node, layout);
				boolean hovered = mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;

				// Actives wear a blue halo, capstones a purple one — a 1px
				// ring left showing under the inset frame.
				TreeNodes.NodeKind kind = TreeNodes.kind(tree, i);

				if (kind == TreeNodes.NodeKind.ACTIVE) {
					graphics.fill(x - stroke, y - stroke, x + size + stroke, y + size + stroke, 0xFF3B82F6);
				} else if (kind == TreeNodes.NodeKind.CAPSTONE) {
					graphics.fill(x - stroke, y - stroke, x + size + stroke, y + size + stroke, 0xFFA855F7);
				}

				VanillaUi.inset(graphics, x, y, size, size);

				NodePurchases.Verdict verdict = this.minecraft.player == null
						? NodePurchases.Verdict.NOT_CONNECTED
						: NodePurchases.check(this.minecraft.player, tree, i);
				boolean isOwned = owned.contains(i);

				if (isOwned) {
					// Yours: filled with the archetype's colour, icon on top.
					graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, this.archetype.color());
				} else if (hovered && verdict == NodePurchases.Verdict.BUYABLE) {
					graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, VanillaUi.INSET_BODY_HOVERED);
				}

				// The skill's icon, ALWAYS — a node is never too small for one.
				// Sized to the node minus its bevel and centred: at the reference
				// node that is a 16px icon inset by 1, i.e. the native draw, and
				// away from it the pose does the work (VanillaUi.nodeIcon).
				int iconSize = Math.max(3, size - stroke * 2);
				VanillaUi.nodeIcon(graphics, tree, i,
						x + (size - iconSize) / 2, y + (size - iconSize) / 2, iconSize);

				// Unreachable nodes dim, icon included — drawn over it on purpose.
				if (!isOwned && verdict != NodePurchases.Verdict.BUYABLE) {
					graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0x99000000);
				}

				if (hovered) {
					tooltip = this.nodeTooltip(tree, i, verdict);
				}
			}

			if (section < this.shown.size() - 1) {
				VanillaUi.verticalDivider(graphics,
						this.canvasLeft() + this.sectionWidth() * (section + 1) - 1,
						this.canvasTop() + 4, this.canvasBottom() - 4);
			}
		}

		this.progressBars(graphics, mouseX, mouseY);

		// Grey out the switcher direction that matches what's already shown.
		for (int section = 0; section < this.epicUp.length; section++) {
			if (this.epicUp[section] != null) {
				boolean epicShown = this.shown.get(section).isEpic();
				this.epicUp[section].active = !epicShown;
				this.epicDown[section].active = epicShown;
			}
		}

		// Widgets last: Screen.extractRenderState only walks the renderables, so
		// anything drawn after it covers the buttons.
		//? if >=26.1 {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		//?} else {
		/*super.render(graphics, mouseX, mouseY, a);
		*///?}

		// Click-only (user call — no hover preview): the pinned legend sits
		// anchored under its button until clicked again. Node tooltips win
		// if one is up.
		// `setTooltipForNextFrame` is `>=1.21.11` (the deferred, end-of-frame form); below it the
		// call is the immediate `renderTooltip(Font, List<? extends FormattedCharSequence>, int,
		// int)`. Both sites here are already the LAST thing the draw does, so drawing now and
		// drawing at the end of the frame put the same pixels in the same place.
		if (tooltip != null) {
			//? if >=1.21.11 {
			graphics.setTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
			//?} else {
			/*graphics.renderTooltip(this.font, tooltip, mouseX, mouseY);
			*///?}
		} else if (this.legendPinned) {
			//? if >=1.21.11 {
			graphics.setTooltipForNextFrame(this.font, this.legendLines(),
					this.legendButton.getX() - 4, this.legendButton.getY() + 24);
			//?} else {
			/*graphics.renderTooltip(this.font, this.legendLines(),
					this.legendButton.getX() - 4, this.legendButton.getY() + 24);
			*///?}
		}
	}

	// 1.21.1 ONLY — the menu-blur phase trap, measured in the 1.21.1 client jar.
	//
	// There `Screen.render` is `renderBackground(g, mouseX, mouseY, a)` and THEN the
	// renderable walk, and `Screen.renderBackground` calls `renderBlurredBackground`, which
	// is `GameRenderer.processBlurEffect` — a post-process pass over the whole main render
	// target. `GuiGraphics.fill`, `drawString`, `fillGradient` and `renderItem` all flush
	// eagerly on that version, so every panel, label, node icon and tree line this screen
	// drew is already IN the framebuffer when the blur runs, and comes back blurred.
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

	private List<FormattedCharSequence> nodeTooltip(final SubTree tree, final int index,
			final NodePurchases.Verdict verdict) {
		List<FormattedCharSequence> lines = new java.util.ArrayList<>();

		Component name = Component.translatable(TreeNodes.nameKey(tree, index));

		if (TreeNodes.familySize(tree, index) > 1) {
			name = Component.translatable("node.archetypes.ranked", name, TreeNodes.rankOf(tree, index));
		}

		lines.add(name.copy().withStyle(ChatFormatting.WHITE).getVisualOrderText());

		// The actual effect, wrapped — this is what the hover is for.
		lines.addAll(this.font.split(
				Component.translatable(TreeNodes.descriptionKey(tree, index)).withStyle(ChatFormatting.GRAY),
				VanillaUi.TOOLTIP_WIDTH));

		ChatFormatting statusColor = switch (verdict) {
			case OWNED -> ChatFormatting.GOLD;
			case BUYABLE -> ChatFormatting.GREEN;
			default -> ChatFormatting.RED;
		};
		lines.add(Component.translatable(verdict.key()).withStyle(statusColor).getVisualOrderText());

		if (TreeNodes.isMinor(tree, index)) {
			lines.add(Component.translatable("node.archetypes.inert")
					.withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
		}

		return lines;
	}

	/**
	 * Two bars under the buttons: the long road from start tier to peak, and the
	 * short one to the next point.
	 *
	 * <p>Read straight off the attachment, which syncs to its owning client, so
	 * there is no separate packet to keep in step.
	 */
	//? if >=26.1 {
	private void progressBars(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY) {
	//?} else {
	/*private void progressBars(final GuiGraphics graphics, final int mouseX, final int mouseY) {
	*///?}
		if (this.minecraft.player == null) {
			return;
		}

		Player player = this.minecraft.player;
		int left = this.panelLeft() + PAD;
		int width = this.panelWidth() - PAD * 2;
		int top = this.canvasBottom() + FOOTER;

		int level = SkillPoints.level(player);
		int unspent = SkillPoints.available(player);
		int epicUnspent = SkillPoints.epicAvailable(player);
		// Epic points only count as spendable while some epic tree of this
		// archetype still has room — an archetype whose epic trees are all
		// capped banks the rest, and the raw pool alone would never read zero.
		int epicRoom = 0;

		for (SubTree base : this.baseTrees) {
			SubTree epicTree = base.epicCounterpart();

			if (epicTree != null) {
				epicRoom += Math.max(0, SkillPoints.MAX_POINTS_PER_EPIC_SUB_TREE
						- NodePurchases.owned(player, epicTree).size());
			}
		}

		// Any section previewing its epic tree flips the point chip and cap line
		// to the epic pool.
		boolean epicView = this.shown.stream().anyMatch(SubTree::isEpic);

		// The journey over and every spendable point committed — normal AND
		// epic: the bars have nothing left to say, so a line of flavor stands
		// where they were.
		if (level >= SkillPoints.MAX_LEVEL && unspent <= 0 && Math.min(epicUnspent, epicRoom) <= 0) {
			Component mastered = Component.translatable("screen.archetypes.tree.mastered",
					this.archetype.tierName(1));
			VanillaUi.chipText(graphics, this.font, mastered,
					left + (width - this.font.width(mastered)) / 2, top + 14,
					this.archetype.color());
			return;
		}

		// When previewing an epic tree, name its tighter per-tree cap.
		if (epicView) {
			Component cap = Component.translatable("screen.archetypes.tree.epic.cap",
					SkillPoints.MAX_POINTS_PER_EPIC_SUB_TREE);
			//? if >=26.1 {
			graphics.text(this.font, cap, left + (width - this.font.width(cap)) / 2, top,
			//?} else {
			/*graphics.drawString(this.font, cap, left + (width - this.font.width(cap)) / 2, top,
			*///?}
					VanillaUi.LABEL_FAINT, false);
		}

		// Long bar: start tier -> peak tier.
		Component road = Component.translatable("screen.archetypes.tree.bar.archetype",
				this.archetype.tierName(0), this.archetype.tierName(1));
		Component levelText = Component.translatable("screen.archetypes.tree.bar.level", level
				+ "/" + SkillPoints.MAX_LEVEL);
		//? if >=26.1 {
		graphics.text(this.font, road, left, top, VanillaUi.LABEL, false);
		//?} else {
		/*graphics.drawString(this.font, road, left, top, VanillaUi.LABEL, false);
		*///?}
		//? if >=26.1 {
		graphics.text(this.font, levelText, left + width - this.font.width(levelText), top,
		//?} else {
		/*graphics.drawString(this.font, levelText, left + width - this.font.width(levelText), top,
		*///?}
				VanillaUi.LABEL, false);
		VanillaUi.progressBar(graphics, left, top + 10, width, BAR_HEIGHT,
				SkillPoints.archetypeProgress(player), this.archetype.color());

		// Short bar: XP into the current level, over what the NEXT level
		// costs (quadratic curve), with the live advancement rate beside it.
		int advancements = SkillPoints.advancementCount(player);
		float rate = SkillPoints.xpMultiplier(player);
		Component next = Component.literal(SkillPoints.xpIntoLevel(player)
				+ "/" + SkillPoints.costForNextLevel(player) + " XP  (x"
				+ String.format(java.util.Locale.ROOT, "%.2f", rate) + ")");
		//? if >=26.1 {
		graphics.text(this.font, next, left, top + 18, VanillaUi.LABEL_FAINT, false);
		//?} else {
		/*graphics.drawString(this.font, next, left, top + 18, VanillaUi.LABEL_FAINT, false);
		*///?}

		// The rate earns an explanation on hover. Both lines go through
		// font.split at VanillaUi.TOOLTIP_WIDTH, the same width every other
		// tooltip on this screen wraps at — a getVisualOrderText() straight off
		// a Component is ONE line however long it is, which is how this one
		// grew across the whole window and got clipped at the edge.
		if (mouseY >= top + 16 && mouseY < top + 28 && mouseX >= left
				&& mouseX < left + this.font.width(next)) {
			List<FormattedCharSequence> lines = new java.util.ArrayList<>();
			lines.addAll(this.font.split(
					Component.translatable("screen.archetypes.tree.rate.tooltip")
							.withStyle(ChatFormatting.GRAY), VanillaUi.TOOLTIP_WIDTH));
			lines.addAll(this.font.split(
					Component.translatable("screen.archetypes.tree.rate.tooltip.count",
							advancements, String.format(java.util.Locale.ROOT, "%.2f", rate))
							.withStyle(ChatFormatting.WHITE), VanillaUi.TOOLTIP_WIDTH));
			//? if >=1.21.11 {
			graphics.setTooltipForNextFrame(this.font, lines, mouseX, mouseY);
			//?} else {
			/*graphics.renderTooltip(this.font, lines, mouseX, mouseY);
			*///?}
		}

		// The points-remaining chip: epic points while previewing an epic tree,
		// normal points otherwise.
		int chipPoints = epicView ? epicUnspent : unspent;

		if (chipPoints > 0) {
			Component spare = Component.translatable("screen.archetypes.tree.points", chipPoints);
			VanillaUi.chipText(graphics, this.font, spare,
					left + width - this.font.width(spare) - 3, top + 18,
					this.archetype.color());
		}

		VanillaUi.progressBar(graphics, left, top + 28, width, BAR_HEIGHT,
				SkillPoints.levelProgress(player), 0xFF7FCF5F);
	}

	/** The legend's lines: halo colors in their own colors, the element
	 * commitment and the respec brew in plain gray. */
	private List<FormattedCharSequence> legendLines() {
		List<FormattedCharSequence> lines = new java.util.ArrayList<>();
		lines.addAll(this.font.split(Component.translatable("screen.archetypes.tree.legend.actives")
				.withStyle(style -> style.withColor(0xFF3B82F6 & 0xFFFFFF)), VanillaUi.TOOLTIP_WIDTH));
		lines.addAll(this.font.split(Component.translatable("screen.archetypes.tree.legend.capstones")
				.withStyle(style -> style.withColor(0xFFA855F7 & 0xFFFFFF)), VanillaUi.TOOLTIP_WIDTH));

		// The element commitment only concerns Seekers.
		if (this.archetype == Archetype.INTELLECT) {
			lines.addAll(this.font.split(Component.translatable("screen.archetypes.tree.legend.elements")
					.withStyle(ChatFormatting.GRAY), VanillaUi.TOOLTIP_WIDTH));
		}
		lines.addAll(this.font.split(Component.translatable("screen.archetypes.tree.legend.reset")
				.withStyle(ChatFormatting.GRAY), VanillaUi.TOOLTIP_WIDTH));
		return lines;
	}

	/**
	 * The sub-tree's name across the top of its section: bold and 1.5x, in a
	 * washed-out tone so it sits into the backdrop art rather than competing with
	 * the nodes.
	 *
	 * <p>1.5x is a CEILING, not the size. A section is a third of a panel that is a
	 * fraction of the screen, so at a large GUI scale on a small window there is not
	 * 1.5x of room for "Colossus Crusher" — the title then shrinks to what fits
	 * rather than running across the divider into its neighbour. The room it has to
	 * fit is the section minus its padding and minus the switcher column on the
	 * right, doubled because the title is centred and has to clear it symmetrically.
	 */
	//? if >=26.1 {
	private void sectionTitle(final GuiGraphicsExtractor graphics, final SubTree tree, final int section) {
	//?} else {
	/*private void sectionTitle(final GuiGraphics graphics, final SubTree tree, final int section) {
	*///?}
		Component label = tree.displayName().copy().withStyle(ChatFormatting.BOLD);
		int width = Math.max(1, this.font.width(label));
		int room = Math.max(1, this.sectionWidth() - PAD * 2 - SWITCH_W * 2);
		float scale = Math.min(SECTION_TITLE_SCALE, room / (float) width);
		float x = this.sectionCenter(section) - width * scale / 2.0F;
		float y = this.canvasTop() + 6;

		// `GuiGraphics.pose()` is a 2-D `Matrix3x2fStack` from 1.21.11 up and a 3-D `PoseStack`
		// below it, so push/translate/scale/pop all gain a z. The MATH is unchanged and stays
		// outside the fork (conventions §5b): the same `x`, `y` and `scale` go in, with a zero
		// z and a unit z-scale, which is what a 2-D stack does implicitly.
		//? if >=1.21.11 {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		//?} else {
		/*graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0.0F);
		graphics.pose().scale(scale, scale, 1.0F);
		*///?}
		//? if >=26.1 {
		graphics.text(this.font, label, 0, 0, VanillaUi.SECTION_TITLE, false);
		//?} else {
		/*graphics.drawString(this.font, label, 0, 0, VanillaUi.SECTION_TITLE, false);
		*///?}
		//? if >=1.21.11 {
		graphics.pose().popMatrix();
		//?} else {
		/*graphics.pose().popPose();
		*///?}
	}
}
