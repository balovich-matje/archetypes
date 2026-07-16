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
 * strung bow: True Shot at the bottom tip where stave meets string, the
 * string side (right) carrying the ammunition-and-reload families up to Snap
 * Shot, the stave side (left) carrying the archer's-body families up to
 * Seeker Arrow, Combustion burning at the arrow rest on the stave's belly,
 * and one unnamed star at the top tip — spare, awaiting an idea.
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

		// The root: where the stave meets the string.
		byCell.put(cell(3, 0), new Def(Family.TRUE_SHOT, 1));

		// String side, bottom-up: ammunition first, then the reload climb,
		// Focus at the string's top anchor.
		byCell.put(cell(4, 1), new Def(Family.CONSERVATION, 1));
		byCell.put(cell(4, 2), new Def(Family.CONSERVATION, 2));
		byCell.put(cell(4, 3), new Def(Family.CONSERVATION, 3));
		byCell.put(cell(4, 4), new Def(Family.CONSERVATION, 4));
		byCell.put(cell(4, 5), new Def(Family.RAPID_RELOAD, 1));
		byCell.put(cell(4, 6), new Def(Family.RAPID_RELOAD, 2));
		byCell.put(cell(4, 7), new Def(Family.RAPID_RELOAD, 3));
		byCell.put(cell(4, 8), new Def(Family.RAPID_RELOAD, 4));
		byCell.put(cell(4, 9), new Def(Family.FOCUS, 1));

		// Stave side, bottom-up: the archer's body — footwork, draw, flight,
		// pinning — with Combustion at the arrow rest on the belly.
		byCell.put(cell(2, 1), new Def(Family.DISENGAGE, 1));
		byCell.put(cell(1, 2), new Def(Family.DISENGAGE, 2));
		byCell.put(cell(0, 3), new Def(Family.NIMBLE_DRAW, 1));
		byCell.put(cell(0, 4), new Def(Family.NIMBLE_DRAW, 2));
		byCell.put(cell(0, 5), new Def(Family.NIMBLE_DRAW, 3));
		byCell.put(cell(0, 6), new Def(Family.SWIFT_FLIGHT, 1));
		byCell.put(cell(0, 7), new Def(Family.SWIFT_FLIGHT, 2));
		byCell.put(cell(1, 6), new Def(Family.COMBUSTION, 1));
		byCell.put(cell(0, 8), new Def(Family.PINNING, 1));
		byCell.put(cell(1, 9), new Def(Family.PINNING, 2));

		// The crown: capstones on the two limbs, the spare star at the tip.
		byCell.put(cell(2, 10), new Def(Family.SEEKER_ARROW, 1));
		byCell.put(cell(4, 10), new Def(Family.SNAP_SHOT, 1));
		byCell.put(cell(3, 11), new Def(Family.MINOR, 1));

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
