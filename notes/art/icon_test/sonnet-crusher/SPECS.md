# Crusher node icons — specs

32x32, vanilla sprites at 2x NEAREST plus a few hand-plotted accent pixels,
per `notes/art/make_node_icons.py`'s house style. One per family, `MINOR`
skipped. The tree is a mace (per `CrusherNodes.java`'s own framing): handle
passives run mace-only, the fist flange runs fist-only, the mace flange runs
mace + corner accent (the game's own existing pattern for this half of the
tree), and the crown is vanilla's own absorption heart.

**adrenaline** — The vanilla `mace` with three speed-dashes (the house's
`bash_overlay`/`wide_swings_overlay` grammar) trailing its swing. Landing a
hit grants attack speed for both weapons; the mace stands in for "either
weapon," the dashes say "faster."

**sunder** — An `iron_chestplate` with a jagged hole punched clean through
the chest and the mace's own dark head sitting inside it, crack lines
radiating out. The house's "armor ignored" grammar (a punched hole, same
read as the Marksman sheet's `piercing_tips`) for a node that parts armor
like Breach.

**bare_knuckle** — A clenched fist, built from skin-tone values sampled
straight off the vanilla player skin's bare forearm (`steve.png`'s
short-sleeve default), with a "+" pinned at the corner for the four ranks of
flat unarmed damage.

**iron_skin** — The intact `iron_chestplate` (no hole this time — the
contrast with Sunder is the point) with two small fists hanging at its
sides, arms bare under the armor, plus the same "+" for the per-rank
armor/toughness stacking.

**haymaker** — Capstone. A bigger fist driving into a starburst impact, the
house's down-chevron "slowed" mark pinned on the struck side (Slowness VI),
and a knockback streak launched onward past the hit (Knockback II
send-off).

**meteor** — The `mace` with a `fire_charge` riding its shoulder — the
falling-star pun already established by the game's own canon overlay for
this family — plus a short fall streak. Bonus damage per block fallen.

**shockwave** — The `mace` swung head-down into the ground, two clearly
separated concentric rings marking the blow's reach. A falling mace blow
that hits everything within 2/4 blocks of the target.

**earth_shatter** — The `mace` grounded, a cracked floor and four chunky
stone-rubble pieces kicked out around it. Blocks up to stone hardness
shatter in a growing radius when a Quake meets no creature.

**quake** — Capstone. The `mace` raised higher and angled harder than Earth
Shatterer's, a wider ground crack, more rubble thrown up on both sides —
bigger and higher, since this is where the mace path ends. Hostiles go
skyward.

**battle_trance** — Vanilla's own `absorbing_full` heart sprite, upscaled,
with two `goldheart` particle shards (the game's own absorption-gain
sparkle) tucked at the corners to read as banking health, not just holding
it.
