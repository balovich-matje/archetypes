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
	 * A bow at rest (user sketch, markman-new-20260717): the string dead
	 * straight down the right — the crossbow branch, root to Snap Shot —
	 * the stave arcing left as the bow branch up to Seeker Arrow, tips
	 * shared (True Shot bottom, Focus top, touching both capstones), and
	 * the Conservation arrow crossing at the bulge, bridging the branches.
	 */
	public static final Constellation MARKSMAN_BOW = Constellation.of(
			"....#",
			"...##",
			"..#.#",
			".#..#",
			"#...#",
			"#####",
			"#...#",
			".#..#",
			"..#.#",
			"...##",
			"....#");

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
	 * A crescent moon opening right (user sketch, new-shadow-20260717):
	 * three shared cells at each tip — the senses row at the bottom with
	 * the active, the crown row at the top with both capstones flanking
	 * Umbral Mastery — the outer arc sweeping wide, the inner hugging the
	 * hollow.
	 */
	public static final Constellation SHADOW_MOON = Constellation.of(
			"....###",
			"...#.#.",
			"..#.#..",
			".#..#..",
			".#..#..",
			"#......",
			".#..#..",
			".#..#..",
			"..#.#..",
			"...#.#.",
			"....###")
			// The inner arc skips a row where the outer bows deepest — the
			// explicit edge keeps the curve buyable across the gap.
			.withEdge(4, 4, 4, 6);

	/**
	 * A flame with a forked crown, 26 nodes (user sketch,
	 * elementalist-new-20260717): fire up the left edge, ice up the right,
	 * the two starting spells at the base with Focused Mind's four ranks
	 * rising as the flame's core. The capstone tongue row sits shifted so
	 * each branch top touches both of its own capstones naturally — the
	 * explicit Permafrost edge died here — and every capstone touches the
	 * crown pair.
	 */
	public static final Constellation ELEMENTALIST_FLAME = Constellation.of(
			"....##...",
			"...####..",
			"...#..#..",
			"..#....#.",
			".#.....#.",
			"#.......#",
			"#....#..#",
			".#..#..#.",
			"..#.#.#..",
			"...###...");

	/**
	 * The wizard's staff (user sketches, Wizard-redisign then
	 * new-edits-for-wizard, both 2026-07-17): a long plain haft (root,
	 * Force, Range), a two-row grip block — Flow, the single Mana Shield
	 * and Arcane Orb, Mind Well — Siphon alone in the diamond's heart,
	 * capstones at the head's side points, and the crown arc (Clarity,
	 * Velocity, Echo, Concussion) closing over the top. 22 nodes: the
	 * second Mana Shield died in the second sketch.
	 */
	public static final Constellation WIZARD_STAFF = Constellation.of(
			"...#...",
			"..#.#..",
			".#...#.",
			"#.....#",
			".#.#.#.",
			"..###..",
			"..###..",
			"...#...",
			"...#...",
			"...#...",
			"...#...",
			"...#...",
			"...#...");

	/**
	 * The Priest's ankh: a five-wide loop closing onto the crossbar's centre,
	 * arms reaching one node past the loop on each side, and a shaft as long
	 * as the loop is tall. Where loop, arms and shaft meet, the junction node
	 * touches five neighbours — the knot the whole symbol hangs from.
	 */
	public static final Constellation PRIEST_ANKH = Constellation.of(
			"..###..",
			".#...#.",
			".#...#.",
			"..#.#..",
			"...#...",
			"#######",
			"...#...",
			"...#...",
			"...#...",
			"...#...",
			"...#...",
			"...#...");

}
