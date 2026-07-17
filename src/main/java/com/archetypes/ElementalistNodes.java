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
 * What each node of the Elementalist constellation is. The flame's two edges
 * are the two elements: fire up the left (Kindling, Scorch, Ignition,
 * Vaporize, then the Flamethrower-or-Meteorite choice), ice up the right
 * (Chill, Frostbite, Shatter, Permafrost, then Blizzard-or-Glacial-Spike).
 * The base row holds the two starting spells — Fireball left, Ice Blast
 * right, mutually exclusive, each the only door into its element — with
 * Focused Mind between them, and the crown above either capstone pays out in
 * cheaper (Spellweaver) and harder (Arcane Power) casting.
 */
public final class ElementalistNodes {
	public enum Family {
		FIREBALL(() -> Items.FIRE_CHARGE),
		ICE_BLAST(() -> Items.ICE),
		/** +0.5 mana regen a second per rank, whatever your element. */
		FOCUSED_MIND(() -> Items.LAPIS_LAZULI),
		/** Fire spells cost 5 less per rank. */
		KINDLING(() -> Items.FLINT_AND_STEEL),
		/** Fire spells hit a heart harder per rank. */
		SCORCH(() -> Items.BLAZE_POWDER),
		/** Your fire burns 3 seconds longer per rank. */
		IGNITION(() -> Items.CAMPFIRE),
		/** Fire projectiles boil away the water they pass. */
		VAPORIZE(() -> Items.SPONGE),
		/** Ice spells cost 5 less per rank. */
		CHILL(() -> Items.SNOWBALL),
		/** Ice Blast slows harder and longer per rank. */
		FROSTBITE(() -> Items.POWDER_SNOW_BUCKET),
		/** Slowed or freezing targets take +15% per rank from your spells. */
		SHATTER(() -> Items.AMETHYST_SHARD),
		/** Ice projectiles glaze the water they pass into frosted ice. */
		PERMAFROST(() -> Items.PACKED_ICE),
		/** Fire capstones: the crater or the hose. */
		METEORITE(() -> Items.MAGMA_BLOCK),
		FLAMETHROWER(() -> Items.BLAZE_ROD),
		/** Ice capstones: the lance or the storm. */
		GLACIAL_SPIKE(() -> Items.BLUE_ICE),
		BLIZZARD(() -> Items.SNOW_BLOCK),
		/** The crown: cheaper casts, then harder ones. */
		SPELLWEAVER(() -> Items.ENCHANTED_BOOK),
		ARCANE_POWER(() -> Items.NETHER_STAR),
		MINOR((Supplier<Item>) null);

		private final @Nullable Supplier<Item> icon;

		Family(final @Nullable Supplier<Item> icon) {
			this.icon = icon;
		}

		public @Nullable Item icon() {
			return this.icon == null ? null : this.icon.get();
		}

		public String nameKey() {
			return "node.archetypes.elementalist." + this.name().toLowerCase(Locale.ROOT);
		}

		public String descriptionKey() {
			return this.nameKey() + ".desc";
		}
	}

	public record Def(Family family, int rank) {
	}

	private static final Map<Integer, Def> BY_INDEX = build();

	private ElementalistNodes() {
	}

	private static Map<Integer, Def> build() {
		Map<Long, Def> byCell = new HashMap<>();

		// The base: the two starting spells and the mind between them.
		byCell.put(cell(3, 0), new Def(Family.FIREBALL, 1));
		byCell.put(cell(5, 0), new Def(Family.ICE_BLAST, 1));

		// The flame's core: Focused Mind, four ranks rising up the hollow —
		// the element-agnostic spend that lets one path fill all 15 points.
		byCell.put(cell(4, 0), new Def(Family.FOCUSED_MIND, 1));
		byCell.put(cell(4, 1), new Def(Family.FOCUSED_MIND, 2));
		byCell.put(cell(4, 2), new Def(Family.FOCUSED_MIND, 3));
		byCell.put(cell(5, 3), new Def(Family.FOCUSED_MIND, 4));

		// Fire, up the left edge.
		byCell.put(cell(2, 1), new Def(Family.KINDLING, 1));
		byCell.put(cell(1, 2), new Def(Family.KINDLING, 2));
		byCell.put(cell(0, 3), new Def(Family.SCORCH, 1));
		byCell.put(cell(0, 4), new Def(Family.SCORCH, 2));
		byCell.put(cell(1, 5), new Def(Family.IGNITION, 1));
		byCell.put(cell(2, 6), new Def(Family.IGNITION, 2));
		byCell.put(cell(3, 7), new Def(Family.VAPORIZE, 1));

		// Ice, up the right edge.
		byCell.put(cell(6, 1), new Def(Family.CHILL, 1));
		byCell.put(cell(7, 2), new Def(Family.CHILL, 2));
		byCell.put(cell(8, 3), new Def(Family.FROSTBITE, 1));
		byCell.put(cell(8, 4), new Def(Family.FROSTBITE, 2));
		byCell.put(cell(7, 5), new Def(Family.SHATTER, 1));
		byCell.put(cell(7, 6), new Def(Family.SHATTER, 2));
		byCell.put(cell(6, 7), new Def(Family.PERMAFROST, 1));

		// The four capstone tongues, and the crown above — every tongue
		// touches a crown cell, so any choice still finishes the tree.
		byCell.put(cell(3, 8), new Def(Family.FLAMETHROWER, 1));
		byCell.put(cell(4, 8), new Def(Family.METEORITE, 1));
		byCell.put(cell(5, 8), new Def(Family.GLACIAL_SPIKE, 1));
		byCell.put(cell(6, 8), new Def(Family.BLIZZARD, 1));
		byCell.put(cell(4, 9), new Def(Family.ARCANE_POWER, 1));
		byCell.put(cell(5, 9), new Def(Family.SPELLWEAVER, 1));

		Map<Integer, Def> byIndex = new HashMap<>();
		var nodes = Constellations.ELEMENTALIST_FLAME.nodes();

		for (int i = 0; i < nodes.size(); i++) {
			Def def = byCell.get(cell(nodes.get(i).col(), nodes.get(i).row()));

			if (def != null) {
				byIndex.put(i, def);
			}
		}

		if (byIndex.size() != nodes.size()) {
			throw new IllegalStateException("Elementalist flame has " + nodes.size()
					+ " nodes but " + byIndex.size() + " skill mappings — the grid and the map drifted");
		}

		return byIndex;
	}

	private static long cell(final int col, final int row) {
		return ((long) col << 32) | (row & 0xFFFFFFFFL);
	}

	public static Def def(final SubTree tree, final int nodeIndex) {
		return tree == SubTree.ELEMENTALIST
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
