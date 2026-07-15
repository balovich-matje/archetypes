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

## The architectural question this raises

If Archetypes needs a working skill screen + XP engine on its own, and Specialities
already has one, who owns the engine?

- **A. Duplicate a minimal engine in Archetypes.** Fully independent, no shared
  artifact, but two copies of the XP curve / attachment / sync / HUD code to keep in
  step. When both are installed, one has to defer to the other, and skills would live
  in two separate attachments.
- **B. Extract a shared library mod** (e.g. `skill-core`) that both depend on. Clean
  and no duplication, but forces every user of either mod to install a third one —
  real friction on the mod page, and it's a permanent public API to maintain.
- **C. Specialities owns the engine and exposes an API; Archetypes soft-depends.**
  Archetypes compiles against Specialities (`compileOnly`) and guards every call
  behind `FabricLoader.getInstance().isModLoaded("specialities")`; classes touching
  the API only load when the guard passes. Standalone Archetypes then needs its own
  fallback screen anyway — so it's B's complexity plus A's duplication, unless the
  fallback is deliberately minimal.

Leaning **C with a deliberately thin fallback**, but this is the decision that shapes
everything else and should be made before either mod grows further. Worth prototyping
the standalone-Archetypes screen first to see how thin "thin" really is.

## Open questions

- **Which archetypes?** Count and names. Their space is Wizard/Paladin/Priest/Archer/
  Rogue/Warrior/Death Knight/Druid/Berserker/Witcher/Forcemaster — we need our own set
  and our own vocabulary.
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
