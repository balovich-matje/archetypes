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
 * What each node of the epic Oracle-Wizard constellation is (draft
 * oracle-wizard). Magic Armaments conjures a weapon in place of the wand; the
 * left line hardens the channel (Magic Armor's temp health, then Levitation's
 * flight and Warding's status immunity) while the right line sharpens the blade
 * (Mind Over Matter's three ranks), whose top rung splits to Blink and the
 * Spellbow capstone that swaps the sword for a bow. Item icons stand in until an
 * epic sprite set ships.
 */
public final class OracleWizardNodes {
	public enum Family {
		/** The epic active: conjure a weapon of immense power in place of the wand. */
		MAGIC_ARMAMENTS(() -> Items.NETHERITE_SWORD),
		/** Channel mana also grants temporary health, up to 10/20. */
		MAGIC_ARMOR(() -> Items.DIAMOND_CHESTPLATE),
		/** Flight during the channel. */
		LEVITATION(() -> Items.FEATHER),
		/** Immunity to negative status effects during the channel. */
		WARD(() -> Items.MILK_BUCKET),
		/** More weapon damage for more channel cost, three ranks. */
		MIND_OVER_MATTER(() -> Items.BREEZE_ROD),
		/** Swings teleport you forward when not targeting a hostile. */
		BLINK(() -> Items.ENDER_PEARL),
		/** Capstone-worthy: the armament is a bow instead of a sword. */
		SPELLBOW(() -> Items.BOW),
		MINOR((Supplier<Item>) null);

		private final @Nullable Supplier<Item> icon;

		Family(final @Nullable Supplier<Item> icon) {
			this.icon = icon;
		}

		public @Nullable Item icon() {
			return this.icon == null ? null : this.icon.get();
		}

		public String nameKey() {
			return "node.archetypes.oracle_wizard." + this.name().toLowerCase(Locale.ROOT);
		}

		public String descriptionKey() {
			return this.nameKey() + ".desc";
		}
	}

	public record Def(Family family, int rank) {
	}

	private static final Map<Integer, Def> BY_INDEX = build();

	private OracleWizardNodes() {
	}

	private static Map<Integer, Def> build() {
		Map<Long, Def> byCell = new HashMap<>();

		// The root.
		byCell.put(cell(2, 0), new Def(Family.MAGIC_ARMAMENTS, 1));

		// Left line: the defensive channel upgrades, bottom-up.
		byCell.put(cell(1, 1), new Def(Family.MAGIC_ARMOR, 1));
		byCell.put(cell(0, 2), new Def(Family.MAGIC_ARMOR, 2));
		byCell.put(cell(0, 3), new Def(Family.LEVITATION, 1));
		byCell.put(cell(0, 4), new Def(Family.WARD, 1));

		// Right line: the offensive channel upgrades, splitting at the top.
		byCell.put(cell(3, 1), new Def(Family.MIND_OVER_MATTER, 1));
		byCell.put(cell(4, 2), new Def(Family.MIND_OVER_MATTER, 2));
		byCell.put(cell(4, 3), new Def(Family.MIND_OVER_MATTER, 3));
		byCell.put(cell(3, 4), new Def(Family.BLINK, 1));
		byCell.put(cell(5, 4), new Def(Family.SPELLBOW, 1));

		Map<Integer, Def> byIndex = new HashMap<>();
		var nodes = Constellations.ORACLE_WIZARD.nodes();

		for (int i = 0; i < nodes.size(); i++) {
			Def def = byCell.get(cell(nodes.get(i).col(), nodes.get(i).row()));

			if (def != null) {
				byIndex.put(i, def);
			}
		}

		if (byIndex.size() != nodes.size()) {
			throw new IllegalStateException("Oracle Wizard has " + nodes.size()
					+ " nodes but " + byIndex.size() + " skill mappings — the grid and the map drifted");
		}

		return byIndex;
	}

	private static long cell(final int col, final int row) {
		return ((long) col << 32) | (row & 0xFFFFFFFFL);
	}

	public static Def def(final SubTree tree, final int nodeIndex) {
		return tree == SubTree.ORACLE_WIZARD
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
