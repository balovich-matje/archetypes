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
 * What each node of the Assassin constellation is (user sketch,
 * assassin-rebalance-20260717). The dagger reads bottom to top: pommel and
 * grip carry the shared body-work (Lightfoot, Sidestep), the five-wide
 * guard holds the per-hit steel (Razor Edge, Flense, the new Crippling
 * Poison), then the edges split — raw steel left (Razor Edge's last rank,
 * Expose, up to Shadow Flurry), coatings right (Venom, Blight, up to
 * Momentum) — with Twin Fangs at the point, nudging the fantasy toward a
 * dagger in each hand.
 */
public final class AssassinNodes {
	public enum Family {
		SHADOW_STEP(() -> Items.ENDER_PEARL),
		/** +10% move speed per rank while a dagger is in hand. */
		LIGHTFOOT(() -> Items.LEATHER_BOOTS),
		/** 7% per rank to simply not be where a melee blow lands. */
		SIDESTEP(() -> Items.FEATHER),
		/** Dagger hits slow, I then II — the poison that hobbles. */
		CRIPPLING_POISON(() -> Items.FERMENTED_SPIDER_EYE),
		/** +8% dagger damage per rank. */
		RAZOR_EDGE(() -> ModItems.IRON_DAGGER),
		/** +10% per rank against targets below half health. */
		EXPOSE(() -> Items.TARGET),
		/** Dagger hits poison, I then II. */
		VENOM(() -> Items.SPIDER_EYE),
		/** Dagger hits wither, I then II. */
		BLIGHT(() -> Items.WITHER_ROSE),
		/** Dagger damage ignores half, then all, of armor. */
		FLENSE(() -> Items.SHEARS),
		/** Capstones: the flurry and the spree. */
		SHADOW_FLURRY(() -> ModItems.NETHERITE_DAGGER),
		MOMENTUM(() -> Items.WITHER_SKELETON_SKULL),
		/** The point: the off-hand dagger joins Shadow Step's strike. */
		TWIN_FANGS(() -> ModItems.DIAMOND_DAGGER),
		MINOR((Supplier<Item>) null);

		private final @Nullable Supplier<Item> icon;

		Family(final @Nullable Supplier<Item> icon) {
			this.icon = icon;
		}

		public @Nullable Item icon() {
			return this.icon == null ? null : this.icon.get();
		}

		public String nameKey() {
			return "node.archetypes.assassin." + this.name().toLowerCase(Locale.ROOT);
		}

		public String descriptionKey() {
			return this.nameKey() + ".desc";
		}
	}

	public record Def(Family family, int rank) {
	}

	private static final Map<Integer, Def> BY_INDEX = build();

	private AssassinNodes() {
	}

	private static Map<Integer, Def> build() {
		Map<Long, Def> byCell = new HashMap<>();

		// The pommel: the active in the centre, footwork either side.
		byCell.put(cell(2, 0), new Def(Family.SHADOW_STEP, 1));
		byCell.put(cell(1, 0), new Def(Family.LIGHTFOOT, 1));
		byCell.put(cell(3, 0), new Def(Family.LIGHTFOOT, 2));

		// The grip: the body learns to not be hit.
		byCell.put(cell(2, 1), new Def(Family.SIDESTEP, 1));
		byCell.put(cell(2, 2), new Def(Family.SIDESTEP, 2));
		byCell.put(cell(2, 3), new Def(Family.SIDESTEP, 3));

		// The five-wide guard: the per-hit steel, Crippling Poison at the
		// right quillons.
		byCell.put(cell(0, 4), new Def(Family.RAZOR_EDGE, 2));
		byCell.put(cell(1, 4), new Def(Family.RAZOR_EDGE, 1));
		byCell.put(cell(2, 4), new Def(Family.FLENSE, 1));
		byCell.put(cell(3, 4), new Def(Family.CRIPPLING_POISON, 1));
		byCell.put(cell(4, 4), new Def(Family.CRIPPLING_POISON, 2));

		// The blade base row, then the edges: raw steel up the left to the
		// flurry, coatings up the right to the spree.
		byCell.put(cell(1, 5), new Def(Family.RAZOR_EDGE, 3));
		byCell.put(cell(2, 5), new Def(Family.FLENSE, 2));
		byCell.put(cell(3, 5), new Def(Family.VENOM, 1));
		byCell.put(cell(1, 6), new Def(Family.EXPOSE, 1));
		byCell.put(cell(3, 6), new Def(Family.VENOM, 2));
		byCell.put(cell(1, 7), new Def(Family.EXPOSE, 2));
		byCell.put(cell(3, 7), new Def(Family.BLIGHT, 1));
		byCell.put(cell(1, 8), new Def(Family.EXPOSE, 3));
		byCell.put(cell(3, 8), new Def(Family.BLIGHT, 2));
		byCell.put(cell(1, 9), new Def(Family.SHADOW_FLURRY, 1));
		byCell.put(cell(3, 9), new Def(Family.MOMENTUM, 1));

		// The point.
		byCell.put(cell(2, 10), new Def(Family.TWIN_FANGS, 1));

		Map<Integer, Def> byIndex = new HashMap<>();
		var nodes = Constellations.ASSASSIN_DAGGER.nodes();

		for (int i = 0; i < nodes.size(); i++) {
			Def def = byCell.get(cell(nodes.get(i).col(), nodes.get(i).row()));

			if (def != null) {
				byIndex.put(i, def);
			}
		}

		if (byIndex.size() != nodes.size()) {
			throw new IllegalStateException("Assassin dagger has " + nodes.size()
					+ " nodes but " + byIndex.size() + " skill mappings — the grid and the map drifted");
		}

		return byIndex;
	}

	private static long cell(final int col, final int row) {
		return ((long) col << 32) | (row & 0xFFFFFFFFL);
	}

	public static Def def(final SubTree tree, final int nodeIndex) {
		return tree == SubTree.ASSASSIN
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
