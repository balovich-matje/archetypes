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
 * What each node of the epic Oracle-Priest constellation is (draft
 * oracle-priest). Aura of Radiance is the root and every other node shapes it:
 * Brilliance's two rungs raise both the damage and the healing, the crossbar
 * spreads the aura sideways (Blinding Light saps the undead caught in it,
 * Beacon of Light triples how long it burns, Steadfast roots the caster) and
 * Retribution crowns the shaft with the caster's own war blessing.
 */
public final class OraclePriestNodes {
	public enum Family {
		/** The root: casting Holy Light wreathes the caster in a damaging,
		 * healing aura, and doubles what Holy Light costs. */
		AURA_OF_RADIANCE(() -> Items.GLOWSTONE),
		/** The aura's per-second damage AND healing, 1/2 hearts by rank. */
		BRILLIANCE(() -> Items.SEA_LANTERN),
		/** The aura runs 30 seconds instead of 10. */
		BEACON_OF_LIGHT(() -> Items.BEACON),
		/** The aura lays Weakness II and Slowness II on the undead it burns. */
		BLINDING_LIGHT(() -> Items.FERMENTED_SPIDER_EYE),
		/** Nothing shifts the caster while the aura runs. */
		STEADFAST(() -> Items.NETHERITE_INGOT),
		/** The aura arms the caster: Strength II and Speed II while it runs. */
		RETRIBUTION(() -> Items.GOLDEN_SWORD),
		MINOR((Supplier<Item>) null);

		private final @Nullable Supplier<Item> icon;

		Family(final @Nullable Supplier<Item> icon) {
			this.icon = icon;
		}

		public @Nullable Item icon() {
			return this.icon == null ? null : this.icon.get();
		}

		public String nameKey() {
			return "node.archetypes.oracle_priest." + this.name().toLowerCase(Locale.ROOT);
		}

		public String descriptionKey() {
			return this.nameKey() + ".desc";
		}
	}

	public record Def(Family family, int rank) {
	}

	private static final Map<Integer, Def> BY_INDEX = build();

	private OraclePriestNodes() {
	}

	private static Map<Integer, Def> build() {
		Map<Long, Def> byCell = new HashMap<>();

		// The shaft, bottom-up: the aura, then the two rungs that brighten it.
		byCell.put(cell(1, 0), new Def(Family.AURA_OF_RADIANCE, 1));
		byCell.put(cell(1, 1), new Def(Family.BRILLIANCE, 1));
		byCell.put(cell(1, 2), new Def(Family.BRILLIANCE, 2));

		// The crossbar, left to right.
		byCell.put(cell(0, 3), new Def(Family.BLINDING_LIGHT, 1));
		byCell.put(cell(1, 3), new Def(Family.BEACON_OF_LIGHT, 1));
		byCell.put(cell(2, 3), new Def(Family.STEADFAST, 1));

		// The head.
		byCell.put(cell(1, 4), new Def(Family.RETRIBUTION, 1));

		Map<Integer, Def> byIndex = new HashMap<>();
		var nodes = Constellations.ORACLE_PRIEST.nodes();

		for (int i = 0; i < nodes.size(); i++) {
			Def def = byCell.get(cell(nodes.get(i).col(), nodes.get(i).row()));

			if (def != null) {
				byIndex.put(i, def);
			}
		}

		if (byIndex.size() != nodes.size()) {
			throw new IllegalStateException("Oracle Priest has " + nodes.size()
					+ " nodes but " + byIndex.size() + " skill mappings — the grid and the map drifted");
		}

		return byIndex;
	}

	private static long cell(final int col, final int row) {
		return ((long) col << 32) | (row & 0xFFFFFFFFL);
	}

	public static Def def(final SubTree tree, final int nodeIndex) {
		return tree == SubTree.ORACLE_PRIEST
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
