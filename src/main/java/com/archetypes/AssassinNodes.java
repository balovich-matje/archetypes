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
 * What each node of the Assassin constellation is. The dagger reads bottom to
 * top: the pommel and grip carry the shared body-work (Lightfoot, Sidestep),
 * the centre line up the blade improves the active itself (Adrenaline Rush,
 * Opportunist), and the two edges are the two ways to kill with a knife —
 * the left edge raw speed-and-steel (Razor Edge, Frenzy, Expose, up to
 * Shadow Flurry), the right edge what's ON the steel (Venom, Blight, Flense,
 * up to Momentum) — with Deathblow at the point, improving either capstone.
 */
public final class AssassinNodes {
	public enum Family {
		SHADOW_STEP(() -> Items.ENDER_PEARL),
		/** +10% move speed per rank while a dagger is in hand. */
		LIGHTFOOT(() -> Items.LEATHER_BOOTS),
		/** 7% per rank to simply not be where a melee blow lands. */
		SIDESTEP(() -> Items.FEATHER),
		/** Shadow Step leaves you Speed II for a moment. */
		ADRENALINE_RUSH(() -> Items.SUGAR),
		/** Shadow Step returns three seconds sooner. */
		OPPORTUNIST(() -> Items.CLOCK),
		/** +8% dagger damage per rank. */
		RAZOR_EDGE(() -> ModItems.IRON_DAGGER),
		/** +10% attack speed per rank while a dagger is in hand. */
		FRENZY(() -> Items.REDSTONE),
		/** +15% dagger damage against targets below half health. */
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
		/** The point: Shadow Step's strikes land half again harder. */
		DEATHBLOW(() -> Items.NETHER_STAR),
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
		byCell.put(cell(3, 0), new Def(Family.SHADOW_STEP, 1));
		byCell.put(cell(2, 0), new Def(Family.LIGHTFOOT, 1));
		byCell.put(cell(4, 0), new Def(Family.LIGHTFOOT, 2));

		// The grip: the body learns to not be hit.
		byCell.put(cell(3, 1), new Def(Family.SIDESTEP, 1));
		byCell.put(cell(3, 2), new Def(Family.SIDESTEP, 2));
		byCell.put(cell(3, 3), new Def(Family.SIDESTEP, 3));

		// The centre line through guard and blade base: the active's own
		// improvements, reachable from either edge.
		byCell.put(cell(3, 4), new Def(Family.ADRENALINE_RUSH, 1));
		byCell.put(cell(3, 5), new Def(Family.OPPORTUNIST, 1));

		// Left edge: raw speed and steel, up to the flurry.
		byCell.put(cell(1, 4), new Def(Family.RAZOR_EDGE, 1));
		byCell.put(cell(2, 4), new Def(Family.RAZOR_EDGE, 2));
		byCell.put(cell(2, 5), new Def(Family.RAZOR_EDGE, 3));
		byCell.put(cell(2, 6), new Def(Family.FRENZY, 1));
		byCell.put(cell(2, 7), new Def(Family.FRENZY, 2));
		byCell.put(cell(2, 8), new Def(Family.EXPOSE, 1));
		byCell.put(cell(2, 9), new Def(Family.SHADOW_FLURRY, 1));

		// Right edge: what's on the steel, up to the spree.
		byCell.put(cell(5, 4), new Def(Family.VENOM, 1));
		byCell.put(cell(4, 4), new Def(Family.VENOM, 2));
		byCell.put(cell(4, 5), new Def(Family.BLIGHT, 1));
		byCell.put(cell(4, 6), new Def(Family.BLIGHT, 2));
		byCell.put(cell(4, 7), new Def(Family.FLENSE, 1));
		byCell.put(cell(4, 8), new Def(Family.FLENSE, 2));
		byCell.put(cell(4, 9), new Def(Family.MOMENTUM, 1));

		// The point.
		byCell.put(cell(3, 10), new Def(Family.DEATHBLOW, 1));

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
