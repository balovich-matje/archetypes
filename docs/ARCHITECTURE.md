# Archetypes — architecture

A Fabric 26.2 RPG-class mod. A player picks one **archetype** — Brawler
(`STRENGTH`), Cutpurse (`AGILITY`), or Seeker (`INTELLECT`) — in the first minute
of a playthrough. Each archetype has three **sub-trees** (constellation-shaped
skill trees), levels off a mirror of vanilla XP, and casts/swings active
abilities bound to four keys. This document is for someone extending the mod;
every claim below is grounded in the source under `src/`.

Package layout: gameplay/server logic in `src/main/java/com/archetypes`, mixins
in `.../mixin`, the Specialities soft-dependency shim in `.../compat`, and all
client/render/HUD/screen code in `src/client/java/com/archetypes/client`. The
`main` entrypoint is `Archetypes` (`ModInitializer`); the `client` entrypoint is
`ArchetypesClient` (`ClientModInitializer`).

## 1. Big picture

### Server-authoritative attachments

All persistent and transient per-player state lives in **Fabric attachments**,
declared in `ModAttachments`. The server is the only writer; clients read a
synced copy. Two sync scopes are used:

- `AttachmentSyncPredicate.targetOnly()` — synced to the owning client only.
  Used for private state: `ARCHETYPE`, `ARCHETYPE_XP`, `SPENT_POINTS`,
  `PURCHASED`, `MANA`, and the per-ability `*_READY_AT` cooldown timestamps
  (all but `DISENGAGE_READY_AT`, which stays server-side and unsynced).
- `AttachmentSyncPredicate.all()` — synced to everyone. Used for state other
  players' renderers need: `BULWARK_ACTIVE`, `ARMOR_HIDDEN`, `DECIMATE_SWING_AT`,
  `BLADESTORM_END`, `QUAKE_CHARGE_END`, `RADIANCE_END`, `DEADEYE_END` (which the
  owner's client also needs, because it predicts a crossbow's charge time), the
  per-arrow `DEADEYE_ARROW`, and `MARKED_BY` — which is the one attachment that
  lives on a *non-player* entity to describe it: Death Mark writes the assassin's
  entity id onto the marked creature, so a client asks the body who is hunting it
  instead of being handed anyone's roster.

Some attachments are `.persistent(codec)` and `.copyOnDeath()` (the archetype,
its XP, owned nodes, mana); others are transient (cooldown timestamps, proc
bookkeeping like `MISSILE_CAST_COUNT`, `SMASH_AT`). `ModAttachments.get(player)`
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
respawn hook. A node that grants an attribute permanently (the Colossus
Crusher's Bulwark, `+7.0` max health a rank) is asserted by its ticker every
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
  Bulwark(`OMNI_BLOCK`)|Spear Phalanx(`GROUND_SLAM`). Elementalist is special: its four capstones
  are **one choice total** — any owned capstone locks the other three.

### Compacting a family instead of redrawing a grid

A node's identity is its index into `constellation().nodes()`, and `PURCHASED`
stores indices — so the cheapest safe way to free a cell for a new skill is to
**shorten an existing family's chain and remap the cell**, never to edit the
ASCII art. Editing a `'#'` renumbers every node after it and silently
repurposes saved data.

The same move works in reverse, to delete a skill without deleting a cell.
Reinforced Straps was one node at `(0,7)` — a flat Unbreaking I on the blocking
item, a durability discount pretending to be a skill. It is gone and its cell is
Reflection's second rank, so `REFLECT` now runs `(0,7)`→`(0,8)` at x0.5/x1.0
returned damage. No migration code exists because none is needed: `PURCHASED`
stores indices, so an owner of Straps owns the same index and it reads as
Reflection I, and an owner of both reads Reflection II. Note what this does NOT
do — it frees no cell. A rank **is** a cell in this system, so folding two
one-rank skills into one two-rank skill is exactly cell-neutral; the only way to
free one is the compaction below.

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
  refunds every node via `ModAttachments.forgetNodes` but keeps the archetype;
  Amnesia II (`forgetArchetype`) wipes nodes, the choice, and all banked XP. The
  creative `ResetArchetypePayload` path (`ModAttachments.clear`) refunds nodes but
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
   check in `applyItemBlocking`) forces the angle to 0 so a Bulwark holder blocks
   from every direction. (That is the Protector's `OMNI_BLOCK`. The Colossus
   Crusher's same-named node is not on this funnel at all — it used to be a flat
   victim-side reduction and is now a standing `MAX_HEALTH` modifier, for the
   reason in the next paragraph.)
5. `archetypes$spearwall` and `archetypes$spearwallHand` — not damage shapers at
   all, but they live beside the block hooks because they decide *whether a
   block happened*. See "Spearwall" below.

### Spearwall: two item-use states vanilla only has one slot for

A braced spear and a raised shield are the same action to 26.2. Both are "using
an item", `LivingEntity` holds exactly one (`useItem`), and `startUsingItem`
opens with `if (stack.isEmpty() || this.isUsingItem()) return;` — so the second
request is dropped. The hand order settles which one wins and it is always the
same one: the use key walks `InteractionHand.values()`, main hand first, so a
player holding spear and shield the normal way braces the spear and never
raises the shield.

**The spear half is untouched.** It is the real `useItem`; vanilla ticks its
`KINETIC_WEAPON` component through `ItemStack.onUseTick` →
`KineticWeapon.damageEntities`, and every speed condition, contact cooldown
(`recentKineticEnemies`, allocated by `startUsingItem` only for a kinetic used
item) and stab sound stays vanilla's. Driving that by hand would have meant
reimplementing it *and* managing the stab-memory map, whose absence silently
disables the contact cooldown rather than erroring — `wasRecentlyStabbed`
returns false on a null map, so a hand-rolled brace stabs every tick.

**The shield half is one hook.** `LivingEntity.getItemBlockingWith` is the only
thing in the game that answers "is this entity blocking" — `isBlocking`, the
block pose, the block arc, `applyItemBlocking` and the `blockUsingItem` disable
path all defer to it and none reads `useItem` for itself. `archetypes$spearwall`
injects at its RETURN and, **only over a null**, offers the other hand's shield
(`Spearwall.guardingShield`). Everything downstream arrives free, including this
tree's own block-gated nodes: Iron Spikes and Braced hang off `blockedByItem`,
which only fires because a block happened.

Injected common, not server-side: the client asks the same question to pose the
player, and can answer it because `PURCHASED` is synced and `NodePurchases.owned`
is the same code there. **For the owner only, though** — `PURCHASED` rides
`targetOnly()`, so an onlooker's client evaluates someone else's build as empty
and answers no. Everything that RENDERS the guard therefore reads a published
flag, `SPEARWALL_GUARD` (transient, `all()`, maintained by `ProtectorTicker`
beside `BULWARK_ACTIVE`), and pays a tick of latency for being right about
everybody. Gameplay and the owner's own movement still ask the real function.

**Looking like it and moving like it are three separate seams, none of them the
one you would guess.**

- *The shield mesh.* A raised shield does not look raised because of an arm
  transform — `ItemInHandRenderer`'s `BLOCK` branch explicitly skips a
  `ShieldItem`. It looks raised because `assets/minecraft/items/shield.json` is
  a `minecraft:condition` on `minecraft:using_item`, swapping `item/shield` for
  `item/shield_blocking`, which is a different model with different display
  transforms. That property is `IsUsingItem`, whose whole body is
  `owner.isUsingItem() && owner.getUseItem() == itemStack` — false for a
  Spearwall shield, in every view at once. `IsUsingItemMixin` answers yes for
  the guard stack, which fixes first person, third person and anything else
  that draws a held item from one place.
- *The arm — two mixins, and both are required.* `AvatarRenderer.getArmPose`
  only reaches `BLOCK` when `avatar.getUsedItemHand() == hand`, which under
  Spearwall is the spear's, so the guarding hand fell through to
  `ArmPose.ITEM`. `AvatarRendererMixin` returns `BLOCK` for the guard hand —
  and on its own that value is written and never read. `HumanoidModel.setupAnim`
  poses the USING arm and then poses the other arm *only if*
  `usingArmPose.affectsOffhandPose()` is false; `ArmPose.SPEAR` is built
  `(twoHanded = false, affectsOffhandPose = true)`, so vanilla skips the guard
  arm outright, for either hand configuration (the guard is duplicated in both
  arms of the `if`). `HumanoidModelMixin` closes it: an `@Inject` at the
  `setupAttackAnimation` call — the point where vanilla's arm posing ends —
  runs vanilla's own private `poseBlockingArm` (reached by `@Invoker`) on the
  guard arm, gated on the exact negative of vanilla's skip (using an item, the
  guard arm is not the using arm, guard pose is `BLOCK`, using pose claims the
  off hand). Nothing vanilla produces satisfies that gate, so no extra Spearwall
  flag is consulted; handedness comes from `state.mainArm`. **Not `TAIL`:** the
  crouch block and the swim/fall-flying lerps run after that call and read the
  arm rotations, so posing at `TAIL` would feed crouch's `+0.4` into
  `poseBlockingArm`'s own `xRot * 0.5` and sit ~11° off vanilla whenever a
  guarding player sneaks.
- *The slowdown.* `LocalPlayer.modifyInput` scales input by the USE item's
  `UseEffects.speedMultiplier`. A shield has no `USE_EFFECTS` and takes
  `UseEffects.DEFAULT` — `0.2F`, `canSprint = false`. A spear declares its own:
  `Item.Properties.spear` sets `new UseEffects(true, false, 1.0F)`, because a
  braced spear is meant to be carried at a run. So the synthesised guard was
  free — full shield, full running speed. `LocalPlayerMixin` returns the
  guard's multiplier from `itemUseSpeedMultiplier` and forces
  `isSlowDueToUsingItem` (which is what gates `canStartSprinting`). Local-player
  only, and that is correct rather than a shortcut: players are
  movement-authoritative.

**Two seams, and both are vanilla reaching past `getItemBlockingWith` for the
USE ITEM.** One `@ModifyExpressionValue` each, neither touching the guard:

- *Durability.* `applyItemBlocking` charges the block with
  `hurtBlockingItem(level, blockingStack, this, getUsedItemHand(), blocked)` —
  the stack is ours and right, the hand is the *spear's*, so the shield would
  wear correctly and shatter in the wrong slot. `archetypes$spearwallHand`
  rewrites that single `getUsedItemHand()` call, and only when the guard is
  genuinely Spearwall's; a normally raised shield already is the used item and
  must keep its own hand.
- *Feedback — the bug that read as "the attacks pass through".* The guard was
  never the problem: in `hurtServer`'s bytecode `applyItemBlocking` is called at
  offset 79 and `amount -= blocked` lands at 84–88, so the damage really was
  being stopped. But `hurtServer` also stashes `getUseItem()` at 69–73, and at
  288–324 reads `BLOCKS_ATTACKS` off *that* stack to choose between
  `blocks.onBlocked(level, this)` (the block sound, offset 315) and
  `level.broadcastDamageEvent(this, source)` (a plain hurt, offset 324). A spear
  carries no `BLOCKS_ATTACKS`, so every genuine block took the second branch:
  silent, and broadcast to every client as an ordinary hit — visually identical
  to no block at all. `archetypes$spearwallBlockFeedback` hands that one read the
  shield. It is safe to be that blunt because the local is read in exactly one
  place (`aload 5` appears once in the whole method) and the call is the method's
  only `getUseItem`, so the redirect moves the feedback and cannot reach the
  arithmetic. Everything else downstream that consults the blocking stack — the
  axe disable among it — now reads the shield too.

**What actually ends a brace — and what Free Hand does about it.** Not the use
item: `Item.getUseDuration` answers a flat `72000` for anything holding
`KINETIC_WEAPON`, so `LivingEntity.updateUsingItem`'s `--useItemRemaining == 0`
arm is an hour away. The real lifetime is `KineticWeapon.Condition`, one per
effect (dismount / knockback / damage), each opening `ticksUsed <=
maxDurationTicks` — and `KineticWeapon.damageEntities` derives that `ticksUsed`
from `stack.getUseDuration(holder) - ticksRemaining - delayTicks`, which only
climbs. An iron spear's windows are 50 / 135 / 225 ticks past a 12-tick wind-up:
past about eleven seconds the spear is still braced, still animating, and inert.
`KineticWeaponMixin` rewrites the one argument that number comes from, freezing
it at `Tuning.FREE_HAND_BRACE_HOLD_TICKS` past the wind-up for a Free Hand
holder — inside the shortest window every shipped material declares, so all
three effects stay alive without reconstructing a single `Condition`. The speed
halves of `Condition.test` are untouched, so the node buys duration and never
damage.

**Free Hand explicitly does not extend to spears, and the exclusion is
load-bearing.** Free Hand is an input permission gated on nothing but "am I
blocking?" (`ColossusProtector.canAttackWhileBlocking`, consumed by
`MinecraftMixin`'s `handleKeybinds` head). Spearwall makes that true, so the two
nodes together would have bought a raised shield, a braced spear and a free
melee arm for two points. The refusal is one clause on that one function —
`&& !Spearwall.bracingSpear(player)` — rather than a second gate beside the
click loop, so there is no other path to keep in step. A braced spear is a
planted weapon: it hurts what runs onto it, and it does not swing.

**Why a defensive node should not be a shaper.** A `@ModifyVariable` at
`hurtServer`'s HEAD is *pre-armour*, and vanilla's armour term degrades by
`damage / t` — so cutting the raw number there does not merely take its
advertised share, it also stops the victim's own armour from being shredded and
collects a second, unadvertised reduction on top, biggest against the biggest
hits. Flat percentage DR on this funnel therefore reads as one number and is
worth another; the Colossus Crusher's Bulwark was the clearest case and is now
max health instead, which composes additively with armour and cannot change
what a single blow is worth. Both remaining victim-side entries earn their place
by not being flat: Mana Shield moves damage into another pool, Instinctive Guard
spends shield durability and answers the shield's own `BlocksAttacks`.

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

Other mixins: `PlayerMixin` (XP mirror, the `canGlide` hook that lets a
Magic Armaments channel glide in an elytra's place — declared common because
`Player` is common and the client's jump-to-deploy runs the same check — and
`canEat`, so Well Fed's raised hunger ceiling is fillable past 20 on both
sides), `PlayerAdvancementsMixin` (advancement
count), `AbstractArrowMixin`/`AbstractArrowAccessor`/`ProjectileMixin` (True Shot
flight and reflection), `CrossbowItemMixin` (Rapid Reload), `BlocksAttacksMixin`,
`ItemStackMixin` (Well Fed's faster eating, on `getUseDuration`),
`FoodPropertiesMixin` (Well Fed's banked hunger — `FoodData` clamps to a
hardcoded 20 and holds no reference to its owner, so the top-up is wrapped
around `FoodData.eat` where the player is a parameter), `ConsumableMixin`
(Hearty Meal, injected at `onConsume`'s `stack.consume` call: late enough that
milk's clear-everything cannot wipe the Regeneration it grants, early enough
that the stack still knows what it is), `EntityMixin` (Spell Reflect answers
vanilla's own `Entity.deflection`, so a parried spell turns around before it
lands and the return-to-sender is the Protector's Reflection unchanged — plus
the `shouldBeSaved` veto that keeps the Spear Phalanx spears out of region
files, see below),
`MobEffectInstanceMixin` and `HealOrHarmMobEffectMixin` (which heals count as
magic for Barbarian — healing carries no `DamageSource`, so the flag is raised
around vanilla's ticked and instantaneous effect application),
`LivingEntityAccessor`, and `DisplayAccessor`/`ItemDisplayAccessor` (the
phalanx's spears, below).
Client-side: `AvatarRendererMixin` (armor hiding,
ability poses), `HumanoidModelMixin` (the Spearwall guard arm vanilla skips —
see above; the only injector this mod has into a model class),
`LocalPlayerMixin`, `MinecraftMixin` (charged-swing announce,
plus the `handleKeybinds` head that spends the attack click before the
`isUsingItem()` arm swallows it — the whole of Free Hand, since nothing
server-side forbids attacking while blocking), `HudMixin` (the night
form's grey hearts), `EntityRendererMixin` and `LevelExtractorMixin` (Extra
Sensory Perception's outlines and their exemption from occlusion culling), and
two accessors.

### Spear Phalanx: the formation, and why the spears are only decoration

The node is **displayed** as Spear Phalanx and is still `Family.GROUND_SLAM`
everywhere else: the enum constant, the `node.archetypes.protector.ground_slam`
lang keys, the `textures/node/protector/ground_slam.png` sprite and — the point
of reworking in place rather than adding a node — the constellation index that
`PURCHASED` has been storing all along. Only the lang VALUE moved. Never
"tidy" the constant to match the title; that renumbers nothing but it does
orphan the sprite and both lang keys at once.

The capstone used to turn the bash into a ring — the same hit over a larger
circle, which is a poor thing for a capstone to be when the node opposite it is
Bulwark and half the tree is already about widening the bash. It is now
`SpearPhalanx`: **carrying a spear**, the bash plants a spear at each of the
caster's shoulders and all three thrust forward. Without a spear the bash is
untouched, so the capstone is a loadout ask rather than a dead node, and it sits
on the same column as Spearwall by design.

- **The hit is one hit.** Each victim in the front cone takes the bash's damage
  *plus* a full spear stab (`ATTACK_DAMAGE × PHALANX_STAB_MULTIPLIER`), once,
  and `ShieldBash` returns before its own target loop so nobody is charged
  twice. It is wrapped in `MeleeSwing.begin/end` for the same reason
  `SlayerActives.resolve` is: a capstone blow outside the funnel would be the
  one attack in the tree armour treated differently, and Specialities' combat
  multiplier rides that funnel too. One swing is opened around *all* victims,
  not one per victim, or a per-swing passive would fire once per body.
- **Victims are recomputed by the phalanx**, over its own longer reach
  (`Tuning.phalanxRange`, 4 blocks + 1 per Wide Swings rank — Wide now feeds
  the capstone as reach instead of ring radius).
- **Spears, not spearmen — the clones were tried and failed in-game.** A pass
  between these two conjured two clones of the caster out of clientbound
  packets (a client-only team to kill the name plates, an `ADD_PLAYER` info
  entry carrying the caster's own signed `textures` property, a `PLAYER`
  add-entity, the skin-layer byte, the caster's armour) and let vanilla's
  `SpearAnimations` arm pose aim their weapons. It is gone. The skin transplant
  needs a signed `textures` property and an **offline account has none**, so on
  every dev and LAN launch the two spearmen wore random default skins instead of
  the caster's — which is the whole reason to clone a player in the first place.
  The formation is back to the two things it was ever about: a spear at each
  shoulder, and a thrust.
- **The spears carry no gameplay.** Each is a `Display.ItemDisplay` holding a
  one-count copy of the caster's spear: zero-size bounding box, `noPhysics`,
  no AI, and `Display.hurtServer` is a hard `false`, so nothing can be hit,
  pushed or killed by one or can kill one. Gameplay resolves in full at cast,
  server-side, the way the rest of `ShieldBash` does — so a spear that vanished
  early, or that a client never received, cannot cost anybody a hit.
- **They ARE real entities, and that is the one failure this design owns.**
  An `ItemDisplay` is fully serialised for its whole 12-tick life:
  `EntityTypes` registers it without `noSave()` and `Display` never overrides
  `shouldBeSaved`, so an autosave or a `/stop` landing inside the spawn→sweep
  window writes it to a region file, and on the next load `SpearPhalanx.LIVE`
  is empty and nothing ever discards it — a permanent 0×0-hitbox spear hanging
  in the air. `EntityMixin` vetoes `shouldBeSaved` at `RETURN` for anything
  carrying the `archetypes_phantom` command tag, so every other entity's save
  path is untouched.
- **The angle is carried by the display's own transformation, and it is
  measured from the HORIZON.** This is the part that has to be derived rather
  than eyeballed, because the previous pass's 47 degrees were relative to a
  humanoid arm rest pose that no longer exists:
  1. A `Transformation` composes as `T · L · S · R`
     (`Transformation.compose`), so the left rotation turns the model and the
     translation is applied OUTSIDE it, in the display's own unrotated axes.
  2. Those axes come straight off `DisplayRenderer.calculateOrientation`: a
     FIXED billboard — the default, and what these are — is posed with
     `rotationYXZ(-yRot, +xRot, 0)`, and `Ry(-yRot)` carries local `+Z` onto
     `(-sin yRot, 0, cos yRot)`, which is Minecraft's own facing vector for
     that yaw. So local `+Z` is **forward**, `+Y` is up, `+X` is the caster's
     left, and a **positive** rotation about `+X` tips forward down — the same
     sign the renderer spends a positive (downward) Minecraft pitch with.
  3. The base pose is `+Y`, tip up. `ItemDisplayRenderer.submitInner` spins the
     item 180° about `+Y` and draws it under its `THIRD_PERSON_RIGHT_HAND`
     display transform, which for `spear_in_hand.json` is
     `rotation [5, 270, -40]`, `scale [1.7, 1.7, 0.85]`. Composed as
     `Rx(5)·Ry(270)·Rz(-40)` against the sprite's own 45° diagonal (tip at the
     texture's top-left, butt at its bottom-right) that lands the shaft on
     exactly `+Y`; the Y-flip cannot disturb it because `+Y` is the one axis
     the flip fixes.
  4. `Rx(θ)` carries `+Y` onto `(0, cos θ, sin θ)`, so `θ = 90°` is level and
     pointing forward. The left rotation is therefore
     **`Rx(90° + PHALANX_SPEAR_ANGLE_DEGREES)`**, giving
     `+Y ↦ (0, −sin 47°, +cos 47°)` — forward, 47° under the horizon. Written
     as `90 + the knob` on purpose: the 90 is the base pose and the 47 is
     tuning, and moving the knob re-derives nothing.
  5. The display's **yaw** is the caster's, so the formation points where they
     are pointing and so "forward" means anything at all. Their **pitch** is
     explicitly `0`: `xRot` is a second rotation the renderer applies before
     ours, so a cast aimed at the sky would tilt the whole formation with it and
     the 47° would stop being measured from the horizon.
- **The thrust is the Display's own interpolation, in two steps.** A Display
  lerps from the transformation it is currently showing to the one it is given,
  so a single transformation at spawn would appear in its final place and never
  move. Both spears are spawned drawn back with duration 0 (a fresh render
  state, no lerp), and `PHALANX_WINDUP_TICKS` later the ticker sets delay 0,
  duration `PHALANX_STAB_TICKS` and the thrust pose. The slide runs along the
  **depressed shaft**, not along `+Z`, so the lunge reads as a stab rather than
  a tilted spear sliding level — `pose(along)` scales the same
  `(0, −sin 47°, +cos 47°)` vector it built the rotation from.
  `setTransformationInterpolationDelay` forces its data entry dirty
  (`entityData.set(…, true)`), which is why re-sending the same `0` still
  restarts the client's interpolation clock; `setTransformationInterpolationDuration`
  does not, which is why it can be left at 0 at spawn and only written once.
- **One latch and one clock for the pair.** `stabbed` is a latch, not an
  equality check against `stabAt`: a lagged server tick can skip the exact
  gametime and the thrust has to fire on the next tick rather than never. It is
  one latch for both spears because they lunge together — a formation whose
  halves thrust on different ticks reads as two spears, which is the thing this
  node is not. Both gametimes come from `server.overworld().getGameTime()`, the
  clock every dimension shares, so a cast made in the Nether is not compared
  against a gametime read somewhere else.
- **The pair is the unit on the way out too.** If anything outside the class
  removed one of them — a `/kill`, an unloading chunk — the survivor is
  discarded with it rather than left standing alone. `SpearPhalanx.forget` on
  `SERVER_STOPPED` empties the static list, because an integrated server is torn
  down and rebuilt inside one JVM on every world exit; the spears themselves need
  no discard there, since the level is going away with them and the
  `shouldBeSaved` veto is what stops the shutdown save writing them down.
- **`DisplayAccessor` and `ItemDisplayAccessor` are `@Invoker`s, not direct
  calls, and the reason is a trap worth naming.** All five setters are `private`
  in the shipped class, but `fabric-transitive-access-wideners-v1` widens them,
  so the compile classpath and the decompiler both show `public final` while
  `javap` on the game jar shows `private`. Calling them straight would compile
  and would keep working only for as long as Fabric API keeps those entries in
  its list. An `@Invoker` owes that list nothing, is not an injection point that
  can silently find nothing, and still fails loudly at class-load if a name
  moves.
- **Player Animation Library was checked and rejected.** Its entire API
  (`PlayerAnimationAccess.getPlayerAnimManager`,
  `PlayerAnimationFactory.invoke`) is keyed on `Avatar` — an actual player
  entity — so there is nothing for it to animate on a floating weapon (and
  nothing on a packet puppet either, back when that was the plan).
- **Untested without a client.** Everything above is derived from the shipped
  bytecode and the shipped model JSON, and a headless server cannot look at it.
  The spears' final on-screen angle, where the shafts sit relative to the
  caster's shoulders, and whether the thrust reads as a lunge are an in-game
  pass.

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
| `ArchetypePickerScreen` | the pre-pick screen: three archetype cards, crest, preview actives |
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
   `Archetypes.onInitialize`, and any cooldown attachments to `ModAttachments`.
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
