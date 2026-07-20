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
	 * The Crusher constellation IS a mace (user sketch,
	 * crusher-rework-20260718): Bare-Knuckle's four dual-purpose ranks up the
	 * handle (day-one value, fists or mace), a neck of Sunder and Meteor, a
	 * top-heavy head whose left flange is the bare-fists path (Iron Skin,
	 * Clinch, Haymaker) and right flange the mace path (Shockwave, Earth
	 * Shatterer, Quake), and Battle Trance's crown across the top.
	 */
	public static final Constellation CRUSHER_MACE = Constellation.of(
			".###.",
			"##.##",
			"##.##",
			"##.##",
			".#.#.",
			".#.#.",
			"..#..",
			"..#..",
			"..#..",
			"..#..");

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
	 * A single dagger, point up (user sketch, assassin-rebalance-20260717):
	 * a slim two-edged blade over a five-wide guard, a handle nearly as
	 * long as the blade, a spread pommel — narrower than before, so the
	 * silhouette reads knife at a glance.
	 */
	public static final Constellation ASSASSIN_DAGGER = Constellation.of(
			"..#..",
			".#.#.",
			".#.#.",
			".#.#.",
			".#.#.",
			".###.",
			"#####",
			"..#..",
			"..#..",
			"..#..",
			".###.");

	/**
	 * A crescent moon opening right (user sketch, shadow-new-edits-20260717):
	 * three shared cells at each tip — the senses row at the bottom with
	 * the active, the crown row at the top with both capstones flanking
	 * Night Stalker — the outer arc sweeping wide, the inner hugging the
	 * hollow.
	 */
	public static final Constellation SHADOW_MOON = Constellation.of(
			"....###",
			"...#..#",
			"..#..#.",
			".#..#..",
			"#...#..",
			"#......",
			"#...#..",
			".#..#..",
			"..#..#.",
			"...#..#",
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

	/**
	 * Oracle Elementalist (epic, draft oracle-elementalist): Lightning Strike
	 * is the lone root, three columns forking off it — the chain-reaction ladder
	 * up the left, the recurrence ("never strikes once") ladder up the right, and
	 * the Oracle's mana line (Wisdom then Focus) up the centre — each column
	 * capped by a payoff node (Tempest, Overcharge). The root sits a row below
	 * the columns, so three explicit edges bridge the gap to each ladder's foot.
	 */
	public static final Constellation ORACLE_ELEMENTALIST = Constellation.of(
			"#.#.#",
			"#.#.#",
			"#.#.#",
			"#.#.#",
			".....",
			"..#..")
			.withEdge(2, 0, 0, 2)
			.withEdge(2, 0, 4, 2)
			.withEdge(2, 0, 2, 2);

	/**
	 * Oracle Wizard (epic, user sketch oracle-wizard-second-20260720): a trunk
	 * of Magic Armaments and the two Magic Armor rungs, splitting at its head
	 * into three ways to spend the channel — Gliding into Warding on the left,
	 * Mind over Matter into Blink up the centre, Spellbow into Mana Siphon on
	 * the right. Row 3 is deliberately empty, so all three branches leave the
	 * trunk on the explicit edges the sketch records rather than by grid
	 * adjacency. The sketch's other three lines (each branch's own pair) are
	 * already grid-diagonal and are NOT repeated here — a second edge between
	 * the same two nodes would draw the line twice.
	 */
	public static final Constellation ORACLE_WIZARD = Constellation.of(
			"#..#..#",
			".#.#.#.",
			".......",
			"...#...",
			"...#...",
			"...#...")
			.withEdge(3, 2, 1, 4)
			.withEdge(3, 2, 3, 4)
			.withEdge(3, 2, 5, 4);

	/**
	 * Oracle Priest (epic, draft oracle-priest): a standing cross — Aura of
	 * Radiance at the foot, the two Brilliance rungs up the shaft, the crossbar
	 * spreading the aura sideways (Blinding Light left, Beacon of Light centre,
	 * Steadfast right), Retribution at the head. Fully grid-connected — no
	 * explicit edges needed.
	 */
	public static final Constellation ORACLE_PRIEST = Constellation.of(
			".#.",
			"###",
			".#.",
			".#.",
			".#.");

	/**
	 * Nemesis Shadow (epic, user sketch nemesis-shadow-first-20260720): the
	 * Dark Ritual alone at the foot, forking into the two ways of wearing the
	 * night — Ghost Form's three rungs up the left into Incorporeal at the
	 * crown, Extra Sensory Perception into the night-sight node and Feast's two
	 * rungs up the right. Row 2 is deliberately empty, so both forks cross the
	 * gap on the two explicit edges the sketch records rather than by grid
	 * adjacency.
	 */
	public static final Constellation NEMESIS_SHADOW = Constellation.of(
			"#...#",
			"#...#",
			"#...#",
			".....",
			".#.#.",
			"..#..")
			.withEdge(1, 1, 0, 3)
			.withEdge(3, 1, 4, 3);

	/**
	 * Nemesis Marksman (epic, draft nemesis-marksman): Deadeye alone at the
	 * foot, two columns carrying the window's two questions — how you move
	 * (Fleet, Vault, On the Wing) up the left, how the arrow lands (Long Shot's
	 * two rungs, Punch Through) up the right — and a crown row where Evasion,
	 * Long Watch and Siege sit apart. Row 2 is deliberately empty, so both
	 * columns cross the gap on explicit edges; Long Watch at the crown's centre
	 * touches neither neighbour by grid adjacency and takes two more.
	 */
	public static final Constellation NEMESIS_MARKSMAN = Constellation.of(
			"#.#.#",
			"#...#",
			"#...#",
			".....",
			".#.#.",
			"..#..")
			.withEdge(1, 1, 0, 3)
			.withEdge(3, 1, 4, 3)
			.withEdge(0, 4, 2, 5)
			.withEdge(4, 4, 2, 5);

	/**
	 * Nemesis Assassin (epic, draft nemesis-assassin): Death Mark at the foot
	 * forking into the two columns that spend it — Stalk into Headhunter's two
	 * rungs and Coup de Grace up the left (what you do to the mark), Contagion
	 * into Carrier, Vanishing Act and Death's Head up the right (what the mark
	 * does to everything near it). Fully grid-connected — no explicit edges.
	 */
	public static final Constellation NEMESIS_ASSASSIN = Constellation.of(
			"#...#",
			"#...#",
			"#...#",
			".#.#.",
			"..#..");

	/**
	 * Colossus Protector (epic, user sketch colossus-protector-suggested-edits-
	 * 20260720): the armour root alone at the foot, two columns up the sides —
	 * the fed body on the left, the guard that never drops on the right — joined
	 * at the crown by a node the sketch hangs between them. Row 1 is
	 * deliberately empty, so the root reaches both columns on explicit edges;
	 * the crown node is grid-adjacent to neither side and takes two more.
	 */
	public static final Constellation COLOSSUS_PROTECTOR = Constellation.of(
			"#.#.#",
			"#...#",
			"#...#",
			".....",
			"..#..")
			.withEdge(2, 0, 0, 2)
			.withEdge(2, 0, 4, 2)
			.withEdge(2, 4, 0, 4)
			.withEdge(2, 4, 4, 4);

	/**
	 * Colossus Slayer (epic, user sketch colossus-slayer-suggested-edits-
	 * 20260720): an hourglass pinched at Parry. Two roots at the foot — the
	 * left one the body that shrugs off magic, the right one the hands that
	 * know the blade — meeting at Parry in the middle, which then opens back
	 * out into what a parry pays: Strength up the left, temporary hearts and
	 * spell reflection up the right. Fully grid-connected — no explicit edges.
	 */
	public static final Constellation COLOSSUS_SLAYER = Constellation.of(
			"#...#",
			".#.#.",
			"..#..",
			".#.#.",
			"#...#");

	/**
	 * Colossus Crusher (epic, user sketch colossus-crusher-suggested-edits-
	 * 20260720): Titan's Leap at the foot forking into the two things a landing
	 * can be — Aftershock's three rungs up the left (it hits) and Gravity Well
	 * up the right (it holds) — with the right column continuing into what the
	 * body itself becomes. Fully grid-connected — no explicit edges.
	 */
	public static final Constellation COLOSSUS_CRUSHER = Constellation.of(
			"#...#",
			"#...#",
			"#...#",
			".#.#.",
			"..#..");

}
