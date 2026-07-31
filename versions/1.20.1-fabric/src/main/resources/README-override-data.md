# Why this node overrides 19 recipes and 19 advancements

Read the 1.21.1 node's README beside its own overrides first — the reason is the same one
(the recipe schema changed at 1.21.2, and the seven `#minecraft:*_tool_materials` tags do not
exist below 1.21.11), and the tag mapping is identical. What follows is only what is
DIFFERENT here. The two nodes' files are not copies of each other and must not be kept in
step by copying one over the other; `scratchpad/s5port/gen_legacy_data.py` regenerates both
from the shared tree.

## Three further schema deltas, all `<1.21`

| | 1.21.1 | here |
|---|---|---|
| recipe result | `{"count": 1, "id": "archetypes:x"}` | `{"item": "archetypes:x"}` |
| `ItemPredicate` item | `{"items": "minecraft:stick"}` | `{"items": ["minecraft:stick"]}` |
| `ItemPredicate` tag | `{"items": "#minecraft:planks"}` | `{"tag": "minecraft:planks"}` |

All three read out of `data/minecraft/recipes/iron_sword.json` and
`data/minecraft/advancements/recipes/combat/iron_sword.json` in the 1.20.1 server jar.

## The directory names are PLURAL here, and these files are not

`recipes/`, `advancements/` — the same rename that took `tags/items` to `tags/item` took the
whole datapack registry directory set with it, at 1.21. Measured on the two vanilla jars:

```
1.20.1  advancements/ loot_tables/ recipes/ structures/ tags/
1.21.1  advancement/  loot_table/  recipe/  structure/  tags/
```

These override files are nevertheless authored at the SINGULAR shared path, so that they sit
where the file they replace sits. `build.fabric.gradle.kts` relocates the whole tree to the
plural names on this node, and the relocation runs over the override exactly as it runs over
the shared copy. Do not "fix" the paths here to plural: that would put the override at a path
the shared file never occupies, and the broken shared copy would ship alongside it.

## The twentieth recipe is GONE, not rewritten

`breeze_wand` — `minecraft:breeze_rod` is a 1.21 item and there is no legacy spelling of it.
The recipe and its unlock advancement are excluded in the node script (see the comment there);
the Breeze Wand ITEM is still registered and still works, it just cannot be crafted here.
Substituting a different ingredient was rejected on purpose: the same item costing something
different on one node is a silent balance divergence.

## And one tag: `tags/item/meat.json`

`#minecraft:meat` is `>=1.21.1`; 1.20.1 has no such tag, and `#minecraft:fishes` (which it
also references) does exist here. A tag that fails to load is not a partial load — it is a
whole missing tag, and every `#archetypes:meat` reference cascades off it, which is R-16's
lesson and is why the failure showed up as a POSITIVE probe (`clear @a #archetypes:meat`
answering `Unknown item tag`) rather than as a quiet behaviour change.

`required: false` was rejected: it would make the tag load and hold nothing but fish, so
`ColossusProtector.MEAT` would silently stop naming meat on exactly one node. The override
writes out the ELEVEN items `#minecraft:meat` holds on 1.21.1 / 1.21.11 / 26.1 / 26.2 — read
out of all four server jars, which agree exactly — and keeps the `#minecraft:fishes`
reference the shared file has. Same set, same behaviour, one node's spelling.
