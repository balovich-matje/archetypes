package com.archetypes.client;

import java.util.List;
import java.util.Optional;

import com.archetypes.Archetype;
import com.archetypes.Constellation;
import com.archetypes.ResetArchetypePayload;
import com.archetypes.SubTree;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The archetype's skill tree: a full-screen vanilla window whose canvas is
 * split into three sections by thin dividers, one per sub-tree. Each section
 * holds a constellation — the sub-tree's symbol drawn in nodes (shield, sword,
 * mace, ...), rooted at the bottom and growing upward.
 *
 * <p>Esc closes back to the inventory, as on the advancements screen.
 *
 * <p>Every node is a placeholder ("+1% damage", no effect); the shapes are what
 * is being judged. Class-fantasy background art comes later and is deliberately
 * unrelated to these layouts.
 */
public class ArchetypeScreen extends Screen {
	private static final int MARGIN = 8;
	private static final int PAD = 8;
	private static final int HEADER = 22;
	/** Strip below the canvas holding the creative reset button. Always reserved,
	 * so the tree lays out identically in both game modes. */
	private static final int FOOTER = 24;
	/** Room under each constellation for its name. */
	private static final int LABEL_STRIP = 14;

	private static final int NODE = 18;
	private static final int MIN_SPACING = NODE + 2;
	private static final int MAX_SPACING = 30;

	private final @Nullable Screen parent;
	private final List<SubTree> subTrees;

	public ArchetypeScreen(final @Nullable Screen parent, final Archetype archetype) {
		super(Component.translatable("screen.archetypes.tree.title",
				archetype.tierName(0).copy().withStyle(style -> style.withColor(archetype.color() & 0xFFFFFF))));
		this.parent = parent;
		this.subTrees = SubTree.of(archetype);
	}

	@Override
	protected void init() {
		// Creative-only testing affordance: undo the "permanent" choice. The
		// server re-checks game mode; this button just hides the option.
		if (this.minecraft.player == null || !this.minecraft.player.isCreative()) {
			return;
		}

		Component label = Component.translatable("screen.archetypes.tree.reset");
		int width = this.font.width(label) + 16;

		this.addRenderableWidget(Button.builder(label, button -> {
					ClientPlayNetworking.send(new ResetArchetypePayload());
					this.minecraft.gui.setScreen(new ArchetypePickerScreen(this.parent));
				})
				.bounds(this.width - MARGIN - PAD - width, this.canvasBottom() + 4, width, 20)
				.tooltip(Tooltip.create(Component.translatable("screen.archetypes.tree.reset.tooltip")))
				.build());
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
		return this.height - MARGIN - PAD - FOOTER;
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
		int usableHeight = this.canvasBottom() - this.canvasTop() - LABEL_STRIP - PAD - NODE;
		int byWidth = shape.width() > 1 ? usableWidth / (shape.width() - 1) : MAX_SPACING;
		int byHeight = shape.height() > 1 ? usableHeight / (shape.height() - 1) : MAX_SPACING;
		return Mth.clamp(Math.min(byWidth, byHeight), MIN_SPACING, MAX_SPACING);
	}

	/** Top-left of a node, given its section and the spacing for that shape. */
	private int nodeX(final int section, final Constellation shape, final Constellation.Node node,
			final int spacing) {
		int sectionCenter = this.canvasLeft() + this.sectionWidth() * section + this.sectionWidth() / 2;
		int shapeWidth = (shape.width() - 1) * spacing;
		return sectionCenter - shapeWidth / 2 + node.col() * spacing - NODE / 2;
	}

	private int nodeY(final Constellation shape, final Constellation.Node node, final int spacing) {
		// Roots sit just above the label strip; row grows upward from there.
		int rootTop = this.canvasBottom() - LABEL_STRIP - NODE;
		return rootTop - node.row() * spacing;
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

		for (int section = 0; section < this.subTrees.size(); section++) {
			SubTree tree = this.subTrees.get(section);
			Constellation shape = tree.constellation();
			int spacing = this.spacing(shape);

			// Connections first, so nodes sit on top of the line ends.
			for (int[] edge : shape.edges()) {
				Constellation.Node from = shape.nodes().get(edge[0]);
				Constellation.Node to = shape.nodes().get(edge[1]);
				VanillaUi.line(graphics,
						this.nodeX(section, shape, from, spacing) + NODE / 2,
						this.nodeY(shape, from, spacing) + NODE / 2,
						this.nodeX(section, shape, to, spacing) + NODE / 2,
						this.nodeY(shape, to, spacing) + NODE / 2,
						VanillaUi.INSET_DARK);
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

			// Name plus its symbol, centered under the constellation.
			Component label = tree.displayName();
			int labelWidth = this.font.width(label);
			int sectionCenter = this.canvasLeft() + this.sectionWidth() * section + this.sectionWidth() / 2;
			int labelY = this.canvasBottom() - 11;
			graphics.fakeItem(new ItemStack(tree.icon()), sectionCenter - labelWidth / 2 - 20, labelY - 4);
			graphics.text(this.font, label, sectionCenter - labelWidth / 2, labelY, VanillaUi.LABEL, false);

			if (section < this.subTrees.size() - 1) {
				VanillaUi.verticalDivider(graphics,
						this.canvasLeft() + this.sectionWidth() * (section + 1) - 1,
						this.canvasTop() + 4, this.canvasBottom() - 4);
			}
		}

		if (tooltip) {
			graphics.setTooltipForNextFrame(this.font,
					List.of(Component.translatable("node.archetypes.placeholder")),
					Optional.empty(), mouseX, mouseY);
		}
	}
}
