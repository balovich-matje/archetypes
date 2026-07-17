# Wizard node icons — opus

Magic-missile artillery. One shared visual vocabulary so the tree reads as one hand:
- **missile** = an amethyst-white comet bolt (palette lifted from vanilla `amethyst_shard`)
- **mana** = the mod's own blue `mana_orb_full` sprite
- **wand** = the mod's `magic_wand` sprite, amethyst tip and all
- **"+"** more · **"−"** less · arrows for speed/reach · a heart for health-state

All are full standalone 32x32 PNGs. `python3 make.py` renders `icons/` and `contact_sheet.png`.

## Per icon (composition → why it reads)

- **magic_missile** (active) — Wand in hand, a bright amethyst bolt just loosed off its tip with a trailing streak. The wand + a projectile leaving it = "cast a missile," the tree's defining act.
- **mana_shield** — A heart split down the seam: red health left, mana-blue right. Damage paid half in mana instead of blood — the split IS the mechanic.
- **force** — A fat amethyst missile with hit-spikes at the nose and a bold "+". Same bolt, lands harder = +missile damage.
- **clarity** — A mana orb with a big "−" and a small bolt overhead. The missile still flies, but costs less mana.
- **siphon** — A skull up top, a curved arrow sweeping its life down into a mana orb. A kill pays mana back.
- **echo** — Two identical bolts running parallel, the rear one a faded ghost. Sometimes the cast comes doubled.
- **range** — A bolt at the end of a long dashed measuring line with reach-ticks and a far target mark. The same missile, thrown far.
- **arcane_orb** — A fat mana orb with a "+" and an expansion ring. The pool itself grows = +max mana. (Reads against clarity: plus vs minus.)
- **velocity** — One bolt with hard horizontal speed-streaks stacked behind it. The missile flies faster.
- **overwhelm** — A comet slamming a wounded half-heart (chipped edge), with a "+". Bonus damage against anything already hurt.
- **concussion** — A struck target ring and three descending sickly-green chevrons. The hit leaves the enemy weakened (Weakness debuff).
- **shatterpoint** — A comet cracking a *full*, bright heart into white splinters, with a "+". Bonus against a target at full health. (Reads against overwhelm: whole heart vs wounded heart.)
- **seeker_missile** (capstone) — A curved homing trail hooking up into a locked red reticle. The bolt bends to chase the enemy.
- **lance** (capstone) — A blazing straight beam driven through a row of dark foes, amethyst spearhead at the tip. Pierces everything in its path.
- **mind_well** — Three small identical bolts marching right, then one that comes out big and glowing with charge-sparks. Every so many missiles, one is empowered.
- **flow** — A mana orb with rising up-arrows and droplets feeding into it, plus a small "+". Mana regenerates over time. (Reads against arcane_orb: rising/regen vs static +max.)
- **archmage** (crown) — A nether-star ablaze with amethyst missiles streaming out in every direction, hot amethyst core. The master whose every missile bites harder.

## Notes
- MINOR fallback skipped per brief.
- Palette values sampled straight from the source sprites (see the top of `make.py`) so hand-plotted pixels sit in the game's own colours.
- The three "just a bolt" risks (magic_missile / echo / velocity / range) are kept distinct by their second element: the wand, a ghost twin, speed-streaks, and a measured reach line respectively.
- The three mana-orb nodes (clarity / arcane_orb / flow) are kept distinct by minus / plus / rising-arrows.
