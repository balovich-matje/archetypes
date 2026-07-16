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
 * What each node of the Marksman constellation is. The constellation IS a
 * resting bow with a nocked arrow: True Shot at the bottom tip, the string
 * (right, straight) carrying the crossbow branch — Piercing Tips, Pinning,
 * Rapid Reload — up to Snap Shot, the stave (left, arcing) carrying the bow
 * branch — Disengage, Nimble Draw, Swift Flight — up to Seeker Arrow, the
 * arrow through the bulge with Combustion at its head and the shared
 * Conservation shaft, and Focus at the top tip, fed by either capstone.
 */
public final class MarksmanNodes {
	public enum Family {
		TRUE_SHOT(() -> Items.SPECTRAL_ARROW),
		/** Chance per rank for a fired arrow to not be consumed. */
		CONSERVATION(() -> Items.ARROW),
		/** Arrow hits slow the target, I then II. */
		PINNING(() -> Items.COBWEB),
		/** Faster arrows, damage-neutral (speed up, base damage down). */
		SWIFT_FLIGHT(() -> Items.FEATHER),
		/** Sprint while drawing a bow: a quick roll forward. */
		ACROBATICS(() -> Items.RABBIT_FOOT),
		/** Walk closer to full speed while the bowstring is drawn. */
		NIMBLE_DRAW(() -> Items.STRING),
		/** A crossbow kill charges the next reload, up to instant. */
		RAPID_RELOAD(() -> Items.REDSTONE),
		/** Arrows hitting a burning target detonate. */
		COMBUSTION(() -> Items.FIRE_CHARGE),
		/** Arrow hits shave seconds off True Shot's cooldown. */
		FOCUS(() -> Items.SPYGLASS),
		/** Bow and crossbow shots ignore two points of armor. */
		PIERCING_TIPS(() -> Items.IRON_NUGGET),
		SEEKER_ARROW(() -> Items.ENDER_EYE),
		SNAP_SHOT(() -> Items.CROSSBOW),
		MINOR((Supplier<Item>) null);

		private final @Nullable Supplier<Item> icon;

		Family(final @Nullable Supplier<Item> icon) {
			this.icon = icon;
		}

		public @Nullable Item icon() {
			return this.icon == null ? null : this.icon.get();
		}

		public String nameKey() {
			return "node.archetypes.marksman." + this.name().toLowerCase(Locale.ROOT);
		}

		public String descriptionKey() {
			return this.nameKey() + ".desc";
		}
	}

	public record Def(Family family, int rank) {
	}

	private static final Map<Integer, Def> BY_INDEX = build();

	private MarksmanNodes() {
	}

	private static Map<Integer, Def> build() {
		Map<Long, Def> byCell = new HashMap<>();

		// The bottom tip, where stave meets string.
		byCell.put(cell(7, 0), new Def(Family.TRUE_SHOT, 1));

		// The string, bottom-up (the crossbow branch): Piercing Tips at the
		// nock, the Pinning barbs, then the Rapid Reload climb to Snap Shot.
		byCell.put(cell(7, 1), new Def(Family.PIERCING_TIPS, 1));
		byCell.put(cell(7, 2), new Def(Family.PINNING, 1));
		byCell.put(cell(7, 3), new Def(Family.PINNING, 2));
		byCell.put(cell(7, 4), new Def(Family.RAPID_RELOAD, 1));
		byCell.put(cell(7, 5), new Def(Family.RAPID_RELOAD, 2));
		byCell.put(cell(7, 6), new Def(Family.RAPID_RELOAD, 3));
		byCell.put(cell(7, 7), new Def(Family.RAPID_RELOAD, 4));
		byCell.put(cell(7, 8), new Def(Family.SNAP_SHOT, 1));

		// The stave, bottom-up (the bow branch): footwork, the draw at the
		// grip, Swift Flight up the springy upper limb to Seeker Arrow.
		byCell.put(cell(6, 1), new Def(Family.ACROBATICS, 1));
		byCell.put(cell(5, 2), new Def(Family.ACROBATICS, 2));
		byCell.put(cell(4, 3), new Def(Family.NIMBLE_DRAW, 1));
		byCell.put(cell(3, 4), new Def(Family.NIMBLE_DRAW, 2));
		byCell.put(cell(3, 5), new Def(Family.NIMBLE_DRAW, 3));
		byCell.put(cell(4, 6), new Def(Family.SWIFT_FLIGHT, 1));
		byCell.put(cell(5, 7), new Def(Family.SWIFT_FLIGHT, 2));
		byCell.put(cell(6, 8), new Def(Family.SEEKER_ARROW, 1));

		// The arrow at the bulge: burning head out past the stave, the
		// shared Conservation shaft running through to the string.
		byCell.put(cell(1, 5), new Def(Family.COMBUSTION, 1));
		byCell.put(cell(2, 5), new Def(Family.CONSERVATION, 1));
		byCell.put(cell(4, 5), new Def(Family.CONSERVATION, 2));
		byCell.put(cell(5, 5), new Def(Family.CONSERVATION, 3));
		byCell.put(cell(6, 5), new Def(Family.CONSERVATION, 4));

		// The top tip: Focus, adjacent to both capstones below it.
		byCell.put(cell(7, 9), new Def(Family.FOCUS, 1));

		Map<Integer, Def> byIndex = new HashMap<>();
		var nodes = Constellations.MARKSMAN_BOW.nodes();

		for (int i = 0; i < nodes.size(); i++) {
			Def def = byCell.get(cell(nodes.get(i).col(), nodes.get(i).row()));

			if (def != null) {
				byIndex.put(i, def);
			}
		}

		if (byIndex.size() != nodes.size()) {
			throw new IllegalStateException("Marksman bow has " + nodes.size()
					+ " nodes but " + byIndex.size() + " skill mappings — the grid and the map drifted");
		}

		return byIndex;
	}

	private static long cell(final int col, final int row) {
		return ((long) col << 32) | (row & 0xFFFFFFFFL);
	}

	public static Def def(final SubTree tree, final int nodeIndex) {
		return tree == SubTree.MARKSMAN
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
