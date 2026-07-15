package com.archetypes;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * What each node of the Protector constellation actually is.
 *
 * <p>Skills are laid onto the shield shape by grid coordinate: the bottom tip
 * is Shield Bash, the left rim is the utility path, the right rim the damage
 * path, the centre column is Shield Rush with the capstone cross above it. The
 * shape has more nodes than the design has skills — the surplus stays as
 * {@link Family#MINOR} placeholders, purchasable but inert, until the node
 * list settles.
 *
 * <p>Multi-rank skills are chains of nodes; a family's rank is simply how many
 * of its nodes are owned, and the chain's adjacency enforces buy order.
 */
public final class ProtectorNodes {
	public enum Family {
		BASH, SLAM, COOLDOWN, KNOCKBACK, WIDE, UNBREAKING, SPIKES, RUSH, REFLECT,
		OMNI_BLOCK, GROUND_SLAM, MINOR;

		public String nameKey() {
			return "node.archetypes.protector." + this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public record Def(Family family, int rank) {
	}

	private static final Def MINOR_DEF = new Def(Family.MINOR, 1);
	/** Node index in the constellation -> definition. */
	private static final Map<Integer, Def> BY_INDEX = build();

	private ProtectorNodes() {
	}

	private static Map<Integer, Def> build() {
		// (col, row) with row 0 at the bottom, exactly as Constellation parses.
		Map<Long, Def> byCell = new HashMap<>();
		byCell.put(cell(4, 0), new Def(Family.BASH, 1));

		// Left rim, bottom-up: the utility path.
		byCell.put(cell(2, 2), new Def(Family.COOLDOWN, 1));
		byCell.put(cell(1, 3), new Def(Family.COOLDOWN, 2));
		byCell.put(cell(1, 4), new Def(Family.COOLDOWN, 3));
		byCell.put(cell(0, 5), new Def(Family.KNOCKBACK, 1));
		byCell.put(cell(0, 6), new Def(Family.KNOCKBACK, 2));
		byCell.put(cell(0, 7), new Def(Family.KNOCKBACK, 3));
		byCell.put(cell(0, 8), new Def(Family.UNBREAKING, 1));
		byCell.put(cell(0, 9), new Def(Family.REFLECT, 1));

		// Right rim, bottom-up: the damage path.
		byCell.put(cell(6, 2), new Def(Family.SLAM, 1));
		byCell.put(cell(7, 3), new Def(Family.SLAM, 2));
		byCell.put(cell(7, 4), new Def(Family.SLAM, 3));
		byCell.put(cell(8, 5), new Def(Family.SPIKES, 1));
		byCell.put(cell(8, 6), new Def(Family.SPIKES, 2));
		byCell.put(cell(8, 7), new Def(Family.SPIKES, 3));
		byCell.put(cell(8, 8), new Def(Family.WIDE, 1));
		byCell.put(cell(8, 9), new Def(Family.WIDE, 2));

		// Centre column: Shield Rush, then the capstone cross — Omni-block left,
		// Ground Slam right, mutually exclusive (enforced at purchase).
		byCell.put(cell(4, 2), new Def(Family.RUSH, 1));
		byCell.put(cell(4, 3), new Def(Family.RUSH, 2));
		byCell.put(cell(4, 4), new Def(Family.RUSH, 3));
		byCell.put(cell(4, 5), new Def(Family.RUSH, 4));
		byCell.put(cell(3, 7), new Def(Family.OMNI_BLOCK, 1));
		byCell.put(cell(5, 7), new Def(Family.GROUND_SLAM, 1));

		Map<Integer, Def> byIndex = new HashMap<>();
		var nodes = Constellations.PROTECTOR_SHIELD.nodes();

		for (int i = 0; i < nodes.size(); i++) {
			Def def = byCell.get(cell(nodes.get(i).col(), nodes.get(i).row()));

			if (def != null) {
				byIndex.put(i, def);
			}
		}

		return byIndex;
	}

	private static long cell(final int col, final int row) {
		return ((long) col << 32) | (row & 0xFFFFFFFFL);
	}

	/** Only the Protector tree has real definitions so far. */
	public static Def def(final SubTree tree, final int nodeIndex) {
		return tree == SubTree.PROTECTOR ? BY_INDEX.getOrDefault(nodeIndex, MINOR_DEF) : MINOR_DEF;
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
