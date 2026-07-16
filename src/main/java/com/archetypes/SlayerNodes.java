package com.archetypes;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * What each node of the Slayer constellation is. The constellation IS a sword:
 * a two-column hilt at the bottom holding the weapon-agnostic families — both
 * are roots, so the player enters through Hamstring or Taste of Blood as they
 * please — then the guard row where the weapon paths split (its quillons carry
 * each path's flavour single: Flurry left, First Blood right), then the two
 * blade edges — sword path up the left (gap-closing, crowd control), greatsword
 * path up the right (the oneshot fantasy) — with an empty fuller between them
 * and the capstone crown at the tip.
 */
public final class SlayerNodes {
	public enum Family {
		SLOWNESS(Identifier.withDefaultNamespace("textures/mob_effect/slowness.png"), 18),
		TASTE_OF_BLOOD(Archetypes.id("textures/node/taste_of_blood.png"), 16),
		LUNGE(() -> Items.RABBIT_FOOT),
		KBRES(() -> Items.OBSIDIAN),
		BLEED(Archetypes.id("textures/node/rend.png"), 16),
		HEAVY(() -> ModItems.IRON_GREATSWORD),
		FIRSTBLOOD(Archetypes.id("textures/node/first_blood.png"), 16),
		FLURRY(() -> Items.SUGAR),
		EXECUTIONER(Archetypes.id("textures/node/executioner.png"), 16),
		BLOODLUST(() -> Items.FERMENTED_SPIDER_EYE),
		RELENTLESS(() -> Items.CLOCK),
		BLADESTORM(Archetypes.id("textures/node/bladestorm.png"), 16),
		DECIMATE(Archetypes.id("textures/node/decimate.png"), 16),
		MINOR((Supplier<Item>) null);

		private final @Nullable Supplier<Item> icon;
		private final @Nullable Identifier sprite;
		private final int spriteSize;

		Family(final @Nullable Supplier<Item> icon) {
			this.icon = icon;
			this.sprite = null;
			this.spriteSize = 0;
		}

		/** For icons that exist as textures, not items — effect sprites and
		 * our own drawn node icons (see notes/art/make_node_icons.py). */
		Family(final Identifier sprite, final int spriteSize) {
			this.icon = null;
			this.sprite = sprite;
			this.spriteSize = spriteSize;
		}

		public @Nullable Item icon() {
			return this.icon == null ? null : this.icon.get();
		}

		public @Nullable Identifier sprite() {
			return this.sprite;
		}

		public int spriteSize() {
			return this.spriteSize;
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

		// The grip: Hamstring, the root — crowd control both weapons share.
		// Full path economics: grip 2 + blood 3 + branch 7 + capstone +
		// Bloodlust + Relentless = 15 exactly, either weapon.
		byCell.put(cell(4, 0), new Def(Family.SLOWNESS, 1));
		byCell.put(cell(4, 1), new Def(Family.SLOWNESS, 2));

		// The crossguard centre: Taste of Blood, the shared trio the grip
		// opens into. Rank I sits on the axis so the natural next buy is
		// always I; ranks fan out to the arms (rank is count-based, so buy
		// order never matters mechanically).
		byCell.put(cell(4, 2), new Def(Family.TASTE_OF_BLOOD, 1));
		byCell.put(cell(3, 2), new Def(Family.TASTE_OF_BLOOD, 2));
		byCell.put(cell(5, 2), new Def(Family.TASTE_OF_BLOOD, 3));

		// The crossguard arms: each weapon path opens here, its outermost
		// cell a quillon leaf you can grab on the way past, not a toll.
		byCell.put(cell(2, 2), new Def(Family.LUNGE, 1));
		byCell.put(cell(1, 2), new Def(Family.LUNGE, 2));
		byCell.put(cell(6, 2), new Def(Family.KBRES, 1));
		byCell.put(cell(7, 2), new Def(Family.FIRSTBLOOD, 1));

		// Left blade edge: the sword path, Flurry at the top like a false edge.
		byCell.put(cell(3, 3), new Def(Family.LUNGE, 3));
		byCell.put(cell(3, 4), new Def(Family.BLEED, 1));
		byCell.put(cell(3, 5), new Def(Family.BLEED, 2));
		byCell.put(cell(3, 6), new Def(Family.BLEED, 3));
		byCell.put(cell(3, 7), new Def(Family.FLURRY, 1));

		// Right blade edge: the greatsword path.
		byCell.put(cell(5, 3), new Def(Family.KBRES, 2));
		byCell.put(cell(5, 4), new Def(Family.HEAVY, 1));
		byCell.put(cell(5, 5), new Def(Family.HEAVY, 2));
		byCell.put(cell(5, 6), new Def(Family.HEAVY, 3));
		byCell.put(cell(5, 7), new Def(Family.EXECUTIONER, 1));

		// The tip: Bloodlust sits in the crossing between the capstones, so
		// every crown path runs through it; Relentless caps the very point.
		byCell.put(cell(3, 8), new Def(Family.BLADESTORM, 1));
		byCell.put(cell(5, 8), new Def(Family.DECIMATE, 1));
		byCell.put(cell(4, 8), new Def(Family.BLOODLUST, 1));
		byCell.put(cell(4, 9), new Def(Family.RELENTLESS, 1));

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
