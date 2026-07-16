package com.archetypes;

/**
 * The nine constellation layouts, one per sub-tree. Each grid is drawn the way
 * it appears on screen, so the shape can be judged and edited right here —
 * see {@link Constellation} for how they are parsed.
 *
 * <p>All nodes are placeholders for now: no effects, no ranks. The shapes are
 * the point.
 */
public final class Constellations {
	private Constellations() {
	}

	/**
	 * Exactly 25 nodes — one per real Protector skill, no placeholders. Bash at
	 * the tip, utility up the left rim, damage up the right, Quick Recovery up
	 * the centre into the crown: Braced and Taunt side by side, cross-linked to
	 * both capstones above, so a build takes both pre-capstones or trades one
	 * for its capstone. The top edge closes with a decorative line.
	 */
	public static final Constellation PROTECTOR_SHIELD = Constellation.of(
			"#.......#",
			"#.......#",
			"#..#.#..#",
			"#..#.#..#",
			".#..#..#.",
			".#..#..#.",
			"..#.#.#..",
			"...###...",
			"....#....")
			// The capstone cross: Braced and Taunt each connect to both capstones.
			.withEdge(3, 5, 5, 6)
			.withEdge(5, 5, 3, 6)
			// Close the rim across the crown so the silhouette reads as a finished
			// shield: Reflection to Wide Swings II, cosmetic only.
			.withDecorativeEdge(0, 8, 8, 8);

	/**
	 * The Slayer constellation IS a greatsword, mirror-symmetric on a 10-wide
	 * grid: a centred two-column hilt of adjacent, weavable weapon-agnostic
	 * chains (both roots), a contiguous four-node guard — each hilt top reaches
	 * its own quillon plus BOTH inner branch entries — the two blade edges as
	 * the sword and greatsword paths (fuller empty between them), and a 2x2
	 * crown for the point: capstones below, Bloodlust and Relentless above,
	 * reachable only through a capstone. Blunt on purpose — the slab's bevel,
	 * same language as the item sprite.
	 */
	public static final Constellation SLAYER_SWORD = Constellation.of(
			"....##....",
			"....##....",
			"...#..#...",
			"...#..#...",
			"...#..#...",
			"...#..#...",
			"...####...",
			"....##....",
			"....##....",
			"....##....");

	public static final Constellation CRUSHER_MACE = Constellation.of(
			"#..#..#",
			".#.#.#.",
			"#######",
			"#.....#",
			"#.....#",
			"#######",
			".#.#.#.",
			"...#...",
			"...#...",
			"...#...",
			"...#...",
			"...#...");

	public static final Constellation MARKSMAN_BOW = Constellation.of(
			"...#...",
			"..#.#..",
			".#..#..",
			"#...#..",
			"#...#..",
			"#...#..",
			"#...#..",
			"#...#..",
			"#...#..",
			".#..#..",
			"..#.#..",
			"...#...");

	public static final Constellation ASSASSIN_DAGGERS = Constellation.of(
			"..#.....#..",
			"...#...#...",
			"....#.#....",
			"...#####...",
			"....#.#....",
			".....#.....",
			"....#.#....",
			"...#####...",
			"....#.#....",
			"...#...#...",
			"..##...##..");

	public static final Constellation SHADOW_CLOAK = Constellation.of(
			"...#...",
			"..#.#..",
			"..#.#..",
			".#...#.",
			".#...#.",
			"#.....#",
			"#.....#",
			"#.....#",
			"#.....#",
			"#######");

	public static final Constellation FIRE_MAGE_FLAME = Constellation.of(
			"....#....",
			"...##....",
			"...#.#...",
			"..#...#..",
			"..#....#.",
			".#.....#.",
			".#......#",
			"#.......#",
			"#.......#",
			".#.....#.",
			"..#...#..",
			"...###...");

	public static final Constellation WIZARD_STAFF = Constellation.of(
			"...#...",
			"..#.#..",
			".#...#.",
			"#.....#",
			".#...#.",
			"..#.#..",
			"...#...",
			"...#...",
			"..###..",
			"...#...",
			"...#...",
			"...#...",
			"...#...");

	public static final Constellation HEALER_HEART = Constellation.of(
			".###.###.",
			"#...#...#",
			"#.......#",
			"#.......#",
			".#.....#.",
			"..#...#..",
			"...#.#...",
			"....#....");

}
