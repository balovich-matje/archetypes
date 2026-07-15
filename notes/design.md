# Class mod — design notes

Working doc. Nothing here is decided yet except the premise.

## Premise

An RPG class system where **you pick your class in the first minute of a playthrough**,
from a button next to the Skills button in the inventory screen.

Explicitly *not* the RPG Series model (see [rpg-series-reference.md](rpg-series-reference.md)):

| RPG Series | Us |
| --- | --- |
| Find a Spell Binding Table, craft a spell book, bind spells with XP | Pick a class from a menu, immediately |
| Class only active while holding the right weapon + book | Class is a property of the player |
| Runes as consumable ammo | (undecided — probably no consumable) |
| Needs Spell Engine, Runes, AzureLib, Gazebos, Better Combat | Vanilla + Fabric API only, ideally |
| All active, cast-on-button abilities | (undecided — passives are an open lane) |

## Open questions

- **Which classes?** Count and names. Their space is Wizard/Paladin/Priest/Archer/
  Rogue/Warrior/Death Knight/Druid — we need our own set, and our own vocabulary.
- **Passive or active?** Specialities is entirely passive and that's been its
  character. Classes may want at least some active buttons — but "all passives" is
  a genuinely unoccupied niche in this space.
- **Can you change class later?** Free swap / cost / one-shot item / never?
- **Relationship to Specialities.** Same mod or separate? A class could gate or
  boost skills (e.g. Warrior levels Arms Mastery faster). Needs deciding before
  either grows further. Currently planned as a separate mod.
- **Progression.** Does a class level? Or is it flat identity, with Specialities'
  skills carrying the progression?
- **Balance guard.** If classes give combat power on top of Specialities' combat
  skills, the two multiply. Watch that.

## UI seed

- Button beside the Skills "S" button in the inventory, same anchoring trick
  (see `SpecialitiesClient.anchorButton` — the recipe book moves `leftPos` without
  re-running init).
- First open with no class = the class picker. Afterwards = your class page.

## Reusable from Specialities

The whole spine transfers; it's server-authoritative and already solved:

- Persistent per-player data via a Fabric attachment, synced to the owning client
  (`ModAttachments.SKILLS` + `PlayerSkills` codec pattern).
- Server → client payload for state changes (`SkillUpdatePayload`).
- Inventory-screen button injection + custom screen (`SkillsScreen`).
- Attribute-modifier passives, applied on join/respawn/change (`DefencePassives`).
- All 26.2 API gotchas already documented in `specialities/CLAUDE.md` — read that
  before writing any 26.2 code here.
