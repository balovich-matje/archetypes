# Elementalist skill-tree icons — round two (opus-elementalist-v2)

Re-render of the round-one winner to the settled style standard. The round-one
set was flagged by the author as standing out from the other eight trees — too
many fully hand-plotted comets, orbs and saturated arrows. This set rebuilds
every icon on a **real vanilla sprite** pulled from the client jar, recoloured /
cropped / composed, with only a handful of hand-plotted accent pixels carrying
the grammar the earlier rounds settled on.

- 32×32 PNGs, 16px art upscaled 2× NEAREST (block/scene icons composed directly
  at 32px), matching the neighbour sets (opus-marksman/slayer, sonnet-wizard/priest).
- Built by `make.py`; contact sheet at 4× on `#2b2b2b`.

## Shared grammar
| glyph | meaning |
|---|---|
| `+` | more (damage / duration / capacity) |
| `-` over a mana orb | less mana cost |
| down-chevrons (cold blue) | slowed |
| bright forked crack | bonus damage vs a state (slowed / frozen) |
| speed / motion dashes | in flight |
| fire warm palette vs ice cool palette | which element |

Fire runs up the left edge (warm sprites), ice up the right (cool sprites), so
the set reads as one constellation.

## Icons
| family | vanilla source | composition / mechanic telegraphed |
|---|---|---|
| **fireball** | `item/fire_charge` | the thrown fire, ember flight-trail behind it — the base fire bolt |
| **ice_blast** | `item/snowball` (tinted ice-blue) | cold bolt + flight trail + slowed-chevrons — the base ice bolt that slows |
| **focused_mind** | *copied byte-for-byte from `priest/devotion.png`* | the unified +mana/s icon shared across every Seeker tree (NOT designed here) |
| **kindling** | `item/flint_and_steel` | fire-starter throwing sparks, over a mana orb with a `-` — fire spells cost less |
| **scorch** | `item/blaze_powder` | fiery powder + `+` + hot impact spark — fire hits harder |
| **ignition** | `item/campfire` + `item/clock_00` | fire that keeps burning + a clock — your fire lasts longer |
| **vaporize** | `item/fire_charge` + `block/water_still` | a fireball skimming water, steam boiling up in its wake |
| **chill** | `item/snowball` | plain snowball over a mana orb with a `-` — the ice mirror of Kindling |
| **frostbite** | `item/powder_snow_bucket` | powder snow + a deepening stack of slowed-chevrons + `+` — slows harder and longer |
| **shatter** | `block/blue_ice` | a block of ice split by a clean forked crack + `+` — bonus damage vs slowed/frozen targets |
| **permafrost** | `block/frosted_ice_0` + `block/water_still` + `item/snowball` | the mirror of Vaporize: an ice bolt freezing a solid, walkable crust in its wake |
| **meteorite** | `item/fire_charge` + `particle/flame` | the fireball falling as a meteor, flame trail down to a ground impact |
| **flamethrower** | `item/blaze_rod` + `particle/flame` | the rod as a nozzle, a widening cone of fire spraying from its tip |
| **glacial_spike** | `item/trident` (tinted ice-blue) | vanilla's one polearm recoloured to a lance of winter, frost sparkle at the point |
| **blizzard** | `block/snow` | a snow bank with sleet raking down at a slant — the called storm over an area |
| **spellweaver** | `item/enchanted_book` | the crown of cheaper casting: spells over a mana orb with a `-` |
| **arcane_power** | `item/nether_star` | the keystone + `+`, one warm mote and one cold mote — both elements deal more |

## Notes / decisions
- **FOCUSED_MIND is not ours**: `make.py`'s `focused_mind()` copies
  `priest/devotion.png` verbatim so a re-run keeps it byte-identical (verified
  with `cmp`).
- **Vaporize ↔ Permafrost** are deliberately built as a mirrored pair — both are
  "projectile passes over water" utility nodes on opposite edges; fire → steam,
  ice → a frozen crust. Warm vs cool palette keeps them distinct in the grid.
- **Glacial Spike** reuses the Wizard set's trick of recolouring the vanilla
  trident into a clean diagonal lance, rather than hand-plotting an ice shard.
- **Cost reducers** (Kindling / Chill / Spellweaver) share one recipe — element
  token + small mana orb + `-` — so the "costs less mana" read is consistent.
- Block/scene icons (Shatter, Permafrost, Blizzard) are full-bleed, matching the
  precedent set by Wizard `shatterpoint` and Priest `lumen`/`radiance`.
