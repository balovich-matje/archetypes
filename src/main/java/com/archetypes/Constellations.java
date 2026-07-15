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
	 * Exactly 23 nodes — one per real Protector skill, no placeholders. Bash at
	 * the tip, utility up the left rim, damage up the right, Shield Rush up the
	 * centre with the two capstones flanking its top. The top edge is open; the
	 * crown cross keeps the shield readable.
	 */
	public static final Constellation PROTECTOR_SHIELD = Constellation.of(
			"#.......#",
			"#.......#",
			"#.......#",
			"#..#.#..#",
			".#..#..#.",
			".#..#..#.",
			"..#.#.#..",
			"...###...",
			"....#....")
			// Close the rim across the crown so the silhouette reads as a finished
			// shield: Reflection to Wide Swings II, cosmetic only.
			.withDecorativeEdge(0, 8, 8, 8);

	public static final Constellation SLAYER_SWORD = Constellation.of(
			"...#...",
			"..#.#..",
			"..#.#..",
			"..#.#..",
			"..#.#..",
			"..#.#..",
			"..#.#..",
			"#######",
			"...#...",
			"...#...",
			"..###..",
			"...#...");

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
