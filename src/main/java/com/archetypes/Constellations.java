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
	 * The Slayer constellation IS a sword: a two-column hilt of adjacent,
	 * weavable weapon-agnostic chains (both roots — Hamstring on the outside,
	 * Taste of Blood on the centre axis, diagonal to BOTH branch entries), a
	 * short quilloned guard where the weapon paths split, the two blade edges
	 * as the sword and greatsword paths (fuller empty between them), capstones
	 * flanking the tip with Bloodlust in the crossing.
	 */
	public static final Constellation SLAYER_SWORD = Constellation.of(
			"....#....",
			"...###...",
			"...#.#...",
			"...#.#...",
			"...#.#...",
			"...#.#...",
			"..##.##..",
			"...##....",
			"...##....",
			"...##....");
			// No explicit cross needed: Bloodlust sits in the crossing itself,
			// adjacent to both pre-capstones, both capstones, and the tip.

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
