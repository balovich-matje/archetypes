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
| Brawler | **Claymore** | stick + more material than a sword | Bigger and slower, more damage per swing. |

Notes on each, honestly:

- **Wand is still a soft gate** and we should keep our eyes open about it. Two sticks
  in the first minute is about as ungated as an item gets, but a Seeker who drops
  their wand down a ravine stops being able to cast. The cantrip staying bare-handed
  is what keeps this honest — **if casting ever requires the wand, the rule is broken.**
- **Dagger is the model.** It gives up damage to gain a situational advantage; it is
  never strictly better than a sword. Copy this shape for anything later.
- **Claymore needs care.** "Costs more, hits harder" is a straight vertical upgrade —
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

### Open: does an archetype *reshape* power rather than add it?

The sharper version: Seeker casts well **and swings worse**. That is how every real
class system holds balance, and it makes the choice cost something — which is what
makes it mean something. It is also the version players complain about. Undecided.

### Open: does Specialities' x2 damage survive?

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
