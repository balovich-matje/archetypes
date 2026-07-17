# Protector icon set — SPECS

Every icon starts from a real vanilla sprite (recolored/cropped/composed),
built at 16x16 and saved upscaled 2x NEAREST to 32x32, matching the size the
`Family` enum in `ProtectorNodes.java` already declares. The shield glyph
used across most of these is built by flood-filling vanilla's own
`gui/sprites/container/slot/shield.png` — the greyed-out empty-shield-slot
placeholder, the one flat vanilla sprite that's actually shield-*shaped*
(the real item icon is a 3D render) — with the shield's oak/iron palette.
Gold corner ticks mark the two capstones.

- **bash** — Shield Bash. The shield glyph with a bright white starburst
  landing on its face: the shove itself.
- **slam** — Shield Slam. The same burst, hotter/wider (orange-red), plus
  vanilla's own Strength effect icon riding the corner: the "+damage" tell.
- **cooldown** — Quick Recovery. Vanilla's resting clock (`clock_00`) ringed
  by a counter-clockwise sweep of dashes: time running back, not forward.
- **knockback** — Concussive Blow. The piston's own crate face as the
  housing, an iron ram punched hard out of it with blunt impact chevrons —
  bigger and less flashy than Bash's spark, since it trades damage for it.
- **wide** — Wide Swings. The shield under a bash arc thrown corner-to-corner
  instead of a single-target burst.
- **unbreaking** — Reinforced Straps. The shield belted with a real
  leather-brown strap corner to corner, iron rivets pinning both ends down.
- **spikes** — Iron Spikes. The shield's own rim (read off the real outline
  sprite's edges) bristling with short iron spikes; one tipped red where it
  just caught someone. Thorns worn on the shield, not a stray caltrop.
- **rush** — Shield Rush. The shield crossed by three bold speed streaks and
  a wind-charge swirl (vanilla's own sprite, shrunk) kicked up at the heel.
- **braced** — Braced. A small gold clock badge (backed by a dark disc so it
  pops off the wood) rides the corner with a downward gold tick: every block
  shaves the second right off.
- **reflect** — Reflection. An arrow arriving low-left, the same arrow
  already leaving high-right, a white spark where the two paths cross on the
  shield's face.
- **taunt** — Taunt. The goat horn mid-call, sound rings fanned into the
  open corner above the bell, the outermost gone hostile-red: a challenge,
  not a greeting.
- **omni_block** — Bulwark (capstone). The real shield flanked by two faded
  ghost copies covering both sides — every direction blocked at once — gold
  corner ticks marking the capstone.
- **ground_slam** — Ground Slam (capstone). The shield (duller rim, heavier
  reading) over ground that's already cracking with shockwave dashes kicked
  out both sides; a small hand-plotted anvil-in-profile badge (colour-
  matched to the real block texture, since that texture itself is just grey
  noise) crowns it, gold-ticked for the capstone.
