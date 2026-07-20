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
  `BLADESTORM_END`, `QUAKE_CHARGE_END`, `RADIANCE_END`.

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
| `ActiveAbilityPayload(slot)` | fire ability key `slot` (0–3) |
| `SpellChannelPayload` | one Flamethrower channel tick while the key is held |
| `MeleeSwingPayload` | announce a charged swing (the greatsword whoosh) |
| `RushPayload` / `DisengagePayload` | Shield Rush / the Marksman's Acrobatics roll (`AgilityActives.acrobatics`; the payload keeps its older Disengage name) |

Clientbound (server → client): `PassiveProcPayload` — a fire-and-forget "this
passive just fired" flash for `ProcIndicatorHud`. Everything else the client
needs (levels, cooldowns, mana) rides the synced attachments, so there is no
bespoke state packet.

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
bookkeeping, and mana regen this way. `SlayerCombat`, `AgilityCombat`, and
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
buyable when it is a root (`row() == 0`) or adjacent to an owned node, not
excluded by a capstone, under the per-tree cap, and the player has a point free.
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
  Strike or Deadeye, slot 5 Magic Armaments or Death Mark, slot 6 the Dark
  Ritual or Titan's Leap — the dispatch picks on archetype). Three epic trees
  claim no key: Oracle Priest's Aura of Radiance is painted `ACTIVE` but fires
  off a Holy Light cast, Colossus Protector's root is a flat passive, and
  Colossus Slayer's Parry is an attack+block input combo.
- **Exclusive capstone pairs**: `TreeNodes.exclusiveTaken(tree, owned, index)`
  encodes each tree's mutually-exclusive capstones (owning one locks the other),
  e.g. Slayer's Bladestorm|Decimate, Crusher's Quake|Haymaker, Protector's
  Bulwark(`OMNI_BLOCK`)|Ground Slam. Elementalist is special: its four capstones
  are **one choice total** — any owned capstone locks the other three.

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

- **The curve.** `COST[L] = 15 + (6L² + 2) / 5` (exact integer half-up rounding
  of `1.2L² + 15`), running unchanged to the epic cap, and `CUM[L]` is the
  cumulative XP to reach level `L`. Both tables are built in a `static` block
  that asserts the anchors (`CUM[45] = 38_349`, `CUM[60] = 89_472`,
  `CUM[15] = 1_713`, `COST[1] = 16`, `COST[45] = 2_445`) and throws if the curve
  drifts. `level(player)` walks `CUM`; `available(player) =
  max(min(level, 45) − spent, 0)` and `epicAvailable(player) =
  max(max(level − 45, 0) − epicSpent, 0)` keep the two pools apart.
- **Advancement multiplier.** Banking is scaled at deposit time by
  `xpMultiplier(advancementCount) = min(1 + 0.025 · count, 3.0)` — every completed
  non-recipe advancement adds 2.5% to the banking rate, tripling it at 80. The
  count is cached on the synced `ADVANCEMENT_COUNT` attachment: recomputed on join
  and by `PlayerAdvancementsMixin` (which recounts only when a *real*, displayable
  advancement is awarded or revoked, skipping the ~1,500 silent recipe unlocks).
  Because scaling happens at banking time, `ARCHETYPE_XP` stays an append-only
  ledger — retuning the rate never re-inflates past XP.
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

Creative-only `SkillTokenItem` (`skill_token`, `skill_token_45`) grants levels for
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
   `archetypes$greatswordDamage` (Heavy Blows → First Blood → Executioner),
   `archetypes$daggerDamage` (Razor Edge / Expose / Flense / Shadow Flurry / Twin
   Fangs + Venom/Blight/Crippling coatings), `archetypes$marksmanArrowHit`
   (delegates to `MarksmanCombat.onArrowHit`), `archetypes$firstStrike` (bonus out
   of invisibility), `archetypes$sunderDamage` (mace/fists armor-shred + Meteor
   smash bonus + the `CrusherCombat.onCrusherHit` batch). Attacker-side hooks
   check `source.getEntity()`; victim-side `archetypes$manaShield` checks
   `(Object)this` and drains the pool instead of health.
3. Separately, `archetypes$daggerKnockback` (`@ModifyVariable` on `knockback`)
   funnels all knockback: daggers and missiles shove at half, Flamethrower and
   Blizzard pulses at zero, and Clinch reduces a bare-fisted Crusher's shove.
4. `archetypes$bulwark` (`@ModifyExpressionValue` on the `Math.acos` block-angle
   check in `applyItemBlocking`) forces the angle to 0 so a Bulwark holder blocks
   from every direction.

Other mixins: `PlayerMixin` (XP mirror, plus the `canGlide` hook that lets a
Magic Armaments channel glide in an elytra's place — declared common because
`Player` is common and the client's jump-to-deploy runs the same check),
`PlayerAdvancementsMixin` (advancement
count), `AbstractArrowMixin`/`AbstractArrowAccessor`/`ProjectileMixin` (True Shot
flight and reflection), `CrossbowItemMixin` (Rapid Reload), `BlocksAttacksMixin`,
and `LivingEntityAccessor`. Client-side: `AvatarRendererMixin` (armor hiding,
ability poses), `LocalPlayerMixin`, `MinecraftMixin`, `HudMixin` (the night
form's grey hearts), `EntityRendererMixin` and `LevelExtractorMixin` (Extra
Sensory Perception's outlines and their exemption from occlusion culling), and
two accessors.

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
| `NightAnimations`, `NightFormFx`, `NightAuraLayer` | the Dark Ritual's pose, its particle column and quickening heartbeat plus the transformed body's trail, and the violet energy-swirl aura onlookers see on a vampire |
| `SunBlindOverlay`, `UndeadHud` | the night form's sun bloom, its grey hearts and its hidden hunger row |
| `ExtraSensoryPerception`, `NightIdentity` | the sensed-creature outline colours, and the two empowered active identities |
| `RadianceLight` | Aura of Radiance's block light, placed in the client's own level copy only |

**The night form's client half.** Everything the Nemesis Shadow's night form
looks and sounds like reads the synced attachments through `NightForm`'s static
predicates — there is no night-form packet. `NightFormFx` and `NightAnimations`
walk `level.players()` each client tick the way `SlayerAnimations` does, so
onlookers see a caster's ritual exactly as the caster does. The three display
overrides are gated per frame and hold no state to restore: `UndeadHud.active()`
decides both the hunger row's `replaceElement` and `HudMixin`'s heart-sprite
swap (our own `hud/heart/grey_*` set — vanilla's WITHERED sprites are left to
mean the Wither), and `SunBlindOverlay` snaps its bloom to zero the frame the
form ends. The aura and the trail are the one part that is NOT purely the
owner's view: `AvatarRendererMixin` writes `NightAuraLayer.ACTIVE` onto every
player's render state the way it does `BULWARK_ACTIVE`, and both it and
`NightFormFx`'s trail are suppressed whenever the player is invisible, so a
Cutpurse's Invisibility is never betrayed by their own vampirism. `ExtraSensoryPerception` supplies the
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
