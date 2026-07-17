# Priest node icons — specs

32x32, vanilla sprites at 2x NEAREST plus hand-plotted accent pixels, per
`notes/art/make_node_icons.py`'s house style. One per family, `MINOR` skipped.

**holy_light** — Glowstone dust with a sunburst of gold ray-pixels punching
out on all sides. Dust = raw light; the burst = "lob a light that shatters
on impact," the base cast every other node builds on.

**lumen** — A glowstone block (denser, brighter than loose dust) shrunk to
leave a margin, with a crisp gold "+" in the corner. Block-over-dust reads
as "the same light, stronger"; the "+" is the family's universal
more-per-rank glyph.

**grace** — Sugar (vanilla's own "cheaper/faster" ingredient) paired with
the mod's own mana orb sprite and a "−" beside it. Sugar signals economy;
the orb makes it unambiguously about mana cost.

**radiance** — A sea lantern shrunk to leave a margin, with four gold rays
punching straight through it to all four frame edges — the burst literally
reaching further than the block that made it.

**devotion** — Lapis lazuli (mana-blue) next to the mod's mana orb, with a
few rising tick-pixels above it — mana quietly climbing over time rather
than a one-shot restore.

**fervent_cast** — A feather (vanilla's own "light and fast" item) trailing
three streak-lines to its left, the same speed-line language as the
existing Slayer bash_overlay icon — instantly reads as "faster."

**mercy** — A glistering melon slice (vanilla's healing-potion ingredient)
with a plain red heart in the corner. Heal ingredient + heart = more
healing, no ambiguity.

**wrath** — Blaze powder (vanilla's fire/harm ingredient) with a bone-white
skull glyph laid over it. Fire ingredient + skull = damage aimed at the
undead specifically.

**renewal** — Ghast tear (vanilla's own Regeneration-potion ingredient,
already this family's coded icon) with the actual vanilla Regeneration
badge pinned on top — as literal a read as the palette allows.

**benediction** — A golden apple (vanilla's own buff-food) with four tiny
colour pips — one each sampled from Speed/Strength/Resistance/Fire
Resistance's own icons — on a small card, for "one of these, at random."

**beacon** — The mod's mana orb, enlarged, with a bold gold "+" beside it.
No vanilla item reads as "mana capacity" the way the player's own HUD orb
does — the clearest icon of the set.

**immolation** — A skull wreathed in ordinary orange fire (not soul fire's
blue, which read as ice/frost in testing and got swapped out). Skull +
flame = burning the undead, no reading required.

**aegis** — The vanilla shield's flat front plate (reused from the
existing bulwark_overlay crop) with a golden absorption heart centred on
its face — the shield that gives you extra hearts.

**sanctuary** — Two faded shields flanking a pair of golden absorption
hearts — same shield-plus-absorption language as Aegis, but doubled and
outward-facing to read as "protects others," not just you.

**judgement** — Fermented spider eye (vanilla's own Weakness-potion
ingredient) with a skull glyph and a small downward purple tick beneath
it — eye = the debuff, skull = who it lands on, arrow = weakened.

**ascendant** — The end crystal (already this family's capstone icon) with
a small heart at one corner and a small skull at the other — the crystal
that turns both halves of Holy Light up at once.

## Notes on the pass

First render had three quiet failures, all fixed before finalizing:
- `lumen` and `radiance` originally scaled their block textures to fill
  the full 32px canvas edge-to-edge, leaving no transparent margin for the
  accent pixels to sit in — the "+" and rays blended into the block and
  vanished. Fixed by shrinking the block ~30% and centering it.
- `immolation` used the soul-fire particle (blue) for "on fire," which at
  this size reads as ice or water, not flame. Swapped for the ordinary
  orange fire particle.
- The contact sheet's own cell height was shorter than the icon it held,
  so every label overlapped the icon's bottom few rows (most visibly
  clipping judgement's debuff arrow). Fixed the sheet's own layout math.
