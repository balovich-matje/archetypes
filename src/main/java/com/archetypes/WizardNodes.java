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
 * What each node of the Wizard constellation is. The staff reads bottom to
 * top as one cast: the butt cap holds the active and Mana Shield, the shaft
 * climbs through Force's damage ranks to the crossbar (Clarity, Siphon,
 * Echo — the economy of casting), the neck extends Range, and the diamond
 * head splits over the Arcane Orb: the left face finishes wounded prey
 * (Velocity, Overwhelm, up to Seeker Missile), the right face opens fights
 * (Concussion, Shatterpoint, up to Lance). The diamond's upper arc is the
 * crown — Mind Wells, Flow, the Archmage's tip — reachable from either
 * capstone.
 */
public final class WizardNodes {
	public enum Family {
		MAGIC_MISSILE(() -> Items.AMETHYST_SHARD),
		/** Part of the damage you take drains mana instead of health. */
		MANA_SHIELD(() -> Items.SHULKER_SHELL),
		/** +1 missile damage per rank. */
		FORCE(() -> Items.BREEZE_ROD),
		/** Missiles cost 5 less mana. */
		CLARITY(() -> Items.SUGAR),
		/** Missile kills refund mana. */
		SIPHON(() -> Items.GLASS_BOTTLE),
		/** Sometimes a missile brings a free twin. */
		ECHO(() -> Items.AMETHYST_CLUSTER),
		/** +8 blocks of missile range per rank. */
		RANGE(() -> Items.SPYGLASS),
		/** The orb in the head: +25 max mana. */
		ARCANE_ORB(() -> Items.ENDER_PEARL),
		/** Missiles fly 30% faster. */
		VELOCITY(() -> Items.FEATHER),
		/** +20% missile damage to anything already wounded. */
		OVERWHELM(() -> Items.SPECTRAL_ARROW),
		/** Missiles shove their target. */
		CONCUSSION(() -> Items.PISTON),
		/** +30% missile damage to targets at full health. */
		SHATTERPOINT(() -> Items.TARGET),
		/** Capstones: the hunt and the lance. */
		SEEKER_MISSILE(() -> Items.ENDER_EYE),
		LANCE(() -> Items.END_ROD),
		/** The crown: deeper wells, faster flow, the Archmage's fifth. */
		MIND_WELL(() -> Items.LAPIS_LAZULI),
		FLOW(() -> Items.HEART_OF_THE_SEA),
		ARCHMAGE(() -> Items.NETHER_STAR),
		MINOR((Supplier<Item>) null);

		private final @Nullable Supplier<Item> icon;

		Family(final @Nullable Supplier<Item> icon) {
			this.icon = icon;
		}

		public @Nullable Item icon() {
			return this.icon == null ? null : this.icon.get();
		}

		public String nameKey() {
			return "node.archetypes.wizard." + this.name().toLowerCase(Locale.ROOT);
		}

		public String descriptionKey() {
			return this.nameKey() + ".desc";
		}
	}

	public record Def(Family family, int rank) {
	}

	private static final Map<Integer, Def> BY_INDEX = build();

	private WizardNodes() {
	}

	private static Map<Integer, Def> build() {
		Map<Long, Def> byCell = new HashMap<>();

		// The butt cap: the active flanked by the shield's two ranks.
		byCell.put(cell(3, 0), new Def(Family.MAGIC_MISSILE, 1));
		byCell.put(cell(2, 0), new Def(Family.MANA_SHIELD, 1));
		byCell.put(cell(4, 0), new Def(Family.MANA_SHIELD, 2));

		// The shaft: raw force, rank over rank.
		byCell.put(cell(3, 1), new Def(Family.FORCE, 1));
		byCell.put(cell(3, 2), new Def(Family.FORCE, 2));
		byCell.put(cell(3, 3), new Def(Family.FORCE, 3));

		// The crossbar: the economy of casting.
		byCell.put(cell(2, 4), new Def(Family.CLARITY, 1));
		byCell.put(cell(3, 4), new Def(Family.SIPHON, 1));
		byCell.put(cell(4, 4), new Def(Family.ECHO, 1));

		// The neck: reach.
		byCell.put(cell(3, 5), new Def(Family.RANGE, 1));
		byCell.put(cell(3, 6), new Def(Family.RANGE, 2));

		// The diamond: the orb in the cradle, a face per build, a capstone
		// at each side point.
		byCell.put(cell(3, 8), new Def(Family.ARCANE_ORB, 1));
		byCell.put(cell(2, 7), new Def(Family.VELOCITY, 1));
		byCell.put(cell(1, 8), new Def(Family.OVERWHELM, 1));
		byCell.put(cell(0, 9), new Def(Family.SEEKER_MISSILE, 1));
		byCell.put(cell(4, 7), new Def(Family.CONCUSSION, 1));
		byCell.put(cell(5, 8), new Def(Family.SHATTERPOINT, 1));
		byCell.put(cell(6, 9), new Def(Family.LANCE, 1));

		// The crown arc over the head, fed by either capstone.
		byCell.put(cell(1, 10), new Def(Family.MIND_WELL, 1));
		byCell.put(cell(5, 10), new Def(Family.MIND_WELL, 2));
		byCell.put(cell(2, 11), new Def(Family.FLOW, 1));
		byCell.put(cell(4, 11), new Def(Family.FLOW, 2));
		byCell.put(cell(3, 12), new Def(Family.ARCHMAGE, 1));

		Map<Integer, Def> byIndex = new HashMap<>();
		var nodes = Constellations.WIZARD_STAFF.nodes();

		for (int i = 0; i < nodes.size(); i++) {
			Def def = byCell.get(cell(nodes.get(i).col(), nodes.get(i).row()));

			if (def != null) {
				byIndex.put(i, def);
			}
		}

		if (byIndex.size() != nodes.size()) {
			throw new IllegalStateException("Wizard staff has " + nodes.size()
					+ " nodes but " + byIndex.size() + " skill mappings — the grid and the map drifted");
		}

		return byIndex;
	}

	private static long cell(final int col, final int row) {
		return ((long) col << 32) | (row & 0xFFFFFFFFL);
	}

	public static Def def(final SubTree tree, final int nodeIndex) {
		return tree == SubTree.WIZARD
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
