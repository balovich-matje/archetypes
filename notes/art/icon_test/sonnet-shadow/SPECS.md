# Shadow node icons — specs

32x32, vanilla sprites at 2x NEAREST plus a handful of hand-plotted accent
pixels, per `notes/art/make_node_icons.py`'s house style. One per family;
`INVISIBILITY` (already drawn) and `MINOR` skipped.

**The tree's one recurring mark**: the vanilla Invisibility status badge
(`mob_effect/invisibility.png`, a light-blue mirror in a gold ring) pinned in
a corner at its native 18x18 size. It's the exact badge the game's own HUD
already shows the player mid-invisibility, so pinning it means "this
triggers on/because of invisibility" without inventing a glyph. Nodes keyed
to *sneaking* rather than invisibility (Night Eyes, Umbral Sight, Swift
Shadow, Dim Presence) pointedly don't get it — the absence is information
too, and matches their `.desc` text exactly (Swift Shadow's even says "no
invisibility required").

**night_eyes** — Golden Carrot with the vanilla Night Vision badge (an eye
with a crescent moon in it) pinned top-right. The badge is a literal, 1:1
vanilla sprite for the exact effect the node grants, and its moon doubles as
a nod to the constellation's own crescent.

**umbral_sight** — Glow Ink Sac with the vanilla Glowing badge (white
spiked burst) pinned top-right. Glow Ink Sac already applies Glowing to
whatever it's thrown on in vanilla, so item and badge are the same mechanic
twice, once as the ingredient and once as the effect it leaves on a target.

**swift_shadow** — Sugar with three shrinking speed-dashes trailing off it,
the same motion-line language as the Slayer tree's `bash_overlay` and the
Wizard tree's `velocity`/`fervent_cast`. No invisibility badge — the node
explicitly works without it.

**dark_mending** — Glistering Melon Slice (vanilla's own healing-potion
ingredient) with a red heart at the corner and the invisibility badge
pinned above — ingredient, effect, and trigger condition in one glance.

**dim_presence** — Phantom Membrane at full opacity (its own hazy vanilla
texture already reads as "insubstantial," so no fade is needed) with a
small white eye-and-slash tag bottom-right — the classic "not seen"
stealth-game pictograph, kept small enough to read as an accent rather than
the icon's subject. No invisibility badge: the node explicitly works
"hidden or not."

**cleansing_veil** — Milk Bucket (vanilla's own status-cure item) with the
invisibility badge pinned above and two pale rim-glints for "wiped clean."

**stillness** — A stopped clock (`clock_00`) with the invisibility badge
above and a small white pause glyph tucked into the clock sprite's own
transparent bottom-right corner (round face, square canvas) — the timer,
held, without fighting the clock's face or hands for contrast.

**first_strike** — Iron Sword with a red/gold ambush burst at the blade's
upscaled tip and the invisibility badge at the hilt — the surprise hit
landing, and what has to be true for it to land.

**bloodrush** — Redstone (already red, already "quickened" in player
shorthand) with the vanilla Strength badge pinned above — the literal buff
granted — and the invisibility badge at the corner for the kill condition.

**reaper** — a Wither Rose (vanilla's own damage-over-time flower) with a
red heart above and the invisibility badge at the corner — the kill, the
health it gives back, and the condition it needs.

**ghost_armor** — a Chainmail Chestplate rendered at reduced alpha, full
stop. No badge needed: the fade *is* the mechanic, the same "ghost copy"
trick the Slayer tree's `bulwark_overlay` already uses, applied to the
player's own armor instead of a duplicate shield.

**last_shadow** — Totem of Undying (vanilla's own "cheat death" item, an
exact match for "fatal damage instead...") with the invisibility badge
pinned above for the Invisibility cast that follows.

**predator** — the skeleton's own front-face skin crop, a real vanilla
skull rather than a hand-drawn one, luminance-remapped into a bone/socket
palette for contrast at this size (the same brightness-preserving recolor
the tree's own `invisibility.png` icon already uses on `bad_omen.png` —
shape stays 100% the vanilla UV, only the palette changes). The
invisibility badge marks the kill condition; a small gold refresh-loop
bottom-left is the one purely-drawn glyph in the set, for "renews the full
duration."

**umbral_mastery** — a dimmed Ender Eye (its `Family.icon()` mapping) inside
a thin crescent-moon ring built by subtracting one circle from another —
the crown sitting on the constellation's own moon between the two
capstones, dimmed because the mechanic is still unwritten. No invented
"this is what it does" glyph, since it doesn't do anything yet.

## Notes on the pass

First render had three real failures, all caught by the cold contact-sheet
look and fixed before finalizing:
- `dim_presence` faded the Phantom Membrane to 165 alpha *and* faded a tiny
  Darkness badge on top of it — the result was a shapeless gray-purple
  blob with no readable accent at all. Fixed by keeping the membrane at
  full strength (its own texture already reads as "hazy") and swapping the
  low-contrast badge for a small, high-contrast eye-and-slash tag.
- `stillness` laid solid gold pause-bars directly over the clock's own gold
  face, so bars and face merged into one blob and the clock stopped
  reading as a clock. Fixed by moving the pause glyph into the clock
  sprite's transparent corner and recoloring it white with a dark outline.
- `predator`'s raw skeleton-skin crop is native mid-gray, which nearly
  vanished against the sheet's dark background — it read as a scrap of
  wall texture, not a skull. Fixed with a luminance-preserving recolor into
  a bone/socket palette plus a thin dark outline, which pushed the eye
  sockets and jaw line into clear silhouette.
