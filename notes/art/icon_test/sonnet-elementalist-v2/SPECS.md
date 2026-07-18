# Elementalist node icons -- round two -- specs

32x32 PNGs, real vanilla sprites (client jar 26.2) composed + a small
handful of hand-plotted accent pixels, upscaled 2x NEAREST for the blocky
look. One icon per family; MINOR skipped. Run `python3 make.py` to
regenerate `icons/` and `contact_sheet.png`.

## Why a round two

Round one (`../opus-elementalist/`, currently live at
`textures/node/test/opus/elementalist/`) reads as the one hand-drawn tree
next to the other eight: `ice_blast`'s faceted crystal, `glacial_spike`'s
lance and `chill`/`frostbite`/`blizzard`'s snowflakes were filled polygons
plotted point-by-point, not vanilla sprites with a few pixels on top. This
pass rebuilds every one of those from a real sprite instead:

- `ice_blast` / `glacial_spike` -- the *rotation itself* turns a real block
  face into an angular diamond (the same trick `iso_block()` in
  `notes/art/make_node_icons.py` already uses for its cube tops), not a
  hand-plotted facet.
- `chill` / `frostbite` -- the family's own mapped vanilla item (snowball,
  powder snow bucket) stands in for the hand-drawn snowflake.
- `blizzard` -- a real snow block fills the frame (the same
  block-fills-the-canvas composition `sonnet-wizard`'s `shatterpoint` uses)
  instead of five painted snowflakes.
- `meteorite` -- a real magma block (literally `Items.MAGMA_BLOCK`, the
  family's own mapping) for the crater, not a painted burst.

Every `Family` in `ElementalistNodes.java` already names a real vanilla
item -- that mapping was the design brief as much as the lang file.

## Design grammar (so the whole tree reads before you read a word)

- **Warm palette = fire branch, cool palette = ice branch, mana-blue/white
  = element-agnostic.**
- **Fireball is round** (fire_charge, a real vanilla item); **Ice Blast is
  angular** (the ice block's own face, rotated 45 degrees). Their two
  capstones (Meteorite, Glacial Spike) are bigger, more saturated escalations
  of the same shapes, not new motifs.
- A small pale **"-" beside the mod's mana orb = cheaper cast** -- Kindling,
  Chill and Spellweaver, the tree's three cost-down nodes, all share it.
- A small **red "^" = more damage** -- Scorch and Arcane Power share it.
- A small **dark-blue "v" = slows harder/longer** -- Frostbite.
- **Vanilla's own clock** = longer duration -- Ignition, reused straight
  from `make_node_icons.py`'s `braced_overlay`.
- **A crack through a real ice block = bonus vs. slowed/frozen** -- Shatter,
  the same crack-through-a-block idiom as `sonnet-wizard`'s `shatterpoint`.
- **A faded, offset echo of the same sprite = motion**, reused from the
  Slayer tree's `bladestorm`/`heavy_blows` language -- Fireball, Ice Blast
  and Meteorite all trail one.

## Per icon

- **fireball** -- `item/fire_charge.png`, full frame, real vanilla item.
  Three ember-coloured accent pixels trailing off the sprite's own
  transparent lower-left margin: just thrown, not sitting still.
- **ice_blast** -- `block/ice.png` rotated 45 degrees and scaled to fill the
  frame -- angular where Fireball is round -- with a matching frost trail.
- **focused_mind** -- not designed here. Byte-for-byte copy of
  `textures/node/test/sonnet/priest/devotion.png` per the hard constraint
  (`shutil.copyfile`, verified identical by hash).
- **kindling** -- `item/flint_and_steel.png` (the vanilla fire-lighting
  tool) with the mod's mana orb and the tree's cost-down minus.
- **scorch** -- `item/blaze_powder.png` (vanilla's fire-damage ingredient,
  and already reads like a burst of embers on its own) with the
  more-damage chevron in its free top corner.
- **ignition** -- `item/campfire.png` (a real flat vanilla item icon, not a
  rendered block) with `item/clock_00.png` pinned in the corner.
- **vaporize** -- `block/sponge.png` (vanilla's water-eating block) sitting
  just above a flat water strip, a few pale steam pixels lifting off where
  they meet.
- **chill** -- `item/snowball.png` (the plain round icy item, mirroring
  Kindling's plain fire tool) with the same mana orb and minus.
- **frostbite** -- `item/powder_snow_bucket.png` (vanilla's own
  freeze-you-solid material) with the slowed chevron stacked twice beneath
  it.
- **shatter** -- `block/packed_ice.png`, full frame, split by a bold crack
  -- the classic cracked-thing-for-bonus.
- **permafrost** -- `block/frosted_ice_3.png` -- the literal vanilla block
  this node creates -- over a flat water strip, with frost sparkles.
- **meteorite** -- `item/fire_charge.png` (this tree's own established
  fireball glyph, enlarged) falling out of open space onto a strip of
  `block/magma.png` -- the exact real material `Items.MAGMA_BLOCK` maps to.
- **flamethrower** -- `item/blaze_rod.png` (vanilla's actual fire-spray
  tool) with real `particle/flame.png` puffs spraying from its tip, not a
  painted cone.
- **glacial_spike** -- `block/blue_ice.png` (the coldest, slickest vanilla
  ice) rotated into a much larger, taller diamond than Ice Blast's, frost
  at its foot.
- **blizzard** -- `block/snow.png`, full frame, a few wind-streak accent
  dashes and frost flecks driven across it -- restrained, rather than a
  handful of hand-drawn flakes.
- **spellweaver** -- `item/enchanted_book.png` with the same mana orb and
  minus every cost-down node in this tree shares.
- **arcane_power** -- `item/nether_star.png`, almost exactly as vanilla
  ships it, with the same more-damage chevron Scorch uses.

## Notes on the pass

First render had three real problems, fixed before finalizing:

- `mana_orb()` was called with a target-pixel-looking argument (`11`) but
  its `scale()` helper treats the argument as a *multiplicative factor*, so
  a 9x9 source blew up to ~99px and got silently clipped by
  `alpha_composite` -- Kindling, Chill and Spellweaver all showed a
  corner-cropped smear instead of a small orb. Fixed by switching to the
  correct factor (~1.3, giving a ~12px orb) and confirmed by re-checking
  the sampled bounding box in the output PNG.
- `meteorite`'s magma block covered the top two-thirds of the frame, so the
  falling fire_charge (dark outline, warm fill) sat on top of an
  equally warm, equally busy block texture and disappeared into it.
  Fixed by shrinking the magma to a thin ground strip near the bottom so
  the ball reads against open canvas before it reaches impact.
- `blizzard`'s wind streaks and sparkle dots were both near-white on the
  near-white snow block -- functionally invisible. Fixed with a darker
  blue-gray for the streaks and a two-tone (icy-light / navy) dot pair for
  the flecks so both read against the pale block.

Compared side-by-side against `sonnet-wizard` and `sonnet-priest`'s
contact sheets: same "item/block fills most of the frame, small accent
pixels in the margin" rhythm, no filled polygons, no vector snowflakes or
comets anywhere in the set.
