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
 * What each node of the epic Colossus-Slayer constellation is (user sketch
 * colossus-slayer-suggested-edits-20260720). Parry is the pinch of the
 * hourglass and takes no ability key — it is an input combo (attack and block
 * together), so nothing here rides {@code ActiveAbilityPayload}. Below it sit
 * the two roots that earn it, Barbarian and Blade Master; above it the three
 * ways a successful parry pays.
 */
public final class ColossusSlayerNodes {
	public enum Family {
		/** The pinch: attack + block together, on a generous window. */
		PARRY(() -> Items.IRON_SWORD),
		/** Two ranks of magical damage AND magical healing cut away. */
		BARBARIAN(() -> Items.POTION),
		/** Two ranks: greatsword swing time down, sword damage up. */
		BLADE_MASTER(() -> Items.NETHERITE_SWORD),
		/** Directed spells parried back at the caster; area spells only voided. */
		SPELL_REFLECT(() -> Items.AMETHYST_SHARD),
		/** Parries bank temporary hearts (lang name: Stalwart). */
		STALWART(() -> Items.GOLDEN_APPLE),
		/** Two ranks of Strength off a parry (lang name: Riposte). */
		RIPOSTE(() -> Items.BLAZE_POWDER),
		MINOR((Supplier<Item>) null);

		private final @Nullable Supplier<Item> icon;

		Family(final @Nullable Supplier<Item> icon) {
			this.icon = icon;
		}

		public @Nullable Item icon() {
			return this.icon == null ? null : this.icon.get();
		}

		public String nameKey() {
			return "node.archetypes.colossus_slayer." + this.name().toLowerCase(Locale.ROOT);
		}

		public String descriptionKey() {
			return this.nameKey() + ".desc";
		}
	}

	public record Def(Family family, int rank) {
	}

	private static final Map<Integer, Def> BY_INDEX = build();

	private ColossusSlayerNodes() {
	}

	private static Map<Integer, Def> build() {
		Map<Long, Def> byCell = new HashMap<>();

		// The two feet, and the pinch they both reach.
		byCell.put(cell(0, 0), new Def(Family.BARBARIAN, 1));
		byCell.put(cell(1, 1), new Def(Family.BARBARIAN, 2));
		byCell.put(cell(4, 0), new Def(Family.BLADE_MASTER, 1));
		byCell.put(cell(3, 1), new Def(Family.BLADE_MASTER, 2));
		byCell.put(cell(2, 2), new Def(Family.PARRY, 1));

		// What the parry pays, fanning back out.
		byCell.put(cell(1, 3), new Def(Family.RIPOSTE, 1));
		byCell.put(cell(0, 4), new Def(Family.RIPOSTE, 2));
		byCell.put(cell(3, 3), new Def(Family.STALWART, 1));
		byCell.put(cell(4, 4), new Def(Family.SPELL_REFLECT, 1));

		Map<Integer, Def> byIndex = new HashMap<>();
		var nodes = Constellations.COLOSSUS_SLAYER.nodes();

		for (int i = 0; i < nodes.size(); i++) {
			Def def = byCell.get(cell(nodes.get(i).col(), nodes.get(i).row()));

			if (def != null) {
				byIndex.put(i, def);
			}
		}

		if (byIndex.size() != nodes.size()) {
			throw new IllegalStateException("Colossus Slayer has " + nodes.size()
					+ " nodes but " + byIndex.size() + " skill mappings — the grid and the map drifted");
		}

		return byIndex;
	}

	private static long cell(final int col, final int row) {
		return ((long) col << 32) | (row & 0xFFFFFFFFL);
	}

	public static Def def(final SubTree tree, final int nodeIndex) {
		return tree == SubTree.COLOSSUS_SLAYER
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
		return rank(SubTree.COLOSSUS_SLAYER,
				NodePurchases.owned(player, SubTree.COLOSSUS_SLAYER), family);
	}
}
