# Archetypes — design notes

Working doc. Nothing here is decided yet except the premise and the interop rule.

## Premise

An RPG class mod where **you pick your class in the first minute of a playthrough**,
from a button next to the Skills button in the inventory screen.

Explicitly *not* the RPG Series / More RPG Classes model
(see [rpg-series-reference.md](rpg-series-reference.md)):

| Them | Us |
| --- | --- |
| Find a Spell Binding Table, craft a spell book, bind spells with XP | Pick an archetype from a menu, immediately |
| Class only active while holding the right weapon + book | Archetype is a property of the player |
| Runes as consumable ammo | (undecided) |
| Needs Spell Engine, Runes, AzureLib, Gazebos, Better Combat | Vanilla + Fabric API only, ideally |
| All active, cast-on-button abilities | (undecided — passives are an open lane) |

## Naming

- Mod name: **Archetypes**, slug `archetypes` (free on Modrinth as of 2026-07-15;
  CurseForge unverified — their API blocks anonymous queries, needs a manual check).
- Publish under the title **"Archetypes — RPG Classes"**, not bare "Archetypes".
  Evidence: on a Fabric search for `class`, **all ten top hits have "class" in the
  title** — including irrelevant ones like *Classic Pipes*, because "class" prefix-
  matches "classic". Meanwhile **Wizards (RPG Series), with 4.7M downloads, does not
  appear at all**. Title tokens beat popularity by a mile. Same lesson Specialities
  learned; this time on purpose.
- Only weak name collision: *Ancestral Archetypes* (4.5k downloads) — judged acceptable.

## Interop with Specialities — the rule

**Both mods must work standalone. Neither may hard-depend on the other. Installed
together, they should feel like one system.**

### Together

- Archetypes can add its own **skills** to the skills screen — things that only exist
  because Archetypes does. Example: a caster archetype uses **mana**, so Archetypes
  contributes a skill that lowers mana cost and raises max mana as it levels.
- The skills screen gets **tabs at the top**: *Common skills* (Specialities' 15) and
  *Archetype skills* (whatever Archetypes contributes).
- Specialities must never learn what mana is. The dependency only ever points
  **Archetypes → Specialities**, never back.

### Archetypes alone (no Specialities)

- Archetypes puts the **skills button in the inventory itself** and opens the same
  screen, showing only archetype skills. No tabs, or a single tab.
- So the screen is a shared surface either mod can own; whoever's present provides it.

### Specialities alone

- Unchanged from today. It must not know Archetypes exists.

## DECIDED: no library mod. Host election instead.

**Archetypes carries its own copy of the skill engine.** There is no shared library
mod, and there never will be — users install either mod, or both, and are never asked
to install a third thing.

Rejected: extracting a `skill-core` library. The install step is the smaller half of
the cost; the real cost is version matching ("Archetypes 1.3 needs skill-core 1.2+"),
which generates confused bug reports forever. Not worth it to save ~960 lines of the
most stable code in the mod (the XP curve and attachment layer have not changed since
day one; every bug so far has been in UI or mixins).

### Host election

Exactly one mod owns the HUD bar, the inventory button and the screen:

| Installed | Host | Other mod |
| --- | --- | --- |
| Specialities only | Specialities | — |
| Archetypes only | Archetypes (own engine, own skills, no tabs) | — |
| Both | **Specialities** | Archetypes goes dormant and hands its skills over as a tab |

### Mechanism (costs the user nothing)

- Specialities defines a small public interface (`com.specialities.api.SkillTab` or
  similar) and collects tabs with
  `FabricLoader.getEntrypointContainers("specialities:skill_tabs", SkillTab.class)`.
- Archetypes takes Specialities as **`modCompileOnly`** and declares that entrypoint
  in its `fabric.mod.json`. If Specialities is absent nobody ever queries the key, so
  the adapter class never loads — no crash, no dependency, no version pin.
- Archetypes guards its own HUD/button/screen registration behind
  `FabricLoader.getInstance().isModLoaded("specialities")`.
- So Archetypes has two paths: its own internal skill type + screen (standalone), and
  a thin adapter implementing Specialities' interface (only ever loaded when
  Specialities is present).

### Day-one collisions — must be handled before both mods ship together

Not hypothetical; all three are guaranteed if Archetypes registers its UI blindly:

1. **The HUD shifts twice.** Both mods raise the vanilla hearts/XP bar by
   `HUD_SHIFT = 7` to make room. Both active = 14px. Visibly broken.
2. **Two "S" buttons** in the inventory, at the same anchor.
3. **Two skill XP bars** at identical coordinates.

### Accepted long-term costs

- **The tab interface is a public contract.** Once Archetypes ships against it,
  renaming it breaks users. Keep it tiny.
- **UI fixes land twice** (the XP-0 bar bug would have). Softened: each copy only
  affects that mod's standalone users — two products, not one product broken twice.
- **Curve drift.** If Specialities' XP curve changes and Archetypes' does not, a
  player with both sees two progression feels in one screen. Keep the curve
  consciously mirrored.

## DECIDED: the three archetypes

Three archetypes, each with a **start name** (what you pick, minute one, holding a
wooden pickaxe) and a **peak name** (the same archetype fully levelled).

| Stat | Start | Peak | Fantasy |
| --- | --- | --- | --- |
| Strength | **Brawler** | **Colossus** | Melee, face to face. Starts punching zombies bare-fisted on night one. |
| Agility | **Cutpurse** | **Nemesis** | Stealth melee *and* ranged (bows/crossbows). |
| Intellect | **Seeker** | **Oracle** | Casting. Starts with a feeble cantrip that drains mana for less DPS than a wooden sword; ends casting meteors on long cooldowns. |

The naming rule that makes the set cohere: **a humble medieval occupation becomes a
figure of myth.** The peaks are classical antiquity — the Oracle of Delphi, the
Colossus of Rhodes, Nemesis the goddess of inescapable retribution. The gap between
"nobody with a wooden pickaxe" and "thing out of a legend" *is* the arc.

Internal ids are the neutral stat (`strength`, `agility`, `intellect`) so renaming a
tier never touches saved data.

Names deliberately avoided (taken by the competition): Warrior, Rogue, Mage, Wizard,
Paladin, Priest, Druid, Archer, Death Knight, Berserker, Witcher, Forcemaster. Also
Warden — that's a vanilla mob.

## DECIDED: vanilla-look UI

**Every screen should feel like it shipped with the game.** White borders + grey
backgrounds — the classic container style, not the dark custom look of v0.1's picker.
Proof this reads as native at scale: **Pufferfish's Skills** (3.3M downloads) draws its
whole full-screen tree in exactly this style.

Exact palette, sampled from 26.2's `inventory.png` (don't eyeball it, use these):

| Element | Colors |
| --- | --- |
| Window | 1px `#000000` outline (corner pixel transparent = rounded), 2px `#FFFFFF` highlight top/left, body `#C6C6C6`, 2px `#555555` shadow bottom/right |
| Inset / slot | 1px `#373737` top/left, body `#8B8B8B`, 1px `#FFFFFF` bottom/right (the window bevel inverted) |
| Titles/labels | `0x404040` dark grey, **no shadow** (like "Crafting") |

Colored text (archetype names) stays — vanilla uses colored text freely; it's the
custom *chrome* that breaks the illusion.

## Picker screen (sketched)

- Each archetype is a square frame split by a **diagonal**: start tier top-left, peak
  tier bottom-right. Frames are vanilla insets on a vanilla window (see palette).
- Hovering a half **enlarges that art out of the frame** — e.g. Brawler is a player in
  copper armor with an iron sword; Colossus is the same player in netherite with a
  rare armor trim, dual-wielding enchanted netherite swords.
- Needs 6 pieces of art (3 archetypes x 2 tiers). Placeholder for now.
- Third-party/mod armor art would need permission from those authors first.

## Skill trees

**A mix of WoW talent trees + vanilla Minecraft UI + Skyrim's constellation design.**

Each archetype gets one tree screen holding **three sub-trees**. Every sub-tree is a
*constellation*: its connected nodes trace the outline of something representative of
the class — like Skyrim's skill constellations. **Multiple starting points, all at the
bottom, expanding upwards.**

| Archetype | Sub-tree | Constellation shape |
| --- | --- | --- |
| Brawler | **Protector** | shield |
| Brawler | **Slayer** | sword |
| Brawler | **Crusher** | mace |
| Cutpurse | **Marksman** | bow |
| Cutpurse | **Assassin** | dagger |
| Cutpurse | **Shadow** | cloak |
| Seeker | **Fire Mage** | fire |
| Seeker | **Wizard** | staff |
| Seeker | **Healer** | heart / regeneration |

- The constellation image is **background art, barely visible — outlines only**. Nodes
  and connections draw over it. Image references can come from the Minecraft wiki /
  Minecraft Dungeons wiki.
- Node graph in the vanilla style: slot-inset squares joined by dark lines on the grey
  inset canvas (the Pufferfish look), not glowing WoW arrows.
- Ranks (`2/2`), actives vs passives, costs: all undecided — see open questions.
- **Shipped placeholder first**: every node is "+1% damage" with no effect, so the
  layout/feel can be judged in-game before any balance work.
- **Next discussion: how to create the background art** (hand-pixelled? traced from
  wiki renders? generated then cleaned up?). Whatever the method, output must be
  faint grey outlines that don't fight the nodes.

## Open questions
- **Passive or active?** Specialities is entirely passive and that's been its
  character. Classes may want active buttons — but "all passives" is a genuinely
  unoccupied niche here.
- **Mana** (or any resource): only if an archetype needs it. Don't invent a resource
  for its own sake.
- **Can you change archetype later?** Free / costly / one-shot item / never?
- **Does an archetype level?** Or is it flat identity, with skills carrying progression?
- **Balance guard.** If archetypes grant combat power on top of Specialities' combat
  skills, the two multiply. Watch that.

## UI seed

- Button beside the Skills "S" button in the inventory, same anchoring trick
  (see `SpecialitiesClient.anchorButton` — the recipe book moves `leftPos` without
  re-running init).
- First open with no archetype = the picker. Afterwards = your archetype page.
- Skills screen gains a tab strip at the top once there's more than one source.

## Reusable from Specialities

The whole spine transfers; it's server-authoritative and already solved:

- Persistent per-player data via a Fabric attachment, synced to the owning client
  (`ModAttachments.SKILLS` + `PlayerSkills` codec pattern).
- Server → client payload for state changes (`SkillUpdatePayload`).
- Inventory-screen button injection + custom screen (`SkillsScreen`, `SkillIcons`).
- Attribute-modifier passives applied on join/respawn/change (`DefencePassives`).
- All 26.2 API gotchas already documented in `specialities/CLAUDE.md` — read that
  before writing any 26.2 code here.
