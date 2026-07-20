package com.archetypes;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * What each node of the epic Nemesis-Marksman constellation is (draft
 * nemesis-marksman). Deadeye at the foot is the tree's one ability key (slot 4
 * for a Cutpurse) and every other node reads inside its window: the left column
 * is how you move while it holds, the right column is how the arrow lands, and
 * the crown row is what the window itself is worth.
 */
public final class NemesisMarksmanNodes {
	public enum Family {
		/** The epic active: the fifteen-second shooting window, slot 4. */
		DEADEYE(() -> Items.SPYGLASS),
		/** Deadeye's slow becomes speed. */
		FLEET(() -> Items.FEATHER),
		/** Two ranks: damage scaling with the distance flown. */
		LONG_SHOT(() -> Items.SPECTRAL_ARROW),
		/** Acrobatics goes further, works airborne and comes back sooner. */
		VAULT(() -> Items.RABBIT_FOOT),
		/** No fall damage and Slow Falling; hits refund Acrobatics. */
		ON_THE_WING(() -> Items.PHANTOM_MEMBRANE),
		/** Armour ignored, and two creatures pierced. */
		PUNCH_THROUGH(() -> Items.ARROW),
		/** Projectiles pass through you while the window holds. */
		EVASION(() -> Items.GLASS),
		/** The window lasts longer. */
		LONG_WATCH(() -> Items.CLOCK),
		/** Standing still doubles the arrow and plants you. */
		SIEGE(() -> Items.TARGET),
		MINOR((Supplier<Item>) null);

		private final @Nullable Supplier<Item> icon;

		Family(final @Nullable Supplier<Item> icon) {
			this.icon = icon;
		}

		public @Nullable Item icon() {
			return this.icon == null ? null : this.icon.get();
		}

		public String nameKey() {
			return "node.archetypes.nemesis_marksman." + this.name().toLowerCase(Locale.ROOT);
		}

		public String descriptionKey() {
			return this.nameKey() + ".desc";
		}
	}

	public record Def(Family family, int rank) {
	}

	private static final Map<Integer, Def> BY_INDEX = build();

	private NemesisMarksmanNodes() {
	}

	private static Map<Integer, Def> build() {
		Map<Long, Def> byCell = new HashMap<>();

		byCell.put(cell(2, 0), new Def(Family.DEADEYE, 1));

		// Left column: how you move, bottom-up.
		byCell.put(cell(1, 1), new Def(Family.FLEET, 1));
		byCell.put(cell(0, 3), new Def(Family.VAULT, 1));
		byCell.put(cell(0, 4), new Def(Family.ON_THE_WING, 1));
		byCell.put(cell(0, 5), new Def(Family.EVASION, 1));

		// Right column: how the arrow lands, bottom-up.
		byCell.put(cell(3, 1), new Def(Family.LONG_SHOT, 1));
		byCell.put(cell(4, 3), new Def(Family.LONG_SHOT, 2));
		byCell.put(cell(4, 4), new Def(Family.PUNCH_THROUGH, 1));
		byCell.put(cell(4, 5), new Def(Family.SIEGE, 1));

		// The crown's centre, hung between both columns on explicit edges.
		byCell.put(cell(2, 5), new Def(Family.LONG_WATCH, 1));

		Map<Integer, Def> byIndex = new HashMap<>();
		var nodes = Constellations.NEMESIS_MARKSMAN.nodes();

		for (int i = 0; i < nodes.size(); i++) {
			Def def = byCell.get(cell(nodes.get(i).col(), nodes.get(i).row()));

			if (def != null) {
				byIndex.put(i, def);
			}
		}

		if (byIndex.size() != nodes.size()) {
			throw new IllegalStateException("Nemesis Marksman has " + nodes.size()
					+ " nodes but " + byIndex.size() + " skill mappings — the grid and the map drifted");
		}

		return byIndex;
	}

	private static long cell(final int col, final int row) {
		return ((long) col << 32) | (row & 0xFFFFFFFFL);
	}

	public static Def def(final SubTree tree, final int nodeIndex) {
		return tree == SubTree.NEMESIS_MARKSMAN
				? BY_INDEX.getOrDefault(nodeIndex, new Def(Family.MINOR, 1))
				: new Def(Family.MINOR, 1);
	}

	/** A family's earned rank: how many of its nodes are owned. */
	public static int rank(final SubTree tree, final Set<Integer> owned, final Family family) {
		int count = 0;

		for (int index : owned) {
			if (def(tree, index).family() == family) {
				count++;
			}
		}

		return count;
	}

	/** The owning player's rank in a family — the form every gameplay hook wants. */
	public static int rank(final net.minecraft.world.entity.player.Player player, final Family family) {
		return rank(SubTree.NEMESIS_MARKSMAN,
				NodePurchases.owned(player, SubTree.NEMESIS_MARKSMAN), family);
	}
}
