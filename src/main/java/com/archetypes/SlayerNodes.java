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
 * What each node of the Slayer constellation is. The constellation IS a sword:
 * pommel root at the bottom (Hamstring — CC that serves both weapons), grip,
 * the crossguard row where the paths split (Lunge arm left, Immovable arm
 * right), then the two blade edges — sword path up the left (gap-closing,
 * crowd control, sustain), claymore path up the right (the oneshot fantasy) —
 * with an empty fuller between them and the capstone crown at the tip.
 */
public final class SlayerNodes {
	public enum Family {
		SLOWNESS(() -> Items.COBWEB),
		VAMP(() -> Items.GLISTERING_MELON_SLICE),
		LUNGE(() -> Items.RABBIT_FOOT),
		KBRES(() -> Items.OBSIDIAN),
		BLEED(() -> Items.REDSTONE),
		HEAVY(() -> ModItems.IRON_CLAYMORE),
		FIRSTBLOOD(() -> Items.TARGET),
		FLURRY(() -> Items.SUGAR),
		EXECUTIONER(() -> Items.NETHERITE_AXE),
		BLOODLUST(() -> Items.FERMENTED_SPIDER_EYE),
		RELENTLESS(() -> Items.CLOCK),
		BLADESTORM(() -> Items.DIAMOND_SWORD),
		DECIMATE(() -> ModItems.NETHERITE_CLAYMORE),
		MINOR(null);

		private final @Nullable Supplier<Item> icon;

		Family(final @Nullable Supplier<Item> icon) {
			this.icon = icon;
		}

		public @Nullable Item icon() {
			return this.icon == null ? null : this.icon.get();
		}

		public String nameKey() {
			return "node.archetypes.slayer." + this.name().toLowerCase(Locale.ROOT);
		}

		public String descriptionKey() {
			return this.nameKey() + ".desc";
		}
	}

	public record Def(Family family, int rank) {
	}

	private static final Def MINOR_DEF = new Def(Family.MINOR, 1);
	private static final Map<Integer, Def> BY_INDEX = build();

	private SlayerNodes() {
	}

	private static Map<Integer, Def> build() {
		Map<Long, Def> byCell = new HashMap<>();

		// Pommel and grip: crowd control both weapons share.
		byCell.put(cell(4, 0), new Def(Family.SLOWNESS, 1));
		byCell.put(cell(4, 1), new Def(Family.SLOWNESS, 2));

		// Crossguard: Lunge out the left arm, Immovable out the right, sustain
		// at the junction.
		byCell.put(cell(3, 2), new Def(Family.LUNGE, 1));
		byCell.put(cell(2, 2), new Def(Family.LUNGE, 2));
		byCell.put(cell(1, 2), new Def(Family.LUNGE, 3));
		byCell.put(cell(4, 2), new Def(Family.VAMP, 1));
		byCell.put(cell(5, 2), new Def(Family.KBRES, 1));
		byCell.put(cell(6, 2), new Def(Family.KBRES, 2));
		byCell.put(cell(7, 2), new Def(Family.KBRES, 3));

		// Left blade edge: the sword path.
		byCell.put(cell(3, 3), new Def(Family.BLEED, 1));
		byCell.put(cell(3, 4), new Def(Family.BLEED, 2));
		byCell.put(cell(3, 5), new Def(Family.BLEED, 3));
		byCell.put(cell(3, 6), new Def(Family.VAMP, 2));
		byCell.put(cell(3, 7), new Def(Family.VAMP, 3));
		byCell.put(cell(3, 8), new Def(Family.FLURRY, 1));

		// Right blade edge: the claymore path.
		byCell.put(cell(5, 3), new Def(Family.HEAVY, 1));
		byCell.put(cell(5, 4), new Def(Family.HEAVY, 2));
		byCell.put(cell(5, 5), new Def(Family.HEAVY, 3));
		byCell.put(cell(5, 6), new Def(Family.FIRSTBLOOD, 1));
		byCell.put(cell(5, 7), new Def(Family.FIRSTBLOOD, 2));
		byCell.put(cell(5, 8), new Def(Family.EXECUTIONER, 1));

		// The tip: Bloodlust sits in the old X-crossing between the capstones,
		// so every crown path runs through it; Relentless caps the very point,
		// its only neighbours the capstones — post-capstone by geometry alone.
		byCell.put(cell(3, 9), new Def(Family.BLADESTORM, 1));
		byCell.put(cell(5, 9), new Def(Family.DECIMATE, 1));
		byCell.put(cell(4, 9), new Def(Family.BLOODLUST, 1));
		byCell.put(cell(4, 10), new Def(Family.RELENTLESS, 1));

		Map<Integer, Def> byIndex = new HashMap<>();
		var nodes = Constellations.SLAYER_SWORD.nodes();

		for (int i = 0; i < nodes.size(); i++) {
			Def def = byCell.get(cell(nodes.get(i).col(), nodes.get(i).row()));

			if (def != null) {
				byIndex.put(i, def);
			}
		}

		if (byIndex.size() != nodes.size()) {
			throw new IllegalStateException("Slayer sword has " + nodes.size()
					+ " nodes but " + byIndex.size() + " skill mappings — the grid and the map drifted");
		}

		return byIndex;
	}

	private static long cell(final int col, final int row) {
		return ((long) col << 32) | (row & 0xFFFFFFFFL);
	}

	public static Def def(final SubTree tree, final int nodeIndex) {
		return tree == SubTree.SLAYER ? BY_INDEX.getOrDefault(nodeIndex, MINOR_DEF) : MINOR_DEF;
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
