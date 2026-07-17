# Slayer node icons — specs (bake-off round three)

32x32, vanilla sprites (or the mod's own greatsword textures) at 2x NEAREST
plus a few hand-plotted accent pixels, per `notes/art/make_node_icons.py`'s
house style — the same pipeline that built the tree's own existing icons.
One per family, `MINOR` skipped, 14 total.

Grammar: `+` = more · a "−" = shorter/less · a fading rotated echo = a
repeated/duplicated strike · down-chevrons = slowed · a cracked, near-empty
thing = a bonus tied to a target's low-health state · an ingredient next to
the real vanilla status-effect badge = "applies X" · a small bright twinkle
= a kill snapping a cooldown back to ready.

**slowness** (Hamstring) — vanilla's own Slowness badge, already what the
live tree shows for this family, made dominant with an iron sword tucked
into its empty corner as the trigger and two down-chevrons stacked below —
the house "slowed" glyph — so it reads as an effect even before the badge
itself is recognised.

**taste_of_blood** — a full heart with its own falling drop (the family's
original read, rebuilt at 32px) plus a small white "+": kills restore
health, not just "here is a heart."

**lunge** — the rabbit foot already assigned to this family (a real leap
ingredient) with a tapering burst of white-to-grey motion ticks continuing
straight off its own toe into the canvas's empty top-right corner — the
leap carrying past the frame, following the foot's own diagonal rather
than a generic side-trail.

**kbres** (Immovable) — the greatsword with an obsidian mini-cube (built
via the canon `iso_block()` helper) anchored in its empty bottom-right
corner — obsidian is the family's own assigned item, the game's own
"doesn't move for anything" block. Its raw texture reads as near-black, so
it's brightened hard plus two lifted violet flecks, or it vanishes into
the tree's own dark background exactly the way an unbrightened badge did
on an earlier Assassin pass.

**bleed** (Rend) — the family's own claw-gash motif, bigger, with a small
sword tucked in the corner as the trigger and three tally drops along the
bottom edge for "every second, for 3 seconds" — a single gash alone can't
show a duration.

**blade_dance** — two swords crossing opposite ways with arcs off each
tip: the family's original composition, doubled cleanly onto the 32px
canvas.

**heavy_blows** — the family's own motion-echo grammar (a faded ghost of
the greatsword hanging behind the real one — weight read as blur, which
also carries "slower") plus a small "+" for the extra damage.

**first_blood** — an unbloodied greatsword, except the very tip where the
first cut lands, and the drop it's already shedding: the family's original
read, rebuilt.

**flurry** — Lunge's own rabbit foot, tying the two families together,
with a small kill-drop and a bright green "ready again" spark — the same
kill-resets-cooldown grammar as a spawn on a kill elsewhere in the house
canon, told with the actual family it resets.

**executioner** — the greatsword with a heart CONTAINER (vanilla's
empty-heart outline) cracked and down to one red sliver, tucked in the
corner: the classic cracked-thing-for-a-bonus, here a death sentence
instead of a damage multiplier. Vanilla's container sprite is a near-black
line — invisible against the tree's near-black background — so it's
restamped bone-white first.

**bloodlust** — a kill (the house blood drop, oversized) paired with
vanilla's own Speed badge and a couple of motion dashes trailing off it:
the same ingredient+badge pairing used elsewhere in the constellation for
on-hit effects.

**relentless** — the point: your capstone comes back sooner, whichever one
you run. Vanilla's own clock (the house's established cooldown glyph) with
two dark-outlined "−" marks at opposite corners on the rim. A plain white
minus vanished into the gold rim on the first pass; dropping it onto the
clock's dark closed-eye band instead fixed contrast but turned the clock
into a startled cartoon face. A dark halo behind the mark — stroke it like
a UI icon — let it sit on the rim without touching the face.

**bladestorm** — capstone: the family's own fan-of-echoes whirl, doubled
onto the 32px canvas — two faded rotated copies of the same sword behind
the real one, all sweeping the same way.

**decimate** — capstone: vanilla's own sweep-attack flash blown up past
the canvas edge for a bigger, more violent arc than Heavy Blows' own
restrained echo, greatsword kept straight on top. A rotated blade was
tried first for the "tilted cleave" the tooltip promises; at 32px NEAREST
the rotation broke the weapon's own silhouette into a diagonal smear that
stopped reading as a sword at all. The oversized sweep alone carries
"massive"; the greatsword stays intact.

## Notes on the pass

Viewing the first render at 4x cold (not just as native 32px PNGs) caught
six icons that looked fine as isolated files but failed against the
sheet's actual `#2b2b2b` background:

- `kbres`'s obsidian cube was plotted straight from the raw block texture,
  which is itself near-black — it disappeared into the tree background
  entirely, same failure class as an unbrightened dark badge. Brightened
  hard, plus two manually-lifted violet flecks.
- `executioner`'s heart container is a near-black outline sprite by
  default — invisible on both the sheet and the in-game background.
  Restamped to bone-white before compositing.
- `relentless`'s plain white minus, on the clock's gold rim, was
  white-on-near-white and vanished. Moving it onto the clock's own dark
  "closed eye" band fixed contrast but turned the clock into a startled
  face instead of reading as "shorter." Landed on a dark-outlined version
  of the mark instead, which survives on any background including the
  gold rim.
- `lunge`'s first motion accent was three same-size, evenly-spaced blocks
  that read as disconnected noise rather than a trail. Rebuilt as a
  tapering line co-linear with the rabbit foot's own diagonal, continuing
  straight off its toe.
- `flurry`'s kill-drop was plotted at native (non-doubled) pixel scale —
  effectively a single pixel, invisible at icon size. Redrawn at the house
  2x scale in the canvas's genuinely empty top strip.
- `decimate`'s first pass rotated the whole greatsword sprite for a
  "tilted" cleave; NEAREST-resampled rotation at 32px turned the blade
  into an unreadable diagonal smear. Reverted to a straight blade and
  oversized the sweep flash instead — "massive" carried by scale, not a
  broken weapon silhouette.

All six were only visible once every icon was recomposited onto the
sheet's actual dark background before judging it — a bare transparent PNG
in an image viewer hides every one of these.
