# Nemesis Marksman — epic sub-tree design

Epic sibling of MARKSMAN (Agility / Cutpurse). Tree id `nemesis_marksman`,
lang prefix `node.archetypes.nemesis_marksman.*`. Nine nodes, five buyable.

Status: design only. No Java, no assets, no lang written.

---

## 1. Pitch

The base Marksman is a tree about **one** arrow. True Shot arms it, Seeker Arrow
aims it, Snap Shot fires it at x4.0, Focus hands it back — everything else
(Conservation, Swift Flight, Rapid Reload, Nimble Draw) exists to keep you
shooting between the big shots. What the tree never gives you is a moment where
the bow itself stops being a slow weapon. Nemesis Marksman is that moment:
**Deadeye**, fifteen seconds in which draw time stops mattering, gravity stops
mattering and your quiver stops emptying, followed by a minute and a half of
being an ordinary archer again. A level-46 Cutpurse wants it because it is the
first thing in the archetype that answers "what do I do when the fight is
already happening and True Shot is on cooldown" — and because the two branches
answer that question in opposite ways: plant and delete, or never stop moving.

---

## 2. The root — DEADEYE (active)

**Keybind slot 7** (`ArchetypesClient.ABILITY_KEYS[7]`, lang
`key.archetypes.ability_8`, suggested default `GLFW_KEY_K`). Slot 6 is already
the Dark Ritual; slot 7 is the second Agility epic key, dispatched in
`Archetypes` under `archetype == Archetype.AGILITY` exactly as slot 6 is. The
`payload.slot() >= 7` guard becomes `>= 8` and `ABILITY_KEYS` grows to 8.

**Cost.** Not mana — Agility has no pool. Deadeye is priced the way the Dark
Ritual is priced, in commitment: **15 seconds up, 90 seconds cooldown, and
Slowness II the whole time it holds**. Requires a bow or crossbow in the main
hand to start (same gate shape as `AgilityActives.trueShot`, which checks
`getMainHandItem().is(Items.BOW)`). Pressing the key while it is up does
nothing — like the night form, you do not get to end it early. Unlike the night
form it is short, so the cooldown is a second stamp rather than one shared one.

**What it does while up:**

- *Full draw, always.* Every bow arrow leaves at full-draw speed however briefly
  you pulled. This is a spawn-time rescale in `MarksmanCombat.onArrowSpawn`, not
  a draw-time mixin: arrow damage scales with impact speed (the Swift Flight
  comment in that file says so and divides `baseDamage` back down to compensate),
  so raising an underdrawn arrow's `deltaMovement` to full-draw speed raises its
  damage with it. No new BowItem mixin, no server/client draw disagreement.
- *Crossbows charge instantly.* `CrossbowItemMixin.getChargeDuration` already
  exists for Rapid Reload; Deadeye returns 1 from it.
- *Flat flight.* `setNoGravity(true)` and the 64-block despawn, i.e. exactly
  `AgilityActives.empower(arrow, 1.0F, false)` plus the existing
  `AbstractArrowMixin.archetypes$trueShotFlight` range check on
  `TRUE_SHOT_ORIGIN` / `Tuning.TRUE_SHOT_RANGE_BLOCKS`.
- *Arrows are not consumed.* The Conservation refund path in
  `MarksmanCombat.onArrowSpawn`, at 100% instead of 12.5% per rank, including
  the `Pickup.CREATIVE_ONLY` downgrade that stops double collection. You still
  need one arrow in the inventory to fire — nothing is conjured.
- *Slowness II* re-asserted each tick, hidden, and left to lapse rather than
  removed — the idiom `NightForm.tickForm` uses for Slow Falling and Night
  Vision.

No damage multiplier at the root. The branches decide whether Deadeye is damage
at all.

Deliberately **not** included: a sprint lock. Suppressing sprint server-side
means fighting the client's own sprint state every tick; Slowness II carries the
"you are rooted artillery" read on its own.

---

## 3. The branches

Five points buy the root plus exactly one four-node line. Crossing over is
impossible: the other line's foot is a separate purchase off the root and there
is no sixth point. The only real decision inside a build is whether to spend the
fifth point on your capstone or on the shared **Long Watch** node.

### Left — Fleet: the skirmisher (mobility / defence, no damage nodes)

The Ghost Form analogue. It buys back everything Deadeye took and then some:
Slowness gone and Speed II instead, an Acrobatics roll that is four times longer,
usable with a crossbow and in mid-air, on a 3-second clock instead of 8, fall
damage off, and finally projectiles passing straight through you. Not one node
on this line raises a damage number. It is for the player who wants to fight
five skeletons in the open and win by never being where the arrows are.

**Five-point build:** Deadeye → Fleet → Vault → On the Wing → Evasion.

### Right — Siege: the sniper (lethality, immobile)

Leans into what the root already did to you. Damage that grows with the distance
the arrow has flown, a full armour bypass with pass-through, and a capstone that
doubles your damage *only while you are standing still*. It is the strongest
damage in the archetype and it is worthless in a melee scrum, at close range, or
while kiting. The two capstones are opposite instructions: Evasion says keep
moving, Siege says stop.

**Five-point build:** Deadeye → Long Shot 1 → Long Shot 2 → Punch Through → Siege.

### Shared — Long Watch (the dip)

Hangs off both lines at their second rung. Buying it costs you your capstone:
Deadeye → three of a line → Long Watch is also exactly five points. Trading
Evasion or Siege for ten more seconds of stance is a genuine call, and it is the
only node either build shares.

---

## 4. Node table

| Family (SCREAMING_SNAKE) | Display | Ranks | Description |
| --- | --- | --- | --- |
| `DEADEYE` | Deadeye | 1 | Enter Deadeye for 15 seconds. Your arrows leave at full draw however briefly you pull, fly flat out to 64 blocks and are not consumed, and crossbows charge instantly. Slowness II while it holds. 90 seconds cooldown. Bow or crossbow in hand. |
| `LONG_SHOT` | Long Shot | 2 | Deadeye arrows deal 2/4% more damage for every block they have flown, up to x2.0/x3.0 at 50 blocks. |
| `PUNCH_THROUGH` | Punch Through | 1 | Deadeye arrows ignore all of the target's armor and pass through 2 creatures before stopping. |
| `SIEGE` | Siege | 1 | While you stand still in Deadeye your arrows deal x2.0 damage and you cannot be knocked back. Moving ends it; standing still for 1 second brings it back. |
| `FLEET` | Fleet | 1 | Deadeye no longer slows you and grants Speed II while it holds. |
| `VAULT` | Vault | 1 | Acrobatics rolls 8 blocks, works with a crossbow and in mid-air, and its cooldown is 3 seconds. |
| `ON_THE_WING` | On the Wing | 1 | While Deadeye holds you take no fall damage and gain Slow Falling. Each arrow that hits takes 2 seconds off Acrobatics' cooldown. |
| `EVASION` | Evasion | 1 | While Deadeye holds, arrows and other projectiles pass through you. |
| `LONG_WATCH` | Long Watch | 1 | Deadeye lasts 25 seconds. |

### Where the numbers come from

- **15s / 90s.** True Shot is 400 ticks (20s), Snap Shot fires on it, Focus
  refunds 200 ticks per hit. A stance that removes draw time is worth roughly
  four to six ordinary shots' worth of extra output; 15 seconds of uninterrupted
  full-draw fire is about that, and a 90-second lockout keeps Deadeye a fight
  opener rather than a rotation button. It never overlaps itself.
- **2/4% per block, cap x2.0/x3.0.** Both ranks cap at exactly 50 blocks, which
  is inside the arrow's 64-block despawn (`Tuning.TRUE_SHOT_RANGE_BLOCKS`) but
  well outside any mob's aggro range — you have to choose to fight at range to
  get paid. At the 10–15 block range where bows are actually used it is
  x1.2–x1.6 at rank 2, i.e. comparable to a base-tree node, not a capstone.
- **Ignore all armor.** Piercing Tips already ignores 2 points by compensating
  `amount * 0.04 * min(2, armor)`; Punch Through is the same expression with the
  clamp removed. Against full netherite (~20 armor) that is roughly +80% damage,
  and against an unarmoured mob it is nothing — the same shape the base node has.
- **Pass through 2.** One below vanilla Piercing IV's reach, so an enchanted
  crossbow is still doing something the tree is not.
- **x2.0 while planted.** The largest single multiplier in the tree, gated on the
  hardest condition in it. It is Snap Shot's x4.0 halved, because unlike Snap
  Shot it applies to every arrow.
- **Slowness II / Speed II.** Vanilla's -30% and +40% — Fleet is a net swing of
  about 70% movement, which is what makes it worth a point on its own.
- **8 blocks, 3s.** Acrobatics is 2/4 blocks on 8s
  (`Tuning.ACROBATICS_BLOCKS_PER_RANK = 2.0`, `DISENGAGE_COOLDOWN_TICKS`). Vault
  doubles the maxed distance and cuts the clock by 5/8 — plus the two condition
  removals, which are most of its value.
- **2s off Acrobatics per hit.** Focus refunds 10s off a 20s cooldown per hit;
  this is the same ratio against a 3-second cooldown, i.e. hitting keeps you
  rolling.
- **25s (Long Watch).** +67% uptime, which against a capstone worth roughly a
  doubling is a fair trade rather than an obvious one.

---

## 5. Grid

Sketchpad format, top-down, bottom row is the root. 5 columns × 6 rows — the same
footprint as Nemesis Shadow.

```
#.#.#
#...#
#...#
.....
.#.#.
..#..
```

Coordinates, (col, row-from-bottom):

| Node | col | row |
| --- | --- | --- |
| `DEADEYE` 1 | 2 | 0 |
| `FLEET` 1 | 1 | 1 |
| `LONG_SHOT` 1 | 3 | 1 |
| `VAULT` 1 | 0 | 3 |
| `LONG_SHOT` 2 | 4 | 3 |
| `ON_THE_WING` 1 | 0 | 4 |
| `PUNCH_THROUGH` 1 | 4 | 4 |
| `EVASION` 1 | 0 | 5 |
| `LONG_WATCH` 1 | 2 | 5 |
| `SIEGE` 1 | 4 | 5 |

Row 2 is empty on purpose, exactly as in Nemesis Shadow: the two feet sit low and
inside, the two lines run high and outside, and the gap is crossed by explicit
edges so the shape reads as a bow's two limbs rather than as two touching columns.

Explicit connections (the grid's 8-connectivity does not supply these):

```
LINE + FLEET 1 <-> VAULT 1                 (1,1) <-> (0,3)
LINE + LONG_SHOT 1 <-> LONG_SHOT 2         (3,1) <-> (4,3)
LINE + ON_THE_WING 1 <-> LONG_WATCH 1      (0,4) <-> (2,5)
LINE + PUNCH_THROUGH 1 <-> LONG_WATCH 1    (4,4) <-> (2,5)
```

Everything else is grid-adjacent: the root at (2,0) touches both feet
diagonally, (0,3)-(0,4)-(0,5) and (4,3)-(4,4)-(4,5) are vertical runs.

Note the dip node hangs off the **third** rung of each line, not the second — so
Long Watch is the fifth point either way, and no build can reach both Long Watch
and a capstone. `NodePurchases.buy` already enforces exactly this: row 0 is free
to buy, everything else needs an owned neighbour along `shape.edges()`.

---

## 6. Implementation notes

### Reused as-is

- `MarksmanCombat.onArrowSpawn` — the full-draw rescale, the 100% no-consume
  path (the existing Conservation branch with a Deadeye short-circuit), and
  stamping the arrow's origin.
- `MarksmanCombat.onArrowHit` — Long Shot's distance multiplier, Punch Through's
  armour bypass and Siege's planted multiplier all belong in the returned shaped
  damage, alongside the existing Piercing Tips compensation. This is called from
  the damage-shaping mixin rather than AFTER_DAMAGE, which is why killing blows
  still get the bonuses (see that file's class comment).
- `AgilityActives.empower` — no-gravity plus `TRUE_SHOT_ORIGIN`, and
  `AbstractArrowMixin.archetypes$trueShotFlight` for the 64-block despawn. Same
  attachment carries the origin Long Shot measures from.
- `AbstractArrowAccessor` — `baseDamage` get/set for the full-draw rescale.
- `CrossbowItemMixin.getChargeDuration` — instant charge.
- `ProjectileMixin.archetypes$incorporeal` (`canHitEntity` HEAD, cancellable) —
  Evasion is a second predicate on the same hook.
- `LivingEntityMixin`'s `knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V`
  injections (there are two already, for Steadfast and Incorporeal) — Siege adds
  a third condition.
- `AgilityActives.acrobatics` — Vault relaxes its three conditions
  (`isUsingItem()`, `getUseItem().is(Items.BOW)`, the cooldown constant) and
  scales `ACROBATICS_BLOCKS_PER_RANK`.
- `NodePurchases`, `SkillPoints.epicAvailable`, the 5-point epic cap, the
  bookmark/legend plumbing — all already generic over `SubTree.isEpic()`.
- `CooldownBarHud` — a tile in the same shape as the Dark Ritual's, on
  `ABILITY_KEYS[7]`, drained off `DEADEYE_READY_AT`.

### Genuinely new

- `NemesisMarksmanNodes.java` — a straight copy of `NemesisShadowNodes`'s shape
  (Family enum with icon suppliers, `Def`, `BY_INDEX`, the size-mismatch guard,
  the two `rank` overloads including the `Player` one).
- `Constellations.NEMESIS_MARKSMAN` — the grid above plus the four `withEdge`
  calls.
- `SubTree.NEMESIS_MARKSMAN` — Agility, epic `true`, icon `Items.TIPPED_ARROW`;
  add to `epicCounterpart` (MARKSMAN →) and `baseCounterpart`.
- `Deadeye.java` — the small state machine: `isActive(Player)`,
  `remainingTicks(Player)`, `isPlanted(Player)`, `toggle(ServerPlayer)`,
  `tick(ServerPlayer)`. Modelled on `NightForm` but with one state instead of
  three and no persistence concerns (15 seconds cannot survive a relog; clear it
  on JOIN the way the ritual channel is cleared).
- Attachments: `DEADEYE_END` (sync `all()` — other clients need it for the FX
  and the third-person pose), `DEADEYE_READY_AT` (`targetOnly()`),
  `DEADEYE_STILL_SINCE` (`all()`, drives Siege's 1-second arm and its particles).
- Ticker hook: one call from `AgilityTicker` per player per tick — the Slowness
  re-assert, the still/moving test, the lapse.
- Slot-7 dispatch in `Archetypes`, the eighth key in `ArchetypesClient`, and the
  `key.archetypes.ability_8` lang entry.

### Icons

`DEADEYE` → `SPYGLASS`, `LONG_SHOT` → `SPECTRAL_ARROW`, `PUNCH_THROUGH` →
`FLINT`, `SIEGE` → `TARGET`, `FLEET` → `SUGAR`, `VAULT` → `RABBIT_FOOT` (the
deliberate echo of base Acrobatics, which it upgrades), `ON_THE_WING` →
`ELYTRA`, `EVASION` → `WIND_CHARGE`, `LONG_WATCH` → `CLOCK`. Sub-tree icon
`TIPPED_ARROW`.

### Effects

- **Deadeye start:** `SoundEvents.SPYGLASS_USE` at pitch 0.8, layered under a
  low `SoundEvents.BEACON_ACTIVATE` (0.6 volume, 0.7 pitch). End:
  `SoundEvents.SPYGLASS_STOP_USING`, quiet — the lapse should be smaller than the
  arrival, the rule `NightForm.end` follows.
- **Deadeye holding:** a narrow client vignette, built the way
  `SunBlindOverlay` is built (a full-screen overlay class registered client-side)
  — dark at the edges, clear in the middle, ~15% strength. It should read as
  concentration, not as damage.
- **Deadeye arrows in flight:** a thin `ParticleTypes.CRIT` trail from the
  per-tick hook that already exists in `AbstractArrowMixin` for the Spellbow.
- **Siege planted:** `ParticleTypes.ELECTRIC_SPARK` at the feet the moment the
  1-second arm completes, and a soft `SoundEvents.NOTE_BLOCK_PLING` (low pitch).
  The player needs to *know* they are planted, because moving loses it silently.
- **Evasion:** a `ParticleTypes.CLOUD` puff where a projectile passes through,
  so the target and the shooter both see it happen.
- **Vault:** the existing Acrobatics `RABBIT_JUMP` cue, pitched up.

### Expected to be awkward in 26.2

- **Pass-through.** `AbstractArrow` has historically had `setPierceLevel(byte)`;
  I have **not** verified it survives under that name in 26.2. If it does not,
  Punch Through falls back to the same `canHitEntity` trick Seeker Arrow already
  uses in `AbstractArrowMixin.archetypes$seekerPassesThrough`, with a hit counter
  in an arrow attachment.
- **Full-draw rescale.** Raising `deltaMovement` at `ENTITY_LOAD` needs
  `hurtMarked = true` (as Swift Flight does) or the client never sees it. Watch
  the ordering against Swift Flight's own rescale — they must compose, not
  overwrite. The bow's *animation* will still show a half-drawn string on a
  snap-release; the client `UseDurationMixin` is the place to fix that if the
  author minds, but it is cosmetic and it is a second mixin for a 15-second
  stance, so I would ship without it first.
- **Standing still.** Do not trust `getDeltaMovement()` on a player — the client
  is authoritative about movement. Compare the server-side position between
  ticks with a small tolerance and reset `DEADEYE_STILL_SINCE` when it moves.
- **Fall damage.** I did not confirm which hook Ghost Form's damage negation uses;
  `LivingEntityMixin` has several `hurtServer` HEAD-cancellable injections and
  On the Wing should join whichever one already handles a typed source, rather
  than adding a new one.
- **Deadeye + Magic Armaments' Spellbow** cannot collide (different archetypes),
  but Deadeye + Specialities' Archery draw-time reduction can. The full-draw
  rescale is velocity-based rather than time-based, so it should be neutral to
  Archery — worth an actual test, since `MagicArmaments.drawTimeFactor` exists
  precisely because the two systems compounded once already.

---

## 7. Balance

**What five points add over the base tree.** The base Marksman at full build
already has True Shot at x2.0 (or x4.0 through Snap Shot) on a cooldown Focus
keeps resetting, 2 armour ignored, 50% arrow refunds, and Slowness on hits. The
epic tree adds no permanent numbers at all — everything it gives lives inside a
15-second window that is available for one sixth of the time. Over a long fight
the Siege line is worth roughly a 60–80% damage increase *if the player actually
plays at 30+ blocks and stands still*, and close to nothing if they do not. The
Fleet line adds zero damage and instead removes a category of incoming damage
for 15 seconds per 90.

**Where it can go wrong.**

1. **Multiplier stacking.** Snap Shot (x4.0) × Long Shot 2 (x3.0) × Siege (x2.0)
   on an armour-bypassed arrow is x24 on one shot. That is the failure mode of
   this design and it must be closed at implementation time. Recommendation:
   **Long Shot and Siege do not apply to True Shot / Snap Shot arrows** — the
   epic tree buffs the *stream* of ordinary shots, the base tree owns the one big
   shot. Belt and braces: a `Tuning.DEADEYE_MAX_MULTIPLIER = 6.0F` clamp on the
   product of everything Deadeye contributes.
2. **PvP Evasion.** Fifteen seconds of total projectile immunity against another
   archer is not "defensive", it is a win button in that matchup. If it plays
   badly, the honest fix is a chance (say 75%) rather than immunity, which is
   also how Ghost Form already words its negation.
3. **Siege in a doorway.** Standing still is trivially safe in the right terrain;
   a player who blocks themselves into a one-block hole gets the capstone for
   free. Acceptable — it costs them the whole Fleet line and it is a choice about
   how to fight, not a bypass — but worth watching in the first playtest.
4. **Long Watch's uptime.** 25 up / 90 cooldown is 28% uptime. If Deadeye ends up
   feeling mandatory rather than special, raise the cooldown before touching the
   node numbers; the cooldown is the one dial that changes nothing else.
5. **Free arrows.** No-consume plus instant crossbow charge is a lot of projectiles
   in the air. Watch the entity count on a 15-second Deadeye with a Multishot
   crossbow before shipping.
