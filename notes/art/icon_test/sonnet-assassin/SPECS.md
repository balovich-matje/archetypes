# Assassin node icons — specs

32x32, vanilla (or mod dagger) sprites at 2x NEAREST plus a few hand-plotted
accent pixels, per `notes/art/make_node_icons.py`'s house style. One per
family, `MINOR` skipped.

**shadow_step** — The ender pearl (the family's own coded icon) with a small
faded iron dagger tucked behind it, a white sparkle trail linking them, and a
bright flash at the dagger's tip. Pearl = the blink, dagger + flash = the
strike that lands the instant you arrive.

**lightfoot** — Leather boots with three white/grey speed-dashes trailing
off to the left, the same speed-line grammar as the Slayer tree's own
`bash_overlay` and Priest's `fervent_cast`. Boots + motion = faster on foot.

**razor_edge** — The mod's iron dagger with a couple of white glint pixels
along the edge and a small white "+" at the tip — sharpened steel, the
house's own more-per-rank glyph.

**expose** — Vanilla's target block texture (a literal bullseye — the weak
point) with a half heart pinned in the corner. Bullseye + half-full heart =
bonus damage against a target already below half health.

**venom** — A spider eye (vanilla's own poison ingredient) with the real
Poison status badge pinned beside it. Ingredient + the actual effect icon —
no ambiguity about what the hit applies.

**shadow_flurry** — The mod's netherite dagger with two rotated, fading
echoes behind it — the exact `bladestorm()` grammar from the Slayer house
canon, and literally three daggers on screen for a rank whose own flavour
text says "three daggers' weight." A glint on the foremost blade only keeps
the eye on the "real" strike against netherite's dark palette.

**adrenaline_rush** — Sugar (vanilla's own speed-potion ingredient) with the
real Speed status badge pinned in the corner. Same ingredient+badge pairing
as Venom, one node over on the tree's centre spine.

**opportunist** — Vanilla's clock (already tied to cooldown by the Slayer
house canon's `braced_overlay`) with a small white "−" — the same glyph
Priest's `grace` uses for "costs less." Clock + minus = the cooldown itself
shrinks.

**blight** — A wither rose (vanilla's own Wither-effect flower) with the
real Wither status badge, brightened and tucked into the flower's naturally
empty top-right corner rather than over its near-black petals, where the
first pass let the two melt into one indistinguishable blob.

**momentum** — The wither skeleton's own skull, cropped straight off its
entity skin and brightened for contrast, with a small bright-green twinkle
at the corner — the kill, and the cooldown snapping back to ready. A
circular reset-arrow was tried first and just turned to noise at this size;
a plain "just happened" spark reads cleaner.

**flense** — Shears (vanilla's own cutting tool) with an iron chestplate in
the corner, split by a bright diagonal cut straight through it — the armor
visibly parted, matching "ignores armor" more literally than a badge could.

**sidestep** — A feather (light on your feet) with a white "miss" X sitting
in the feather's own dead-empty top-left corner. The X sat directly against
the plumage's own grey/white edge on the first pass and vanished into it;
moving it fully clear of the sprite's silhouette was the fix.

**deathblow** — The nether star (the tree's own capstone-tier item) with the
netherite dagger laid through it and a scatter of extra white sparkle at the
corners — the star's power running straight into the blade, on the node
that boosts every Shadow Step strike there is.

## Notes on the pass

Viewing the first render at 4x cold (not just at native 32px) caught four
icons that read fine as isolated PNGs but failed against the actual dark
tree background used everywhere else:
- `shadow_step`'s teleport dots were plotted in a teal sampled straight off
  the ender pearl itself — same palette as the sprite they sat on, so they
  vanished into it. Swapped for white.
- `sidestep`'s dodge accent was plotted immediately adjacent to the
  feather's own antialiased edge, in a near-identical grey — it read as one
  blurry mass, not "feather + separate mark." Moved into the sprite's
  actual empty corner.
- `blight`'s Wither badge is a naturally dark sprite, composited straight
  over the wither rose's equally dark petals — both nearly black, both
  nearly the same value as the `#2b2b2b` sheet background, so the badge was
  there in the file but invisible on screen. Brightened it and moved it to
  the rose's genuinely transparent corner instead of over the petals.
- `momentum`'s original reset mark was five scattered gold dots meant to
  read as a broken circular arrow; at 32px it just read as loose sparkle
  with no shape. Replaced with a single deliberate twinkle glyph instead of
  trying to force a circular arrow to survive the resolution.

All four were caught by re-compositing every icon onto the sheet's actual
`#2b2b2b` background before judging it — a plain white-matte preview (what
an image viewer shows a transparent PNG on by default) hid every one of
these problems.
