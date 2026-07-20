# Nemesis Assassin (epic sibling of the Assassin dagger tree)

Design doc only — no Java, no assets. Everything mechanical below is anchored to code that
exists today: `AssassinNodes`, `AgilityActives.shadowStep`, `LivingEntityMixin#archetypes$daggerDamage`,
`AgilityCombat`'s `AFTER_DEATH` hook, `NightForm` (the Nemesis Shadow precedent), `ModItems`
dagger maths, and `Constellation`'s 8-connectivity.

## 1. Pitch

The base Assassin is a knife fighter: fast, flimsy, and at its best when it picks the fight —
Shadow Step drops you behind something and Shadow Flurry makes that one blow count, but only
for 16 blocks and only every 15 seconds, and the tree never says *who* you were coming for.
The Nemesis Assassin is the contract killer: you name a victim, and from that moment the world
narrows to it. **Death Mark** puts a name on one creature for a minute, and while that name
holds, Shadow Step stops being a gap-closer and becomes delivery — it always lands on the mark,
from twice the range, and it hits harder for it. A level-46 Cutpurse who has already bought
Shadow Flurry, Twin Fangs and Flense wants this because their kit is a single enormous blow
looking for a target, and this is the tree that hands them the target, tells them where it is
through the walls, and then decides what a dead mark is worth: one clean execution, or a room.

## 2. The root — DEATH MARK (active)

**Keybind slot 7** (0-indexed; the eighth key). Slots 0-2 are the base sub-trees, 3 is the
Elementalist capstone, 4-6 are Lightning Strike / Magic Armaments / Dark Ritual. Slot 7 is the
next free one and the second non-Intellect epic key — `Archetypes.java` currently rejects
`payload.slot() >= 7` and `ArchetypesClient.ABILITY_KEYS` is length 7, so both move to 8, with a
new default bind (`GLFW_KEY_K` is free next to the Dark Ritual's `J`).

**Cost.** Not mana — Agility has no pool, and the two Cutpurse actives already price themselves
in cooldowns (Shadow Step 300 ticks, Invisibility 600). Death Mark costs **45s cooldown, cleared
the moment the mark dies.** That is deliberately the Dark Ritual's shape inverted: the Ritual
commits you to an hour you cannot undo, this commits you to nothing but demands you finish the
job — kill the mark and you may immediately name another; let it walk away and you wait the
full 45 seconds. It is a strong active because of what it does to Shadow Step, not because of a
damage number:

- one mark at a time, 60 seconds, re-casting moves it;
- your dagger hits on the mark deal **+25%**;
- **Shadow Step always jumps to the mark**, ignoring the crosshair, at up to **32 blocks**
  instead of 16.

That last line is the tree. Shadow Step today needs a clean line to a body within 16 blocks
(`ProjectileUtil.getEntityHitResult`, `Tuning.SHADOW_STEP_RANGE = 16.0`); with a mark up, it is
a guaranteed 32-block teleport onto a named victim, and every node above either makes that
arrival lethal or makes the mark's death do something to everything around it.

## 3. The branches

Two, not three. The point cap is 5 and the root costs one, so a branch of four nodes is
exactly a build; a third branch would force 3-node branches and turn every build into
"one branch plus a dip", which is the failure mode the Nemesis Shadow's shape avoids. The
grid joins the branches only at the root, so the two 5-point builds below are the only two
complete builds that exist.

### Left — THE HUNT (single target; duelist, boss-killer, PvP)

The predator half, the sibling of Nemesis Shadow's stalking branch. Everything here is about
one creature dying: you see it through terrain, it stops noticing you, it takes far more from
your dagger, and under 30% health the Shadow Step strike simply ends it. No AoE, nothing that
helps you against a second enemy, nothing defensive at all.

**5-point build:** Death Mark → Stalk → Headhunter 1 → Headhunter 2 → Coup de Grace. You mark
a Warden / a raid captain / a player across the field, watch it through the walls while you
close, and open with a Shadow Step strike at +25% (mark) × +50% (Headhunter), then finish it
with an execute the moment it drops under a third. Against anything with a big health bar this
is the only branch worth owning.

### Right — THE MASSACRE (crowds; the mark as a fuse)

The mark is not a victim here, it is a delivery mechanism. Nothing on this branch raises your
dagger damage by a single percent — the sibling relationship the author asked for. Instead the
mark carries your existing coatings outward (Venom, Blight and Crippling Poison from the base
tree), and when it dies it detonates and moves on to the next body, with four seconds of
invisibility for you in the gap.

**5-point build:** Death Mark → Contagion → Carrier → Vanishing Act → Death's Head. You mark
one mob in a pack, poison it, and its death is a 5-heart pulse that hops the mark to the next
thing standing — a chain that runs as long as bodies keep being adjacent. Against a boss it is
close to worthless (no direct damage, nothing to spread to); against a raid, a dungeon room or
a mob farm it is the whole fight.

**Genuinely exclusive?** Yes. The two capstones are mutually unreachable at 5 points, and the
mid nodes do not want each other: Headhunter's multiplier does nothing for a chain that kills
through detonations, and Carrier does nothing to a lone boss with no neighbours. The only
hybrid a player can build is root + gate + gate + one mid, which owns neither capstone and is
strictly worse than either line — visible enough on the tree screen to be a choice, not a trap.

## 4. Node table

| Family | Display name | Ranks | Description |
| --- | --- | --- | --- |
| `DEATH_MARK` | Death Mark | 1 | Mark the creature you are looking at up to 32 blocks away for 60 seconds. Your dagger hits on the mark deal 25% more damage, and Shadow Step always jumps to the mark, from up to 32 blocks. Cooldown 45s, cleared when the mark dies. |
| `STALK` | Stalk | 1 | You see the mark outlined through walls at any distance. While you sneak, the mark cannot notice you beyond 8 blocks. |
| `HEADHUNTER` | Headhunter | 2 | Your dagger hits on the mark deal 25/50% more damage. |
| `COUP_DE_GRACE` | Coup de Grace | 1 | Shadow Step's strike kills the mark outright below 30% health. Marked players take x2 damage instead. |
| `CONTAGION` | Contagion | 1 | When the mark dies, the mark passes to the nearest creature within 16 blocks for the rest of its 60 seconds. |
| `CARRIER` | Carrier | 1 | Poison, Wither and Slowness on the mark spread every second to creatures within 8 blocks of it. |
| `VANISHING_ACT` | Vanishing Act | 1 | Killing the mark grants 4 seconds of Invisibility and Speed II. |
| `DEATHS_HEAD` | Death's Head | 1 | When the mark dies, every other creature within 8 blocks takes 5 hearts of damage. |

Where the numbers come from:

- **+25% / 25-50%.** A netherite dagger swings for `0.6 * (1 + SWORD_BASE + 4)` ≈ 4.8
  (`ModItems.daggerSwingDamage`, `DAGGER_MULTIPLIER = 0.6F`). A finished base Assassin already
  multiplies a Shadow Step strike by Razor Edge 1.24, Shadow Flurry 3.0 and Twin Fangs 1.5
  (two identical daggers) ≈ 26.8 damage. Mark 1.25 × Headhunter 1.5 = 1.875 takes that to ≈ 50,
  i.e. **25 hearts on a 30s cooldown against one named target**, next to Lightning Strike's 20
  hearts for 150 mana. That is the ceiling I am willing to hand a fully-built epic Hunt line;
  higher per-rank values push past the Oracle actives.
- **32 blocks / 16 blocks.** Double `Tuning.SHADOW_STEP_RANGE`, and the same 32 the Nemesis
  Shadow's Extra Sensory Perception already uses (`Tuning.ESP_RADIUS`), so the mark can never be
  outside the range you can already sense it at.
- **60s mark / 45s cooldown.** The mark outlives its own cooldown by 15s, so an assassin who
  keeps killing never sees downtime and one who whiffs eats the full wait.
- **30% execute.** `Tuning.EXECUTE_THRESHOLD` already exists for the Slayer's Executioner;
  reuse the same constant rather than inventing a second threshold. Players excluded (x2
  instead) for the same reason Executioner should be: a guaranteed PvP delete on a 45s clock is
  not a skill node.
- **5 hearts / 8 blocks.** Aura of Radiance's radius (`Tuning.RADIANCE_*`), and 10 damage is
  roughly one un-multiplied Shadow Flurry strike — a detonation worth chaining, not worth
  building around on its own.
- **4s Invisibility, Speed II.** The Shadow tree's Bloodrush already grants 4s of Strength on a
  kill; this is its mobility twin and reuses `ShadowTicker`'s effect handling wholesale.

## 5. Grid

Sketchpad format, top row first, bottom row is the root. 5 columns × 5 rows.

```
tree: nemesis-assassin

#...#
#...#
#...#
.#.#.
..#..
```

The silhouette is a broadhead / caret: one point at the foot (the mark itself) and two fangs
rising away from it, which reads as a dagger tree without repeating the base Assassin's
dagger.

Coordinates, `(col, row-from-bottom)`:

| Node | col | row |
| --- | --- | --- |
| `DEATH_MARK` | 2 | 0 |
| `STALK` | 1 | 1 |
| `CONTAGION` | 3 | 1 |
| `HEADHUNTER` 1 | 0 | 2 |
| `HEADHUNTER` 2 | 0 | 3 |
| `COUP_DE_GRACE` | 0 | 4 |
| `CARRIER` | 4 | 2 |
| `VANISHING_ACT` | 4 | 3 |
| `DEATHS_HEAD` | 4 | 4 |

**No explicit `withEdge` calls needed.** `Constellation.of` joins on 8-connectivity, so
(2,0)–(1,1), (2,0)–(3,1), (1,1)–(0,2) and (3,1)–(4,2) are all diagonal neighbours and connect
by themselves, and the two columns are plain vertical runs. (1,1) and (3,1) are two columns
apart, so the branches do **not** cross-connect — which is exactly the exclusivity the design
needs, and it comes from the grid rather than from a rule. Worth a unit check at build time all
the same: the tree must have exactly 9 nodes or `NemesisAssassinNodes.build()` throws, the same
guard every other node class carries.

## 6. Implementation notes

### Reused wholesale

- **`NemesisShadowNodes`** is the template for `NemesisAssassinNodes`: same `Family` enum with
  item-icon suppliers, same `cell(col,row)` map, same `rank(...)` pair, same drift guard.
  Suggested icons: `DEATH_MARK` → `Items.TARGET`, `STALK` → `Items.SPYGLASS`, `HEADHUNTER` →
  `ModItems.DIAMOND_DAGGER`, `COUP_DE_GRACE` → `ModItems.NETHERITE_DAGGER`, `CONTAGION` →
  `Items.SCULK_VEIN`, `CARRIER` → `Items.SPIDER_EYE`, `VANISHING_ACT` → `Items.ECHO_SHARD`,
  `DEATHS_HEAD` → `Items.SKELETON_SKULL`.
- **`SubTree`**: add `NEMESIS_ASSASSIN(Archetype.AGILITY, "nemesis_assassin", ..., true)`,
  plus `ASSASSIN -> NEMESIS_ASSASSIN` in `epicCounterpart()` and the inverse in
  `baseCounterpart()`. `SubTree.of` stays untouched (epic trees are never listed).
- **`TreeNodes`**: `kind()` (root `ACTIVE`, both capstones `CAPSTONE`), `pickerActives`,
  `familyOf`, `isMinor`. All of these switch exhaustively on `SubTree`, so the compiler will
  find every site.
- **`AgilityActives.shadowStep`**: one insertion before the raycast — if a live mark exists
  within 32 blocks, it *is* the victim; otherwise fall through to today's
  `ProjectileUtil.getEntityHitResult`. Everything after (the behind-the-back destination, the
  wall fallback, the cooldown-before-strike ordering that the Momentum bug fix demands) is
  unchanged.
- **`LivingEntityMixin#archetypes$daggerDamage`**: the mark multiplier and Coup de Grace belong
  inside this one `ModifyVariable`, after Razor Edge/Expose/Flense and next to the existing
  `STEP_STRIKE_AT` block that already implements Shadow Flurry, Twin Fangs and the night form's
  x1.5. Execute copies Executioner's idiom verbatim:
  `result = Math.max(result, victim.getHealth() + 100.0F)`.
- **`AgilityCombat`'s `ServerLivingEntityEvents.AFTER_DEATH`**: already the place where
  Momentum, Predator and `NightForm.onKill` hang. Add one `DeathMark.onKill(player, victim)`
  there for cooldown refund, Contagion's hop, Vanishing Act and Death's Head.
- **`ExtraSensoryPerception` + `EntityRendererMixin`**: Stalk's through-wall outline is the ESP
  renderer with a roster of one. Give the mark its own colour (a bone white or amber, clearly
  not ESP's red/violet) and let the same `outlineColor` / `senses` pair answer for both.
- **`CooldownBarHud`**: one more block alongside the Dark Ritual's, keyed on
  `ABILITY_KEYS[7]`, cooldown-only (no mana cost tile).
- **`ShadowTicker`/vanilla effects** for Vanishing Act; `damageSources().indirectMagic(player, player)`
  for Death's Head, the exact source `NightForm.tickBleeds` uses so a detonation kill still
  credits the assassin.

### Genuinely new

- **`DeathMark.java`**, modelled on `NightForm`: a small state machine over synced attachments,
  with static predicates as the only client-readable face. Needs three new `ModAttachments`
  entries — `MARK_TARGET` (`Integer` entity id, `targetOnly()` so the client can outline it),
  `MARK_END` (`Long`, `targetOnly()`), `DEATH_MARK_READY_AT` (`Long`, `targetOnly()`, the shape
  every other cooldown uses). Entity id, not UUID: the outline is a client-side id test and the
  server resolves the id through `level.getEntity(id)` each time it needs the body. The mark
  must clear when the target dies, leaves the level, or the timer lapses.
- **A per-tick mark pass**, cheapest as a few lines in `AgilityTicker` (which already walks the
  player list every tick for Lightfoot): expire the mark, and for Stalk, if the marked entity is
  a `Mob` whose `getTarget()` is the sneaking owner and the distance is over 8 blocks, call
  `setTarget(null)`. Doing it server-side this way avoids touching
  `LivingEntityMixin#archetypes$dimPresence` — that injection is a `ModifyReturnValue` on
  `getVisibilityPercent` that does not currently capture the `lookingEntity` argument, and
  I would rather not find out mid-build whether appending it works cleanly.
- **Carrier's spread**: once a second, read `POISON`, `WITHER` and `SLOWNESS` off the marked
  entity and copy each instance (same amplifier, remaining duration) onto every `LivingEntity`
  within 8 blocks except the owner. Pure effect copying — no damage, so no re-entrancy guard
  needed.
- **Contagion + Death's Head ordering**: detonate first, then hop the mark, and hold a
  `private static boolean detonating` guard around the detonation the way
  `NightForm.bleeding` and `RadianceAura.isPulsing` do — a detonation that kills a second mob
  re-enters `AFTER_DEATH`, and without the guard a dense pack could cascade.

### Effects and sound

- **Cast:** a short black-and-red particle burst at the target (`ParticleTypes.SCULK_CHARGE_POP`
  plus `SMOKE`) and a dry, close `SoundEvents.SCULK_SENSOR_CLICKING` or the low
  `WITHER_SHOOT` pitched down. Nothing grand — this is a name being written, not a spell.
- **Mark upkeep:** a slow drip of `ParticleTypes.SMOKE` above the mark's head, visible to
  everyone, plus the owner-only outline. A visible tell matters in PvP.
- **Coup de Grace:** the Executioner's own cue, sharp and short (`PLAYER_ATTACK_CRIT` pitched
  down + `ProcIndicators.send`), so the player learns which blow was the execute.
- **Death's Head:** a `SCULK_SHRIEKER_SHRIEK` at low pitch and a ring of `SOUL` particles at
  8 blocks. Loud on purpose — it is the crowd branch's payoff.
- **Vanishing Act:** reuse Invisibility's existing `CANDLE_EXTINGUISH` cue exactly, so the
  effect reads as the same thing the Shadow tree already taught.
- No new textures strictly needed (all icons are vanilla items or existing daggers), but the
  node art folder convention (`textures/node/<tree>/`) would want a set eventually.

### Expected to be awkward in 26.2

- **Retargeting Shadow Step.** Straightforward, but the destination maths reads the victim's
  `getYRot()` and does two `noCollision` probes; a 32-block jump onto a mark inside a build is
  much likelier to fall through to the "land on top of it" fallback than a 16-block one. Worth
  a third probe (step back along the approach vector) before shipping.
- **Outline colour per-source.** `EntityRenderState.NO_OUTLINE` and the single `outlineColor`
  hook are what the ESP path uses today; I have not verified that 26.2 lets two systems disagree
  about one entity's outline. Precedence rule to pick before building: the mark wins over ESP.
- **`level.getEntity(int)` on `ServerLevel`** is what I expect for id resolution, but I have not
  confirmed the exact 26.2 signature — the codebase never resolves an id server-side today (ESP
  only ever writes ids out). Verify before designing around it; a `UUID` + `getEntity(UUID)`
  fallback exists if not.
- **Effect instance copying** for Carrier: whether `MobEffectInstance` still exposes a copy
  constructor, or whether the amplifier/duration have to be read and a fresh instance built. The
  latter always works; check before assuming the former.
- **An eighth keybind** is a `KeyMapping` array resize and a lang key, but the cooldown bar
  lays out one hotbar-sized tile per owned active — an Agility player who owns Shadow Step,
  Invisibility, True Shot, the Dark Ritual and Death Mark now shows five tiles. Check the bar
  still fits beside the hotbar at 1x GUI scale.

## 7. Balance

**What 5 points buy over the finished base tree.** The base Assassin's peak is a ~26.8-damage
Shadow Step strike every 15s (30s with Flurry, reset on any kill via Momentum), with 100% armor
ignore and a 21% dodge. The Hunt line takes that single blow to ~50 damage against one named
target and adds a sub-30% delete — roughly a doubling of burst, but only against the one
creature you spent a 45s cooldown naming, and with nothing at all for the second enemy. The
Massacre line adds no dagger damage whatsoever; it converts kills into 5-heart pulses, a
travelling mark and a 4-second escape, so its ceiling is entirely a function of how many bodies
are standing next to each other.

**Where it could go wrong.**

- **Death's Head in a mob farm or a raid.** A 5-heart pulse that hops and re-detonates is a
  chain reaction with no cap. If a single mark can clear a raid wave unattended, the fix is a
  hop limit (the mark passes at most N times per cast) rather than a damage cut — the fantasy is
  the chain, so cap the chain, not the hit.
- **PvP.** Coup de Grace deliberately does not delete players, but x2 on an already-doubled
  Shadow Step strike is still a large opener, and Stalk plus the 32-block guaranteed teleport
  removes most of a target's warning. The visible smoke tell on the mark is load-bearing; if
  servers complain, the next lever is making the mark visible to *its own target* at all times.
- **Stacking with the Nemesis Shadow.** 15 epic points and a 5-point cap per tree means an
  Agility player can hold Death Mark *and* the night form. Stalker's Step's x1.5 multiplies
  with the mark's 1.25 and Headhunter's 1.5 in the same `ModifyVariable` — ~75 damage on one
  strike. That is the highest single number in the mod and it is reachable; it costs the player
  10 of their 15 epic points, an hour of undeath and daylight that sets them on fire, which I
  think earns it, but it is the first number to re-check after playtesting.
- **The mark as free tracking.** Stalk's any-distance through-wall outline is powerful outside
  combat too (finding a fleeing player, tracking a wandering trader). That is intended, but if
  it feels like a compass rather than a hunt, clamp the outline to the same 32 blocks the mark
  can be cast at.
- **Contagion's "nearest creature"** must exclude the owner's pets and, probably, passive
  animals — otherwise a chain through a cow field is a stronger farm than any fight. Recommend
  hostile-only for the hop, even though Carrier and Death's Head hit anything.
