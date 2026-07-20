# Decimate / armour rework — proposal

> **Status: DRAFT, not implemented.** Every number below is a starting point for the author to
> sketch over. Three independent verification passes recomputed the fights under this proposal
> and all three reported design targets MISSED — their objections are in §5 and several are
> serious. Do not implement any of this as-is.

Design goal, the author's words: *"Decimate should decimate. You don't want to be near it when
it's cast, end of story."* And each archetype survives for one specific reason — Nemesis because
the Slayer cannot see it, Oracle by blinking or by Mana Shield eating one hit, Colossus by
parrying or by the unarmed Crusher's stacked armour and temporary HP.

## 1. Summary

Decimate stops being "a swing with a x2 sticker" and becomes a telegraphed, percent-of-max-health hit on its own damage type that bypasses armour, Protection and shields — 36 into a 40 HP netherite player, leaving an unprepared target at 4 HP, with a 12-tick wind-up so the answers are reactions rather than dice. The greatsword's sustained problem is fixed with a real vanilla-shaped enchantment (`archetypes:sundering`, armor_effectiveness -0.15/level on `#archetypes:greatswords`, exclusive with Sharpness), not with another multiplier — I reject widening `#minecraft:enchantable/mace` (Wind Burst leaks) and reject stamping virtual Breach onto Decimate (redundant once it bypasses). Ironclad is re-shaped so it stops pinning realArmor at vanilla's 20 cap, which is the actual reason big hits and small hits currently land identically and the reason Iron Skin and Hardened are worth literally zero. Flense and Sunder are both re-derived off the real `armorFraction` instead of the fake `ARMOR x 0.04` linear stand-in, turning Flense from a x5.0 switch into a two-Breach-level curve, and the Cutpurse's ambush multipliers are collapsed into one additive box so the 377 opener lands at ~29 with a follow-up finishing an unaware target. Each archetype then survives for exactly one reason: Nemesis because Decimate now gates its victim list on `isInvisibleTo`/line-of-sight, Oracle because the wind-up is longer than a blink and because Mana Shield finally sees the conjured sword, Colossus because Parry is unlocked for shields and fists and because Bulwark's absorption bank eats the hit.

## 2. Constants

| Constant | File | Current | Proposed | Why |
|---|---|---|---|---|
| `DECIMATE_DAMAGE_MULTIPLIER` | src/main/java/com/archetypes/Tuning.java:152 | 2.0F (damage = ATTACK_DAMAGE x 2.0, then the whole on-hit multiplier chain rides it) | **DELETE** | Scaling off ATTACK_DAMAGE couples the capstone to Sharpness, Heavy Blows, First Blood and Combat 100, so it can never be tuned independently and it is still only ~31 raw. Replaced by the three constants below. |
| `DECIMATE_MAX_HEALTH_FRACTION` | src/main/java/com/archetypes/Tuning.java (new, beside :152) | — | **0.90F** | damage = clamp(victim.getMaxHealth() * 0.90F, MIN, MAX). 36 into a 40 HP player. On a bypassing damage type that is 36 actual, leaving 4 HP — target 1, 'close enough that they must respect it', with death for anyone below 40 max HP. |
| `DECIMATE_MIN_DAMAGE` | src/main/java/com/archetypes/Tuning.java (new) | — | **24.0F** | Floor so the capstone still one-shots a 20 HP zombie rather than dealing 18. Percent-of-max-health is self-defeating against small pools; the floor is what stops it. |
| `DECIMATE_MAX_DAMAGE` | src/main/java/com/archetypes/Tuning.java (new) | — | **40.0F** | Ceiling so 90%-of-max-HP does not delete a 500 HP Warden. This cap is the only thing keeping a PvP-tuned number from becoming a boss-deleter, and it is the single most important guard against 'nothing becomes the new outlier'. |
| `DECIMATE_WINDUP_TICKS` | src/main/java/com/archetypes/Tuning.java (new) | — (SlayerActives.java:73-99 sets DECIMATE_SWING_AT, plays both sounds and calls hurtServer in the same tick) | **12** | 0.6s telegraph. Without it PARRY_WINDOW_TICKS = 8 is a pre-emptive coin flip covering 4.8% of the cooldown, the Oracle cannot blink in response, and 'you don't want to be NEAR it when it's cast' has no reaction to be near. This is the change that converts all three survival routes from luck into input. |
| `DECIMATE_COOLDOWN_TICKS` | src/main/java/com/archetypes/Tuning.java:153 | 600 | **600 (unchanged)** | Correction to the brief, which said 300: it is 600. 30s is the right price for a hit that leaves a full-netherite player at 10%. RELENTLESS_REDUCTION_TICKS still applies. |
| `PARRY_WINDOW_TICKS` | src/main/java/com/archetypes/Tuning.java:1037 | 8 | **10** | Must be comfortably inside DECIMATE_WINDUP_TICKS = 12 so a parry pressed on the telegraph lands. 10 of 12 is a reaction; 8 of 12 is a reaction with no slack for latency. |
| `PARRY_COOLDOWN_TICKS` | src/main/java/com/archetypes/Tuning.java:1044 | 160 | **200** | Parry now fully cancels the biggest hit in the game and is free on success (ColossusSlayer.java:401-402). 10s on a whiff keeps mashing unprofitable against a 30s cooldown. |
| `IRONCLAD_ARMOUR_BONUS` | src/main/java/com/archetypes/Tuning.java:987 | 0.50, applied as ADD_MULTIPLIED_TOTAL to BOTH ARMOR and ARMOR_TOUGHNESS (ColossusProtector.java:91-113) | **0.20, applied to ARMOR_TOUGHNESS ONLY** | At 0.50 on ARMOR, netherite gives 30.0 = exactly the RangedAttribute cap (vsrc Attributes.java:13), and CombatRules clamps realArmor to MAX_ARMOR = 20 for any hit under 65 raw. The mitigation curve is FLAT: a zombie punch and a Decimate are both cut 92.8%. That is the root cause of 'Decimate cannot decimate', and it is also what clamps Iron Skin and Hardened to zero. At 0.20 on toughness only: ARMOR 22 (with the flat below), TOUGHNESS 14.4, t = 5.6, realArmor = clamp(22 - d/5.6, 4.4, 20) — pinned only below 11 raw, so damage scaling returns. |
| `IRONCLAD_ARMOUR_FLAT` | src/main/java/com/archetypes/Tuning.java (new, beside :987) | — | **2.0 (ADD_VALUE on ARMOR)** | Keeps Ironclad feeling like armour without touching the 30 cap, and leaves 8 points of ARMOR headroom so Iron Skin x3 (+3) and Hardened plates (+2/hit) stop being clamped out of existence. Five previously-inert skill points become live. |
| `INSTINCTIVE_GUARD_PER_RANK` | src/main/java/com/archetypes/Tuning.java:1023 | 0.25F (rank 2 = -50% of every hit, pre-armour, no input, no cooldown, no facing check, no block-delay) | **0.15F (rank 2 = -30%)** | A flat 50% for carrying a shield you never raise is the anti-skill gradient the whole rework is fighting. Paired with two mechanic fixes below (real facing angle, blockDelayTicks) it becomes a passive worth two points rather than a passive worth more than the button it replaces. |
| `COLOSSUS_BULWARK_DR_PER_RANK` | src/main/java/com/archetypes/Tuning.java:930 | 0.20F (rank 2 = x0.60) | **0.15F (rank 2 = x0.70)** | This is the Colossus's designated Decimate answer, so it must survive — but at x0.60 plus a gate that any golden apple satisfies it is free. x0.70 plus the corrected gate (below) means 36 raw lands as 25.2 and eats his whole 18-point absorption bank. |
| `STRAPS_SKIP_CHANCE` | src/main/java/com/archetypes/Tuning.java:109 | 0.5F | **0.25F** | Tuning.java:1018-1021 claims Instinctive Guard is fenced by shield durability (30/proc on 336 = 11 blocks). Reinforced Straps, a one-point base node in the same archetype, doubles that to 22 and cancels the fence. 0.25 leaves the node worth taking without erasing the only cost. |
| `GHOST_FORM_NEGATE_PER_RANK` | src/main/java/com/archetypes/Tuning.java:702 | 0.25F (3 ranks = 75% to void ANY hit) | **0.15F (3 ranks = 45%)** | The Nemesis must survive by not being seen, not by a 75% coin flip that voids everything from Decimate to fall damage. 45% is a real epic payoff; 75% makes the invisibility gate cosmetic. |
| `FLENSE_PER_RANK` | src/main/java/com/archetypes/Tuning.java:327 | 0.5F — 'fraction of armor's absorption clawed back per rank; rank 2 = all', computed against a FAKE absorption of min(0.8, ARMOR x 0.04) (LivingEntityMixin.java:296-306, mirrored DamageTrace.java:438) | **0.12F, semantics changed to 'percentage points off the REAL armorFraction per rank' — identical units to Breach** | Current rank 2 is a flat x5.0 switch that reaches full value at 20 armour (plain diamond) and rewards attacking heavy armour more than light. At 0.12 x 2 = 0.24pp it is worth ~2.2x against a capped Colossus and ~1.2x against a lightly-armoured target — a curve, and one denominated in the same unit as the enchantment it imitates. |
| `SUNDER_PER_LEVEL` | src/main/java/com/archetypes/Tuning.java:168 | 0.15F, but applied as result += result x absorbed x 0.15 x levels (LivingEntityMixin.java:654-661) | **0.15F unchanged — the FORMULA is wrong, not the number** | Real Breach multiplies final damage by 1 + 0.15N/(1-A); Sunder multiplies raw by 1 + A x 0.15N. The ratio is 1/(A(1-A)), never below 4, and exactly 6.25x too weak against a Colossus. Sunder is currently the most underpowered node in the Crusher tree and nobody noticed because it is expressed in different units from everything it is compared to. |
| `SHADOW_FLURRY_MULTIPLIER` | src/main/java/com/archetypes/Tuning.java:311 | 3.0F (multiplicative) | **rename SHADOW_FLURRY_BONUS = 1.0F, additive into one ambush box** | The 377 opener is 3.0 x 1.5 x 1.25 x 1.5 = 8.44x of stacked multipliers. Collapsing them into 1 + sum() is the structural fix; the individual numbers barely change. |
| `NIGHT_FORM_SHADOW_STEP_FACTOR` | src/main/java/com/archetypes/Tuning.java:690 | 1.5F (multiplicative) | **rename NIGHT_FORM_SHADOW_STEP_BONUS = 0.35F, additive** | Same box. |
| `DEATH_MARK_DAMAGE_FACTOR / HEADHUNTER_PER_RANK` | src/main/java/com/archetypes/Tuning.java:818-819 | 0.25F and 0.25F, applied as two separate multiplications (x1.25 x x1.50 = x1.875) | **values unchanged, moved into the same additive box (+0.25 and +0.50)** | Same box. Sustained marked damage barely moves (1.75 vs 1.875, x0.93); only the opener collapses. |
| `TWIN_FANGS_OFFHAND_FACTOR` | src/main/java/com/archetypes/Tuning.java:329 | 0.5F (x1.5 on the step strike) | **0.30F (x1.30)** | Last 11% of the opener cut. Total opener chain goes 8.44 x 1.5 = 12.66 down to 3.10 x 1.30 = 4.03, a x3.14 reduction: 249 raw becomes ~79 raw. |
| `RAZOR_EDGE_PER_RANK` | src/main/java/com/archetypes/Tuning.java:325 | 0.08F | **0.12F** | Budget moves from the opener to sustained. The Cutpurse currently has no sustained damage at all — everything is in one click on a 300-tick cooldown. See the risks section: this alone does not get the follow-up to 16 and the dagger's base ATTACK_DAMAGE has to come up too. |
| `MANA_SHIELD_MANA_PER_DAMAGE` | src/main/java/com/archetypes/Tuning.java:445 | 2.0F (500 mana pool absorbs 250 raw, ~1.9 full Decimates at the old numbers) | **3.0F** | The author wants Mana Shield to buy 'just one hit'. At 3.0 a 36-raw Decimate costs 54 of a 500 pool for 18 absorbed — cheap per hit, but paired with the widened gate below it now has to fund the Spellsword's whole channel too, so it is a real budget rather than a free pool. |
| `MAGIC_ARMOR_CAP_PER_RANK` | src/main/java/com/archetypes/Tuning.java:566 | 10.0F (rank 2 = 20 absorption, refilled from empty in 2 seconds at 10 mana/s) | **6.0F (rank 2 = 12)** | A 20-point buffer that refills faster than Decimate's cooldown means the Spellsword survives by standing still, not by blinking. At 12 he survives the hit at 16/52 and is in serious trouble — mobility stays the real answer. |
| `EXECUTE_THRESHOLD` | src/main/java/com/archetypes/Tuning.java:143 | 0.15F | **0.15F (unchanged; the mechanic changes — see Mechanics)** | The threshold is fine. The bug is that Executioner clamps to health + 100 RAW and that raw number is then mitigated, so it delivers 2.29 to a Colossus on 6 HP. A finisher that does not finish. |
| `ARMOR_TOUGHNESS reference value` | src/main/java/com/archetypes/Tuning.java:982 | brief said 20 (capped) | **18 — correction** | Netherite is 3.0/piece (vsrc ArmorMaterials.java:33-34) = 12; x1.5 Ironclad = 18; the attribute cap is 20 (Attributes.java:14). Ironclad never reached the toughness cap. Every downstream number in the brief (x0.072, x0.0216) still checks out. |

## 3. Mechanics

DECIMATE — RECOMMENDED, and this is the load-bearing change.

New damage type `archetypes:decimate` (the mod has no custom damage types yet; only tags over vanilla ones at data/archetypes/tags/damage_type/magical.json, so this is new but standard datapack work). Put it in three vanilla tags:
- `minecraft:bypasses_armor` — LivingEntity.getDamageAfterArmorAbsorb (vsrc LivingEntity.java:1895) skips the entire armour stage. Kills armour, kills the MAX_ARMOR = 20 clamp, kills all future gear inflation.
- `minecraft:bypasses_enchantments` — getDamageAfterMagicAbsorb (:1930) returns early. Kills Protection IV's x0.36.
- `minecraft:bypasses_shield` — this is the elegant part. Instinctive Guard's own gate already tests `!source.is(DamageTypeTags.BYPASSES_SHIELD)` (ColossusProtector.java:206-247), so a raised shield, Omni-block, and the free 30% passive all drop out with no new code. Parry does NOT drop out: ColossusSlayer.tryParry (:326-354) classifies on `direct instanceof LivingEntity && direct == source.getEntity()`, which is true for playerAttack(player), and the mixin does `cir.setReturnValue(false)` (LivingEntityMixin.java:1056-1064) before any of that. The tag is therefore a precise discriminator: it deletes the zero-input defences and keeps the timed one.
Do NOT put it in `archetypes:magical` — Barbarian x2 (x0.25) would eat it, and Barbarian is zero-input.

SlayerActives.decimate (SlayerActives.java:43-99), three edits:
1. Split into cast and resolve. On cast: set DECIMATE_SWING_AT, play PLAYER_ATTACK_SWEEP + MACE_SMASH_AIR, write the cooldown, and schedule resolve for now + DECIMATE_WINDUP_TICKS. Recompute the victim AABB at resolve, not at cast, so stepping out of the arc works.
2. Damage becomes `Mth.clamp(victim.getMaxHealth() * DECIMATE_MAX_HEALTH_FRACTION, DECIMATE_MIN_DAMAGE, DECIMATE_MAX_DAMAGE)` — per victim, since it reads their max health.
3. Drop the `MeleeSwing.begin/end` wrapper. Heavy Blows, First Blood, Combat 100, Sharpness, Rend and Expose must NOT ride Decimate. It reads identically to every victim, which is exactly what 'you don't want to be near it, end of story' means, and it is the only way the number stays tunable. Keep the try/finally shape for the scheduled resolve.
4. Victim filter gains `player.hasLineOfSight(victim) && !victim.isInvisibleTo(player)` alongside the existing inFront dot-product test (SlayerActives.java:82-85, 185-194). This single predicate is what makes the Nemesis's stated reason the real reason.

BREACH ON GREATSWORDS — RECOMMENDED as a mod enchantment; REJECTING the other two routes.
Register `archetypes:sundering`: `armor_effectiveness` with `AddValue(LevelBasedValue.perLevel(-0.15F))`, `supported_items: "#archetypes:greatswords"`, `exclusive_set: "#minecraft:exclusive_set/damage"`, max_level 4.
- REJECT widening `#minecraft:enchantable/mace`: it is a leaf tag with one item (VanillaItemTagsProvider.java:374) and its only consumers are Density, Breach and Wind Burst (Enchantments.java:984/995/1006). Density is inert on a greatsword, but Wind Burst is live and item-agnostic — it runs through EnchantmentHelper.doPostAttackEffectsWithItemSource from Player.java:1093 with whatever stack attacked. A Wind Burst greatsword gusting on every hit with fall_distance >= 1.5 is a real behavioural leak for no benefit.
- REJECT overriding `data/minecraft/enchantment/breach.json`: registry entries replace wholesale, so any other datapack touching Breach silently wins or loses.
- Own it: Sundering IV is mutually exclusive with Sharpness V (both in `#minecraft:exclusive_set/damage`, VanillaEnchantmentTagsProvider.java:67-68, enforced at AnvilMenu.java:189). That is a genuine build choice — anti-armour or anti-flesh — and it is the honest answer to 'change the allowed enchants'.

DECIMATE AS A BREACH CARRIER — REJECTED. Stamping virtual Breach via the MagicArmaments.java:430-444 pattern works and needs no plumbing (DamageSource.getWeaponItem already carries the mainhand into CombatRules), but armour penetration's hard ceiling against this target is x5.0 and Protection's x0.36 is untouchable by it. Full Breach bypass gets Decimate to 3.37 damage. It is the wrong instrument; the damage-type bypass reaches the same place and further, with less code.

EXECUTIONER — REFORMULATE. Current: clamp raw to victim.getHealth() + 100, then mitigate, which delivers 2.29 to a Colossus on 6 HP. New: after the normal hit resolves, if `victim.getHealth() <= victim.getMaxHealth() * EXECUTE_THRESHOLD`, apply a second hurtServer on a new `archetypes:execute` type (same three bypass tags) for `victim.getHealth() + victim.getAbsorptionAmount() + 1.0F`. Ghost Form's negate roll and Parry still cancel it; Instinctive Guard, Bulwark and Mana Shield do not. A finisher either finishes or is not a finisher.

FLENSE — REFORMULATE, at LivingEntityMixin.java:296-306 and the mirror at DamageTrace.java:438 (they must move in lockstep).
Replace `absorbed = min(0.8F, ARMOR * 0.04F)` — which ignores the `- damage/toughness` shred term entirely and saturates at 20 armour — with the real thing:
  float t = 2.0F + victim.getAttributeValue(ARMOR_TOUGHNESS) / 4.0F;
  float armor = (float) victim.getAttributeValue(ARMOR);
  float A = Mth.clamp(armor - raw / t, armor * 0.2F, 20.0F) / 25.0F;
  float ignored = Math.min(A, Tuning.FLENSE_PER_RANK * flense);
  result = result * (1.0F - A + ignored) / (1.0F - A);
This is exactly Breach's arithmetic, in Breach's units. Apply the same shape to Sunder (LivingEntityMixin.java:654-661, DamageTrace.java:573-577): `result *= 1 + 0.15F * levels / (1 - A)`, where levels = rank x (HANDS ? 2 : 1) and A is the same real fraction — that is the 6.25x correction, and it is a straight buff to the Crusher.

CUTPURSE OPENER — one additive box. In the Shadow Step path, replace the chain of multiplications with:
  float ambush = 1.0F + SHADOW_FLURRY_BONUS + (nightForm ? NIGHT_FORM_SHADOW_STEP_BONUS : 0) + (marked ? DEATH_MARK_DAMAGE_FACTOR + HEADHUNTER_PER_RANK * rank : 0);
Opener chain goes from 8.44 x 1.5 (twin fangs) = 12.66 to 3.10 x 1.30 = 4.03. Sustained marked damage goes from 1.875 to 1.75, i.e. essentially unchanged.
I am NOT adding an aware/unaware mechanic. An unaware target is one that does not blink, parry, disengage or heal — awareness is already the whole difference between eating the follow-ups and not. Adding a facing check would be one more multiplier, which is the disease.

BRAWLER FIXES (the author asked for these and they are independently correct):
- ColossusProtector.java:230 passes a hard-coded `0.0` angle into resolveBlockedDamage, so DamageReduction.resolve's 90-degree test can never fail and Instinctive Guard blocks hits from directly behind. Pass the real angle between the hit direction and the player's look vector.
- Instinctive Guard never consults `blockDelayTicks`, so it covers exactly the 5-tick window vanilla shields cannot (LivingEntity.java:3610-3623). Make it respect the same delay.
- TitansLeap.bulwarkHolding (TitansLeap.java:231-236) checks `getAbsorptionAmount() > 0`, so a golden apple buys 40% flat DR for two minutes. Track a BATTLE_TRANCE_ABSORPTION attachment in CrusherTicker (it already computes the cap at :127-132) and gate on that.
- ColossusSlayer.canParry (:239-247) requires a sword or greatsword in the mainhand. Widen to `isSword || isGreatsword || isMace || held.has(DataComponents.BLOCKS_ATTACKS) || WeaponClass.of(player) == HANDS`. Both builds the author names as Decimate survivors — the shield Protector and the unarmed Crusher — currently cannot parry at all. This is the single cheapest unlock for design target 2.
- Stalwart picks its amplifier from `floor(currentAbsorption / 4)` capped at 4 (ColossusSlayer.java:453-458), so a Colossus standing in his 18 Battle Trance points gets the full 20 on the very first parry and the advertised five-parry ramp does not exist. Change the input to absorption granted BY PARRIES, tracked on its own attachment.

MANA SHIELD GATE — LivingEntityMixin.java:511-540 requires `ModItems.isWand(player.getMainHandItem())`, but MagicArmaments.start (:97-109) removes the wand and puts MAGIC_SWORD there, and magic_sword is not in `archetypes:wands`. The blinking Spellsword the author describes has no Mana Shield and cannot have one. Widen the gate to `isWand(mainhand) || MagicArmaments.isChanneling(player)`.

## 4. Counterplay, per archetype

All numbers against the new Decimate: 36 raw on a type that bypasses armour, Protection and shields, resolved 12 ticks after the telegraph. Baseline for comparison — full netherite + Protection IV, 40 max HP, no relevant nodes, no reaction: takes the full 36 and ends at 4 HP (10%). That is target 1, and it holds for every archetype that does nothing.

NEMESIS — survives by not being seen, and only by that.
The victim filter now requires `player.hasLineOfSight(victim) && !victim.isInvisibleTo(player)`. An invisible Nemesis inside the arc takes 0. Not 36-reduced-to-something: zero, he was never in the list.
Uptime is the cost: INVIS_TICKS 160 x (1 + 0.5 x Stillness 2) = 320 ticks on INVIS_COOLDOWN_TICKS 600 (Tuning.java:281-282, ShadowTicker.java:138-142) = 53% coverage, and Ghost Armor hides the armour too (ShadowTicker.java:92-101). Nothing in the Slayer's kit can mark him — the mod's only GLOWING application is Umbral Sight, which filters `living instanceof Monster` and cannot tag players.
Caught visible: 36 into 40, ends at 4 HP. Ghost Form 3 at the reduced 0.15/rank is a 45% chance to void it outright (LivingEntityMixin.java:723-741) — a real epic payoff, no longer the primary answer. Last Shadow still catches the death once per 3600 ticks (AgilityCombat.java:123-153): health to 1, cleanse, 40 ticks of immunity, re-vanish.
Note this is a genuine change of story. Today invisibility does literally nothing against Decimate — the cast is a pure AABB sweep and never asks whether it can see anything.

ORACLE — survives by mobility, or by Mana Shield eating exactly one hit. Both routes now exist on the same build.
Blink: MAGIC_ARMAMENTS_BLINK_DISTANCE 8.0 (Tuning.java:569, MagicArmaments.java:319-376), no cooldown, fires on any conjured-sword swing with no hostile within 8 blocks of the crosshair. DECIMATE_RANGE is 3.5. The 12-tick wind-up is the entire reason this is counterplay rather than a coincidence: 0.6s is enough to press it and one blink clears the arc completely. Takes 0.
Mana Shield, wand OR conjured sword after the gate fix: absorbs `36 x MANA_SHIELD_ABSORB 0.5` = 18 raw, at the new 3.0 mana/point = 54 mana of a 500 ceiling (MANA_BASE 100 + Arcane Orb 25 + Beacon 25 + Spellcasting 100, all x2.0 from Oracle's Wisdom 2). He takes 18 and ends at 22/40 = 55%. That is 'lives through one hit' precisely: he is alive, he is below half, and the greatsword's normal swings finish him if he stays.
Spellsword with no shield up and no blink pressed: 36 into 40 HP + 12 Magic Armor absorption (the reduced cap) = 52 pool. Ends at 16/52, absorption gone, and it takes 1.2s of channel at 10 mana/s to rebuild. He survived, badly, and the answer he should have pressed was blink.
Mana regen is zero while a summoned weapon is held (ModItems.java:93-95, Mana.regenPerSecond:40-42), so a 500 pool buys ~50s of channel — the shield and the sword now compete for one budget, which is the fence that keeps this from being free.

COLOSSUS — survives by parrying, or by the unarmed Crusher's stacked temporary HP.
Parry: with canParry widened to shields, maces and empty hands, and PARRY_WINDOW_TICKS at 10 inside a 12-tick telegraph, the Colossus the author is describing can finally press it. tryParry classifies playerAttack(player) as melee (direct entity is the player and equals the source entity), the mixin returns false, and the hit does not happen — at any magnitude, and the `bypasses_shield` tag does not touch this path. He takes 0, gets Stalwart absorption, gets PARRY_READY_AT cleared, and if he holds a greatsword with Decimate he fires a free Decimate back (SlayerActives.decimate(player, true) — no cooldown read, no cooldown written). Whiffing costs 200 ticks against a 600-tick cooldown, so guessing is punished.
Unarmed Crusher: Bulwark 2 at the reduced 0.15/rank is x0.70 pre-armour, and it is the only mod-side shaper Decimate does not bypass — deliberately, because this is the author's named route. 36 x 0.70 = 25.2 into a pool of 40 HP + 18 absorption (Battle Trance 3 x 2.0 + Bulwark 2 x 6.0, CrusherTicker.java:127-132). He ends at 32.8/40 with his entire absorption bank spent, and rebuilding it costs 9 landed bare-fisted punches at 2/hit. The temporary HP is what saved him and it is a resource he had to bank in advance — exactly the author's sentence.
His armour, by contrast, does nothing against Decimate at all, and that is intended: `bypasses_armor` means Iron Skin, Hardened and Ironclad are all irrelevant to this specific hit. They come back to life against the greatsword's NORMAL swings, which is where the Ironclad reshape pays off: with ARMOR 22 / TOUGHNESS 14.4, a 29.25-raw swing now sees realArmor 16.8 (x0.328) instead of the pinned 20 (x0.20), and a bigger hit sees less — the damage curve exists again.
A Colossus who parries nothing and has banked no absorption takes the full 36 and ends at 4 HP, same as everyone else.

## 5. Verification — what the proof passes found

Three passes recomputed the fights under the proposal: one on Decimate against every counterplay,
one on the dagger, one hunting for the new outlier. **All three reported targets missed.**

### Pass 1 — targets hit: False

**PARRY_WINDOW_TICKS (Tuning.java:1037) vs DECIMATE_WINDUP_TICKS (proposed, 12)**

The parry window opens when the key is pressed and runs forward PARRY_WINDOW_TICKS (ColossusSlayer.parry sets PARRY_UNTIL = now + PARRY_WINDOW_TICKS). At 10 ticks it covers T..T+10 for a player who reacts instantly to a telegraph at T, but the hit resolves at T+12. A perfect reaction whiffs by 2 ticks and pays PARRY_COOLDOWN_TICKS. Only a player who deliberately delays 2-4 ticks after the telegraph can parry. The proposal's rationale that the window must be 'comfortably inside' the wind-up is backwards — a press-triggered window must be at least as long as the wind-up it is answering.

*Suggested fix:* PARRY_WINDOW_TICKS 8 -> 14 (not 10). 14 covers T..T+14 against a resolve at T+12, giving 2 ticks of latency slack on both sides. Equivalently DECIMATE_WINDUP_TICKS 12 -> 8, but 14 is the better number because it also leaves the window usable against mobs with slower wind-ups.

**MANA_SHIELD_ABSORB (Tuning.java:444) — the Oracle double-dip**

Mana Shield is a passive HEAD ModifyVariable with no button and no cooldown (LivingEntityMixin.java:511-540); Magic Armor's absorption is subtracted separately at Player.java:751 and no damage-type tag can bypass absorption. On the exact build the author describes — channeling Spellsword, Prot IV netherite, Mana Shield, Magic Armor 2 — they stack: 36 x 0.5 = 18 after the shield, minus 12 absorption = 6.0 to health, ending at 34/40 (85%). The proposal computed the wand case (55%) and the channel case (31%) as alternatives and never summed them. The Oracle ends up the tankiest target in the analysis, tankier than the Crusher at 82%, and the claim that 'mobility stays the real answer' is false — standing still is a better answer than blinking.

*Suggested fix:* MANA_SHIELD_ABSORB 0.5 -> 0.25. Then the stacked build takes 36 x 0.75 = 27, minus 12 absorption = 15 to health -> 25/40 (62%), and the wand-only build takes 27 -> 13/40 (33%). One number fixes both cases and puts the Spellsword below the Crusher, which is the ordering the design wants.

**DECIMATE_MAX_DAMAGE (Tuning.java, new) = 40.0F**

Against anything with a large health pool and little armour the cap makes the capstone weaker than it is today. Today Decimate wraps MeleeSwing.begin/end (SlayerActives.java:88-96) so the whole multiplier chain rides it: 24 raw x Heavy 1.3 x Combat 100 1.5 = 46.8 sustained (65.5 with First Blood). Capped at 40 and stripped of the chain, the new capstone delivers 40 flat — a 15% nerf on a 600-tick cooldown, and less than two normal 29.25-raw greatsword swings. On a 500 HP Warden it is 8% of the pool. The capstone becomes a PvP-only button and a PvE downgrade.

*Suggested fix:* DECIMATE_MAX_DAMAGE 40.0F -> 70.0F. It still cannot delete a Warden (14% of the pool), it stays entirely inert against players (36 < 70, so the cap never binds on a 40 HP target and Case A is unchanged at 4.0 HP), and it keeps the capstone ahead of its current PvE output.

**archetypes:decimate damage type JSON (new, data/archetypes/damage_type/decimate.json)**

The proposal specifies three tags but never specifies the type's own `scaling` field. Player.hurtServer applies difficulty scaling BEFORE any mod shaper or actuallyHurt (Player.java:691-705), and DamageSource.scalesWithDifficulty (DamageSource.java:92-98) returns true for ALWAYS regardless of a player source. Authored with "scaling": "always", 36 becomes 54 on Hard — an instant kill through a full 40 HP netherite player with no counterplay at all — and 19 on Easy. Vanilla player_attack.json uses "when_caused_by_living_non_player", which is why the current Decimate is difficulty-invariant.

*Suggested fix:* Author the type with "scaling": "never" and "exhaustion": 0.1. Also keep it OUT of #minecraft:bypasses_effects (or Resistance stops applying, deleting a legitimate answer and short-circuiting getDamageAfterMagicAbsorb at LivingEntity.java:1905) and OUT of #minecraft:bypasses_invulnerability (or Ghost Form stops rolling entirely, LivingEntityMixin.java:727, and the Nemesis's fallback disappears).

**ColossusSlayer.canParry widening (ColossusSlayer.java:239-247)**

The proposed clause `held.has(DataComponents.BLOCKS_ATTACKS)` is near-dead code. canParry reads getMainHandItem() only, but the vanilla shield is registered with equippableUnswappable(EquipmentSlot.OFFHAND) (Items.java:1613) and ColossusProtector.guardHand checks the offhand first precisely because 'that is where one lives'. A shield Protector holding anything at all in the mainhand still cannot parry; the only clause that actually unlocks him is WeaponClass.of(player) == HANDS, i.e. a completely empty mainhand.

*Suggested fix:* Test the offhand too: `player.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)` alongside the mainhand test. Without it, 'the Colossus survives because Parry is unlocked for shields' is only true for a Colossus carrying nothing in his main hand.

**Proposal's stated mechanism for Instinctive Guard dropping out (cited as ColossusProtector.java:206-247)**

The proposal claims 'Instinctive Guard's own gate already tests !source.is(DamageTypeTags.BYPASSES_SHIELD)'. That test is not in the code. The gate tests the ITEM's component: `blocksAttacks.bypassedBy().map(types -> types.contains(source.typeHolder()))`. It works only because the vanilla shield's BLOCKS_ATTACKS is constructed with DamageTypeTags.BYPASSES_SHIELD as its bypassedBy set (Items.java:1621). The outcome is correct for vanilla shields, but the reasoning is not, and it silently fails for any shield-like item whose BLOCKS_ATTACKS component names a different bypassedBy set or none at all (Optional.empty() -> orElse(false) -> the 30% still applies to Decimate). BlocksAttacks.resolveBlockedDamage itself has no BYPASSES_SHIELD test.

*Suggested fix:* Add an explicit `if (source.is(DamageTypeTags.BYPASSES_SHIELD)) return amount;` at the top of ColossusProtector.instinctiveGuard rather than relying on the item component, so the claim is true by construction for every blocking item the mod or any other mod adds.

**COLOSSUS_BULWARK_DR_PER_RANK (Tuning.java:930) as the sole surviving shaper**

After the rework Bulwark is the only mod-side multiplier that Decimate does not bypass, and it is a flat, zero-input, no-facing, no-cooldown percentage — structurally identical to the Instinctive Guard passive the proposal spends a paragraph condemning as 'the anti-skill gradient the whole rework is fighting'. It is defensible because the absorption bank behind it must be earned in advance, but at x0.70 the Crusher ends at 82% having pressed nothing at resolve time, the softest landing of any archetype examined.

*Suggested fix:* COLOSSUS_BULWARK_DR_PER_RANK 0.15F -> 0.10F (rank 2 = x0.80). 36 x 0.80 = 28.8, minus the 18-point bank = 10.8 to health -> 29.2/40 (73%). That puts the Crusher below the Oracle-after-its-own-fix and makes the banked absorption, not the passive multiplier, the thing that saved him — which is the author's sentence.

### Pass 2 — targets hit: False

**Opener ~25 into 40 HP**

The opener lands at 48.62 actual against an unarmoured 40 HP player (7.8 x 1.36 Razor x 2.35 ambush x 1.30 Twin Fangs x 1.5 Combat), a 22% overkill one-shot, and 31.73 against the armoured Colossus. The 25/16 pair is arithmetically unreachable: it demands the whole ambush package be worth at most +56%, but Twin Fangs (+30%) and Night Form (+35%) already sum to +65% before Shadow Flurry contributes, forcing SHADOW_FLURRY_BONUS = -0.09.

*Suggested fix:* Abandon 25 and adopt the same target Decimate uses: leave a prepared 40 HP player at ~10%. Fold Twin Fangs into the additive box and set SHADOW_FLURRY_BONUS = 0.55 -> box = 1 + 0.55 + 0.35 + 0.30 = 2.20, opener = 10.608 x 2.20 x 1.5 = 35.0 into 40 HP (5 HP left, 87.5%), with the armoured Colossus taking 35.0 raw -> realArmor clamp(22 - 6.25) = 15.75 -> x0.37 -> x0.36 = 4.7. If 25 is non-negotiable, SHADOW_FLURRY_BONUS = 0.20 with Twin Fangs folded in gets 27.5 and is the closest a single number reaches.

**COUP_DE_GRACE_PLAYER_MULTIPLIER (Tuning.java:830, applied LivingEntityMixin.java:333-336)**

The proposal collapses Shadow Flurry, Night Form, Death Mark and Headhunter into one additive box but leaves Coup de Grace's x2.0 against players entirely outside it, and that branch has NO health gate for players (the isPlayer branch multiplies unconditionally). The full Nemesis opener is therefore 128.3 actual on an unarmoured 40 HP player and 83.7 through netherite + Protection IV + Ironclad, and 58.6 even through Bulwark. The rework removes the one-shot only from the build that skips the Nemesis capstone.

*Suggested fix:* Move Coup de Grace into the same additive box as +1.0 (COUP_DE_GRACE_PLAYER_BONUS = 1.0F), giving a marked opener box of 1 + 0.55 + 0.35 + 0.30 + 0.25 + 0.50 + 1.0 = 3.95 instead of 3.10 x 2.0 = 6.20. Marked opener becomes 10.608 x 3.95 x 1.5 = 62.9 unarmoured -- still lethal, so also gate the player branch on COUP_DE_GRACE_THRESHOLD (0.30F, Tuning.java:826) the way the mob branch already is, so the finisher finishes rather than opens.

**Follow-up ~16 against an armoured target**

The follow-up hits 15.91 exactly against an unarmoured 40 HP player but only 4.66 against the reshaped Colossus (35.0 raw -> realArmor 15.75 -> x0.37 -> Protection IV x0.36). This is a structural ceiling, not a tuning miss: even with 100% armour ignore the stab is 15.91 x 0.36 = 5.73, because Protection IV alone caps the Cutpurse at 36%. No Assassin-side number reaches 16. The Ironclad reshape makes it worse specifically for daggers -- un-pinning realArmor was meant to punish big hits and reward small ones for the greatsword, but the dagger IS the small hit, so it now eats A = 0.63 where the old pinned clamp gave 0.80... i.e. the reshape helps the dagger less than it helps the greatsword.

*Suggested fix:* State 16 as an unarmoured-only target and set a separate armoured target of ~7-8, or raise FLENSE_PER_RANK from 0.12 to 0.20 (rank 2 = 0.40pp). That takes the follow-up factor from x2.20 to (1 - 0.63 + 0.40)/0.37 = x2.08 on the realized fraction and lands ~6.5 -- still a curve, not the old x5.0 switch, and it is the only bounded lever left. Do not chase 16 through armour; Protection IV makes it impossible.

**Flense formula, proposed snippet (LivingEntityMixin.java:296-306 and mirror DamageTrace.java:434-440)**

The snippet computes A from a local named `raw`, but every candidate value of `raw` inside a HEAD ModifyVariable is wrong. Using the incoming amount (7.8) gives A = clamp(22 - 1.39)/25 = 0.80 (armour-cap clamped) and F = x2.20. Using the damage that CombatRules will actually see is circular -- Flense inflates the damage, which lowers realArmor, which lowers A, which lowers Flense. Solving the fixed point for the follow-up gives A = 0.682 and F = x1.756, a 20% swing; on the opener the realized A collapses to the 4.4/25 = 0.176 floor and the true F is only x1.21 versus the x2.20 the code will compute -- Flense over-compensates by 1.8x on exactly the hit that matters most, and does so worst against the heaviest armour, which is the same bias the rework set out to remove.

*Suggested fix:* Compute A from the damage that would arrive WITHOUT Flense, i.e. run the Flense stage last (after Razor, Expose, mark, ambush and Twin Fangs) and pass the accumulated `result` rather than the HEAD `amount`. That makes A single-valued, non-circular, and correctly makes Flense near-worthless on an alpha strike (armour is already floored at armor * MIN_ARMOR_RATIO) and meaningful on the sustained stab -- which is exactly the budget shift the proposal says it wants.

**Bulwark 2 as a binary switch against the dagger**

Because Bulwark is a pre-armour HEAD ModifyVariable (LivingEntityMixin.java:1000-1007), its x0.70 does not merely cut 30% -- it also drops the incoming raw enough that realArmor stops being floored, so the armour term swings from x0.824 to x0.6548 on the opener and from x0.37 to x0.295 on the stab. Net effect: an armoured non-Bulwark Colossus dies to opener + 2 stabs in 1.3 seconds (31.73 + 7.29 + 7.29 into 40 HP), while a Bulwark Colossus takes 17.65 then 2.60 per stab -- 9 more stabs over 3.7s while the absorption bank refills at CrusherTicker.java:127-132 rates. One two-point node is the difference between a 1.3s death and immortality.

*Suggested fix:* This is the multiplicative-DR-stacking-with-a-nonlinear-armour-curve problem the Decimate section already identified; move Bulwark to the post-armour stage (a ModifyVariable on actuallyHurt's amount, or the same hook Mana Shield uses) so its 30% is 30% and does not silently buy a second 20% by un-flooring realArmor. If that is too invasive, drop COLOSSUS_BULWARK_DR_PER_RANK from 0.15F to 0.10F (rank 2 = x0.80): opener 85.6 raw -> realArmor 6.71 -> x0.7316 -> x0.36 = 22.5, stab 3.6, killing the binary.

**Death Mark / Headhunter placement in the additive box**

The proposal's box formula is written inside the Shadow Step path only, but DEATH_MARK_DAMAGE_FACTOR and HEADHUNTER_PER_RANK are applied at LivingEntityMixin.java:316-321, OUTSIDE the STEP_STRIKE_AT gate at :325 -- they ride every dagger hit, not just the step. Taking the snippet literally deletes the marked bonus from every sustained stab (27.85 -> 15.91 against an unarmoured target, a 43% cut), which contradicts the proposal's own claim that sustained marked damage moves only from 1.875 to 1.75.

*Suggested fix:* Split the box in two: a mark box (1 + 0.25 + 0.25 * headhunter = 1.75) computed at :316 for every dagger hit, and a step box that ADDS the flurry/night/coup terms into the same sum inside the :325 gate rather than multiplying a second box on top. Otherwise the two stated outcomes cannot both hold.

**The proposal's own opener arithmetic ("249 raw becomes ~79 raw", "8.44 x 1.5 = 12.66")**

Neither figure reconciles with source. The real base is 7.8 raw (ATTACK_DAMAGE 4.8 from ModItems.java:203-206 plus Sharpness V's 3.0 flat add), and the current plain-Cutpurse chain is 7.8 x 1.24 x 5.00 x 1.5 x 3.0 x 1.5 x 1.5 = 489.65 raw, not 249. The brief's 377 figure is reproducible as 372.1 but only as armour-only mitigation with Protection IV and the entire mod-side stack omitted (with them it is 134.0 and 40.2). The claimed x3.14 reduction is therefore measured against a number that does not exist.

*Suggested fix:* Restate the opener baseline as 489.65 raw / 372.1 after armour / 134.0 after Protection IV / 40.2 after Instinctive Guard + Bulwark, and recompute the claimed reduction against it. Under the proposal the same build is 106.96 raw / 88.13 / 31.73 / 17.65 -- a x4.6 cut on raw, better than the x3.14 claimed, but the post-mitigation numbers land where they land because armour is already floored, not because the multipliers were collapsed.

### Pass 3 — targets hit: False

**SUNDER_PER_LEVEL / levels = rank x 2 for HANDS (Tuning.java:168, LivingEntityMixin.java:655)**

The re-derived Sunder makes the unarmed Crusher the strongest thing in the game. Bare fists land 4.47 per punch at 4.0 punches/s = 17.9 DPS through full netherite + Protection IV, versus 6.66 DPS for the greatsword the rework is supposed to be fixing. Two compounding causes: fists get 4 virtual Breach levels to the mace's 2, and the compensation is computed from the pre-boost raw but applied to the post-boost number, so CombatRules shreds armour a second time off the inflated damage. Haymaker advertises x2.23 and delivers x3.84 (24.27 landed, on a 400-tick cooldown with no telegraph and a Slowness V stun); Quake advertises x1.70 and delivers x2.34.

*Suggested fix:* Two changes, one of which is the single number. NUMBER: drop the fist doubling — levels = rank, not rank x (HANDS ? 2 : 1), at LivingEntityMixin.java:655. That takes the punch from x3.206 to x1.789 (16.1 raw, 1.91 landed, 7.6 DPS) which sits correctly beside the greatsword's 6.66. FORMULA: compute A from the damage that will actually reach CombatRules, not from raw — either iterate once (compute the multiplier, then recompute A from raw x multiplier and re-solve) or, cleaner and exactly what Breach does, stop multiplying raw and instead hand the ignored fraction to CombatRules by stamping a real armor_effectiveness modifier on the weapon, the MagicArmaments.java:430-444 pattern the proposal already rejected for Decimate but which is right here.

**ColossusSlayer.pay (:401-402) and SlayerActives.decimate free path (:55-64)**

A successful parry removes PARRY_READY_AT entirely, so parrying is free on success, and the greatsword riposte fires a Decimate that neither reads nor writes its own cooldown. With Decimate at 36 damage on a type that bypasses armour, Protection and shields, and tryParry (:333) treating ANY melee blow as parryable, this is an unlimited-rate AoE true-damage nuke driven by being attacked. Two parries kill any 40 HP player. Thirteen parries kill a Warden in ~12 seconds at zero damage taken, which defeats DECIMATE_MAX_DAMAGE = 40 by raising the cast rate instead of the number. Raising PARRY_COOLDOWN_TICKS to 200 does not touch this — 200 is the whiff cost at :296-297.

*Suggested fix:* Give the free Decimate its own clock. Add DECIMATE_FREE_COOLDOWN_TICKS = 200 and have riposte read and write it (a separate attachment from DECIMATE_READY_AT, so the author's 'automatically cast Decimates can happen while the skill is on cooldown' still holds). If only one number may change: stop refunding on success — delete the removeAttached(PARRY_READY_AT) at ColossusSlayer.java:402 and write PARRY_COOLDOWN_TICKS there instead, so a landed parry costs the same 200 ticks a whiffed one does.

**DECIMATE_MIN_DAMAGE = 24.0F (new, Tuning.java beside :152)**

Design target 1 — 'leaving an unprepared target at 4 HP' — is missed for every player with max health below 26.67, i.e. everyone under roughly Defence 34. The floor beats the fraction whenever 0.90 x maxHealth < 24, so a vanilla 20 HP player takes 24 into 20 and dies outright: 120% of the pool against a stated 90%. Missed by 4 HP over lethal at the vanilla baseline, and the miss is a guaranteed one-shot through armour, Protection and shields on a 600-tick cooldown. The floor also buys nothing it was sold for: 0.90 x 20 already leaves a zombie on 2 HP.

*Suggested fix:* Set DECIMATE_MIN_DAMAGE = 0.0F and let the fraction do all the work — 90% of max health is self-scaling by construction and never needs a floor. If the author insists a Decimate must kill trash outright, gate the floor on the victim: apply MIN only when !(victim instanceof Player), which preserves the zombie one-shot and restores target 1 for the entire player population.

**Executioner reformulation (Tuning.java:143, LivingEntityMixin.java:172-176)**

The proposed replacement — a second hurtServer on archetypes:execute for health + absorption + 1.0 with all three bypass tags — is uncapped, has no cooldown, and is a passive on every greatsword hit. It deletes the last 15% of every boss in the game: 75 HP off a Warden, 45 off a Wither, 30 off an Ender Dragon. This is strictly larger than the 40 that DECIMATE_MAX_DAMAGE exists to enforce, in the same tree, and the proposal's own argument for that cap ('the single most important guard against nothing becoming the new outlier') condemns it.

*Suggested fix:* Cap the execute payload at DECIMATE_MAX_DAMAGE: deal Mth.min(victim.getHealth() + victim.getAbsorptionAmount() + 1.0F, Tuning.DECIMATE_MAX_DAMAGE). It still guarantees the finish on anything with a PvP-sized health pool (40 max HP x 0.15 = 6 HP, well under 40) and stops the greatsword from removing 75 HP of Warden for free.

**CrusherCombat.onCrusherHit splash loop (CrusherCombat.java:44-53) called from inside archetypes$sunderDamage (LivingEntityMixin.java:667)**

The `splashing` flag guards re-entry into onCrusherHit only; it does not guard the ModifyVariable. The splash re-enters hurtServer with archetypes' MeleeSwing still open, so Sunder is applied a second time to a number that already carries it. Under the proposed formula the splash victim of an 8-block mace smash takes 28.8 while the target that produced the splash takes 18.2 — the splash hits 58% harder than the primary. The same bug exists today at x1.25 and is invisible; the proposal raises it to x2.17.

*Suggested fix:* Make archetypes$sunderDamage itself respect the splash guard: expose the `splashing` flag from CrusherCombat and early-return from the mixin when it is set, exactly as onCrusherHit does at CrusherCombat.java:31-33. One boolean read, and it also fixes the pre-existing x1.25.

**Proposed Sunder and Flense formulas (LivingEntityMixin.java:296-306, :654-661; mirrors at DamageTrace.java:438, :573-577)**

Both compute A from the victim's ARMOR/ARMOR_TOUGHNESS attributes, which cannot see armor_effectiveness. Real Breach is applied later, inside CombatRules (vsrc CombatRules.java:24-27). So a mace with Breach IV plus Sunder 2 pays twice for the same armour: the 44-raw smash lands 24.44 where Breach alone lands 15.84 and neither lands more than 18.2, because once Breach has clamped armorFraction to zero Sunder's x1.543 is pure damage multiplier on a node sold as armour penetration. Separately, both formulas newly read `raw` inside a `- raw/t` term, which makes their output depend on whether the victim's Bulwark / Instinctive Guard / Mana Shield ModifyVariables ran first — mixin order among same-target HEAD ModifyVariables is not guaranteed, so the proposal introduces order-dependence into damage numbers where there was none.

*Suggested fix:* Stop computing armour penetration outside CombatRules. Implement Sunder and Flense as real armor_effectiveness contributions — the MagicArmaments.java:430-444 virtual-enchant stamp, or an EnchantmentHelper.modifyArmorEffectiveness hook — so they compose additively with Breach, see the correct post-shred armorFraction automatically, and stop being sensitive to mixin ordering. That is exactly the 'same units as the enchantment it imitates' the proposal argues for; it just has to be applied in the same PLACE as well as the same units.

**COUP_DE_GRACE_PLAYER_MULTIPLIER (Tuning.java:830, LivingEntityMixin.java:333-336) and SIDESTEP_PER_RANK (Tuning.java:319, LivingEntityMixin.java:493-502)**

Two omissions from the proposal's counterplay accounting. (a) Coup de Grace is a flat x2.0 applied to any Shadow Step strike on a player, unconditionally and with no health gate, and it is not in the additive box — the one multiplier that is player-exclusive is the one left out of the collapse, and it is silently supplying the missing factor in the proposal's own '~29' figure. (b) Sidestep is a HEAD cancel with no damage-type test, so 20% of Decimates are voided by dice; stacked with the reduced Ghost Form 3 (45%) a Nemesis Assassin voids 1 - 0.80 x 0.55 = 56% of Decimates whether visible or not. Design target 2's 'Nemesis survives because Slayer cannot see Nemesis' is still not the reason he survives.

*Suggested fix:* Put Coup de Grace's player branch inside the same additive ambush box as +1.00 rather than leaving it as a multiplication (opener goes 8.06 -> 4.03 as the proposal actually intends). For Sidestep, give it the same victim-list treatment Decimate's other answers get: skip the roll when the source is archetypes:decimate, so the Nemesis's answer is invisibility and Ghost Form and nothing else.

## 6. Risks the proposal names itself

WHAT BREAKS, deliberately.
Instinctive Guard, Omni-block, Barbarian and every point of armour stop mattering against Decimate. That is the design — a capstone that four separate zero-input passives can each halve is not a capstone — but it does mean a Colossus Protector who built entirely for passive mitigation has no answer at all until he takes Parry. If that reads as too harsh, the softer version is to leave `bypasses_shield` off and accept Instinctive Guard's x0.70 (post-fix), which puts Decimate at 25.2 on him; I do not recommend it, because it reintroduces a zero-input answer to the one attack that is supposed to demand input.
Barbarian x2 (x0.25 against `archetypes:magical`) is now the largest untouched zero-input multiplier in the game, and it is worth 2.0x EHP per point — the best ratio in the Brawler kit after Ironclad. I have not touched it because it is out of scope for Decimate, but it is the next thing to look at, and its symmetric penalty on magical healing is the only reason it is not already the top complaint.

WHAT MIGHT BECOME THE NEW OUTLIER.
Sundering IV instead of Sharpness V takes a greatsword's sustained swing against a post-fix Colossus from ~1.04 to ~2.94, i.e. 56 swings to 20. Against an UNARMOURED target it is a nerf: attribute 12.0 x 1.3 Heavy x 1.5 Combat = 23.4 raw versus 29.25 with Sharpness. That is the correct shape — an anti-armour enchantment should lose to Sharpness on flesh — and it means Sundering cannot delete the Cutpurse, who wears little. Checked.
The Cutpurse after the cuts: opener ~79 raw, which against full netherite floors realArmor at 4 (x0.84), then x0.36 Protection, then Flense 2 at 0.24pp fully covering A = 0.16, giving x1.19 — final ~28.8 into 40. Follow-up lands ~9-11 at current dagger stats, so it is opener plus two clicks, not opener plus one. Against an unarmoured target the opener is 79 flat, still the highest burst in the game by a wide margin, so the archetype's identity survives. Sundering's 2.94/swing does not come close.
Percent-of-max-health cuts both ways on mobs: DECIMATE_MAX_DAMAGE 40.0 is the only thing standing between this design and a Warden-deleter, and it is the constant most likely to need a second pass once anyone fights something with a large health pool. If 40 feels low against bosses, raise the cap rather than the fraction — the fraction is calibrated against a 40 HP player and should not move.

WHAT IS GUESSWORK.
The dagger follow-up number is the weakest part of this draft. Getting it to 16 requires the sustained marked swing to reach ~52 raw against netherite; I back-derived the current value as ~35-37 from the 377 figure in the prior analysis rather than reading ModItems' dagger ATTACK_DAMAGE directly, and RAZOR_EDGE_PER_RANK 0.08 to 0.12 only buys ~7% of the ~50% needed. The rest has to come from the dagger item's base damage (roughly +40%, netherite dagger ~7 to ~10). Verify the actual item stats before touching that; my constant is a direction, not a value.
The 377 reconstruction itself (249 raw x 0.84 x 0.36 x 5.0) matches to within rounding, which is reassuring, but it assumes the opener floors realArmor — true at 249 raw, and still true at 79.
DECIMATE_WINDUP_TICKS = 12 is a feel number, not a derived one. It has to exceed PARRY_WINDOW_TICKS with slack and has to be long enough to blink in; below 8 the parry becomes a guess again and above ~20 the cast becomes trivially dodgeable by walking. 12 is the middle of that band and wants playtesting more than arithmetic.
GHOST_FORM_NEGATE_PER_RANK at 0.15 makes the Nemesis's epic capstone a 45% coin flip on top of an invisibility answer that is now genuinely strong. If invisibility-as-immunity turns out to be too clean — and it might, since 53% uptime against a 30s cooldown means he can often just choose to be untargetable — the lever to pull is INVIS_COOLDOWN_TICKS (600), not Ghost Form.
Finally: I have not verified that scheduling Decimate's resolve 12 ticks out interacts safely with the DECIMATE_SWING_AT client pose attachment, with the block-sweep loop, or with the player logging out mid-wind-up. The resolve needs a null/alive guard on the caster and should probably cancel outright if the greatsword leaves the mainhand.