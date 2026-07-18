# Magic Missile — Variant C: the Arcane Mote

## Concept
A four-pointed spark of pure force with a hot white core and a violet glow —
built on the game's own amethyst palette (sampled straight from
`item/amethyst_shard.png`, so it reads as the same crystal the wand is
chiming). It trades the shard's flat "here is an item" for a small lit thing
that looks *cast*, not *held*.

## Why a spark, not a shard or a bolt
The thrown-item renderer billboards the sprite toward the camera and tumbles
it with the projectile's motion. An arrow or a shard has a "right way up"; at a
random billboard angle it looks like a mistake. A **radially symmetric
four-point star has no wrong angle** — rotated 23° or 45° it's still,
obviously, a spark (verified by eye against tumbled copies). It's also the
oldest visual shorthand for "a mote of magic," which is exactly what a magic
missile is. Strong silhouette over detail: at in-world size it collapses to a
bright violet dot with a cross-glint, and that's enough to track.

## The three-channel step up (normal -> empowered)
The empowered cast fires rarely and has to be unmistakable **with eyes closed
and open**. It steps up on every channel at once so no single one has to shout:

| channel | normal | empowered |
|---|---|---|
| sprite | compact 4-point spark | wider white core, 8-point cross, faint bloom (and the renderer's own 1.5x) |
| trail | one dim violet speck / 2 ticks | brighter, denser violet **every** tick + a sparse white END_ROD glint |
| sound | one soft jittered chime | bright high chime **layered** over a warm AMETHYST_BLOCK_RESONATE bloom |

## Sound — designed for 4 casts a second
The cast keeps the amethyst **chime** (soft glassy attack, fast decay — one of
the least fatiguing sounds in the game) but fixes the two things that made
spam grate: it's **quiet** (vol 0.35) and it **jitters** (±0.13). Jitter is the
whole trick — at a fixed pitch, 4/sec is a machine gun; spread across a
semitone-ish band it becomes a shimmer the ear stops resolving into hits.
Normal cast is deliberately **one layer**; the second layer is reserved as the
empowered tell (a high ping riding a warm resonate bloom — the "ding-mmm" you
can name blind). A new soft **hit** tick closes the feedback loop the silent
missile was missing, jittered too since Lance can land several a tick.

## Trail — a whisper, not a contrail
The author removed the old always-on white streak on purpose, so the trail is
almost nothing: **one small violet dust every other tick, on the flight line,
no drift** — a few dim specks that wink out, letting the glowing sprite carry
the read. If it still nags at 500 casts, `trail: []` is a clean fallback; the
mote stands on its own. Empowered earns a real (still tasteful) thread.

## Capstones
- **Seeker (homing):** same dust, but every tick while steering — the denser
  thread lets the *curve* draw itself, which is the thing worth seeing.
- **Lance (pierce):** keep the spinning 6-point ring, recolour it from vanilla
  END_ROD white to dust `#B38EF3` so the AOE circle joins the arcane family.

## Files
`missile.png`, `empowered.png` (16×16), `preview.png` (both at 4× on #2b2b2b),
`spec.json` (FX plan), `make_missile.py` (regenerates the sprites; palette
sampled from the client jar, same pattern as `make_node_icons.py`).
