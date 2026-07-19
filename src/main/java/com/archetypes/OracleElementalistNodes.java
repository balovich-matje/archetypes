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
 * What each node of the epic Oracle-Elementalist constellation is (draft
 * oracle-elementalist). Lightning Strike is the root; three columns fork off
 * it — Chain Reaction up the left (the bolt jumps to more targets), Recurrence
 * up the right (the bolt strikes the same target again), and the Oracle's mana
 * line up the centre (Wisdom's max mana, then Focus's regen) — each capped by a
 * payoff: Tempest turns the strike into a mana-per-target AOE, Overcharge
 * doubles its damage.
 */
public final class OracleElementalistNodes {
	public enum Family {
		/** The epic active: a targeted bolt for massive single-target damage. */
		LIGHTNING_STRIKE(() -> Items.END_ROD),
		/** The strike arcs to +1/2/3 nearby hostiles. */
		CHAIN(() -> Items.IRON_CHAIN),
		/** The strike lands again on the same target, 1/2/3 extra times. */
		RECURRENCE(() -> Items.COPPER_INGOT),
		/** +50/100% max mana. */
		ORACLE_WISDOM(() -> Items.LAPIS_BLOCK),
		/** Regenerate 2.5/5% of max mana a second. */
		ORACLE_FOCUS(() -> Items.LAPIS_LAZULI),
		/** Capstone-worthy: the strike becomes an AOE that spends mana per
		 * extra target caught. */
		TEMPEST(() -> Items.TRIDENT),
		/** +100% Lightning Strike damage. */
		OVERCHARGE(() -> Items.REDSTONE_BLOCK),
		MINOR((Supplier<Item>) null);

		private final @Nullable Supplier<Item> icon;

		Family(final @Nullable Supplier<Item> icon) {
			this.icon = icon;
		}

		public @Nullable Item icon() {
			return this.icon == null ? null : this.icon.get();
		}

		public String nameKey() {
			return "node.archetypes.oracle_elementalist." + this.name().toLowerCase(Locale.ROOT);
		}

		public String descriptionKey() {
			return this.nameKey() + ".desc";
		}
	}

	public record Def(Family family, int rank) {
	}

	private static final Map<Integer, Def> BY_INDEX = build();

	private OracleElementalistNodes() {
	}

	private static Map<Integer, Def> build() {
		Map<Long, Def> byCell = new HashMap<>();

		// The root, a row below the columns.
		byCell.put(cell(2, 0), new Def(Family.LIGHTNING_STRIKE, 1));

		// Left column: the chain-reaction ladder, capped by Tempest.
		byCell.put(cell(0, 2), new Def(Family.CHAIN, 1));
		byCell.put(cell(0, 3), new Def(Family.CHAIN, 2));
		byCell.put(cell(0, 4), new Def(Family.CHAIN, 3));
		byCell.put(cell(0, 5), new Def(Family.TEMPEST, 1));

		// Centre column: the Oracle's mana line.
		byCell.put(cell(2, 2), new Def(Family.ORACLE_WISDOM, 1));
		byCell.put(cell(2, 3), new Def(Family.ORACLE_WISDOM, 2));
		byCell.put(cell(2, 4), new Def(Family.ORACLE_FOCUS, 1));
		byCell.put(cell(2, 5), new Def(Family.ORACLE_FOCUS, 2));

		// Right column: the recurrence ladder, capped by Overcharge.
		byCell.put(cell(4, 2), new Def(Family.RECURRENCE, 1));
		byCell.put(cell(4, 3), new Def(Family.RECURRENCE, 2));
		byCell.put(cell(4, 4), new Def(Family.RECURRENCE, 3));
		byCell.put(cell(4, 5), new Def(Family.OVERCHARGE, 1));

		Map<Integer, Def> byIndex = new HashMap<>();
		var nodes = Constellations.ORACLE_ELEMENTALIST.nodes();

		for (int i = 0; i < nodes.size(); i++) {
			Def def = byCell.get(cell(nodes.get(i).col(), nodes.get(i).row()));

			if (def != null) {
				byIndex.put(i, def);
			}
		}

		if (byIndex.size() != nodes.size()) {
			throw new IllegalStateException("Oracle Elementalist has " + nodes.size()
					+ " nodes but " + byIndex.size() + " skill mappings — the grid and the map drifted");
		}

		return byIndex;
	}

	private static long cell(final int col, final int row) {
		return ((long) col << 32) | (row & 0xFFFFFFFFL);
	}

	public static Def def(final SubTree tree, final int nodeIndex) {
		return tree == SubTree.ORACLE_ELEMENTALIST
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
}
