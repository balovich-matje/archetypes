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
 * What each node of the Priest constellation is. The ankh's shaft is the
 * calling itself — Lumen's power, Grace's economy, Radiance's spread,
 * Devotion's flow — meeting the crossbar at Fervent Cast. The arms are the
 * two ministries: Mercy heals harder toward Renewal on the left, Wrath
 * burns the undead toward Benediction on the right. The loop above is the
 * halo (per the user's sketch): protection up the left arc — Aegis for the
 * caster, Sanctuary for friends — wrath up the right — Immolation's fire
 * and Judgement's weakness on the undead — with the Ascendant at the top.
 */
public final class PriestNodes {
	public enum Family {
		HOLY_LIGHT(() -> Items.GLOWSTONE_DUST),
		/** +1 to both the heal and the undead damage, per rank. */
		LUMEN(() -> Items.GLOWSTONE),
		/** Holy Light costs 10 less mana. */
		GRACE(() -> Items.SUGAR),
		/** The burst reaches 1.5 blocks further. */
		RADIANCE(() -> Items.SEA_LANTERN),
		/** +0.5 mana regeneration per second. */
		DEVOTION(() -> Items.LAPIS_LAZULI),
		/** The lob flies half again faster and flatter. */
		FERVENT_CAST(() -> Items.FEATHER),
		/** Mercy: +2 healing per rank, the heal side only. */
		MERCY(() -> Items.GLISTERING_MELON_SLICE),
		/** Wrath: +2 undead damage per rank, the harm side only. */
		WRATH(() -> Items.BLAZE_POWDER),
		/** Capstones: the two ministries' crowns. */
		RENEWAL(() -> Items.GHAST_TEAR),
		BENEDICTION(() -> Items.GOLDEN_APPLE),
		/** The halo: reachable through the junction, capstone or not — the
		 * protective arm on the left, the wrathful on the right. */
		BEACON(() -> Items.CONDUIT),
		/** Holy Light sets undead ablaze, 3/6 seconds. */
		IMMOLATION(() -> Items.SOUL_TORCH),
		/** Casting shells YOU in 1/2 absorption hearts. */
		AEGIS(() -> Items.SHIELD),
		/** Casting shells friendly targets in 1/2 absorption hearts. */
		SANCTUARY(() -> Items.ENCHANTED_GOLDEN_APPLE),
		/** Holy Light lays Weakness I/II on the undead. */
		JUDGEMENT(() -> Items.FERMENTED_SPIDER_EYE),
		ASCENDANT(() -> Items.END_CRYSTAL),
		MINOR((Supplier<Item>) null);

		private final @Nullable Supplier<Item> icon;

		Family(final @Nullable Supplier<Item> icon) {
			this.icon = icon;
		}

		public @Nullable Item icon() {
			return this.icon == null ? null : this.icon.get();
		}

		public String nameKey() {
			return "node.archetypes.priest." + this.name().toLowerCase(Locale.ROOT);
		}

		public String descriptionKey() {
			return this.nameKey() + ".desc";
		}
	}

	public record Def(Family family, int rank) {
	}

	private static final Map<Integer, Def> BY_INDEX = build();

	private PriestNodes() {
	}

	private static Map<Integer, Def> build() {
		Map<Long, Def> byCell = new HashMap<>();

		// The shaft, bottom-up: the calling.
		byCell.put(cell(3, 0), new Def(Family.HOLY_LIGHT, 1));
		byCell.put(cell(3, 1), new Def(Family.LUMEN, 1));
		byCell.put(cell(3, 2), new Def(Family.LUMEN, 2));
		byCell.put(cell(3, 3), new Def(Family.GRACE, 1));
		byCell.put(cell(3, 4), new Def(Family.RADIANCE, 1));
		byCell.put(cell(3, 5), new Def(Family.DEVOTION, 1));

		// The crossbar: Fervent Cast at the junction, Mercy's arm to
		// Renewal on the left, Wrath's arm to Benediction on the right.
		byCell.put(cell(3, 6), new Def(Family.FERVENT_CAST, 1));
		byCell.put(cell(2, 6), new Def(Family.MERCY, 1));
		byCell.put(cell(1, 6), new Def(Family.MERCY, 2));
		byCell.put(cell(0, 6), new Def(Family.RENEWAL, 1));
		byCell.put(cell(4, 6), new Def(Family.WRATH, 1));
		byCell.put(cell(5, 6), new Def(Family.WRATH, 2));
		byCell.put(cell(6, 6), new Def(Family.BENEDICTION, 1));

		// The halo (user sketch, priest-edits-20260717): protection climbs
		// the left arc, wrath the right, the Ascendant at the circle's top.
		byCell.put(cell(3, 7), new Def(Family.BEACON, 1));
		byCell.put(cell(2, 8), new Def(Family.AEGIS, 1));
		byCell.put(cell(1, 9), new Def(Family.AEGIS, 2));
		byCell.put(cell(1, 10), new Def(Family.SANCTUARY, 1));
		byCell.put(cell(2, 11), new Def(Family.SANCTUARY, 2));
		byCell.put(cell(4, 8), new Def(Family.IMMOLATION, 1));
		byCell.put(cell(5, 9), new Def(Family.IMMOLATION, 2));
		byCell.put(cell(5, 10), new Def(Family.JUDGEMENT, 1));
		byCell.put(cell(4, 11), new Def(Family.JUDGEMENT, 2));
		byCell.put(cell(3, 11), new Def(Family.ASCENDANT, 1));

		Map<Integer, Def> byIndex = new HashMap<>();
		var nodes = Constellations.PRIEST_ANKH.nodes();

		for (int i = 0; i < nodes.size(); i++) {
			Def def = byCell.get(cell(nodes.get(i).col(), nodes.get(i).row()));

			if (def != null) {
				byIndex.put(i, def);
			}
		}

		if (byIndex.size() != nodes.size()) {
			throw new IllegalStateException("Priest ankh has " + nodes.size()
					+ " nodes but " + byIndex.size() + " skill mappings — the grid and the map drifted");
		}

		return byIndex;
	}

	private static long cell(final int col, final int row) {
		return ((long) col << 32) | (row & 0xFFFFFFFFL);
	}

	public static Def def(final SubTree tree, final int nodeIndex) {
		return tree == SubTree.PRIEST
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
