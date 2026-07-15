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

	public static final Constellation PROTECTOR_SHIELD = Constellation.of(
			"#########",
			"#.......#",
			"#...#...#",
			"#..###..#",
			"#...#...#",
			"#...#...#",
			".#..#..#.",
			".#..#..#.",
			"..#.#.#..",
			"...###...",
			"....#....");

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
