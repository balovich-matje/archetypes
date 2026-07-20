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
 * What each node of the epic Colossus-Crusher constellation is (user sketch
 * colossus-crusher-suggested-edits-20260720). Titan's Leap at the foot is the
 * tree's one ability key (slot 6 for a Brawler), and the fork above it decides
 * what the landing is for: the left column makes it hit, the right column
 * makes it hold — then goes on to what the body is worth once it lands.
 */
public final class ColossusCrusherNodes {
	public enum Family {
		/** The epic active: a mace-or-fists leap, slot 6 for Strength. */
		TITAN_LEAP(() -> Items.MACE),
		/** Three ranks: the landing slams, scaling with the fall. */
		AFTERSHOCK(() -> Items.MAGMA_BLOCK),
		/** The landing drags and holds instead of hitting. */
		GRAVITY_WELL(() -> Items.ENDER_PEARL),
		/** Nothing shoves, launches or drops you. */
		IMMOVABLE(() -> Items.ANVIL),
		/** Two ranks riding Battle Trance's banked health. */
		BULWARK(() -> Items.IRON_CHESTPLATE),
		/** Mace and unarmed hits cannot be blocked (lang name: Unstoppable force). */
		SIEGEBREAKER(() -> Items.OBSIDIAN),
		MINOR((Supplier<Item>) null);

		private final @Nullable Supplier<Item> icon;

		Family(final @Nullable Supplier<Item> icon) {
			this.icon = icon;
		}

		public @Nullable Item icon() {
			return this.icon == null ? null : this.icon.get();
		}

		public String nameKey() {
			return "node.archetypes.colossus_crusher." + this.name().toLowerCase(Locale.ROOT);
		}

		public String descriptionKey() {
			return this.nameKey() + ".desc";
		}
	}

	public record Def(Family family, int rank) {
	}

	private static final Map<Integer, Def> BY_INDEX = build();

	private ColossusCrusherNodes() {
	}

	private static Map<Integer, Def> build() {
		Map<Long, Def> byCell = new HashMap<>();

		byCell.put(cell(2, 0), new Def(Family.TITAN_LEAP, 1));

		// Left column: the landing that hits, bottom-up.
		byCell.put(cell(1, 1), new Def(Family.AFTERSHOCK, 1));
		byCell.put(cell(0, 2), new Def(Family.AFTERSHOCK, 2));
		byCell.put(cell(0, 3), new Def(Family.AFTERSHOCK, 3));
		byCell.put(cell(0, 4), new Def(Family.SIEGEBREAKER, 1));

		// Right column: the landing that holds, then the body, bottom-up.
		byCell.put(cell(3, 1), new Def(Family.GRAVITY_WELL, 1));
		byCell.put(cell(4, 2), new Def(Family.IMMOVABLE, 1));
		byCell.put(cell(4, 3), new Def(Family.BULWARK, 1));
		byCell.put(cell(4, 4), new Def(Family.BULWARK, 2));

		Map<Integer, Def> byIndex = new HashMap<>();
		var nodes = Constellations.COLOSSUS_CRUSHER.nodes();

		for (int i = 0; i < nodes.size(); i++) {
			Def def = byCell.get(cell(nodes.get(i).col(), nodes.get(i).row()));

			if (def != null) {
				byIndex.put(i, def);
			}
		}

		if (byIndex.size() != nodes.size()) {
			throw new IllegalStateException("Colossus Crusher has " + nodes.size()
					+ " nodes but " + byIndex.size() + " skill mappings — the grid and the map drifted");
		}

		return byIndex;
	}

	private static long cell(final int col, final int row) {
		return ((long) col << 32) | (row & 0xFFFFFFFFL);
	}

	public static Def def(final SubTree tree, final int nodeIndex) {
		return tree == SubTree.COLOSSUS_CRUSHER
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
		return rank(SubTree.COLOSSUS_CRUSHER,
				NodePurchases.owned(player, SubTree.COLOSSUS_CRUSHER), family);
	}
}
