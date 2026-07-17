# Crusher node icons — specs

The Crusher: maces and bare fists. Every icon starts from a real vanilla
sprite (recolored / cropped / composed) plus a small mechanic grammar plotted
on top — the recipe the mod author picked in rounds one and two. Grammar used:
`+` = more, speed dashes = faster, concentric rings = area, rising chevrons =
launch/up, downward dashes = falling, cracks = sundered/shattered, gold clock =
cooldown paid back (the project's own `braced_overlay` grammar).

The **fist** is the one hand-plotted object — vanilla has no fist sprite, so it
is built from the player skin's own skin-tone palette and item-outlined, the
same way the canon `iron_spikes` Brawler icon is plotted from the iron palette.
The mace path uses the real `item/mace.png` throughout.

Composed at 16px (vanilla item resolution), saved 2x NEAREST → 32px.

| Family | Base sprite(s) | Icon |
|---|---|---|
| **adrenaline** | `item/mace.png` | Mace with speed dashes streaming off the head — attack speed on every hit. |
| **sunder** | `item/iron_chestplate.png` | The chestplate split by a breach crack, plate chips knocked loose — armor parted. |
| **bare_knuckle** | fist (skin palette) | Bare fist plus a gold `+` — more unarmed damage per rank. |
| **iron_skin** | fist (iron palette) + `hud/armor_full.png` | The fist cast in iron beside an armor plate — bare hands that armor you. |
| **haymaker** | fist (skin palette) | Capstone. Fist driven into a bright impact star, knockback streaming off — one enormous punch. |
| **meteor** | `item/mace.png` + `item/fire_charge.png` | Mace wreathed in fire on its head, falling dashes below — bonus per block fallen. |
| **shockwave** | `item/mace.png` | Mace over concentric shock rings on the ground — full damage rings outward from the target. |
| **earth_shatter** | `item/mace.png` + iso `block/stone.png` + `item/clock_00.png` | Mace bursting a stone block apart, a clock for the refunded cooldown. |
| **quake** | `item/mace.png` + cracked ground | Capstone. Mace slamming cracked ground, hostiles launched skyward on rising chevrons. |
| **battle_trance** | `hud/heart/absorbing_full.png` | Gold absorption heart plus a gold `+` — every hit banks temporary health. |

MINOR is the intentional skip (fallback family, no mechanic to telegraph).
