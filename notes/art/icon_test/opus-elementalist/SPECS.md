# Elementalist node icons — specs

32x32 PNGs, vanilla sprites (client jar 26.2) composed + a few hand-plotted
effect pixels, upscaled 2x NEAREST for the blocky look. One icon per family;
MINOR skipped. Run `python3 make.py` to regenerate `icons/` and
`contact_sheet.png`.

## Design grammar (so the whole tree reads before you read a word)
- **Warm palette = fire branch, cool palette = ice branch, mana-blue/white =
  element-agnostic.** The two edges of the flame are two colours at a glance.
- **Round fiery orb** (fire_charge) = the fire *active*; **angular ice shard** =
  the ice *active*. Passive element markers are a **flame-lick** and a
  **snowflake** — soft vs sharp reinforces fire vs ice.
- Repeated glyphs carry the mechanic: **mana-orb + down-arrow = cheaper cast**,
  **red up-arrow = more damage**, **clock = longer duration**, **cracked frozen
  cube = bonus vs slowed/frozen**.

## Per icon
- **fireball** (active) — fire_charge orb with a shrinking flame trail: a
  fireball in flight. Round + trail = a thrown projectile.
- **ice_blast** (active) — a faceted ice shard on a frost trail, the mirror of
  Fireball. Angular where Fireball is round, so the two element-doors read as
  opposites.
- **focused_mind** — mana orb welling up with a white "+" and rising motes.
  Neutral blue (feeds either element); "+" not a down-arrow = *more* mana.
- **kindling** — flame + mana orb + down-arrow: fire spells cost less mana.
  Same layout as Chill so the two cost nodes read as a pair.
- **scorch** — a flame under a bold red up-arrow: fire hits harder. Red
  up-arrow = damage, everywhere it appears.
- **ignition** — flame beside a gold clock: fire burns longer. Clock is this
  house style's established "time" glyph.
- **vaporize** — a fireball skims a water strip and white steam plumes rise:
  fire boils away the water it passes.
- **chill** — snowflake + mana orb + down-arrow: ice spells cost less. Mirror
  of Kindling with the ice marker swapped in.
- **frostbite** — snowflake gripping a target that sinks under stacked
  down-chevrons: ice slows harder and longer. No mana orb, so it reads as slow,
  not cost.
- **shatter** — a frozen ice cube split by a solid crack with an impact burst:
  the classic cracked-thing-for-bonus (extra damage vs slowed/frozen).
- **permafrost** — a slab of frosted blue ice over water with frost sparkles:
  ice glazes water into a walkable frozen surface.
- **meteorite** (capstone) — a molten ball plunging on a fire trail into a
  burst crater below: the called-down meteor and its impact.
- **flamethrower** (capstone) — a blaze-rod nozzle spraying an expanding cone
  of fire: the held channel.
- **glacial_spike** (capstone) — one great faceted lance of ice, far larger
  than the Ice Blast bolt, frost at its foot: the winter lance that freezes.
- **blizzard** (capstone) — a swarm of snowflakes driven on slanted wind
  streaks: many flakes, so it reads as a storm, not one spell.
- **spellweaver** (crown) — an enchanted book (all your magic, and it glints)
  with a mana orb + down-arrow: every spell costs less.
- **arcane_power** (crown) — a nether star with a bold red up-arrow: every
  spell hits harder. Shares the damage glyph with Scorch.
