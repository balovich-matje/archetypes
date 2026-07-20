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
 * What each node of the epic Colossus-Protector constellation is (user sketch
 * colossus-protector-suggested-edits-20260720). Ironclad at the foot is a
 * passive, not an active — the sketch replaced the planted-Aegis root with a
 * flat armour multiplier, so this tree claims no ability key. The two columns
 * split the colossus in half: the left one is the body it takes to stand there
 * (eating, and what eating is worth), the right one is the guard that never
 * drops.
 */
public final class ColossusProtectorNodes {
	public enum Family {
		/** The root: a final multiplier on armour and armour toughness. */
		IRONCLAD(() -> Items.DIAMOND_CHESTPLATE),
		/** Two ranks: faster eating, and a hunger bar that banks more. */
		WELL_FED(() -> Items.GOLDEN_APPLE),
		/** Food carries an eight-minute buff, by food group. */
		HEARTY_MEAL(() -> Items.MILK_BUCKET),
		/** Two ranks of blocking that costs no button — the shield still pays. */
		INSTINCTIVE_GUARD(() -> Items.SHIELD),
		/** Every one-handed action stays available while blocking. */
		FREE_HAND(() -> Items.POTION),
		/** Block and swing at once (author's name: Immovable object). */
		IMMOVABLE_OBJECT(() -> Items.IRON_SWORD),
		MINOR((Supplier<Item>) null);

		private final @Nullable Supplier<Item> icon;

		Family(final @Nullable Supplier<Item> icon) {
			this.icon = icon;
		}

		public @Nullable Item icon() {
			return this.icon == null ? null : this.icon.get();
		}

		public String nameKey() {
			return "node.archetypes.colossus_protector." + this.name().toLowerCase(Locale.ROOT);
		}

		public String descriptionKey() {
			return this.nameKey() + ".desc";
		}
	}

	public record Def(Family family, int rank) {
	}

	private static final Map<Integer, Def> BY_INDEX = build();

	private ColossusProtectorNodes() {
	}

	private static Map<Integer, Def> build() {
		Map<Long, Def> byCell = new HashMap<>();

		byCell.put(cell(2, 0), new Def(Family.IRONCLAD, 1));

		// Left column: the fed body, bottom-up.
		byCell.put(cell(0, 2), new Def(Family.WELL_FED, 1));
		byCell.put(cell(0, 3), new Def(Family.WELL_FED, 2));
		byCell.put(cell(0, 4), new Def(Family.HEARTY_MEAL, 1));

		// Right column: the guard that never drops, bottom-up.
		byCell.put(cell(4, 2), new Def(Family.INSTINCTIVE_GUARD, 1));
		byCell.put(cell(4, 3), new Def(Family.INSTINCTIVE_GUARD, 2));
		byCell.put(cell(4, 4), new Def(Family.IMMOVABLE_OBJECT, 1));

		// The crown, hung between the two columns on explicit edges.
		byCell.put(cell(2, 4), new Def(Family.FREE_HAND, 1));

		Map<Integer, Def> byIndex = new HashMap<>();
		var nodes = Constellations.COLOSSUS_PROTECTOR.nodes();

		for (int i = 0; i < nodes.size(); i++) {
			Def def = byCell.get(cell(nodes.get(i).col(), nodes.get(i).row()));

			if (def != null) {
				byIndex.put(i, def);
			}
		}

		if (byIndex.size() != nodes.size()) {
			throw new IllegalStateException("Colossus Protector has " + nodes.size()
					+ " nodes but " + byIndex.size() + " skill mappings — the grid and the map drifted");
		}

		return byIndex;
	}

	private static long cell(final int col, final int row) {
		return ((long) col << 32) | (row & 0xFFFFFFFFL);
	}

	public static Def def(final SubTree tree, final int nodeIndex) {
		return tree == SubTree.COLOSSUS_PROTECTOR
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
		return rank(SubTree.COLOSSUS_PROTECTOR,
				NodePurchases.owned(player, SubTree.COLOSSUS_PROTECTOR), family);
	}
}
