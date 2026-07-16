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
 * What each node of the Crusher constellation is. The constellation IS a mace:
 * the shared bruiser passives run up the handle (both weapons — bare fists and
 * the mace — use them, fists at double effect), the head's left flange is the
 * fists path, the right flange the mace path (still placeholders), and Battle
 * Trance crowns the top, fed by either capstone.
 *
 * <p>Item icons are placeholders until the drawn-icon pass.
 */
public final class CrusherNodes {
	public enum Family {
		ADRENALINE(() -> Items.SUGAR),
		SUNDER(() -> Items.FLINT),
		BARE_KNUCKLE(() -> Items.BRICK),
		IRON_SKIN(() -> Items.IRON_CHESTPLATE),
		HAYMAKER(() -> Items.BLAZE_POWDER),
		METEOR(() -> Items.ANVIL),
		SHOCKWAVE(() -> Items.WIND_CHARGE),
		QUAKE(() -> Items.MACE),
		BATTLE_TRANCE(() -> Items.GOLDEN_APPLE),
		MINOR(null);

		private final @Nullable Supplier<Item> icon;

		Family(final @Nullable Supplier<Item> icon) {
			this.icon = icon;
		}

		public @Nullable Item icon() {
			return this.icon == null ? null : this.icon.get();
		}

		public String nameKey() {
			return "node.archetypes.crusher." + this.name().toLowerCase(Locale.ROOT);
		}

		public String descriptionKey() {
			return this.nameKey() + ".desc";
		}
	}

	public record Def(Family family, int rank) {
	}

	private static final Map<Integer, Def> BY_INDEX = build();

	private CrusherNodes() {
	}

	private static Map<Integer, Def> build() {
		Map<Long, Def> byCell = new HashMap<>();

		// The handle: shared bruiser passives, root at the pommel. Full path
		// economics: handle 4 + flange 8 (capstone included) + crown 3 = 15
		// exactly, either weapon; 23 nodes total.
		byCell.put(cell(4, 0), new Def(Family.ADRENALINE, 1));
		byCell.put(cell(4, 1), new Def(Family.ADRENALINE, 2));
		byCell.put(cell(4, 2), new Def(Family.SUNDER, 1));
		byCell.put(cell(4, 3), new Def(Family.SUNDER, 2));

		// Left flange: the bare-fists path.
		byCell.put(cell(3, 4), new Def(Family.BARE_KNUCKLE, 1));
		byCell.put(cell(3, 5), new Def(Family.BARE_KNUCKLE, 2));
		byCell.put(cell(3, 6), new Def(Family.BARE_KNUCKLE, 3));
		byCell.put(cell(3, 7), new Def(Family.BARE_KNUCKLE, 4));
		byCell.put(cell(2, 6), new Def(Family.IRON_SKIN, 1));
		byCell.put(cell(2, 7), new Def(Family.IRON_SKIN, 2));
		byCell.put(cell(2, 8), new Def(Family.IRON_SKIN, 3));
		byCell.put(cell(3, 8), new Def(Family.HAYMAKER, 1));

		// Right flange: the mace path — Shockwave up the column, Meteor on the
		// side, two slots still open for the utility perk, Quake at the peak.
		byCell.put(cell(5, 4), new Def(Family.SHOCKWAVE, 1));
		byCell.put(cell(5, 5), new Def(Family.SHOCKWAVE, 2));
		byCell.put(cell(5, 6), new Def(Family.SHOCKWAVE, 3));
		byCell.put(cell(5, 7), new Def(Family.METEOR, 1));
		byCell.put(cell(6, 7), new Def(Family.METEOR, 2));
		byCell.put(cell(6, 6), new Def(Family.MINOR, 1));
		byCell.put(cell(6, 8), new Def(Family.MINOR, 1));
		byCell.put(cell(5, 8), new Def(Family.QUAKE, 1));

		// The crown: Battle Trance, shared, reachable from either capstone.
		byCell.put(cell(3, 9), new Def(Family.BATTLE_TRANCE, 1));
		byCell.put(cell(4, 9), new Def(Family.BATTLE_TRANCE, 2));
		byCell.put(cell(5, 9), new Def(Family.BATTLE_TRANCE, 3));

		Map<Integer, Def> byIndex = new HashMap<>();
		var nodes = Constellations.CRUSHER_MACE.nodes();

		for (int i = 0; i < nodes.size(); i++) {
			Def def = byCell.get(cell(nodes.get(i).col(), nodes.get(i).row()));

			if (def != null) {
				byIndex.put(i, def);
			}
		}

		if (byIndex.size() != nodes.size()) {
			throw new IllegalStateException("Crusher mace has " + nodes.size()
					+ " nodes but " + byIndex.size() + " skill mappings — the grid and the map drifted");
		}

		return byIndex;
	}

	private static long cell(final int col, final int row) {
		return (long) col << 32 | row;
	}

	public static Def def(final SubTree tree, final int index) {
		Def def = BY_INDEX.get(index);

		if (def == null) {
			throw new IllegalArgumentException("No crusher node at index " + index);
		}

		return def;
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
