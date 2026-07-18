# Magic Missile — variant-a: the Arcane Mote

## The read
The old amethyst shard reads as *a purple rock you dropped*, not a spell. The fix
isn't "more epic" — it's changing the **category** from gemstone to **bolt of
condensed force**. A four-point star is the universal shorthand for "magic," and
it is radially symmetric, so it stays legible no matter how the projectile is
viewed or tumbles — exactly what the brief asks (strong silhouette over detail).

## Where the pixels come from
Both sprites are vanilla's own **`nether_star`** silhouette recolored, pixel by
pixel, into the **`amethyst_shard`** palette (both pulled from the 26.2 client
jar; ramp sampled straight from the shard's real colors). So the mote already
matches the game's amethyst family — and the `AMETHYST_BLOCK_CHIME` cast — for
free. The only hand-plotted accent is a white-hot 2×2 heart that replaces the
nether star's astral-yellow core, so it reads *arcane*, not *astral*. This is the
same "vanilla base + a few plotted pixels" rule `make_node_icons.py` follows.
`make_missile_art.py` regenerates both from scratch.

## Why it survives 500 casts
- **The heart does the glow, so the trail barely exists.** The mote carries a
  bright white core; it does not need a stream behind it. The normal trail is one
  dim-violet `DUST` speck *every other tick* — a faint dotted thread, not the
  always-on white beam the author deliberately deleted. It can go to `[]` and the
  sprite still holds.
- **The cast is soft, short, and jittered.** Kept the iconic amethyst chime, but
  lifted it to a light register (pitch ~1.35), dropped it to volume 0.4, and gave
  it the ±0.15 jitter it currently lacks. The present fixed-0.5 chime is *the*
  fatigue bug: 500 identical bongs. Jittered and airy, four-a-second becomes a
  wind-chime shimmer instead of a drone.
- **Nothing is loud.** Normal cast 0.4, hit 0.5. A small spell should sound small.

## Empowered — a real step up, same family
Sprite: the same star **opens from 4 points to a bold 8-point burst** with a
hotter, fuller heart. (Authored at 16px — `SpellProjectileRenderer` already draws
empowered at 1.5×, so it must not be pre-enlarged; the extra *reach* is the point
count, not a scaled bitmap.) Cast: the **same** bright chime **plus** a low
`AMETHYST_BLOCK_RESONATE` bloom at pitch 0.85 — a deep resonance only the
empowered cast has. That undertone is the eyes-closed tell. Its trail is the
richer signature it can afford at every-8th/4th frequency: brighter violet dust
every tick, plus a sparse white `END_ROD` sparkle.

## Hit feedback (new)
The missile currently makes **no** impact sound. Added a soft, well-jittered
`AMETHYST_BLOCK_HIT` tick so hits feel like they land — quiet enough to spam.

## Capstones
- **Seeker (homing):** normal sprite, trail bumped to every-tick violet dust so
  the curve is legible. No sound change.
- **Lance (piercing):** keep the travelling ring; recolor its `END_ROD` points to
  violet `DUST` for family cohesion (white is an acceptable fallback if you want
  the AOE ring to read as a brighter hazard).
- **Echo twins:** plain normal sprite and trail, as today.

## Dials, if it's ever too much
Cut `hitSound` first → set normal `trail` to `[]` → halve `castSound` volume.
All real 26.2 fields (javap-verified); the three amethyst sounds are plain
`SoundEvent`s (no `.value()`).
