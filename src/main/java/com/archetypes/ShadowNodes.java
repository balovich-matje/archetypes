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
 * What each node of the Shadow constellation is (user sketch,
 * new-shadow-20260717). The crescent's two arcs are the two ways to use the
 * dark: the outer endures it — speed, mending, a dimmer presence, a
 * cleansing cast, Last Shadow at its top — the inner kills in it —
 * stillness, ambush damage, Bloodrush's strength, Reaper's feeding, ghost
 * armor, Predator at its top. Both tips are three-cell rows: the senses
 * beside the active at the bottom, Umbral Mastery between the capstones at
 * the crown.
 */
public final class ShadowNodes {
	public enum Family {
		/** The drawn icon: the bad-omen face, eyes turned glowing orange
		 * (user concept; notes/art/make_node_icons.py). */
		INVISIBILITY(Archetypes.id("textures/node/invisibility.png"), 18),
		/** Night Vision while sneaking, held above the flicker threshold. */
		NIGHT_EYES(() -> Items.GOLDEN_CARROT),
		/** Hostiles near you glow while you sneak or hide. */
		UMBRAL_SIGHT(() -> Items.GLOW_INK_SAC),
		/** +20% move speed per rank while invisible. */
		SWIFT_SHADOW(() -> Items.SUGAR),
		/** A heart every 8/6/4/2 seconds while invisible. */
		DARK_MENDING(() -> Items.GLISTERING_MELON_SLICE),
		/** Mobs notice you 15% per rank less, hidden or not. */
		DIM_PRESENCE(() -> Items.PHANTOM_MEMBRANE),
		/** Casting Invisibility scrubs your ailments off. */
		CLEANSING_VEIL(() -> Items.MILK_BUCKET),
		/** Standing still slows the invisibility timer, to a full stop. */
		STILLNESS(() -> Items.CLOCK),
		/** Melee from invisibility hits +30% per rank harder. */
		FIRST_STRIKE(() -> Items.IRON_SWORD),
		/** Kills quicken your hands for a few seconds. */
		BLOODRUSH(() -> Items.REDSTONE),
		/** Kills while invisible feed you health. */
		REAPER(() -> Items.WITHER_ROSE),
		/** Your armor vanishes with you. */
		GHOST_ARMOR(() -> Items.CHAINMAIL_CHESTPLATE),
		/** Capstones: the escape and the hunt. */
		LAST_SHADOW(() -> Items.TOTEM_OF_UNDYING),
		PREDATOR(() -> Items.SKELETON_SKULL),
		/** The crown. Currently a pickable placeholder — its first two ideas
		 * (longer invisibility; kill-refreshes-cooldown) fell to Stillness
		 * and to Predator-overlap/Last Shadow abuse respectively. */
		UMBRAL_MASTERY(() -> Items.ENDER_EYE),
		MINOR((Supplier<Item>) null);

		private final @Nullable Supplier<Item> icon;
		private final @Nullable Identifier sprite;
		private final int spriteSize;

		Family(final @Nullable Supplier<Item> icon) {
			this.icon = icon;
			this.sprite = null;
			this.spriteSize = 0;
		}

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
			return "node.archetypes.shadow." + this.name().toLowerCase(Locale.ROOT);
		}

		public String descriptionKey() {
			return this.nameKey() + ".desc";
		}
	}

	public record Def(Family family, int rank) {
	}

	private static final Map<Integer, Def> BY_INDEX = build();

	private ShadowNodes() {
	}

	private static Map<Integer, Def> build() {
		Map<Long, Def> byCell = new HashMap<>();

		// The bottom tip row: the senses beside the active.
		byCell.put(cell(6, 0), new Def(Family.INVISIBILITY, 1));
		byCell.put(cell(5, 0), new Def(Family.UMBRAL_SIGHT, 1));
		byCell.put(cell(4, 0), new Def(Family.NIGHT_EYES, 1));

		// Outer arc, bottom-up: surviving the dark, up to Last Shadow.
		byCell.put(cell(3, 1), new Def(Family.SWIFT_SHADOW, 1));
		byCell.put(cell(2, 2), new Def(Family.SWIFT_SHADOW, 2));
		byCell.put(cell(1, 3), new Def(Family.DARK_MENDING, 1));
		byCell.put(cell(1, 4), new Def(Family.DARK_MENDING, 2));
		byCell.put(cell(0, 5), new Def(Family.DARK_MENDING, 3));
		byCell.put(cell(1, 6), new Def(Family.DARK_MENDING, 4));
		byCell.put(cell(1, 7), new Def(Family.DIM_PRESENCE, 1));
		byCell.put(cell(2, 8), new Def(Family.DIM_PRESENCE, 2));
		byCell.put(cell(3, 9), new Def(Family.CLEANSING_VEIL, 1));
		byCell.put(cell(4, 10), new Def(Family.LAST_SHADOW, 1));

		// Inner arc, bottom-up: killing in it, up to Predator. The explicit
		// constellation edge bridges First Strike II to Bloodrush I.
		byCell.put(cell(5, 1), new Def(Family.STILLNESS, 1));
		byCell.put(cell(4, 2), new Def(Family.STILLNESS, 2));
		byCell.put(cell(4, 3), new Def(Family.FIRST_STRIKE, 1));
		byCell.put(cell(4, 4), new Def(Family.FIRST_STRIKE, 2));
		byCell.put(cell(4, 6), new Def(Family.BLOODRUSH, 1));
		byCell.put(cell(4, 7), new Def(Family.BLOODRUSH, 2));
		byCell.put(cell(4, 8), new Def(Family.REAPER, 1));
		byCell.put(cell(5, 9), new Def(Family.GHOST_ARMOR, 1));
		byCell.put(cell(6, 10), new Def(Family.PREDATOR, 1));

		// The crown row: Umbral Mastery between the capstones, touching both.
		byCell.put(cell(5, 10), new Def(Family.UMBRAL_MASTERY, 1));

		Map<Integer, Def> byIndex = new HashMap<>();
		var nodes = Constellations.SHADOW_MOON.nodes();

		for (int i = 0; i < nodes.size(); i++) {
			Def def = byCell.get(cell(nodes.get(i).col(), nodes.get(i).row()));

			if (def != null) {
				byIndex.put(i, def);
			}
		}

		if (byIndex.size() != nodes.size()) {
			throw new IllegalStateException("Shadow moon has " + nodes.size()
					+ " nodes but " + byIndex.size() + " skill mappings — the grid and the map drifted");
		}

		return byIndex;
	}

	private static long cell(final int col, final int row) {
		return ((long) col << 32) | (row & 0xFFFFFFFFL);
	}

	public static Def def(final SubTree tree, final int nodeIndex) {
		return tree == SubTree.SHADOW
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
