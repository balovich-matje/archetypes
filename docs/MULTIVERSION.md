<!-- The port design of record. Copied verbatim from the design pass of 2026-07-31;
     edit it here from now on, not in the scratchpad it came from. -->

# Archetypes multi-version port — design

> **Binding authority.** This document sets the plan; **Skill Proficiencies'
> `specialities/docs/MULTIVERSION-CONVENTIONS.md` sets the rules**, and it binds this
> repo too — §1 bottleneck files, §2 active-node discipline, §3 the FROZEN predicate
> vocabulary, §4 `//?` syntax and the JSON prohibition, §5a–§5l the shared-implementation
> rules, §6 R-10/R-11/R-16/R-17/R-18/R-20/R-22, §7 release. Where the two disagree, the
> conventions file wins and this one gets fixed.
>
> **Status: Stage 0 (§5.1) is DONE**, on `workspace`, seven commits, still a
> single-target 26.2 Fabric mod. What Stage 0 measured that changed this document is
> recorded in "Stage 0 outcomes" immediately below; the body of the document is the
> design as written and is otherwise unedited.

## Stage 0 outcomes — what the pre-port refactor actually found

| Lane | Landed | Deviation from §5.1 as written |
|---|---|---|
| 0-A | `state/{StateKey,WireCodec}`, `ModAttachments` → `ModState` (74 keys), `platform/{ArchetypeStore,FabricArchetypeStore}`, 259 call sites in 38 files | 74 registrations, not 75; 259 call sites, not 253. `ArchetypeStore.remove` returns the previous value rather than `void` — `REFLECT_AIM` is read-and-cleared in one step in the arrow hit handler, and every platform's remove already hands it back. `StateKey` carries a dense `index` so the platform keeps handles in a flat array; these are read inside the damage funnel. |
| 0-B | `state/WireId`, `platform/{Net,FabricNet}`, 33 networking sites | `clientReceivers` IS implementable on Fabric here, which SP's shape does not expect: a sink schedules its own hop onto the client thread, so the registration never needs `Minecraft` — the one client class `src/main` genuinely cannot name. `FabricNet.ClientSide` isolates `ClientPlayNetworking` in a nested holder. |
| 0-C | `platform/{Platform,FabricPlatform}`, 9 `FabricLoader` sites | R-C4 closed: both HUD rows now read `SpecialitiesBridge.hudShift()` live. Needed the Skill Proficiencies **1.6.0** mavenLocal artifact — the pin was still `1.5.0`, which has `HUD_SHIFT` but not `hudShift()`. |
| 0-D | 44 handler shells + `@Unique …Impl` bodies across 4 mixins | 44, not 28 — the count in §0 is LivingEntityMixin's alone. `ServerLevel level` is forced off every `hurtServer` impl and derived in-body; 14 impls do that. |
| 0-E | full descriptors on all 72 `method =` targets | 72, not 77 (§0's 77 counts injection POINTS, several of which share a target). Two needed hand-resolution: a `@Mixin(targets = "…")` string target and an `<init>`. |
| 0-F | `/archetypes dummy` deleted outright; `DamageTrace.ENABLED` dev flag | — |
| 0-G | Java-17 audit | **§5.1's audit was wrong about two rows.** `sealed` is NOT a violation: `javac --release 17` compiles it (JEP 409 finalised sealed classes in 17), so conventions §5e's exclusion is toolchain-driven — `OracleStrikes.Pending` stays. And pattern-matching `switch` is NOT zero: `SpellProjectile` labels a null case in three switches, which is Java 21, compiles to an `invokedynamic` against `java.lang.runtime.SwitchBootstraps` (absent on Java 17) and would have broken the 1.20.1 node. Fixed by hoisting the null out of the switch. |

**Gate tooling built in Stage 0 and reused by every later stage** (in the session
scratchpad, `arch-gate/`): `snap.sh` (per-class `javap -c -p -constants` with constant-pool
indices normalised, plus per-resource sha256), `cmp.py` (three buckets: resources, class
shape, instruction diffs), `smoke.sh` (26.2 dedicated server with Skill Proficiencies 1.6.0,
PAL and fabric-api alongside, `mixin.checks` + export + injection counting, positive tag and
item probes each with a bogus control), `funnel.sh` (§5.9 gate 5 — the `hurtServer` handler
order out of the mixin export, 32 calls, `archetypes$traceBegin` first and `archetypes$flense`
behind Skill Proficiencies' multipliers).

**Still open from §7, unchanged by Stage 0:** all six questions. Stage 0 needed none of them.

---

# Archetypes → 7 targets: implementation design

**Framing:** every section is split **`SAME AS SP`** (copy the playbook, cite it, don't re-derive) vs **`ARCHETYPES-SPECIFIC`** (no SP precedent, or SP's precedent is the wrong shape at this scale).

Authorities cited, not restated: `specialities/docs/MULTIVERSION-CONVENTIONS.md` (§1 bottleneck files, §2 active node, §3 frozen predicates, §4 `//?` syntax + the JSON prohibition, §5a–§5l shared-implementation rules, §6 R-10/R-11/R-16/R-17/R-18/R-20/R-22, §7 release), `specialities/docs/MULTIVERSION.md` §2/§4/§4.1, `specialities/stonecutter.properties.toml`, `specialities/build.{fabric,neoforge,forge}.gradle.kts`, `mc-modding/CLAUDE.md`.

---

## 0. Scale, measured — why this is not "SP again, bigger"

| Metric | Skill Proficiencies | Archetypes | Ratio |
|---|---|---|---|
| Java files / LOC (`src/main`) | 73 / 7,608 | **116 / 22,513** | 2.9× |
| Java files / LOC (`src/client`) | (in above) 2,874 | **36 / 5,093** | 1.8× |
| **Total** | 73 / 10,482 | **152 / 27,606** | **2.6×** |
| Mixin classes | 15 | **28** (19 common + 9 client) | 1.9× |
| Injection points | 31 | **77** (43 `@Inject`, 16 `@ModifyVariable`, 8 `@ModifyReturnValue`, 5 `@WrapOperation`, 3 `@ModifyExpressionValue`, 2 `@WrapMethod`) + 12 accessor/invoker | **2.5×** |
| MixinExtras injectors (R-10 exposure) | 1 `@WrapMethod` | **18** | 18× |
| Attachment registrations | ~5 | **75** (47 `syncWith`: 31 targetOnly, 16 all) | 15× |
| `(AttachmentTarget)` call sites | 1 file (seam) | **253 occurrences across 38 files** | — |
| Payloads | 3 | **11** (10 serverbound, 2 clientbound; `ParrySwing` both directions counted once) | 3.7× |
| `FabricLoader` named outside a seam | 0 | **9 sites / 4 files** | — |
| Custom entities / renderers / render layers / particles / potions / mob effects / keybinds | 0 / 0 / 0 / 0 / 0 / 0 / 0 | 1 / 1 / 3 / 1 / 4 / 3 / 7 | ∞ |

Two conclusions the stage plan is built on:

1. **The playbook transfers ~70% by risk and ~40% by volume.** Everything SP closed (R-16 tags, item-model relocation, R-17 legacy blits, R-10 MixinExtras JiJ, R-18 no-switch builds, R-20 re-rooting contract, R-22 registry window, §5h `Gui` descriptors) transfers *verbatim*. The remaining 30% — the seam that does not exist, the render-state architecture, `BlocksAttacks`, PAL — has no precedent in either repo.
2. **The server smoke gate is materially weaker here than it was for SP.** SP's dedicated-server smoke exercised 15/15 common mixins and most of the mod's behaviour. Archetypes' would exercise 19/28 mixin classes and ~80% of `src/main`, but `src/client` — 5,093 LOC, 9 mixins, the render-state rewrite, 5 PAL drivers, 3 render layers, 6 HUD elements, 2 screens — is untestable headless. **Plan an in-game pass per node family, not one at the end** (SP's Stage-5 item-model bug and Stage-6 missing Forge client bootstrap are both the cost of not doing this).

---

## 1. Workspace mechanics

### 1.1 `SAME AS SP` — the scaffold, copied file for file

Copy and adapt these six, and nothing else changes shape:

```
archetypes/
├── settings.gradle.kts            ← was settings.gradle   (bottleneck)
├── stonecutter.gradle.kts         ← NEW, replaces nothing (bottleneck; root build script)
├── stonecutter.properties.toml    ← NEW                   (bottleneck)
├── build.fabric.gradle.kts        ← was build.gradle
├── build.neoforge.gradle.kts      ← copy SP's
├── build.forge.gradle.kts         ← copy SP's
├── buildSrc/…/neoforge-mutex.gradle.kts  ← copy SP's verbatim (R-14)
├── gradle.properties              ← Gradle options ONLY (strip every version pin)
├── src/                           ← THE shared tree
└── versions/<node>/src/           ← per-node overrides
```

- **Delete `build.gradle` and `settings.gradle`.** `stonecutter.gradle.kts` *is* the root build script; there is no root `build.gradle(.kts)` (conventions §1).
- **`settings.gradle.kts`**: copy SP's verbatim including the `pluginManagement` repositories block (`maven.fabricmc.net`, `maven.kikugie.dev/releases`, `maven.neoforged.net/releases`, `maven.architectury.dev`, **`maven.minecraftforge.net`** — the last is not optional, Arch Loom's own buildscript needs `de.oceanlabs.mcp:mcinjector` from it, design R-01/§1.6). Same `match(project, vararg loaders)` helper, same seven `match(...)` lines, `vcsVersion = "26.2-fabric"`, `rootProject.name = "archetypes"`.
- **`stonecutter.gradle.kts`**: copy SP's verbatim. `stonecutter active "26.2-fabric"`, `properties { tags(version, loader) }`, `constants { match(loader, "fabric","neoforge","forge") }`, `dependencies["fapi"] = …`, `stonecutter tasks { order("publishModrinth") }`.
- **Node registration is its own small commit per node, landed before parallel work on that node** (conventions §1, single-writer rule). Non-negotiable — it is what makes the lanes below safe.
- **§2 discipline**: `src/` on disk is always in `26.2-fabric` state; building `:26.2-fabric` never validates `//?` syntax; never run `stonecutterSwitchTo…` in a working session.
- **§5f**: no `//?` in build scripts — plain Kotlin `if (sc.current.parsed >= "…")`.
- **`//?` in `.json` is crash-level** (conventions §4). Everything in `fabric.mod.json`, both mixin configs, `data/**` and `assets/**` goes through `processResources` conditioning or a per-node override.
- **§5j**: every new `eachFile`/`filter` needs its decision declared with `inputs.property(...)` in the same edit, or Gradle reports `processResources UP-TO-DATE` and ships the untransformed resources.

### 1.2 `SAME AS SP` — the three `replacements` and the toml

`stonecutter.properties.toml`: copy SP's structure and **every pin verbatim** — they were verified against real maven metadata on 2026-07-25 and nothing about Archetypes moves them.

```toml
mod.id      = "archetypes"
mod.name    = "Archetypes"
mod.group   = "com.archetypes"
mod.version = "1.2.0"          # next release; 1.1.0 is live

deps.fabric_loader = "0.19.3"

[fabric]                        # R-01: bare table, NEVER the toml root, NEVER gradle.properties
loomx.loom_version = "1.17.17"  # Archetypes is on 1.17.13 today — move to SP's pin for parity
```

Then the seven per-node sections, copied from SP unchanged: `mod.mc_compat` / `mod.loader_floor` / `deps.fabric_api` per Fabric node; `mod.mc_range` + `deps.neoforge = "21.1.243"` (floor 21.1.200 — attachment sync, and Archetypes needs it *more* than SP: 47 synced attachments vs 1); `mod.mc_range` + `deps.forge = "47.4.22"` + `deps.forge_floor = "47"` + `deps.mixinextras = "0.5.4"`.

**`mod.loader_floor` findings transfer and get sharper:**

| Node | Floor | Why (Archetypes-specific reinforcement) |
|---|---|---|
| `1.21.1-fabric` | `>=0.16.3` **not** fabric-api's `0.15.11` | SP took this floor for **one** `@WrapMethod`. Archetypes has **18 MixinExtras injectors** including 2 `@WrapMethod` (needs ≥0.4.0) and 3 `@ModifyExpressionValue`. Loader 0.15.11 bundles MixinExtras 0.3.5 → cryptic mixin-apply crash, not a version message. |
| `1.20.1-fabric` | `>=0.16.10` (fabric-api's own) | bundles 0.4.1, sufficient. |
| `1.20.1-forge` | **R-10 MANDATORY** | LexForge bundles no MixinExtras (measured in SP: `forge-1.20.1-47.4.22-userdev.jar` config.json lists only `org.spongepowered:mixin:0.8.5`). All 18 injectors fail without `jarJar(implementation("io.github.llamalad7:mixinextras-forge:${deps.mixinextras}"))`. |
| `1.21.1-neoforge` | — | MixinExtras 0.5.3 is a platform library on the compile classpath, no JiJ. |

Add the two `replacements` blocks from SP's controller **unchanged** (`ResourceLocation`→`Identifier`, `net.minecraft.Util`→`net.minecraft.util.Util`, both at `>=1.21.11`), plus **one Archetypes-specific third** (§4.1 below).

### 1.3 `ARCHETYPES-SPECIFIC` — the dependency block, per node

This is where Archetypes' node script genuinely diverges from SP's. `build.fabric.gradle.kts`:

**a. Fabric API modules — SP's nine become twelve, and three of them swap by version.**

Reuse SP's `fun fapi(vararg modules: String)` helper (compile-only modules + `modLocalRuntime` umbrella for dev runs — the 2026-07-26 launcher lesson).

Always present on all five Fabric nodes:
```
fabric-data-attachment-api-v1   fabric-networking-api-v1        fabric-entity-events-v1
fabric-lifecycle-events-v1      fabric-events-interaction-v0    fabric-command-api-v2
fabric-screen-api-v1            fabric-rendering-v1             fabric-particles-v1
fabric-object-builder-api-v1    (EntityType/attribute builders)
```

Three conditional swaps:
```kotlin
if (sc.current.parsed >= "26.1") fapi("fabric-creative-tab-api-v1")  // creativetab.v1  [SP has this]
else                            fapi("fabric-item-group-api-v1")    // itemgroup.v1

if (sc.current.parsed >= "26.1") fapi("fabric-key-mapping-api-v1")   // KeyMappingHelper  [NEW]
else                            fapi("fabric-key-binding-api-v1")   // KeyBindingHelper

// brewing is a THREE-way split, not two — see §4.1
```

**b. PAL — the dependency *configuration* forks, not just the coordinate.** See §2.

**c. SP-interop — `modCompileOnly`, per node.** See §3.5.

**d. Java level.** SP's `requiredJava` ladder is copied verbatim (`>=26.1 → 25`, `>=1.20.5 → 21`, else `17`). Archetypes compiles at `release = 25` today and has never been checked against 17.

**e. Loader-axis source-set exclusions must land BEFORE the first loader file** (conventions §5e-ter — SP's script says so and it is right):
```kotlin
sourceSets["main"].java.exclude(
    "com/archetypes/platform/NeoForge*.java", "com/archetypes/platform/Forge*.java",
    "com/archetypes/platform/ArchetypesNeoForge.java", "com/archetypes/platform/ArchetypesForge.java")
sourceSets["client"].java.exclude(
    "com/archetypes/client/NeoForge*.java", "com/archetypes/client/Forge*.java")
```
Naming rule the anchored globs depend on: a one-loader file is named after its loader; `Forge*` does not match `NeoForge*`.

**f. `processResources` — copy SP's two transforms verbatim, plus two new ones.**

| Transform | Predicate | Status |
|---|---|---|
| R-16 `tags/item/` → `tags/items/` | `< "1.21"` | **SAME AS SP** — Archetypes has `data/minecraft/tags/item/swords.json` + 5 own item tags. Copy the `eachFile` block character for character (including the `inputs.property("legacyTagDir", …)`). |
| Item model **definitions** → legacy models | `< "1.21.4"` | **SAME AS SP** — 20 definitions in `assets/archetypes/items/`. Critically: **Archetypes already ships the legacy geometry** at `assets/archetypes/models/item/` (18 files), so the conversion is *simpler* than SP's — for the 18 that have a twin, the definition just needs deleting; the other 2 need SP's regex rewrite. Anchor on `^assets/[^/]+/items/[^/]+\.json$` and run it **after** the tag rename (SP's comment explains why: a loose `/items/` test would mangle the renamed tag JSONs). |
| `fabric.mod.json` `depends` block | per node | **NEW** — see below |
| `player_animation_library` entrypoint/dep stripping | `< "1.21.1"` | **NEW** — see §2 |

`fabric.mod.json` today hard-declares four per-node values and **none can be `//?`-conditioned** (Fabric's `JsonReader` runs `lenient = false` → unloadable mod). Templatise exactly as SP does, via `expand(metadataProps)`:
```json
"depends": {
  "fabricloader": "${loader}",
  "minecraft":    "${minecraft}",
  "java":         ">=${java_floor}",
  "fabric-api":   "*"
}
```
and the PAL line is *removed by the same `filter { }` line-stripping mechanism SP uses for the `modmenu` entrypoint* on the two 1.20.1 nodes. Both `*.mixins.json` get `expand("java" to "JAVA_${requiredJava.majorVersion}")` — Archetypes' configs hard-code `JAVA_25` today, which is a 1.20.1 boot failure.

**Trap to carry (conventions §4, measured on both SP loader nodes):** anything through `expand` has its `#` comments treated as Groovy template source. A literal `${…}` or a bare `Foo$Inner` in a comment fails the COPY. Put the warning in both metadata files.

### 1.4 `SAME AS SP` — branch strategy

Identical recipe, no deviation:

- Work on **`workspace`**. `26.2-fabric` is the active node and the VCS reset point.
- `main` is **fast-forwarded to `workspace` at release points only**. The desktop launcher runs `:26.2-fabric:runClient` off `main` — the `~/Desktop/"Specialities + Archetypes Dev 26.2.command"` script must be updated in the same commit that lands the workspace, because Archetypes will no longer have a root `runClient`.
- Archetypes has no `26.1` branch to archive (SP did) — nothing to tag.
- Commits stay local-only unless the user says push (standing instruction).
- **Bottleneck-file rule**: `settings.gradle.kts`, `stonecutter.gradle.kts`, `stonecutter.properties.toml` are single-writer; node registration is its own commit.

### 1.5 `ARCHETYPES-SPECIFIC` — three *additional* de-facto bottleneck files

SP's parallel model works because SP's shared files are small. Archetypes has three hot files every lane would touch:

| File | Size | Touched by |
|---|---|---|
| `src/main/java/com/archetypes/mixin/LivingEntityMixin.java` | 1,177 lines, **28 handlers** | every damage-funnel lane, every node |
| `src/main/java/com/archetypes/ModAttachments.java` | 740 lines, **75 registrations** | every seam lane, every node |
| `src/client/java/com/archetypes/client/ArchetypesClient.java` | 356 lines, **3 registries + 8 HUD + 7 keys** | every client lane, every node |

**Mitigation is Stage 0 (§5.1): pre-split all three before the workspace exists**, so later lanes touch thin, independent shells. This is the single largest compression lever in the plan.

---

## 2. THE PAL DECISION, per target

### 2.1 The matrix (deps lane, verified against Modrinth API + `javap` on the six cached jars in `scratchpad/pal/`)

Project `player-animation-library` (`ha1mEyJS`) declares loaders `["fabric","neoforge"]` and its lowest `game_version` is **1.21.1**.

| Node | PAL | Pin (version **id**, not number) | Jar namespace | Dependency configuration | Source fork |
|---|---|---|---|---|---|
| `26.2-fabric` | ✅ | `1.2.5` `OQqtEQC6` (Merged) | mojmap, **no** `Fabric-Mapping-Namespace` | plain `implementation` | baseline |
| `26.1-fabric` | ✅ | `1.2.5` `SdKAeB6x` (Merged) | mojmap, no header | plain `implementation` | **none** — `IAnimatedAvatar`/`AvatarAnimManager`/`invoke(Avatar)` identical to 26.2 |
| `1.21.11-fabric` | ✅ | `1.1.9` `BXYewCJb` (Fabric) | **`intermediary`** | **`modImplementation`** ← mandatory | none at source level |
| `1.21.1-fabric` | ✅ | `1.1.5` `FkO8Scek` (Fabric) | **`intermediary`** | **`modImplementation`** | **2-line fork ×5 files** |
| `1.21.1-neoforge` | ✅ | `1.1.5` `ReDTdA0C` (NeoForge) | mojmap | plain `compileOnly` + `additionalRuntimeClasspath` | same 1.1.x fork |
| `1.20.1-fabric` | ❌ **none exists** | — | — | — | **RED** |
| `1.20.1-forge` | ❌ **no LexForge build at any MC version** | — | — | — | **RED** |

**Two traps that fail silently, not loudly:**

1. **The dependency configuration forks and getting it backwards is a silent mis-remap.** Today's `implementation "maven.modrinth:player-animation-library:g8XDqDTi"` works *only because* the 26.x "Merged" jars ship mojmap-named classes with no namespace header. The 1.21.11 and 1.21.1 Fabric jars declare `Fabric-Mapping-Namespace: intermediary` (their `PlayerAnimationController` references `net.minecraft.class_742`/`class_2960`/`class_11890`), so on those two nodes Loom **must** remap them → `modImplementation`. Putting the 26.x mojmap jar through `modImplementation` would have Loom remap it as if it were intermediary.
2. **The coordinate must be the version *id*, not the version number.** `1.1.5` is duplicated across Fabric and NeoForge; only `FkO8Scek` / `ReDTdA0C` disambiguate. Keep `exclusiveContent`-filtered `maven.modrinth` (already in Archetypes' `build.gradle`).

**The 1.21.1 API fork (2 lines × 5 files), coinciding exactly with the vanilla `Avatar`→`AbstractClientPlayer` rename** — one `//? if >=1.21.11` arm, not two:

| 1.2.x / 1.1.9 (26.x, 1.21.11) | 1.1.5 (1.21.1) |
|---|---|
| `IAnimatedAvatar` | `IAnimatedPlayer` |
| `AvatarAnimManager` | `PlayerAnimManager` |
| `PlayerAnimationFactory…invoke(Avatar)` | `…invoke(AbstractClientPlayer)` |
| `PlayerAnimationController(Avatar, …)` | `PlayerAnimationController(AbstractClientPlayer, …)` |
| base `HumanoidAnimationController` | base `AnimationController` |

Identical across all: `FirstPersonMode`, `FirstPersonConfiguration(b,b,b,b)`, `PlayState`, `triggerAnimation(id)`, `isActive()`, `getTriggeredAnimation()`, `stop()`. `setFirstPersonMode`/`setFirstPersonConfiguration` return `void` on 1.1.x vs the controller on 1.2.x — **source-compatible** because every call site uses them as statements.

### 2.2 `USER DECISION REQUIRED` — the 1.20.1 fork, stated per node so it can be vetoed

**Option A — port the drivers to KosmX `playerAnimator` 1.0.x** (project `gedNE4y2`; loaders `["fabric","forge","neoforge","quilt"]`; the upstream PAL was forked from). Both 1.20.1 builds exist and download:
- Fabric `yDqYTUaf` = `1.0.2-rc1+1.20-fabric`, maven `dev.kosmx.player-anim:player-animation-lib-fabric:1.0.2-rc1+1.20` (HTTP 200 from `https://maven.kosmx.dev/`)
- Forge `xe2EVE6q` = `1.0.2-rc1+1.20-forge`, `mods.toml` modId `playeranimator`, versionRange `[1.20,)`

What survives: `PlayerAnimationFactory.ANIMATION_DATA_FACTORY` with the same `invoke(AbstractClientPlayer)` shape; `FirstPersonMode` + `FirstPersonConfiguration`. What does **not**: there is no `PlayerAnimationController`, no `AnimationStateHandler`, no `PlayState`, no `triggerAnimation`. 1.0.x is `ModifierLayer<IAnimation>` + `setAnimation(new KeyframeAnimationPlayer(PlayerAnimationRegistry.getAnimation(id)))` + `replaceAnimationWithFade(…)`. **Different paradigm → a rewrite of the driver body, not a rename.** Cost: one `//? if <1.21.1` arm covering ~620 lines across the 5 `*Animations.java` files — the largest single source fork in the whole port.

**Option B — no-op animation seam on the 1.20.1 pair.** Whole compilation units inside a `//?` block (the `client/mixin/GuiMixin.java` trick, conventions §4), PAL dropped from `fabric.mod.json`/`mods.toml`, the 9 JSONs shipped unused (harmless) or excluded.

**Features lost under Option B, per node, so the user can veto individually.** All ten are **cosmetic** — every driver reads either vanilla's own broadcast swing state or a synced attachment the server already owns; none gates damage, cooldown, or resource cost:

| Layer | Animation | Lost on `1.20.1-fabric` | Lost on `1.20.1-forge` | Severity |
|---|---|---|---|---|
| `DaggerAnimations` (1003) | `dagger_stab` | ✔ | ✔ | **lowest** — falls back to vanilla arm swing, already correct and already broadcast |
| `SlayerAnimations` (1000) | `bladestorm`, `decimate`, `decimate_charge`, `quake_charge` | ✔ | ✔ | **highest** — Decimate and Quake lose their wind-up read, the clearest telegraph the mod has |
| `ProtectorAnimations` (1004) | `shield_sweep_{left,right,dual}` | ✔ | ✔ | high — Shield Sweep loses its entire visual |
| `NightAnimations` (1001) | `dark_ritual` | ✔ | ✔ | medium — channel pose gone |
| `ElementalistAnimations` (1002) | `flame_channel` | ✔ | ✔ | medium — channel pose gone |

Net: **10 of 10 animations, 5 of 5 layers, on 2 of 7 nodes.** First-person casters lose the most — the poses are what put these abilities on the caster's own screen via `FirstPersonMode.THIRD_PERSON_MODEL`.

**Recommendation: ship Option B first, treat Option A as a follow-up.** Reasons: (i) B is a bounded `//?` arm that unblocks both 1.20.1 nodes today; (ii) A's decisive unknown is untested — *do the 9 authored `player_animations/*.json` load unchanged on the 1.0.x loader?* PAL is a fork of playerAnimator and the `"version": 3` torso/body trap documented in `DaggerAnimations.java` is a 1.0.x-lineage trap, so reuse is *likely* — but a failure means re-authoring 10 animations, not porting a driver; (iii) `1.20.1-forge` is the node where PAL never existed on any loader, so if the answer is "1.20.1 ships without poses", **both 1.20.1 nodes converge on one code path** and the fork cost halves.

**If Option A is taken**, add `maven("https://maven.kosmx.dev/")` to the node script's repositories and run experiment **E-PAL-1** (§6) *before* committing.

---

## 3. Seams

### 3.1 `ARCHETYPES-SPECIFIC` — `ArchetypeStore` must NOT copy `SkillStore`'s shape

SP's `SkillStore` is one named method per piece of state — correct for ~5 items. **At 75 registrations / 47 synced / 253 call sites across 38 files, one method per attachment is 150 interface methods and 450 implementations.** That is not a seam, it is a transcription error waiting to happen.

**Design: a typed key-value seam.** The key table lives in *shared* code with zero fabric-api imports; the seam implementation builds the loader's own registry from it at init.

```java
// com/archetypes/state/StateKey.java — SHARED, zero net.fabricmc / loader imports
public record StateKey<T>(String id, Class<T> type, Codec<T> persist /*null = transient*/,
                          StreamCodecLike<T> wire /*null = unsynced*/, Sync sync, boolean copyOnDeath) {
    public enum Sync { NONE, TARGET_ONLY, ALL_TRACKING }
}

// com/archetypes/platform/ArchetypeStore.java — the seam (mirrors SP's INSTANCE form exactly)
public interface ArchetypeStore {
    //? if fabric {
    ArchetypeStore INSTANCE = new FabricArchetypeStore();
    //?} elif neoforge {
    /*ArchetypeStore INSTANCE = new NeoForgeArchetypeStore();
    *///?} elif forge {
    /*ArchetypeStore INSTANCE = new ForgeArchetypeStore();
    *///?}

    void register(List<StateKey<?>> keys);           // called once from common init
    <T> @Nullable T get(Entity target, StateKey<T> key);
    <T> void set(Entity target, StateKey<T> key, T value);
    <T> void remove(Entity target, StateKey<T> key);
    <T> boolean has(Entity target, StateKey<T> key);
    void resyncAll(ServerPlayer player);             // JOIN / dimension change replay
    void syncOnStartTracking(Entity tracked, ServerPlayer viewer);  // ALL_TRACKING replay
}
```

Then `ModAttachments.java` (740 lines of fabric-api) becomes **`ModState.java`** — 75 `StateKey` constants, portable to every node with **zero `//?` blocks**, and `FabricArchetypeStore` is one ~200-line file with SP's `>=1.20.5` fork inside it. Every one of the 253 `(AttachmentTarget) x` casts becomes `ArchetypeStore.INSTANCE.get(x, ModState.FOO)`.

**Consequence for the whole plan:** the 253-site refactor is done **once, on Fabric-26.2 only, in Stage 0**, with a single node to regress against and no `//?` syntax in play. Every later node then inherits it free. This is the compression lever.

**The `ALL_TRACKING` scope is the largest genuinely-new infrastructure in the port.** 16 keys are `AttachmentSyncPredicate.all()` — synced to every tracking client so *other* players' renderers can read them (`BULWARK_ACTIVE`, `ARMOR_HIDDEN`, `BLADESTORM_END`, `MARKED_BY`, `SHIELD_SWEEP_AT`, `DECIMATE_SWING_AT`, `DECIMATE_INSTANT_AT`, `QUAKE_CHARGE_END`, `SPELLBOW_ARROW`, `DEADEYE_ARROW`, `DEADEYE_END`, `RADIANCE_END`, `NIGHT_FORM_SINCE`, `NIGHT_CHANNEL_END`, `NIGHT_SUNLIT`, `FLAME_LAST_TICK`). SP synced one attachment, target-only, and hand-rolled the legacy branch. There is no template anywhere for `all()`:
- Below `>=1.20.5` (both 1.20.1 nodes): fabric-api 0.92.11 has a persistent builder and **no sync at all** — no `syncWith`, no `AttachmentSyncPredicate`. `TARGET_ONLY` scales from SP's example; `ALL_TRACKING` needs a broadcast payload + start-tracking replay (`EntityTrackingEvents.START_TRACKING`, present on 0.92.11).
- On both loader nodes: NeoForge attachments sync (≥21.1.200), Forge 1.20.1 has capabilities and no sync at all.

**Recommendation: build the `ALL_TRACKING` fallback ONCE, in shared code, on top of the `Net` seam** — a `archetypes:state_sync` payload carrying `(entityId, keyId, blob)` plus a `START_TRACKING` replay — and have every implementation that lacks native `all()` sync (Fabric <1.20.5, Forge) delegate to it. One implementation, three consumers.

### 3.2 `ARCHETYPES-SPECIFIC` — `Net` is bidirectional, and the interface is mercifully simple

SP's `Net` is clientbound-only by design (its javadoc explains why: a `src/main` seam cannot reach client-only API, conventions §5g). Archetypes has **10 serverbound + 2 clientbound**, so the seam must widen.

**Measured, and it makes this easy:** all 11 payload records carry only `int` and `String` — `ActiveAbilityPayload(int slot)`, `BuyNodePayload(String subTreeId, int node)`, `ParrySwingPayload(int ticker)`, `PassiveProcPayload(String subTreeId, String family)`, `PickArchetypePayload(String archetypeId)`, and six unit records. **Zero registry-bound data**, so the seam needs no `RegistryFriendlyByteBuf` at its boundary and no `StreamCodec` in its signature.

```java
public interface Net {
    // INSTANCE: same three-arm BLOCK chain as SP (§4 — the inline `elif` cannot chain)
    void registerAll();                                                  // common init
    void sendToServer(WireId id, Consumer<FriendlyByteBuf> writer);      // client → server
    void sendToClient(ServerPlayer to, WireId id, Consumer<FriendlyByteBuf> writer);
    void onServerbound(WireId id, BiConsumer<ServerPlayer, FriendlyByteBuf> handler);
    void clientReceivers(Map<WireId, Consumer<FriendlyByteBuf>> sinks); // handed down from client init
}
```

`clientReceivers` copies SP's exact trick and for the exact same reason: NeoForge's `PayloadRegistrar.playToClient(TYPE, CODEC, handler)` and Forge's `SimpleChannel.registerMessage(...)` take the handler as an *argument* to the one registration call, which must run in common init because a dedicated server registers the type too. Nothing in `src/main` names a client type.

**Frozen wire contract** (SP's rule): the `WireId` strings and field order are frozen so a client and server built from different nodes of the same MC version stay compatible.

The 11 payload *records* stay as they are on `>=1.20.5` and are replaced by `FabricPacket`/`PacketType.create` inside `FabricNet` below it — **inside the implementation only**, per SP's design note.

### 3.3 `SAME AS SP` — `Platform`

Small and near-verbatim. `isModLoaded(String)` retires all **9 `FabricLoader` sites in 4 files** (`compat/SpecialitiesBridge:15`, `client/ManaHud:36`, `client/BankedHungerHud:89`, `client/ArchetypesClient:331,352`). Archetypes has no config file, so no `configDir()`. No `skillProviders()` equivalent — Archetypes is the *consumer*, not the host.

**Seam-hygiene rule (conventions §5g), restated for Archetypes:** nothing outside `com/archetypes/platform/` may name `FabricLoader`, `AttachmentTarget`/`AttachmentType`/`AttachmentRegistry`, `PayloadTypeRegistry`, `ServerPlayNetworking`, or any NeoForge/Forge API. A `grep` is the review gate. **Today Archetypes violates this 253 + 41 + 9 = 303 times.**

### 3.4 `ARCHETYPES-SPECIFIC` — loader-event helpers: SP's ten, plus twelve

SP's `platform/{NeoForgeEvents,ForgeEvents}` expose ten: `afterBlockBreak`, `afterDamage`, `allowDamage`, `playerJoin`, `afterRespawn`, `playerLeave`, `endServerTick`, `registerItems`, `creativeTabOutput`, `registerCommands`. Its `client/{NeoForgeClientEvents,ForgeClientEvents}` expose three: `afterScreenInit`, `addWidget`, `afterScreenTick`.

**Reused unchanged:** `playerJoin`, `endServerTick` (14 `ServerTickEvents` sites — the whole ticker pattern), `registerItems` (**R-22 applies identically** — `WandItem`/`MagicSwordItem`/`MagicBowItem`/`SpellcastingTomeItem`/`SkillTokenItem`, and `Item.<init>` asking for an intrusive holder is a *boot crash* on both loaders), `creativeTabOutput`, `registerCommands`, and all three client screen events (the bookmark tab is the same surface).

**Twelve additions, each its own arm in `NeoForgeEvents`/`ForgeEvents` or their client twins:**

| # | Fabric API used | Sites | NeoForge | Forge | Notes |
|---|---|---|---|---|---|
| 1 | `ServerLivingEntityEvents.ALLOW_DEATH` | 2 | `LivingDeathEvent` (cancellable) | `LivingDeathEvent` | **R-20 applies hard** — "cancel" means the entity survives at current health, *not* that damage was voided. Carries Last Shadow / cheat-death. |
| 2 | `ServerLivingEntityEvents.AFTER_DEATH` | 4 | `LivingDeathEvent` (post) | same | SP has `afterDamage`, never `AFTER_DEATH`. |
| 3 | `ServerEntityEvents.ENTITY_LOAD` | 2 | `EntityJoinLevelEvent` | same | True Shot arrow empower, stray-conjured-item void. |
| 4 | `ServerEntityEvents.ENTITY_UNLOAD` / tracking | — | `EntityLeaveLevelEvent` / `PlayerEvent.StartTracking` | same | needed by `ALL_TRACKING` replay (§3.1). |
| 5 | `ServerLifecycleEvents.SERVER_STOPPED` | 1 | `ServerStoppedEvent` | same | Feast bleed-list drain, BlizzardZones. |
| 6 | `UseItemCallback.EVENT` | 1 | `PlayerInteractEvent.RightClickItem` | same | greatsword 2H lock. |
| 7 | `UseBlockCallback.EVENT` | 1 | `PlayerInteractEvent.RightClickBlock` | same | ditto. |
| 8 | `ClientTickEvents.END_CLIENT_TICK` | **8** | `ClientTickEvent.Post` | `TickEvent.ClientTickEvent` + phase check | **NEW CLIENT SEAM FILE on both loaders.** SP has no client tick event at all. Ability-key poll + all 5 PAL drivers. |
| 9 | `KeyMappingHelper.registerKeyMapping` | 7 | `RegisterKeyMappingsEvent` (MOD bus) | `RegisterKeyMappingsEvent` | largest new client seam. |
| 10 | `EntityRendererRegistry.register` | 1 | `EntityRenderersEvent.RegisterRenderers` (MOD bus) | same | `SpellProjectileRenderer`. |
| 11 | `ParticleProviderRegistry` / `FabricParticleTypes.simple()` | 1+1 | `RegisterParticleProvidersEvent` + plain `SimpleParticleType` ctor | same | |
| 12 | `FabricPotionBrewingBuilder` | 2 | `RegisterBrewingRecipesEvent` | `BrewingRecipeRegistry.addRecipe` | plus MobEffect + Potion registration (SP registers neither). |

**R-22 exposure widens.** SP's finding was `Item.<init>`. Archetypes additionally constructs `EntityType` (via `EntityType.Builder.build`), `SimpleParticleType`, 3 `MobEffect` subclasses and 4 `Potion`s at class-init time. **Experiment E-R22-1 (§6)** must establish, per loader, which of those constructors touches an intrusive holder — the failure mode is `<clinit>`-time and therefore far from the call you moved.

### 3.5 `ARCHETYPES-SPECIFIC` — the SP-interop seam and its **blocking** dependency question

**Proven, not assumed: one SP artifact cannot serve seven nodes.** `javap` on the shipped 1.6.0 jars in `specialities/build/libs/1.6.0/`:
- `com.specialities.api.SkillType.iconTexture()` returns `net.minecraft.resources.Identifier` on 26.x, `ResourceLocation` on the two loader jars, `net.minecraft.class_2960` on the three legacy Fabric jars. Same for `icon()` and `displayName()`.
- Manifest namespaces: 26.2/26.1 → `Fabric-Mapping-Namespace: official`; 1.21.11/1.21.1/1.20.1 Fabric → `intermediary`; both loader jars → no header, mojmap.
- The split is **not** a `//?` in SP's source — `SkillType.java` imports `Identifier` unconditionally and SP's controller `replacements { replace("ResourceLocation","Identifier") }` rewrites it below 1.21.11. **Archetypes' `compat/SpellcastingSkill.java` implements `SkillType` and imports `Identifier`, so Archetypes needs the identical replacement rule in its own controller** — that is the Archetypes-specific third `replacements` entry mentioned in §1.2 (it happens to be the same rule SP already has, so copying SP's controller verbatim gets it for free; the point is that it is *load-bearing here*, not inert).

**The stub-vendoring escape hatch is dead.** `compat/SpecialitiesBridge.java` reaches SP **internals**, not just `com.specialities.api`: `SkillManager.get(player).level(...)`, `SkillManager.addXp`, `SkillManager.addLevels`, `Skill.ARCHERY`, `Tuning.recoveryTimeMultiplier(int)`. The whole SP jar must be on the compile classpath of all seven nodes.

**Mechanism, in preference order:**

1. **PREFERRED — extend SP's `publishing` block.** `specialities/build.fabric.gradle.kts:324` gates it `if (sc.current.parsed >= "26.2")`; `build.neoforge.gradle.kts:294` says in terms "No `publishing { }` block: only the 26.2-fabric node owns the mavenLocal coordinate". Change to publish from **all seven** nodes as `com.specialities:specialities:<mod.version>+<node key>` (`1.6.0+1.21.1`, `1.6.0+1.21.1-neoforge`, `1.6.0+1.20.1-forge`, …) **while 26.2 keeps publishing bare `1.6.0`**, so nothing that exists today breaks. The NeoForge (ModDevGradle) and Forge (Arch Loom) nodes need their own `maven-publish` blocks. Archetypes then declares one `modCompileOnly("com.specialities:specialities:${scVersion}")` per node from `mavenLocal()`.
   → **THIS IS AN SP-SIDE EDIT. It touches SP's single-writer publishing wiring and needs the user's go-ahead and its own commit.**
2. **FALLBACK — `modCompileOnly(files("../specialities/build/libs/1.6.0/specialities-1.6.0+<node>.jar"))` per node.** Zero SP changes; works *today* (those 14 jars exist on disk right now). Fragile: `build/` is gitignored, so a fresh clone or a `clean` in SP breaks Archetypes' *configure* phase, and the version directory is hard-coded.

**Configuration per node** — use `modCompileOnly` uniformly (Loom/Arch-Loom remaps intermediary→named and SRG→named; on an already-`official` jar it is effectively identity):

| Node | SP jar namespace | Configuration |
|---|---|---|
| 26.2 / 26.1 fabric | mojmap | `modCompileOnly` (plain `compileOnly` also works — that is what Archetypes does today) |
| 1.21.11 / 1.21.1 / 1.20.1 fabric | **intermediary** | `modCompileOnly` **mandatory** |
| 1.21.1-neoforge | mojmap | `compileOnly` sufficient (MDG has no remapper) |
| 1.20.1-forge | official classes, SRG members | `modCompileOnly` through Arch Loom is correct. Archetypes only *implements* SP interfaces and calls SP's own methods (never remapped), and the only MC types in those signatures are class references — so plain `compileOnly` would very likely also compile. **Verify with a trial compile; do not assume.** |

**Entrypoint mechanism per loader — SP has already decided this, and Archetypes is the first consumer.**
- Fabric nodes: keep `"specialities:skills": ["com.archetypes.compat.SpellcastingEntrypoint"]` in `fabric.mod.json`.
- NeoForge + Forge: `NeoForgePlatform.java` records that `InterModComms` and `ServiceLoader` were both **rejected** (timing; cannot name the owning mod) and that the **implemented** mechanism is a `[modproperties.<modid>]` key — the contributing mod writes `specialities_skills = "com.archetypes.compat.SpellcastingEntrypoint"` under `[modproperties.archetypes]` in its own `neoforge.mods.toml` / `mods.toml`, and SP walks `ModList.get().getMods()` and instantiates the no-arg constructor. LexForge supports `[modproperties]` identically. Those two files are per-node overrides at `versions/<node>/src/main/resources/META-INF/`.
- ⚠ **`NeoForgePlatform.java:33` says that surface "IS A SECOND PUBLISHED API SURFACE AND NEEDS THE USER'S SIGN-OFF BEFORE IT IS DOCUMENTED AS ONE (prep Q5)"**, and SP's `README.md` was deliberately not edited "because Archetypes is the first consumer, so publishing it is the user's decision". **The two loader nodes cannot register Spellcasting without that sign-off.**

**Documentation correction found while scouting:** `mc-modding/CLAUDE.md` states Archetypes "contributes tab via `specialities:skill_tabs` entrypoint". **It does not.** `fabric.mod.json` declares only `specialities:skills`, and `grep -r skill_tabs src/ build.gradle` returns nothing. The interop surface to port is *one* entrypoint, not two. Fix the doc in the same commit that lands the workspace.

**Latent drift risk to fix while the seam is being built:** `SPECIALITIES_SHIFT = 7` is hand-duplicated in `ManaHud`, `BankedHungerHud` (and implicitly `CooldownBarHud`), mirroring SP's `HUD_SHIFT = 7`. Since SP now reads it through `SpecialitiesClient.hudShift()` (conventions §5l, HUD-bar toggle), **the constant can go stale in a way that is invisible to both builds** — SP's toggle can set the shift to 0 at runtime and Archetypes would still offset by 7. Route it through `SpecialitiesBridge` in Stage 0.

---

## 4. Version deltas

### 4.1 Frozen-predicate additions — **new rows ONLY**

SP's table already covers, and Archetypes reuses **verbatim, inventing no synonyms**: `>=1.21.2` (`hurtServer`, `Equippable`, `InteractionResult`), `>=1.21.11` (`Identifier`, `.projectile.arrow`, `HudElementRegistry`/`VanillaHudElements`, `ARGB` vs `FastColor.ARGB32`, the whole pre-1.21.11 `Toast` interface, `client.renderer.item.properties.numeric.UseDuration` + `ItemOwner`, jspecify vs jetbrains `@Nullable`, `setTooltipForNextFrame`, `MouseButtonEvent`, `pose()`→`Matrix3x2fStack`, `PermissionCheck.Require`), `>=26.1` (Java 25, `GuiGraphicsExtractor`/extract-vs-immediate, `text()` vs `drawString()`, `fakeItem()` vs `renderFakeItem()`, `CreativeModeTabEvents` vs `ItemGroupEvents`, `Screens.getWidgets`), `>=1.21` (id-keyed `AttributeModifier`, data components, singular `tags/item/`, the GUI **sprite atlas** as a whole, `AbstractArrow.getWeaponItem()`, `EnchantmentTags`), `>=1.20.5` (`StreamCodec`/`RegistryFriendlyByteBuf`/payload stack, attachment `syncWith` + `AttachmentSyncPredicate`, `Math.clamp`, Java 21).

**Four genuinely new rows.** Each must land in `docs/MULTIVERSION-CONVENTIONS.md` §3 **in the same commit that first uses it** (conventions §3 rule).

| Predicate | True for | New boundary Archetypes needs |
|---|---|---|
| **`>=26.2`** (existing row — **large additions**) | 26.2 only | ⚠ **Measured by me on the loom cache, and this is the headline finding:** `LivingEntity.knockback(double,double,double,DamageSource,float,boolean)` and its 5-arg sibling exist on **26.2 ONLY** — 26.1.2 has only `knockback(double,double,double)`. Likewise `LivingEntity.blockedByItem(LivingEntity,DamageSource,float)` is 3-arg on 26.2 and **1-arg on 26.1.2 and 1.21.11**. Likewise `Player.blockUsingItem(ServerLevel,LivingEntity,DamageSource,float)` on 26.2 vs `(ServerLevel,LivingEntity)` on 26.1.2. Also: `net.minecraft.client.gui.Hud` and `net.minecraft.client.renderer.extract.LevelExtractor` are **26.2-only classes — absent on 26.1.2** (verified by `unzip -l` on both clientOnly jars). |
| **`>=1.21.11`** (existing row — additions) | 26.x, 1.21.11 | `net.minecraft.client.KeyMapping$Category` (present 26.2/26.1/1.21.11, **absent 1.21.1 and 1.20.1** — measured); the whole render-state architecture (`EntityRenderState`, `EntityRenderer.extractRenderState`, `AvatarRenderer`/`Avatar`/`AvatarRenderState`, `RenderLayer.submit(PoseStack,SubmitNodeCollector,…)` vs `render(PoseStack,MultiBufferSource,int,T,float×6)`, `ItemStackRenderState`) — measured: `EntityRenderState` and `AvatarRenderer` present on 26.2/26.1/1.21.11, absent on 1.21.1 (which has `PlayerRenderer`) and 1.20.1; `LivingEntity.applyItemBlocking`; `world.item.component.BlocksAttacks`; `world.item.component.Consumable`; `FoodProperties.onConsume`; `Player.isSweepAttack`/`canGlide`; `LocalPlayer.itemUseSpeedMultiplier` (measured **0 hits** on 1.20.1); `MobEffectInstance.tickServer`; `ItemStack.processDurabilityChange`; `CrossbowItem.releaseUsing` returning `boolean`; `FoodData.tick(ServerPlayer)` vs `(Player)`. |
| **`>=26.1`** (existing row — fabric-api additions) | 26.2, 26.1 | `client.keymapping.v1.KeyMappingHelper` (below: `client.keybinding.v1.KeyBindingHelper` — the module renamed `fabric-key-binding-api-v1` → `fabric-key-mapping-api-v1`); `registry.FabricPotionBrewingBuilder`. |
| **`>=1.21.11`** (fabric-api additions) | 26.x, 1.21.11 | `client.rendering.v1.FabricRenderState` + `RenderStateDataKey` — **absent on 0.116.14+1.21.1 and 0.92.11+1.20.1**, and the absence is architectural, not a rename. |

**STAGE 2 ADDITIONS to the `>=26.2` row — measured on the node, not predicted.** The rule
is conventions §3's: a boundary a `//?` block relies on is written down in the same commit
that first uses it. Every one of these is the SAME `>=26.2` boundary — no synonym was
invented, and no new predicate was needed for the whole beachhead.

| API | 26.2 | 26.1.2 and below | Used by |
|---|---|---|---|
| `net.minecraft.world.effect.InstantaneousMobEffect` | that spelling | **`InstantenousMobEffect`** — vanilla's typo, still present on 1.21.11 / 1.21.1 / 1.20.1 (checked in all four mojmap jars) | handled by a controller `replacements` rule, not `//?`: the rename also reaches `MobEffect.applyInstantaneousEffect`, `MobEffect.isInstantaneous` and a mixin `method =` STRING |
| `net.minecraft.world.entity.EntityTypes` | new holder class for the vanilla `EntityType` constants (with `EntityTypeIds`) — the same registry split 26.2 did to blocks and items | constants live on **`EntityType`**; `EntityTypes.class` is absent from the jar | `OracleStrikes` (`LIGHTNING_BOLT`) |
| `ThrowableProjectile.getAirDrag()` / `Entity.getAirDrag()` | overridable hook, default `0.99F` | **absent** — `ThrowableProjectile.applyInertia()` does `ldc 0.99f` inline in the non-water arm (`isInWater()` picks 0.8 on both) | `SpellProjectile`: below 26.2 the `@Override` goes and `tick()` pre-scales the delta by `ours / 0.99` before `super.tick()`, which lands applyInertia on exactly the 26.2 product |
| `Entity.getEffectiveGravity()` | present | absent | not used today; recorded so it is not reached for |

**STAGE 2 ADDITIONS — the `>=1.21.11` row is confirmed, not extended.** `applyItemBlocking`,
`BlocksAttacks.resolveBlockedDamage` and `Player.isSweepAttack` all resolve on 26.1.2, so
the shield cluster survives this node intact and its excision question (R-A5) stays where
the design put it, at Stage 4.

**STAGE 3 ADDITIONS to the `>=26.1` row — measured on the node.** Same rule as Stage 2's: a
boundary a `//?` block relies on is written down in the same commit that first uses it. Every
one of these is the SAME `>=26.1` boundary. **No new predicate was invented for the whole
node**, and the one compound predicate that appears (`>=1.21.11 && <26.2`) is a conjunction of
two frozen ones, not a synonym.

*Vanilla:*

| API | 26.x | 1.21.11 and below | Used by |
|---|---|---|---|
| `net.minecraft.client.gui.GuiGraphicsExtractor` | extract-then-draw | **`GuiGraphics`**, immediate | 12 client files. `fill` and all five `blit` overloads are declared IDENTICALLY on both (`javap -p`), so only the type name, `text()`→`drawString()` and `fakeItem()`→`renderFakeItem()` move, each on a single line |
| `Screen.extractRenderState(GuiGraphicsExtractor,III F)` | that | **`render(GuiGraphics,int,int,float)`** | both screens, super call included |
| `AbstractWidget.extractWidgetRenderState` | that | **`renderWidget(GuiGraphics,int,int,float)`** | `BookmarkTab`. `onClick(MouseButtonEvent, boolean)`, `playDownSound`, `updateWidgetNarration`, `isHovered()` are UNCHANGED |
| `Toast.extractRenderState(GuiGraphicsExtractor,Font,long)` | that | **`render(GuiGraphics,Font,long)`** | `ArchetypeLevelUpToast`. The rest of the interface — `getWantedVisibility`, `update(ToastManager,long)`, default `getSoundEvent()` — is unchanged on 1.21.11 |
| `Hud`/`Gui.extractHeart(GuiGraphicsExtractor, …$HeartType, IIZZZ)V` | that | **`Gui.renderHeart(GuiGraphics, Gui$HeartType, IIZZZ)V`** | `HudMixin`, now a THREE-arm chain (owner moves at 26.2, method at 26.1). The wrapped `blitSprite(RenderPipeline,Identifier,IIII)V` is the same overload on both graphics types — read out of the 1.21.11 `renderHeart` bytecode, not assumed |
| `net.minecraft.client.renderer.state.level.{QuadParticleRenderState,CameraRenderState}` | that package | **`net.minecraft.client.renderer.state`** — a package MOVE only | `GreatswordSweepParticle`, `SpellProjectileRenderer`. ⚠ **The design predicted a particle REWRITE here and was wrong**: `SingleQuadParticle.extract`/both `extractRotatedQuad` overloads exist on 1.21.11 with identical shapes, so the extract-based particle pipeline survives this node and the `render(VertexConsumer, Camera, float)` rewrite belongs to Stage 4 |
| `Particle.getLightColor(float)` | **`getLightCoords(float)`** | `getLightColor(float)` | `GreatswordSweepParticle` |
| `Level.getDayTime()` | **`getOverworldClockTime()`** (26.x moved the clock behind `WorldClock` holders) | `getDayTime()` | `ShadowTicker.isNight` |
| `Player.displayClientMessage(Component, boolean)` | **`sendOverlayMessage(Component)`** | `displayClientMessage(c, true)` | `NightForm`, `SkillTokenItem`, `SpellcastingTomeItem` |
| `BlocksAttacks.bypassedBy()` | `Optional<HolderSet<DamageType>>` | **`Optional<TagKey<DamageType>>`** | `ColossusProtector`. `DamageSource.is(TagKey)` exists on every node, so the legacy arm is a direct read |
*fabric-api:*

| API | `>=26.1` | below | Used by |
|---|---|---|---|
| `PayloadTypeRegistry.clientboundPlay()`/`serverboundPlay()` | that | **`playS2C()`/`playC2S()`** | `FabricNet` |
| `creativetab.v1.{CreativeModeTabEvents.modifyOutputEvent, FabricCreativeModeTab.builder}` | that | **`itemgroup.v1.{ItemGroupEvents.modifyEntriesEvent, FabricItemGroup.builder}`** | `ModItems`. Both callback types implement vanilla's `CreativeModeTab.Output`, so every `output.accept(...)` is shared |
| `registry.FabricPotionBrewingBuilder` | that | **`FabricBrewingRecipeRegistryBuilder`** | `ManaPotions`, `AmnesiaPotions`. `BUILD` + `registerPotionRecipe(Holder,Ingredient,Holder)` identical |
| `client.keymapping.v1.KeyMappingHelper.registerKeyMapping` | that | **`client.keybinding.v1.KeyBindingHelper.registerKeyBinding`** | `ArchetypesClient` |
| `client.particle.v1.ParticleProviderRegistry` (+ `PendingParticleProvider`) | that | **`ParticleFactoryRegistry`** (+ `PendingParticleFactory`) | `ArchetypesClient` |
| `client.screen.v1.Screens.getWidgets` | that | **`getButtons`** | `ArchetypesClient` ×2 |
| `hud.HudElement`'s functional method | `extractRenderState(GuiGraphicsExtractor, DeltaTracker)` | **`render(GuiGraphics, DeltaTracker)`** | `ArchetypesClient`'s two `replaceElement` wrappers. `HudElementRegistry` itself and every `VanillaHudElements` id are UNCHANGED |

**STAGE 3, A BOUNDARY THAT IS NOT AN API AT ALL — and it is the finding with the longest
reach.** At `>=26.1` the node runs plain fabric-loom (Minecraft is unobfuscated); below it,
`dev.kikugie.loom-back-compat` switches to fabric-loom-**remap**. The remapping loom honours a
mod jar's `Fabric-Loom-Split-Environment` header and puts only the `-common` half on
`src/main`'s compile classpath. Measured:

```
:26.2-fabric    compileClasspath   net.fabricmc.fabric-api:fabric-networking-api-v1:6.3.3
:26.1-fabric    compileClasspath   net.fabricmc.fabric-api:fabric-networking-api-v1:6.3.1
:1.21.11-fabric compileClasspath   remapped.…:fabric-networking-api-v1-…-COMMON:5.1.6
:1.21.11-fabric clientCompileClasspath  …-CLIENT + …-common
```

So from 1.21.11 down, **`src/main` cannot name `net.fabricmc.fabric.api.client.…` or
`com.specialities.client.…` at all.** That is Skill Proficiencies' conventions §5g ("a seam in
`src/main` cannot reach client-only API") restated as a build fact; this repo had been getting
away with two violations because 26.x's loom does not split. Both are now hand-downs from
client init (`platform/ClientNetHooks`, `SpecialitiesBridge#installClientHudShift`,
`client/ClientHandDown`), gated `<26.1` so the two 26.x jars do not move a byte. **Review test
for every later stage: a new `src/main` reference to any `…api.client…` package compiles green
on 26.x and fails only on a remapped node.**

**Also record (not new predicates, but new *three-way* splits):**
- **Brewing is three-way**: `FabricPotionBrewingBuilder` (`>=26.1`) / `FabricBrewingRecipeRegistryBuilder` (1.21.11, 1.21.1) / `FabricBrewingRecipeRegistry` (0.92.11 only). Two arms plus an else.
- **The heart draw is four-way** (§4.3).
- **`Projectile.deflect` is three-way**: absent on 1.20.1; 3rd param `Entity` on 1.21.1; `EntityReference<Entity>` on 1.21.11+.
- **`FoodProperties` is three-way**: plain class with `getNutrition()` on 1.20.1; a record with no `onConsume`/`ConsumableListener` on 1.21.1; `ConsumableListener` with `onConsume(...)` from 1.21.11.

**§5k hygiene, restated as a review test:** for every `//?` block that exists because an API is missing, ask which §3 row that API is in. If the block's predicate is not that row, it is either a new boundary (add it) or a bug. **And once a non-Fabric node exists, a fabric-api substitute is scoped `fabric && <predicate>`, never `<predicate>` alone** (conventions §4).

### 4.2 Worst-file fork plans

Ranked by structural fork weight, not diff size.

---

#### `LivingEntityMixin.java` — 1,177 lines, **28 handlers** (measured), five colliding boundaries

Target census, measured: **18 × `hurtServer`**, 3 × `knockback`, 2 × `applyItemBlocking`, 1 × `blockedByItem`, 1 × `swing`, 1 × `getVisibilityPercent`, 1 × `heal`, 1 × `isInvertedHealAndHarm`.

The boundaries that collide in this one file: `hurtServer` (`>=1.21.2`), `applyItemBlocking` + `BlocksAttacks` (`>=1.21.11`), `blockedByItem` arity (**`>=26.2`**), `knockback` arity (**`>=26.2`**), plus `Consumable`/`FoodProperties` reached from its neighbours.

**Plan, in three parts:**

**(a) Stage 0 pre-split (Fabric-only, zero behaviour change).** Extract every handler body into a shared `@Unique private … archetypes$<name>Impl(…)` and leave a ≤6-line annotated shell. This is conventions §5a done *before* any `//?` exists. It makes the file mergeable by 4+ lanes and it is the single change that most reduces the port's conflict surface.

**(b) The 18 `hurtServer` handlers — SP's §5a shape, ×18.** SP's R-08 proved the fix at 3× smaller scale; this is exactly that, six times over:

```java
// SHARED — never inside a conditional. One implementation, all seven nodes.
@Unique private float archetypes$daggerDamageImpl(final float amount, final DamageSource source) { … }

//? if >=1.21.2 {
@org.spongepowered.asm.mixin.injection.ModifyVariable(
        method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        at = @At("HEAD"), argsOnly = true)
private float archetypes$daggerDamage(final float amount, final ServerLevel level,
        final DamageSource source, final float original) {
    return archetypes$daggerDamageImpl(amount, source);
}
//?} else {
/*@org.spongepowered.asm.mixin.injection.ModifyVariable(
        method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        at = @At("HEAD"), argsOnly = true)
private float archetypes$daggerDamage(final float amount,
        final DamageSource source, final float original) {
    // legacy `hurt` runs on BOTH logical sides — R-08
    if (((LivingEntity) (Object) this).level().isClientSide()) return amount;
    return archetypes$daggerDamageImpl(amount, source);
}
*///?}
```

⚠ **Write FULL DESCRIPTORS on every `method =`.** The file currently uses **bare `"hurtServer"`** on all 18. On the three intermediary-remapped Fabric nodes and on `1.20.1-forge` (SRG members) a bare name is exactly the §5h hazard SP hit on `Gui`. `hurtServer` is not overloaded today, but `hurt` on the legacy arm is inherited from `Entity`, and Mixin's resolution across an inheritance chain with a bare name is not something to leave to luck.

⚠ **The three `@ModifyVariable`s commute only because they are pure multiplications** — SP's frozen rule. Archetypes has *fifteen* `@ModifyVariable`s on this one method, several of them **not** pure multiplications (`Executioner clamp`, `Sunder armour shred`, `instinctiveGuard` share-subtraction). **Their relative order is therefore load-bearing and is currently guaranteed only by source order + `defaultRequire`.** Add an explicit ordering audit to every node's gate (§5.9) — this is Archetypes' equivalent of SP's R-07 ordinal, and it has no SP precedent.

**(c) The parts that do NOT yield to §5a — three re-rootings and one excision.**

| Handler(s) | Problem | Plan |
|---|---|---|
| `archetypes$daggerKnockback` | `knockback` has **no `DamageSource` parameter** on six of seven nodes (measured). It reads that source to zero out Blizzard/Rend/Flamethrower shoves. Cannot be fixed by renaming. | **Re-root**: `@WrapOperation` on the `knockback` **call site inside `hurt`/`hurtServer`**, where the source *is* in scope, stashing it in a `@Unique` field for the call's duration. R-20 applies: the substitute must reproduce the contract, and the caller census must be established first (`knockback` has callers outside the damage path — `Explosion`, `Wind Charge`, mob AI). |
| `archetypes$incorporealKnockback`, `archetypes$siegeKnockback` | read only `this` | port cleanly with an arity-only fork. |
| `archetypes$bulwark` (`@ModifyExpressionValue` on `Math.acos`), `archetypes$instinctiveGuard` (on `BlocksAttacks.resolveBlockedDamage`), + `BlocksAttacksMixin.disable`, + `DamageTraceMixin`'s `applyItemBlocking` leg | **No host below 1.21.11.** `applyItemBlocking` does not exist; blocking is inline in `hurt()`; shield-disable is `Player.disableShield` + `ItemCooldowns`, i.e. **plural** chokepoints. `ARCHITECTURE.md`'s "every way vanilla knocks a shield aside ends at one call" is **false** on 1.21.1 and 1.20.1. | **RECOMMEND EXCISION on the two legacy pairs** rather than approximation, and say so in the changelog. Approximating a defensive multiplier through a different chokepoint is exactly the class of silent balance divergence R-20 exists to catch. **USER DECISION** — it removes the Colossus Protector epic tree's core on 4 of 7 nodes. |
| `archetypes$blockedByItem` (Iron Spikes + Braced) | 3-arg on 26.2, **1-arg on 26.1 and 1.21.11**, absent below | three-arm chain; the 1-arg arm loses `source`/`amount` and must recover them from the wrapping `hurt` frame or drop the amount-scaled half. |

---

#### `ModAttachments.java` → `ModState.java` — 740 lines, **75 registrations / 47 `syncWith`**

Highest volume; **lower per-line risk than `LivingEntityMixin` once §3.1's key-table refactor lands**, because after it the file has *zero* `//?` blocks and *zero* loader imports. All the version forking moves into `FabricArchetypeStore`, one file, where SP's `FabricSkillStore.java` is the exact template — copy it, do not improvise.

The fork inside the implementation, for reference:
```java
//? if >=1.20.5 {
builder.syncWith(codec, AttachmentSyncPredicate.all());
//?} else {
// no attachment sync at all on 0.92.11 — delegate to the shared broadcast path
/*LegacyStateSync.registerBroadcast(key);
*///?}
```
**On-disk id and codec stay identical across the fork** (SP's rule) so a world moved between nodes keeps its data.

---

#### `ArchetypesClient.java` — 356 lines, **3 registries, 8 HUD calls, 7 keybinds**

The client fan-out point. Conventions §4 loader rule applies verbatim: **the registration LINE forks, the lambda BODY never does.**

```java
//? if >=1.21.11 {
HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Archetypes.id("mana"), ManaHud::render);
//?} else {
/*HudRenderCallback.EVENT.register((graphics, tickDelta) -> ManaHud.render(graphics, tickDelta));
*///?}

//? if >=26.1 {
ABILITY_KEYS[slot] = KeyMappingHelper.registerKeyMapping(new KeyMapping(name, KEYSYM, key, CATEGORY));
//?} else {
/*ABILITY_KEYS[slot] = KeyBindingHelper.registerKeyBinding(new KeyMapping(name, KEYSYM, key, CATEGORY));
*///?}
```
⚠ Note those are **two different boundaries** on adjacent lines: the fabric-api helper flips at `>=26.1`, the vanilla `KeyMapping.Category` argument flips at `>=1.21.11` (measured). Below 1.21.11 the last argument is a plain `String` category. **Do not collapse them into one predicate** — that is precisely the §5k bug.

**The two `replaceElement` calls (`FOOD_BAR`, `AIR_BAR`) have no legacy counterpart at all** and become `@WrapMethod` handlers in a client `GuiMixin` — **which is the exact file shape SP already ships**. Extend SP's proven `GuiMixin` fork rather than inventing one, and remember conventions §5h: full descriptors on every `Gui` member reference, because after remap those methods become `a`/`b`/`c` and `a` alone is overloaded ~20×.

---

#### Runners-up

- **`AvatarRendererMixin.java`** — 87 lines but the highest *hit density* in the mod; its entire body is `>=1.21.11`-only and it is the fan-out point for all three render layers. Whole-file `//?` (conventions §4, the `GuiMixin` trick).
- **`DamageTrace.java`** — 1,016 lines, concentrated on the settled `hurtServer`/`hurt` boundary. **Recommend it becomes a per-node-inert dev tool** rather than being ported five ways: gate the whole class `>=1.21.2` and drop `/archetypes trace` below it, exactly as SP would gate a debug surface. It is a dev tool; the balance-parity review (§5.9) is what actually protects the legacy nodes.

### 4.3 Client HUD strategy, per node family — reusing SP's four HUD path findings

SP established **four HUD entry points across seven nodes** (conventions §5l). Archetypes inherits all four and adds a fifth concern.

| Node family | HUD registration | Heart draw (UndeadHud) | Sprite/blit primitive |
|---|---|---|---|
| **26.2** | `HudElementRegistry.attachElementAfter/replaceElement` | `Hud.extractHeart(GuiGraphicsExtractor, Hud$HeartType, …)` | `GuiGraphicsExtractor.blitSprite` (ARGB int), `ARGB.colorFromFloat` |
| **26.1** | `HudElementRegistry` (same) | **`Gui.extractHeart(GuiGraphicsExtractor, Gui$HeartType, …)`** — class *and* HeartType owner move; `Hud` does not exist here (measured) | same |
| **1.21.11** | `HudElementRegistry` (same) | `Gui.renderHeart(GuiGraphics, Gui$HeartType, int,int,boolean,boolean,boolean)` — **immediate**, `GuiGraphics` not `…Extractor` | ARGB-int `blitSprite` public (SP R-17) |
| **1.21.1** | ❌ no `client.rendering.v1.hud` → **client `GuiMixin`** (SP §5h, 5 wrapped methods) | `Gui.renderHeart(GuiGraphics, Gui$HeartType, int,int,boolean,boolean,boolean)` | sprite-taking `blitSprite` **private**; use public float-RGBA `blit(x,y,z,w,h,sprite,r,g,b,a)` (R-17); `setColor` only for untinted-overload draws; `FastColor.ARGB32` not `ARGB` |
| **1.20.1-fabric** | **client `GuiMixin`**, **4** wrapped methods not 5 (SP §5i — no `renderExperienceLevel`; `Gui.render(GuiGraphics,float)`, one `return` at offset 1537) | `Gui.renderHeart(GuiGraphics, Gui$HeartType, int,int,**int**,boolean,boolean)` — **extra `int`** | **No GUI sprite atlas at all** (SP R-17). Every Archetypes HUD background becomes a raw-sheet blit: `textures/gui/icons.png` etc., pixel-verified coordinates. `FastColor.ARGB32` has only the 4-channel `color(a,r,g,b)` — restore `color(alpha,rgb)`/`colorFromFloat` as one-line private helpers (SP did exactly this). |
| **1.21.1-neoforge** | `RegisterGuiLayersEvent.wrapLayer` — **and R-11 says the mixin would resolve and never run** | as 1.21.1 | as 1.21.1 |
| **1.20.1-forge** | **per-node `ForgeGuiMixin`** at `versions/1.20.1-forge/src/client/java/…` — R-11's fourth option; `ForgeGui extends Gui`, overrides `render` and **never calls super** | as 1.20.1 | as 1.20.1 |

**Rules carried from SP, unchanged:**
- **`5l` — the gate goes at the shared funnel.** Archetypes has six HUD elements × up to four paths = 24 registration sites. Each element's *draw* logic must be one method every path calls; only the registration forks.
- **`5b`** — arithmetic and animation math stay outside `//?`; convert at the call site (`iconAlpha / 255.0F`).
- **Blend-state hazard (R-17)**: the legacy 14-arg `innerBlit` ends with an unconditional `RenderSystem.disableBlend()`. Inject at/after the experience bar or at `TAIL` of `Gui.render`, or follow the draws with `enableBlend(); defaultBlendFunc();`.
- **`Screen.render` draws widgets and nothing else on 1.20.1** — `ArchetypeScreen` and `ArchetypePickerScreen` must call `renderBackground` themselves or they render over the live world, compiling and applying cleanly. SP hit this exact bug.
- **Per-node client mixin config is MANDATORY on a legacy loader node** — check *both* directions (a class that is not in the jar must not be listed; a class that *is* in the jar and must not run must not be listed either).

**Archetypes-specific HUD/render additions with no SP path:**

| Surface | 26.2 | 26.1 / 1.21.11 | 1.21.1 / 1.20.1 |
|---|---|---|---|
| 3 × `RenderLayer<AvatarRenderState,PlayerModel>` (`BulwarkShieldLayer`, `BladestormLayer`, `NightEyesLayer`) | `submit(PoseStack,SubmitNodeCollector,…)` | same | **`render(PoseStack,MultiBufferSource,int,T,float×6)` against the ENTITY** — name, params and generic bound all move. Silver lining: below 1.21.11 the layer can read the entity + its state directly, so the fork **collapses the extract indirection** rather than reimplementing it. Rewrite of the data path in 5 files. |
| `SpellProjectileRenderer` | `submit(ThrownItemRenderState,…)` | same | `render(SpellProjectile, float, float, PoseStack, MultiBufferSource, int)` |
| `FabricRenderState`/`RenderStateDataKey` (BULWARK/GHOST/GLOW) | present | present | **absent** — read attachments directly in `render()` |
| `ExtraSensoryPerception` outlines | `EntityRenderState.outlineColor` — one field is both the *ticket* into the outline pass and the *colour* | same | **Two separate channels**: membership from `Minecraft.shouldEntityAppearGlowing(Entity)`, colour from `Entity.getTeamColor()`. The documented "Death Mark red beats Glowing and team colours" precedence **must be rebuilt by hand.** |
| ESP wall-piercing (`LevelExtractorMixin.isEntityVisible`) | ✅ | ❌ **`LevelExtractor` is 26.2-only — absent on 26.1** (measured) | ❌ |
| `GreatswordSweepParticle` | `extractRotatedQuad(QuadParticleRenderState,…)` | same on 26.1; **`QuadParticleRenderState`/`CameraRenderState` absent below 26.1** | `render(VertexConsumer, Camera, float)` |
| `RadianceLight` (client `setBlock` + `LevelLightEngine` + `LightBlock.LEVEL`) | ✅ | needs per-node verification | deepest vanilla-internals coupling in either mod — **recommend gating it off below 1.21.11 unless E-LIGHT-1 (§6) comes back green** |
| `ArchetypeLevelUpToast` | `getWantedVisibility` + `update` + `getSoundEvent`, no `render` in iface | 26.1 same; **1.21.11 adds `render(GuiGraphics,Font,long)` back** | one combined `Visibility render(GuiGraphics, ToastComponent, long)`; `ToastComponent` not `ToastManager`; no `getSoundEvent()` — **SP's fork verbatim** |
| `BookmarkTab` | `AbstractWidget` + `extractWidgetRenderState` + `onClick(MouseButtonEvent,boolean)` | same | `render(GuiGraphics,int,int,float)` + `onClick(double,double)` — **SP's `BookmarkTab` fork transfers 1:1** |

**`VanillaUi.java` (166 lines) is the highest-leverage single file in the whole client port.** Every method takes `GuiGraphicsExtractor` and is called by both screens, the picker and several HUDs. Forking it once carries through to `ArchetypeScreen` (635 lines), `ArchetypePickerScreen` (423), `CooldownBarHud` (400) and five more. **Do `VanillaUi` first in every client lane.**

### 4.4 Non-Java version deltas

| Surface | Node(s) | Mechanism |
|---|---|---|
| `data/minecraft/tags/item/swords.json` + 5 own item tags | both 1.20.1 | R-16 `processResources` rename, copied verbatim |
| `data/archetypes/tags/damage_type/magical.json` | verify per node | the 8 referenced `minecraft:*` damage types must be checked present on 1.20.1/1.21.1; anything absent → `{"id":…,"required":false}` **unconditionally in the shared tree** (R-16's better-than-`//?` fix). ⚠ **Cascade**: a tag that fails to resolve is not put in the result map and `#tag` refs read that map — one drop can take three tags with it, silently. |
| 20 × `assets/archetypes/items/*.json` | `< 1.21.4` | `processResources` relocation, **easier than SP's** — 18 legacy twins already exist |
| 20 recipes + 20 recipe advancements, incl. 2 `_smithing` | 1.20.1 (and verify 1.21.1) | smithing-transform recipe schema churned ~1.20.5 (trim vs upgrade templates). **No SP precedent** — SP ships no custom-item recipes. Needs its own per-node datapack-load probe. |
| `archetypes.mixins.json` `compatibilityLevel: JAVA_25` | all | `expand("java" to "JAVA_${requiredJava.majorVersion}")` — SP's exact mechanism. Two benign 1.20.1-forge log lines (`does not specify "minVersion"`, `JAVA_17 … higher than … JAVA_13`) — **do not "fix" either.** |
| `fabric.mod.json` `depends` (4 per-node values + PAL) | all | `expand` + line-`filter`; never `//?` |
| `pack.mcmeta`, `META-INF/{neoforge.mods,mods}.toml`, per-node client mixin config | 2 loader nodes | per-node overrides at `versions/<node>/src/main/resources/` — mandatory, and for the mechanical reason SP records (the shared resource root ships into every node's jar and there is no resource exclusion) |

---

## 5. Stage plan

**Compression thesis, stated honestly:** the *learning* compresses (zero R-findings to re-derive, the `//?` grammar is known, the seam pattern is known, the release wiring is known). The *volume* does not — 2.6× the code and 2.5× the injectors. The compression shows up as **more parallel lanes per stage**, not fewer stages. SP ran 1–2 lanes; Archetypes can run 3–5 after Stage 0.

### 5.1 Stage 0 — Pre-port refactor, **on the current single-target repo, before Stonecutter exists**

This is the plan's keystone. Everything that can be done as a behaviour-preserving Fabric-26.2-only refactor is done here, where there is **one node to regress against and no `//?` syntax to get wrong**.

| Lane | Deliverable | Conflict surface |
|---|---|---|
| **0-A** | `platform/` package: `ArchetypeStore` (key-table seam, §3.1) + `FabricArchetypeStore`; `ModAttachments` → `ModState` (75 `StateKey`s, zero loader imports); rewrite all **253** `(AttachmentTarget)` sites across 38 files | **touches 38 files** — must run alone or first |
| **0-B** | `Net` seam (§3.2) + `FabricNet`; rewrite all **41** networking sites | 13 files; disjoint from 0-A after 0-A lands |
| **0-C** | `Platform` seam; retire all **9** `FabricLoader` sites; route `SPECIALITIES_SHIFT` through `SpecialitiesBridge.hudShift()` | 4 files |
| **0-D** | `LivingEntityMixin` handler-body extraction to 28 `@Unique …Impl` methods (§4.2a); same for `DamageTraceMixin`, `AbstractArrowMixin`, `PlayerMixin` | 1 hot file — must run alone |
| **0-E** | **Full descriptors on all 77 `method =` targets** (§5h) | 28 files, mechanical, no overlap with 0-D if 0-D lands first |
| **0-F** | **Delete the `/archetypes dummy` subcommand and its Vindicator-dressing code** (standing project instruction, pre-publish); gate `DamageTrace` behind a dev flag | 2 files |
| **0-G** | Java-17 audit **without moving the compiler**: grep for `Math.clamp` (0 found), pattern-matching `switch` (0 found), sealed types (**1 found — `OracleStrikes.java:43` `private sealed interface Pending permits Recurrence, Hop`**), 42 records (legal on 17). Resolve the sealed interface or confirm the conventions §5e exclusion is toolchain- not language-driven | 1 file |

**Gates for Stage 0:** the 26.2 jar builds; a headless dedicated server boots clean with 19/19 common mixins applied under `-Dmixin.checks`; **an in-game session** exercising all three archetypes; and a **`DamageTrace` before/after comparison** — Stage 0 rewrites the damage path's *plumbing*, so the trace's own mismatch and unaccounted alarms are the parity oracle. This is not optional: 0-A and 0-D together touch every damage handler in the mod.

**Output: one commit per lane, all on `workspace`, no Stonecutter anywhere.** After Stage 0, the mod still ships as a single-target 26.2 Fabric mod — it can be released as-is if the user wants an interim build.

### 5.2 Stage 1 — Workspace scaffold, one node

Copy the six scaffold files (§1.1–1.3), register **`26.2-fabric` only**. Sole bottleneck-file commit.

**Gate — the sharpest one in the whole plan:** the `26.2-fabric` jar must be **instruction-identical and resource-byte-identical** to Stage 0's jar (`javap -c -p -constants` per class + resource byte compare). This proves the scaffold is inert. Script it now as `scratchpad/gate/snap.sh`, mirroring SP's, because **every subsequent stage re-runs it against every prior node.**

⚠ Update `~/Desktop/"Specialities + Archetypes Dev 26.2.command"` in this commit — root `runClient` disappears.

### 5.3 Stage 2 — **`26.1-fabric` — THE BEACHHEAD** (deviation from SP: not 1.21.11)

**Why the deviation, on measured grounds.** SP's 26.1 was nearly free, so SP's beachhead was 1.21.11. Archetypes' 26.1 is **not** free: I measured that `knockback(…DamageSource…)`, `blockedByItem(3-arg)`, `blockUsingItem(4-arg)`, `net.minecraft.client.gui.Hud` and `net.minecraft.client.renderer.extract.LevelExtractor` are all **26.2-only**. That is four of the mod's highest-risk surfaces — the damage funnel's knockback trio, the shield-block hook, the undead heart swap, and ESP's wall-piercing — **breaking at the very first step down**. You want those found on the node that is otherwise closest to home (Java 25, jspecify, `Identifier`, extract-render, render-state architecture, `HudElementRegistry`, PAL 1.2.5 merged/mojmap with **no** dependency-configuration fork).

26.1 is also the **only** node that pairs `GuiGraphicsExtractor` with `Gui` (not `Hud`), so proving the heart-swap fork there de-risks it before the render pipeline itself changes. And it forces the SP-interop per-node-artifact blocker (§3.5) to surface at Stage 2 instead of Stage 4.

**Lanes:** 2-A damage funnel (`knockback` re-rooting + `blockedByItem` arity + R-20 caller census); 2-B client (`Hud`→`Gui` heart swap, `LevelExtractor` excision + ESP degradation decision); 2-C build/metadata + SP-interop artifact; 2-D fabric-api swaps (`CreativeModeTabEvents` is already on the right side at 26.1 — verify, don't assume).

#### 5.3.1 Stage 2, as landed — the measured outcome

The node needed **23 `//?` blocks across 10 files**, one `replacements` rule and one
`processResources` transform. Only two predicates appear in the whole set — `>=26.2` and its
negation `<26.2` for the three blocks that are pure additions below the boundary. Nothing about the beachhead argued for revising the design; two of its open
questions are now answered and one new API delta was found (`getAirDrag`, §4.1).

**E-KB-1 IS RUN, and its answer is in `com/archetypes/KnockbackSource.java`** — a
constant-pool scan of every class in the mojmap common jar of BOTH versions (R-20's method).
Every path that reaches `LivingEntity.knockback` is enumerated there with the source 26.2
passes and where that same source lives on 26.1. Three sites are wrapped (two inside
`hurtServer`, one inside `Player.attack`) and that is the whole of what the mod's five
knockback behaviours can reach carrying a PLAYER source. Two residues are stated rather than
hidden — `Player.doSweepAttack` (unreachable for a dagger, a bare fist or a spell, because
vanilla only sweeps with a sweeping-ratio weapon) and the mob-AI legs
(`Mob.doHurtTarget`, `LivingEntity`/`Player.stabAttack`, `ChargeAttack`, `RamTarget`, whose
source is a mob's). In both, only one of the three global `isPulsing()` flags could differ.
**They are a Stage-3 item, not a Stage-2 one**: 1.21.11 / 1.21.1 / 1.20.1 all land on the
same `knockback(DDD)` shape, so the residue should be settled once for all four legacy nodes
rather than four times.

**R-A5 does NOT bite here.** `applyItemBlocking`, `BlocksAttacks.resolveBlockedDamage` and
`Player.isSweepAttack` all resolve on 26.1.2. The shield cluster is whole on this node.

**A mixin whose class vanishes on a node needs its config entry dropped, and `//?` cannot
do it.** Skill Proficiencies' answer is a per-node override of the whole client mixin config;
this repo takes the other sanctioned mechanism (`processResources` line-blank,
`strippedMixinEntries` in the node script) because six of seven nodes drop the SAME entry
(`LevelExtractorMixin`) and six full copies of one JSON is the shape that goes stale. Blanking
leaves valid JSON; the constraint it rests on — the entry must not be the array's last
element — is written at the transform.

**A `*/` inside a disabled `//?` branch closes Stonecutter's own comment early.** Any javadoc
on a whole-file-gated class has to become line comments first (`LevelExtractorMixin`).

### 5.4 Stage 3 — `1.21.11-fabric` — the extract-vs-immediate render split

The `>=26.1` boundary lands here as one wide, shallow diff: `GuiGraphicsExtractor`→`GuiGraphics`, `extractRenderState`→`render`, `extractWidgetRenderState`→`render`, `text()`→`drawString()`, `fakeItem()`→`renderFakeItem()`, `KeyMappingHelper`→`KeyBindingHelper`, `FabricPotionBrewingBuilder`→`FabricBrewingRecipeRegistryBuilder`, plus the `Toast` interface regaining `render`.

**Do `VanillaUi.java` first** (§4.3) — it carries `ArchetypeScreen`, `ArchetypePickerScreen`, `CooldownBarHud`, `ProcIndicatorHud`, `ManaHud`, `BankedHungerHud`, `SunBlindOverlay`, `DeadeyeOverlay`, `BookmarkTab`.

⚠ **First node where PAL's dependency configuration forks** (`modImplementation`, intermediary). ⚠ **First remapped node** — this is where bare-name `method =` targets would bite, and Stage 0-E has already fixed them.

**Lanes:** 3-A `VanillaUi` + screens; 3-B HUDs + toast + widget; 3-C keybinds/brewing/fabric-api; 3-D common-side (largely free at this boundary).

### 5.5 Stage 4 — `1.21.1-fabric` — **the biggest single step in the port**

Everything at `>=1.21.11` lands at once: `Identifier`→`ResourceLocation` (controller `replacements`, free), `.projectile.arrow` package, the **entire render-state architecture**, `AvatarRenderer`→`PlayerRenderer`, `RenderLayer.submit`→`render`, `FabricRenderState`/`RenderStateDataKey` absent, `HudElementRegistry`→client `GuiMixin`, `KeyMapping.Category`→String, `hurtServer`→`hurt` × **18 + DamageTrace + Flense**, `applyItemBlocking`/`BlocksAttacks` gone, `Consumable`/`FoodProperties.onConsume` gone, `Player.isSweepAttack`/`canGlide` gone, `LocalPlayer.itemUseSpeedMultiplier` gone, `MobEffectInstance.tickServer`→`tick`, `ItemStack.processDurabilityChange` gone (but `EnchantmentHelper.processDurabilityChange` **survives** — re-root, don't rewrite), `UseDuration`→`ItemProperties`, `ARGB`→`FastColor.ARGB32`, jspecify→jetbrains (import-only fork), `Toast` split.

**Split into 4a (common) and 4b (client), landed as separate node-internal milestones with their own gates.** 4b is larger than 4a.

**Lanes, 4a:** the 18-handler `hurtServer` fork (one lane, sequential, it is one file); the shield-cluster excision decision; the `Consumable`/`FoodProperties` three-way; arrow/projectile package + `deflect` three-way.
**Lanes, 4b:** client `GuiMixin` (SP's, extended); the three `RenderLayer`s + `AvatarRendererMixin` + `SpellProjectileRenderer` (render-state rewrite); ESP two-channel rebuild; `Toast` + `BookmarkTab` + screens; particles.

### 5.6 Stage 5 — `1.20.1-fabric` — Java 17, no sprite atlas, no attachment sync, no PAL

`>=1.21` and `>=1.20.5` land together: id-keyed `AttributeModifier` → UUID form at **~30+ call sites**, data components, GUI sprite atlas gone entirely, payload stack gone, **attachment sync gone entirely** (the `ALL_TRACKING` fallback of §3.1 is exercised for the first time), Java 25→17 for real, R-16 tag rename, `Entity.deflection` gone, `AbstractArrow.getDefaultGravity`/`pickupItemStack` gone, `Equippable`/`GLIDER` gone (Magic Armaments' glide needs a Levitation-based reimplementation or excision — **USER DECISION**), `FoodData.eat(FoodProperties)` gone, **PAL gone**.

**Lanes:** 5-A attachment-sync fallback (the big new infrastructure); 5-B the `AttributeModifier` sweep (mechanical, wide); 5-C client raw-sheet blits + 4-method `GuiMixin`; 5-D PAL excision/Option-A driver; 5-E resources (tags, models, recipes).

### 5.7 Stage 6 — the loader axis

`1.21.1-neoforge` and `1.20.1-forge`, registered as **two separate bottleneck commits**. Copy SP's `build.neoforge.gradle.kts` / `build.forge.gradle.kts` / `buildSrc` mutex. **R-10 JiJ is mandatory** (18 injectors). **R-11's HUD answers transfer**: NeoForge `wrapLayer` with `GuiMixin.class` present-but-unlisted; Forge per-node `ForgeGuiMixin`. **R-22 applies and widens** (§3.4). The 12 new event arms (§3.4) and the four new registration seams land here. `[modproperties]` interop needs the sign-off from §3.5.

⚠ **The two loader nodes are the only place Archetypes' 5 PAL drivers have a NeoForge artifact (`ReDTdA0C`) and, on Forge, none at all** — so `1.20.1-forge` inherits `1.20.1-fabric`'s animation decision automatically, and `1.21.1-neoforge` inherits `1.21.1-fabric`'s 1.1.x source fork.

### 5.8 Stage 7 — parity review, in-game passes, release

### 5.9 Per-stage gates — **same discipline as SP, one addition**

Every stage, without exception:

1. **Instruction-identity regression on every prior node.** `javap -c -p -constants` per class + resource-byte compare, via `scratchpad/gate/snap.sh`. ⚠ **R-20's javac-17 caveat**: once `1.20.1-fabric` exists, compare **instructions, not bytes** — javac 17 names pattern-match temporaries by source character offset, so editing a *comment* changes that node's class bytes with zero instructions changed.
2. **Headless dedicated-server smoke** per node: `-Dmixin.checks`, 19/19 common mixins applied, zero mixin errors, **zero `missing following references` / `Couldn't load tag`**, plus R-16's **positive** tag probe with a bogus control (absence of an error line does not rule out the cascade — a `required:false` drop is silent by design), plus an item-registry probe for the 30 items, clean stop, exit 0.
3. **Mixin export audit** — `-Dmixin.debug.export=true`, `javap -c` on the transformed targets. Per node, verify: all 77 injectors resolved (`injectors.defaultRequire: 1` is the drift detector — **keep it on every variant**), and the `hurtServer`/`hurt` handler **order** matches 26.2's source order.
4. **`injectors.defaultRequire: 1` on every node's config, both common and client.**
5. **NEW, no SP precedent — the damage-shaper ordering audit.** Archetypes has 15 `@ModifyVariable`s on one method and they do **not** all commute (§4.2b). Per node, read the transformed `hurtServer`/`hurt` and assert the handler offsets are in source order and that `FlenseMixin` (priority 1500) is last and `DamageTraceMixin` (priority 500) is first. **This is Archetypes' R-07.**
6. **Cross-mod ordering gate** (Archetypes-only): with Skill Proficiencies also installed, re-verify by bytecode export that `FlenseMixin` still lands after SP's Combat multiplier and stealth crit, and that `UseDurationMixin` still divides out exactly SP's applied Archery reduction. Per node — mixin ordering is not guaranteed by priority docs, which is why `ARCHITECTURE.md` says it was verified by export.
7. **In-game pass per node family** — not deferred to the end. SP's Stage-5 item-model bug and Stage-6 missing-Forge-client-bootstrap were both in-game-only, and Archetypes' client half is 3× the risk.
8. **At the end: balance-parity review + Tuning-propagation proof.** `Tuning.java` is 1,653 lines with **verified zero `net.minecraft`/`net.fabricmc` imports** — the ideal oracle, exactly SP's. Proof: a 1-character edit changes that value in all seven `Tuning.class` files and **nothing else changes** (whole-class `javap` diff, constant-pool indices normalised). Plus an adversarial per-node behavioural review against 26.2 — **R-20's lesson is that every build-shaped gate passed while four behavioural regressions sat in one re-rooted event.** Archetypes has *three* re-rootings queued (knockback, `EnchantmentHelper.processDurabilityChange`, the `ALL_TRACKING` sync) plus one excision, so budget this review generously.

### 5.10 Conflict analysis for parallel lanes

**Serialize absolutely:** the three bottleneck files (§1.1) — node registration is always its own commit by the stage owner, landed before lanes start.

**Serialize within a stage:** `LivingEntityMixin.java`, `ModState.java`/`FabricArchetypeStore.java`, `ArchetypesClient.java`. After Stage 0's pre-split these are *mergeable* but still hot; assign each to exactly one lane per stage.

**Safely parallel:** the 18 `*Nodes.java` data tables, `Constellation`/`Constellations`, `Tuning`, `TreeNodes`, `SubTree`, `WeaponClass`, `MeleeSwing`, `Homing`, `SureFooting` — all `PURE`, zero MC/Fabric imports in several, and they will need **zero `//?` blocks on any node**. That is ~6,000 LOC that the port simply does not touch. Similarly the 20 recipe/advancement JSON pairs (resource lane only).

**The lane ceiling is ~4.** Beyond that, lanes start colliding on `VanillaUi`, the mixin configs, and the node script.

### 5.11 Release, `SAME AS SP`

Steps 1–9 of conventions §7, unchanged. `mod.version` in the toml, `changelogs/<version>.md` with an `# H1` title, `buildAndCollect` → seven jars, per-jar boot smoke, `printPublishMetadata`, `publishMods` dry run, then the live upload **only with the user's explicit go-ahead**, `--no-daemon`, `MODRINTH_TOKEN` from `~/.config/modrinth/token`.

Archetypes-specific: **read the project id back from the API** (`GET /v2/project/archetypes`) rather than guessing; Archetypes has no multi-version publish history, so **adopt SP's numbering scheme exactly** — bare `<version>` on the newest Fabric node, `+<node key>` on the rest, and the **whole node directory name** on the two loader nodes (`1.2.0+1.21.1-neoforge`, `1.2.0+1.20.1-forge`) with the loader in `base.archivesName` too, or both 1.20.1 nodes write the same jar name into `build/libs/<version>/`. **64-char name budget = 38 title characters** (same arithmetic: 64 − version − `" — "` − longest suffix `" (1.21.1 NeoForge)"`). Copy SP's build-gate that fails the *live* upload with the budget instead of taking a 400.

---

## 6. Risk register

Ordered by "blocks the port" first. Every row names the experiment that resolves it.

### Tier 1 — blocks a node, needs a **USER DECISION**

| # | Risk | Resolving experiment / decision |
|---|---|---|
| **R-A1** | **PAL has no 1.20.1 artifact on either loader, and no LexForge build at any MC version.** It is a hard `depends` in `fabric.mod.json`. 10 animations / 5 layers dead on 2 of 7 nodes. | **DECISION.** Option A (rewrite drivers against KosmX `playerAnimator` 1.0.x) vs Option B (no-op seam, cosmetic loss table in §2.2). **Recommendation: B first, A as follow-up.** |
| **R-A2** | **E-PAL-1 (untested, decisive for Option A):** do the 9 `assets/archetypes/player_animations/*.json` load unchanged on `dev.kosmx.player-anim:player-animation-lib-fabric:1.0.2-rc1+1.20`? PAL is a fork of playerAnimator and the `"version": 3` torso/body trap is a 1.0.x-lineage trap, so reuse is *likely*. | **EXPERIMENT.** Drop the 9 JSONs into a 1.20.1 Fabric dev client with the 1.0.x lib, call `PlayerAnimationRegistry.getAnimation(id)` on each, assert non-null and that bone names survive. Cheap. **Failure = re-authoring 10 animations, not porting a driver.** Run before committing to A. |
| **R-A3** | **SP cannot supply Archetypes on 6 of 7 nodes today.** `build.fabric.gradle.kts:324` gates publishing to `>= "26.2"`; `build.neoforge.gradle.kts:294` states only 26.2 owns the coordinate. One artifact provably cannot serve seven (measured: `iconTexture()` returns `Identifier`/`ResourceLocation`/`class_2960` by node; namespaces `official`/`intermediary`/mojmap-no-header). | **DECISION + SP-SIDE EDIT.** Preferred: extend SP's publishing to all seven as `…:<version>+<node key>` while 26.2 keeps the bare coordinate. Touches SP's single-writer wiring → **needs the user's go-ahead and its own commit.** Fallback that needs zero SP change and works today: `modCompileOnly(files("../specialities/build/libs/1.6.0/…jar"))` per node. |
| **R-A4** | **SP's loader-node external-skill contract is not a published API.** `NeoForgePlatform.java:33` requires the user's sign-off before `[modproperties.<modid>]`/`specialities_skills` is documented as one; SP's README was deliberately left unedited "because Archetypes is the first consumer". | **SIGN-OFF.** Without it, `1.21.1-neoforge` and `1.20.1-forge` cannot register Spellcasting. |
| **R-A5** | **The `BlocksAttacks` shield subsystem has no host below 1.21.11** (`applyItemBlocking` absent; disable paths plural). Affects `archetypes$bulwark`, `archetypes$instinctiveGuard`, `BlocksAttacksMixin`, `DamageTraceMixin`'s blocking leg — i.e. the Colossus Protector epic tree's core, on 4 of 7 nodes. | **DECISION.** Recommend **excise on the two legacy pairs and say so in the changelog**, rather than approximate through a different chokepoint — approximation is precisely the silent-balance-divergence class R-20 exists to catch. |
| **R-A6** | **`Equippable`/`DataComponents.GLIDER` is `>=1.21.2`** — Magic Armaments' "conjured weapon doubles as an Elytra" has no component on 1.20.1. | **DECISION.** Levitation-effect reimplementation vs excision on 1.20.1. Recommend excision (a Levitation stand-in is a different mechanic, not the same one). |

### Tier 2 — large but tractable; the experiment is a build, not a question

| # | Risk | Resolving experiment |
|---|---|---|
| **R-B1** | **`ALL_TRACKING` attachment sync has no template anywhere.** 16 keys broadcast to every tracking client; fabric-api 0.92.11 has no sync at all and Forge 1.20.1 has capabilities and none. | Build the shared `archetypes:state_sync` broadcast + `START_TRACKING` replay **once** on top of the `Net` seam (§3.1) and have all three sync-less implementations delegate. Prove with a two-client dev session: player A's `BULWARK_ACTIVE` visible on B's screen after B starts tracking A mid-flight. |
| **R-B2** | **The render-state architecture is `>=1.21.11`.** 5 client files lose their target on 1.21.1/1.20.1. | Not a question — a rewrite of the data path (layers read the entity + attachments directly). Sequenced as Stage 4b's largest lane. The one *unknown* is whether `ItemStackRenderState`'s ghost-model path has a legacy equivalent → **E-GHOST-1**: `javap` `ItemRenderer.render(ItemStack,…)` on 1.21.1 and confirm the 12 ghost-sword / 3 ghost-shield copies can be drawn from a plain `ItemStack` in `render()`. |
| **R-B3** | **ESP loses both halves below 26.2/1.21.11.** Wall-piercing (`LevelExtractor`) is 26.2-only — **absent even on 26.1** (measured). Outline colour splits into two channels below 1.21.11, breaking the "one field is both ticket and colour" invariant. | **E-ESP-1**: on 26.1, find whether an equivalent visibility/frustum hook exists at all; if not, ESP ships **wall-piercing-free on 6 of 7 nodes** — a feature-degradation row for the changelog, and arguably a **USER DECISION**. Outline colour: mixin `Minecraft.shouldEntityAppearGlowing` + override `Entity.getTeamColor`, then re-establish the Death-Mark-beats-Glowing precedence by hand and prove it with a team-coloured, Glowing, marked mob. |
| **R-B4** | **`knockback` has no `DamageSource` on 6 of 7 nodes** (measured). `archetypes$daggerKnockback` cannot be ported by renaming. | **E-KB-1**: establish the caller census of `LivingEntity.knockback` on each node (constant-pool scan — R-20's method) before choosing the wrap site. The re-rooted hook **must be no wider** than the modern one: `Explosion`, wind charge and mob AI also call it. |
| **R-B5** | **R-22 widens.** Archetypes constructs `EntityType`, `SimpleParticleType`, 3 `MobEffect`s and 4 `Potion`s at class-init, on top of SP's 5 items. | **E-R22-1**: per loader, `javap` each constructor for an intrusive-holder call (`createIntrusiveHolder`). Failure mode is `<clinit>`-time and far from the call you moved. Resolve *before* Stage 6 starts, not during. |
| **R-B6** | **Java 17 exposure has never been tested** — Archetypes has compiled at `release = 25` its whole life. Found: **1 sealed interface** (`OracleStrikes.java:43`). Zero `Math.clamp`, zero pattern-matching `switch`, 42 records (legal on 17). | **E-J17-1**: set `release = 17` on the current single-target build in Stage 0-G and compile. Cheap, and it moves the whole class of finding off the critical path. Note: sealed types were finalised in Java 17 (JEP 409), so conventions §5e's exclusion may be toolchain- rather than language-driven — **confirm against the conventions file before rewriting the interface**. |
| **R-B7** | **Well Fed's banked hunger needs THREE implementations.** `FoodProperties` is a plain class (1.20.1) / a record with no `onConsume` (1.21.1) / a `ConsumableListener` (1.21.11+); `FoodData.eat(FoodProperties)` absent on 1.20.1; `Consumable` absent below 1.21.11; `FoodData.tick` forks `(Player)`→`(ServerPlayer)` at 1.21.11. | **E-FOOD-1**: `javap` the three shapes and pick an anchor per family; the raised-ceiling re-add is the invariant to preserve, not the injection point. |
| **R-B8** | **~30+ id-keyed `AttributeModifier` sites** must become the UUID form on 1.20.1. Mechanical but wide, and a wrong stable-UUID derivation silently stacks modifiers forever. | **E-ATTR-1**: derive every legacy UUID from `UUID.nameUUIDFromBytes(id.toString().getBytes(UTF_8))` from **one shared helper**, and prove on a 1.20.1 server that relog + death + dimension change leaves exactly one modifier per slot. |
| **R-B9** | **Custom recipes/advancements have no SP precedent.** 20 recipes incl. 2 smithing-transform, whose schema churned ~1.20.5. | **E-RECIPE-1**: datapack-load probe on 1.21.1 and 1.20.1 servers; grep the log for recipe parse failures (which, like tags, are logged and swallowed). |

### Tier 3 — low confidence, cheap to resolve, resolve early

| # | Risk | Resolving experiment |
|---|---|---|
| **R-C1** | **`RadianceLight`** manipulates `ClientLevel.setBlock`, `LevelLightEngine.checkBlock`, `setSectionRangeDirty`, `LightBlock.LEVEL` — the deepest vanilla-internals coupling in either repo, no SP precedent. | **E-LIGHT-1**: `javap` all four on each node. **Recommend gating it off below 1.21.11 unless it comes back clean** — a mis-driven client light engine strobes or corrupts the section cache, and it is a cosmetic feature. |
| **R-C2** | `data/archetypes/tags/damage_type/magical.json` references 8 `minecraft:*` damage types; unresolved entries **cascade silently** through `#tag` refs. | Datapack probe per node (R-16's positive `#tag` probe with a bogus control); mark anything absent `{"required": false}` **unconditionally in the shared tree**. |
| **R-C3** | The 15 `@ModifyVariable`s on `hurtServer` do **not** all commute (Executioner clamp, Sunder shred, instinctiveGuard subtraction are not pure multiplications). Order is guaranteed only by source order today. | Gate #5 of §5.9, per node, from the bytecode export. **This is Archetypes' R-07 and it has no SP precedent.** |
| **R-C4** | `SPECIALITIES_SHIFT = 7` is hand-duplicated in Archetypes while SP now reads its own through `SpecialitiesClient.hudShift()` (conventions §5l) — SP's HUD-bar toggle can set the shift to 0 and Archetypes would still offset by 7, invisibly to both builds. | Route through `SpecialitiesBridge` in Stage 0-C. |
| **R-C5** | **PAL dependency configuration forks and fails *silently***: 26.x merged jars are mojmap with no `Fabric-Mapping-Namespace`; 1.21.11/1.21.1 Fabric jars declare `intermediary`. Putting the mojmap jar through `modImplementation` would have Loom remap it as if intermediary. | Assert the manifest header per node in the build script before choosing the configuration; verify the animations actually play in-game per node. |
| **R-C6** | **Doc drift**: `mc-modding/CLAUDE.md` claims a `specialities:skill_tabs` entrypoint. **It does not exist** (`fabric.mod.json` declares only `specialities:skills`; `grep` finds nothing). The interop surface is smaller than documented. | Fix the doc in the workspace-landing commit. |
| **R-C7** | 77 `method =` targets are **bare names** today; three Fabric nodes remap to intermediary and `1.20.1-forge` remaps members to SRG. | Stage 0-E. Verify in the *shipped* jar against each node's `mappings.tiny` — Loom remaps mixin annotations in place, there is no refmap, so what is in the jar is literally what Mixin uses (§5h). |
| **R-C8** | `Reinforced Straps`: `ItemStack.processDurabilityChange` is `>=1.21.11`, **but `EnchantmentHelper.processDurabilityChange(ServerLevel,ItemStack,int)` exists from 1.21.1** — so 1.21.1 re-roots onto `hurtAndBreak` and only 1.20.1 needs a genuine rewrite. | Downgraded from blocker to fork; flagged because the naive read of the 1.21.11 boundary would have cut it on both legacy pairs. |

---

## 7. What must be answered before Stage 0 starts

1. **PAL on 1.20.1** — Option A or Option B? (§2.2, R-A1)
2. **SP publishing extension** — go-ahead to edit the Specialities repo so it publishes a per-node mavenLocal coordinate? (§3.5, R-A3) If no → fallback `files(...)` and accept the fragility.
3. **`[modproperties]` external-skill API** — sign-off to publish it as an API surface? (§3.5, R-A4) If no → the two loader nodes ship without Spellcasting.
4. **Colossus Protector shield subsystem on 1.21.1 + 1.20.1** — excise, or approximate? (§4.2, R-A5)
5. **Magic Armaments glide on 1.20.1** — excise, or Levitation stand-in? (R-A6)
6. **ESP wall-piercing on 6 of 7 nodes** — accept the loss? (R-B3)

Everything else in this design is decided, or is decided by an experiment that costs less than an hour.