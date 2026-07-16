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
