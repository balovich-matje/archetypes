# Archetypes — design notes

Working doc. Nothing here is decided yet except the premise and the interop rule.

## Premise

An RPG class mod where **you pick your class in the first minute of a playthrough**,
from a button next to the Skills button in the inventory screen.

Explicitly *not* the RPG Series / More RPG Classes model
(see [rpg-series-reference.md](rpg-series-reference.md)):

| Them | Us |
| --- | --- |
| Find a Spell Binding Table, craft a spell book, bind spells with XP | Pick an archetype from a menu, immediately |
| Class only active while holding the right weapon + book | Archetype is a property of the player |
| Runes as consumable ammo | (undecided) |
| Needs Spell Engine, Runes, AzureLib, Gazebos, Better Combat | Vanilla + Fabric API only, ideally |
| All active, cast-on-button abilities | (undecided — passives are an open lane) |

## Naming

- Mod name: **Archetypes**, slug `archetypes` (free on Modrinth as of 2026-07-15;
  CurseForge unverified — their API blocks anonymous queries, needs a manual check).
- Publish under the title **"Archetypes — RPG Classes"**, not bare "Archetypes".
  Evidence: on a Fabric search for `class`, **all ten top hits have "class" in the
  title** — including irrelevant ones like *Classic Pipes*, because "class" prefix-
  matches "classic". Meanwhile **Wizards (RPG Series), with 4.7M downloads, does not
  appear at all**. Title tokens beat popularity by a mile. Same lesson Specialities
  learned; this time on purpose.
- Only weak name collision: *Ancestral Archetypes* (4.5k downloads) — judged acceptable.

## Interop with Specialities — the rule

**Both mods must work standalone. Neither may hard-depend on the other. Installed
together, they should feel like one system.**

### Together

- Archetypes can add its own **skills** to the skills screen — things that only exist
  because Archetypes does. Example: a caster archetype uses **mana**, so Archetypes
  contributes a skill that lowers mana cost and raises max mana as it levels.
- The skills screen gets **tabs at the top**: *Common skills* (Specialities' 15) and
  *Archetype skills* (whatever Archetypes contributes).
- Specialities must never learn what mana is. The dependency only ever points
  **Archetypes → Specialities**, never back.

### Archetypes alone (no Specialities)

- Archetypes puts the **skills button in the inventory itself** and opens the same
  screen, showing only archetype skills. No tabs, or a single tab.
- So the screen is a shared surface either mod can own; whoever's present provides it.

### Specialities alone

- Unchanged from today. It must not know Archetypes exists.

## DECIDED: no library mod. Host election instead.

**Archetypes carries its own copy of the skill engine.** There is no shared library
mod, and there never will be — users install either mod, or both, and are never asked
to install a third thing.

Rejected: extracting a `skill-core` library. The install step is the smaller half of
the cost; the real cost is version matching ("Archetypes 1.3 needs skill-core 1.2+"),
which generates confused bug reports forever. Not worth it to save ~960 lines of the
most stable code in the mod (the XP curve and attachment layer have not changed since
day one; every bug so far has been in UI or mixins).

### Host election

Exactly one mod owns the HUD bar, the inventory button and the screen:

| Installed | Host | Other mod |
| --- | --- | --- |
| Specialities only | Specialities | — |
| Archetypes only | Archetypes (own engine, own skills, no tabs) | — |
| Both | **Specialities** | Archetypes goes dormant and hands its skills over as a tab |

### Mechanism (costs the user nothing)

- Specialities defines a small public interface (`com.specialities.api.SkillTab` or
  similar) and collects tabs with
  `FabricLoader.getEntrypointContainers("specialities:skill_tabs", SkillTab.class)`.
- Archetypes takes Specialities as **`modCompileOnly`** and declares that entrypoint
  in its `fabric.mod.json`. If Specialities is absent nobody ever queries the key, so
  the adapter class never loads — no crash, no dependency, no version pin.
- Archetypes guards its own HUD/button/screen registration behind
  `FabricLoader.getInstance().isModLoaded("specialities")`.
- So Archetypes has two paths: its own internal skill type + screen (standalone), and
  a thin adapter implementing Specialities' interface (only ever loaded when
  Specialities is present).

### Day-one collisions — must be handled before both mods ship together

Not hypothetical; all three are guaranteed if Archetypes registers its UI blindly:

1. **The HUD shifts twice.** Both mods raise the vanilla hearts/XP bar by
   `HUD_SHIFT = 7` to make room. Both active = 14px. Visibly broken.
2. **Two "S" buttons** in the inventory, at the same anchor.
3. **Two skill XP bars** at identical coordinates.

### Accepted long-term costs

- **The tab interface is a public contract.** Once Archetypes ships against it,
  renaming it breaks users. Keep it tiny.
- **UI fixes land twice** (the XP-0 bar bug would have). Softened: each copy only
  affects that mod's standalone users — two products, not one product broken twice.
- **Curve drift.** If Specialities' XP curve changes and Archetypes' does not, a
  player with both sees two progression feels in one screen. Keep the curve
  consciously mirrored.

## DECIDED: the three archetypes

Three archetypes, each with a **start name** (what you pick, minute one, holding a
wooden pickaxe) and a **peak name** (the same archetype fully levelled).

| Stat | Start | Peak | Fantasy |
| --- | --- | --- | --- |
| Strength | **Brawler** | **Colossus** | Melee, face to face. Starts punching zombies bare-fisted on night one. |
| Agility | **Cutpurse** | **Nemesis** | Stealth melee *and* ranged (bows/crossbows). |
| Intellect | **Seeker** | **Oracle** | Casting. Starts with a feeble cantrip that drains mana for less DPS than a wooden sword; ends casting meteors on long cooldowns. |

The naming rule that makes the set cohere: **a humble medieval occupation becomes a
figure of myth.** The peaks are classical antiquity — the Oracle of Delphi, the
Colossus of Rhodes, Nemesis the goddess of inescapable retribution. The gap between
"nobody with a wooden pickaxe" and "thing out of a legend" *is* the arc.

Internal ids are the neutral stat (`strength`, `agility`, `intellect`) so renaming a
tier never touches saved data.

Names deliberately avoided (taken by the competition): Warrior, Rogue, Mage, Wizard,
Paladin, Priest, Druid, Archer, Death Knight, Berserker, Witcher, Forcemaster. Also
Warden — that's a vanilla mob.

## DECIDED: vanilla-look UI

**Every screen should feel like it shipped with the game.** White borders + grey
backgrounds — the classic container style, not the dark custom look of v0.1's picker.
Proof this reads as native at scale: **Pufferfish's Skills** (3.3M downloads) draws its
whole full-screen tree in exactly this style.

Exact palette, sampled from 26.2's `inventory.png` (don't eyeball it, use these):

| Element | Colors |
| --- | --- |
| Window | 1px `#000000` outline (corner pixel transparent = rounded), 2px `#FFFFFF` highlight top/left, body `#C6C6C6`, 2px `#555555` shadow bottom/right |
| Inset / slot | 1px `#373737` top/left, body `#8B8B8B`, 1px `#FFFFFF` bottom/right (the window bevel inverted) |
| Titles/labels | `0x404040` dark grey, **no shadow** (like "Crafting") |

Colored text (archetype names) stays — vanilla uses colored text freely; it's the
custom *chrome* that breaks the illusion.

## Picker screen (sketched)

- Each archetype is a square frame split by a **diagonal**: start tier top-left, peak
  tier bottom-right. Frames are vanilla insets on a vanilla window (see palette).
- Hovering a half **enlarges that art out of the frame** — e.g. Brawler is a player in
  copper armor with an iron sword; Colossus is the same player in netherite with a
  rare armor trim, dual-wielding enchanted netherite swords.
- Needs 6 pieces of art (3 archetypes x 2 tiers). Placeholder for now.
- Third-party/mod armor art would need permission from those authors first.

## Skill trees

**A mix of WoW talent trees + vanilla Minecraft UI + Skyrim's constellation design.**

Each archetype gets one tree screen holding **three sub-trees**. Every sub-tree is a
*constellation*: its connected nodes trace the outline of something representative of
the class — like Skyrim's skill constellations. **Multiple starting points, all at the
bottom, expanding upwards.**

| Archetype | Sub-tree | Constellation shape |
| --- | --- | --- |
| Brawler | **Protector** | shield |
| Brawler | **Slayer** | sword |
| Brawler | **Crusher** | mace |
| Cutpurse | **Marksman** | bow |
| Cutpurse | **Assassin** | dagger |
| Cutpurse | **Shadow** | cloak |
| Seeker | **Fire Mage** | fire |
| Seeker | **Wizard** | staff |
| Seeker | **Healer** | heart / regeneration |

- Node graph in the vanilla style: slot-inset squares joined by dark lines on the grey
  inset canvas (the Pufferfish look), not glowing WoW arrows.
- **Size: 20–40 nodes per sub-tree** — whatever the shape needs to read.
- The tree window is **full-screen** (small margin), like Pufferfish's — not a
  dialog-sized panel.
- The three sections are split by **thin vanilla dividers** (1px dark + 1px light
  engraved groove) — deliberately slimmer than the 2px-per-side window bevel, and
  *not* blended background art, which is what would break the vanilla feel.
- Ranks (`2/2`), actives vs passives, costs: all undecided — see open questions.
- **Shipped placeholder first**: every node is "+1% damage" with no effect, so the
  layout/feel can be judged in-game before any balance work.

### DECIDED: constellations and background art are independent

The nodes are **not** traced over the background image, and the background is not the
sub-tree's symbol. Two separate things:

- **Constellations** = the node layout, one symbol per sub-tree, drawn in nodes.
- **Background art** = *one image per class*, class-fantasy themed — the
  [WoW talent tree](https://www.wowhead.com/talent-calc/warrior/protection) model.
  Not copying it; same idea.

## Background art: local generation (first pass done)

**Toolchain**, installed at `/Volumes/ADATA SE920 SSD/repos/stable-diffusion.cpp`:
[stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp) built with
`-DSD_METAL=ON`, running **Z-Image-Turbo Q6_K** (6B, GGUF) + Qwen3-4B text encoder +
the FLUX VAE. Generator script: `gen-archetypes.sh`, first outputs in `notes/art/`.

Why this route: Z-Image documents no Mac/MPS support, and its "fits in 16G VRAM" claim
means *dedicated* VRAM — this box is an M4 with 16GB **unified**. sd.cpp's Metal
backend plus a quantised model is what actually fits. **~4 min/image** at 1024x576,
8 steps, cfg 1.0 (the Turbo recipe from the repo's own `docs/z_image.md`).

Gotcha: the VAE the docs point at (`black-forest-labs/FLUX.1-schnell`) is **gated** —
401 anonymously. Same file is ungated at `Comfy-Org/z_image_turbo/split_files/vae`.

**Prompt shape that worked.** Three blocks: a Minecraft-style block (voxel geometry,
cubic shapes, pixelated 16x16 textures), the scene, then a mood block. The mood block
is the important one, because these are backdrops for a node graph, not pictures:
*dark, muted, desaturated, deep shadows, uncluttered center, subject small and at the
edges, no text, no characters facing camera.*

**Verdict on the first pass** (composites in `notes/art/composite_*.png` — art +
vignette + nodes, which is the only test that matters):

- `strength` — best. Reads as genuine Minecraft: blocky torches, cobblestone, crossed
  swords, a shield. Detail left, empty right.
- `agility` — good. Night rooftop, lantern, fog; detail at both edges, empty middle.
- `intellect` — **v2, fixed.** The first pass read as an isometric voxel diorama and
  was centre-bright exactly where the middle constellation sits. Two prompt changes
  did it: *eye level perspective* instead of isometric framing, and the enchanting
  table's glow pushed *far off to the left*. Now a dark library hall with shelves
  receding on both sides and an empty floor through the middle.

Confirmed by the composites: a vignette plus ~55% brightness leaves the nodes fully
readable while the art still reads. Imperfect art is genuinely fine.

**The generalisable lesson**: the failure was never "the model can't do Minecraft" —
it does that well. It was *framing and light placement*. Name the camera and put the
light where the nodes are not.

## Licensing: no blocker, but a judgement call (checked 2026-07-15)

The question "can we use AI art under MIT?" has the premise backwards. MIT is the
licence *we grant*; it says nothing about inputs. What matters:

- **Model licence**: Z-Image-Turbo is **Apache 2.0** — commercial use allowed, no
  claim on outputs. This is the part that could have blocked us. It doesn't.
- **Copyright in the output**: in the US, purely AI-generated work lacks human
  authorship and is **not copyrightable**. So we may not own it — but that is *more*
  permissive than MIT, not less: under MIT we were already letting anyone copy it.
  Substantial hand-editing would create a copyrightable human contribution.
- **Modrinth**: has **no AI policy**. The rules never mention AI; they only require
  that we hold the rights to distribute, which Apache 2.0 gives us. There are open
  community issues asking for disclosure tags, so this may change.

**So the real risk is reception, not law.** Modding audiences skew toward hand-made
pixel art, and Nexus already requires disclosure. Live option if that matters: use
these as **concept art** for a pixel artist — sidesteps reception *and* yields
ownable assets. Undecided; not to be settled by quietly shipping.

### Authoring: ASCII grids

Shapes live in `Constellations.java` as ASCII grids (`'#'` = node), written top-down
the way they appear on screen, so the shape is legible and editable in source.
`Constellation.of(...)` parses them and derives edges by 8-connectivity, so a chain of
touching cells becomes a chain of nodes and an outline becomes a ring. Row 0 of the
parsed result is the bottom row: **each constellation roots at the bottom and grows
up**, and the three roots per class satisfy "multiple starting points at the bottom".

**Lesson from the first pass** (prototyped in Python and rendered before writing any
Java — much faster than rebuilding the game): at this node density *only a
distinctive silhouette survives*. A circle on a stick describes a mace **and** a
staff; both first drafts read as lollipops. An X reads as a butterfly, not as crossed
daggers. Fixes that worked: spikes on the mace, a diamond crystal on the staff,
guards and pommels on the daggers, a pointed hood and wide hem for the cloak, and a
single curled tongue for the flame (three tongues read as a cloud).

## DECIDED: what this mod is

**Groundwork for a large RPG modpack** — one that will bring its own structures,
enemies and boss fights — **while still being worth installing on bare vanilla.**
Those two goals pull in opposite directions, and most of the decisions below are
about holding both.

Consequences:

- **Few recipes.** The job is to improve vanilla play, not to bolt a new crafting
  economy onto it.
- **No difficulty system** (see below). The pack owns that knob. So might a third
  mod of ours.
- Closer to vanilla than to Better Combat / RPG Series: no hard dependency on
  Spell Engine, Better Combat, AzureLib or similar.

## DECIDED: items amplify, they never gate

The premise is that **an archetype is a property of the player**, not of what is in
their hand — that is the whole difference from RPG Series (see the table under
[Premise](#premise)). "The rogue isn't whole without daggers" quietly rebuilds the
thing we rejected: a Cutpurse who has not found iron yet would not really be a
Cutpurse.

So the rule, and the test for every item we add:

> **Delete the item from the game. Does the archetype still function?**
> If no, it is a gate — redesign it.

Every archetype works from minute one with nothing: the Seeker's opening cantrip is
cast bare-handed, the Cutpurse backstabs with any sword, the Brawler punches. Items
make the fantasy *fit better*; they never switch it on.

### The three items

| Archetype | Item | Recipe | Shape |
| --- | --- | --- | --- |
| Seeker | **Wand** | 2 sticks | Cast with it held in either hand. Deliberately first-minute cheap. |
| Cutpurse | **Dagger** | stick + ingot/diamond/etc | Sword-like but shorter. Faster movement while sneaking; **less damage than a sword outside stealth**. |
| Brawler | **Greatsword** | stick + more material than a sword | Bigger and slower, more damage per swing. |

Notes on each, honestly:

- **Wand is still a soft gate** and we should keep our eyes open about it. Two sticks
  in the first minute is about as ungated as an item gets, but a Seeker who drops
  their wand down a ravine stops being able to cast. The cantrip staying bare-handed
  is what keeps this honest — **if casting ever requires the wand, the rule is broken.**
- **Dagger is the model.** It gives up damage to gain a situational advantage; it is
  never strictly better than a sword. Copy this shape for anything later.
- **Greatsword needs care.** "Costs more, hits harder" is a straight vertical upgrade —
  exactly what the next section says not to build. It only stays honest if the extra
  damage is paid for in swing speed, i.e. **similar DPS, different rhythm**: big
  commitment, big recovery. If it ends up simply better than a sword, it is power
  creep wearing a trade-off costume. Watch the numbers.
- **Wizard capes** deferred: pure cosmetic, and vanilla armour trims already carry it.

## DECIDED: no difficulty system — grant power that does not need offsetting

Vanilla is too easy once armour is enchanted, and layering more power onto a player
who already wins makes the game worse, not better. The instinct is to add difficulty
to compensate. **We are not doing that**, for three reasons: it fights every pack
that brings its own difficulty mod (RPG packs always do), it is enormous scope, and
it is a workaround for power we chose to hand out.

Instead, archetype power must be **horizontal or costed**:

- **Vertical** = `+100% damage`. The same fight, won faster. This is what makes an
  easy game easier.
- **Horizontal** = a new verb. *Hit at range. Vanish mid-fight. Hold a chokepoint.*
- **Costed** = mana that runs dry, stealth that breaks when seen, a bash on cooldown.
  Power that answers a situation instead of raising a baseline.

Done right, a fully-enchanted Oracle is not *stronger* than a fully-enchanted vanilla
player — they are **differently capable**. The game gets textured, not easier, and the
difficulty knob stays free for the pack to own.

### DECIDED: archetypes reshape power (starting with Seeker)

Seeker casts well **and a sword is not viable for them**. The choice costs something,
which is what makes it mean something. How the sword penalty is expressed (damage
malus, recovery malus, or both) is Seeker-tree design work, later.

### DECIDED: Specialities' x2 damage stays

The uncomfortable part. **Specialities already grants +100% melee damage at level 100**,
plus passive Fortune and Looting. By our own diagnosis it layers power onto a player
who already wins — and in the intended modpack that immortal becomes doubly immortal,
hitting twice as hard, with better animations. So the power creep we are trying to
avoid in Archetypes **is already shipped in the other mod**.

Two live positions:

1. **Cut or curve it down.** Archetypes staying horizontal cannot save a stack where
   Specialities doubles damage underneath it.
2. **Leave it; a third mod supplies the challenge.** We do not have to stop at two
   mods — the missing danger is itself a gap worth filling, and packs may end up
   treating these as survival essentials rather than power-ups.

The honest weakness of (2): it bets on a mod that does not exist yet, and until it
does, **Specialities standalone is the unbalanced thing** — and standalone is what
everyone who downloaded it is playing. Not urgent, but not resolved.

## Protector (shield) tree — build order

**Shield Bash is the root and everything's dependency**: five of the nine nodes are
modifiers on it, so it lands first and alone. It is the mod's first *active* ability —
keybind, C2S packet, server-side cooldown and targeting.

Ranked by cost, having checked what each actually needs:

| Tier | Node | Why |
| --- | --- | --- |
| **Root** | Shield Bash | Keybind + payload + cooldown + targeting. Everything else waits on it. |
| **Free** | Shield Slam (+33/66/100% dmg, +cooldown) | A number on the bash. |
| **Free** | Bash cooldown −33/66/100% | A number. **But −100% = no cooldown at all** — should be −25/50/75, or the capstone is a spam button. |
| **Free** | Knockback trade (less dmg, more knockback) | Two numbers. |
| **Cheap** | Wide Swings (bash hits several) | Swap the single-target pick for an AABB query. |
| **Cheap** | Shield Unbreaking I | Mixin on durability loss. Independent of bash. |
| **Cheap** | Iron Spikes (Thorns V/X/XV, no knockback) | `hurtServer` mixin while blocking; reuse vanilla's Thorns durability formula. Independent of bash. |
| **Medium** | Omni-block (block from all directions) | Mixin on the block-angle check. Independent of bash. |
| **Medium** | Shield Rush (block + sprint → lunge 2→5) | Client input combo → payload → server velocity. Needs care: a lunge is a movement exploit vector. |
| **Medium** | Reflection (projectiles return to sender) | Projectile mixin; re-aim and re-own so the shooter takes it. Vanilla already bounces arrows off shields — build on that. |
| **Hardest** | Ground Slam (3x3 AOE) | AOE is easy; the *animation* is the work. Placeholder first. |

**Suggested first slice**: Bash → Slam → Cooldown → Wide Swings. One active plus three
numbers gives a whole playable path and proves the active-ability plumbing before any
animation work.

### Capstones

**Omni-block** and **Ground Slam** are the tree's magnum opus — reachable only after
enough points are spent, and mutually exclusive (the middle node picks one). Both want
an effect that reads without a custom model: Omni-block gets a **glowing aura** around
the player, which is also the honest signal to *other* players that backstabbing is
off; Ground Slam gets a placeholder swing + existing sound until real animation work.

### DECIDED: you cannot buy the whole tree

**15 points per sub-tree**, against ~23 real nodes — so a Protector is full damage,
full utility, or a compromise, never all three. This is the actual balance lever (see
the XP note below): the cost per point only paces *when* you spend, the cap decides
*how much* you ever get.

The numbers line up on purpose: **45 archetype levels, one point each, 15 per
sub-tree** — a fully-levelled Brawler has exactly enough points to fill all three
budgets, and no budget covers its own tree.

**The node-count gap stays, deliberately.** The Protector constellation has 38 nodes;
the skill list is ~23 even counting every rank. The surplus stays as inert "+1% damage"
placeholders for now — the shape was authored to read as a shield and that is worth
keeping while the real node list settles.

Candidates to replace the placeholders (all horizontal or costed, dagger-shaped):

- **Steadfast** — knockback resistance while blocking.
- **Unburdened** — blocking slows your walk less.
- **Bell Ringer** — bash briefly applies Slowness: crowd control, not damage.
- **Taunt** — bash forces nearby mobs to target you. The tank verb; shines in co-op.
- **Braced** — blocking a hit refunds a slice of bash cooldown (synergy, costed by
  requiring you to actually get hit).
- **Perfect Guard** — a block within ~0.5s of raising the shield negates the hit
  entirely: a timing skill, the most horizontal thing a shield can do.

### The cooldown formula — the honest problem

The intent: bash is spammable at sword cadence when undamaged-boosted, and Shield Slam
trades speed for burst so DPS lands near a diamond sword either way. **Shield Slam is
good design** — equal damage and cooldown multipliers make it exactly DPS-neutral. It
converts sustained damage into burst, which is horizontal.

**Pure cooldown reduction is the problem, and it is not a rounding error.** With
cooldown `= base × (1 + slam) × (1 − reduction)`, the slam terms cancel and DPS scales
as `1 / (1 − reduction)`:

| Reduction | DPS multiplier |
| --- | --- |
| −33% | 1.49x |
| −66% | 2.94x |
| −100% | **infinite** |

So it is vertical power at *any* value, and −100% is division by zero. Capping it only
makes the creep smaller, not horizontal.

**DECIDED — the two-layer model (the original design, which was right).** The 1/(1−r)
blow-up above only afflicts a single multiplicative cooldown — which is what the first
implementation shipped. The intended shape never had the problem:

- **Swing layer**: the vanilla item cooldown (grey sweep), 16 ticks, a cadence floor
  the bash can never beat. Bashing also **resets the melee attack timer**, so a bash
  always costs a sword swing — without that, a fast bash weaves between sword hits as
  free additive DPS, which playtesting immediately found as "very spammable".
- **Ability layer**: +6s on top, drawn as a numeric countdown under the crosshair
  (synced timestamp attachment, no bespoke packet). Quick Recovery strips −33/66/100%
  of *this layer only*, so −100% is safe by construction: spammable at sword cadence,
  0.55x sword damage, shield still up. Shield Slam adds its +33/66/100% here too —
  full Slam against full Recovery lands back on 6s: a bigger hit, original rhythm.
- Baseline (no nodes): one 5.0-damage shove every ~6.8s — periodic crowd control,
  not a damage rotation. Exactly the Protector fantasy.

### Reflection: keep the fantasy, tax the damage

Auto-returning projectiles trivialises skeletons and ghasts. **Decided**: keep the
mechanic — it is the best fantasy in the tree — and apply a **x0.5 damage modifier** to
the reflected projectile. The mob still eats its own arrow; it just is not a free
execute.

## DECIDED: archetype XP is vanilla XP

No separate XP bar. The archetype feeds off the player's own experience, and a skill
point costs **160 XP — exactly levels 0→10** (verified against
`Player.getXpNeededForNextLevel`: `7 + 2L` below 15, `37 + 5(L−15)` below 30,
`112 + 9(L−30)` above).

**XP is mirrored, not consumed** — earning XP feeds points in parallel, so archetype
progress never competes with enchanting. Otherwise every point is an enchant you did
not get, and the mod starts taxing vanilla instead of adding to it.

**The cost is flat, so it means wildly different things over time:**

| At level | One vanilla level | A point costs |
| --- | --- | --- |
| 0 | 7 XP | ~23 levels |
| 10 | 27 XP | ~5.9 levels |
| 30 | 112 XP | ~1.4 levels |
| 50 | 292 XP | ~0.55 levels |

That is a **40x swing**, and ~8.7 points by the time you first hit level 30. An XP farm
buys the tree outright. This is fine *only because the cap exists* — farming changes
how fast you reach the ceiling, not how high it is. **The cap is doing all the balance
work here; the XP cost is only pacing.** If the cap ever goes, this breaks.

Testing affordance: a creative-only **Skill Token** grants one point.

Notes:

- **Cooldown −100% is a bug in waiting.** Cap the reduction well under 100%.
- **Iron Spikes and Unbreaking are the best "easy wins"** — real, felt, no bash needed,
  no new plumbing. Good candidates if Bash slips.
- **Reflection may want a cap** (rate or arc), or it trivialises skeletons and ghasts
  outright — that is vertical power wearing a utility hat.
- **Animations**: Better Combat drives its own via its library; we are not taking that
  dependency (see "what this mod is"). Placeholder = vanilla arm swing + an existing
  sound event; revisit only when the tree is proven.
- **Node icons**: vanilla item sprites, tinted/composited, exactly as Specialities does
  it (`SkillIcons`). No new art needed.

## Slayer tree (built 2026-07-16)

The constellation IS a sword: pommel root (Hamstring — CC both weapons share),
grip, a 7-node crossguard where the paths split (Lunge out the left arm,
Immovable out the right, Vampirism at the junction), the two blade edges as the
paths — **sword left: gap-closing, crowd control, sustain; greatsword right:
oneshots, slow, immovable** — an empty fuller between them, and the capstone
cross at the tip with Bloodlust above it, reachable only through a capstone by
geometry alone. 24 nodes; full sword lane 14, greatsword lane 13, cap 15.

**One ability key.** G is now "Archetype Ability": the server dispatches on the
mainhand — shield bashes, greatsword Decimates, sword Bladestorms. No new binds.

- **Decimate** (greatsword capstone): one tilted cleave (sweep arc drawn falling
  ~25° across the swing), double attribute damage in the front arc. Blocks:
  only instant-break clutter (torches, grass, fire...) is swept — playtesting
  showed anything stronger turns base defence into base demolition, so the
  earlier weaker-than-stone + logs/planks rule is gone. Capped at 48 blocks.
  30s cooldown.
- **Bladestorm** (sword capstone): 3s channel, six half-damage volleys, ends
  early if the sword leaves the hand. 45s cooldown. Four copies of the actual
  sword spin near-flat around the player at two heights (the Bulwark ghost
  pipeline), which half-hides them — the WoW read.
- Passives: Hamstring (Slowness I/II on hit), Vampirism (half heart per rank on
  melee kills), Lunge (sword swings hop you along the look vector — whiffs
  too, it is a gap-closer; suppressed during bladestorm whose volleys swing),
  Immovable + Heavy Blows (transient attribute modifiers while greatsword held),
  Rend (bleed, sword only), First Blood (+25%/rank vs unhurt targets),
  Executioner (greatsword finishes below 15%), Flurry (sword kills reset Lunge),
  Bloodlust (kills grant Speed).
- Greatswords live in `#archetypes:greatswords` and are deliberately NOT in
  `#minecraft:swords`-scoped passives: `ModItems.isSword` subtracts the tag, so
  bleed and lunge never trigger from the two-hander.
- Cooldown numbers ride the weapon slots like the bash's, same HUD.
- Greatsword texture v2: full 16px-diagonal blade + display-transform scale
  (1.35 third-person), so it reads player-length in hand.

## Animation library research (checked 2026-07-16)

The question: 2H greatsword grip, dual-wield for the rogue, and real swing
animations for Decimate/Bladestorm. Checked against Modrinth's live version
data, not memory — 26.2 is three weeks old and support lags:

| Mod | 26.2? | Verdict |
| --- | --- | --- |
| **Player Animation Library** (`player-animation-library`) | **yes** | The one. MIT, Fabric+NeoForge, actively maintained, loads Blockbench/GeckoLib JSON animations with molang and particle keyframes. Proof it works: Emotecraft ships on it for 26.2. The playerAnimator lineage's current form. |
| playerAnimator (`playeranimator`) | no (caps at 1.21.7) | Superseded by the above for 26.x. |
| Better Combat | **no** (caps at 1.21.11, updated 2 days ago) | Actively maintained but not yet through the 26.x port. Its data-driven weapon attributes (two_handed, dual_wield, per-weapon animations) are exactly what we want — when it ports. |
| GeckoLib | yes | Wrong tool: entities/blocks/items, not player combat poses. |

**Plan:**
- Branch `player-animation-experiment`: Player Animation Library as the first
  external dependency. Test cases: a real Decimate swing (the tilted cleave as
  an actual body animation), a Bladestorm spin pose, and a 2H greatsword idle.
  Judge the feel against the dependency cost, then decide.
- Better Combat compat can ship **today as pure data**: weapon-attribute JSONs
  under `data/bettercombat/` are inert without BC installed — zero dependency,
  and the greatsword becomes properly two-handed for anyone who adds BC once it
  ports to 26.x.
- Rogue dual-wield: revisit when we get there; BC's port status decides
  whether we ride theirs or build our own.

## Open questions
- **Passive or active?** Specialities is entirely passive and that's been its
  character. Classes may want active buttons — but "all passives" is a genuinely
  unoccupied niche here. (Shield Bash commits us to actives for Archetypes.)
- **Mana** (or any resource): only if an archetype needs it. Don't invent a resource
  for its own sake. (Leaning yes for Seeker — the cantrip already implies it.)
- **Can you change archetype later?** Free / costly / one-shot item / never?
- **Does an archetype level?** Or is it flat identity, with skills carrying progression?
- **Warrior needs no new verbs from items** — the interesting work there is behaviour
  on vanilla gear: shield bash, parry timing, a cleaving swing. Mixins, not recipes.

## UI seed

- Button beside the Skills "S" button in the inventory, same anchoring trick
  (see `SpecialitiesClient.anchorButton` — the recipe book moves `leftPos` without
  re-running init).
- First open with no archetype = the picker. Afterwards = your archetype page.
- Skills screen gains a tab strip at the top once there's more than one source.

## Reusable from Specialities

The whole spine transfers; it's server-authoritative and already solved:

- Persistent per-player data via a Fabric attachment, synced to the owning client
  (`ModAttachments.SKILLS` + `PlayerSkills` codec pattern).
- Server → client payload for state changes (`SkillUpdatePayload`).
- Inventory-screen button injection + custom screen (`SkillsScreen`, `SkillIcons`).
- Attribute-modifier passives applied on join/respawn/change (`DefencePassives`).
- All 26.2 API gotchas already documented in `specialities/CLAUDE.md` — read that
  before writing any 26.2 code here.

**Claymore → Greatsword rename (2026-07-16).** The six-ingot slab never was a
claymore — the name says slender Scottish two-hander, the sprite says wall of
metal. "Greatsword" tells every RPG player exactly what it is. Item ids, tag,
recipes, particle and code all renamed; old claymore items in test worlds are
gone (id change, pre-release so no migration).

**Slayer tree rework (2026-07-16).** Weapon-agnostic families now live at the
bottom, weapon-specific ones in their branches. The hilt is two columns — both
roots: Hamstring (now 3 ranks — I, II, then 5s duration) and Taste of Blood
(ex-Vampirism, renamed + raw-beef icon; was nonsensically split between guard
and sword edge). The guard shrank to four nodes; its quillons carry each path's
flavour single (Flurry / First Blood — leaves, not tolls). Ranks rebalanced so
a full path is exactly 15 again: Lunge 2×0.75 blocks, Immovable 2×30%, Heavy
Blows 2×15%, First Blood 1×40%. Hamstring wears the vanilla Slowness effect
sprite — first non-item node icon (Family now supports texture icons).

**Custom melee swings (2026-07-16, v1).** PAL-driven attack poses for four
weapon classes — greatsword (arc L-R, arc R-L, overhead), sword (two diagonal
slashes), mace (overhead, side), bare hands (alternating jabs; bare means
BARE — an offhand item reverts to vanilla). Spears skipped (too new). Client
catches startAttack when it isn't aimed at a block AND the swing is charged
(>=0.9 attack strength — spam clicks stay vanilla flicks, matching the damage
discount); server bumps a synced counter encoding (seq, class); every client
plays the class's next pose in cycle. Attack layer priority 900 — below the
capstone poses, so a bladestorm channel outranks a mid-storm click.
Hold-to-attack: attack key held on an entity + full charge = auto swing.
UI note ("2014 RPG minecraft"): node icons prefer real vanilla item renders
with effect overlays; strength-effect icon hides behind the shield for Slam,
piston pushes off it for Concussive Blow, dripstone grows from it for Spikes,
quarter clock for Braced.

**DECIDED: custom swing poses deprecated (2026-07-16).** Five iterations of
blind pose-authoring across two animation formats (axis conventions bit twice
— item roll vs pitch, z sign inverted) never got the greatsword arcs past
"almost". The deeper reason to stop: Better Combat will port to 26.x
eventually, and two systems fighting over first-person rendering, swing
detection and attack pacing in one modpack is a support nightmare. Kept, all
BC-independent: swing gating (no half-charged flicks), hold-to-attack, the
bladestorm swing lock, the greatsword's 2H offhand restriction and heavy
whoosh, and the PAL capstone poses (bladestorm spin, Decimate cleave) — those
are ability-scoped and coexist with anything. When BC ships 26.x, our compat
is pure data (weapon attributes JSON) plus possibly disabling our gating.

**DECIDED: hold-to-attack deprecated too (2026-07-16)** — same principle as the
swing poses: base-game combat behaviour belongs to combat mods (BC ships it).
Swing gating stays for now.

**Crusher tree, fists half (2026-07-16).** The constellation IS a mace: shared
handle of 4 (Adrenaline 2 — landing mace/fist hits grants 5%/10% attack speed
for 3s, doubled for fists; Sunder 2 — virtual Breach I/II mace, II/IV fists,
approximated as clawing back 15%/level of armor's ~4%/point absorption,
stacks with the real enchant), left flange fists (Bare-Knuckle 4 ranks ×0.5 →
fist DPS ≈ iron sword; Iron Skin 3 ranks +1.5 armor/+0.5 toughness while
bare-handed; Haymaker capstone on B: 2.5x attack damage single punch + 1.5s
near-stun, cooldown only spent on a hit), crown Battle Trance 3 ranks —
absorption per hit (doubled fists), 1 heart cap/rank, drains 5s after the
fight. Right flange: 8 placeholder minors, mace perks next. Economy mirrors
Slayer exactly: 23 nodes, 15 per full path (the math forces crown = 3 nodes —
went 3-rank Battle Trance rather than invent an unasked tip node). Item
placeholder icons until the perks settle.

**Crusher mace flange + fists tuning (2026-07-16).** Haymaker up to x4 damage
with a Knockback-II send-off; Iron Skin now +1 armor/+1 toughness per rank
(the earlier "2/10 armor" report was 4.5 armor = 2.25 icons — working as
coded, rebalanced anyway). Fixed: proc indicators crashed on Crusher families
(client resolver only knew Slayer/Protector) — likely why Battle Trance
looked silent; absorption itself is server-side, so retest with a truly
empty offhand. Mace flange: Shockwave 3 (falling mace hit splashes its full
damage within rank blocks), Meteor 2 (Density-like: +0.5 dmg per fallen
block per rank), Quake capstone (B with mace: 1.5s knockback-immune charge
with a rising-mace PAL pose, then a slam — 1.5x attack damage in 3 blocks,
monsters launched skyward, MACE_SMASH_GROUND_HEAVY + muffled explosion for
the rock-crushing feel). Two flange slots stay open for the utility perk.

**Crusher mace flange, round 2 (2026-07-16).** Shockwave bug root-caused: the
mace's post-hit hook resets fallDistance before AFTER_DAMAGE listeners run,
so the splash's "was falling" gate never passed (the knockback the playtest
saw was vanilla's own smash wind). Fixed with a SMASH_AT tick stamp written
during damage shaping, where fallDistance is still intact. Shockwave is now
2 ranks at 2/4 blocks. Flange reordered per spec: Meteor 1-2 at the base,
Shockwave 1-2 above, Earth Shatterer 1-3 up the outer edge, Quake at the
peak. Earth Shatterer: a Quake meeting no creature refunds 33%/rank of the
cooldown and shatters blocks up to stone hardness in 2/4/6 radius (two
layers: feet and below), one mace durability per block. Quake now scales
with Density (+1.5/level) and Meteor (+2/rank): mace AD 9 x1.5 + 7.5 + 4 ≈
25 — one-shots fresh zombies/skeletons at full investment.

**Crusher fixes round 3 (2026-07-16).** Battle Trance root cause: since
1.20.2 absorption clamps to the MAX_ABSORPTION attribute (default 0) — raw
setAbsorptionAmount was clamped straight back to nothing while the proc
indicator (same code block) fired happily. The ticker now holds a
MAX_ABSORPTION modifier equal to the rank cap. Trance icon: vanilla's
absorbing_full heart sprite. Shockwave hardened: the smash stamp now also
comes from the ticker (mace + airborne + falling faster than 0.4/t) with a
3-tick freshness window — immune to wherever vanilla resets fallDistance.
Mace flange icons per spec: mace item render + fire charge corner (Meteor),
compass corner (Shockwave), stone corner (Earth Shatterer), cracks-behind
(Quake).

**Shockwave finally root-caused (2026-07-16, evening).** The playtest log held
the smoking gun: an Over-Overkill advancement (50-heart mace blow) with ZERO
handler activity. Fabric's AFTER_DAMAGE is bytecode-gated on
!isDeadOrDying() — it never fires on lethal hits, and a Density/Meteor
Shockwave test one-shots everything. All Crusher on-hit passives (Adrenaline,
Shockwave, Battle Trance) moved out of AFTER_DAMAGE into the hurtServer
damage-shaping mixin, which runs before death resolution and knows the exact
shaped damage for the splash. Trade-off accepted: the shaping hook can fire on
hits later cancelled (iframes/shields) — rare — versus never firing on kills,
which is the common case for this build. Slayer's AFTER_DAMAGE users
(Hamstring, Rend) are correctly gated: slowing/bleeding a corpse is pointless.

**Picker crests replace tier portraits (2026-07-16).** The pick screen's
two cut-out character placeholders (Brawler/Colossus halves per frame) are
scrapped. Each frame now carries the start-tier name only and one image:
the archetype's crest, a collage of its three sub-archetype weapons.
Strength = Protector's shield front and center with the Slayer's sword and
the Crusher's mace crossed behind it — the shield a scale step smaller
than the weapons, because at parity it swallowed the X and the crest read
as "a shield" instead of "three arms". Composed by
notes/art/make_picker_art.py from vanilla sprites at integer scales on a
128px canvas, doubled to the 256px the screen samples. Agility and
intellect fall back to their item icons until their trees decide what
their three weapons even are.

**Picker crest verdict + constellation economy (2026-07-16).** The
shield-forward crest (big shield, weapon tips and grips peeking out) beat
the weapons-dominant rebalance — reverted to it. All six agility/intellect
placeholder constellations redrawn to the Brawler economy: 23 nodes each
(15-point subtree cap, 45 levels), so building those trees later means
naming nodes, not reshaping grids. Shape changes: Assassin's crossed pair
is now a single dagger (broad tapered blade, long handle, spread pommel —
the handle-to-blade ratio is what says "knife"); Shadow's cloak — which
never read as one — is now a crescent moon, two arcs sharing tips with an
explicit edge bridging the inner arc's skipped centre row; Fire Mage is
renamed Elementalist (flame kept, tightened to 23; amethyst shard icon —
element-neutral raw magic); Healer is renamed Apothecary and the heart
became a round-bottom flask (corked lip, pinched neck, fat globe; brewing
stand icon). Wizard's staff gained an orb cradled low in the diamond head
and a flared butt cap; the bow gained an arrow-rest nub on the stave.
Protector remains at 25 nodes — one per real skill, grandfathered.

**Cutpurse & Seeker: weapons + skill plan (2026-07-16).**

*Weapons first, trees second.* The Cutpurse's dagger: seven material tiers
derived from the vanilla swords in make_weapon_textures.py (the blade cut
four diagonal steps shorter — palette and outline stay vanilla for free);
recipe one material over one stick; stats 0.6x the sword's damage at 1.5x
the swings = 0.9x DPS, the Assassin tree pays the tenth back. In
minecraft:swords like the greatswords, excluded from isSword() so Slayer
passives never proc from a knife. The Seeker's starting Magic Wand: two
sticks stacked, a longer carved stick with a spark at the tip, no melee
stats; casting checks the archetypes:wands tag so better wands are just a
texture and a recipe. WeaponClass gained DAGGER and WAND.

*Apothecary is now Priest*, third rename of this tree (Healer, Apothecary,
Priest) — the kit below is holy, not herbal. Constellation: an ankh (loop
of 10, crossbar of 7, shaft of 6 = 23). Icon: totem of undying.

*Planned actives and capstones* (numbers are first drafts; every tree also
gets its passive families later, same 23-node/15-cap economy as Brawler):

- Marksman — True Shot, first node: 20s cooldown, next bow shot flies
  without gravity at x2.0 damage; the arrow quietly despawns past 64
  blocks (deliberately not in the description). Capstones: (1) True Shot
  homes to the nearest target but drops to x1.0 — trades the damage for
  never missing; (2) x4.0 and fires instantly on use, no draw.
- Shadow — Invisibility: 8s of the effect, 30s cooldown. Capstones:
  (1) kills while invisible renew the full duration; (2) cheat death —
  fatal damage instead cleanses negative effects, grants 2s of total
  immunity and casts Invisibility; both then share a 180s cooldown.
- Assassin — Shadow Step: teleport behind a target up to 16 blocks away
  and strike once, dagger in hand required, 15s cooldown. Capstones:
  (1) the step strikes 4 times at full power, cooldown 30s; (2) any kill
  resets Shadow Step's cooldown.

*Seeker casts on mana, not cooldowns.* Mana comes from a new Specialities
skill, Spellcasting: +1 max mana per level, +1 base regen per 20 levels,
XP earned by spending mana. Base 100 mana, 1 regen/s. Mana bar sits above
the hunger bar: ten water bottles as a percentage gauge (47/100 mana =
4 full bottles, floor rounding); the bottle count never changes with the
pool. Placeholder art, may replace later. NOTE: registering a skill from
Archetypes means Specialities needs a public skill-registration API — that
half of the work belongs to the Specialities chat and needs coordinating.

- Elementalist — Fireball: 50 mana, 2.5 hearts, ignites the target,
  ghast-style projectile that cannot be reflected. Capstones: (1)
  Meteorite — fireball becomes a meteor spawned 16 blocks above the
  target block, flying down fast into AoE damage; spends ALL current
  mana (100 minimum), more mana = bigger radius and damage; wipes
  zero-hardness blocks (torches etc.) in the area; uncastable without
  clear sky above the target. (2) Flamethrower — fireball becomes a
  channel: rapid blaze-style bolts, 50 mana to start + 25/s held.
- Wizard — Magic Missile: 25 mana, wand in main hand, straight line up
  to 16 blocks, 3 hearts. Capstones: (1) missiles home to the nearest
  enemy at 33% less projectile speed; (2) missiles pierce everything
  they hit and get a larger hitbox.
- Priest — Holy Light: 50 mana, a projectile that bursts on hard
  surfaces like a splash potion — 2.5 hearts of healing to the living,
  the same as damage to undead. Capstones: (1) also grants Regeneration
  for 10s to non-hostiles; (2) also grants a random positive effect for
  10s to non-hostiles — Speed, Strength, Resistance or Fire Resistance.

Open question, parked: only Magic Missile names a wand requirement — do
Fireball and Holy Light require one too, or is the wand Wizard-flavoured?

**Cutpurse & Seeker actives + the mana engine (2026-07-16, late).** All six
new trees are live as active-plus-capstones skeletons: the active sits at
the constellation's root (bottom row), the two capstones flank the crown as
a mutually exclusive pair (user ruling: ALL capstone pairs exclusive,
Slayer precedent), and every other node is a pickable, inert placeholder
(PlaceholderNodes — one table serving all six until their passives arrive).

The three ability keys became SLOTS (G/H/B = first/second/third tree of
YOUR archetype), one ActiveAbilityPayload(slot) replacing the three bespoke
payloads; the server resolves what a slot means. Strength keeps its exact
old bindings by construction.

Agility: True Shot (arm; next bow shot flies flat at x2, arrow silently
despawns at 64 blocks; Seeker Arrow = homing at x1, Snap Shot = instant
fire at x4 — arrow spawn caught via ServerEntityEvents.ENTITY_LOAD age-0,
flight rules in an AbstractArrow tick mixin), Invisibility (8s/30s;
Predator renews on kills while invisible via AFTER_DEATH; Last Shadow
cheats death via ALLOW_DEATH — cleanse harmful, 2s hurtServer-cancel
immunity, invis, 180s shared clock), Shadow Step (ray 16 blocks, teleport
behind the TARGET's facing, one full-charge strike — attackStrengthTicker
accessor set high, then Player.attack for authentic crits/enchants;
Shadow Flurry = 4 strikes over ticks at 30s; Momentum resets on any kill).

Seeker: zero cooldowns, mana only. Mana lives in Archetypes (attachment,
regen tick, spend), the Spellcasting skill lives in SPECIALITIES' engine
via its new external-skill API: Specialities 1.4.0-dev turned the Skill
enum into SkillType (registry-backed, enum keeps its constants), pulls a
"specialities:skills" entrypoint at init, and Archetypes registers
Spellcasting through it — soft dependency both ways, compileOnly via
mavenLocal (publishToMavenLocal after any Specialities API change), every
runtime touch behind compat/SpecialitiesBridge's isModLoaded guard.
1 XP per mana spent (fractional carry), +1 max mana/level, +1 regen/20.
Mana bar: ten 9px hand-drawn bottles above hunger, percentage with floor
rounding, shifted up 7 when Specialities' HUD_SHIFT is in play; air-bubble
overlap accepted for now.

All five spells are ONE entity (SpellProjectile extends
ThrowableItemProjectile — the vanilla ThrownItemRenderer draws whatever
item it wears, zero custom rendering): Fireball (fire charge, 50 mana, 2.5
hearts + ignite, undeflectable by construction), Meteorite (magma block
falling from 16 above the targeted block, all mana min 100, radius/damage
scale with mana, wipes only hardness-0 blocks, needs clear sky),
Flamethrower (channel: client streams SpellChannelPayload while the key is
held, gap = channel over; 50 to start + 1.25/tick, blaze-powder bolts),
Magic Missile (amethyst shard, wand-gated, straight 16 blocks; homing at
-33% speed OR pierce-with-wider-sweep — pierce bypasses vanilla entity
hits entirely and sweeps an inflated box, each victim once), Holy Light
(glowstone lob, splash-shaped burst: heal living / hurt undead 2.5 hearts;
Renewal adds Regeneration, Benediction a random buff, non-hostiles only).
A chunk-reloaded spell wakes modeless and discards itself.

Wand rule confirmed: Wizard requires the wand; Elementalist and Priest
cast bare-handed. The tree screen's blanket "Preview" header died — every
tree now does something; only the placeholder minors say so, per node.

**Testing QoL + Marksman passives (2026-07-17).** Spellcasting regen formula
now +1/s per 25 levels (+5 at cap, on top of the base 1). Two creative
Spellcasting Tomes (+25/+100 levels, Specialities' knowledge-book twins,
bridge-guarded). Mana potions, shaped like their health twins: Mana Restore
(lapis; instant +50/level) and Mana Regeneration (amethyst; +2/s per level,
45s halved at II) — custom MobEffects + four Potion registrations; splash/
lingering/tipped-arrow forms and creative-tab entries all come free from
vanilla's potion plumbing.

Seeker Arrow's "homes half the time" root cause: nearest-in-8-blocks target
selection (including things BEHIND the arrow) plus a 20%-per-tick nudge that
barely bends a 3-block-per-tick arrow. Shared Homing helper now: 16-block
sensor, forward cone (dot > 0.2) so only things ahead count, 50% per-tick
turn. Seeker Missile uses the same brain with an Enemy filter.

Marksman is the first Cutpurse tree with real passives (graduated from
PlaceholderNodes to MarksmanNodes): string side Conservation 1-4 (12.5%/rank
arrow refund — refunds the item and downgrades the flying arrow to
creative-only pickup so it can't be collected twice), Rapid Reload 1-4
(crossbow kill primes the next charge, -25%/rank via a getChargeDuration
ModifyReturnValue, floored at 1 tick because vanilla divides by it; prime
synced to the owner for the draw animation, cleared server-side on load),
Focus (arrow hits refund 10s of True Shot); stave side Disengage 1-2 (sprint
mid-draw leaps 3/6 blocks back — same consumeClick trick as Shield Rush,
which owns the press while blocking, this one while drawing), Nimble Draw
1-3 (the draw slowdown is the USE_EFFECTS component's speedMultiplier, read
in LocalPlayer.itemUseSpeedMultiplier — a client-only ModifyReturnValue
hands back a third of the penalty per rank; players are movement-
authoritative so no server half), Swift Flight 1-2 (+50%/rank arrow speed,
damage-neutral: base damage divided back down since arrow damage scales
with impact speed), Pinning 1-2 (Slowness I/II 3s), Combustion at the
arrow-rest nub (burning target hit = 3-block blast for the arrow's full
damage; on-hit batch lives in the hurtServer damage-shaping mixin — the
AFTER_DAMAGE lethal gate again — with a reentrancy latch like Shockwave's).
One node spare: the bow's top tip stays an Unnamed Star for a future idea.

**Marksman rework round 2 + spell price tags (2026-07-17).** The bow
constellation is now what the user asked for literally: a drawn bow with
an arrow through it — two symmetric limbs from grip (True Shot, bottom) to
crown (Focus, top, adjacent to BOTH capstones so either choice unlocks
it), and the arrow as a full 11-cell horizontal row bridging the branches:
Combustion at the head, a Pinning barb either end of the shared
4-node Conservation shaft, Swift Flight in the fletching. Left limb bow
(Disengage, Nimble Draw → Seeker Arrow), right limb crossbow (Night's
Gift — the new both-weapons Night Vision-on-kill passive, which landed
exactly in the one spare node — then Rapid Reload → Snap Shot).

Balance/behavior: Disengage cooldown 1s → 8s. Snap Shot now fires from a
crossbow too (the base arming stays bow-only; the capstone conjures its
own shot). Seeker Arrow rebuilt from "steering" to "aimbot": on launch it
picks the nearest visible hostile within 24 and leaves TOWARD it, flight
homing is hostile-only, non-hostiles are ghosts (canHitEntity waves them
through — a self-aiming arrow must not murder pets), and the base
cooldown drops to 10s so Focus (10s refund per hit) chains it
indefinitely — the low-damage spam fantasy vs Snap Shot's x4 nuke.

Cooldown bar: Seeker spells get tiles after all — not clocks but price
tags: mana cost top-left in blue, keybind bottom-right as usual, and the
icon dims exactly like a cooldown when the pool can't pay (one visual
language). Icon follows the build: fireball → magma block (Meteorite) or
blaze rod (Flamethrower). Note: 8s Night Vision sits inside vanilla's
sub-10s warning flicker the whole time; if it annoys, bump
NIGHT_VISION_TICKS past 200.

**Marksman constellation round 3 + mana HUD polish (2026-07-17).** The
pierced-diamond read badly in game; the bow is now at rest and tall (10x8):
string a straight column down the right (crossbow branch, bottom-up:
Night's Gift, Pinning x2, Rapid Reload x4, Snap Shot), stave arcing left
(bow branch: Disengage x2, Nimble Draw x3, Swift Flight x2, Seeker Arrow),
tips shared — True Shot bottom, Focus top, still adjacent to both
capstones — and the nocked arrow as a short horizontal row at the bulge:
Combustion head poking past the stave, Conservation x4 shaft bridging to
the string. No rank changes were needed; Pinning and Swift Flight just
moved onto the limbs (they stay both-weapon mechanically; the arrow row
bridges builds across).

Mana HUD: bottles are now blue orbs with the hearts' near-black outline
(radius-test drawn, glint upper-left; empties are dark sockets), and the
exact current mana sits over the row's middle, black-outlined like the XP
bar's level number. The Meteorite tile prices itself honestly: it shows
the mana it would actually drink right now (the whole pool), greying out
below the 100 minimum; fixed tiles keep their static costs.

**Three trees built while the user was away (2026-07-17): Shadow, Assassin,
Elementalist — plus Marksman fixes.** Wizard and Priest stay placeholders
per instruction. All numbers are DRAFTS for the balance pass.

Marksman: Disengage became Acrobatics — a 2/4-block forward roll on sprint
mid-draw, 8s cooldown. Night's Gift died to vanilla's sub-10s night-vision
flicker; its node is now Piercing Tips: bow/crossbow shots ignore 2 armor
points (Sunder-style compensation in the arrow damage shaping).

SHADOW (user's five passives + fillers on their branch brief). Outer arc =
endure the dark: Night Eyes (sneak = flicker-free Night Vision, fades
after), Swift Shadow 2 (+20%/rank speed while invisible), Dark Mending 4
(heart per 8/6/4/2s invisible), Dim Presence 2 (mobs notice you 15%/rank
less, always — same getVisibilityPercent channel as sneaking, stacks),
Cleansing Veil (cast cleanses debuffs), capstone Last Shadow. Inner arc =
kill in it: Umbral Sight (hostiles within 8 glow while sneaking/hidden),
Stillness 2 (standing still halves then stops the invis timer — re-adding
the effect each tick), First Strike 2 (+30%/rank melee from invisibility;
vanilla breaks invis after, so it self-limits), Bloodrush 2 (kills grant
+20%/rank attack speed 4s), Reaper (kills while invisible heal 2 hearts),
Ghost Armor (ARMOR_HIDDEN all-sync attachment; the avatar renderer's
extract clears the state's equipment fields so armor vanishes for every
onlooker), capstone Predator. Umbral Mastery crowns the top tip (+4s to
every invisibility: active, renewals, cheat-death).

ASSASSIN (dps branch to sword-parity, DoT branch, shared utility, and a
post-capstone improver, per brief). Pommel/grip shared: Lightfoot 2
(+10%/rank speed with dagger), Sidestep 3 (7%/rank melee dodge with
dagger). Centre line improves the active: Adrenaline Rush (Speed II 3s
after the blink), Opportunist (-3s step cooldown). Left edge dps: Razor
Edge 3 (+8%/rank), Frenzy 2 (+10%/rank attack speed), Expose (+15% below
half HP) — dagger 0.9x sword dps base becomes ~1.36x with everything, in
Slayer-sword territory. Right edge DoTs: Venom 2 (Poison I/II 4s), Blight
2 (Wither I/II 3s), Flense 2 (ignore 50%/100% of armor — exact
compensation, so full ranks vs 20-armor targets is a x5 on the absorbed
share). Deathblow at the point: Shadow Step strikes (flurry included)
+50%, recognised by a same-tick attachment stamp.

ELEMENTALIST (two elements, exclusive at the root, per brief). Base row:
Fireball left, ICE BLAST right (new spell: 50 mana, 2 hearts, Slowness III
4s, PLAYER_HURT_FREEZE report), mutually exclusive — picking one locks the
other AND its whole branch, since each branch only connects through its
root. Focused Mind between them (+0.5 regen). Fire edge: Kindling 2 (-5
cost/rank), Scorch 2 (+1 heart/rank fireball, half for flame bolts),
Ignition 2 (+3s burn/rank), Vaporize (fire projectiles boil water blocks
off their path), then the old Meteorite-or-Flamethrower choice. Ice edge:
Chill 2 (-5 cost/rank), Frostbite 2 (slow +1 level +1s per rank), Shatter
2 (slowed/freezing targets take +15%/rank from ALL your spells — reads the
slow already on the victim, so volleys ramp), Permafrost (ice projectiles
glaze water into frost-walker ice), then a new choice: GLACIAL SPIKE
(x2.5 lance that also powder-snow-freezes the target in place via
setTicksFrozen) or BLIZZARD (snow-bolt channel, flamethrower's twin: 50 +
25/s, bolts slow and stack Shatter). Crown above either capstone:
Spellweaver (all spells -10% mana — cross-tree, missile and holy light
included) and Arcane Power (all spells +20% damage). Ice/snow modes are
two new SpellProjectile modes; all per-cast shaping (Scorch/Ignition/
Shatter/water meddling/slow/freeze) rides builder flags set at cast time.

Cooldown-bar tile follows the element and build: fireball/magma/blaze rod
vs ice/blue ice/snow block, priced through the same elementCost() the
server charges.

**Balance round from the first Shadow/Assassin playtest (2026-07-17).**
Stillness was accidentally infinite invisibility (the +1-duration-per-tick
trick cancelled decay entirely while standing still) — now it's simply
+50%/100% invisibility duration, folded into invisDuration(). That freed
Umbral Mastery to become the loop-closer: kills while invisible refresh
Invisibility's COOLDOWN (Predator refreshes the duration, Mastery hands
the active back — together they chain darkness between fights). Shadow
Flurry stopped being a strike train — knockback shoved the target out of
reach of strikes 2-4 — and is now ONE strike at x3.0, stacking
multiplicatively with Deathblow's x1.5 (a stealth-opener build peaks at
x4.5 with First Strike on top). Root cause treated too: daggers now
knock back at HALF strength (ModifyVariable on the 6-arg knockback
overload — the 5-arg delegates into it, one hook covers all).
Attack speed on daggers is unreadable (they're already faster than the
click), so: Frenzy died, its two cells became Expose ranks 2-3 (Expose is
now 10/20/30% under half health), and Bloodrush became Strength I/II for
4s on kills while invisible. Swift Shadow (invisible movement speed —
underpowered per playtest) became a sneak-penalty refund: 50%/100% of
sneaking's speed loss back, via a flat ADD on the SNEAKING_SPEED
attribute (0.3 base → 0.65 → 1.0), invisibility not required.

**Umbral Mastery shelved (2026-07-17).** Its second idea (kills while
invisible refresh the invis cooldown) duplicated Predator's fantasy and,
worse, would have refreshed Last Shadow's deliberately long 180s clock —
the user flagged both before it ever shipped to a real playtest. The node
is now a pickable placeholder at the crown while a distinct effect is
designed. Candidate directions parked here: (a) Shadow Step/other-tree
synergy (crown = cross-tree), (b) invisibility grants absorption on cast,
(c) breaking invisibility by attacking leaves a 2s afterimage that mobs
target instead. Last Shadow's own cooldown may also want to grow past
180s once the tree settles.

**Elementalist crown unlocked (2026-07-17).** Playtest found Blizzard (and
symmetrically any outer capstone) walling the player off the crown: the
capstones were the only bridge upward and exclusivity locked the
neighbouring one. New flame: 26 nodes (second tree past the 23 standard,
after Protector's 25) — a four-capstone tongue row [Flamethrower]
[Meteorite][Glacial Spike][Blizzard] under a side-by-side crown
[Arcane Power][Spellweaver], every tongue touching a crown cell; plus one
explicit edge Permafrost→Glacial Spike, whose only grid neighbour among
the ice capstones was Blizzard (the same exclusivity deadlock, one level
down). Focused Mind grew to 4 ranks (+0.5 regen/s each) rising up the
flame's core, making a pure-element path exactly 15 points: root 1 +
passives 7 + capstone 1 + crown 2 + mind 4.

**First three Sketchpad edits ported (2026-07-17).** The user redrew three
constellations in tools/ (the new local sketch tool) and the edits ported
mechanically from tools/edits/:
- elementalist-new: the capstone tongue row shifted right one, so Vaporize
  and Permafrost each touch both of their element's capstones naturally —
  the explicit Permafrost→Glacial edge (the "weird line") is gone. FM4
  tucks diagonally at (5,3).
- markman-new: the bow slimmed to 5 wide — string perfectly straight up
  the right (root, Piercing Tips, Reload x4, Pinning x2, Combustion, Snap
  Shot, Focus at the tip), stave arcing left (Acrobatics, Nimble, Swift,
  Seeker Arrow), Conservation as the arrow crossing at the bulge.
- new-shadow: the moon compacted to 11 tall with three-cell tips — senses
  flank the active at the bottom, Umbral Mastery sits between the
  capstones at the crown, touching both. The inner arc's one-row gap kept
  its explicit bridge (now First Strike II → Bloodrush I).
Also from sketch comments: Night Eyes now snuffs out the moment the sneak
ends (ours is the ambient instance; potion night vision untouched), and
Invisibility wears a drawn icon — the bad-omen face with its glare
recolored to two glowing orange eyes — which needed the sprite-icon path
wired through ShadowNodes/TreeNodes and onto the cooldown bar tile.

**The wand economy (2026-07-17).** The Seeker had a battle-mage hole: a
sword-or-bow build could take spells as free bonus DPS. Two closures, per
the user's design: (1) mana regeneration is ZERO while any weapon or
shield sits in either hand — swords tag (greatswords and daggers
included), spears, mace, bow, crossbow, shield, trident; (2) every spell
now requires a wand in the main hand — any wand casts anything, but the
specialist wands pay bonuses. Four new craftables join the basic wand:
Apprentice (amethyst + 2 sticks; -10 all spell costs), Blaze (2 blaze
rods; -15 fire costs, x1.5 fire damage), Breeze (2 breeze rods; -15 frost
costs, x1.5 frost damage), Holy (glowstone + 2 sticks; -15 Priest costs,
x1.5 HEALING — the undead-harm side stays base). Discounts apply as flats
before Spellweaver's multiplier; meteor power, both channels' bolts and
Glacial Spike all inherit their school's wand power. Textures ride the
double-rod diagonal trick with per-school sparks (blaze/breeze wands are
literally two rods of their material). Spell tiles on the cooldown bar
now also dim when no wand is held and price with the held wand's
discount live.

**Meteorite refund + rod economics verdict (2026-07-17).** User ruling on
the Blaze/Breeze recipes: staying cheap-by-materials — blaze rods gate on
a nether fortress, breeze rods on a cartographer-grind trial chamber; the
acquisition IS the cost. Meteorite now always drains the ENTIRE pool
(impact math keeps the full number) and refunds the cost-modifiers' share
after the cast: refund = spent − elementCost(spent), so a 200-pool cast
with a Blaze wand spends 200, hits like 200, and hands 15 back (Kindling
and Spellweaver stack into the refund the same way they discount any
other fire spell).

**Meta round (2026-07-17): wand models fixed, creative tab, level toast,
blocky iron greatsword.** The four new wands rendered as checkerboards —
the item-model DEFINITIONS existed but the models/item/*.json they point
at didn't (26.2's two-file item plumbing; magic_wand had both). While
fixing, the wands were redrawn to the user's spec: single vanilla rods
(blaze/breeze rods are already wand-length; shifted down-left a pixel for
tip room) and single stick for apprentice/holy, each wearing its school
at the tip — amethyst crystal, flame, snowflake, golden cross. Only the
basic wand keeps the doubled-stick look.

New: an "Archetypes" creative tab (FabricCreativeModeTab.builder — 26.2
renamed the fabric item-group module) holding all 7+7 weapons, 5 wands,
all 8 mana potion stacks (drink + splash), both skill tokens and both
tomes. Skill Token x45 added (SkillTokenItem parameterized; instant max).

Archetype level-ups now toast like Specialities skills — same advancement-
frame layout, archetype colour and icon, "Level x -> y" — driven client-
side by watching the synced XP attachment (no new packet; -1 sentinel so
the join sync never toasts). Reaching 45 plays UI_TOAST_CHALLENGE_COMPLETE.

Iron greatsword: the 32px hi-res sprite replaced by a 16px vanilla-derived
one in the daggers' dialect — blade lengthened two steps up its own
diagonal, widened one perpendicular step with highlights flattened to the
core colour, internal seam outlines dissolved, outer outline repaired,
grip stretched. Other materials keep hi-res until the user approves the
look; the greatsword-based node icons (First Blood, Heavy Blows, Decimate)
still carry the old hi-res art and will want regeneration if this lands.

**Wizard and Priest trees built (2026-07-17, while the user was away).**
The last two placeholder trees are real; PlaceholderNodes is deleted. All
numbers are DRAFTS for the balance pass.

WIZARD — the arcane artillerist, 23 nodes. The staff reads as one cast,
bottom to top. Butt cap: Magic Missile flanked by MANA SHIELD 2 (25%/rank
of damage taken drains mana instead of health, 2 mana per point — wand in
hand only, same rule as regen). Shaft: FORCE 3 (+0.5 heart missile damage
per rank). Crossbar: CLARITY (-5 cost), SIPHON (missile kills refund 15),
ECHO (20% free twin missile). Neck: RANGE 2 (+8 blocks each, 16→32).
Diamond: ARCANE ORB (+25 max mana) cradled between the two faces — left
finishes the wounded (VELOCITY +30% speed, OVERWHELM +20% vs hurt →
Seeker Missile), right opens fights (CONCUSSION shove, SHATTERPOINT +30%
vs full → Lance). Crown arc over the head, fed by either capstone:
MIND WELL 2 (+20 mana each), FLOW 2 (+0.5 regen each), ARCHMAGE (+20%
missile damage) at the tip. Full path = exactly 15 without the crown, so
finishing costs shared-spine cuts — deliberate.

PRIEST — mercy or wrath, 23 nodes. Shaft: LUMEN 2 (+0.5 heart to BOTH
sides), GRACE (-10 cost), RADIANCE (+1.5 burst radius), DEVOTION (+0.5
regen), FERVENT CAST at the junction (x1.5 speed, flatter arc — the lob's
QoL). Arms: MERCY 2 (+1 heart heal each) to Renewal; WRATH 2 (+1 heart
undead damage each) to Benediction. The halo loop is open through the
junction WITHOUT a capstone: BEACON (+25 mana), VITALITY 2 (+1 max heart
each, transient MAX_HEALTH modifier in SeekerTicker), AEGIS 2 (casting
shells the caster in absorption via the vanilla EFFECT — the Crusher's
MAX_ABSORPTION lesson dodged entirely), CLEANSING LIGHT 2 (healed targets
lose poison/wither, then everything harmful), MIRACLE 2 (10%/rank free
cast), ASCENDANT (+25% both sides) at the circle's top.

Implementation notes: missile/holy costs moved into missileCost()/
holyCost() helpers shared by casts and HUD tiles; missile conditional
damage (Overwhelm/Shatterpoint are mutually exclusive per hit — full
health takes one, wounded the other), knockback as a deltaMovement push
(the proven Haymaker pattern, not the knockback() overload); Siphon rides
a new SeekerCombat AFTER_DEATH hook keyed on the projectile's mode; Echo
twins are free and slightly inaccurate; Miracle rolls before the spend.

**Art + wizard polish from playtest (2026-07-17).** All seven greatswords
now use the vanilla-derived blocky sprite (the iron trial approved); the
three Slayer icons that composed the old 32px art regenerate from the new
16px sprite doubled NEAREST — and Heavy Blows was redrawn per request as
the greatsword with a semi-transparent rotated echo behind it,
bladestorm-style. Wands: the basic wand takes the small amethyst-crystal
look, and Apprentice/Holy carry ~4x ornaments (a faceted crystal, a
golden starburst) on a stick shifted down-left for room. Wizard balance:
Velocity swapped into the Lance/opener face (it made no sense beside the
deliberately-slower Seeker Missile) with Concussion taking its place on
the finisher face — and Concussion now also applies Weakness I for 3s on
hit. Sketchpad TREES table now reads WizardNodes/PriestNodes, so the tool
renders the real trees for the user's upcoming edits.

**Playtest fixes round (2026-07-17, evening).** Skill Token x45 was a
checkerboard: the original token borrows vanilla's experience_bottle MODEL
outright (no archetypes texture exists), and the x45 definition pointed at
a texture that was never there — it now borrows the same vanilla model.
Wand ornaments reseated: both big tips now start with a socket pixel ON
the stick's tip (they floated before), and the apprentice crystal doubled
again (~7px faceted gem) so it reads at a glance against the basic wand's
small crystal. All five wands became WandItem with hover tooltips: a
shared "Casts Seeker spells" line plus the wand's own bonus line. Wand
bonuses confirmed main-hand-only by construction (elementCost/wandPower/
holy heal all read getMainHandItem) — an offhand wand adds nothing, so
dual-wielding never doubles. Mana bar now greys out entirely (dark
overlay) while a regen-blocking weapon is held in either hand — the "why
is my mana stuck" answer on screen. Greatswords: durability = 3x their
material's sword (three ingots, three lives); vanilla-matching before.

**Naming, HUD and highlight round (2026-07-17, late).** Wand ladder
renamed (display only, ids untouched): the starter is now the APPRENTICE
WAND, its amethyst upgrade the ADEPT WAND — the old names read backwards
as a progression. The Adept's sprite was rebuilt mounted ON the
unshifted stick's tip (the shifted version dropped the stick's base off
the canvas). Mana bar: the grey OVERLAY died; blocked regen now greys the
orbs (blit tint) and the number instead — and the vanilla air bar is
wrapped via HudElementRegistry.replaceElement to step up one row whenever
the mana bar is visible, so underwater no longer hides the orbs. Tree
screens: actives wear a blue 1px halo, capstones purple, via a new
TreeNodes.kind() (Slayer/Crusher's capstone-actives count as capstones).
Sketchpad: node descriptions are now shown and EDITABLE while recording
(separate box from the Claude-comment; saved as desc/descChanged per node
and echoed in the .txt), and an Edits… manager lists saved sketches with
Load (continue editing) and Delete.

**Wizard redesign ported from the Sketchpad (2026-07-17,
Wizard-redisign sketch — the first edit to use description editing).**
New staff: a long haft (root, Force x3, Range x2), a two-row grip block
holding the whole casting economy (Clarity/Arcane Orb/Echo over
Concussion/Mana Shield 1/Velocity), the Mana Shield tower rising to
Siphon INSIDE the diamond, conditional-damage faces to the capstones,
crown arc unchanged on top. Mechanics from the sketch comments:
- Magic Missile: 4-tick (200ms) gap between casts, a hand-swing
  animation per cast, and its knockback halved dagger-style (the
  knockback mixin now also catches MISSILE-mode projectiles).
- Concussion: the shove is gone — pure Weakness I for 3s.
- Echo: 20% -> 25%.
- MIND WELL REBORN: no more +max mana — every 8th (rank 1) then every
  4th (rank 2) missile cast leaves EMPOWERED at +1.5 hearts (cast
  counter attachment; the empowered cast chimes high; Echo twins are
  never empowered so the counter stays honest).
- Seeker Missile: confirmed at -33% speed, desc updated.
All six sketch-edited descriptions ported into lang.

**Priest halo rework ported from the Sketchpad (2026-07-17,
priest-edits sketch), plus a Wizard tower flip.** The ankh's grid didn't
move — only what lives on it. The halo now splits by intent: protection
climbs the LEFT arc (Aegis 1-2 into Sanctuary 1-2), wrath climbs the
RIGHT (Vitality 1-2 into Miracle 1-2), Ascendant still at the top.
- SANCTUARY (was Cleansing Light — the user found full-cleanse too
  strong AND the old name stale): casting shells FRIENDLY targets in
  1/2 absorption hearts, the same AEGIS_TICKS shell Aegis puts on the
  caster. The cleanse mechanic is gone entirely.
- VITALITY repurposed: no more +max hearts (the SeekerTicker attribute
  block died with it) — holy light now sets undead ablaze 3/6 seconds,
  lit BEFORE the hit so killing blows still burn.
- MIRACLE repurposed: no more free-cast roll — holy light lays
  Weakness I/II on the undead for 6 seconds.
- New icons: Sanctuary = enchanted golden apple, Vitality = soul torch,
  Miracle = fermented spider eye.
- Desc-only sketch edits ported: Fervent Cast, Mercy, Wrath, Aegis all
  read plainly now ("Holy light flies faster and farther.").
Wizard: the user reconsidered the tower — Mana Shield should be CHEAP,
not deep. New climb bottom-up: MS1 -> MS2 (grip rows) -> Siphon ->
Arcane Orb (diamond), so the defensive shells sit two steps off the
haft and the mana engine is what costs the climb.

**Immolation & Judgement, wizard sketch #2, mastery cosmetics, Amnesia
(2026-07-17, second chunk).** Vitality and Miracle kept their reworked
mechanics but lost their stale names — they're Immolation and Judgement
now (user agreed to the rename suggestion). Wizard tree, per the
new-edits-for-wizard sketch: the mana engine moved EARLY (Flow 1-2, Mind
Well 1-2, Arcane Orb in the grip rows; single Mana Shield at 50% right
off the haft) and the casting economy moved LATE (Clarity, Velocity,
Echo, Concussion form the crown arc under Archmage). Mana Shield rank 2
was deleted outright — the tree is 22 nodes now; the sketch's parked
cell at (0,6) was the drag-tool's parking spot, not a real node. Siphon
sits alone in the diamond's heart. Missile visuals: only Mind Well's
empowered missile keeps the white END_ROD trail (normal missiles fly
clean) and it renders 1.5x via a new SpellProjectileRenderer reading a
synced DATA_EMPOWERED flag. Mind Well (on empowered cast) and Echo (on
twin) now flash the crosshair proc display — first wizard families
there, item-icon branch added to ProcIndicatorHud. Tree screen: the
header now uses SkillPoints.tier() — a 45 Seeker reads "Oracle" — and
once level 45 AND every point is spent the two progress bars give way to
a centered "Full potential unlocked — you are now a true %s." line in
the archetype color. Amnesia potions for the SMP respec path: awkward +
red mushroom = Amnesia I (refunds every node), + glowstone = Amnesia II
(also forgets the archetype choice; banked XP STAYS both ways — these
are respecs, the creative Reset button remains the full wipe). Strictly
drinkable by construction: the instant effect fires only when
source == target, which only the drink path produces — the splash/
lingering/arrow forms vanilla brewing derives are inert, so no thrown-
bottle griefing. Sketch .json files are deleted after porting from now
on (.txt kept as archive).

**Lance ring + Holy Light palette (2026-07-17).** Lance missiles now
trace a spinning 6-spark END_ROD ring perpendicular to flight, at
exactly the radius pierceSweep damages (MISSILE_PIERCE_INFLATE + half
the hitbox) — the AOE reads as a travelling circle drilling a faint
helix. Holy Light's particles (trail AND burst) follow the build: gold
dust by default, the old green GLOW kept for Renewal, orange dust for
Benediction — the capstones are exclusive so the colors never fight.

**Bake-off icons wired for in-game A/B (2026-07-17).** Both rendered
sets ship under textures/node/test/{sonnet,opus}/{tree}/ and the three
intellect trees draw them through a new TreeNodes.testSprite path.
TreeNodes.TEST_ICON_SET ("sonnet" first) is the one-string switch for
the swap between the user's two screenshot passes. Parked for later,
user idea: remake the OTHER trees' default item icons too — Crusher's
fists branch literally uses BRICKS.

**Icon verdict locked + spell feel pass (2026-07-17 evening).** The
bake-off verdict is now per-tree in TreeNodes.iconSet(): Sonnet's
Wizard and Priest (vanilla-anchored won; Opus-Priest "looks good, just
not in Minecraft"), Opus' Elementalist (best of all six, great
in-game). Spell feel round, all user calls:
- Flamethrower: knockback GONE (the stream was pushing victims out of
  the stream — FLAME_BOLT joins the knockback mixin at x0) and the flat
  BLAZE_SHOOT screech became a pitch-jittered FIRECHARGE_USE whoosh per
  bolt.
- BLIZZARD REWORKED (WoW-mage style, "the Meteorite's AOE opposite"):
  no longer a channel. Press-cast at the targeted block (meteor-style
  pick, 32 blocks): a BlizzardZones storm rakes the 3x3 for
  BLIZZARD_TOTAL_DAMAGE 20 over 8 seconds in 1-second pulses of 2.5,
  Enemy-only, wandPower x arcane scaling baked at cast. One storm per
  caster, recasting moves it; zones are transient (restart/logout/
  dimension-hop clears). Cost: NEW BLIZZARD_COST 75 flat through
  elementCost (user never specced a price — flagged for playtest).
  Visuals: fast-falling ICE block-crumb "icicles" + snowflake haze;
  cast = BREEZE_SHOOT, pulse = POINTED_DRIPSTONE_LAND (a literal
  falling-icicle impact). SNOW_BOLT mode deleted with the channel.
- Holy Light's burst: glass shatter -> BELL_RESONATE + brighter chime.
Kept for the record: Shatter does NOT amplify blizzard pulses (zone
damage isn't a SpellProjectile); mention if the ice build feels flat.

**Round-2 flip + Blizzard buffs + capstone lockout (2026-07-17 late).**
Cutpurse A/B flipped to Opus (user: "from the preview Opus made much
better job, it seems" — Sonnet pass screenshotted first). Blizzard,
after the user's power-level read vs Meteorite: 5x5 (HALF_WIDTH 2.5),
ZERO knockback on pulses (a static pulsing flag around the hurtServer
loop, read by the knockback mixin — the storm was juggling mobs out of
its own square), and an audible icicle impact every 10 ticks instead of
20 (off-beat impacts between damage pulses, lighter crumb burst).
BUG FIX, Elementalist capstones: the four capstones were exclusive only
within their element pair, so a fire pick left both ice capstones
buyable. Now any owned capstone locks the other three (openers stay a
separate pair).
Verify pass on this chunk caught a real one: the cast runs from the
packet queue, so the first observed tick-end already has game time
advanced — the head pulse (remaining == 160) could never fire and the
storm delivered 7/8 of its damage with a dead second up front. Fixed
with a +1 tick pad on endTick. Known cosmetic edge, deliberately left:
thorns retaliation landing DURING a pulse also has its knockback
suppressed (the flag window covers the whole hurtServer chain).

**Assassin rebalance ported from Sketchpad (2026-07-17,
assassin-rebalance sketch).** The dagger constellation narrowed to a
5-wide guard (23 nodes still) and the centre-line active perks were
replaced:
- Adrenaline Rush + Opportunist DELETED. Their old effects (Speed II
  after the blink; -3s cooldown) are gone with them.
- CRIPPLING_POISON new: dagger hits apply Slowness I/II for 4s
  (CRIPPLING_SLOW_TICKS 80), a third coating alongside Venom/Blight on
  the guard row. Icon = fermented spider eye + grey down-chevrons.
- DEATHBLOW reworked+renamed to TWIN_FANGS: Shadow Step's strike now
  also lands the OFF-HAND dagger at x0.5 efficiency
  (TWIN_FANGS_OFFHAND_FACTOR 0.5). Math: result *= 1 + 0.5*(offDmg/mainDmg)
  using ModItems.daggerSwingDamage() — identical daggers reproduce old
  Deathblow's exact x1.5, a bare/non-dagger off-hand gives nothing, so
  the node nudges toward dual-wielding for the fantasy. Applied only to
  the STEP_STRIKE_AT-stamped hit. Icon = two parallel iron daggers.
Node reshuffle: Razor Edge 1-2 + Flense 1 + Crippling Poison 1-2 on the
guard; Razor Edge 3 / Flense 2 / Venom 1 on the base row; Expose climbs
the left edge to Shadow Flurry, Venom/Blight climb the right to Momentum;
Twin Fangs at the point. Full desc rewrites ported to lang (per-rank
values spelled out: 8/16/24%, 7/14/21%, 10/20/30%, 50/100%). Icon
verdict for Assassin is opus; both opus AND sonnet fallback sets updated
(stale adrenaline_rush/opportunist/deathblow removed) so no flip can
miss a texture. Crusher's own ADRENALINE_* Tuning constants are
unrelated and untouched.

**Momentum one-shot cooldown fix (2026-07-17).** Bug: Shadow Step that
one-shot its target didn't reset the cooldown despite Momentum. Cause:
strike()'s attack() applies lethal damage synchronously, so AFTER_DEATH
(and Momentum's removeAttached(SHADOW_STEP_READY_AT)) fired INSIDE
strike() — but shadowStep() then re-armed the cooldown on the line after
strike(). Fix: arm the cooldown BEFORE strike(), so a kill's Momentum
reset always writes last and wins. Non-kill and non-Momentum cases
unchanged.

**Shadow rework ported from Sketchpad (2026-07-17, shadow-new-edits).**
Crescent grid reshaped (still 23 nodes) — outer arc pushed to the frame,
inner arc's gap still bridged by the explicit (4,4)-(4,6) edge.
- NIGHT_EYES DELETED (night vision at the start felt too strong). Its
  node became UMBRAL_SIGHT rank 2, so Umbral Sight is now 2 ranks:
  hostiles highlighted within 8 (r1) / 16 (r2) blocks, gated on
  sneaking only (was "sneak or hide"). Radius = UMBRAL_SIGHT_RADIUS *
  rank. NIGHT_EYES_TICKS removed.
- UMBRAL_MASTERY (the last placeholder) renamed to NIGHT_STALKER, icon
  kept (ENDER_EYE; sprite files renamed umbral_mastery.png ->
  night_stalker.png in both test sets). New effect: while invisible AND
  night (overworld clock 13000-23000, any dimension), Jump Boost II +
  Slow Falling I, dropped within 1 tick when invis/night ends.
  dropOurAmbient() guards on isAmbient && duration<=NIGHT_STALKER_TICKS
  (40) so a beacon's longer ambient Jump Boost / a potion isn't
  clobbered.
- SWIFT_SHADOW BUG FIX: it was using ADD_MULTIPLIED_TOTAL on
  SNEAKING_SPEED (0.3 base), so rank 2 only reached ~0.51 — user noticed
  it didn't fully negate the sneak penalty. Both apply() branches now use
  ADD_VALUE, so 0.35/rank lands rank 1 at 0.65 and rank 2 at 1.0 = full
  walking speed (matches "equal to just running around, not sprinting").
- DIM_PRESENCE: 15%->20% per rank AND now gated on sneaking (was
  always-on). FIRST_STRIKE: 30%->25% per rank. STILLNESS/BLOODRUSH/
  INVISIBILITY/PREDATOR/LAST_SHADOW/CLEANSING_VEIL: desc-only reword to
  the sketch text (mechanics already matched).
All descs ported verbatim from the sketch.
Verify pass caught a Night Stalker teardown bug: granting Jump Boost II
over a beacon's Jump Boost I buries the beacon effect as a vanilla
hidden-effect, and removeEffect() then discards the whole chain (nuking
the beacon/leaping-potion buff, which only returned ~4s later). Fixed by
NOT removing — the effects now use a 5-tick duration re-asserted each
tick and simply lapse when the hunt ends, so vanilla restores any buried
effect on expiry. Teardown stays ~immediate (0.25s).

**Readable ink on the grey panel (2026-07-17, user screenshots).** The
archetype pastels are tuned for dark grounds (node fills, the progress
bar trough) but washed out as TEXT on the C6C6C6 window — Nemesis green
was near-invisible. New VanillaUi.ink(): darkens a color 10% at a time
until it clears 5:1 WCAG contrast on WINDOW_BODY (vanilla's own LABEL
grey sits at ~6:1), keeping the hue; cached per color. Applied to the
tree screen's four colored-text sites (constructor title, per-frame
header, mastery line, points-unspent). Results: strength E06C4A->753825
(5.2:1), agility 7FCF9F->2D4E3A (5.4:1), intellect 7A9CEE->38486F
(5.3:1). Node fills and bar fills keep the bright pastels — they sit on
dark grounds where the pastels are right.

**Marksman port + Acrobatics trigger + color revert (2026-07-18
overnight batch, part 1).** Marksman sketch: bow-branch reshuffle
(Swift Flight to the base, Acrobatics mid, Nimble Draw high), all descs
to the user's plain per-rank style — every mechanic already matched the
sketch numbers, so no Tuning changes. ACROBATICS BUG: vanilla reads the
sprint key via isDown and never consumes clicks, so presses piled up
during normal running and the moment a draw began our consumeClick loop
ate the backlog — roll fired instantly. Now the backlog is drained on
the draw's first tick and only presses made DURING the draw count.
(Same staleness pattern exists on Shield Rush's sprint-while-blocking —
unflagged, unfixed, watch for it.) Colors: the ink() darkening read as
plain black in-game (user); reverted to the bright archetype colors
drawn WITH the picker's drop shadow (header, mastery line, points line).
VanillaUi.ink() deleted.

**Description unification pass (2026-07-18 overnight, part 2).** Six
Sonnet agents rewrote every Brawler + Seeker node desc into the
Cutpurse house style (plain, per-rank values spelled out, effects by
their Minecraft names, cooldowns/costs at the end) — 45 rewrites
applied, user-authored sketch texts left untouched. The agents also
surfaced 9 desc-vs-code mismatches; maintainer resolutions (descs
follow code):
- Bash cooldown is really 6.8s (16t swing floor + 120t ability), not
  the claimed 6s -> desc now says 7s; Quick Recovery desc drops the
  stale "6-second" figure.
- Reflection only halves ARROW damage (other projectiles return at full)
  -> desc says so.
- Bloodlust only triggers on sword/greatsword kills, not "any melee"
  -> desc says so.
- Arcane Power / Shatter said "all your spells" but only cover the
  elementalist's own paths (Shatter additionally skips meteor impact +
  blizzard pulses) -> descs scoped to "fire and ice spells" /
  "fire and ice projectiles".
- AEGIS/SANCTUARY: the user-authored "1/2 hearts of absorption" is
  IMPOSSIBLE as written — vanilla Absorption quantizes at 2 hearts per
  level, and the code grants Absorption I/II = 2/4 hearts. Descs now say
  2/4 hearts. FLAG FOR USER: if 1/2 hearts was the real intent the
  mechanic needs a custom absorption amount, not amplifiers.
- CODE FIX: EARTH_SHATTER_REFUND_PER_RANK 0.33F -> exact third, so rank
  3 refunds 100% of Quake's cooldown as designed instead of 99%.

**XP curve 1-45 (2026-07-18 overnight, part 3 — agent-designed, judge-
verified, implemented).** The flat 160/level died. New system:
- COST(L) = 15 + round(1.2 L^2), exact integer math, precomputed tables
  with a boot assertion (cum(45)=38,349, cum(15)=1,713 — 5.3x the old
  7,200 total). Level 15 ~ a few in-game days of normal play; levels
  31-45 are 69% of the whole road; one raw dragon kill ~ cum(31).
- ADVANCEMENT RATE: banking is scaled by 1 + 0.025 x (completed
  non-recipe advancements), capped x3.0 at 80 (~63% of vanilla's 126
  display-bearing advancements — thorough, not completionist). Counted
  via ServerAdvancementManager.getAllAdvancements() filtered on
  display().isPresent(); cached in the new synced ADVANCEMENT_COUNT
  attachment, recounted on join and in PlayerAdvancementsMixin at
  award/revoke RETURN (recipe unlocks skipped) — bank() stays O(1).
- Applied AT BANKING TIME: ARCHETYPE_XP is an append-only ledger of
  already-scaled XP; past XP keeps its historical rate, revokes never
  deflate. Judge-simulated: normal progressing player hits 45 in
  ~14-18h real time, landing at/near the first dragon kill (12,000 raw
  x rate closes the last gap in one event); frozen-stage farm grinder
  ~18-34h; manual mob-hunting ~100h. Grinding legal, progression wins.
- ensureBankCoversSpent() on join: any future retune can never strand
  committed points. grantLevels() jumps by CUM (tokens bypass the
  multiplier deliberately).
- Tree screen short bar: "1850/2445 XP (x2.40)" with a hover tooltip
  explaining the rate. Long bar unchanged.
Constants XP_PER_ADVANCEMENT/MAX_XP_MULTIPLIER/curve coefficients all
in SkillPoints for retuning; cap is vanilla-tuned (datapacks grow the
advancement pool — revisit per modpack).

**Picker rework (2026-07-18 overnight, part 4 — agent-designed, judge-
synthesized, implemented).** The cards grew from 96 to 140 tall
(panel 253): name / five-word ROLE line / crest band / an always-
visible ABILITY ROW of the archetype's three actives. Ability slots are
real vanilla slots (18px single, 36px fork pair — Decimate|Bladestorm,
Quake|Haymaker, Fireball|Ice Blast, fork order pinned by
TreeNodes.pickerActives, not constellation order); hover previews the
REAL node tooltip (name + desc) via the new shared VanillaUi.nodeIcon /
TOOLTIP_WIDTH, so picker icons are pixel-identical to the tree screen,
bake-off sets included. Fork tooltips stack both actives divided by a
"Pick one path — the other locks." hint. Copy: prompt/confirm now warn
the choice is deliberate-until-Amnesia-II (not "permanent"); blurbs are
playstyle-descriptive, plus new role lines. The agility/intellect cards
get a code-built three-item mini-collage of their sub-tree symbols
(pose-scaled fakeItems blooming with the same hover ease as real art) —
enabled by SubTree icon fixes: Assassin now IRON_DAGGER (the stale "no
dagger in vanilla" comment predated our own daggers), Shadow
PHANTOM_MEMBRANE, Wizard MAGIC_WAND (these also improve tree-screen
section labels). AND the fork hint forced an honesty fix: the
Quake<->Haymaker lockout the TreeNodes doc promised "when it lands" was
never wired — Crusher's capstones are now genuinely exclusive like
every other pair, with "Locks out X." lines in both descs.

**Brawler icon bake-off, round 3 (2026-07-18 overnight, part 5).** Six
agents rendered Protector/Slayer/Crusher against the fresh descriptions
with the Opus Cutpurse sheets + the original hand-made icons as
references. NOT wired in-game — unlike rounds 1-2 the Brawler trees
already wear hand-made canon, so the choice is three-way (keep original
/ sonnet / opus) and waits for the user. Sheet-read: Opus edges all
three (durability-bar straps, cracked-chestplate Sunder, clock+down-
arrow Recovery), Sonnet found THE shield-shaped vanilla sprite (empty
shield-slot placeholder) that Opus's door-like plate lacks — a
Protector merge (Sonnet's glyph + Opus's overlays) is the likely
endgame. Both models independently chose a golden heart for Battle
Trance. sonnet-slayer's two filenames were normalized to the enum
(first_blood->firstblood, heavy_blows->heavy).

**Morning feedback round (2026-07-18).** Text readability attempt #3:
VanillaUi.chipText — the bright archetype color on a small INSET_DARK
plate with shadow (ink read black; shadow-on-grey washed out; the plate
supplies the ground the picker cards had all along). Applied to the
tree header, mastery line and points-unspent. NEW "?" legend at the
panel's top-right: hover explains blue ring = actives, purple ring =
capstones (mutually exclusive), and the Amnesia respec brew (red
mushroom), including that it costs levels. AMNESIA NOW PRICED (user
musing made concrete, FLAGGED FOR TUNING): I keeps 2/3 of levels
(AMNESIA_LEVEL_KEEP), II wipes archetype AND all levels — a full
restart. Picker: prompt/confirm no longer name Amnesia ("changing your
mind later has a price" — discovery stays in-game); ability previews
trimmed to ONE per tree (Bladestorm not Decimate, Quake not Haymaker,
Fireball not Ice Blast) so each card shows exactly three 18px slots.
BRAWLER ICONS: Opus wired live for all three strength trees (test
sprites outrank the hand-made sprite()/overlay() paths while the
comparison vs the user's screenshots of the originals runs); proc
display follows. Sonnet-protector ruled out by the user (wrong shield
sprite).

**Strict description pass, round 2 (2026-07-18).** The user rejected
round 1's remnant poetry with a precise standard; agents re-ran with it
encoded: all rank values in one run always ("50/100%", never "per
rank"/"then"), no role announcements ("Capstone:" etc — the ring says
it), no lockout mentions (the "?" legend owns that), no discovery
spoilers (Decimate's torch-sweeping stays a secret), multipliers always
x2.0-style, weapon gates as flat conditions ("With a greatsword: ...").
35 rewrites applied. Agents also caught real content gaps now filled:
Flamethrower never stated bolt damage (1 heart), Meteorite's crater
had no numbers (5 hearts/3 blocks at 100 mana, +1 heart/+0.4 blocks per
20), Glacial Spike's freeze never said 12s, Holy Light never stated its
4-block radius, Aegis/Sanctuary never said 30s, Reinforced Straps
actually a 50% full-skip chance (not a halving) on ANY blocking item.
Maintainer overrides: bash says 7s (true 6.8 — Tuning's own doc calls
it 7), Decimate keeps "in front" (the cone is real), Relentless loses
the word "capstone", and the legend's capstone line now also covers the
fire/ice opener lock since those descs no longer mention it.

**Crusher rework + spell feel + UI polish (2026-07-18, second round).**
CRUSHER (user sketch crusher-rework): grid narrowed to 5 wide; the
handle is now Bare-Knuckle x4 (day-one fantasy): fists +1 heart/rank
(1.0 fist -> 9.0 at full, past an iron sword), mace +0.5 hearts/rank —
one ATTACK_DAMAGE modifier retuned on weapon swap (apply() re-asserts
now). ADRENALINE deleted -> CLINCH (renamed per sketch note): bare
hands give AND take 50/100% less knockback. Received = KNOCKBACK_
RESISTANCE (0..1 ranged attribute — can't go negative, satisfying the
user's stacking worry); applied = the knockback funnel mixin
(Haymaker's send-off is a push() impulse, untouched). ADRENALINE_UNTIL
attachment removed. New clinch.png icon (opus fist + inward arrows).
SPELLS: Fireball/Ice Blast now explode in a 3x3 (ELEMENT_BURST_RADIUS
1.5) on any impact — full payload to everyone in it. Every Seeker cast
swings the wand (flamethrower on channel start). METEORITE reworked to
the user's m = mana/100 formula: 8 hearts across 5x5 at 100 mana, and
damage, area, particle count, LOUDNESS and the rock's rendered size
(new synced DATA_VISUAL_SCALE; renderer scales; FX capped x4, numbers
uncapped) all scale together. Cast sound: ghast scream ->
TRIDENT_RIPTIDE_3 whoosh, also scaled.
UI: the "?" is a real vanilla Button now; clicking pins the legend
tooltip (anchored under the button) until clicked again. Legend split:
capstone line purple and capstone-only; NEW gray line for the
Elementalist's one-element commitment; reset line gray. MANA NODES
UNIFIED: wizard Arcane Orb + priest Beacon -> "Mana Pool" (Beacon's
icon); wizard Flow + priest Devotion + elementalist Focused Mind ->
"Mana Flow" (Devotion's icon) — lang names + live icons + enum
fallbacks (CONDUIT / LAPIS). Keys/enums unchanged, display only.

**Missile FX bake-off + variant A wired (2026-07-18).** Three Opus
agents designed full audiovisual identities for Magic Missile (sprite,
empowered sprite, trail/sound spec, all javap-verified); A and B
independently converged on "the Arcane Mote" — vanilla nether_star
silhouette recolored to the amethyst palette with a white-hot core,
radially symmetric so the billboard renderer has no wrong angle.
VARIANT A is live: two new never-obtainable items (MAGIC_BOLT /
_EMPOWERED) the projectile wears in flight; cast chime lifted to a
LIGHT jittered register (0.4 vol, pitch 1.35 +-0.15 — the old fixed
0.5 pitch at 4 casts/sec was a drone) with a deep AMETHYST_RESONATE
bloom under empowered casts only (the eyes-closed tell); a soft
AMETHYST_HIT tick on connection (missiles had NO hit sound); trails:
near-nothing dim violet dust every 2 ticks for the rank and file
(sprite carries the glow), bright dust every tick for empowered (+
sparse END_ROD) and for the homing capstone (curve legibility), lance
ring tinted violet for family cohesion. Variants B and C staged in
notes/art/missile_fx/ — flipping = swap 2 textures + retune the 3
sound/trail sites.

**Elementalist v2 icons (2026-07-18).** Two agents re-rendered the tree
to the settled vanilla-first standard (round one's winner predated it
and "stood out from all the other 8 trees"). OPUS V2 WIRED LIVE
(test/opus/elementalist overwritten; old set archived in notes/):
fire charge fireballs, real flint-and-steel/campfire/blaze sprites,
snowfall blizzard, fire-charge-skimming-water vaporize, falling-rock
meteorite. Sonnet v2 staged in test/sonnet/elementalist — iconSet's
one-word flip A/Bs the two. focused_mind is byte-identical to the
unified Mana Flow (devotion) icon in both, cmp-verified. The cost-down
grammar (mana orb + minus on kindling/chill/spellweaver) now matches
across both candidates.

**Morning feedback round 2 (2026-07-18).** Legend "?" is click-only now
(no hover) and the element-commitment line only appears on Seeker
trees. Keybinds moved to their own "Archetypes" controls section
(KeyMapping.Category.register(archetypes:archetypes), lang key
key.category.archetypes.archetypes). CREATIVE RESET keeps banked
levels (user call: tree-hopping in creative shouldn't cost a x45 token
each time) — clear() now forgetNodes + archetype only; Amnesia II
remains the level wipe. COOLDOWN TRACKER reworked: docked to the RIGHT
of the hotbar (x = width/2 + 91 + 4, bottom-aligned, tracks the
centred hotbar at any scale), hotbar-sized 22px slots with native 16px
icons — and the icons are now the NODE icons via VanillaUi.nodeIcon +
new TreeNodes.indexOfFamily, so the tracker always matches the tree
screen (bake-off sets included). Crusher early-game verdict from the
user: fists at 9.0 damage is fine — unenchantable, no netherite path,
"good early game, mostly irrelevant in end-game."
PENDING USER DECISIONS: (a) missile FX B/C review before sound edits
(user direction: cast whoosh + louder looped glint from the projectile
until hit/expiry); (b) whether Elementalist keeps BOTH base spell and
capstone once the capstone is picked, capstone on a 4th keybind — my
recommendation delivered: yes, split them (see chat).

**The 4th ability key + capstone split, FX round (2026-07-18 midday).**
CAPSTONE SPLIT SHIPPED (user approved the recommendation): ABILITY_KEYS
is 4 slots now (defaults G/H/B/V, lang "Ability 1-4", room to grow);
slot 3 = the Elementalist capstone. castElementalist casts the BASE
element spell always (fireball/ice blast keep their AOE role after the
capstone); new castElementalistCapstone dispatches Meteorite / Glacial
Spike / Blizzard, and the Flamethrower channel streams on key 4. The
cooldown tracker shows base + capstone tiles for Elementalists.
GLACIAL SPIKE = THE ICE FINISHER (user synergy spec): its own
single-target GLACIAL_SPIKE mode, no burst — x2.0 Ice Blast damage
cold, x10.0 against slowed/freezing targets (SLOWNESS effect or frozen
ticks), still freezes 12s. Deliberately NOT run through shattered() —
the multiplier IS the chill payoff. Prime with AOE blast on G, execute
with spike on V.
METEORITE: violent radial knockback, level = ceil(mana/100) (I at 100,
III at 250), push impulse 0.4+0.5/level horizontal + rising vertical.
MISSILE FX: switched to variant B's sprites; per user direction the
cast is now a subtle jittered BREEZE_SHOOT whoosh and the chime GLINT
sings from the projectile itself (every 3 flight ticks, 0.5 vol
normal / 0.7 lower-pitched empowered) until hit/expiry; resonate bloom
still marks empowered casts; hit tick kept.
HOLY LIGHT: cast = whoosh (BREEZE_SHOOT, replacing the bowstring-like
splash throw), burst = immediate XP-orb pling + high chime (the bell
resonate's slow bloom read as a delayed ring — user).
PICKER COLLAGES (user layouts): Cutpurse = crossed daggers front,
mirrored crossbow upper-left + drawn bow (bow_pulling_2) upper-right;
Seeker = mirrored glacial-spike node icon upper-left x flamethrower
node icon upper-right, mana regeneration potion in front. Orientations
are best-guess from vanilla sprite directions — EYEBALL IN GAME.

**Collage fix (2026-07-18).** Two bugs, both mine: (1) mirrored blits
via pose.scale(-1,1) flip the quad winding and the GUI renderer CULLS
them — every mirrored sprite (crossbow, second dagger, glacial spike)
was invisible. Mirrors are PRE-BAKED assets now (textures/gui/collage/
crossbow_left, dagger_left, flame_right) — never mirror at draw time.
(2) I'd read "crossing" as corner placement for the Seeker pair; and
the node icons' native orientations were the OPPOSITE of my assumption
(flamethrower points NW natively, spike NE) — the mirror now goes on
the flamethrower, spike used as-is. Compositions are PIL-previewed at
exact blit offsets before shipping from now on — that render would
have caught all of this pre-commit.

## Epic tier: levels 46-60, Oracle trees (2026-07-19)

Post-45 progression shipped as a framework plus the first two epic trees
(sketched by the author in the sketchpad, implemented by agents):

- Cap 45 -> 60 on the same quadratic curve (CUM[60] = 89,472; the 45 anchors
  unchanged). Levels 46-60 grant EPIC points — a separate pool, 15 total,
  5-point cap per epic tree. Peak tier name still lands at 45; "full
  potential" flavor needs 60 + no spendable epic points left.
- Epic trees are SubTree entries flagged epic with base<->epic mapping
  (ELEMENTALIST<->ORACLE_ELEMENTALIST, WIZARD<->ORACLE_WIZARD). SubTree.of()
  still returns base trees only. Tree screen: up/down switcher, top-right.
- Ability 5 (N) = Lightning Strike: 32-block targeted bolt, 40 dmg, 150 mana,
  Chain x1/2/3, Recurrence x1/2/3 extra strikes, Overcharge x2.0, Tempest
  AOE capstone (30 mana per extra target, 64-bolt cap). Cosmetic-only
  LightningBolt carries the look; damage is ours (indirectMagic).
- Ability 6 (M) = Magic Armaments: toggle channel, 50 mana + 10/s. Conjured
  diamond-tier sword swaps with the wand (wand parked in a copyOnDeath
  attachment; dirty channels torn down on JOIN/death/reset). Summoned items
  self-void anywhere outside a live channel (inventoryTick + ENTITY_LOAD).
  Mind over Matter +2/4/6 dmg for +10/20/30 upkeep; Magic Armor mana->
  absorption 0.5/1.0 capped 10/20; Blink 8-block empty-swing teleport;
  Levitation flight (interop-safe mayfly bookkeeping); Warding purges
  harmful effects; Spellbow capstone conjures ammo-free arrows.
- Agent-invented names pending author review: Tempest, Overcharge,
  Recurrence, Levitation, Warding, Spellbow.

Open by design, revisit before advertising the tier: only 10 of 15 epic
points are spendable (two trees) and Strength/Agility have none — ship the
remaining epic trees or gate epic banking; Lightning's 150 cost exceeds the
100 base pool (mana-column on-ramp is arguably the point); Blink rides the
unthrottled MeleeSwingPayload (anticheat posture); Tempest+Chain stacks
bolts in tight packs (capstone fantasy until proven degenerate).
