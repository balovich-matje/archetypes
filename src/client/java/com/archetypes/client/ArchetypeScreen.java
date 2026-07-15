package com.archetypes.client;

import java.util.List;
import java.util.Optional;

import com.archetypes.Archetype;
import com.archetypes.SubTree;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The archetype's skill tree: a window filling the whole screen (the
 * Pufferfish's Skills footprint), whose canvas holds the three constellation
 * sub-trees, roots at the bottom, growing upward. Esc closes, back to the
 * inventory — no button, same as the advancements screen.
 *
 * <p>All 60 nodes are placeholders ("+1% damage", no effect) sharing one
 * 20-node lattice — the real shapes come with the constellation background
 * art, where each sub-tree's nodes trace its symbol (shield, sword, mace, ...).
 */
public class ArchetypeScreen extends Screen {
	private static final int MARGIN = 8;
	private static final int PAD = 8;
	private static final int HEADER = 22;

	private static final int NODE = 18;
	private static final int ROWS = 7;
	/** Placeholder lattice, as {column, row} with row 0 at the bottom. */
	private static final int[][] TEMPLATE = {
			{0, 0},
			{-1, 1}, {1, 1},
			{-2, 2}, {0, 2}, {2, 2},
			{-2, 3}, {-1, 3}, {1, 3}, {2, 3},
			{-2, 4}, {-1, 4}, {1, 4}, {2, 4},
			{-2, 5}, {0, 5}, {2, 5},
			{-2, 6}, {0, 6}, {2, 6}
	};
	private static final int[][] EDGES = {
			{0, 1}, {0, 2},
			{1, 3}, {1, 4}, {2, 4}, {2, 5},
			{3, 6}, {4, 7}, {4, 8}, {5, 9},
			{6, 10}, {7, 11}, {8, 12}, {9, 13},
			{10, 14}, {11, 15}, {12, 15}, {13, 16},
			{14, 17}, {15, 18}, {16, 19}
	};

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
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	private int canvasLeft() {
		return MARGIN + PAD;
	}

	private int canvasTop() {
		return MARGIN + HEADER;
	}

	private int canvasWidth() {
		return this.width - (MARGIN + PAD) * 2;
	}

	private int canvasBottom() {
		return this.height - MARGIN - PAD;
	}

	/** Horizontal node spacing: spread with the window, within vanilla-ish bounds. */
	private int nodeDx() {
		return Mth.clamp((this.canvasWidth() / 3 - NODE - 8) / 4, 18, 30);
	}

	/** Vertical node spacing: fill the space above the root row. */
	private int nodeDy() {
		int rootTop = this.canvasBottom() - 17 - NODE;
		return Mth.clamp((rootTop - this.canvasTop() - 6) / (ROWS - 1), 18, 30);
	}

	/** Top-left corner of node {@code node} of sub-tree column {@code column}. */
	private int nodeX(final int column, final int node) {
		int columnWidth = this.canvasWidth() / 3;
		int center = this.canvasLeft() + columnWidth * column + columnWidth / 2;
		return center + TEMPLATE[node][0] * this.nodeDx() - NODE / 2;
	}

	private int nodeY(final int node) {
		return this.canvasBottom() - 17 - NODE - TEMPLATE[node][1] * this.nodeDy();
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);

		VanillaUi.window(graphics, MARGIN, MARGIN, this.width - MARGIN * 2, this.height - MARGIN * 2);

		graphics.text(this.font, this.title, MARGIN + PAD, MARGIN + 8, VanillaUi.LABEL, false);

		Component preview = Component.translatable("screen.archetypes.tree.preview");
		graphics.text(this.font, preview, this.width - MARGIN - PAD - this.font.width(preview),
				MARGIN + 8, VanillaUi.LABEL_FAINT, false);

		VanillaUi.inset(graphics, this.canvasLeft(), this.canvasTop(),
				this.canvasWidth(), this.canvasBottom() - this.canvasTop());

		boolean tooltip = false;

		for (int column = 0; column < this.subTrees.size(); column++) {
			SubTree tree = this.subTrees.get(column);

			// Connections first, so the nodes sit on top of the line ends.
			for (int[] edge : EDGES) {
				VanillaUi.line(graphics,
						this.nodeX(column, edge[0]) + NODE / 2, this.nodeY(edge[0]) + NODE / 2,
						this.nodeX(column, edge[1]) + NODE / 2, this.nodeY(edge[1]) + NODE / 2,
						VanillaUi.INSET_DARK);
			}

			for (int node = 0; node < TEMPLATE.length; node++) {
				int x = this.nodeX(column, node);
				int y = this.nodeY(node);
				boolean hovered = mouseX >= x && mouseX < x + NODE && mouseY >= y && mouseY < y + NODE;

				VanillaUi.slot(graphics, x, y);

				if (hovered) {
					graphics.fill(x + 1, y + 1, x + NODE - 1, y + NODE - 1, VanillaUi.INSET_BODY_HOVERED);
					tooltip = true;
				}

				// The root carries the sub-tree's symbol; the rest stay empty
				// until real nodes exist.
				if (node == 0) {
					graphics.fakeItem(new ItemStack(tree.icon()), x + 1, y + 1);
				}
			}

			Component label = tree.displayName();
			int labelCenter = this.nodeX(column, 0) + NODE / 2;
			graphics.text(this.font, label, labelCenter - this.font.width(label) / 2,
					this.canvasBottom() - 12, VanillaUi.LABEL, false);
		}

		if (tooltip) {
			graphics.setTooltipForNextFrame(this.font,
					List.of(Component.translatable("node.archetypes.placeholder")),
					Optional.empty(), mouseX, mouseY);
		}
	}
}
