# Archetypes — architecture

A Fabric 26.2 RPG-class mod. A player picks one **archetype** — Brawler
(`STRENGTH`), Cutpurse (`AGILITY`), or Seeker (`INTELLECT`) — in the first minute
of a playthrough. Each archetype has three **sub-trees** (constellation-shaped
skill trees), levels off a mirror of vanilla XP, and casts/swings active
abilities bound to four keys. This document is for someone extending the mod;
every claim below is grounded in the source under `src/`.

Package layout: gameplay/server logic in `src/main/java/com/archetypes`, the
portable state/wire key tables in `.../state`, the three loader seams in
`.../platform`, mixins in `.../mixin`, the Specialities soft-dependency shim in
`.../compat`, and all client/render/HUD/screen code in
`src/client/java/com/archetypes/client`. The `main` entrypoint is `Archetypes`
(`ModInitializer`); the `client` entrypoint is `ArchetypesClient`
(`ClientModInitializer`).

## Binding rules for anyone touching this tree

Two documents outrank this one and have to be read before the first edit:

1. **`../specialities/docs/MULTIVERSION-CONVENTIONS.md` — BINDING HERE TOO.** It is Skill
   Proficiencies' multi-version playbook and it is the ruleset for this repo as well, not a
   neighbouring project's housekeeping. In particular: §3's **frozen predicate vocabulary**
   (inventing a synonym for an existing boundary is the likeliest silent-divergence bug there
   is), §4's `//?` syntax and its **crash-level prohibition on directives in any `.json`**,
   §5a **mixin annotations fork, balance logic never**, §5b arithmetic stays outside a
   conditional, §5e **Java 17 is the shared-code ceiling**, §5g the seam-hygiene rule, §5h
   full descriptors on every mixin target, and §6's closed findings R-10/R-11/R-16/R-17/
   R-18/R-20/R-22. Never fetch `stonecutter.kikugie.dev` (R-13).
2. **`docs/MULTIVERSION.md` — this repo's port design**, including the stage plan, the seam
   shapes, the per-node deltas and the risk register. Its "Stage 0 outcomes" table records
   where measurement has already overtaken the plan.

The three **seams** exist so that the workspace conversion is mechanical, and a grep is the
review gate for each: nothing outside `com/archetypes/platform/` may name `AttachmentTarget`,
`AttachmentType`, `AttachmentRegistry`, `AttachmentSyncPredicate`, `PayloadTypeRegistry`,
`ServerPlayNetworking`, `ClientPlayNetworking`, `FabricLoader` or any NeoForge/Forge API. A
new call site elsewhere is drift to catch in review.

## 1. Big picture

### Server-authoritative attachments

All persistent and transient per-player state is declared as a table of
`state.StateKey` records in **`ModState`** (74 of them) and stored by
**`platform.ArchetypeStore`**, whose Fabric implementation turns each key into a
Fabric attachment. The server is the only writer; clients read a synced copy.
Read and write it as `ArchetypeStore.INSTANCE.get(entity, ModState.FOO)` —
`ModState` names the state, the seam stores it, and the key table itself carries
no loader import and no Minecraft API newer than 1.20.1 so that it ports
unchanged. Two sync scopes are used (`StateKey.Sync`, which the Fabric store maps
onto `AttachmentSyncPredicate`):

- `TARGET_ONLY` / `AttachmentSyncPredicate.targetOnly()` — synced to the owning client only.
  Used for private state: `ARCHETYPE`, `ARCHETYPE_XP`, `SPENT_POINTS`,
  `PURCHASED`, `MANA`, and the per-ability `*_READY_AT` cooldown timestamps
  (all but `DISENGAGE_READY_AT`, which stays server-side and unsynced).
- `ALL_TRACKING` / `AttachmentSyncPredicate.all()` — synced to everyone. Used for state other
  players' renderers need: `BULWARK_ACTIVE`, `ARMOR_HIDDEN`, `DECIMATE_SWING_AT`,
  `BLADESTORM_END`, `QUAKE_CHARGE_END`, `RADIANCE_END`, `DEADEYE_END` (which the
  owner's client also needs, because it predicts a crossbow's charge time), the
  per-arrow `DEADEYE_ARROW`, and `MARKED_BY` — which is the one attachment that
  lives on a *non-player* entity to describe it: Death Mark writes the assassin's
  entity id onto the marked creature, so a client asks the body who is hunting it
  instead of being handed anyone's roster.

Some attachments are `.persistent(codec)` and `.copyOnDeath()` (the archetype,
its XP, owned nodes, mana); others are transient (cooldown timestamps, proc
bookkeeping like `MISSILE_CAST_COUNT`, `SMASH_AT`). `ModState.get(player)`
resolves the stored `ARCHETYPE` string to an `Archetype`; `set`/`clear` write it.

The key design consequence: because the owning client holds a synced mirror,
purchase rules (`NodePurchases.check`) and cost math (`SkillPoints`,
`SeekerSpells.elementCost`) run **identically on both sides** — the client paints
a node buyable or a spell affordable using the same code the server re-validates
with.

### Payload flow

Networking is small. Serverbound (client → server) play payloads, all registered
in `Archetypes.onInitialize`:

| Payload | Meaning |
| --- | --- |
| `PickArchetypePayload` | choose an archetype (ignored if one is already set) |
| `ResetArchetypePayload` | creative-only wipe of the choice |
| `BuyNodePayload` | spend a point into `(subTreeId, node)` |
| `ActiveAbilityPayload(slot)` | fire ability key `slot` (0–6) |
| `SpellChannelPayload` | one Flamethrower channel tick while the key is held |
| `MeleeSwingPayload` | announce a charged swing (the greatsword whoosh) |
| `RushPayload` / `DisengagePayload` | Shield Rush / the Marksman's Acrobatics roll (`AgilityActives.acrobatics`; the payload keeps its older Disengage name) |

Clientbound (server → client): `PassiveProcPayload` — a fire-and-forget "this
passive just fired" flash for `ProcIndicatorHud` — and `ParrySwingPayload`, the
raw `attackStrengthTicker` a landed parry earned, which the client installs
verbatim (the server is the only side that knows whether the hit paying for a
parry ever arrived, so this one number cannot be derived).
Everything else the client needs (levels, cooldowns, mana) rides the synced
attachments, so there is no bespoke state packet.

`ActiveAbilityPayload` carries a **slot index, not an ability id**: slots 0–2 are
the archetype's three sub-trees in screen order, slot 3 is the capstone key. The
server resolves what a slot casts from `SubTree.of(archetype).get(slot)` and, for
Strength trees, the held weapon (`ModItems.isGreatsword` → Decimate,
`isSword` → Bladestorm, `Items.MACE` → Quake, else → Haymaker). See the dispatch
`switch` in `Archetypes.onInitialize`.

### The ticker pattern

Ongoing per-tick effects live in classes named `*Ticker` (and event-driven ones
in `*Combat`), each exposing a static `initialize()` called from
`Archetypes.onInitialize`. The canonical shape is `SeekerTicker`: register a
`ServerTickEvents.END_SERVER_TICK` listener, iterate `getPlayerList()`, gate on
the archetype, and act. `ProtectorTicker`, `SlayerTicker`, `CrusherTicker`,
`AgilityTicker`, `ShadowTicker`, and `SeekerTicker` maintain auras, cooldown
bookkeeping, and mana regen this way.

A ticker is also this mod's **only** lifecycle for a standing attribute
modifier — there is no purchase-time apply and no `DefencePassives`-style
respawn hook. A node that grants an attribute permanently (Battle Trance's
`MAX_ABSORPTION` ceiling, whose only condition is owning the node and whose
amount moves when the epic Bulwark is bought) is asserted by its ticker every
tick through the local `apply`/`stance` helper, which adds the modifier when it
is missing, rewrites it when its amount drifts, and removes it when the
condition drops. That is what makes it respawn-, relog- and respec-safe without
any event of its own: attributes and transient modifiers do not survive a death,
and the loop puts them back a tick later. It is also why every one of these
loops walks **every** player rather than filtering on archetype — a node revoked
by Amnesia II or the creative reset has to be taken back off by the same pass
that put it on. `SlayerCombat`, `AgilityCombat`, and
`SeekerCombat` instead hook combat/entity events; `BlizzardZones` runs its zone
pulses off an `END_SERVER_TICK` listener of its own.

## 2. The tree system

### Enums

`Archetype` (STRENGTH/AGILITY/INTELLECT) and `SubTree` (nine values) are the
spine. `SubTree.of(archetype)` returns the three sub-trees in left-to-right
screen order (e.g. STRENGTH → `PROTECTOR, SLAYER, CRUSHER`). Each `SubTree`
carries its owning `Archetype`, a wire `id()`, a stand-in `Item` icon, and a
`Constellation` (its node layout). `SubTree.byId` resolves a wire id back, or
null for garbage from the client.

### Constellations

`Constellation` is a node graph authored as an ASCII grid in `Constellations`
(one `public static final Constellation` per sub-tree). `Constellation.of(grid)`
parses `'#'` as a node; **row 0 is the bottom** (trees root at the bottom and grow
up), and edges are derived by 8-connectivity, so touching cells become connected
nodes and an outline becomes a ring. Two authored extras:

- `.withEdge(c1,r1,c2,r2)` — a real extra edge (renders **and** counts for
  purchase adjacency), used for the capstone cross where both pre-capstones reach
  both capstones.
- `.withDecorativeEdge(...)` — a cosmetic line that closes a silhouette but does
  **not** count for adjacency.
- `.withRoots(col, row, ...)` — the tree's entry points, named explicitly
  instead of "every node on the bottom row". For a shape that puts column feet
  on the same row as the root meant to feed them.

A node's stable identity is its **index** into `constellation().nodes()`.
`PURCHASED` stores owned indices per sub-tree id, so grids must not be reordered
casually — indices are saved data.

### Per-tree `*Nodes` classes

Each sub-tree has a `*Nodes` class (`ProtectorNodes`, `SlayerNodes`,
`CrusherNodes`, `MarksmanNodes`, `AssassinNodes`, `ShadowNodes`,
`ElementalistNodes`, `WizardNodes`, `PriestNodes`) built to one convention:

- A nested `enum Family` — one constant per skill, plus a `MINOR` sentinel for
  inert placeholder nodes. Each `Family` carries its icon strategy (item
  supplier, hand-made `sprite()`, and/or `overlay()`) and derives `nameKey()` /
  `descriptionKey()` lang keys from `node.archetypes.<tree>.<family>`.
- A `record Def(Family family, int rank)`.
- `private static Map<Integer, Def> build()` — maps grid cells to `Def`s using a
  `cell(col,row)` packed-long key, then translates cell → node index against the
  constellation. `build()` throws `IllegalStateException` if the grid's node
  count and the mapping disagree, so a drifted grid fails loudly at class-load.
- `def(tree, index)` returns the node's `Def` (falling back to a `MINOR` def for
  unmapped indices), and `rank(tree, owned, family)` counts how many owned nodes
  share a family — that count *is* the family's earned rank. Multi-rank skills are
  chains of same-family nodes; rank is count-based, so grid adjacency keeps a
  chain contiguous but buy order within it never matters mechanically.

### `TreeNodes` — the dispatch face

`TreeNodes` is the tree-agnostic front over the nine `*Nodes` classes: the screen
and purchase rules call `TreeNodes.nameKey`, `descriptionKey`, `icon`, `rankOf`,
`familySize`, `isMinor`, `kind`, and the icon-resolution helpers without knowing
which tree they're looking at (each method is a `switch (tree)`). Gameplay code
still calls the concrete `*Nodes` directly. `TreeNodes.kind` classifies a node as
`ACTIVE`, `CAPSTONE`, or `NORMAL` for display; `TreeNodes.pickerActives` lists the
one preview active per tree shown on the picker (pinned explicitly, not derived).

### Purchase rules and the caps

`NodePurchases` owns buy logic. `NodePurchases.check` returns a `Verdict`
(`BUYABLE`, `OWNED`, `NOT_CONNECTED`, `NO_POINTS`, `TREE_FULL`,
`EXCLUSIVE_TAKEN`) so the screen can explain *why* a node is locked. A node is
buyable when it is a root (`Constellation.isRoot`) or adjacent to an owned
node, not excluded by a capstone, under the per-tree cap, and the player has a
point free. A tree's roots are its bottom row unless it declares them with
`withRoots`; only `COLOSSUS_CRUSHER` does, because its bottom row carries the
feet of both columns as well as Titan's Leap, and the leap has to stay the way
in.
`NodePurchases.buy` is server-only, re-runs `check`, appends the index to
`PURCHASED`, and increments `SPENT_POINTS`.

- **15-point cap**: `SkillPoints.MAX_POINTS_PER_SUB_TREE = 15`, below each tree's
  node count, so a full build fills one tree's budget with utility, damage, or a
  compromise — never everything. `SkillPoints.BASE_LEVEL_CAP = 45` = 3 × 15, so a
  peak-tier archetype has exactly enough normal points for all three budgets and
  no budget covers its own tree.
- **Epic tier**: levels 46–`MAX_LEVEL = 60` each grant one **epic point** instead
  of a normal one (`EPIC_SPENT_POINTS` tracks the spends; the pools never mix —
  `check`/`buy` pick pool and cap off `SubTree.isEpic()`). Every base tree now
  has an epic sub-tree: `ORACLE_ELEMENTALIST`/`ORACLE_WIZARD`/`ORACLE_PRIEST` on
  Intellect, `NEMESIS_MARKSMAN`/`NEMESIS_ASSASSIN`/`NEMESIS_SHADOW` on Agility,
  `COLOSSUS_PROTECTOR`/`COLOSSUS_SLAYER`/`COLOSSUS_CRUSHER` on Strength. They are
  upgraded siblings of base trees (`epicCounterpart()`/`baseCounterpart()`), capped at
  `MAX_POINTS_PER_EPIC_SUB_TREE = 5` each, reached via the per-section switcher
  on the tree screen, and excluded from `SubTree.of` so the picker, legends and
  slot dispatch stay on the three base trees. Their actives ride
  `ActiveAbilityPayload` slots 4–6, so there are seven ability keys, not four:
  an epic tree takes slot `4 + N` where `N` is its base tree's place in
  `SubTree.of`, and archetypes share those three keys (slot 4 is Lightning
  Strike or Deadeye, slot 5 Magic Armaments, Death Mark or the Colossus
  Slayer's Parry, slot 6 the Dark Ritual or Titan's Leap — the dispatch picks
  on archetype). Two epic trees claim no key: Oracle Priest's Aura of Radiance
  is painted `ACTIVE` but fires off a Holy Light cast, and Colossus Protector's
  root is a flat passive.
- **Exclusive capstone pairs**: `TreeNodes.exclusiveTaken(tree, owned, index)`
  encodes each tree's mutually-exclusive capstones (owning one locks the other),
  e.g. Slayer's Bladestorm|Decimate, Crusher's Quake|Haymaker, Protector's
  Omni Block(`OMNI_BLOCK`)|Shield Sweep(`GROUND_SLAM`). Elementalist is special: its four capstones
  are **one choice total** — any owned capstone locks the other three.

### Compacting a family instead of redrawing a grid

A node's identity is its index into `constellation().nodes()`, and `PURCHASED`
stores indices — so the cheapest safe way to free a cell for a new skill is to
**shorten an existing family's chain and remap the cell**, never to edit the
ASCII art. Editing a `'#'` renumbers every node after it and silently
repurposes saved data.

The same move works in reverse, to delete a skill without deleting a cell.
Reinforced Straps was one node at `(0,7)` — a flat Unbreaking I on the blocking
item. It was folded away and its cell became Reflection's second rank, so
`REFLECT` runs `(0,7)`→`(0,8)` at x0.5/x1.0 returned damage. No migration code
existed because none was needed: `PURCHASED` stores indices, so an owner of
Straps owned the same index and it read as Reflection I, and an owner of both
read Reflection II. Note what this does NOT do — it frees no cell. A rank **is**
a cell in this system, so folding two one-rank skills into one two-rank skill is
exactly cell-neutral; the only way to free one is the compaction below.

(Straps has since come back, on `(4,4)` and under the `SPEARWALL` constant,
which is the other half of the same lesson: a family's CONSTANT is the save key
and the derivation root for its sprite path and both lang keys, so it never
tracks the title. That cell has been a fourth rank of Quick Recovery, Spearwall
and now Reinforced Straps without one index moving.)

The Protector is the worked example. Quick Recovery (`COOLDOWN`) ran four cells
up the centre column at a fifth of the bash's ability layer each; it runs three
at `Tuning.RECOVERY_PER_RANK = 4/15`, which lands on the same −80% it always
did. The node kept its ceiling, gave back a point, and cell `(4,4)` went to
`SPEARWALL` — grid untouched, all 25 indices unchanged. The cell was worth
having for its edges as much as its emptiness: `(4,4)` is grid-adjacent to
Recovery below and diagonally to *both* crown nodes, so the new node gates into
Braced and Taunt exactly the way the retired rank did.

## 3. Node icon resolution

The tree screen, cooldown bar, and picker all draw a node through
`VanillaUi.nodeIcon` (the proc HUD re-implements the same resolution by hand
for its flash), which walks this order:

1. **`TreeNodes.iconSprite(tree, index)`** — if non-null, blit that texture and
   stop. This is where the per-tree branching lives:
   - **`familySprite(tree, family)`** points at a 32px sprite in
     `textures/node/<tree>/<family>.png` (one complete set per tree; `null` for
     `MINOR`). For MARKSMAN/ASSASSIN/WIZARD/PRIEST/ELEMENTALIST and all nine
     epic trees, `iconSprite` is *only* this per-tree set.
   - The Strength trees (SLAYER/CRUSHER/PROTECTOR) try `familySprite` first, then
     fall back to the family's hand-made `sprite()`. SHADOW reverses that (its
     hand-made sprites outrank the set).
2. **`TreeNodes.icon(tree, index)`** — the family's vanilla `Item`, drawn via
   `graphics.fakeItem`. If null (e.g. a bare `MINOR`), nothing is drawn.
3. **`TreeNodes.iconOverlay(tree, index)`** — an effect layer composited over (or,
   when `iconOverlayBehind` is true, under) the item render. Only PROTECTOR and
   CRUSHER families carry overlays (e.g. the shield item + a `bash_overlay.png`).

This layered fallback is deliberately the **development path** for a tree whose
per-tree sprite set does not exist yet: build the tree with item icons plus
overlays first (its `iconSprite` branch returning null or hand-made `sprite()`s),
then point the branch at `familySprite` once a finished
`textures/node/<tree>/<family>.png` set lands. Note that `familySprite` builds
the path unconditionally — it never checks that the file exists — so a tree's
branch should only prefer it once its set actually ships.
`TreeNodes.iconSpriteSize` / `iconOverlaySize` report the source
texture's pixel size so blits scale to a 16px node.

## 4. XP and levels

`SkillPoints` is the whole progression system. Archetype XP **mirrors** vanilla
XP: `PlayerMixin` injects at the tail of `giveExperiencePoints` and calls
`SkillPoints.bank(player, amount)` with the same amount the player earned — the
player keeps all their XP and the archetype banks a copy, so levelling never
competes with enchanting.

- **The curve — two tiers, two shapes.** The **base tier**, levels 1-45, is the
  original quadratic `COST[L] = 15 + (6L² + 2) / 5` (exact integer half-up
  rounding of `1.2L² + 15`) and is **frozen**: it is what every existing save's
  level is read off, so it must never move. The **epic tier**, levels 46-60, is
  the literal `EPIC_COST` table — `7,000 · 8,500 · 10,000 · 12,000 · 14,500 ·
  17,500 · 21,000 · 25,000 · 30,000 · 36,000 · 43,000 · 51,000 · 61,000 ·
  73,000 · 88,000`, about ×1.20 a level, summing to **497,500**. Level 46's
  7,000 is a deliberate step up from level 45's 2,445: 46 is the first level of
  a different tier and is priced like one. `CUM[L]` is the cumulative XP to
  reach level `L`; a `static` block builds both arrays and asserts the anchors
  (`CUM[15] = 1_713`, `COST[1] = 16`, `COST[45] = 2_445`, `CUM[45] = 38_349` —
  all four unchanged — plus `COST[46] = 7_000`, `CUM[50] = 90_349`,
  `COST[60] = 88_000`, `CUM[60] = 535_849`), throwing if the curve drifts.
  Levels past 45 are 93% of the road by design. `level(player)` walks `CUM`;
  `available(player) = max(min(level, 45) − spent, 0)` and `epicAvailable(player)
  = max(max(level − 45, 0) − epicSpent, 0)` keep the two pools apart.
- **Advancement multiplier — frame-weighted, uncapped, never below x1.** Banking
  is scaled at deposit time by
  `xpMultiplier(tasks, goals, challenges) = 1 + 0.05 · tasks + 0.75 · goals +
  2.00 · challenges`. The frame comes from `holder.value().display().get()
  .getType()` (`AdvancementType.TASK` / `GOAL` / `CHALLENGE`); vanilla ships
  91 / 10 / 25 = 126 displayable advancements, so the weights are paid
  1 : 15 : 40 per advancement and land at **x1.00** fresh, **x1.60** for a
  farm-parked player holding twelve tasks, **x24.80** for a thorough
  playthrough (66/6/8) and **x63.05** for the full 126. There is no cap and no
  sub-1.0 branch anywhere — the rate only ever climbs, so a slow player is
  never *penalised*, only un-accelerated. Three synced attachments cache it:
  `ADVANCEMENT_COUNT` (the total, which is what the tree screen shows),
  `ADVANCEMENT_GOALS` and `ADVANCEMENT_CHALLENGES` — tasks are the remainder.
  All three are recomputed on join and by `PlayerAdvancementsMixin` (which
  recounts only when a *real*, displayable advancement is awarded or revoked,
  skipping the ~1,500 silent recipe unlocks). Because scaling happens at
  banking time, `ARCHETYPE_XP` stays an append-only ledger — retuning the rate
  never re-inflates past XP.
- **What the numbers buy (model, mid rate).** A semi-AFK farm banks ~3,000-7,200
  raw XP/h; a thorough player kills and explores for ~350-1,010. Times for the
  full 5→60 climb: **grinder 46 / 66 / 112 h** (fast / mid / slow raw),
  **explorer 21 / 32 / 62 h** — the explorer is **×2.07** faster overall despite
  banking a seventh of the raw XP, which is the whole point of the weights.
  Within the epic tier the explorer clears 45→50 in ~3 h and then spends ~26 h
  on 50→60; the last level alone costs 88,000 XP. A 126/126 completionist runs
  x63.05 and still needs ~12 h for 45→60.
- **Guards.** `ensureBankCoversSpent` (run on join) raises the bank if a retune
  ever left more points spent than XP justifies; it only ever raises.
- **Amnesia / respec.** `AmnesiaPotions` registers two drinkable potions.
  Amnesia I (`shaveLevels`, keeping `Tuning.AMNESIA_LEVEL_KEEP` = 2/3 of levels)
  refunds every node via `ModState.forgetNodes` but keeps the archetype;
  Amnesia II (`forgetArchetype`) wipes nodes, the choice, and all banked XP. The
  creative `ResetArchetypePayload` path (`ModState.clear`) refunds nodes but
  *keeps* banked levels. `forgetNodes` clears both spent-point pools, ends a
  live Magic Armaments channel (the ticker's own guards die with the archetype
  on the Amnesia II and reset paths), and clears proc bookkeeping
  (`MISSILE_CAST_COUNT`, `TRUE_SHOT_ARMED`, `CROSSBOW_PRIMED`) so a respec cannot
  inherit a half-charged proc.

Creative-only `SkillTokenItem` (`skill_token`, `skill_token_60` — 1 level and the
full `MAX_LEVEL`, so one click reaches the epic tier) grants levels for
testing; `SpellcastingTomeItem` does the same for the Spellcasting skill.

## 5. Combat and spell systems

### `Tuning` — the single balance source

Every balance constant lives in `Tuning` (damage, cooldowns, radii, per-rank
factors, mana costs). Gameplay classes read from it and never hardcode numbers;
retuning is a one-file edit. Design rationale for the numbers lives in
`notes/design.md`.

### Mixin injection points

Injection targets are declared in `archetypes.mixins.json` (server/common) and
`archetypes.client.mixins.json`. The load-bearing one is `LivingEntityMixin`,
which hangs almost every melee/on-hit passive off `hurtServer`. **Order matters,
and the reason is `hurtServer`'s HEAD is before vanilla death resolution:**
Fabric's `AFTER_DAMAGE` event is bytecode-gated on `!isDeadOrDying()` and never
fires for killing blows, so on-hit effects that must land on a lethal hit
(Executioner, First Blood, Venom/Blight coatings, Combustion, the Crusher
on-hit batch) shape damage here instead.

The `hurtServer` funnel, all at `@At("HEAD")`:

1. **Cancelling `@Inject`s** can void the hit by returning `false`:
   `archetypes$cheatDeathGrace` (the Last Shadow immunity window via
   `IMMUNE_UNTIL`) and `archetypes$sidestep` (the dagger dodge chance).
2. **`@ModifyVariable` shapers** each read and rewrite the `amount` argument, so
   they compose (each one's output is the next one's input). They are gated by
   role + weapon so at most one attacker-side shaper applies per hit:
   `archetypes$greatswordDamage` (Heavy Blows → First Blood → Executioner → Blade
   Master), `archetypes$swordDamage` (Blade Master alone — its weapon gate is
   `ModItems.isSword`, the vanilla `swords` tag minus this mod's greatswords and
   daggers, so it and the greatsword hook can never both fire),
   `archetypes$daggerDamage` (Razor Edge / Expose / Flense / Shadow Flurry / Twin
   Fangs + Venom/Blight/Crippling coatings), `archetypes$marksmanArrowHit`
   (delegates to `MarksmanCombat.onArrowHit`), `archetypes$sunderDamage` (mace/fists armor-shred + Meteor
   smash bonus + the `CrusherCombat.onCrusherHit` batch). Attacker-side hooks
   check `source.getEntity()`; victim-side `archetypes$manaShield` checks
   `(Object)this` and drains the pool instead of health, and
   `archetypes$instinctiveGuard` does the same for a Colossus Protector's
   carried-but-unraised shield (see `ColossusProtector.instinctiveGuard`: the
   shield's own `BlocksAttacks` decides what is blockable, the facing angle is
   forced to 0, and the durability charged is the whole block even though the
   player keeps only a quarter or half of it).
3. Separately, `archetypes$daggerKnockback` (`@ModifyVariable` on `knockback`)
   funnels all knockback: daggers and missiles shove at half, Flamethrower and
   Blizzard pulses at zero, and Clinch reduces a bare-fisted Crusher's shove.
4. `archetypes$bulwark` (`@ModifyExpressionValue` on the `Math.acos` block-angle
   check in `applyItemBlocking`) forces the angle to 0 so an Omni Block holder
   blocks from every direction. Below 1.21.11 the same handler name targets the
   single `Vec3.dot` inside `LivingEntity.isDamageSourceBlocked` and hands back a
   negative double, which defeats the arc and nothing else — same effect, and it
   is not on the `hurt` funnel either. (That is the Protector's `OMNI_BLOCK`. The
   Colossus Crusher's once-same-named node is not on this funnel at all — it used
   to be a flat victim-side reduction and is now a standing `MAX_HEALTH`
   modifier, for the reason in the next paragraph.)

**Why a defensive node should not be a shaper.** A `@ModifyVariable` at
`hurtServer`'s HEAD is *pre-armour*, and vanilla's armour term degrades by
`damage / t` — so cutting the raw number there does not merely take its
advertised share, it also stops the victim's own armour from being shredded and
collects a second, unadvertised reduction on top, biggest against the biggest
hits. Flat percentage DR on this funnel therefore reads as one number and is
worth another; the Colossus Crusher's Bulwark was the clearest case and is now
Battle Trance's ceiling instead — an absorption bank vanilla spends *after*
armour, in a step this mixin never sees, so it cannot change what a single blow
is worth and it has to be earned hit by hit before it is worth anything at all.
(It was briefly flat max health in between. That fixed the funnel problem and
kept the other one — a zero-input node — which is why it did not last.)
Both remaining victim-side entries earn their place
by not being flat: Mana Shield moves damage into another pool, Instinctive Guard
spends shield durability and answers the shield's own `BlocksAttacks` (below
1.21.11, vanilla's own `isDamageSourceBlocked` clauses instead — same numbers,
and there the handler is a pure multiplication, so it commutes with everything
else on the funnel).

**`ArmourMath` is signed.** `afterArmour`/`rawForAfterArmour` transcribe
`CombatRules.getDamageAfterAbsorb` and its exact inverse, and they now also
answer a **negative** armour value on a branch of their own (floored at -20, the
mirror of vanilla's 20-point ceiling), because Blade Master's PvE bite produces
one: against a non-player the node's ignored share `s` is spent both as a share
of the plate and as a flat bite past it, `(1 - s) x armour - s x 20`, so a naked
mob takes `x1.40` at rank 2 through vanilla's own `1 - realArmour / 25`. Vanilla
never produces a negative here and its clamp is the wrong shape below zero (a
fifth of a negative number is a ceiling, not a floor), which is why it is a
branch and not a widened clamp. Against a **player** the pre-existing expression
runs unchanged — the bite is gated on `!(victim instanceof Player)` and the PvP
arithmetic the node was tuned around is untouched.

**A guard that cannot be broken.** Every way vanilla knocks a shield aside —
an axe hit through `Player.blockUsingItem`, the Warden's own five seconds, and
this mod's Unstoppable Force — ends at one call, `BlocksAttacks.disable`.
Colossus Protector's Immovable Object refuses it there (`BlocksAttacksMixin`,
`HEAD`, cancellable, asking `ColossusProtector.immovableObject`), which is what
lets the node promise "by normal means" without keeping a list of attackers.
Our own Unstoppable Force is deliberately not exempted: the two absolutes meet
as the clash instead (`ProtectorClash`, fired from the cancelling
`hurtServer` head `archetypes$clash`).

**Free Hand is an input permission and nothing else.** Nothing on the server
forbids swinging while blocking — `Player.attack` runs happily while an item is
in use and does not end the use. The whole prohibition is the
`if (this.player.isUsingItem())` arm of `Minecraft.handleKeybinds`, which drains
the attack-click queue into an empty `while` loop and throws it away. So
`MinecraftMixin` spends those clicks at the method's HEAD, before that arm can
eat them, gated on `ColossusProtector.canAttackWhileBlocking` — which asks
nothing but "do I own the node and is my guard up". Every weapon, no weapon
gate, no exceptions: the one exception the node ever carried was for a braced
spear, and it existed only because Spearwall could make "am I blocking?" true
without a shield being raised. The arm itself is deliberately left alone,
because it is also what lowers the shield when the use key comes up.

Other mixins: `PlayerMixin` (XP mirror, `canEat` so Well Fed's raised hunger
ceiling is fillable past 20 on both sides, and — below 1.21.11 only — one of the
three `getItemBySlot` wraps that let a Magic Armaments channel glide in an
elytra's place, declared common because `Player` is common and the client's
jump-to-deploy runs the same check; at 1.21.11 and up the glide is the conjured
weapon's own GLIDER component and needs no mixin at all),
`PlayerAdvancementsMixin` (advancement
count), `AbstractArrowMixin`/`AbstractArrowAccessor`/`ProjectileMixin` (True Shot
flight and reflection), `CrossbowItemMixin` (Rapid Reload), `BlocksAttacksMixin`,
`ItemStackMixin` (Well Fed's faster eating on `getUseDuration`, and
Reinforced Straps wrapping the one `EnchantmentHelper.processDurabilityChange`
call every durability loss in the game funnels through — the node hands that
roll a stack copy carrying a higher Unbreaking level rather than shrinking the
amount, so vanilla's own `level / (level + 1)` binomial does the arithmetic),
`FoodPropertiesMixin` (Well Fed's banked hunger — `FoodData` clamps to a
hardcoded 20 and holds no reference to its owner, so the top-up is wrapped
around `FoodData.eat` where the player is a parameter), `ConsumableMixin`
(Hearty Meal, injected at `onConsume`'s `stack.consume` call: late enough that
milk's clear-everything cannot wipe the Regeneration it grants, early enough
that the stack still knows what it is), `EntityMixin` (Spell Reflect answers
vanilla's own `Entity.deflection`, so a parried spell turns around before it
lands and the return-to-sender is the Protector's Reflection unchanged),
`MobEffectInstanceMixin` and `HealOrHarmMobEffectMixin` (which heals count as
magic for Barbarian — healing carries no `DamageSource`, so the flag is raised
around vanilla's ticked and instantaneous effect application), and
`LivingEntityAccessor`.
Client-side: `AvatarRendererMixin` (armor hiding,
ability poses), `LocalPlayerMixin`, `MinecraftMixin` (charged-swing announce,
plus the `handleKeybinds` head that spends the attack click before the
`isUsingItem()` arm swallows it — the whole of Free Hand, since nothing
server-side forbids attacking while blocking), `HudMixin` (the night
form's grey hearts), `EntityRendererMixin` and `LevelExtractorMixin` (Extra
Sensory Perception's outlines and their exemption from occlusion culling), and
two accessors.

### Shield Sweep: a capstone that is three changes to one ability

The node is **displayed** as Shield Sweep and is still `Family.GROUND_SLAM`
everywhere else: the enum constant, the `node.archetypes.protector.ground_slam`
lang keys, the `textures/node/protector/ground_slam.png` sprite and — the point
of reworking in place rather than adding a node — the constellation index that
`PURCHASED` has been storing all along. That cell has now carried a shockwave,
a spear formation and this, and every owner of every one of them still owns the
same index. Only the lang VALUE and the sprite's pixels move. Never "tidy" the
constant to match the title.

It is deliberately not a second ability, and that is the whole design. The node
opposite it is Omni Block; a capstone with its own key, its own timer and its own
animation would have made the tree's left column optional. So Shield Sweep is
three edits to `ShieldBash.execute`, all of them gated on one boolean:

- **The cone.** `Tuning.BASH_CONE_DOT = 0.5` (a 120° arc) becomes
  `SHIELD_SWEEP_CONE_DOT = 0.0` — the whole half-disc in front — over
  `shieldSweepRange(wide)` = 4/5/6 blocks instead of `BASH_RANGE`'s flat 3.
  Everything inside takes the same blow, so Wide Swings' secondary FRACTION
  stops applying to a Sweep holder and Wide feeds the capstone as reach
  instead. Those are the same three reach numbers the previous capstone had, so
  nothing a Wide Swings owner had measured moved.
  The box query needs a **radial clamp on top** and the plain bash does not: a
  box inflated to cover a half-disc also covers its corners, which is 8.5
  blocks out at the diagonal of a 6-block arc.
- **The weapon.** The whole of `ATTACK_DAMAGE` — item, Strength and every tree
  bonus, the same reading the Slayer's capstone takes — is added to the bash's
  own number. This is the node's answer to a real complaint about the tree: a
  Protector's sword had nothing to do with the button they actually press.
- **Two shields.** `SHIELD_SWEEP_DUAL_SHIELD_MULTIPLIER = 4.0` on the sum. It
  reads enormous and is not: a second shield spends the weapon term (a fist's
  `ATTACK_DAMAGE` is 1.0), the off-hand slot, and every melee attack the player
  owns outside this button.

**The blow goes through `MeleeSwing`, opened ONCE around all victims.** Same
reasoning as `SlayerActives.resolve`: a capstone blow outside the funnel would
be the one attack in the tree armour treated differently, Specialities' combat
multiplier rides the same funnel, and a window per victim would let every
per-swing passive in the mod fire once per body. The plain bash is *not* wrapped
— it never was, and it is a shove rather than a swing.

**The animation is Player Animation Library, and it is three files.** The swing
opens from the pose vanilla's own `HumanoidModel.poseBlockingArm` holds a raised
shield in (`xRot = -0.9424779` rad = −54°, `yRot = ∓30°`) and travels OUTWARD,
so which arm is doing it decides which way it goes: `shield_sweep_right`,
`shield_sweep_left`, and `shield_sweep_dual` for both at once from the same
centre. They are generated from one authored arm track
(`notes/art/.../anim`-style script kept with the sketch) so the mirror is exact
— yaw and roll negated, arms swapped.

- **Positive `yaw` swings an arm toward the player's RIGHT.** Derived, not
  guessed: `ModelPart` composes `Rz·Ry·Rx`, an arm's rest direction is model
  `+Y` (down), `Rx(-90°)` carries it to model `−Z` (forward), and `Ry(φ)` then
  takes that to `(−sin φ, 0, −cos φ)` — model `+X` being the entity's left.
  `poseBlockingArm`'s mirrored ∓30° is the same fact stated by vanilla.
  PAL writes `bone.rotation.y` straight onto `ModelPart.yRot` for an arm
  (`RenderUtil.translatePart`), and its loader negates nothing for arm bones —
  only for `body`, `cape` and the item bones.
- **`"version": 3` is required in every file.** Below 3 the loader rewrites the
  bone name `torso` to `body`, and `body` additionally has its pitch and yaw
  negated on the way in. This is the trap named in `CLAUDE.md`, and it is
  visible in `PlayerAnimatorLoader.moveDeserializer`'s bytecode as a literal
  `if (version < 3 && name.equals("torso")) name = "body"`.
- **A move's `easing` governs the segment LEAVING that keyframe**, so the
  sweep's acceleration is written on the tick-0 move and its settle on tick 2.
- **The drive window must equal the files' `stopTick`** (10), for the reason
  `SlayerAnimations` spells out: longer re-triggers and loops a one-shot,
  shorter calls `stop()` mid-swing. All three end on an all-zero keyframe at
  tick 8, so a stop in the tail is invisible.
- `ProtectorAnimations` picks the file per **arm**, not per hand — for a
  right-handed player that is the sketch's "main hand to the right, off-hand to
  the left" exactly, and for a left-handed one it keeps each arm sweeping out on
  its own side.

**One synced stamp, and nothing else.** `SHIELD_SWEEP_AT` (transient, `all()`)
carries the gametime the sweep landed on. Which arms hold shields is *not*
synced and does not need to be: held items are tracked equipment, so every
client already knows whether this player has one shield or two. The stamp exists
only because `player.swing` is broadcast for **every** bash and only a capstone
holder's bash is a sweep. It is written before the damage, so a blow that kills
its victim still animates.

### `SpellProjectile` modes and mana

Every Seeker spell in flight is one entity, `SpellProjectile extends
ThrowableItemProjectile`, wearing a different item so the vanilla thrown-item
renderer draws it — `SpellProjectileRenderer` subclasses `ThrownItemRenderer`
only to scale up empowered missiles and mana-fed meteors. Its `enum Mode` — `FIREBALL`,
`METEOR`, `FLAME_BOLT`, `MISSILE`, `HOLY_LIGHT`, `ICE_BLAST`, `GLACIAL_SPIKE` —
selects the physics (`getDefaultGravity`, range, homing `steer()`, `pierceSweep()`
for Lance), the trail particles, and the on-hit rules (`onHitEntity` for direct
hits, `onHit` for area bursts). Per-cast shaping is applied through the fluent
`with*` builders (`withPower`, `withHoming`, `withDamage`, `withSlow`, `withAegis`,
…) that `SeekerSpells` sets at cast time. A spell that gets saved and
chunk-reloaded wakes with a null mode and discards itself.

`Mana` is the Seeker's resource (there are **no** spell cooldowns — mana is the
throttle). `Mana.max` = base + node bonuses + Spellcasting level; `regenPerSecond`
returns 0 while any combat weapon is held (`ModItems.holdingCombatWeapon`) so
sword-and-sorcery can't double-dip. `spend`/`spendAll`/`drain`/`add` write the
synced `MANA` attachment; spending awards Spellcasting XP with a fractional carry
(`MANA_XP_REMAINDER`). `SeekerTicker` calls `Mana.regenTick` for every Seeker each
tick.

## 6. Client UI map

| Class | Owns |
| --- | --- |
| `ArchetypesClient` | keybinds, HUD registration, the inventory bookmark/button, level-up toast, channel/rush/disengage input edges |
| `ArchetypePickerScreen` | the pre-pick screen: three archetype cards, crest, preview actives. Card width is the screen's unit — a ceiling of 112 that shrinks to fit, because the guaranteed GUI-scaled surface is only 320 wide |
| `ArchetypeScreen` | the skill tree: full-screen window, three constellation sections, buy-on-click, the two progress bars, Back/Reset |
| `CooldownBarHud` | one slot per owned active docked right of the hotbar, reading the synced `*_READY_AT` timestamps and mana cost |
| `ManaHud` | the Seeker's ten mana orbs above the hunger bar |
| `ProcIndicatorHud` | the falling node-icon flash driven by `PassiveProcPayload` |
| `ArchetypeLevelUpToast` | the level-up toast |
| `VanillaUi` | shared vanilla-style window/inset drawing and `nodeIcon` |
| `BookmarkTab` | the survival-inventory bookmark widget |
| `SpellProjectileRenderer`, `BladestormLayer`, `BulwarkShieldLayer`, `GreatswordSweepParticle`, `SlayerAnimations` | render layers and the animation player |
| `NightAnimations`, `NightFormFx`, `NightEyesLayer` | the Dark Ritual's pose, its particle column and quickening heartbeat plus the transformed body's trail, and the red eye glow onlookers see on a vampire |
| `SunBlindOverlay`, `UndeadHud` | the night form's sun bloom, its grey hearts and its hidden hunger row |
| `DeadeyeOverlay` | the Deadeye stance's concentration vignette, drawn as nested fills rather than a texture |
| `ExtraSensoryPerception` | the sensed-creature outline colours *and* Death Mark's red (the mark wins over ESP and over anything vanilla paints; Stalk adds only the through-walls exemption) |
| `RadianceLight` | Aura of Radiance's block light, placed in the client's own level copy only |
| `BankedHungerHud` | Well Fed's hunger above 20, as a bevelled 1px halo around the vanilla drumsticks that bank is currently backing (leftmost first, the end vanilla drains first). Anchored after `FOOD_BAR`, not `HOTBAR`, or it would draw under the row it marks; hidden in creative and spectator |

**What scales with the GUI scale and what does not.** A `Screen` measures in
GUI-scaled pixels, so a constant there is a claim about the player's scale
setting — which is how the tree screen came to be authored at scale 2 and drawn
wrong everywhere else (`MULTIVERSION.md` §5.8.2). The rule the two screens now
follow: **chrome and text are fixed, diagrams are fluid.** Headers, buttons, the
epic switchers, the progress bars and every tooltip keep their pixel sizes, the
way vanilla's screens do — a smaller GUI scale buys more room, not bigger text.
The constellation is derived entirely from one unit, `ArchetypeScreen.pitch()`:
the grid cell at which the largest sub-tree of this archetype (base *or* epic, so
a switcher flip never resizes anything) fills the tighter axis of a section. Node
size, halo ring, connection stroke and icon size are all fractions of that unit,
and the clamps sit on the node rather than the pitch so a cramped screen closes
its gaps instead of pushing rows out through the canvas floor. `VanillaUi.nodeIcon`
takes a size and reaches it through the pose — the one idiom that scales a sprite
blit and an item render together, an item render having no width argument to pass.
When you add a surface here, size it off the live `width`/`height` or off a unit
derived from them; the picker's fixed 380px panel was wider than the 320px
Minecraft guarantees, and no build- or server-shaped gate can see that.

**The night form's client half.** Everything the Nemesis Shadow's night form
looks and sounds like reads the synced attachments through `NightForm`'s static
predicates — there is no night-form packet. `NightFormFx` and `NightAnimations`
walk `level.players()` each client tick the way `SlayerAnimations` does, so
onlookers see a caster's ritual exactly as the caster does. The three display
overrides are gated per frame and hold no state to restore: `UndeadHud.active()`
decides both the hunger row's `replaceElement` and `HudMixin`'s heart-sprite
swap (our own `hud/heart/grey_*` set — vanilla's WITHERED sprites are left to
mean the Wither), and `SunBlindOverlay` snaps its bloom to zero the frame the
form ends. The eye glow and the trail are the one part that is NOT purely the
owner's view: `AvatarRendererMixin` writes `NightEyesLayer.GLOW` onto every
player's render state the way it does `BULWARK_ACTIVE`. `NightFormFx`'s trail is
suppressed whenever the player is invisible, and so is the glow — with one
exception, which is the whole design of `NightEyesLayer`: a vampire holding a
live Death Mark glows much brighter and glows *through* invisibility, because
naming a victim is supposed to cost you your hiding place. Everything else about
an invisible Cutpurse still gives away nothing. Since the mark's owner-side
stamps are target-synced, an onlooker's client answers "does this player have a
mark out?" backwards, from the `MARKED_BY` flag on the bodies it can see (cached
once a game tick). `ExtraSensoryPerception` supplies the
outline colour that `EntityRendererMixin` writes onto `EntityRenderState`
(vanilla's glowing field) and the sensed test that `LevelExtractorMixin` uses to
excuse a walled-off creature from occlusion culling — both read the LOCAL
player's roster only, so one vampire's senses never tint another player's view.

HUD elements register after `VanillaHudElements.HOTBAR`. The mana row also
replaces `VanillaHudElements.AIR_BAR` to nudge air bubbles up one row when the
mana bar is visible, rather than overlapping.

## 7. How-to

### Adding a node to an existing tree

Worked for a hypothetical new Slayer skill "Cleave":

0. **Find it a cell without growing the tree.** Trees are at their point
   economy, so the first move is not "add a `'#'`" — it is to shorten some
   inert multi-rank family and remap the freed cell, keeping its old ceiling by
   raising the per-rank value. See §2, "Compacting a family instead of
   redrawing a grid". Adding a cell renumbers saved `PURCHASED` data.
1. **Grid** (`Constellations.SLAYER_SWORD`): if the node occupies a cell that
   already exists in the ASCII grid, no change; if you need a new cell, add a
   `'#'` — but note that changes node indices and therefore saved `PURCHASED`
   data, so prefer reusing an authored cell.
2. **Family** (`SlayerNodes.Family`): add a constant with its icon strategy
   (item supplier, hand-made `sprite()`, and/or `overlay()`).
3. **Mapping** (`SlayerNodes.build()`): `byCell.put(cell(col, row), new
   Def(Family.CLEAVE, 1))` for each rank's cell. The count invariant will throw
   at class-load if the grid and map disagree.
4. **Behavior**: read `SlayerNodes.rank(SubTree.SLAYER, owned, Family.CLEAVE)`
   wherever the effect lives (a `hurtServer` shaper in `LivingEntityMixin`, a
   `*Ticker`, or a `*Combat` hook) and scale by a new `Tuning` constant.
5. **Display**: if it's an active or capstone, add it to `TreeNodes.kind` (and, if
   it should preview on the picker, `pickerActives`). If it's an exclusive
   capstone, extend `TreeNodes.exclusiveTaken`.
6. **Icon**: drop `textures/node/slayer/cleave.png` (32px) for the per-tree set,
   or rely on the item/overlay fallback (§3).
7. **Lang** (`assets/archetypes/lang/en_us.json`): add
   `node.archetypes.slayer.cleave` and `node.archetypes.slayer.cleave.desc`.

### Adding a new sub-tree

1. **Constellation**: add a `Constellation` to `Constellations` (an ASCII grid at
   the 15-point-cap economy; the shipped trees run 22–26 nodes, most at 23).
2. **`SubTree`**: add the enum value (archetype, id, stand-in icon, constellation)
   and include it in `SubTree.of`.
3. **`*Nodes` class**: create it following the convention in §2 — `Family` enum
   (with `MINOR`), `Def` record, `build()` with the count invariant, `def`, and
   `rank`.
4. **`TreeNodes`**: add the new `SubTree` to every `switch` (name/description/icon,
   `iconSprite`, `isMinor`, `rankOf`, `kind`, `pickerActives`, `familyOf`) — those
   switches are exhaustive, so the compiler lists what you missed — and extend
   `exclusiveTaken` by hand: it is an if-chain that falls through to the
   Protector branch, so the compiler will not flag a missing tree there.
5. **Actives**: add the ability dispatch to the `ActiveAbilityPayload` switch in
   `Archetypes.onInitialize`, and any cooldown keys to `ModState`.
6. **Balance**: add constants to `Tuning`.
7. **Icons**: add the `textures/node/<tree>/` sprite set (or use the fallback
   chain during development).
8. **Lang**: `subtree.archetypes.<id>` plus every `node.archetypes.<id>.<family>`
   and `.desc` key.

## 8. Interop with Specialities

Archetypes takes Specialities as a **soft dependency** — both mods work
standalone, and neither hard-depends on the other. Contact is confined to
`com.archetypes.compat`.

- **The `specialities:skills` entrypoint.** `fabric.mod.json` declares
  `SpellcastingEntrypoint` under `specialities:skills`. Fabric only instantiates
  it when Specialities pulls that entrypoint, so the Specialities API classes it
  references never load without the mod present. It registers `SpellcastingSkill`
  (the Seeker's mana skill: +1 max mana/level, +1 regen per
  `MANA_REGEN_LEVELS_PER_POINT` levels) into Specialities' `SkillRegistrar`.
- **`SpecialitiesBridge`** is the single runtime touch-point. Every method is
  gated on a `FabricLoader.isModLoaded("specialities")` flag, and the code that
  names Specialities classes lives in an inner `Linked` holder loaded only behind
  that guard. Without Specialities the mana pool simply stays at its base size.
- **`HUD_SHIFT` collision contract.** Specialities raises the vanilla hearts/XP
  stack by its `HUD_SHIFT = 7` to make room for its bar; Archetypes leaves the
  vanilla stack alone. `ManaHud` reads the `SPECIALITIES_LOADED` flag and adds
  `SPECIALITIES_SHIFT = 7` (a local mirror of that constant) to its own vertical
  offset, so the mana row sits above the already-shifted stack instead of
  overlapping into it. (Host election: with both installed Specialities owns
  the shared skills screen/button/HUD and Archetypes contributes its skill as a
  tab; standalone, Archetypes provides its own.)
- **Shared bookmark-tab width formula.** `BookmarkTab.widthFor(label) =
  font.width(label) + 2 · PAD` (PAD = 6). Both mods compute their inventory
  bookmark width with this same label-plus-padding formula, so
  `ArchetypesClient.anchorTab` can slot its tab past the Skills tab by
  `BookmarkTab.widthFor(Component.translatable("screen.specialities.skills")) + 2`
  without ever referencing Specialities' widget.
- **Shared damage funnel.** Both mods' `LivingEntityMixin`s inject `@ModifyVariable`
  on the same `hurtServer` `amount`, so their melee multipliers stack
  multiplicatively on one value — the two damage models compound rather than one
  overriding the other.
