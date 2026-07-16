package com.archetypes;

/**
 * The nine constellation layouts, one per sub-tree. Each grid is drawn the way
 * it appears on screen, so the shape can be judged and edited right here —
 * see {@link Constellation} for how they are parsed.
 *
 * <p>Strength's three trees are real; the agility and intellect grids are
 * placeholder silhouettes, but already at the final point economy — 23 nodes
 * each, matching the 15-point subtree cap and 45 total levels — so building
 * their skills later means naming nodes, not reshaping constellations.
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

	/**
	 * A strung bow in profile: the stave arcing out on the left, the string a
	 * straight column on the right, tips shared top and bottom, and a single
	 * nub on the stave's belly at mid-height — the arrow rest.
	 */
	public static final Constellation MARKSMAN_BOW = Constellation.of(
			"...#...",
			"..#.#..",
			".#..#..",
			"#...#..",
			"#...#..",
			"##..#..",
			"#...#..",
			"#...#..",
			"#...#..",
			".#..#..",
			"..#.#..",
			"...#...");

	/**
	 * A single dagger, point up: a broad blade tapering to the tip, a guard a
	 * step narrower than the Slayer sword's, and — the tell that this is a
	 * knife, not a sword — a handle nearly as long as the blade, capped by a
	 * spread pommel.
	 */
	public static final Constellation ASSASSIN_DAGGER = Constellation.of(
			"...#...",
			"..#.#..",
			"..#.#..",
			"..#.#..",
			"..#.#..",
			"..###..",
			".#####.",
			"...#...",
			"...#...",
			"...#...",
			"..###..");

	/**
	 * A crescent moon opening right: the outer arc sweeps wide, the inner arc
	 * hugs the hollow, and the two share their tips. The inner arc skips the
	 * row where the outer bows deepest — the explicit edge bridges it so the
	 * curve still draws unbroken.
	 */
	public static final Constellation SHADOW_MOON = Constellation.of(
			"......#",
			".....##",
			"....#.#",
			"...#.#.",
			"..#..#.",
			"..#..#.",
			".#.....",
			"..#..#.",
			"..#..#.",
			"...#.#.",
			"....#.#",
			".....##",
			"......#")
			.withEdge(5, 5, 5, 7);

	/**
	 * A flame — element-agnostic enough to carry the Elementalist: a wide
	 * unsteady body pulled tighter on the left than the right, narrowing to a
	 * two-node lick at the top.
	 */
	public static final Constellation ELEMENTALIST_FLAME = Constellation.of(
			"....#....",
			"....#....",
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

	/**
	 * A wizard's staff: a diamond headpiece cradling a loose orb low in its
	 * hollow, a lashed crossbar where head meets haft, and a flared cap at
	 * the butt.
	 */
	public static final Constellation WIZARD_STAFF = Constellation.of(
			"...#...",
			"..#.#..",
			".#...#.",
			"#.....#",
			".#.#.#.",
			"..#.#..",
			"...#...",
			"...#...",
			"..###..",
			"...#...",
			"...#...",
			"...#...",
			"..###..");

	/**
	 * The Apothecary's round-bottom flask: a corked lip, a pinched neck, and
	 * a fat globe of a body — the heart it replaced belonged to a healer;
	 * this tree brews.
	 */
	public static final Constellation APOTHECARY_FLASK = Constellation.of(
			"...###...",
			"....#....",
			"...#.#...",
			"..#...#..",
			".#.....#.",
			".#.....#.",
			"#.......#",
			"#.......#",
			".#.....#.",
			"..#####..");

}
