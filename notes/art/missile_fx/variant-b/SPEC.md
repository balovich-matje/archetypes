# Magic Missile — variant-b: the Arcane Star-Mote

## The identity
A compact four-point star of arcane light — a *mote*, not a gem. The old amethyst
shard read as dropped loot flying sideways; this reads as **magic** at a glance,
which is the whole job of the most-cast spell in the game.

## Why this shape
- **Rotation-safe.** The thrown-item renderer billboards and can spin the sprite.
  A radially symmetric star looks intentional at every angle; an oriented shard
  looks *wrong* the moment it tips. Silhouette over detail, exactly as the brief
  asks.
- **Reads at flight size.** At 16–40 px it collapses to a clean glowing spark
  with a bright cross and a warm core (see `smallsize` / `preview.png`). Nothing
  fiddly is lost.
- **Not epic, not cheap.** Cool amethyst purples, one small cream core sparkle.
  Calm enough to fire 500 times an hour without eye fatigue; luminous enough to
  feel like a spell on cast one.

## How it's built (house rules, same as `make_node_icons.py`)
`make_missile_fx.py` takes the **real** `item/nether_star.png` out of the client
jar — a genuine radial 4-point star — and recolors it pixel-for-pixel from its
teal/cream ramp onto the Wizard tree's amethyst palette, every stop **sampled
from the real `item/amethyst_shard.png`**. So the mote shares its exact material
with the `AMETHYST_BLOCK_CHIME` cast sound and the shard the tree is themed on —
palette-matched to the game for free. Hand-plotted accents: a lilac-white core,
one `#FFFDD5` cream sparkle (the shard's own highlight), and for empowered a
white-hot heart, four on-axis ray extensions, and a faint one-pixel lilac halo.
Recolored + reheart-ed, it reads as an arcane spark, never as "a nether star."

## Empowered (every 8th / 4th cast)
Same mote, overcharged: hotter white core, rays a pixel longer, a soft lilac
skirt. A clear in-family step up — and the renderer's existing **1.5x** empowered
scale stacks on top, so it's unmistakable in flight without being a different
spell. (Author's texture stays 16×16; do not double-enlarge.)

## Sound — surviving 4 casts a second
- **Normal cast:** `AMETHYST_BLOCK_CHIME`, soft (vol 0.35), pitched **up** to a
  light "ting" (base 1.4) with **mandatory ±0.18 jitter**. High + soft + jittered
  = it never builds into a droning wall the way the old low 0.5-pitch chime would
  under spam.
- **Empowered cast:** the same chime **layered** with `AMETHYST_BLOCK_RESONATE` —
  a deep amethyst undertone (base 0.9). That resonant "bloom" under the ting is
  the eyes-closed signature: you *hear* the proc without watching the projectile.
  Low jitter keeps the proc consistent and recognizable.
- **Hit:** `AMETHYST_BLOCK_HIT`, a crisp bright "tink" (base 1.5) — connection
  feedback that stays light across hundreds of impacts.

All amethyst-family, all vanilla, max two layers per event.

## Trail — subtle by design
- **Normal:** one dim lilac `DUST` mote every **3 ticks**. A faint breadcrumb, not
  a contrail — the glowing sprite does the work, honoring the author's removal of
  the always-on white trail.
- **Empowered:** a lean `END_ROD` sparkle (the author's established empowered
  look) tinted into the family with a violet `DUST`. Charged, not fireball-loud.
- **Seeker (homing):** keep the sprite; add a second violet dust with a little
  outward speed so the trail curls — a quiet "it's tracking" tell.
- **Lance (pierce):** keep the existing `END_ROD` `lanceRing()` as-is; the spinning
  white ring reads the sweep width better than any restyle.
