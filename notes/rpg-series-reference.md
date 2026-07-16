# Existing class mods — reference notes

Competitive reference only: what classes exist in the RPG Series, the general idea
of each, and their skill names. Gathered 2026-07-15 from each mod's Modrinth page
and its public source repo (spells are data files, so these lists are exact rather
than guessed).

**Not a design target — see [design.md](design.md) for what we're doing differently.**

> There is a **second, separate family**: **"More RPG Classes"** — Archers Expansion
> (1.3M), Elemental Wizards (550k), Berserker (436k), Witcher (435k), Forcemaster
> (414k), plus its own Skill Tree. ~3.2M downloads combined, different authors.
> Notably **they, not the RPG Series, own the `class` search** on Modrinth, purely
> because "Classes" is in their titles. Not yet examined in detail — worth a pass
> before we finalise our archetype list.

## The series at a glance

| Mod | Downloads | Classes it adds |
| --- | ---: | --- |
| [Wizards](https://modrinth.com/mod/wizards) | 4.7M | Wizard (Arcane / Fire / Frost) |
| [Paladins & Priests](https://modrinth.com/mod/paladins-and-priests) | 2.9M | Paladin, Priest |
| [Archers](https://modrinth.com/mod/archers) | 2.7M | Archer |
| [Rogues & Warriors](https://modrinth.com/mod/rogues-and-warriors) | 2.0M | Rogue, Warrior |
| [Death Knights](https://modrinth.com/mod/death-knights) | 1.2M | Death Knight (Blood / Unholy / Frost) |
| [Druids](https://modrinth.com/mod/druids) (RPG Series *Plus*, different author) | 34k | Druid |

Support mods in the same series (not classes): Jewelry, Gazebos, Relics, Arsenal,
Armory, Village Taverns, Skill Tree, Loot Tweaks.

Most are built by ZsoltMolnarrr on the **Spell Engine** ecosystem; Death Knights
(nvb-uy) and Druids (Rulft44) are separate authors. All openly cite **World of
Warcraft** as the inspiration — the class and spell names mostly come straight
from it.

## How you become a class (the part we're rejecting)

Same loop across the whole series:

1. Get the right weapon — Wand/Staff for casters, heavy weapons for Paladin/Warrior,
   daggers for Rogue, bow for Archer. The weapon carries your first spell.
2. Find a **Spell Binding Table** — in a village Gazebo (a separate mod) or build
   one yourself, surrounded by bookshelves like an enchanting table.
3. Craft a **Spell Book** for the class, then spend XP levels binding spells into it
   to unlock them one by one.
4. To use skills: **equip the spell book AND hold a matching weapon.**
5. **Runes** are consumable ammunition for casting spells (crafted via the Runes mod).

Consequences worth noting:
- You're not a class until you've found a structure and done a crafting chain — easily
  hours in.
- Your class is only "on" while holding the right weapon + book. It's an equipment
  loadout, not an identity.
- Requires a stack of dependencies: Spell Engine, Runes, AzureLib Armor, Gazebos,
  usually Better Combat.

## Classes and their skills

### Wizard — three spell books, elemental caster
Uses Wands/Staves. Requires Runes as ammo.

- **Tome of Arcane** — single-target/burst against isolated or clustered foes:
  `arcane_missile`, `arcane_explosion`, `arcane_beam`, `arcane_barrage`,
  `arcane_blink`, `arcane_evocation`
- **Tome of Fire** — wide-area damage:
  `fire_breath`, `fire_slash`, `fire_meteor`, `fire_storm`, `fire_wall`, `fire_hydra`
- **Tome of Frost** — damage plus slows and self-shielding:
  `frost_nova`, `frost_spikes`, `frost_shield`, `frost_lance`, `frost_blizzard`,
  `frost_elemental`

Also: tiered robes/staves/wands, spell enchantments (Spell Infinity, Spell Power,
Spell Haste), Wizard Towers in villages.

### Paladin — heavy-weapon support/bruiser
**Paladin Libram**, used with hammers/greatswords, optionally a shield.
Skills: `flash_heal`, `divine_protection`, `judgement`, `battle_banner`, `immolation`

### Priest — holy caster, healer
**Holy Book**, used with Holy Wands/Staves.
Skills: `holy_beam`, `circle_of_healing`, `barrier`, `lightwell`
(`heal`, `holy_shock`, `lightwell_orb` also exist in data but aren't in the book tag.)

### Archer — ranged
**Archery Manual**. Works with any bow/crossbow, including modded.
Skills: `power_shot`, `entangling_roots`, `barrage`, `rain_of_arrows`, `magic_arrow`,
`spirit_wolf`

Also: Auto-Fire Hook gadget (auto-release fully charged shots), and rebalanced
ranged enchantments (Power +8%/level instead of +50%).

### Rogue — evasion and tricks
**Rogue Manual**, best with dual-wielded fast weapons (daggers, sickles).
Skills: `slice_and_dice`, `shock_powder`, `shadow_step`, `vanish`, `bear_trap`,
`mutilate`

### Warrior — heavy weapons, weaken and smash
**Warrior Codex**, best with slow heavy weapons (double axes, glaives, greatswords).
Skills: `throw`, `throw_net`, `charge`, `shout`, `last_stand`, `mortal_strike`

### Death Knight — three specialisations
Skill books per spec, plus **Runecarving** (fuse runes into weapons to make Runic
Weaponry) and the Death's Call armor set upgraded per spec (Plaguebringer / Frozen
Champion / Crimson Guard).

- **Blood** — `blood_boil`, `death_strike`, `marrowrend`, `dark_command`
  (buffs: Bloodthirst, Marrowshield; debuffs: Blood Plague, Enraged)
- **Frost** — `frost_strike`, `obliterate`, `remorseless_winter`, `breath_of_agony`
- **Unholy** — `death_coil`, `death_grip`, `epidemic`, `plagues`

### Druid — nature caster, DoT/control
**Druidic Grimoire**, used with Nature Staves/Wands.
Skills: `vine_whip`, `bramble_shot`, `bramble_volley`, `dart_shot`, `barkskin`,
`mass_entanglement`, `weapon_nature_root`
Uses a tiered passive system (`druid_tier_N_passive_N`) unlike the others.

## Observations for our design

- **7 distinct classes, ~5–6 skills each** is the shape of the space. Wizard and Death
  Knight subdivide into 3 specs; that's how they get depth.
- **Names are lifted from WoW** almost verbatim (Mortal Strike, Death Grip, Last Stand,
  Slice and Dice). We should pick our own vocabulary — both to avoid looking like a
  clone and because we're not building a WoW tribute.
- **Everything is active abilities** — cast on a button, cost Runes, need the weapon.
  Nobody in this series does purely passive class identity. That's an open lane, and
  it fits how Specialities already works.
- **The weapon coupling is the core design choice we disagree with**: your class only
  exists while holding the right item. We want the class to be *who you are*, chosen
  in minute one.
