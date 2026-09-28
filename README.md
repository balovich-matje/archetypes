# Archetypes

An RPG class mod for Minecraft — pick your archetype in the first minute, from the
inventory screen. No crafting station, no spell book, no weapon coupling.

Fabric 26.2 / 26.1 / 1.21.11 / 1.21.1 / 1.20.1, NeoForge 1.21.1, Forge 1.20.1.
Download: https://modrinth.com/mod/archetypes

- [notes/design.md](notes/design.md) — the premise, the Specialities interop rule, and
  the open questions
- [notes/rpg-series-reference.md](notes/rpg-series-reference.md) — what the existing
  class mods actually ship, for reference

Designed to stand alone, and to interlock with
[Specialities](https://github.com/balovich-matje/specialities) when both are installed.

## Modpack / mod compatibility

Every weapon or item check an ability or passive makes is an **item tag** in the
`archetypes` namespace. Add your own items to these tags with a datapack (or ship the tag
in your mod's jar) and Archetypes treats them exactly like the vanilla ones. No config, no
code. Tags are re-read on `/reload`.

| Tag | What it gates | Default contents |
|---|---|---|
| `#archetypes:greatswords` | Decimate, the greatsword Slayer passives (Heavy Blows, First Blood, Executioner...), the two-handed off-hand lock, the greatsword whoosh | Archetypes' seven greatswords |
| `#archetypes:swords` | Bladestorm, Blade Master and the other one-handed-sword Slayer passives | `#minecraft:swords` (+ `#c:swords` where it exists). Items also in `greatswords` or `daggers` do **not** count as swords |
| `#archetypes:daggers` | Every dagger node of the Assassin (Shadow Step, Razor Edge, Expose, Flense, coatings, Twin Fangs...), the dagger dodge, "daggers never sweep" | Archetypes' seven daggers |
| `#archetypes:maces` | Quake, Meteor, Shockwave, Bare-Knuckle's mace half, Unstoppable Force, Titan's Leap | `minecraft:mace`, `#c:tools/mace` (both optional: 1.20.1 has no mace, so the tag is empty there until a pack adds one) |
| `#archetypes:bows` | True Shot, Acrobatics (roll while drawing), Nimble Draw, Deadeye, the Marksman arrow passives | `minecraft:bow`, `#c:tools/bow`, `#forge:tools/bows` |
| `#archetypes:crossbows` | Snap Shot's crossbow branch, Vault, Rapid Reload, Deadeye, the Marksman arrow passives | `minecraft:crossbow`, `#c:tools/crossbow`, `#forge:tools/crossbows` |
| `#archetypes:shields` | Shield Bash / Shield Sweep (which hand, and the two-shield bonus) | `minecraft:shield`, `#c:tools/shield`, `#c:shields`, `#forge:tools/shields` |
| `#archetypes:wands` | Every Seeker spell and Magic Armaments | Archetypes' six wands |
| `#archetypes:combat_weapons` | Held in either hand, stops a Seeker's mana regeneration | all of the weapon tags above, `minecraft:trident` (+ its `c:`/`forge:` tags), `#minecraft:spears` |
| `#archetypes:meat` | Hearty Meal's Strength | `#minecraft:meat`, `#minecraft:fishes` (1.20.1 has no `#minecraft:meat`; the same eleven meats are listed out there) |
| `#archetypes:fruit` | Hearty Meal's Speed | apple, golden and enchanted golden apple, melon slice, sweet and glow berries, chorus fruit |
| `#archetypes:milk` | Hearty Meal's Regeneration | `minecraft:milk_bucket`, `#c:drinks/milk`, `#c:milk_buckets` |

Every `c:`/`forge:` entry is optional and was checked against the loader it comes from; on
a bare install each one holds only the same vanilla item, so nothing changes for vanilla
weapons. On 1.20.1 an arrow does not remember the weapon that fired it, so there the
Marksman arrow passives apply to any fired arrow or spectral arrow, whatever the bow. What is
**not** a tag, on purpose: "can this item block" is the item's own blocking
behaviour, and "is the bow drawn" is whether it is in use — those already work for any
modded shield or bow.

### Example: Simply Swords heavy weapons as greatswords

`my_pack/data/archetypes/tags/item/greatswords.json`:

```json
{
  "replace": false,
  "values": [
    { "id": "#simplyswords:heavy_weapons", "required": false }
  ]
}
```

plus a `my_pack/pack.mcmeta` with your version's `pack_format`. Drop the folder into the
world's `datapacks/` (or use a global-datapack mod) and run `/reload`. Keep
`"replace": false` so the built-in greatswords stay in, and `"required": false` so the pack
still loads without that mod. Greatswords are two-handed here: the off-hand is locked while
one is in the main hand.

**Minecraft 1.20.1 (Fabric and Forge): the folder is `tags/items/` (plural)** —
`data/archetypes/tags/items/greatswords.json`. 1.21 renamed it to `tags/item/`.

To check a tag took: `/clear @s #archetypes:greatswords 0` counts matching items in your
inventory without removing anything.
