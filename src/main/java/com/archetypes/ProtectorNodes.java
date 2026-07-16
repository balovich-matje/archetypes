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
 * What each node of the Protector constellation actually is.
 *
 * <p>Skills sit on the shield by grid coordinate: Shield Bash at the bottom
 * tip, the utility path up the left rim, the damage path up the right, Shield
 * Rush up the centre with the two mutually-exclusive capstones flanking its
 * top. Every node is a real skill — the placeholder minors were scrapped and
 * the constellation redrawn so every node is real.
 *
 * <p>Multi-rank skills are chains of nodes; a family's rank is simply how many
 * of its nodes are owned, and the chain's adjacency enforces buy order.
 */
public final class ProtectorNodes {
	public enum Family {
		BASH(() -> Items.SHIELD, Archetypes.id("textures/node/bash_overlay.png"), 32),
		SLAM(() -> Items.SHIELD, Archetypes.id("textures/node/shield_slam_overlay.png"), 32, true),
		COOLDOWN(() -> Items.CLOCK),
		KNOCKBACK(() -> Items.PISTON),
		WIDE(() -> Items.SHIELD, Archetypes.id("textures/node/wide_swings_overlay.png"), 32),
		UNBREAKING(() -> Items.LEATHER),
		SPIKES(() -> Items.SHIELD, Archetypes.id("textures/node/iron_spikes_overlay.png"), 32),
		RUSH(() -> Items.WIND_CHARGE),
		BRACED(() -> Items.SHIELD, Archetypes.id("textures/node/braced_overlay.png"), 32),
		REFLECT(() -> Items.SHIELD, Archetypes.id("textures/node/reflection_overlay.png"), 32),
		TAUNT(() -> Items.GOAT_HORN),
		OMNI_BLOCK(() -> Items.SHIELD, Archetypes.id("textures/node/bulwark_overlay.png"), 32),
		GROUND_SLAM(() -> Items.ANVIL, Archetypes.id("textures/node/ground_slam_overlay.png"), 32),
		MINOR(null);

		private final @Nullable Supplier<Item> icon;
		private final net.minecraft.resources.@Nullable Identifier overlay;
		private final int overlaySize;
		private final boolean overlayBehind;

		Family(final @Nullable Supplier<Item> icon) {
			this(icon, null, 0);
		}

		/** The real item render stays the base; the overlay draws the skill's
		 * effect on top of it — see notes/art/make_node_icons.py. */
		Family(final @Nullable Supplier<Item> icon,
				final net.minecraft.resources.@Nullable Identifier overlay, final int overlaySize) {
			this(icon, overlay, overlaySize, false);
		}

		/** behind = the effect layer draws first, so the item covers it —
		 * "peeking out from behind the shield". */
		Family(final @Nullable Supplier<Item> icon,
				final net.minecraft.resources.@Nullable Identifier overlay, final int overlaySize,
				final boolean overlayBehind) {
			this.icon = icon;
			this.overlay = overlay;
			this.overlaySize = overlaySize;
			this.overlayBehind = overlayBehind;
		}

		public @Nullable Item icon() {
			return this.icon == null ? null : this.icon.get();
		}

		public net.minecraft.resources.@Nullable Identifier overlay() {
			return this.overlay;
		}

		public int overlaySize() {
			return this.overlaySize;
		}

		public boolean overlayBehind() {
			return this.overlayBehind;
		}

		public String nameKey() {
			return "node.archetypes.protector." + this.name().toLowerCase(Locale.ROOT);
		}

		public String descriptionKey() {
			return this.nameKey() + ".desc";
		}
	}

	public record Def(Family family, int rank) {
	}

	private static final Def MINOR_DEF = new Def(Family.MINOR, 1);
	/** Node index in the constellation -> definition. */
	private static final Map<Integer, Def> BY_INDEX = build();

	private ProtectorNodes() {
	}

	private static Map<Integer, Def> build() {
		// (col, row) with row 0 at the bottom, exactly as Constellation parses.
		Map<Long, Def> byCell = new HashMap<>();
		byCell.put(cell(4, 0), new Def(Family.BASH, 1));

		// Left rim, bottom-up: the utility path. Shield Rush lives here — Quick
		// Recovery moved to the centre column, since everyone wants it anyway.
		byCell.put(cell(3, 1), new Def(Family.RUSH, 1));
		byCell.put(cell(2, 2), new Def(Family.RUSH, 2));
		byCell.put(cell(1, 3), new Def(Family.RUSH, 3));
		byCell.put(cell(1, 4), new Def(Family.KNOCKBACK, 1));
		byCell.put(cell(0, 5), new Def(Family.KNOCKBACK, 2));
		byCell.put(cell(0, 6), new Def(Family.KNOCKBACK, 3));
		byCell.put(cell(0, 7), new Def(Family.UNBREAKING, 1));
		byCell.put(cell(0, 8), new Def(Family.REFLECT, 1));

		// Right rim, bottom-up: the damage path.
		byCell.put(cell(5, 1), new Def(Family.SLAM, 1));
		byCell.put(cell(6, 2), new Def(Family.SLAM, 2));
		byCell.put(cell(7, 3), new Def(Family.SLAM, 3));
		byCell.put(cell(7, 4), new Def(Family.SPIKES, 1));
		byCell.put(cell(8, 5), new Def(Family.SPIKES, 2));
		byCell.put(cell(8, 6), new Def(Family.SPIKES, 3));
		byCell.put(cell(8, 7), new Def(Family.WIDE, 1));
		byCell.put(cell(8, 8), new Def(Family.WIDE, 2));

		// Centre column: Quick Recovery — the every-build pick — then the
		// capstones flanking its top, Omni-block left, Ground Slam right,
		// mutually exclusive at purchase.
		byCell.put(cell(4, 1), new Def(Family.COOLDOWN, 1));
		byCell.put(cell(4, 2), new Def(Family.COOLDOWN, 2));
		byCell.put(cell(4, 3), new Def(Family.COOLDOWN, 3));
		byCell.put(cell(4, 4), new Def(Family.COOLDOWN, 4));
		// The crown: Braced and Taunt side by side above Recovery, each
		// cross-linked to both capstones (explicit edges in Constellations).
		byCell.put(cell(3, 5), new Def(Family.BRACED, 1));
		byCell.put(cell(5, 5), new Def(Family.TAUNT, 1));
		byCell.put(cell(3, 6), new Def(Family.OMNI_BLOCK, 1));
		byCell.put(cell(5, 6), new Def(Family.GROUND_SLAM, 1));

		Map<Integer, Def> byIndex = new HashMap<>();
		var nodes = Constellations.PROTECTOR_SHIELD.nodes();

		for (int i = 0; i < nodes.size(); i++) {
			Def def = byCell.get(cell(nodes.get(i).col(), nodes.get(i).row()));

			if (def != null) {
				byIndex.put(i, def);
			}
		}

		if (byIndex.size() != nodes.size()) {
			throw new IllegalStateException("Protector shield has " + nodes.size()
					+ " nodes but " + byIndex.size() + " skill mappings — the grid and the map drifted");
		}

		return byIndex;
	}

	private static long cell(final int col, final int row) {
		return ((long) col << 32) | (row & 0xFFFFFFFFL);
	}

	/** Only the Protector tree has real definitions so far. */
	public static Def def(final SubTree tree, final int nodeIndex) {
		return tree == SubTree.PROTECTOR ? BY_INDEX.getOrDefault(nodeIndex, MINOR_DEF) : MINOR_DEF;
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
