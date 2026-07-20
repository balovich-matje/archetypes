# Colossus Protector — epic sub-tree design

Epic sibling of PROTECTOR (Strength, shield). Draft, design only — no code written.
Grounded in: `ProtectorNodes.java`, `ShieldBash.java`, `ShieldRush.java`, `ProtectorTicker.java`,
`Tuning.java` (lines 10–110), `mixin/LivingEntityMixin.java`, `mixin/BlocksAttacksMixin.java`,
`RadianceAura.java`, `NightForm.java`, `Archetypes.java` (slot dispatch), `ArchetypesClient.java`,
`SubTree.java`, `Constellations.java`, `TreeNodes.java`, and the four shipped epic sketches in
`tools/edits/`.

## 1. Pitch

The base Protector is safe and slightly stuck: the bash is deliberately ~0.55x a diamond sword's DPS
(`Tuning` header), Bulwark makes blocking airtight but blocking is idle time, and Taunt pulls a mob
pack you then have to survive with your feet. Nothing in the tree makes you *matter* to anyone else
on the server. Colossus finishes the fantasy the shield started: you stop being a person with a
shield and become a piece of terrain. **Aegis** plants you in the ground — you cannot walk, but you
are protected from every side without holding block, both hands free to bash, and everything with a
grudge within 12 blocks is now yours. A level-46 Protector wants it because it is the first button
in the tree that changes where the fight happens instead of how much of it hurts: you pick the
ground, and the fight comes to you. From there the branches decide what standing still is *for* —
sheltering other people, punishing whoever swings at you, or (the heretic branch) refusing to stand
still at all and walking the fortress forward.

## 2. The root — AEGIS

**Active.** Wants the next free epic key slot: **slot 7** (`ActiveAbilityPayload`, following
Lightning Strike 4, Magic Armaments 5, Dark Ritual 6). Strength's first epic key. Note that
`Archetypes.java` currently rejects `payload.slot() >= 7` and `ABILITY_KEYS` is length 7 — both need
one more entry, plus `key.archetypes.ability_8` in lang and a default keybind (`K` is vanilla-free
alongside the existing G/H/B/V/N/M/J).

**Cost.** Strength has no mana, so the cost is time and commitment, in the spirit of Dark Ritual's
hour: **15 seconds planted, 120 second cooldown that starts when Aegis ends** (~11% uptime). A
shield must be in hand or offhand (same `shieldHand` check `ShieldBash` already does). Pressing the
key again ends it early — you can bail, but the cooldown is the same, so bailing is pure loss.

**What it does while planted (15s):**
- You cannot move or jump. Movement is a transient `MOVEMENT_SPEED` modifier at −100%
  `ADD_MULTIPLIED_TOTAL` — exactly the shape `AgilityTicker` already uses for stance.
- Incoming damage −50%, from every direction, without holding block and without spending shield
  durability. This is its own mitigation on the `hurtServer` intake, not vanilla blocking (see
  Implementation notes) — so it stacks multiplicatively with armour and does not care about facing.
- Knockback immunity: `KNOCKBACK_RESISTANCE` +1.0 transient modifier, the same modifier `RadianceAura`
  applies for Steadfast.
- Every monster within 12 blocks targets you — the same `mob.setTarget(player)` sweep `ShieldBash`
  does for Taunt, at 12 blocks instead of `Tuning.TAUNT_RADIUS` 8, because a rooted player needs the
  fight brought to them.
- Shield Bash still works normally while planted. That is the point: the base tree's blocking
  trade-off ("safe but idle") is dissolved, not buffed.

## 3. The branches

Three columns off the root; each is 3 nodes, so a **5-point build is Aegis + one full branch (4
points) + one dipped node from a neighbour**. That fifth point is deliberately a flavour pick, and
the dips are all first-rung nodes (Shelter 1, Unstoppable, Backlash 1) — the grid chain enforces
buy order, so no one reaches a capstone off a dip.

### Left — THE WALL (protective / group play, adds zero damage)
`SHELTER` (2 ranks) → `IMMOVABLE`. Five points: Aegis + Shelter I + Shelter II + Immovable + one dip.
You are cover. Everyone standing near you takes half damage; nothing that hits you hits hard. This
is the multiplayer branch and the boss branch (Wither, raid, Warden): it does not shorten the fight,
it makes the fight survivable. Solo it still reads — Shelter covers wolves, iron golems, villagers
and horses, which is a real base-defence use, but it is honestly the weakest of the three alone.

### Centre — THE JUGGERNAUT (mobility; buys back the root's restriction)
`UNSTOPPABLE` → `TRAMPLE` → `BREACH`. Five points: Aegis + all three + one dip. This branch's first
node deletes the "cannot move" clause and pays for it by halving Aegis's damage reduction (50% →
25%). You are no longer a wall; you are a slow siege engine, walking into a mob pack, flattening
whatever you touch and charging through the rest. Genuinely exclusive with the Wall: The Wall's
whole value is the 50% floor and a fixed footprint, and Unstoppable trades exactly that away. Taking
both is legal and bad — you spend two points to halve your own mitigation and then buy allied
mitigation for allies who now have to chase you.

### Right — THE ANVIL (retaliation damage)
`BACKLASH` (2 ranks) → `REBUKE`. Five points: Aegis + Backlash I + Backlash II + Rebuke + one dip.
The pure-selfish branch: every melee attacker takes real damage back, and when the Aegis drops it
detonates for half of everything you soaked. Differs from The Wall in that it *wants* to be hit and
wants the damage taken to be high — so it actively dislikes Shelter's mitigation on itself, and
dislikes Unstoppable because moving means fewer attackers in the ring when Rebuke goes off. The
three branches point at three different questions: who else is standing here, do I need to be
elsewhere, and how many of them are swinging at me.

## 4. Node table

Family (SCREAMING_SNAKE) → display name → ranks → finished description (Cutpurse style, ships to
`en_us.json` as `node.archetypes.colossus_protector.<family>[.desc]`).

| Family | Display | Ranks | Description |
| --- | --- | --- | --- |
| `AEGIS` | Aegis | 1 | Plant your shield: for 15 seconds you cannot move, take 50% less damage from every side without blocking, and cannot be knocked back. Every monster within 12 blocks attacks you. Shield Bash still works. Press again to end it early. Cooldown 120s. |
| `SHELTER` | Shelter | 2 | Allies within 8 blocks of your Aegis take 25/50% less damage. |
| `IMMOVABLE` | Immovable Object | 1 | While planted, no single hit deals more than 3 hearts, and nothing can push or pull you. |
| `UNSTOPPABLE` | Unstoppable Force | 1 | You can walk while planted, at half speed. Aegis reduces damage by 25% instead of 50%. |
| `TRAMPLE` | Trample | 1 | While planted, walking into a monster deals 4 hearts and throws it aside. Once per target every 2s. |
| `BREACH` | Breach | 1 | While planted, press sprint to charge 8 blocks forward. Everything in your path takes 5 hearts and is thrown aside. Cooldown 4s. |
| `BACKLASH` | Backlash | 2 | While planted, melee attackers take 3/6 hearts back and are shoved away. |
| `REBUKE` | Rebuke | 1 | When Aegis ends, everything within 8 blocks takes half the damage you took while planted, up to 15 hearts. |

**Where the numbers come from.**
- *50% reduction, 15s, 120s cooldown.* Uptime 11%, close to Invisibility's 8s/30s (27%) but on a
  much stronger effect, and unlike Invisibility it cannot be moved out of danger. 50% is multiplied
  on top of armour, so in full diamond it is a halving of an already small number — strong, not
  absolute, and it never reaches immunity.
- *12 block taunt.* `Tuning.TAUNT_RADIUS` is 8 for the base node; the epic version has to be bigger
  than the base one and has to out-range the distance a rooted player can no longer walk.
- *Shelter 25/50%.* Same ladder Ghost Form uses (25/50/75%) truncated at two ranks, and 8 blocks is
  `RadianceAura`'s radius, so the two auras read at the same size on screen.
- *Immovable's 3-heart cap.* Applied after the 50% cut. A close creeper (~43 raw) or a Warden slam
  becomes 6 damage; a zombie was already under the cap, so the node does nothing against trash and
  everything against burst — which is exactly the promise.
- *Backlash 3/6 hearts.* Iron Spikes rank 3 is Thorns XV = 6–9 damage (3–4.5 hearts) and needs a
  blocked hit; 6 hearts guaranteed on any melee hit is a clear epic step up, for 15 seconds every
  two minutes.
- *Trample/Breach 4 and 5 hearts.* The bash is 5.0 damage (2.5 hearts) baseline; these are ~1.6x and
  2x a bash but are gated on contact and on a branch that gave up half the mitigation.
- *Rebuke cap 15 hearts.* Fifteen seconds at 50% reduction against a serious pack is roughly 30–60
  raw damage soaked; half of that is 15–30, so the cap bites in the worst case and keeps the node
  from scaling with how badly you were losing.

## 5. Grid

Sketchpad format, drawn top-down; bottom row is the root. 5 columns × 5 rows, 10 nodes.

```
tree: colossus-protector
name: colossus-protector-first

#.#.#
#.#.#
#.#.#
.....
..#..
```

Coordinates (col, row from bottom):

| Node | col | row |
| --- | --- | --- |
| `AEGIS` 1 | 2 | 0 |
| `SHELTER` 1 | 0 | 2 |
| `SHELTER` 2 | 0 | 3 |
| `IMMOVABLE` 1 | 0 | 4 |
| `UNSTOPPABLE` 1 | 2 | 2 |
| `TRAMPLE` 1 | 2 | 3 |
| `BREACH` 1 | 2 | 4 |
| `BACKLASH` 1 | 4 | 2 |
| `BACKLASH` 2 | 4 | 3 |
| `REBUKE` 1 | 4 | 4 |

Row 1 is deliberately empty so the root reads as a separate foot, exactly as Oracle Elementalist and
Nemesis Shadow do. Three explicit edges bridge the gap:

```
LINE + AEGIS 1 <-> SHELTER 1 (connect)        // withEdge(2, 0, 0, 2)
LINE + AEGIS 1 <-> UNSTOPPABLE 1 (connect)    // withEdge(2, 0, 2, 2)
LINE + AEGIS 1 <-> BACKLASH 1 (connect)       // withEdge(2, 0, 4, 2)
```

Everything else is grid-adjacent within its column. No exclusive pair — `TreeNodes.exclusive` needs
no new arm; the point cap does the choosing.

## 6. Implementation notes

**Files that get a new arm, no new pattern:**
`SubTree` (`COLOSSUS_PROTECTOR(Archetype.STRENGTH, "colossus_protector", …, epic=true)` plus both
`epicCounterpart`/`baseCounterpart` cases), `Constellations.COLOSSUS_PROTECTOR` (grid above +
three `withEdge`), a new `ColossusProtectorNodes.java` copied structurally from
`NemesisShadowNodes.java` (Family enum with icon suppliers, `cell()` map, the size-mismatch guard),
`TreeNodes` (`isMinor`, `rankOf`, `kind` — `AEGIS` is `NodeKind.ACTIVE`; `IMMOVABLE`, `BREACH` and
`REBUKE` are branch payoffs, mark them `CAPSTONE` if the author wants three purple rings, otherwise
`NORMAL`), lang, and `CooldownBarHud` (one more `Ability` entry keyed on `AEGIS`, `AEGIS_READY_AT`,
2400 ticks).

**New server class `ColossusAegis.java`** (model it on `RadianceAura` + `NightForm`): `begin`,
`end`, and a `ServerTickEvents.END_SERVER_TICK` pass over Strength players only. State lives in new
attachments alongside the Protector ones in `ModAttachments`:
- `AEGIS_END` (Long, transient) — like `RADIANCE_END`.
- `AEGIS_READY_AT` (Long) — like `BASH_READY_AT`.
- `AEGIS_ABSORBED` (Float, transient) — Rebuke's running total.
- `AEGIS_ACTIVE` (Boolean, **synced**) — the client render/HUD flag, same trick as `BULWARK_ACTIVE`,
  which `ProtectorTicker` already sets/clears only on change.
- `BREACH_READY_AT` (Long) and a small per-target cooldown map for Trample (a `Long` "next trample"
  attachment on the *victim* is simplest; entity attachments already work here).

**Reused systems, named:**
- Mitigation, the 50%/25% cut and Immovable's cap: one `@ModifyVariable(method = "hurtServer",
  at = @At("HEAD"), argsOnly = true)` in `LivingEntityMixin`, the same hook shape as Mana Shield and
  Sunder. Accumulate the pre-cut amount into `AEGIS_ABSORBED` in the same hook for Rebuke.
- Knockback immunity: the transient `KNOCKBACK_RESISTANCE` modifier from `RadianceAura.steadfast`.
  For Immovable's "nothing can push or pull you", the `ModifyVariable` on
  `knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V` that Incorporeal already uses is
  the exact precedent.
- The root: transient `MOVEMENT_SPEED` −100% `ADD_MULTIPLIED_TOTAL`, per `AgilityTicker.stance`.
- Taunt sweep, Ground Slam's ring/debris particle helper, and the shield-in-hand check: all lifted
  straight out of `ShieldBash` (`slamDebris` is already parameterised by radius).
- Breach's impulse: `ShieldRush.execute`'s `setDeltaMovement` + `hurtMarked` + `Tuning.RUSH_IMPULSE_PER_BLOCK`.
  Note `ShieldRush` early-returns unless `player.isBlocking()`, and a planted player is *not*
  blocking, so the two sprint-key handlers cannot both fire — but the client sprint dispatch in
  `ArchetypesClient` (which already juggles Shield Rush, night dash and Disengage on the same key)
  needs one more branch, and Breach must win over Shield Rush while `AEGIS_ACTIVE` is set.

**Genuinely new work:**
1. Jump suppression. −100% movement speed does not stop a jump. `Attributes.JUMP_STRENGTH` exists in
   modern versions but is **not used anywhere in this repo** — I could not verify its 26.2 name or
   whether a −100% modifier fully zeroes a player jump (players read jump strength differently from
   mobs). Fallback if it misbehaves: cancel the jump server-side, or accept a hop as harmless.
2. Client-side prediction of the root. A speed modifier syncs, so the client should agree, but the
   author should expect one round of rubber-band tuning; `hurtMarked` is the existing escape hatch.
3. Trample's per-target throttle — small, but it is new bookkeeping.
4. Rebuke's accumulate-and-detonate — new, though the detonation itself is `ShieldBash`'s ring code.
5. Shelter has to pick friends: `RadianceAura` already answers "friendly creature" for the Priest
   aura; reuse that predicate rather than inventing a second definition, and include other players.

**Effects:**
- *Cast:* `SoundEvents.MACE_SMASH_GROUND` (already used by Ground Slam) layered under
  `SoundEvents.ANVIL_LAND` pitched down; `slamDebris` at radius 2 for the plant.
- *While planted:* a low ring of block-coloured dust at the player's feet marking the footprint
  (`DustParticleOptions` with the ground's map colour, exactly `slamDebris`'s edge ring), plus a
  client render layer — `BulwarkShieldLayer` is the model: a stone/iron slab silhouette standing in
  front of the player, driven off the synced `AEGIS_ACTIVE`.
- *Shelter:* a second, wider translucent ring so allies can see where cover ends.
- *Backlash:* `SoundEvents.THORNS_HIT` + `ParticleTypes.ELECTRIC_SPARK`, matching Iron Spikes and Braced.
- *Rebuke:* one hard `SoundEvents.GENERIC_EXPLODE` with the shockwave clouds from `slamDebris`.
- *Trample/Breach:* `SoundEvents.WIND_CHARGE_THROW` (Shield Rush's sound) plus block debris.
- `ProcIndicators.send(...)` on Backlash and Trample, like Spikes/Braced/Reflection.

**Expected 26.2 friction:** the jump attribute above is the one real unknown. Everything else in this
design deliberately avoids the two hard parts of 26.2's blocking — it does not try to fake
`isBlocking()` or to synthesise a `blocks_attacks` use, because `blockedByItem` and
`applyItemBlocking` only fire on a real item-use, and forcing `startUsingItem` server-side is exactly
the kind of client-disagreement bug the author already hit once in `blockedByItem` (see its comment).
Aegis therefore carries its own mitigation and its own retaliation, and the base tree's block-gated
nodes (Iron Spikes, Braced, Reflection, Reinforced Straps) simply do not apply while planted. That is
a deliberate design line, not an oversight — say so in the tooltip if playtesting surfaces confusion.

## 7. Balance

At five points the tree adds, over the base Protector: an 11%-uptime window of halved damage with a
12-block forced-aggro pull, and then one of — half damage for everyone standing with you plus a
3-heart cap on any hit; or a mobile 25% version with two contact-damage tools; or ~6 hearts of
certain retaliation per melee hit plus up to a 15-heart detonation. The base tree already gives
Bulwark (all-direction blocking, but only while blocking and only with a shield's durability paying
for it) and Thorns XV on blocked hits. Aegis is strictly a window, not a permanent state, and the
base tree's mitigation stays untouched outside that window, so nothing here inflates the Protector's
baseline.

Where it could go wrong:
- **Immovable + Shelter is the raid-trivialiser.** Half damage and a 3-heart cap for a whole party
  for 15 seconds is the strongest defensive thing in the mod. If the cap proves too flat, raise it
  to 4 hearts before shortening the duration — the duration is what makes the branch feel like a
  wall.
- **Backlash farms trash.** Six hearts back on every melee hit deletes zombie packs without input.
  It is fenced by the 15s window, but if it reads as an AFK button, add "up to 5 attackers per
  second" the way Rebuke is capped.
- **The 12-block taunt is a griefing surface in multiplayer**: pull a pack and then have Unstoppable
  walk it into someone else's base. Consider dropping the aggro when Aegis ends, or keeping the pull
  as a one-shot at cast rather than continuous.
- **Solo Shelter is thin.** If it stays weak in playtest, the honest fix is to let Shelter also
  cover tamed animals and villagers explicitly in the description rather than to buff the number.
- **Rebuke rewards being bad at the game** — the worse the pack beats on you, the bigger the boom.
  The 15-heart cap is the whole defence against that; do not remove it.
