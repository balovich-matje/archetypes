package com.archetypes.client;

import java.util.List;
import java.util.Optional;

import com.archetypes.Archetype;
import com.archetypes.SubTree;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The archetype's skill tree: a vanilla-style window whose canvas holds the
 * three constellation sub-trees, roots at the bottom, growing upward.
 *
 * <p>All nodes are placeholders ("+1% damage", no effect) sharing one diamond
 * layout — the real shapes come with the constellation background art, where
 * each sub-tree's nodes trace its symbol (shield, sword, mace, ...).
 */
public class ArchetypeScreen extends Screen {
	private static final int PAD = 8;
	private static final int MAX_PANEL_WIDTH = 380;
	private static final int MAX_PANEL_HEIGHT = 230;
	private static final int CANVAS_TOP = 22;
	private static final int BUTTON_STRIP = 36;

	private static final int NODE = 18;
	private static final int NODE_DX = 24;
	private static final int NODE_DY = 26;
	/** Placeholder diamond, as {column, row} with row 0 at the bottom. */
	private static final int[][] TEMPLATE = {
			{0, 0}, {0, 1}, {-1, 2}, {1, 2}, {-1, 3}, {1, 3}, {0, 4}
	};
	private static final int[][] EDGES = {
			{0, 1}, {1, 2}, {1, 3}, {2, 4}, {3, 5}, {4, 6}, {5, 6}
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
	protected void init() {
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
				.bounds(this.panelLeft() + this.panelWidth() / 2 - 60,
						this.panelTop() + this.panelHeight() - 28, 120, 20)
				.build());
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	private int panelWidth() {
		return Math.min(this.width - 24, MAX_PANEL_WIDTH);
	}

	private int panelHeight() {
		return Math.min(this.height - 24, MAX_PANEL_HEIGHT);
	}

	private int panelLeft() {
		return (this.width - this.panelWidth()) / 2;
	}

	private int panelTop() {
		return (this.height - this.panelHeight()) / 2;
	}

	/** Top-left corner of node {@code node} of sub-tree column {@code column}. */
	private int nodeX(final int column, final int node) {
		int canvasLeft = this.panelLeft() + PAD;
		int columnWidth = (this.panelWidth() - PAD * 2) / 3;
		int center = canvasLeft + columnWidth * column + columnWidth / 2;
		return center + TEMPLATE[node][0] * NODE_DX - NODE / 2;
	}

	private int nodeY(final int node) {
		int canvasBottom = this.panelTop() + this.panelHeight() - BUTTON_STRIP;
		return canvasBottom - 13 - 4 - NODE - TEMPLATE[node][1] * NODE_DY;
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);

		int panelLeft = this.panelLeft();
		int panelTop = this.panelTop();
		int panelWidth = this.panelWidth();
		int panelHeight = this.panelHeight();

		VanillaUi.window(graphics, panelLeft, panelTop, panelWidth, panelHeight);

		graphics.text(this.font, this.title, panelLeft + PAD, panelTop + 8, VanillaUi.LABEL, false);

		Component preview = Component.translatable("screen.archetypes.tree.preview");
		graphics.text(this.font, preview, panelLeft + panelWidth - PAD - this.font.width(preview),
				panelTop + 8, VanillaUi.LABEL_FAINT, false);

		VanillaUi.inset(graphics, panelLeft + PAD, panelTop + CANVAS_TOP,
				panelWidth - PAD * 2, panelHeight - CANVAS_TOP - BUTTON_STRIP);

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
					this.panelTop() + panelHeight - BUTTON_STRIP - 12, VanillaUi.LABEL, false);
		}

		if (tooltip) {
			graphics.setTooltipForNextFrame(this.font,
					List.of(Component.translatable("node.archetypes.placeholder")),
					Optional.empty(), mouseX, mouseY);
		}
	}
}
