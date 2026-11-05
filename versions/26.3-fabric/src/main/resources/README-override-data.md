# Why this node ships brewing recipes as JSON

26.3 made brewing data driven and removed `FabricPotionBrewingBuilder`, so the code arm in
`ManaPotions`/`AmnesiaPotions` has nothing to call here. These 30 files are that table, for this
node only: shared resources ship into every node's jar, and no older version can parse
`minecraft:brewing`.

Vanilla also stopped deriving the throwable forms for free. Below 26.3 one mix covered every bottle
type, and gunpowder and dragon's breath converted any potion. Each now needs its own file, so:

- 6 mixes x 3 bottle types = 18
- 6 potions x gunpowder (potion to splash) + dragon's breath (splash to lingering) = 12

Same shape as vanilla's `data/minecraft/recipe/brewing/`. Keep in step with the Fabric arms in the
two potions classes. `README-override*.md` is excluded from the jar by the node script.
