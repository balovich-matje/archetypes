# Why this node overrides the 20 recipes

`data/archetypes/recipe/*.json` here REPLACES the shared copy of the same name, file for
file. Nothing about the recipes' content differs — same ingredients, same patterns, same
results. Only the **schema** does, and it changed at exactly 1.21.2.

## The two spellings

| | shared tree (26.2 / 26.1 / 1.21.11) | here (1.21.1) |
|---|---|---|
| ingredient | `"X": "#minecraft:planks"` | `"X": {"tag": "minecraft:planks"}` |
| ingredient | `"#": "minecraft:stick"` | `"#": {"item": "minecraft:stick"}` |
| result | `{"id": "archetypes:x"}` | `{"count": 1, "id": "archetypes:x"}` |

Both were read out of the matching vanilla server jar (`data/minecraft/recipe/iron_sword.json`
and `wooden_sword.json` on each), not from memory.

## The seven tags that do not exist here

`#minecraft:*_tool_materials` are `>=1.21.11`. Each is replaced by what the tag actually holds
there — verified by reading it out of the 26.2, 26.1 and 1.21.11 server jars, which all agree:

```
wooden    -> #minecraft:planks          (a tag, and it exists here)
stone     -> #minecraft:stone_tool_materials  (exists here too, same three values)
iron      -> minecraft:iron_ingot
gold      -> minecraft:gold_ingot
diamond   -> minecraft:diamond
netherite -> minecraft:netherite_ingot
copper    -> minecraft:copper_ingot
```

An ingredient naming a tag that does not exist is **not** a load error — the recipe parses and
then never matches anything. That is why the mapping is written out rather than left to a
`required: false` style shrug, and it is the same silent-cascade lesson as R-16.

## How this was found, and what it says about the gates

`/recipe give @a archetypes:<id>` on a dedicated server, with a bogus control. It found the
schema break on **this** node too, in a smoke log Stage 4 had already called green: the parse
failures are `ERROR` lines the gate simply never grepped for, and the mod boots and plays
perfectly well with all twenty recipes missing. The smoke driver now greps for them.

## Advancements are NOT overridden here

The 20 recipe advancements parse unchanged on this node — measured, zero errors — because
`ItemPredicate.items` is a HolderSet string here and accepts `"#tag"` directly. Only 1.20.1
needs them rewritten, and only there do they exist as an override.

**Keep in step with `src/main/resources/data/archetypes/recipe/`.** A new or edited recipe
means editing the copy here too; `scratchpad/s5port/gen_legacy_data.py` regenerates both
legacy nodes' copies from the shared tree.
