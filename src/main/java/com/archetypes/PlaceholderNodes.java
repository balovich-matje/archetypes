package com.archetypes;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Node tables for the six Cutpurse/Seeker trees while their passives are being
 * written: each tree has its ACTIVE at the constellation's root, a mutually
 * exclusive capstone pair near the crown, and pickable-but-inert MINOR
 * placeholders everywhere else. When a tree's passives land it graduates to
 * its own class (the SlayerNodes pattern) and leaves this one.
 */
public final class PlaceholderNodes {
	public enum Kind {
		ACTIVE, CAPSTONE_A, CAPSTONE_B, MINOR
	}

	/**
	 * One tree's three real nodes: name segments (lang keys hang off them),
	 * icons, and grid cells in (col, row-from-bottom) form. Cells are resolved
	 * against the constellation at boot and drift throws immediately.
	 */
	private record Spec(String active, String capA, String capB,
			Supplier<Item> activeIcon, Supplier<Item> capAIcon, Supplier<Item> capBIcon,
			int activeCol, int activeRow, int capACol, int capARow, int capBCol, int capBRow) {
	}

	private static final Map<SubTree, Spec> SPECS = new EnumMap<>(Map.of(
			SubTree.MARKSMAN, new Spec("true_shot", "seeker_arrow", "snap_shot",
					() -> Items.SPECTRAL_ARROW, () -> Items.ENDER_EYE, () -> Items.CROSSBOW,
					3, 0, 2, 10, 4, 10),
			SubTree.ASSASSIN, new Spec("shadow_step", "shadow_flurry", "momentum",
					() -> Items.ENDER_PEARL, () -> ModItems.IRON_DAGGER, () -> Items.WITHER_SKELETON_SKULL,
					3, 0, 2, 9, 4, 9),
			SubTree.SHADOW, new Spec("invisibility", "predator", "last_shadow",
					() -> Items.FERMENTED_SPIDER_EYE, () -> Items.SKELETON_SKULL, () -> Items.TOTEM_OF_UNDYING,
					6, 0, 5, 11, 6, 11),
			SubTree.ELEMENTALIST, new Spec("fireball", "meteorite", "flamethrower",
					() -> Items.FIRE_CHARGE, () -> Items.MAGMA_BLOCK, () -> Items.BLAZE_ROD,
					4, 0, 3, 9, 5, 9),
			SubTree.WIZARD, new Spec("magic_missile", "seeker_missile", "lance",
					() -> Items.AMETHYST_SHARD, () -> Items.ENDER_EYE, () -> Items.END_ROD,
					3, 0, 0, 9, 6, 9),
			SubTree.PRIEST, new Spec("holy_light", "renewal", "benediction",
					() -> Items.GLOWSTONE_DUST, () -> Items.GHAST_TEAR, () -> Items.GLISTERING_MELON_SLICE,
					3, 0, 0, 6, 6, 6)));

	/** Per tree: node index -> kind, for the three real nodes. */
	private static final Map<SubTree, Map<Integer, Kind>> BY_INDEX = build();

	private PlaceholderNodes() {
	}

	private static Map<SubTree, Map<Integer, Kind>> build() {
		Map<SubTree, Map<Integer, Kind>> all = new EnumMap<>(SubTree.class);

		SPECS.forEach((tree, spec) -> all.put(tree, Map.of(
				indexOf(tree, spec.activeCol(), spec.activeRow()), Kind.ACTIVE,
				indexOf(tree, spec.capACol(), spec.capARow()), Kind.CAPSTONE_A,
				indexOf(tree, spec.capBCol(), spec.capBRow()), Kind.CAPSTONE_B)));

		return all;
	}

	private static int indexOf(final SubTree tree, final int col, final int row) {
		var nodes = tree.constellation().nodes();

		for (int i = 0; i < nodes.size(); i++) {
			if (nodes.get(i).col() == col && nodes.get(i).row() == row) {
				return i;
			}
		}

		throw new IllegalStateException(tree.id() + " has no node at (" + col + ", " + row
				+ ") — the grid and the placeholder spec drifted");
	}

	public static Kind kind(final SubTree tree, final int index) {
		Map<Integer, Kind> map = BY_INDEX.get(tree);
		return map == null ? Kind.MINOR : map.getOrDefault(index, Kind.MINOR);
	}

	public static String nameKey(final SubTree tree, final int index) {
		Spec spec = SPECS.get(tree);

		return "node.archetypes." + tree.id() + "." + switch (kind(tree, index)) {
			case ACTIVE -> spec.active();
			case CAPSTONE_A -> spec.capA();
			case CAPSTONE_B -> spec.capB();
			case MINOR -> "minor";
		};
	}

	public static String descriptionKey(final SubTree tree, final int index) {
		return nameKey(tree, index) + ".desc";
	}

	public static @Nullable Item icon(final SubTree tree, final int index) {
		Spec spec = SPECS.get(tree);

		return switch (kind(tree, index)) {
			case ACTIVE -> spec.activeIcon().get();
			case CAPSTONE_A -> spec.capAIcon().get();
			case CAPSTONE_B -> spec.capBIcon().get();
			case MINOR -> null;
		};
	}

	/** Whether the player owns a tree's node of this kind. */
	public static boolean owns(final SubTree tree, final Set<Integer> owned, final Kind kind) {
		Map<Integer, Kind> map = BY_INDEX.get(tree);

		if (map == null) {
			return false;
		}

		for (Map.Entry<Integer, Kind> entry : map.entrySet()) {
			if (entry.getValue() == kind && owned.contains(entry.getKey())) {
				return true;
			}
		}

		return false;
	}

	/** The capstone pair is a choice: owning one locks the other. */
	public static boolean exclusiveTaken(final SubTree tree, final Set<Integer> owned, final int index) {
		return switch (kind(tree, index)) {
			case CAPSTONE_A -> owns(tree, owned, Kind.CAPSTONE_B);
			case CAPSTONE_B -> owns(tree, owned, Kind.CAPSTONE_A);
			default -> false;
		};
	}

	public static String kindName(final Kind kind) {
		return kind.name().toLowerCase(Locale.ROOT);
	}
}
