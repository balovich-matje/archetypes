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
 * What each node of the epic Nemesis-Shadow constellation is (user sketch,
 * nemesis-shadow-first-20260720). The Dark Ritual at the foot is the whole
 * tree's gate — every other node reads "while in night form" — and it forks
 * into the two halves of the vampire: the left line is the body that stops
 * being solid (Ghost Form's three rungs into Incorporeal), the right line is
 * the predator's senses and appetite (Extra Sensory Perception, Eyes of the
 * Night, Feast's two rungs). Item icons stand in until an epic sprite set
 * ships.
 */
public final class NemesisShadowNodes {
	public enum Family {
		/** The epic active: a ten-second channel into an hour of night form. */
		DARK_RITUAL(() -> Items.WITHER_SKELETON_SKULL),
		/** Every creature within 32 blocks is sensed while transformed. */
		EXTRA_SENSORY_PERCEPTION(() -> Items.ENDER_EYE),
		/** Night vision while transformed (lang name: Eyes of the Night). */
		NIGHT_EYES(() -> Items.GOLDEN_CARROT),
		/** Attacks bleed the victim and feed the attacker, two ranks. */
		FEAST(() -> Items.REDSTONE),
		/** Three ranks: damage negation, slow falling, and the sneak-dash. */
		GHOST_FORM(() -> Items.PHANTOM_MEMBRANE),
		/** Knockback immunity and projectiles that pass straight through. */
		INCORPOREAL(() -> Items.GLASS),
		MINOR((Supplier<Item>) null);

		private final @Nullable Supplier<Item> icon;

		Family(final @Nullable Supplier<Item> icon) {
			this.icon = icon;
		}

		public @Nullable Item icon() {
			return this.icon == null ? null : this.icon.get();
		}

		public String nameKey() {
			return "node.archetypes.nemesis_shadow." + this.name().toLowerCase(Locale.ROOT);
		}

		public String descriptionKey() {
			return this.nameKey() + ".desc";
		}
	}

	public record Def(Family family, int rank) {
	}

	private static final Map<Integer, Def> BY_INDEX = build();

	private NemesisShadowNodes() {
	}

	private static Map<Integer, Def> build() {
		Map<Long, Def> byCell = new HashMap<>();

		// The root, and the only node that is not gated on night form.
		byCell.put(cell(2, 0), new Def(Family.DARK_RITUAL, 1));

		// Left line: the body going thin, bottom-up.
		byCell.put(cell(1, 1), new Def(Family.GHOST_FORM, 1));
		byCell.put(cell(0, 3), new Def(Family.GHOST_FORM, 2));
		byCell.put(cell(0, 4), new Def(Family.GHOST_FORM, 3));
		byCell.put(cell(0, 5), new Def(Family.INCORPOREAL, 1));

		// Right line: the senses and the appetite, bottom-up.
		byCell.put(cell(3, 1), new Def(Family.EXTRA_SENSORY_PERCEPTION, 1));
		byCell.put(cell(4, 3), new Def(Family.NIGHT_EYES, 1));
		byCell.put(cell(4, 4), new Def(Family.FEAST, 1));
		byCell.put(cell(4, 5), new Def(Family.FEAST, 2));

		Map<Integer, Def> byIndex = new HashMap<>();
		var nodes = Constellations.NEMESIS_SHADOW.nodes();

		for (int i = 0; i < nodes.size(); i++) {
			Def def = byCell.get(cell(nodes.get(i).col(), nodes.get(i).row()));

			if (def != null) {
				byIndex.put(i, def);
			}
		}

		if (byIndex.size() != nodes.size()) {
			throw new IllegalStateException("Nemesis Shadow has " + nodes.size()
					+ " nodes but " + byIndex.size() + " skill mappings — the grid and the map drifted");
		}

		return byIndex;
	}

	private static long cell(final int col, final int row) {
		return ((long) col << 32) | (row & 0xFFFFFFFFL);
	}

	public static Def def(final SubTree tree, final int nodeIndex) {
		return tree == SubTree.NEMESIS_SHADOW
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
		return rank(SubTree.NEMESIS_SHADOW,
				NodePurchases.owned(player, SubTree.NEMESIS_SHADOW), family);
	}
}
