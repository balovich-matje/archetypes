# Colossus Crusher — epic sub-tree design

Epic sibling of **CRUSHER** (Strength: mace and bare fists). Design doc only —
no Java, no assets, no gradle. Every mechanical claim below is anchored to code
that exists today; anything I could not verify is called out as uncertain in
§6.

---

## 1. Pitch

The Crusher is already a gravity build that has no way to get off the ground.
Meteor, Shockwave and vanilla's own mace smash all key on `fallDistance`
(`LivingEntityMixin.archetypes$sunderDamage`, `CrusherTicker.tick`,
`Tuning.SMASH_MIN_FALL`), and a mace player spends the whole fight hunting for a
ledge to jump off. Colossus gives the Crusher the sky. The root is a leap, and
the two branches are the two things a colossus does when it comes back down:
flatten the ground, or plant itself on it and refuse to move. At level 46 a
Crusher wants this because it is the first thing in the archetype that solves
the tree's actual friction — the mace path finally aims itself, and the fists
path finally has a way to close a gap it can't sprint across.

---

## 2. The root — TITAN'S LEAP

**One active. Ability slot 7** (slots 0–2 are the base sub-trees, 3 is the
Elementalist capstone, 4/5/6 are Lightning Strike, Magic Armaments and Dark
Ritual — see `Archetypes.java:64-135`). Suggested default key `K`; `G H B V N M
J` are taken.

**Cost:** no mana — Strength has none. A **30 second cooldown**, matching Quake
(`Tuning.QUAKE_COOLDOWN_TICKS = 600`). No channel: Dark Ritual owns the long
channel, Magic Armaments owns the sustained one, and the Crusher's actives are
instant-or-braced (Quake's 1.5s brace, Haymaker's single frame). A third
channel would be the tree copying its siblings.

**What it does:** launches the player ~15 blocks up and up to ~6 blocks along
their look direction, and cancels fall damage from that leap only. Requires a
mace or bare fists in hand (`WeaponClass.MACE` / `WeaponClass.HANDS`), the same
gate every Crusher active uses.

Why this is strong enough to define a tree without a single damage number on
it: a 15-block fall turns the vanilla mace smash into roughly 29 extra points of
damage on its own, before Density and before Meteor's `0.5 HP × rank × blocks
fallen`. The leap is a damage button that never says "damage".

**Critically: the leap must not reset `fallDistance`.** Fall damage has to be
cancelled at the damage hook, not by zeroing the fall — otherwise the leap
disarms the exact system it exists to feed.

---

## 3. The branches

Two branches, not three. The base Crusher already forks on weapon (fists flange
vs. mace flange), so the epic tree's job is to give each half of that fork a
different *purpose*, the way Nemesis Shadow splits ghost from predator. Three
branches at 9–10 nodes would leave every path too short to say anything.

### Left — SIEGE (mace). Artillery and demolition.

You leap, you come down on a crowd, the ground goes with them. This branch is
the only place damage numbers live. It cares about how far you fell and how
many things are standing under you.

**5-point build:** Titan's Leap → Aftershock 1 → Aftershock 2 → Aftershock 3 →
Siegebreaker. Mace in hand. You are a mortar: 8-block landing slam scaling with
fall height, on a cooldown that partially refunds itself when you use the leap
to break ground rather than bodies.

### Right — ANCHOR (fists-leaning). Control and survival, no damage.

Deliberately mirrors Ghost Form's brief: **not one node on this branch raises a
damage number.** You leap into the middle of a group, drag everything to you,
and become the thing they cannot move or kill. The payoff is that a fight
happens where you decided it would happen.

It is fists-*leaning* rather than fists-gated: Bulwark keys on Battle Trance,
which banks double with bare fists (`CrusherCombat.onCrusherHit`, ×2.0 for
`WeaponClass.HANDS`), and Iron Skin and Clinch already only pay with bare hands.
A mace Crusher can buy this branch; it just gets less out of it, without a hard
lockout to explain in a tooltip.

**5-point build:** Titan's Leap → Gravity Well → Immovable → Bulwark 1 →
Bulwark 2. You pull, you plant, you take 40% less while the trance holds.

### Are they genuinely exclusive?

Yes, at the payoff. Both branches are 4 nodes deep behind a shared root, so 5
points buys the root plus exactly one complete branch — the same economy
Nemesis Shadow uses. The cheapest hybrid (root + Aftershock 1 + Gravity Well +
Immovable + one more) forfeits both Aftershock 3 and Bulwark 2, i.e. both
capstones, and lands you with a 4-block slam and no damage reduction. There is
no line where one branch is a strictly better version of the other: the left
branch adds nothing to survival and the right branch adds nothing to damage.

---

## 4. Node table

Lang prefix `node.archetypes.colossus_crusher.<family>` / `.desc`, following
`NemesisShadowNodes.Family.nameKey()`.

| Family | Display | Ranks | Description |
|---|---|---|---|
| `TITAN_LEAP` | Titan's Leap | 1 | With a mace or bare fists: leap 15 blocks up and 6 blocks forward. You take no fall damage from that leap. 30s cooldown. |
| `AFTERSHOCK` | Aftershock | 3 | Landing from Titan's Leap slams the ground for x1.5 attack damage within 4/6/8 blocks, plus 0.25/0.5/0.75 hearts per block you fell, counting at most 20. Hostiles are launched. Mace in hand. |
| `SIEGEBREAKER` | Siegebreaker | 1 | Aftershock shatters every block up to iron-block hardness inside its radius, at one mace durability per block. If it hits no creature, Titan's Leap's cooldown is refunded. |
| `GRAVITY_WELL` | Gravity Well | 1 | Landing from Titan's Leap drags every creature within 12 blocks to you and holds them with Slowness VI for 3 seconds. It deals no damage and launches nothing. |
| `IMMOVABLE` | Immovable | 1 | You cannot be knocked back or launched — by hits, explosions or wind charges — whatever you hold, and you take no fall damage. |
| `BULWARK` | Bulwark | 2 | While Battle Trance has any health banked: incoming damage reduced 20/40%. Battle Trance's cap increased by 3/6 hearts. |

**Where the numbers come from.**

- *15 blocks / 6 forward.* 15 is the height at which vanilla's own mace tiers
  have stopped scaling steeply, so the leap is a full smash rather than an
  open-ended multiplier; 6 forward is roughly Haymaker's range doubled twice —
  enough to cross a fight, not enough to be a travel skill on its own.
- *30s cooldown.* Exactly Quake's (`QUAKE_COOLDOWN_TICKS = 600`). The two mace
  actives should not be on visibly different clocks.
- *x1.5 attack damage.* Lifted verbatim from Quake
  (`Tuning.QUAKE_DAMAGE_MULTIPLIER = 1.5F`) so Aftershock reads as the same
  slam and needs no second mental model.
- *4/6/8 block radius.* Quake's own radius is 3 (`QUAKE_RADIUS`); rank 1 is a
  small step up, rank 3 is a bit more than double. Earth Shatterer's radius
  already climbs 2/4/6, so 4/6/8 sits one notch above the base tree's ceiling —
  correct for epic.
- *0.25/0.5/0.75 hearts per block fallen.* Meteor pays 0.25/0.5 hearts per
  block (`METEOR_PER_BLOCK_PER_RANK = 0.5F`, HP units) to a **single** target.
  Aftershock pays the same curve one rank longer, but to a whole radius, only on
  a leap, and only once per 30s.
- *Capped at 20 blocks fallen.* Without this, a Colossus who leaps off a
  mountain rather than off the leap gets unbounded AoE. 20 is above the leap's
  own 15, so the cap only ever bites on terrain abuse.
- *Iron-block hardness.* `EARTH_SHATTER_MAX_HARDNESS` is 1.5 (stone). Iron
  blocks are 5.0. That is a real escalation — deepslate, ore, iron and gold
  blocks go — while obsidian (50) and blocks with hardness −1 stay safe, so the
  node cannot open a bedrock roof or an ender chest.
- *Slowness VI, 3s.* Haymaker's stun is Slowness VI for 1.5s
  (`HAYMAKER_STUN_AMPLIFIER = 5`, `HAYMAKER_STUN_TICKS = 30`). Gravity Well is
  the same hold, twice as long, over a whole radius, and with no damage attached.
- *20/40% damage reduction.* Conditional on Battle Trance having banked health,
  which decays 5 seconds after your last landed hit
  (`TRANCE_DECAY_DELAY_TICKS = 100`). It is a reward for still swinging, not a
  passive shield.
- *+3/6 hearts of trance cap.* Battle Trance caps at 2.0 HP per rank, 3 hearts
  at rank 3 (`TRANCE_CAP_PER_RANK = 2.0F`). Bulwark 2 triples that ceiling,
  which is the right size for the last node of a 5-point epic branch.

---

## 5. Grid

Written top-down, `#` is a node; bottom row is the root. 5 columns × 5 rows,
9 nodes. Reads as an anvil stood on its horn — a narrow foot opening into two
heavy legs.

```
#...#
#...#
#...#
.#.#.
..#..
```

Coordinates, `(col, row-from-bottom)`:

| Node | col | row |
|---|---|---|
| `TITAN_LEAP` 1 | 2 | 0 |
| `AFTERSHOCK` 1 | 1 | 1 |
| `AFTERSHOCK` 2 | 0 | 2 |
| `AFTERSHOCK` 3 | 0 | 3 |
| `SIEGEBREAKER` 1 | 0 | 4 |
| `GRAVITY_WELL` 1 | 3 | 1 |
| `IMMOVABLE` 1 | 4 | 2 |
| `BULWARK` 1 | 4 | 3 |
| `BULWARK` 2 | 4 | 4 |

**Explicit connections needed: none.** `Constellation.of` joins on
8-connectivity (`Constellation.java:57-66`), so (2,0)–(1,1), (2,0)–(3,1),
(1,1)–(0,2) and (3,1)–(4,2) all connect diagonally and each branch is a
straight vertical chain above that. The two branch feet at (1,1) and (3,1) are
two columns apart, so nothing cross-links them — the exclusivity is structural,
not a rule.

---

## 6. Implementation notes

### Reused, verbatim or nearly

- **`CrusherNodes`** — copy its `Family` enum / `Def` / `BY_INDEX` shape into a
  new `ColossusCrusherNodes`, but follow **`NemesisShadowNodes`** for the epic
  variant: `getOrDefault(..., MINOR)` in `def()` rather than throwing, plus the
  `rank(Player, Family)` convenience overload that every hook actually calls.
- **`CrusherActives.quakeSlam`** — Aftershock is this method with a radius and a
  fall bonus. Refactor to `slam(ServerPlayer, double radius, float extra,
  boolean launch)`; `quake` keeps calling it with `QUAKE_RADIUS`, `0`, `true`.
  Gravity Well passes `launch = false` and does its own pull.
- **`CrusherActives.quakeSlam`'s Earth Shatterer block loop** — Siegebreaker is
  the same nested loop with `EARTH_SHATTER_MAX_HARDNESS` swapped for a Colossus
  constant and the same `mace.hurtAndBreak(1, …)` / `break outer` durability
  bail-out.
- **`CrusherTicker.apply()`** — the transient-attribute idiom for Immovable's
  `KNOCKBACK_RESISTANCE` (Clinch already writes that attribute, so use a
  distinct `Identifier`; the attribute is ranged 0..1 so the two cannot
  overshoot).
- **`CrusherTicker.tick`** — the natural home for landing detection and for the
  `SMASH_AT` stamp that already exists.
- **`LivingEntityMixin.archetypes$sunderDamage`** — Aftershock must read the
  `SMASH_AT` stamp the same way this does, not live `fallDistance` (see the
  comment at `LivingEntityMixin:487-494`: vanilla resets `fallDistance`
  somewhere in the mace pipeline at a version-dependent point).
- **`LivingEntityMixin.archetypes$dimPresence`** (the `ModifyVariable` at
  `hurtServer` HEAD) — Bulwark's damage reduction is exactly that shape.
- **`LivingEntityMixin.archetypes$cheatDeathGrace`** (cancellable HEAD inject) —
  the pattern for cancelling fall damage without touching `fallDistance`.
- **`ModAttachments`** — two new `Long` attachments, `LEAP_READY_AT` (cooldown)
  and `LEAP_AT` (the in-flight stamp the landing consumes). Both follow
  `QUAKE_READY_AT` / `SMASH_AT`. Also add the tree to `forgetNodes` so a respec
  clears an in-flight leap, the way it calls `NightForm.end`.
- **`SubTree`** — new `COLOSSUS_CRUSHER(Archetype.STRENGTH, "colossus_crusher",
  …, true)` plus both arms of `epicCounterpart` / `baseCounterpart`.
- **`Constellations`** — `COLOSSUS_CRUSHER` from the grid in §5.
- **`ProcIndicators.send`** — for Bulwark and Aftershock, same as Shockwave.
- **`Archetypes.java`** — slot dispatch. Two edits: the guard
  `payload.slot() >= 7` becomes `>= 8`, and a `slot == 7` branch gated on
  `archetype == Archetype.STRENGTH`.
- **`ArchetypesClient`** — `ABILITY_KEYS` grows from 7 to 8 and the `defaults`
  array needs an eighth GLFW constant. Both are hardcoded to 7 today; missing
  either is a silent no-op or an AIOOBE at client init.

### Genuinely new

1. **The leap impulse.** Nothing in the mod launches a player upward.
   `RUSH_IMPULSE_PER_BLOCK = 0.45` is a *horizontal* approximation (Ghost Form's
   dash uses it) and **will not** give 15 blocks vertically — vertical travel is
   gravity plus per-tick drag, not linear in the impulse. Expect to tune
   empirically; a starting `dY` in the 1.5–1.8 blocks/tick range is the right
   neighbourhood, not 15 × 0.45. Use the `setDeltaMovement(...)` +
   `hurtMarked = true` idiom from `NightForm.dash`.
2. **Landing detection.** A tick-edge test in `CrusherTicker`: `LEAP_AT` set and
   `player.onGround()` newly true (or in water/lava, which should also end the
   leap without a slam). Must fire before anything clears `fallDistance`.
3. **Gravity Well's pull.** Same iteration as `quakeSlam`'s victim loop, with
   the push vector reversed and clamped so nothing is yanked *through* the
   player.
4. **Bulwark's conditional DR**, keyed on `player.getAbsorptionAmount() > 0`
   *and* Battle Trance being owned — absorption from other sources (golden
   apples, Oracle Wizard's Magic Armor) must not switch it on.

### FX and sound

- **Takeoff:** a ring of `BlockParticleOption(BLOCK, player.getBlockStateOn())`
  — the exact call `quakeSlam` already makes — plus `MACE_SMASH_AIR` pitched
  down to ~0.5. The ground should visibly give way where you pushed off.
- **In flight:** nothing. Silence at apex sells the drop.
- **Aftershock landing:** reuse `quakeSlam`'s whole FX block —
  `MACE_SMASH_GROUND_HEAVY` at 1.5/0.7, `GENERIC_EXPLODE`, the 40-point debris
  ring — with the ring radius scaled to the node's radius so 8 blocks reads as
  8 blocks.
- **Siegebreaker:** vanilla `destroyBlock(pos, true, player)` already emits each
  block's own break particles and sound, which is the right texture for a
  collapse. Add one `ANVIL_LAND` under it.
- **Gravity Well:** `ParticleTypes.PORTAL` or `SCULK_CHARGE` streaming *inward*
  along the pull vectors, and `CONDUIT_ACTIVATE` or `WARDEN_HEARTBEAT` pitched
  low. This node has no damage FX by design — the whole read is "everything
  moved toward him".
- **Immovable:** no persistent effect. One `ANVIL_LAND` at low volume the first
  time a knockback is nullified in a second, so the player learns the node
  is doing something.
- **Bulwark:** `ProcIndicators` only. The absorption hearts are already the HUD.
- **Icons:** `AFTERSHOCK` can reuse the existing
  `textures/node/shockwave_overlay.png` over `Items.MACE` (the base tree's
  overlay constructor already supports this). Suggestions for the rest:
  `TITAN_LEAP` → `Items.HEAVY_CORE`, `SIEGEBREAKER` → `Items.OBSIDIAN`,
  `GRAVITY_WELL` → `Items.CHAIN`, `IMMOVABLE` → `Items.ANVIL`, `BULWARK` →
  `Items.NETHERITE_CHESTPLATE`.

### Expected to be hard / unverified in 26.2

I did not verify these against the 26.2 sources; do not take them on trust.

- **`Items.HEAVY_CORE`** — I believe it exists (it is the mace's crafting
  component) but I have not confirmed the field name in this version. Pick
  another icon rather than guessing.
- **Explosion and wind-charge knockback.** `KNOCKBACK_RESISTANCE` does not
  govern explosion or wind-charge displacement. There is an
  `EXPLOSION_KNOCKBACK_RESISTANCE` attribute in recent versions, but **I have
  not confirmed it exists in 26.2** and nothing in this mod references it. If it
  is absent, Immovable's explosion clause needs a mixin on the explosion's
  velocity application, or the clause should be dropped from the description
  rather than shipped as a lie.
- **Pushing other players.** `hurtMarked = true` is what `quakeSlam` and
  `NightForm.dash` rely on, and it works for mobs and for the acting player.
  Forced motion applied to *another* `ServerPlayer` (Gravity Well in PvP) needs
  the motion packet sent explicitly. Verify before assuming Gravity Well works
  on players at all.
- **Fall damage cancellation ordering.** The leap has to survive vanilla's mace
  code, which resets `fallDistance` at a point this codebase already documents
  as version-dependent. Budget time for this specifically; it is the one thing
  that can quietly gut the whole tree.
- **Slot-7 plumbing.** Three files hardcode "7" today (`Archetypes.java:69`,
  `ArchetypesClient` `ABILITY_KEYS` and `defaults`). Grep for the literal before
  assuming two edits are enough. The cooldown bar also labels slots and may
  index off the same array.

---

## 7. Balance

**What 5 points buy, over a maxed base Crusher.**

*Siege line.* Base Quake is roughly x1.5 attack damage plus Density and Meteor
in a 3-block radius, once per 30s, and it needs you to already be somewhere
high for Meteor and Shockwave to matter. Siege turns that into: get high on
demand, land inside an 8-block radius, add up to 15 hearts of fall-scaled
damage on top, and flatten the terrain either way. Call it roughly double the
base burst, over roughly seven times the area, with the altitude problem solved.
That is a lot, but it is one button per 30 seconds, it requires physically
landing on the enemy, and it is the same order as Oracle Elementalist's
Overcharge (a single node for x2.0 on Lightning Strike).

*Anchor line.* Adds zero damage and instead: a 12-block forced gather, total
knockback immunity, 40% damage reduction while the trance holds, and a trance
ceiling of 6 hearts. Against the base tree's Iron Skin (+3 armour, +3
toughness) and Clinch, this is the difference between "hard to kill" and "the
fight is happening here now".

**Where it could go wrong.**

1. **Siegebreaker is a mining and griefing tool.** Iron-block hardness in an
   8-block radius, refunding its own cooldown when it hits nothing, is a
   strip-miner and a base-opener. This is the node most likely to need walking
   back — either keep the hardness cap at stone and only widen the radius, or
   gate the destruction behind a gamerule. Flag it to the author before
   building it.
2. **The leap trivialises vertical traversal.** 15 blocks every 30 seconds with
   no fall damage removes most of what mountains and ravines cost. With
   Siegebreaker's refund it approaches free. Consider not refunding when the
   slam broke no blocks *and* hit nothing, so the refund is a reward for
   demolition rather than for missing.
3. **Fall-scaling stacks multiplicatively with the base tree.** Aftershock 3 +
   Meteor 2 + Density V + Bare-Knuckle 4 all read the same fall. The 20-block
   cap is the only thing holding that down; if it is dropped in implementation
   the numbers get silly fast.
4. **Bulwark plus Iron Skin may be too much.** 40% DR on top of +3 armour, +3
   toughness and 6 hearts of absorption, on a class that also has knockback
   immunity, is close to unkillable in PvE. The saving grace is that the DR
   switches off five seconds after you stop landing hits — if that decay is
   ever loosened, Bulwark becomes the problem.
5. **Gravity Well in PvP.** Forced movement on other players is the kind of
   thing that feels awful on the receiving end and desyncs badly. If the motion
   packet work in §6 turns out to be fragile, restricting the pull to non-player
   entities is an acceptable ship.
6. **A mace-less Colossus.** Titan's Leap and the whole Anchor branch work with
   bare fists; Aftershock and Siegebreaker do not. That is intentional — the
   root serves both branches, and a fists Crusher spends the leap on engaging
   rather than on landing damage — but it means the tooltip on Aftershock has to
   say "Mace in hand" or fists players will buy a dead node.
