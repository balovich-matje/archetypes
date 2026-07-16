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
 * drawn bow with an arrow through it: True Shot at the grip (bottom), the
 * left limb the bow's (Disengage, Nimble Draw) up to Seeker Arrow, the right
 * limb the crossbow's (Night Vision, Rapid Reload) up to Snap Shot, the
 * arrow crossing the middle as the shared row — Combustion at the head,
 * Pinning barbs, the Conservation shaft between the limbs, Swift Flight in
 * the fletching — and Focus at the crown, fed by either capstone.
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
		/** Sprint while drawing a bow: a quick leap backwards. */
		DISENGAGE(() -> Items.RABBIT_FOOT),
		/** Walk closer to full speed while the bowstring is drawn. */
		NIMBLE_DRAW(() -> Items.STRING),
		/** A crossbow kill charges the next reload, up to instant. */
		RAPID_RELOAD(() -> Items.REDSTONE),
		/** Arrows hitting a burning target detonate. */
		COMBUSTION(() -> Items.FIRE_CHARGE),
		/** Arrow hits shave seconds off True Shot's cooldown. */
		FOCUS(() -> Items.SPYGLASS),
		/** Kills with either weapon grant Night Vision. */
		NIGHT_VISION(() -> Items.GOLDEN_CARROT),
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

		// The grip: where both limbs meet.
		byCell.put(cell(5, 0), new Def(Family.TRUE_SHOT, 1));

		// Left limb, bottom-up: the bow's — footwork first, then the draw.
		byCell.put(cell(4, 1), new Def(Family.DISENGAGE, 1));
		byCell.put(cell(3, 2), new Def(Family.DISENGAGE, 2));
		byCell.put(cell(2, 3), new Def(Family.NIMBLE_DRAW, 1));
		byCell.put(cell(2, 4), new Def(Family.NIMBLE_DRAW, 2));
		byCell.put(cell(3, 5), new Def(Family.NIMBLE_DRAW, 3));

		// Right limb, bottom-up: the crossbow's — Night Vision at the base
		// (it serves both weapons; the arrow row bridges builds across).
		byCell.put(cell(6, 1), new Def(Family.NIGHT_VISION, 1));
		byCell.put(cell(7, 2), new Def(Family.RAPID_RELOAD, 1));
		byCell.put(cell(8, 3), new Def(Family.RAPID_RELOAD, 2));
		byCell.put(cell(8, 4), new Def(Family.RAPID_RELOAD, 3));
		byCell.put(cell(7, 5), new Def(Family.RAPID_RELOAD, 4));

		// The arrow, left to right: burning head, a barb either end of the
		// shared Conservation shaft, feathers for Swift Flight.
		byCell.put(cell(0, 3), new Def(Family.COMBUSTION, 1));
		byCell.put(cell(1, 3), new Def(Family.PINNING, 1));
		byCell.put(cell(3, 3), new Def(Family.CONSERVATION, 1));
		byCell.put(cell(4, 3), new Def(Family.CONSERVATION, 2));
		byCell.put(cell(5, 3), new Def(Family.CONSERVATION, 3));
		byCell.put(cell(6, 3), new Def(Family.CONSERVATION, 4));
		byCell.put(cell(7, 3), new Def(Family.PINNING, 2));
		byCell.put(cell(9, 3), new Def(Family.SWIFT_FLIGHT, 1));
		byCell.put(cell(10, 3), new Def(Family.SWIFT_FLIGHT, 2));

		// The crown: capstones atop their limbs, Focus at the very top,
		// adjacent to both — either choice unlocks it.
		byCell.put(cell(4, 6), new Def(Family.SEEKER_ARROW, 1));
		byCell.put(cell(6, 6), new Def(Family.SNAP_SHOT, 1));
		byCell.put(cell(5, 7), new Def(Family.FOCUS, 1));

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
