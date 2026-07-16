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
	 * The Slayer constellation IS a sword, the classic silhouette: a single
	 * grip column at the bottom (Hamstring, the root), a wide 7-node
	 * crossguard whose centre trio is Taste of Blood — shared, feeding both
	 * arms — with each arm opening a weapon path (its outermost cell a
	 * quillon leaf), the two blade edges rising close and parallel with the
	 * fuller empty between them, and the capstone crown at the single tip.
	 */
	public static final Constellation SLAYER_SWORD = Constellation.of(
			"....#....",
			"...###...",
			"...#.#...",
			"...#.#...",
			"...#.#...",
			"...#.#...",
			"...#.#...",
			".#######.",
			"....#....",
			"....#....");
			// No explicit cross needed: Bloodlust sits in the crossing itself,
			// adjacent to both pre-capstones, both capstones, and the tip.

	/**
	 * The Crusher constellation IS a mace: a four-node shared handle (the
	 * weapon-agnostic bruiser passives, root at the pommel), a top-heavy head
	 * whose left flange is the bare-fists path and right flange the mace path
	 * (eight nodes each, capstone at each flange's peak), and a three-node
	 * crown across the top — Battle Trance, shared, fed by either capstone.
	 */
	public static final Constellation CRUSHER_MACE = Constellation.of(
			"...###...",
			"..##.##..",
			"..##.##..",
			"..##.##..",
			"...#.#...",
			"...#.#...",
			"....#....",
			"....#....",
			"....#....",
			"....#....");

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
