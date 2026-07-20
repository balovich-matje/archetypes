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
 * What each node of the epic Nemesis-Assassin constellation is (draft
 * nemesis-assassin). Death Mark at the foot is the tree's one ability key (slot
 * 5 for a Cutpurse) and every other node reads off the mark: the left column is
 * what you do to it, the right column is what it does to everything standing
 * near it.
 */
public final class NemesisAssassinNodes {
	public enum Family {
		/** The epic active: the sixty-second mark, slot 5. */
		DEATH_MARK(() -> Items.WITHER_ROSE),
		/** The mark is outlined through walls and stops noticing you. */
		STALK(() -> Items.ENDER_EYE),
		/** The mark's death passes it on rather than clearing it. */
		CONTAGION(() -> Items.SPIDER_EYE),
		/** Two ranks of dagger damage on the mark. */
		HEADHUNTER(() -> ModItems.NETHERITE_DAGGER),
		/** Shadow Step executes a wounded mark outright. */
		COUP_DE_GRACE(() -> ModItems.DIAMOND_DAGGER),
		/** The mark spreads its own poisons to its neighbours. */
		CARRIER(() -> Items.FERMENTED_SPIDER_EYE),
		/** Killing the mark hides you. */
		VANISHING_ACT(() -> Items.POTION),
		/** Killing the mark hurts everything around it. */
		DEATHS_HEAD(() -> Items.SKELETON_SKULL),
		MINOR((Supplier<Item>) null);

		private final @Nullable Supplier<Item> icon;

		Family(final @Nullable Supplier<Item> icon) {
			this.icon = icon;
		}

		public @Nullable Item icon() {
			return this.icon == null ? null : this.icon.get();
		}

		public String nameKey() {
			return "node.archetypes.nemesis_assassin." + this.name().toLowerCase(Locale.ROOT);
		}

		public String descriptionKey() {
			return this.nameKey() + ".desc";
		}
	}

	public record Def(Family family, int rank) {
	}

	private static final Map<Integer, Def> BY_INDEX = build();

	private NemesisAssassinNodes() {
	}

	private static Map<Integer, Def> build() {
		Map<Long, Def> byCell = new HashMap<>();

		byCell.put(cell(2, 0), new Def(Family.DEATH_MARK, 1));

		// Left column: what you do to the mark, bottom-up.
		byCell.put(cell(1, 1), new Def(Family.STALK, 1));
		byCell.put(cell(0, 2), new Def(Family.HEADHUNTER, 1));
		byCell.put(cell(0, 3), new Def(Family.HEADHUNTER, 2));
		byCell.put(cell(0, 4), new Def(Family.COUP_DE_GRACE, 1));

		// Right column: what the mark does to its neighbours, bottom-up.
		byCell.put(cell(3, 1), new Def(Family.CONTAGION, 1));
		byCell.put(cell(4, 2), new Def(Family.CARRIER, 1));
		byCell.put(cell(4, 3), new Def(Family.VANISHING_ACT, 1));
		byCell.put(cell(4, 4), new Def(Family.DEATHS_HEAD, 1));

		Map<Integer, Def> byIndex = new HashMap<>();
		var nodes = Constellations.NEMESIS_ASSASSIN.nodes();

		for (int i = 0; i < nodes.size(); i++) {
			Def def = byCell.get(cell(nodes.get(i).col(), nodes.get(i).row()));

			if (def != null) {
				byIndex.put(i, def);
			}
		}

		if (byIndex.size() != nodes.size()) {
			throw new IllegalStateException("Nemesis Assassin has " + nodes.size()
					+ " nodes but " + byIndex.size() + " skill mappings — the grid and the map drifted");
		}

		return byIndex;
	}

	private static long cell(final int col, final int row) {
		return ((long) col << 32) | (row & 0xFFFFFFFFL);
	}

	public static Def def(final SubTree tree, final int nodeIndex) {
		return tree == SubTree.NEMESIS_ASSASSIN
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

	/** The owning player's rank in a family — the form every gameplay hook wants. */
	public static int rank(final net.minecraft.world.entity.player.Player player, final Family family) {
		return rank(SubTree.NEMESIS_ASSASSIN,
				NodePurchases.owned(player, SubTree.NEMESIS_ASSASSIN), family);
	}
}
