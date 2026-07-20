# Colossus Slayer — epic sibling of SLAYER

Design doc only. No Java, no assets, no gradle touched. Every mechanical claim below
is anchored to code read in `archetypes/src`; anything I could not verify is marked
UNVERIFIED.

---

## 1. Pitch

The base Slayer is a blade that never stops moving: Hamstring cripples, Taste of Blood
tops you back up, and the sword arm (Rend, Blade Dance, Flurry, Bladestorm) or the
greatsword arm (Heavy Blows, First Blood, Executioner, Decimate) carries the kill. What
it never gives you is the moment where the fight stops being a trade and becomes a
slaughter — the Slayer wins by attrition, not by becoming something a mob cannot handle.
Colossus Slayer completes that: **Rampage** is a 20-second berserk you pay for in your
own blood, and everything above it is the question of what a colossus does with those
20 seconds — become a body nothing can stop (Ironhide, Unbreakable, Defiance), or become
a snowball that turns each kill into the next one (Frenzy, Carnage, Deathblow). A player
at 46 wants it because it is the first thing in the whole Strength line that they *press*
and that both weapons care about: the base tree's actives are weapon-locked (`Archetypes`
dispatch: greatsword → `SlayerActives.decimate`, sword → `SlayerActives.bladestorm`),
where Rampage is the tree's first weapon-agnostic active and multiplies whichever of the
two the player already committed 15 points to.

---

## 2. The root — Rampage

**Active.** One node, the sole bottom-row node, so it gates the tree exactly like Dark
Ritual gates Nemesis Shadow (`NodePurchases.check`: "Roots are the bottom row").

- **Keybind slot.** Recommended: **reuse ability slot 4** (`ABILITY_KEYS[4]`, default
  `N`, lang `key.archetypes.ability_5`), gated on `archetype == Archetype.STRENGTH`. In
  `Archetypes.java` slots 4/5/6 are already archetype-gated (`slot 4` returns without
  doing anything unless the player is INTELLECT), so a `STRENGTH` arm on the same slot
  costs zero new keys, zero new lang, and zero new `CooldownBarHud` plumbing beyond the
  tile itself. Strength's later epic trees then take 5 and 6 the same way. The
  alternative — grow `ABILITY_KEYS` to 8, add `key.archetypes.ability_8` and a default
  (`K` is free of vanilla binds; UNVERIFIED against the mod's own binds) — is only worth
  it if the author wants one physical key per epic tree forever. **Author decision.**
- **Cost: 3 hearts, paid on cast; cannot be cast below 4 hearts.** Strength has no mana
  pool (`Mana` is Intellect-only), so health is the only honest price, and it is the
  hook the whole left branch answers.
- **Duration/cooldown: 20 seconds, 90-second cooldown, kills add 2 seconds up to 40.**
  Two stamps, exactly like Bladestorm (`ModAttachments.BLADESTORM_END` +
  `BLADESTORM_READY_AT`): `RAMPAGE_END` and `RAMPAGE_READY_AT`. Not one merged stamp like
  the Dark Ritual — here the buff is much shorter than the cooldown.
- **What it does:** while it runs and a sword or greatsword is in hand, hits deal x1.5
  and you cannot be knocked back.
- **Scale reference:** the other epic roots are Lightning Strike (150 mana, 20 hearts),
  Magic Armaments (50 mana + 10/s channel), Dark Ritual (10s channel, 1-hour commitment),
  Aura of Radiance (a rider that doubles Holy Light's cost). Rampage's commitment is the
  3 hearts plus a 90s lockout — the smallest of the four, deliberately: this is the tree
  that has to work in an ordinary mob fight, not once an hour.

---

## 3. The branches

Both branches are usable with either blade, on purpose. A weapon-split epic tree would
be no choice at all — the player's base build already decided it at level 45.

### Left — Juggernaut (survival, zero damage added)

The Ghost Form analogue. Nothing on this line increases your damage by a single percent;
it buys the right to stand in the middle of the thing you are killing. Ironhide's flat
reduction, Unbreakable against the potion-and-witch layer, Defiance as the death you walk
away from. This is the branch for a horde, a raid, a deep-cave pull, or PvP where you are
outnumbered.

**Five-point build:** Rampage → Ironhide 1 → Ironhide 2 → Unbreakable → Defiance.
20 seconds at x1.5 damage, 35% less damage taken, immune to the debuff game, and one
free death. The 3-heart entry cost is the branch's own tax and it never gets paid back —
you just stop caring.

### Right — Reaper (offense, snowballing)

The predatory line. Every node feeds the same loop: Deathblow converts wounded targets
into kills, kills feed Frenzy stacks and Rampage's 2-second extension, Carnage spreads
hits so more things reach execute range. Against one target this branch is the weakest of
the two on paper (no stacks without kills); against five it is the fantasy.

**Five-point build:** Rampage → Frenzy 1 → Frenzy 2 → Carnage → Deathblow.
20+ seconds that get longer with every corpse, up to x1.5 × (1 + 5×0.20) = x3.0 attack
damage at full stacks, every swing splashing, everything under 20% dying on contact.
You take normal damage the whole time and you started 3 hearts down.

### Are they exclusive?

Yes, and cleanly. Each branch is exactly four nodes; the cap is five
(`SkillPoints.MAX_POINTS_PER_EPIC_SUB_TREE = 5`) and the root is compulsory, so a full
branch consumes the budget exactly. The only hybrid the grid allows is
Rampage + Ironhide 1-2 + Frenzy 1-2 (35% reduction and a two-stack ceiling of +40%) —
legal, coherent, and strictly worse than either capstone line, which is the shape a
hybrid should have. Neither branch dominates: Juggernaut adds no damage at all, Reaper
adds no survivability at all, and the root's health cost points at the left while the
root's kill-extension points at the right.

---

## 4. Node table

Family names are SCREAMING_SNAKE (enum constants in `ColossusSlayerNodes.Family`, keyed
`node.archetypes.colossus_slayer.<lowercase>` per the `nameKey()` convention).

| Family | Display | Ranks | Description (lang `.desc`) |
|---|---|---|---|
| `RAMPAGE` | Rampage | 1 | With a sword or greatsword: rage for 20 seconds. Your hits deal x1.5 damage and you cannot be knocked back. Every kill adds 2 seconds, up to 40. Costs 3 hearts, and needs 4 to cast. 90s cooldown. |
| `IRONHIDE` | Ironhide | 2 | You take 20/35% less damage during Rampage. |
| `UNBREAKABLE` | Unbreakable | 1 | Rampage clears your negative effects and keeps sweeping them off while it lasts. |
| `DEFIANCE` | Defiance | 1 | The first blow that would kill you during Rampage leaves you at half a heart instead. Once per Rampage. |
| `FRENZY` | Frenzy | 2 | Each kill during Rampage grants +10/20% attack damage for the rest of it, stacking 5 times. |
| `CARNAGE` | Carnage | 1 | During Rampage every hit also strikes everything within 3 blocks for x0.5 damage. |
| `DEATHBLOW` | Deathblow | 1 | During Rampage, sword and greatsword blows finish any target below 20% health outright. |

Where the numbers come from:

- **x1.5 / 20s / 90s.** Bladestorm is 45s and Decimate 30s (`Tuning.BLADESTORM_COOLDOWN_TICKS
  = 900`, `DECIMATE_COOLDOWN_TICKS = 600`), so a whole-kit buff sits above both at 90s.
  20s is long enough to cover a Bladestorm channel (`BLADESTORM_CHANNEL_TICKS = 60`) plus
  a Decimate and its Relentless-shortened recast, and short enough that the 90s cooldown
  reads as roughly one fight in three.
- **3 hearts.** Big enough to make a fresh Rampage a real decision at 7 hearts, small
  enough that the base tree's own Taste of Blood (0.5/1/1.5 hearts a kill) can pay it
  back inside the window.
- **20/35% reduction.** Deliberately below Ghost Form's 25/50/75% negate chance
  (`Tuning.GHOST_FORM_NEGATE_PER_RANK = 0.25F`, three ranks): flat reduction over two
  ranks and only for 20 seconds, against a chance-based void that lasts an hour.
- **+10/20% per kill, 5 stacks.** Ceiling of +100%, i.e. Rampage's x1.5 becomes x3.0 —
  the same order as Oracle Elementalist's Overcharge (x2.0 on the strike) but conditional
  on five kills inside the window.
- **20% execute.** Base Executioner is 15% and greatsword-only
  (`Tuning.EXECUTE_THRESHOLD`, `LivingEntityMixin.archetypes$greatswordDamage`); Deathblow
  is the epic version — five points wider and open to the sword — and it only exists for
  20 seconds at a time.
- **3 blocks / x0.5 for Carnage.** Same radius and same factor as Bladestorm's volleys
  (`BLADESTORM_RADIUS = 3.0`, `BLADESTORM_DAMAGE_FACTOR = 0.5F`), so the tree reuses a
  number the player already learned.

---

## 5. Grid

Sketchpad format, top-down, bottom row is the root. Five columns, five rows: the root at
the foot, two arms opening outward and rising into a pillar each — a giant's raised arms
around an empty middle.

```
tree: colossus-slayer
name: colossus-slayer-first

#...#
#...#
#...#
.#.#.
..#..
```

Coordinates, `(col, row-from-bottom)`:

| Node | Cell |
|---|---|
| `RAMPAGE` 1 | (2, 0) |
| `IRONHIDE` 1 | (1, 1) |
| `IRONHIDE` 2 | (0, 2) |
| `UNBREAKABLE` 1 | (0, 3) |
| `DEFIANCE` 1 | (0, 4) |
| `FRENZY` 1 | (3, 1) |
| `FRENZY` 2 | (4, 2) |
| `CARNAGE` 1 | (4, 3) |
| `DEATHBLOW` 1 | (4, 4) |

**No explicit `withEdge` calls are needed.** `Constellation.of` joins on 8-connectivity
(`Math.max(dx, dy) == 1`), so (2,0)–(1,1), (2,0)–(3,1), (1,1)–(0,2) and (3,1)–(4,2) are
all diagonal neighbours and connect by themselves. This is why the grid has no empty row
in the middle the way Nemesis Shadow does — that tree's `withEdge(1,1,0,3)` /
`withEdge(3,1,4,3)` exist only because its sketch left a two-row gap.

Nine nodes, one bottom-row root, two symmetric four-node arms. Verified against
`NodePurchases.check`: every node above row 0 has an owned-neighbour path back to
Rampage, and no node is reachable without it.

---

## 6. Implementation notes

### Reused, unchanged

- **Tree plumbing.** `SubTree` gets
  `COLOSSUS_SLAYER(Archetype.STRENGTH, "colossus_slayer", () -> Items.NETHERITE_SWORD,
  Constellations.COLOSSUS_SLAYER, true)` plus the `SLAYER ↔ COLOSSUS_SLAYER` arms in
  `epicCounterpart()` / `baseCounterpart()`. `SubTree.of` stays untouched.
  `Constellations.COLOSSUS_SLAYER` gets the grid above. `ColossusSlayerNodes` is a
  straight copy of `NemesisShadowNodes`' shape, including its
  `rank(Player, Family)` convenience overload. `TreeNodes` gets the three switch arms
  (`nameKey`, `descriptionKey`, `icon`) — the switches are exhaustive over `SubTree`, so
  the compiler will find them for you.
- **Point economy.** Nothing to do: `NodePurchases` already caps epic trees at 5 and
  spends `EPIC_SPENT_POINTS`, and the tree screen's epic switcher already handles any
  archetype (`ArchetypeScreen` uses `MAX_POINTS_PER_EPIC_SUB_TREE` generically).
- **Rampage's x1.5, and Frenzy on top of it.** Do NOT write a new damage hook. Use the
  transient-attribute idiom already in `SlayerTicker.tickStance` /
  `applyStanceModifier`: one `Attributes.ATTACK_DAMAGE` modifier with
  `Operation.ADD_MULTIPLIED_TOTAL`, value `0.5 + 0.10 * frenzyRank * stacks`, applied
  while `RAMPAGE_END` is live *and* `ModItems.isSword || ModItems.isGreatsword` holds,
  stripped otherwise. This is the reason to prefer the attribute over a `ModifyVariable`:
  `SlayerActives.decimate`, `SlayerTicker.tickBladestorm` and `SlayerCombat.bladeDance`
  all compute from `getAttributeValue(Attributes.ATTACK_DAMAGE)`, so Bladestorm, Decimate
  and Blade Dance inherit Rampage for free and no line of them changes. `AttributeModifier`
  is immutable — a stack change means remove-then-re-add with the new value.
- **Ironhide and Carnage** hang off the victim's intake exactly like Ghost Form and Feast
  do today: `@Inject(method = "hurtServer", at = @At("HEAD"))` in `LivingEntityMixin`
  (Ironhide as a `ModifyVariable` on `amount`, Carnage as a plain inject on the
  attacker's side). Carnage needs the same static reentrancy guard the codebase already
  uses twice — `SlayerCombat.dancing` and `NightForm.bleeding` — or its splash will splash
  itself.
- **Deathblow** is one more arm on the damage-shaping `ModifyVariable`
  (`archetypes$greatswordDamage`), or a sibling of it that accepts `isSword` too; copy
  the Executioner line verbatim — `result = Math.max(result, victim.getHealth() + 100.0F)`
  plus `ProcIndicators.send`.
- **Defiance** is `ServerLivingEntityEvents.ALLOW_DEATH`, confirmed present and used at
  `AgilityCombat.java:115` (Last Shadow) and `Archetypes.java:216`. Set health to 1.0F,
  mark a `RAMPAGE_SAVED` flag so it fires once per Rampage, return false.
- **Unbreakable** must be written as a *sweep*, not a block: I found no 26.2 API to veto
  effect application, and `MagicArmaments.ward` solves the identical problem by walking
  `getActiveEffects()` every `MAGIC_ARMAMENTS_WARD_PERIOD_TICKS` and removing everything
  whose category is `HARMFUL`. Copy that, and keep the lang wording ("keeps sweeping them
  off") honest about it.
- **The kill hooks** (Rampage's +2s extension, Frenzy's stacks) belong in the existing
  `ServerLivingEntityEvents.AFTER_DEATH` listener in `SlayerCombat`, next to Taste of
  Blood and Bloodlust.
- **HUD tile.** `CooldownBarHud.collect` gets one more block; the cooldown-driven
  `Ability` constructor `(tree, family, key, readyAt, totalTicks)` is exactly what
  Rampage wants, pointed at `RAMPAGE_READY_AT` / 1800 ticks.

### Genuinely new

- `Rampage.java` — a small state machine: `begin(ServerPlayer)`, `tick`, `end`, plus
  `isActive(Player)` / `remainingTicks(Player)` predicates for the client, modelled on
  `NightForm` but perhaps a fifth of its size (no channel, no undead-ness, no sunlight).
  Tick it from `SlayerTicker`'s existing `END_SERVER_TICK` loop rather than registering
  another one.
- New attachments in `ModAttachments`: `RAMPAGE_END` and `RAMPAGE_READY_AT` (`Long`),
  `RAMPAGE_STACKS` (`Int`), `RAMPAGE_SAVED` (`Boolean`). All **synced** (the client needs
  `RAMPAGE_END` for the HUD, the overlay and the pose) and all **transient, not
  copyOnDeath** — a 20-second rage should not survive a death, unlike the Dark Ritual's
  hour. `ModAttachments.forgetNodes` must call `Rampage.end` when the node is respecced
  away, the way it already calls `NightForm.end`.
- Tuning block: `RAMPAGE_TICKS = 400`, `RAMPAGE_MAX_TICKS = 800`,
  `RAMPAGE_KILL_BONUS_TICKS = 40`, `RAMPAGE_COOLDOWN_TICKS = 1800`,
  `RAMPAGE_HEALTH_COST = 6.0F`, `RAMPAGE_DAMAGE_BONUS = 0.5F`,
  `IRONHIDE_REDUCTION = {0.20F, 0.35F}` (or per-rank 0.20/0.15 stepped),
  `FRENZY_PER_RANK = 0.10F`, `FRENZY_MAX_STACKS = 5`, `CARNAGE_RADIUS = 3.0`,
  `CARNAGE_FACTOR = 0.5F`, `DEATHBLOW_THRESHOLD = 0.20F`,
  `UNBREAKABLE_PERIOD_TICKS = 10`.
- Nine node sprites under
  `assets/archetypes/textures/node/colossus_slayer/{rampage,ironhide,unbreakable,defiance,frenzy,carnage,deathblow}.png`
  — one per family, 32px, per the `TreeNodes.familySprite` resolver's
  `textures/node/<tree>/<family>.png` contract. Enum item fallbacks while art is pending:
  RAMPAGE → `BLAZE_POWDER`, IRONHIDE → `NETHERITE_SCRAP`, UNBREAKABLE → `MILK_BUCKET`,
  DEFIANCE → `TOTEM_OF_UNDYING`, FRENZY → `GHAST_TEAR`, CARNAGE → `REDSTONE`,
  DEATHBLOW → `WITHER_SKELETON_SKULL` (that last one is Nemesis Shadow's tree icon too —
  swap if the author minds).
- Lang: `subtree.archetypes.colossus_slayer` = "Colossus Slayer", plus the seven
  name/desc pairs from the table.

### FX

- **Cast:** `SoundEvents.RAVAGER_ROAR` at ~0.8 pitch, layered with
  `SoundEvents.ANVIL_LAND` low for weight (both vanilla, both used-adjacent in this repo's
  style of "borrow a vanilla cue and drop it a shade"). A burst of
  `ParticleTypes.ANGRY_VILLAGER` reads wrong (villager-coded); prefer
  `ParticleTypes.LAVA` + `DAMAGE_INDICATOR` at chest height, or `CRIMSON_SPORE` for a
  red haze.
- **During:** a slow drip of red particles at the feet, and — optional, cheap — a
  vignette overlay on the caster's screen. `SunBlindOverlay` is the template; it is
  already a per-client full-screen pass keyed off a synced attachment.
- **A PAL roar pose on cast** would sell it: `SlayerAnimations` already registers a
  `slayer_pose` layer and triggers one-shots (`bladestorm`, `decimate`) from synced
  attachments, so a `rampage` one-shot is one animation JSON and about ten lines.
- **Deathblow** should flash the proc indicator (`ProcIndicators.send`, which the base
  Executioner already does) and land a `PLAYER_ATTACK_CRIT` + a dry `WITHER_BREAK_BLOCK`.
- **Rampage ending:** small, per the house rule the Dark Ritual set —
  `SoundEvents.BEACON_DEACTIVATE` at 0.5/0.7 and a dozen `SMOKE`.

### Expected to be hard in 26.2

- **Ordering of Ironhide against armor.** Hooking `hurtServer` HEAD puts the 20/35%
  reduction *before* vanilla's armor/enchantment reduction, so the number a player
  measures at high armor will not be exactly 20/35% of what they took. Ghost Form dodges
  this by voiding the hit entirely. Acceptable, but say so in review; the alternative is
  finding the post-mitigation hook, and I did not verify one exists in 26.2.
- **Defiance vs. Last Shadow.** Both are `ALLOW_DEATH` listeners and they can only be
  held by different archetypes, so no conflict in practice — but registration order
  across two listeners returning `false` is worth one test.
- **Frenzy's stacks and the attribute modifier.** Remove-then-re-add on every stack change
  is fine, but any other transient `ATTACK_DAMAGE` modifier with the same `Identifier`
  will fight it; give it its own id (`Archetypes.id("rampage_damage")`).
- **The keybind decision (slot 4 reuse vs. an eighth key)** is the one thing I would not
  implement without the author's answer — it is visible in the controls screen forever.
- **UNVERIFIED:** that `K` is free of vanilla binds; that no post-mitigation damage hook
  exists in 26.2; that PAL's one-shot layer tolerates a pose whose trigger lasts 20+
  seconds (the existing poses are 0.6–3s, so a *roar* one-shot at cast is the safe
  design, not a pose held for the whole rage).

---

## 7. Balance

**What five points add over the base tree.** The base 15-point greatsword build already
runs Heavy Blows 3 (x1.3), First Blood, Executioner at 15%, Decimate (x2.0) and Relentless
(15s recast). A netherite greatsword sits at 12 attack damage (`ModItems.baseDamageFor`:
`1.5 × (1 + 3 + 4) − 1 − 4 = 7`, plus the player's 1 and the material's 4). Rampage takes
each of those numbers up by half for 20 seconds every 90: a Decimate inside Rampage is
12 × 1.3 × 2.0 × 1.5 ≈ 47 damage in a 3.5-block front arc, which is roughly 23 hearts of
AoE. That is very large — but it is one press, on a 30s (15s with Relentless) inner
cooldown, inside a 20s window, for 3 hearts, and it is the whole reason a level-46 player
opens this tree at all. Down the Reaper line at full Frenzy the same cleave doubles again.

**Where it could go wrong.**

1. **Frenzy × Carnage × Deathblow in a mob farm or a raid.** Carnage widens the hit,
   Deathblow converts anything below 20% to a corpse, corpses extend Rampage and grow
   Frenzy. In a dense enough crowd the rage may never end (2s per kill against a 40s cap
   means the cap is the real brake — keep it). Watch the cap, not the stacks.
2. **Deathblow in PvP.** A 20% execute on any blade is harsher against players than
   against mobs, since a player at 20% is four hearts from a hit that would not otherwise
   land. Base Executioner already does this at 15%, so parity argues for leaving it — but
   if the author wants a lever, halve the threshold against players, and say so in the
   description.
3. **Defiance + Ironhide + a 20-second window** is a very good PvP kit for one press.
   The counterplay is the 90-second cooldown and the fact that the whole branch adds no
   damage; if it still reads as oppressive, the cheapest nerf is duration (20 → 15),
   not the reduction.
4. **The 3-heart cost is a trap at low health.** The "needs 4 hearts" floor stops the
   suicide case, but a player who presses the key at 5 hearts and then loses is going to
   blame the ability. If playtesting shows that, make the cost absorption-shaped instead
   (drain, then Rampage's first kill refunds it) rather than raising the floor.
5. **Rampage stacks with everything, by construction.** Routing it through
   `ATTACK_DAMAGE` is what makes the implementation small, and it is also what makes it
   multiply Bladestorm, Decimate, Blade Dance, Rend's per-tick — check Rend: it uses the
   flat rank as damage (`SlayerTicker.tickBleeds` passes `rank()`), so bleeds do *not*
   scale, which is correct and worth leaving alone.
