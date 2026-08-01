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

#### 2.2.1 `SUPERSEDES THE TABLE ABOVE` — Option B's loss was filled with vanilla cues (2026-08-01)

Option A was re-checked against the live Modrinth API and is still impossible, not merely expensive: `ha1mEyJS` has **85 versions**, and the union of `game_versions` over all of them contains **no 1.20.x**, while the union of `loaders` is `["fabric","neoforge"]` — **no LexForge build at any MC version**. The sibling `kosmx/playerAnimator` (`gedNE4y2`) *does* cover both 1.20.1 loaders, but it is a different paradigm (`ModifierLayer`/`setAnimation`, no `PlayerAnimationController`/`PlayState`/`triggerAnimation`), it scans the singular resource dir `player_animation` against our plural `player_animations`, and its last 1.20.1 builds are 2023 release candidates. So Option B stands.

What changed is that **Option B is no longer a silent no-op**. The 1.20.1 seam now raises vanilla arm swings server-side at the tick each lost pose would have started. `LivingEntity.swing(InteractionHand, boolean)` was confirmed present on the 1.20.1 mapped jar and confirmed cosmetic by `javap -c`: it writes `swinging`/`swingTime`/`swingingArm` and broadcasts `ClientboundAnimatePacket`, touching no attack-strength timer — so a cue can never become a gameplay change. Six of the ten needed work; four already degraded correctly for free.

| Animation | 1.20.1 outcome | Where |
|---|---|---|
| `dagger_stab` | **already correct, no code** — the driver triggers *off* a vanilla swing and only overrode the pose | — |
| `shield_sweep_{left,right,dual}` | **already correct, no code** — `ShieldBash` already swings the shield-holding hand unconditionally, before the sweep stamp | `ShieldBash.java` |
| `decimate` | main-arm swing at the cleave | `SlayerActives.resolve()` |
| `decimate_charge` | main-arm swing at the release; wind-up still telegraphed by Slowness + `paintWindup`'s ground arc | same site — `resolve()` runs at the blow on both paths, which is why the cue is there and not in `pose()` |
| `quake_charge` | main-arm swing at the slam; the charge keeps its existing sound cue | `CrusherActives.quakeSlam()` |
| `bladestorm` | one swing per volley — 6 over the 60-tick channel, matching the damage cadence | `SlayerTicker` |
| `flame_channel` | throttled swing every 10 ticks (`FLAME_BOLT_PERIOD_TICKS * 5`); **not** per bolt, which would strobe the arm at 10 Hz | `SeekerSpells.channelFlame()` |
| `dark_ritual` | **deliberately none** — a 200-tick static channel has no vanilla analogue, and crouch (the only close cue) changes hitbox height, eye height and speed. Left to its `RESPAWN_ANCHOR_CHARGE`/`_DEPLETE` + `SOUL`/`SCULK_SOUL` FX | `NightForm.beginRitual()`, documented in place |

Implementation notes: every cue sits in the `<1.21` arm of the **existing** `>=1.21` boundary — no new predicate, and `//? if <1.21 {` was already in use at this exact boundary (`LegacyAttributes`, `PlayerMixin`). The ten JSONs keep shipping unused on 1.20.1 rather than being excluded, because the shared resource root feeds every node's jar and excluding them would move all five Fabric jars' resource bytes.

**Verified:** seven jars green. Cue bytecode counted per node — the four new `swing` invocations appear on `1.20.1-fabric` and `1.20.1-forge` and on no other node. Non-regression gate re-run before/after across the five nodes that must not move: **instruction-identical** (26.2 244/244, 26.1 244/244, 1.21.11 250/250, 1.21.1 251/251, 1.21.1-neoforge 254/254) and **resource-byte-identical**.

⚠ **Release metadata is deliberately untouched and needs a decision.** These cues do not add a PAL dependency to either 1.20.1 node — there is nothing to depend on — so the per-node dependency sets in §4 stand as they are: PAL required only where `deps.pal` exists, i.e. still neither 1.20.1 node. Nothing here changes what any node uploads.

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
| `Player.doSweepAttack(Entity,float,DamageSource,float)` | present | present on 1.21.11 with the SAME descriptor, **absent on 1.21.1 and 1.20.1** — they spell it `sweepAttack()`, no parameters and no source in scope | the sweep knockback stash, §5.4.1. This is why that block's predicate is the conjunction `>=1.21.11 && <26.2` rather than a bare `<26.2` |
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

**STAGE 4 ADDITIONS to the `>=1.21.11` row — measured on the node.** Same rule as Stages 2
and 3: a boundary a `//?` block relies on is written down in the same commit that first uses
it. **No new predicate was invented for this node either**, and one existing conjunction
(`>=1.21.2 && <26.2`) gains a sibling (`>=1.21.11 && <26.2`) — both are conjunctions of
frozen predicates, not synonyms.

⚠ **A caveat that applies to this whole table and did not apply to Stages 2 and 3.** Between
1.21.1 and 1.21.11 there are eight Minecraft releases and this machine has an artifact for
neither end of most of them, so every row below is BRACKETED (present on 1.21.11, absent on
1.21.1) rather than pinpointed. That is enough to classify all seven registered nodes
correctly, and it is why these sit in the `>=1.21.11` row rather than getting one of their
own. Where Skill Proficiencies' frozen table already names the API — `hurtServer`,
`InteractionResult` returns — the existing `>=1.21.2` row is used instead and NOT duplicated
here.

*Vanilla:*

| API | 1.21.11 and up | 1.21.1 and below | Used by |
|---|---|---|---|
| `world.item.ToolMaterial` (record) + `Item.Properties.setId`/`repairable`/`enchantable` | that | **`Tier` interface + `Tiers` enum + the `TieredItem`/`SwordItem` class hierarchy**; no id on Properties, no repair or enchantability component | `ModItems`. Copper has NO `Tiers` constant — copper tools ship with `ToolMaterial` — so the copper greatsword and dagger carry a hand-written `Tier` whose six values are read off 26.x's own `ToolMaterial.COPPER` |
| `Player.attack`'s sweep gate | the item's own sword profile | **`getItemInHand(MAIN_HAND).getItem() instanceof SwordItem`** (1.21.1 bytecode, offset 415) | why the legacy greatsword/dagger/`MagicSwordItem` are `SwordItem`s and not plain `Item`s. A plain Item would silently lose a sweep the same weapon performs on 26.x |
| `world.item.component.{BlocksAttacks,Consumable,TooltipDisplay}`, `DataComponents.{GLIDER,EQUIPPABLE}`, `world.item.equipment` | present | **absent** | R-A5 and R-A6, §5.5.1 |
| `LivingEntity.applyItemBlocking` | present | **absent** — blocking is a branch inside `hurt` (`isDamageSourceBlocked` + `hurtCurrentlyUsedShield`) | R-A5 |
| `LivingEntity.blockedByItem(LivingEntity)` | that | **`blockedByShield(LivingEntity)`** — same contract, `this` is the ATTACKER and the parameter the BLOCKER (verified in both jars) | Iron Spikes, Braced. NOT part of the excised cluster |
| `LivingEntity.getItemBlockingWith()` | that | **absent**; `isBlocking()` is the same question and exists on every version | Free Hand, ProtectorClash |
| `MobEffect.applyEffectTick`/`applyInstantaneousEffect` | leading `ServerLevel` | **no ServerLevel** | ManaEffects, RadianceEffect, AmnesiaPotions |
| `SoundEvents.SHIELD_BLOCK` | `Holder<SoundEvent>` | **bare `SoundEvent`** | five sites. Checked field by field — `GENERIC_EXPLODE`, `NOTE_BLOCK_PLING` and `RESPAWN_ANCHOR_DEPLETE` were ALREADY holders on 1.21.1, so this is not a blanket rule |
| `DustParticleOptions(int, float)` | that | **`(org.joml.Vector3f, float)`**; vanilla builds it with `Vec3.fromRGB24(rgb)` | 7 sites |
| `Entity.teleportTo(…, float, float, boolean)` | that | **no trailing flag**; `Relative` is `RelativeMovement` (never written — the set is empty at both call sites) | AgilityActives, MagicArmaments |
| `Entity.needsSync` / `Entity.snapTo(DDD)` | those | **`hasImpulse` / `moveTo(DDD)`** | ProjectileMixin, OracleStrikes |
| `Inventory.getSelectedSlot()` | that | **the public `selected` field** | NightForm ×2, MagicArmaments |
| `EntitySpawnReason` + `EntityType.create(Level, reason)` | that | **`MobSpawnType`; `create(Level)`** | OracleStrikes |
| `EntityType.Builder.build(ResourceKey)` | that | **`build(String)`** | ModEntities |
| `Commands.LEVEL_GAMEMASTERS` | a `PermissionCheck` + `Commands.hasPermission(check)` | **a plain `int`**; the predicate is written by hand | ArchetypeCommands |
| `ThrowableItemProjectile(EntityType, LivingEntity, Level, ItemStack)` | that | **no ItemStack**; `setItem` is a separate call, exactly as vanilla's own legacy throwables do it | SpellProjectile |
| `Projectile.deflect`'s 3rd parameter | `EntityReference<Entity>` | **`Entity`** | ProjectileMixin |
| `ItemTags.SPEARS`, `Items.IRON_CHAIN` | present | **absent** / **`Items.CHAIN`** | ModItems, OracleElementalistNodes |
| `Item.inventoryTick(…ServerLevel…, EquipmentSlot)` | that | **`(…Level…, int slot, boolean selected)`** — and it runs on BOTH logical sides, so the side test the modern signature gives away has to be written | MagicSwordItem, MagicBowItem |
| `Item.releaseUsing` returning `boolean` | that | **`void`** | MagicBowItem |
| `Item.appendHoverText(…, TooltipDisplay, Consumer<Component>, …)` | that | **`(…, List<Component>, …)`** — and `List::add` IS a `Consumer<Component>`, so the body stays shared | WandItem |
| `client.renderer.item.properties.numeric.UseDuration` + `world.entity.ItemOwner` | present | **absent** (the item-model property system post-dates 1.21.1) | UseDurationMixin, gated off |
| `GuiGraphics.blit(RenderPipeline, …, x, y, u, v, w, h, regionW, regionH, texW, texH)` | that | **no pipeline, and `(x, y, w, h, u, v, regionW, regionH, texW, texH)`** — both delegate to the same private twelve-argument blit; the TINT parameter is `setColor` STATE below | 17 sites |
| `net.minecraft.util.ARGB` | that | **`net.minecraft.util.FastColor.ARGB32`**, members declared identically | controller `replacements` |
| `client.input.MouseButtonEvent` | `onClick(MouseButtonEvent, boolean)` | **`onClick(double, double)`** | BookmarkTab |
| `GuiGraphics.pose()` | `org.joml.Matrix3x2fStack` (`pushMatrix`/`scale(f,f)`) | **`PoseStack`** (`pushPose`/`scale(f,f,f)`) | ProcIndicatorHud |
| `world.attribute.EnvironmentAttributes` + `Level.environmentAttributes()` | present — **and present on 26.1 too** | **absent**; `Level.isDay()` is what `Mob.isSunBurnTick` reads there | NightForm. ⚠ Written `>=26.2` first and the 26.1 build caught it — the §5k example this port now owns |

**STAGE 4 ADDITIONS to the `>=1.21.2` row — reused, not extended.** `hurtServer` (26 outgoing
call sites plus every handler in LivingEntityMixin, DamageTraceMixin, HardenedMixin and
FlenseMixin) and `Item.use`'s `InteractionResult` vs `InteractionResultHolder<ItemStack>`
(three items plus fabric-api's `UseItemCallback`, whose return type mirrors it) are both
already in Skill Proficiencies' frozen row and are written with that predicate. **The five
`MobEffects` renames ride the same release** and are recorded here rather than given a row:
`SPEED`/`SLOWNESS`/`STRENGTH`/`RESISTANCE` through a controller replacement, `JUMP_BOOST`
through a `//?` because its legacy spelling `JUMP` is a PREFIX of it.

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

#### 5.4.1 Stage 3, as landed — the measured outcome

The node needed **98 `//?` blocks across 26 shared files** (`git diff` against the Stage-2
tip: 28 files touched, 98 blocks added, none removed), two new whole-file-gated compilation
units and one node-script fork. **Two predicates and one conjunction of them**: `>=26.1` for
everything the boundary owns, the pre-existing `>=26.2` in `HudMixin`'s three-arm chain, and
`>=1.21.11 && <26.2` for the sweep stash. No new predicate was invented.

**The extract-vs-immediate split is wide and shallow, exactly as designed — and the fork is
one line wide.** `GuiGraphicsExtractor` and `GuiGraphics` declare `fill` and all five `blit`
overloads identically, so only the type name, `text()`→`drawString()` and
`fakeItem()`→`renderFakeItem()` move. Wrapping only the line that carries the token leaves
every multi-line signature's continuation and every drawing arithmetic expression shared
(conventions §5b). `VanillaUi` carries the explanation for the other eleven files.

**TWO DESIGN PREDICTIONS WERE WRONG, BOTH IN THE SAME DIRECTION — §4.3 over-forecast the
client work.** The render-state architecture and the extract-based particle pipeline both
survive this node intact: `SubmitNodeCollector`, `ThrownItemRenderState`, `AvatarRenderer`,
`FabricRenderState`, `RenderStateDataKey`, `SingleQuadParticle.extract` and both
`extractRotatedQuad` overloads all resolve on 1.21.11 with 26.x's shapes. The three
`RenderLayer`s, `AvatarRendererMixin` and `BulwarkRenderData` needed **zero** changes. The
only delta is a package move, `client.renderer.state.level` → `client.renderer.state`, worth
two import forks. The rewrite §4.3 describes is real but belongs to **Stage 4**.

**THE BOUNDARY NOBODY PREDICTED IS A BUILD-MECHANICS ONE, and it will bite again.** From
1.21.11 down the node runs fabric-loom-remap, which splits split-environment mod jars and
gives `src/main` the `-common` half only, so `src/main` can no longer name
`net.fabricmc.fabric.api.client.…` or `com.specialities.client.…`. Two long-standing
conventions-§5g violations surfaced at once (`FabricNet`'s `ClientPlayNetworking` holder and
`SpecialitiesBridge.hudShift`) and both became client hand-downs. **Standing review test from
here on: a new `src/main` reference to a client-only package compiles green on the two 26.x
nodes and fails only on a remapped one.**

**A whole-file `//?` gate works pointing DOWN as well as up.** `LevelExtractorMixin` is
`>=26.2`; `platform/ClientNetHooks` and `client/ClientHandDown` are `<26.1`. Same trick, same
constraint — no javadoc inside a disabled branch, because a `*/` would close Stonecutter's own
comment early — and it is what keeps the two 26.x jars byte-identical while the node below
gains six classes.

**PAL 1.1.9 needed no source fork, and that was verified rather than assumed** (design §2.1):
`javap -p` on `BXYewCJb` and on 26.1's `SdKAeB6x` shows all six imported types
signature-identical, return types included. Its dependency CONFIGURATION did fork
(`modImplementation`, the jar declares `Fabric-Mapping-Namespace: intermediary`), and Skill
Proficiencies' matching artifact forked with it (`modCompileOnly`).

**Stage 2's knockback residue is half closed.** `Player.doSweepAttack` is wrapped on the two
nodes that have it. `Player.sweepAttack()` on 1.21.1 and 1.20.1 carries no source at all and
is Stage 4's problem; the mob-AI residue stands as accepted, unchanged from Stage 2.

**Gates, all green.** 26.2 byte-identical to the Stage-2 tip (244 classes / 385 resources);
26.1 byte-identical for commit 3-B and a 43-line pure insertion for 3-C, resources untouched;
`:1.21.11-fabric:build` green; 112/112 mixin targets resolve statically against the 1.21.11
mapped jar, client mixins included; the dedicated server boots clean on all three nodes with
the **same 18 common mixin classes**, exit 0, R-16 positive tag probes passing against a live
bogus control, item-registry probes likewise; and the damage-funnel order in `hurtServer` is
**identical on 26.1 and 1.21.11**, `archetypes$flense` still after Skill Proficiencies'
combat multiplier and stealth crit, `traceFinish (500) < hardened (900) < afterDamage (1000)`
at every RETURN.

**One benign log line to expect on this node and NOT to "fix":** `Compatibility level JAVA_21
specified by archetypes.mixins.json is higher than the maximum level supported by this version
of mixin (JAVA_13)`. Loader 0.19.3 carries sponge-mixin 0.8.7 here; the level governs
vouched-for features, not whether ASM can read the class, and all 18 mixins applied in the
same run. Skill Proficiencies records the identical line on `1.20.1-forge`.

### 5.5 Stage 4 — `1.21.1-fabric` — **the biggest single step in the port**

> **STATUS: DONE.** Landed over `4-A`…`4-E3`. The as-built record is §5.5.1 (the common side,
> with Stage 4-D's correction banner), §5.5.2 (the client side, and the 29-target blocker it
> found) and §5.5.3 (the blocker closed and the whole gate set green). The plan below is
> unedited.

Everything at `>=1.21.11` lands at once: `Identifier`→`ResourceLocation` (controller `replacements`, free), `.projectile.arrow` package, the **entire render-state architecture**, `AvatarRenderer`→`PlayerRenderer`, `RenderLayer.submit`→`render`, `FabricRenderState`/`RenderStateDataKey` absent, `HudElementRegistry`→client `GuiMixin`, `KeyMapping.Category`→String, `hurtServer`→`hurt` × **18 + DamageTrace + Flense**, `applyItemBlocking`/`BlocksAttacks` gone, `Consumable`/`FoodProperties.onConsume` gone, `Player.isSweepAttack`/`canGlide` gone, `LocalPlayer.itemUseSpeedMultiplier` gone, `MobEffectInstance.tickServer`→`tick`, `ItemStack.processDurabilityChange` gone (but `EnchantmentHelper.processDurabilityChange` **survives** — re-root, don't rewrite), `UseDuration`→`ItemProperties`, `ARGB`→`FastColor.ARGB32`, jspecify→jetbrains (import-only fork), `Toast` split.

**Split into 4a (common) and 4b (client), landed as separate node-internal milestones with their own gates.** 4b is larger than 4a.

**Lanes, 4a:** the 18-handler `hurtServer` fork (one lane, sequential, it is one file); the shield-cluster excision decision; the `Consumable`/`FoodProperties` three-way; arrow/projectile package + `deflect` three-way.
**Lanes, 4b:** client `GuiMixin` (SP's, extended); the three `RenderLayer`s + `AvatarRendererMixin` + `SpellProjectileRenderer` (render-state rewrite); ESP two-channel rebuild; `Toast` + `BookmarkTab` + screens; particles.

#### 5.5.1 Stage 4, as landed at commit `4-B1` — **what was believed done, and what the Stage-4-D gate found**

Three commits: `4-A` (registration), `4-A2` (the common side), `4-B1` (the client side's
mechanical boundary). **`:1.21.1-fabric:compileJava` is green; `compileClientJava` is not.**

> ⚠ **CORRECTION, written by Stage 4-D and left here rather than editing the claim away.**
> "The whole common side" was true of everything **javac can see** and false of everything it
> cannot. `compileJava` going green proved the imports, the types and the call shapes; it
> proved nothing about the mixin `method =` and `@At target =` STRINGS, which are strings.
> The static resolution gate (`arch-gate/mixincheck.py`, run for the first time on this node
> in Stage 4-D) finds **29 unresolved common-tree targets** — the whole `hurtServer` family
> among them. See §5.5.2's blocker inventory. The lesson is the one R-20 already states in
> another key: **a build-shaped gate cannot close a string-shaped question**, and on this node
> the two are 29 apart.
The three nodes above are byte-identical to the Stage-3 tip after every commit
(385/385 resources, 244/244/250 classes, zero instruction diffs), re-measured before each
commit rather than after.

**The scale, measured rather than forecast.** The common side needed the `hurtServer`
boundary at 26 outgoing call sites and every handler, plus 24 further API deltas, and it
closed at zero errors. The client side started at 227 compile errors and is at 166; the 61
that are gone were spellings, and the 166 that remain are one architecture.

**R-A5 IS APPLIED, AND THE DESIGN'S ROW NAMED THREE OF ITS FOUR MEMBERS.** Instinctive Guard,
Bulwark (Omni Block), Immovable Object **and Unstoppable Force (Siegebreaker)** all resolve
the question "how much would this shield have stopped", and below 1.21.11 vanilla does not
ask it anywhere: `applyItemBlocking` does not exist, blocking is a branch inside `hurt`, and
the disable path is `Player.disableShield()` plus a raw `ItemCooldowns` write — two
chokepoints rather than the one Immovable Object's promise depends on. Siegebreaker is a
Colossus CRUSHER node and could have been re-rooted onto `isDamageSourceBlocked`; it was not,
because its whole authored point is MEETING Immovable Object, and one of that pair working
while the other silently does not is worse than neither.

The nodes stay **purchasable** — each sits mid-tree with children beyond it — and their
descriptions gain "(Inactive on this Minecraft version.)" through a `processResources`
filter keyed on the full `.desc` key. `//?` cannot do it: Stonecutter never processes
`.json`.

> ⚠ **CORRECTION, written later and left here rather than editing the claim away: R-A5's row
> was FOUR and is now TWO.** The load-bearing sentence above — "the disable path is
> `Player.disableShield()` plus a raw `ItemCooldowns` write — two chokepoints rather than the
> one Immovable Object's promise depends on" — is measurement-wrong on both halves.
>
> * The `ItemCooldowns.addCooldown` **is the body of** `disableShield`, not a second path.
>   `disableShield` is cooldown + `stopUsingItem` + `broadcastEntityEvent(this, 30)` — byte 30
>   being `SoundEvents.SHIELD_BREAK` in `LivingEntity.handleEntityEvent`, i.e. the same three
>   effects `BlocksAttacks.disable` has above the boundary.
> * `disableShield` is named by **exactly one class in the whole jar** on every legacy target:
>   `Player` itself, one call, from `Player.blockUsingShield`. (Constant-pool scan of all 6,136
>   classes of the mapped 1.21.1 jar and all 5,448 of the 1.20.1 one; NeoForge 21.1.243 and
>   LexForge 47.4.22 patch the *body* and keep the single call site — `m_36384_`, two refs.)
>   Vanilla reaches it whenever `attacker.canDisableShield()` is true: the axe and the Warden,
>   which is exactly what the lang string names.
>
> So **Immovable Object** re-roots onto `Player.disableShield`'s head
> (`PlayerMixin.archetypes$immovableObject`, an arity fork — `()V` at `>=1.21`, `(Z)V` below —
> both delegating to the same `ColossusProtector.immovableObject` the 26.x host asks), and with
> the pair whole again **Unstoppable Force (Siegebreaker)** takes the re-rooting this section
> declined: `@ModifyExpressionValue` on the `isDamageSourceBlocked` call inside
> `hurt(DamageSource,F)Z` (`LivingEntityMixin`, legacy arm). That one boolean gates the entire
> blocked branch — `hurtCurrentlyUsedShield`, `amount = 0`, and `blockUsingShield` → the Iron
> Spikes/Braced hook — so answering it `false` reproduces every clause of the 26.x contract
> (R-20) where a `@ModifyVariable` on `amount` would reproduce one of four. Offsets measured:
> 95 on both legacy Fabric nodes, 156 on NeoForge (feeding `CommonHooks.onDamageBlock`), 106 on
> LexForge (feeding `ForgeHooks.onShieldBlock`) — both loaders wrap the branch body and leave
> the question alone, so one arm covers all four nodes.
>
> **Still excised, and for the reason that survives:** Instinctive Guard and Bulwark (Omni
> Block). Those two are a NUMBER taken off a blocked hit, and below the boundary blocking has
> no number — the answer is the whole hit or nothing. Their two `.desc` keys and R-A6's
> Levitation key are all that is left in `inertNodeKeys`.
>
> **One residual degradation, on 1.20.1 only:** `Items.MACE` does not exist there, so
> `WeaponClass.of` can never answer `MACE` and Unstoppable Force is the *unarmed* half of its
> promise on both 1.20.1 nodes. The lang string ("Your mace and unarmed attacks break through
> blocking.") over-promises there by one clause; a per-node lang override is the fix if it is
> judged worth one.

> ⚠⚠ **SECOND CORRECTION, and it empties the row: R-A5 IS ZERO AND `inertNodeKeys` IS GONE.**
> The paragraph immediately above kept two nodes excised on the sentence "below the boundary
> blocking has no number — the answer is the whole hit or nothing". That sentence is *true*
> and the conclusion drawn from it is *wrong*, because the 26.x number is not an arbitrary
> one: for a vanilla shield it **is** the whole hit.
>
> * **Instinctive Guard.** Dumped `BlocksAttacks*` out of the 26.2 common jar plus the vanilla
>   shield's own component factory. The shield is
>   `BlocksAttacks(0.25, 1.0, [DamageReduction(90°, ∅, base 0, factor 1)], ItemDamageFunction(3,1,1), #bypasses_shield…)`,
>   and `DamageReduction.resolve` at the literal `0.0` angle this node passes is
>   `clamp(0 + 1×dmg, 0, dmg)` — i.e. **`blockable == amount`, always**. The durability rule is
>   the same identity: `ItemDamageFunction.apply(f)` is `f < 3 ? 0 : floor(1 + f)`, and legacy
>   `Player.hurtCurrentlyUsedShield` is `if (amount >= 3) { int i = 1 + floor(amount); … }` —
>   equal for every `x >= 0`. So the legacy arm reproduces the node EXACTLY, not approximately.
>   The reimplemented predicate is vanilla's own `isDamageSourceBlocked` clause list: KEEP the
>   piercing arrow, KEEP `#bypasses_shield` (which *is* the shield's `bypassedBy()` there), KEEP
>   `getSourcePosition() == null` (load-bearing — 26.2's `bypassed_by` gained cactus/campfire/
>   dry_out/hot_floor/in_fire/lava/sweet_berry_bush/sulfur_cube_hot, all null-position, so the
>   clause restores parity by another route), INVERT `isBlocking()` into the head as a gate, DROP
>   the facing test. One residual: `lightning_bolt` is in 26.2's `bypassed_by` and not in the
>   legacy tag, so a legacy carried shield soaks lightning — which is what a *raised* vanilla
>   shield does on 1.21.1/1.20.1. That is the node keeping its own version's contract.
>   **Side effect worth naming: on the four legacy nodes this handler is a strictly pure
>   multiplication** (`blockable == amount` always), so it commutes with SP's three `hurtServer`
>   multipliers and drops off R-C3's non-commuting list there. A strengthening, not a risk.
> * **Omni Block.** The claim that excised it — "the facing check is mixed into the same
>   expression as the shield test, there is no `Math.acos` whose result is only the angle" — is
>   **refuted by the bytecode**. `isDamageSourceBlocked` is six clauses in sequence, each with
>   its own jump to a shared `iconst_0; ireturn` tail, and the facing test is the LAST basic
>   block. Its entire contribution is one `Vec3.dot`, which occurs **exactly once in the method
>   and exactly once in the whole `LivingEntity` class** on all four legacy targets (1.21.1-fab
>   / NF offset 111, 1.20.1-fab / FG offset 107, trailing `dconst_0; dcmpg; ifge`). So
>   `@ModifyExpressionValue` returning any negative double defeats the arc and nothing else,
>   while clauses 1–6 still gate — precisely what forcing `acos` to 0 does above the boundary.
>   Both loaders leave `isDamageSourceBlocked` unpatched and wrap only the CALLER
>   (`CommonHooks.onDamageBlock` / `ForgeHooks.onShieldBlock`), so their shield-block events
>   still see a consistent story. Ordering comes out right: callee resolves before caller, so
>   Omni Block forces the arc, Siegebreaker then flips the boolean, and Immovable Object can
>   still refuse the disable — the authored clash, intact.
>
> `inertNodeKeys` is therefore **removed from all three node scripts**, not merely emptied; the
> shape to reuse if a key ever has to come back is documented over the fabric script's
> Unstoppable-Force mace filter, which is the same machinery and is still live.
>
> **The 1.20.1 mace over-promise named above is CLOSED**, by that filter:
> `macelessSiegebreaker = sc.current.parsed < "1.21"` rewrites "Your mace and unarmed attacks"
> to "Your unarmed attacks" on the two 1.20.1 nodes only. Not a per-node `en_us.json` override,
> deliberately — the shared resource root ships into every node's jar, so a copy would move all
> five Fabric jars' resource bytes.

**Not excised, each verified rather than assumed:** Iron Spikes and Braced (`blockedByItem`
is `blockedByShield` here, same contract), Free Hand (`isBlocking()` is the same question),
Ironclad, Hearty Meal, Well Fed.

**R-A6's BOUNDARY IS WRONG IN THE DESIGN AND IS CORRECTED HERE.** §6 says the GLIDER
component is missing "on 1.20.1". Measured: `DataComponents.GLIDER`, `DataComponents.EQUIPPABLE`
and the whole `world.item.equipment` package are absent from the 1.21.1 jar too. Magic
Armaments' glide is excised on **both** legacy Fabric nodes — and not reimplemented, because
the obvious substitute (overriding `Player.canGlide`) is the server crash that method's own
javadoc documents.

> ⚠⚠ **CORRECTION: R-A6 IS CLOSED TOO, AND THE CRASH PREMISE IS 1.21.11+-ONLY.** The boundary
> correction above stands; the excision it justified does not. Three measurements:
>
> 1. **The crash cannot happen below the boundary.** There is no glider-slot list and no
>    `Util.getRandom` anywhere in the legacy `updateFallFlying` — it reads the CHEST stack
>    directly. `Util.getRandom` appears in `LivingEntity` only inside `tickEffects()` on
>    1.21.1-fabric and NeoForge, and not at all on 1.20.1-fabric or LexForge. `canGlide` does
>    not exist as a method on any of the four (`javap -p` on `Player` and `LivingEntity`: 0
>    hits). There is no hook there to be afraid of.
> 2. **A server-only fix would be a total no-op, not a rubber-band.** `LocalPlayer.aiStep`
>    INLINES the chest test before it will call `tryToStartFallFlying()` or send
>    `START_FALL_FLYING` — `chest.is(Items.ELYTRA) && ElytraItem.isFlyEnabled(chest)` on
>    Fabric, `chest.canElytraFly(this)` on both loaders. Without a client half the packet is
>    never sent. That is a GATE, not a prediction mismatch.
> 3. **Once started there is no prediction problem at all.** `LivingEntity.travel`'s glide
>    branch is gated on `isFallFlying()` = shared flag 7 alone; the client never writes it
>    (both the `updateFallFlying` write and `travel`'s landing clear sit behind
>    `if (!level.isClientSide)`), every clear reaches the owner via
>    `ServerEntity.sendDirtyEntityData`, and `handleMovePlayer` picks its 300-vs-100 cap off
>    the SERVER's flag — so no "moved too quickly" either.
>
> **The route taken: wrap the CHEST-slot READ, hand vanilla a stand-in elytra.** The boolean
> forks per loader; `getItemBySlot(EquipmentSlot)ItemStack` does not — it is present exactly
> once in each target method on all four arms, same structural position, same owner, so the
> handler is one shape with **zero annotation fork**. Three anchors, because the client gate is
> where the node actually died:
>
> | # | Target | `@At` owner | offset 1.21.1-fab / 1.20.1-fab / NF 21.1 / FG 47.4 |
> |---|---|---|---|
> | 1 | `Player.tryToStartFallFlying()Z` | `Player` | 35 / 35 / 35 / 35 |
> | 2 | `LivingEntity.updateFallFlying()V` (private) | `LivingEntity` | 39 / 39 / 39 / 39 |
> | 3 | `LocalPlayer.aiStep()V` | `LocalPlayer` | 858 / 825 / 956 / 924 |
>
> One occurrence each ⇒ no ordinal, and `injectors.defaultRequire: 1` is the detector. Neither
> `ServerPlayer` nor `LocalPlayer` overrides `tryToStartFallFlying`/`startFallFlying`/
> `stopFallFlying`/`updateFallFlying`, so anchors 1–2 cover both logical sides from `src/main`.
> The predicate (`ModItems.isSummoned(mainHand)` + `NodePurchases.owned`) is identical on both
> sides and reads only synced state — `ARMAMENTS_WAND` is server-only and deliberately not
> consulted. Everything downstream is stock vanilla: deploy gesture, packet, flag 7, physics,
> firework boosts, landing, the `fallFlyTicks` lean, the `ElytraOnPlayerSoundInstance` wind.
> `ElytraLayer` keys on `Items.ELYTRA` in the CHEST, so **no wings are drawn** — which is
> exactly what 26.x looks like, because its EQUIPPABLE names MAINHAND.
>
> Residual, stated: deploy LATENCY (the client's main-hand slot arrives a tick or two after
> `start()` swaps the wand, so worst case is one missed jump press), and on the two loader
> nodes another mod's `canElytraFly` chestpiece is shadowed for the duration of the channel.

**Two re-rootings rather than excisions, both reproducing the CONTRACT (R-20).** Hearty Meal
moves from `Consumable.onConsume` to a `@WrapOperation` on `ItemStack.finishUsingItem` —
after the item's own effects, so milk cannot wipe what milk just granted, and against a copy
taken BEFORE the call, because `ItemStack.getItem()` returns `Items.AIR` at count zero on
this version. Well Fed moves from `FoodProperties.onConsume` to
`Player.eat(Level,ItemStack,FoodProperties)`; the WRAPPED call is the same
`FoodData.eat(FoodProperties)` on every node, so the invariant is untouched and only the
host moves.

**THE GATE CAUGHT A REAL REGRESSION, and it is why it runs before the commit.** The knockback
stash was first refactored so both legacy arms delegated to one `@Unique` implementation —
correct by conventions §5a, and it added a method and a delegation to **26.1's** transformed
`LivingEntityMixin`, which came back one instruction-diff dirty. The two arms now carry their
own bodies. That is also the honest shape: only the `hurt` arm needs the `isClientSide`
early-out, because only `hurt` runs on both logical sides — and `KnockbackSource` is a static
whose contract is "server thread only", so a client-thread push in singleplayer would race it.

**FOUR MECHANISM FINDINGS the stages below inherit:**

1. **A line comment inside a DISABLED arm but outside its `/* */` is EATEN when the arm is
   enabled** — Stonecutter strips a leading `//` as part of un-commenting, so the note ends
   up as bare text in the generated Java. Notes go above the whole chain. (New; Stage 2's
   `*/` trap is the other half of the same family and bites javadoc in any disabled arm, not
   only whole-file gates.)
2. **A textual `replacements` rule is SAFER than N hand-written else arms for a pure
   rename** — its failure mode is a mangled identifier, i.e. a compile error, where N arms
   can silently drift. That is why the `MobEffects` renames and the projectile sub-package
   moves are rules. **But the rule must use whole class names, never a package prefix**
   (a prefix rule is directional and would rewrite `projectile.Projectile` into a package
   that does not contain it), **and it cannot be used at all when one spelling is a PREFIX
   of the other** — `MobEffects.JUMP` vs `JUMP_BOOST` is the worked example.
3. **The byte-identity gate forbids ordinary refactoring of shared code.** Hoisting a
   repeated accessor into a local, extracting a helper, or reordering a resource file all
   move a prior node's bytes. Where a fork would otherwise duplicate a balance expression,
   the shape that works is to fork the SIGNATURE and the ACCESSOR line and leave the formula
   outside the block (`ModItems.baseDamageFor`). Exactly one expression in this stage is
   written twice (`ModItems.daggerSwingDamage`) and it carries a ⚠ saying so.
4. **The client mixin config's LAST array element cannot be stripped by line-blanking** —
   it leaves a trailing comma — and the shared array cannot be reordered either, because
   that moves three prior nodes' resource bytes. This node therefore takes a per-node
   override at `versions/1.21.1-fabric/src/client/resources/`, with a README beside it.
   Stage 5 will need its own.

**STILL OPEN, and it is one thing rather than a list: the render-state data path.**
`AvatarRenderState`, `SubmitNodeCollector`, `ItemStackRenderState`, `FabricRenderState` /
`RenderStateDataKey` and the extract-based particle pipeline all stop at 1.21.11. Below it a
`RenderLayer` is `render(PoseStack, MultiBufferSource, int light, T entity, float×6)` against
the ENTITY, so the layers read their data directly instead of out of a render state — which
collapses the indirection rather than reimplementing it, exactly as §4.3 predicted. The files:
`BulwarkShieldLayer`, `BladestormLayer`, `NightEyesLayer`, `BulwarkRenderData`,
`AvatarRendererMixin` (→ `PlayerRenderer`), `SpellProjectileRenderer`,
`GreatswordSweepParticle` (→ `render(VertexConsumer, Camera, float)`), plus what hangs off
them: `ArchetypesClient`'s HUD/layer/particle registrations (`HudElementRegistry` → a client
`GuiMixin`; `KeyMapping.Category` → a String), `ArchetypeLevelUpToast`, the two screens'
remaining calls, and ESP's two-channel outline rebuild. **The pieces for ESP are already
scouted:** membership is `Minecraft.shouldEntityAppearGlowing` (put it in the existing
`MinecraftMixin`'s legacy arm) and colour is `Entity.getTeamColor()` (retarget the existing
client `EntityRendererMixin`, the same trick `ConsumableMixin` uses) — doing it that way adds
**zero** new mixin-config entries, which matters because of finding 4 above.
`EntityRenderState.NO_OUTLINE` is a compile-time `0`.

#### 5.5.2 Stage 4-D, as landed — **the client half is done; the common half's strings are not**

Six commits, `4-D1` … `4-D6`. **The client half of `1.21.1-fabric` is complete**: 166 compile
errors → 0, `:1.21.1-fabric:build` green and producing a jar, and the client tree's mixin
targets all resolve against the mapped 1.21.1 jar. The node still cannot boot, for a reason
that is entirely on the common side and is inventoried at the end of this section.

**The three nodes above did not move a byte** — re-measured after `4-D5` and again after
`4-D6`, not once at the end: 26.2 244/244, 26.1 244/244, 1.21.11 250/250 classes
instruction-identical, 385/385 resources byte-identical on all three.

**THE COLLAPSE, WHICH IS THE HEADLINE.** §4.3 predicted that below 1.21.11 a `RenderLayer`
gets the ENTITY rather than an extracted state and that the fork would therefore *collapse*
the indirection rather than reimplement it. That is exactly what happened, and it is why the
below-boundary arms are **shorter** than the arms they fork from:

| Above 1.21.11 | Below | Landed as |
|---|---|---|
| `BulwarkRenderData`'s two `RenderStateDataKey`s | nothing to hang them on | whole compilation unit gated; no type declared, so no `.class` |
| `BladestormLayer.ACTIVE`/`.GHOST` | same | fields gated, class kept |
| `AvatarRendererMixin.extractRenderState` (4 handoffs) | each layer reads the attachment itself | the injection is gated; only the ctor's layer registration survives |
| `ItemStackRenderState.submit(...)` | `ItemRenderer.renderStatic(LivingEntity, ItemStack, ItemDisplayContext, Z, PoseStack, MultiBufferSource, Level, III)` | per-layer |
| `getItemBlockingWith()` | `getUseItem()` while `isBlocking()` | measured absent below 1.21.11 |
| `SubmitNodeCollector.submitModelPart` | `ModelPart.render(pose, buffer, light, overlay, colour)` off `MultiBufferSource.getBuffer(RenderType.eyes(..))` | `NightEyesLayer` |

**The one piece that could NOT collapse, and why it is a different shape.** Ghost Armor was
four assignments blanking `state.*Equipment`. Below the boundary there are no such fields —
every layer reads equipment off the entity with `getItemBySlot` at draw time — so the layers
themselves are cancelled, by `GhostArmorMixin`. One mixin covers three targets because
`HumanoidArmorLayer`, `CustomHeadLayer` and `ElytraLayer` all bind their entity parameter at
`LivingEntity` and therefore share one erased `render` descriptor (`javap -p -s`). It is
deliberately not a `@ModifyReturnValue` on `LivingEntity.getItemBySlot`: that would blank the
armour in the inventory screen, the tooltips, the durability bar and the armour HUD row too.

**ESP: the two-channel rebuild deviates from §5.5.1's plan, and the deviation is the finding.**
§5.5.1 proposed putting membership in `MinecraftMixin`'s legacy arm and retargeting the
existing `EntityRendererMixin` onto `Entity.getTeamColor()`, on the grounds that it adds zero
mixin-config entries. **Measured, that plan changes behaviour.** A global hook on
`Minecraft.shouldEntityAppearGlowing` reaches four other callers on 1.21.1 —
`LivingEntityRenderer` and the mushroom/sheep/slime/snow-golem layers — where the answer
decides whether an **invisible** entity is drawn as an outline-only silhouette instead of not
at all. The render-state write it replaces has no such reach: on the newer nodes
`outlineColor` is read only by the outline collector. So a sensed invisible mob would have
started painting a silhouette in the world on this node and on no other.

The landed shape instead wraps **both** calls scoped to
`LevelRenderer.renderLevel(DeltaTracker,Z,Camera,GameRenderer,LightTexture,Matrix4f,Matrix4f)V`,
where vanilla makes them back to back (offsets 977 and 1001, `javap -c`) and where each occurs
**exactly once in the whole class**, so `defaultRequire: 1` pins them. `@WrapOperation` and not
`@ModifyExpressionValue`, because the wrap handler is handed the RECEIVER and the receiver is
the entity — `renderLevel` is a thousand-instruction method and picking the right `Entity`
local out of it with `@Local` would be a guess. `EntityRendererMixin` becomes an
above-1.21.11-only compilation unit and `LevelRendererMixin` replaces it; exactly one of the
two is in any jar. **Cost: one config entry swapped, two added — finding 4's per-node override
absorbs it, which is what that override is for.**

**HUD: eight registrations, four anchors.** No `hud` package in fabric-rendering-v1 below
1.21.11, so `ArchetypesClient`'s eight calls move wholesale into a client `GuiMixin` (Skill
Proficiencies' §5h shape, doing a different job — Archetypes raises no vanilla element).

| 26.x call | 1.21.1 anchor |
|---|---|
| `attachElementAfter(HOTBAR, …)` ×3 | TAIL of `renderItemHotbar(GuiGraphics,DeltaTracker)V` |
| `attachElementAfter(MISC_OVERLAYS, …)` ×2 | TAIL of `renderCameraOverlays(GuiGraphics,DeltaTracker)V` — vignette+spyglass+pumpkin+frost+portal, i.e. the ids fabric groups under that name, so the two washes stay UNDER the bars |
| `attachElementAfter(FOOD_BAR, banked_hunger)` **+** `replaceElement(FOOD_BAR, …)` | ONE `@WrapMethod` on `renderFood(GuiGraphics,Player,II)V` |
| `replaceElement(AIR_BAR, …)` | `@WrapOperation` on the two `blitSprite` calls inside `renderPlayerHealth(GuiGraphics)V` |

Two of those carry a finding each.

* **The food row is one handler, not a wrap plus a TAIL inject.** `@WrapMethod` renames its
  target, so a separate injector into the same method binds to whichever copy the transformer
  left it and the ordering of that is not something to rely on. Written as one handler the
  semantics are the newer nodes' exactly.
* **There is no air-bubble METHOD below the boundary.** The bubbles are drawn inline in
  `renderPlayerHealth` under the "air" profiler section, and those two `blitSprite` calls
  (offsets 582 and 609) are the ONLY blits in that method — armour, hearts and food are drawn
  by `renderArmor` / `renderHearts` / `renderFood`. Subtracting the shift from the blit's `y`
  is arithmetically what translating the element by `-y` does above the boundary, and unlike a
  push/pop pair split across two injection points it cannot leave an unbalanced pose stack.

**THREE MECHANISM FINDINGS the stages below inherit, on top of §5.5.1's four:**

5. **A javadoc inside a disabled arm is SAFE** — Stonecutter escalates the nested `/* */` to
   `/^ ^/` and de-escalates it on generation (verified end to end on `BulwarkRenderData`). So
   §5.5.1's finding 1 is about `//` line comments *outside* the arm's comment block, and it is
   not a general ban on documenting a fork's else arm. Document inside it.
6. **`ManaHud.airBarShift()` is the shape for "the mixin needs a package-private answer".**
   A client mixin lives in `…client.mixin` and cannot see `…client`'s package-private members.
   Widening the member would change three prior nodes' access flags. Declaring the accessor
   **inside the fork** costs those nodes nothing and keeps the number it returns next to the
   gate that decides it.
7. **Three DOUBLED `//? if` directives were sitting in the tree** — a directive nested inside
   an identical copy of itself, in `ArchetypeScreen`, `ArchetypePickerScreen` and `VanillaUi`.
   All three generated correct code on every node, which is why nothing caught them. They are
   removed. Worth a grep after any large fork pass: `a.strip() == b.strip()` on adjacent
   `//? if` lines.

**THE BLOCKER — CLOSED BY STAGE 4-E (§5.5.3), and the table below is now the as-built record.**
`arch-gate/mixincheck.py` on `1.21.1-fabric` read **106 targets checked, 29 unresolved, all
common-tree**; it now reads **107 checked, ALL RESOLVE**. The three prior nodes and the shared
tree against 26.2 reported ALL RESOLVE throughout, so this was a 1.21.1-only gap and never a
tooling artefact. The last column was written as a forecast and has been replaced by what
landed; where the two differ, the difference is called out.

| Count | File | Unresolved target | AS BUILT (Stage 4-E) |
|---|---|---|---|
| 17 | `LivingEntityMixin` | `hurtServer(ServerLevel,DamageSource,F)Z` | `>=1.21.2` → `hurt(DamageSource,F)Z`, `level` dropped, `isClientSide()` early-out. **SIGNATURE-ONLY fork**: annotation + parameter list + opening brace, body shared outside the block |
| 2 | `DamageTraceMixin` | same | same |
| 1 | `FlenseMixin` | same | same |
| 1 | `HardenedMixin` | same | same |
| 2 | `MobEffectInstanceMixin` | `tickServer(ServerLevel,LivingEntity,Runnable)Z` | → `tick(LivingEntity,Runnable)Z` — **plus an early-out the forecast did not ask for**: `tick` runs on BOTH sides (`baseTick` → `tickEffects()`, offset 765, unguarded) and the flag is a plain static |
| 1 | `FoodDataMixin` | `tick(ServerPlayer)V` | → `tick(Player)V` — **and the `@At` INVOKE owner with it**, `Player.heal(F)V`, offsets 163/221. Owner-only drift is invisible to `mixincheck` (ServerPlayer inherits `heal`) and to `require` |
| 1 | `HealOrHarmMobEffectMixin` | `applyInstantenousEffect(ServerLevel,Entity,Entity,LivingEntity,I,D)V` | → the no-`ServerLevel` form **and a different spelling**: `applyInstantenousEffect` below, `applyInstantaneousEffect` above. ⚠ the pass's only whole-method fork (`@WrapMethod` descriptor = target's) |
| 1 | `CrossbowItemMixin` | `releaseUsing(ItemStack,Level,LivingEntity,I)Z` | returns `V` below; CallbackInfoReturnable → CallbackInfo, fully qualified so the arm above keeps its import list |
| 1 | `ItemStackMixin` | `processDurabilityChange(I,ServerLevel,ServerPlayer)I` | **re-rooted onto `hurtAndBreak(I,ServerLevel,ServerPlayer,Consumer)V`** (`>=1.21.11`). Contract holds: `hurtWithoutBreaking` does not exist on 1.21.1 and the other overload delegates (offset 51), so `hurtAndBreak` IS the single funnel there |
| 1 | `PlayerMixin` | `isSweepAttack(ZZZ)Z` | **`@ModifyExpressionValue` on the `getItemInHand(MAIN_HAND)` at offset 405 of `attack`** — one occurrence, its result consumed only by `instanceof SwordItem`. An empty hand makes the sweep FLAG false, which is the contract (the flag also gates the ordinary hit sound) |
| 1 | `PlayerMixin` | `@At` INVOKE `Player.causeExtraKnockback(Entity,F,Vec3)V` | **one un-ordinal'd `@WrapOperation` on `LivingEntity.knockback(DDD)V` inside `attack`** — offsets 542 and 794, i.e. the extra shove AND the sweep's. Covers what the 26.1 `causeExtraKnockback` and `doSweepAttack` arms cover between them, and CLOSES the 1.21.1 sweep leg Stage 3 logged as open |

The first 21 are mechanical, and cheap for a reason worth stating: **Stage 0-D already split
every handler into an annotated shell and a `@Unique` implementation, and the implementations
do not take `level` — they derive it from `((LivingEntity)(Object) this).level()`.** So the
fork is the shell's annotation and parameter list, nothing else, and `LivingEntityMixin`
already carries a worked example of the shape in its knockback-stash block (`//? if <1.21.2`,
with the `isClientSide()` early-out and the reason it must NOT be shared with the arm above).

The last four are re-rootings or excisions and R-20 governs them: **a re-rooted event must
reproduce the event's own CONTRACT, not merely fire somewhere plausible.**

**Do not attempt the dedicated-server smoke, the mixin export audit, the funnel-ordering audit
or the cross-mod ordering gate on this node until that table is empty.** With
`injectors.defaultRequire: 1` — which stays on — the node aborts at mixin apply, one failure at
a time, which is the slowest possible way to discover 29 of them. `mixincheck.py` reports all
29 in one pass and costs seconds; run it first, every time.

*(That instruction was followed and it held up: the table emptied in three commits and the
whole blocked gate set then passed on the first attempt. Keep the ordering for Stage 5.)*

#### 5.5.3 Stage 4-E, as landed — **the node is DONE: 107 targets resolve, the gate set is green**

Three commits, `4-E1` … `4-E3`, and then the gate set §5.5.2 blocked. `mixincheck.py` on
`1.21.1-fabric`: **107 targets checked, ALL RESOLVE**. 26.1 106 ALL RESOLVE, 1.21.11 106 ALL
RESOLVE, the shared tree against 26.2 101 ALL RESOLVE. The three prior nodes were re-measured
**before each of the three commits** and did not move: 26.2 244/244, 26.1 244/244, 1.21.11
250/250 classes instruction-identical, 385/385 resources byte-identical, every time.

**THE SHAPE THE 21 MECHANICAL ONES TOOK, and it is the reusable half.** The fork is the
SIGNATURE ONLY — annotation, parameter list, opening brace — with the shared body outside the
block. §5.5.1 finding 3 predicted why it has to be: hoisting either the early-out or the
delegation into a shared `@Unique` helper adds a method and a call to three prior nodes'
transformed classes, which is what made 26.1 come back one instruction-diff dirty when the
knockback stash was first written that way. One implementation, four nodes, no balance
expression written twice — and `FlenseMixin`, whose handler has no `…Impl` to delegate to at
all, forks identically because the body never has to move.

The legacy arm's `isClientSide()` early-out is not tidiness. `hurt` runs on both logical sides
and vanilla's own `return false` for a client level is at offset 10-21 of the 1.21.1
`LivingEntity.hurt`, i.e. AFTER every HEAD injection; ten of the impls open with
`(ServerLevel) this.level()`. Two handlers legitimately do without one and both are measured
rather than argued: `FoodData.tick` is called at offset 197 of `Player.aiStep` inside the
`!isClientSide` branch opening at 186, and both `attack` knockback sites sit inside the
`if (target.hurt(...))` branch that a client never enters.

**THREE THINGS THE FORECAST DID NOT CONTAIN**, all of them found by reading the jar rather than
the diff — see the table above for each: the `Player.heal` OWNER moving with `FoodData.tick`
(a drift neither `mixincheck` nor `require` can see, because `ServerPlayer` inherits `heal`);
`applyInstantenousEffect`'s misspelling; and `MobEffectInstance.tick` running on both sides
where `tickServer` cannot.

**THE GATE SET, run in §5.9's order.**

1. *Prior-node identity* — three nodes, three times, all zero (above).
2. *Dedicated-server smoke*, new rig `scratchpad/smoke-1.21.1`: fabric server 1.21.1 / loader
   0.16.14, fabric-api 0.116.14+1.21.1, Skill Proficiencies 1.6.0+1.21.1, PAL `FkO8Scek`
   (1.1.5), 47 mods, JDK 21. `Done (5.316s)`, clean `stop`, **exit 0**. Zero mixin errors, zero
   `missing following references`, zero `Couldn't load tag`. Tag probes: five positive
   (`greatswords`, `daggers`, `wands`, `fruit`, `meat`) silent, bogus control
   `#archetypes:does_not_exist` → `Unknown item tag`. Item probes: five positive silent, bogus
   control → `Unknown item`. **17 of the 19 listed common mixins were applied, and that is
   parity, not a shortfall**: `FoodDataMixin` and `PlayerAdvancementsMixin` are unapplied on
   the 1.21.11 smoke too (their targets are not loaded by a headless boot with no player), and
   the eighteenth on that node is `BlocksAttacksMixin`, which R-A5 excises here.
3. *Funnel-ordering audit* (`funnel.sh`, now parameterised by target name — below 1.21.2 the
   funnel is `hurt`, intermediary `method_5643`). **The 22-entry HEAD sequence is identical to
   1.21.11's, name for name**: `traceBegin` (500) first, then the seventeen shapers in source
   order, then Skill Proficiencies' three, then `archetypes$flense` (1500) last. RETURN
   clusters: `traceFinish` (500) < `hardened` (900) < `fabric-entity-events-v1$afterDamage`
   (1000). The only two diffs against 1.21.11 are structural and expected: no
   `stashBlockKnockback` (no `applyItemBlocking` on this node), and one EXTRA `traceFinish` /
   `hardened` RETURN pair, because `hurt` has one more return site than `hurtServer` — the
   client early-out. That extra pair is exactly what the handlers' own early-out no-ops.
4. *Cross-mod ordering* (§5.9 gate 6). First half PROVEN by the funnel: SP's
   `applyCombatDamage` / `uncapFallProtection` / `stealthCrit` at 326/334/342, `flense` at 349.
   Second half **NOT APPLICABLE on this node family, and symmetrically so**: Archetypes'
   `UseDurationMixin` and Skill Proficiencies' are both `>=1.21.11`, because
   `client.renderer.item.properties.numeric.UseDuration` does not exist on 1.21.1 — read out of
   both shipped 1.21.1 jars' client mixin configs, not assumed.
5. *Mixin export audit*, `javap -c` on the merged classes, and the two re-roots got the
   R-07-style proof because both are anchored on a call read:
   * merged `Player`: `getItemInHand` at 405 → `daggersNeverSweep` at 410 → `astore 14` →
     `getItem` → `instanceof SwordItem` → `istore 11` (the flag), and slot 14 is overwritten by
     a float at 430, so the substituted stack reaches the sweep test and nothing else.
   * merged `Player`: `stashAttackKnockback` at 587 and 895 — **exactly two**, the second
     immediately followed by the sweep loop's `hurt`. The `@Local` is a `LocalRefImpl`
     initialised from and disposed back to slot 4, which is the `damageSource` local at both.
   * merged `ItemStack`: the wrap sits in `method_7956` =
     `hurtAndBreak(I,ServerLevel,ServerPlayer,Consumer)V`, with the `@Local` carrying `aload_3`.
   * merged `LivingEntity`: the 35 handler calls of the funnel above.

**One benign log line, do NOT "fix" it**: `Compatibility level JAVA_21 specified by
archetypes.mixins.json is higher than the maximum level supported by this version of mixin
(JAVA_13)`. Loader 0.16.14's mixin says the same about **every** fabric-api module in the same
boot; it governs vouched-for features, not whether ASM can read the class. SP's §Environment
note records the identical line on `1.20.1-forge`. Never write `JAVA_13` into a shared config.

**OPEN FOR STAGE 5, and both are named where they live.** `FoodDataMixin` and
`PlayerAdvancementsMixin` are never exercised by a headless smoke on any node — the in-game
pass (§5.9 gate 7) is the only thing that covers them, and `FoodDataMixin`'s owner fork landed
in this stage, so it is the one to actually watch. And the two new `<1.21.11` arms in
`PlayerMixin` are written to cover 1.20.1 as well as 1.21.1: if that version spells either
anchor differently, `injectors.defaultRequire: 1` fails loudly at Stage 5's first boot rather
than dropping the node in silence. That is deliberate — a predicate scoped to `>=1.21` would
have been silent instead.

### 5.6 Stage 5 — `1.20.1-fabric` — Java 17, no sprite atlas, no attachment sync, no PAL

`>=1.21` and `>=1.20.5` land together: id-keyed `AttributeModifier` → UUID form at **~30+ call sites**, data components, GUI sprite atlas gone entirely, payload stack gone, **attachment sync gone entirely** (the `ALL_TRACKING` fallback of §3.1 is exercised for the first time), Java 25→17 for real, R-16 tag rename, `Entity.deflection` gone, `AbstractArrow.getDefaultGravity`/`pickupItemStack` gone, `Equippable`/`GLIDER` gone (Magic Armaments' glide needs a Levitation-based reimplementation or excision — **USER DECISION**), `FoodData.eat(FoodProperties)` gone, **PAL gone**.

**Lanes:** 5-A attachment-sync fallback (the big new infrastructure); 5-B the `AttributeModifier` sweep (mechanical, wide); 5-C client raw-sheet blits + 4-method `GuiMixin`; 5-D PAL excision/Option-A driver; 5-E resources (tags, models, recipes).

#### 5.6.1 Stage 5, as landed — **the node builds; the smoke has not run**

Three commits, `5-A` … `5-C`. `./gradlew buildAndCollect` produces **five jars** unqualified,
`mixincheck` on `1.20.1-fabric` reports **99 targets, ALL RESOLVE** across both trees, and the
three nodes that were meant to stay still did: 26.2 244/244, 26.1 244/244, 1.21.11 250/250
classes instruction-identical, 385/385 resources byte-identical, measured on real jars before
and after every commit.

**THE 1.21.1 NODE MOVED, AND IT IS A FIX.** Its classes are still 250/250 identical, but 28
resources leave and 4 arrive. The item MODEL DEFINITION layer (`assets/<ns>/items/`) is
`>=1.21.4`, so every definition Stage 4 shipped on that node was inert — and the four items
with no hand-authored model behind them (`skill_token`, `skill_token_60`,
`spellcasting_tome_25`, `spellcasting_tome_100`) have had **no model at all since Stage 4-B1**.
Skill Proficiencies' Stage 5 found the identical bug and its note is why this one was looked
for: invisible to every build, visible only to a launched client. The 24 definitions with a
legacy twin are dropped (relocating them would overwrite a hand-authored model with a
generated one) and the 4 without are rewritten.

**THE TWO BOUNDARIES, as they actually divided the work.** `>=1.20.5` took the payload stack
(eleven records gated out whole, `FabricNet` swapped for the RAW channel API), attachment SYNC
(`platform/LegacyStateSync`), `AFTER_DAMAGE` (`platform/LegacyDamageEvents` + a TAIL hook on
`hurt`), `AdvancementHolder`, `SynchedEntityData.Builder`, `getDefaultGravity`, `TooltipContext`
and the Java-17 `instanceof` ceiling. `>=1.21` took the id-keyed `AttributeModifier`
(`LegacyAttributes` + three controller replacements), data components, `Holder<MobEffect>`,
NBT enchantments, the `Tier`/`SwordItem` hierarchy (`items/LegacySword`), `PotionContents`,
`DeltaTracker`, the GUI sprite atlas and the whole deflection cluster.

**SEVEN EXCISIONS, and the count is the finding.** Each is display-only or has no host at all,
and each is written where it lives rather than in a list nobody reads: the deflection pair
(Reflection, Spell Reflect), Mind over Matter's armour piercing, Rapid Reload's and Deadeye's
charge speed, the undead grey hearts, the undead food-row suppression, the air-bar shift, and
the sweep particle's three-quad fan. Swift Shadow is the one that MOVED instead: it re-roots
onto `LocalPlayer.aiStep`'s sneak clamp, which is where 1.21's `SNEAKING_SPEED` attribute is
read on every version.

**TRAPS MEASURED HERE, both of which cost real time.**

1. A nested `//?` arm inside an already-disabled one must carry the ESCALATED markers
   (`/^ … ^/`). Stonecutter fails the task with `Unclosed scope` — loud, but only once the
   affected source set is regenerated, and **the client set is generated by
   `stonecutterPrepareClient`, not by `stonecutterGenerate`**: a `--rerun-tasks` on the latter
   will happily leave a stale client tree behind and a green compile that proves nothing.
2. `processResources` transforms are copy-spec ACTIONS and therefore invisible to up-to-date
   checking (conventions §5j). Two of Stage 5's three shipped the previous run's output on
   their first build; each now carries an `inputs.property`. And the PAL `depends` line is the
   LAST entry of its object, so blanking it left a trailing comma — `remapJar` refused the jar
   rather than shipping it, which is the right kind of failure but is not what SP's mechanism
   does, because its stripped line had a sibling after it.

**WHAT IS NOT DONE, and it is the whole of §5.9 past gate 1.** No dedicated-server smoke has
run on this node — there is no 1.20.1 rig on this machine yet (the four that exist are 26.2,
26.1, 1.21.11 and 1.21.1), so the funnel-ordering audit, the mixin export audit, the tag and
item probes and the cross-mod ordering gate are all still ahead. Two of them are pointed at
specific claims Stage 5 makes and should be run first:

* the funnel audit, because the `AFTER_DAMAGE` substitute is a NEW handler on `hurt`'s TAIL at
  the default priority and its position against `HardenedMixin`'s pinned 900 is exactly what
  §5.9 gate 5 exists to check;
* the injection count, because `injectors.defaultRequire: 1` is the only thing that will say
  whether the two Stage-4 arms written blind for this node (`LocalPlayerMixin`'s `0.2F`
  constant, `PlayerMixin`'s sweep anchors) resolve here — the 0.2F one is measured and does,
  the sweep ones are not.

Also untested on this node: `ALL_TRACKING` sync (R-B1's two-client proof), the 20 recipes and
their advancements (R-B9's datapack probe — the smithing schema churned at 1.20.5), and the
`magical` damage-type tag's eight entries (R-C2).

#### 5.6.2 Stage 5-D, as landed — **the rig existed for twenty minutes and found five bugs**

The 1.20.1 rig is `scratchpad/smoke-1.20.1` (fabric server 1.20.1 / loader 0.16.10,
fabric-api 0.92.11, Skill Proficiencies 1.6.0+1.20.1, **no PAL**, `eula.txt` copied from the
1.21.1 rig and never freshly accepted); the driver is `arch-gate/smoke1201.sh`, Java **17**.
The node now **boots clean, stops clean, exits 0**, with **17/19 common mixins applied** (the
two absent are `FoodDataMixin` and `PlayerAdvancementsMixin`, whose target classes no
player-free server ever loads), **zero** injection failures, **zero** `Couldn't load tag`,
**zero** datapack parse errors, and the only two failing probes are the two bogus controls.

**FIVE BUGS, and every one of them was invisible to the build.** In the order the rig found
them, because the order is the argument for building a rig per node early:

1. **`PlayerMixin.archetypes$stashAttackKnockback` did not resolve.** Its `@Local DamageSource`
   has nothing to bind to: 1.20.1 does not hoist the attack's source into a local, it builds
   `damageSources().playerAttack(this)` inline at BOTH sites. Stage 4 predicted a failure here
   and predicted the wrong cause (it guessed the sweep would be spelled differently; the sweep
   is identical — two `knockback(DDD)V` calls at 479 and 714, both inside the
   `if (target.hurt(...))` branch). Fixed by narrowing that arm to `>=1.21` and adding a
   `<1.21` arm that REBUILDS the source with vanilla's own expression — faithful, because the
   impl reads only `getEntity()`/`getDirectEntity()` off it.
2. **`AbstractArrowAccessor` died in `Apply Accessors`.** `pickupItemStack` is a field from
   1.21 and a `protected abstract getPickupItem()` below it. The build had SAID so — `Cannot
   remap pickupItemStack because it does not exist in any of the targets` — as a warning, and
   shipped the jar. **A remap warning on a mixin member name is a boot failure that has not
   happened yet.** Fixed with an `@Invoker` arm.
3. **`archetypes$spellbowGravity` did not resolve.** Stage 5 wrote `doubleValue = 0.05` from a
   javap dump it read three characters into. The literal is `0.05000000074505806d` — vanilla
   writes the gravity as a FLOAT and javac widens it. Now spelled `(double) 0.05F`, which
   produces the identical bits and does not invite the same misreading back.
4. **R-B9, and it was never a 1.20.1 problem alone.** The datapack registry directories are
   PLURAL below 1.21 (`recipes/`, `advancements/`), so the 20 recipes and 20 advancements sat
   where nothing reads them — no error, just absence. Relocating them then exposed the real
   finding: **the recipe SCHEMA breaks on 1.21.1 too**, and the 1.21.1 smoke Stage 4 called
   green had been logging twenty `Parsing error loading recipe` lines that the gate never
   grepped for. Ingredients stopped being bare id strings at 1.21.2, `result` carries `count`
   below it and is `{"item": …}` below 1.21, `ItemPredicate.items` must be an ARRAY below
   1.21, and the seven `#minecraft:*_tool_materials` tags do not exist below 1.21.11 at all.
   Fixed with per-node resource overrides (20 recipes on 1.21.1; 19 recipes + 19 advancements
   + the `meat` tag on 1.20.1), each README'd beside the files. `breeze_wand` is EXCISED on
   1.20.1: `minecraft:breeze_rod` does not exist there and substituting another ingredient
   would make one item cost something different on one node.
5. **R-B1's client half had never been written.** `platform/LegacyStateSync` encodes on
   change, routes by scope, replays on start-tracking and replays on join — and
   `grep -rn LegacyStateSync src/client` returned **nothing**. Every packet it sent was
   dropped by a client that had not registered the channel. There is no error for this
   anywhere: server state stays perfect, so every server-authoritative behaviour keeps
   working and the whole VISIBLE half is blank on that one node. Fixed by
   `client/LegacyStateSyncClient` (a `<1.20.5` whole-file block, registered from
   `ArchetypesClient` — it needs `Minecraft` to resolve the entity id, so it cannot live
   behind the seam).

**GATES 2/3/5/6, as measured.** The funnel on 1.20.1 is the 1.21.1 sequence plus exactly two
entries — `archetypes$afterDamage` then `specialities$afterDamageXp`, both mods' AFTER_DAMAGE
substitutes, and both land where §5.9 gate 5 wanted them: `traceFinish` (500) < `hardened`
(900) < `afterDamage` (1000) at **every** RETURN. Cross-mod ordering holds: SP's
`applyCombatDamage` / `uncapFallProtection` / `stealthCrit` sit between `barbarian` and
`archetypes$flense`, i.e. Flense is still last. `mixincheck` reports **ALL RESOLVE** on all
five nodes (101 / 105 / 104 / 104 / 99 targets).

**WHAT A HEADLESS SERVER STILL CANNOT REACH, stated rather than glossed.**

* **The client mixin tree is unexercised** — a dedicated server loads none of it. The three
  anchors that worried Stage 4 are therefore proven STATICALLY, by `javap` on the node's own
  mojmap jar, and that is the strongest available: `LocalPlayer.aiStep` holds exactly two
  `float 0.2f` (offsets 178 and 193) and they are the only two in the whole class, and
  exactly ONE `Mth.clamp(FFF)F` (the other two clamps are the `(III)I` overload).
* **R-B1's two-client proof is not done and cannot be done headless.** What IS proven, in the
  shipped 1.20.1 bytecode: the routing graph end to end — `set`/`remove` call `push`, `push`
  reads `key.sync()` and fans out through `PlayerLookup.tracking` plus the owner,
  `register` arms `EntityTrackingEvents.START_TRACKING`, `resyncAll`/`syncOnStartTracking`
  call `replay`, and `LegacyStateSyncClient.install` registers `ClientPlayNetworking` on
  `LegacyStateSync.CHANNEL` — plus key-table parity BY CONSTRUCTION (16 `ALL_TRACKING` / 31
  `TARGET_ONLY` / 47 wire-carrying, one declaration read by both the `syncWith` arm and the
  legacy arm). **What remains is exactly the visual: A's flag on B's screen.** It belongs to
  §5.9 gate 7.
* **A per-recipe positive probe needs a connected player.** `/recipe give` resolves its
  target selector before its recipe id, so on an empty server every id — bogus control
  included — answers `No player was found`. The recipes are instead proven by a three-state
  argument: singular directory → 0 parse errors and 0 recipes read; plural directory with the
  26.2 schema → **20** parse errors, which is the positive control that the loader now reads
  them; plural directory with the legacy schema → 0. The advancements have a direct count:
  1271 → **1290**, exactly the 19 files.

**R-C2 IS CLOSED, and needed no change.** All eight `minecraft:*` damage types the `magical`
tag references exist on 1.20.1 and on 1.21.1 (checked against `data/minecraft/damage_type/`
in both server jars). No `required: false` is warranted.

**PRIOR-NODE IDENTITY.** 26.2 244/244, 26.1 244/244, 1.21.11 250/250 classes instruction-
identical and **385/385 resources byte-identical**. 1.21.1: 250/250 classes identical, +0/-0
shape, and **exactly the 20 recipe resources changed** — the R-B9 fix and nothing else.

### 5.7 Stage 6 — the loader axis

`1.21.1-neoforge` and `1.20.1-forge`, registered as **two separate bottleneck commits**. Copy SP's `build.neoforge.gradle.kts` / `build.forge.gradle.kts` / `buildSrc` mutex. **R-10 JiJ is mandatory** (18 injectors). **R-11's HUD answers transfer**: NeoForge `wrapLayer` with `GuiMixin.class` present-but-unlisted; Forge per-node `ForgeGuiMixin`. **R-22 applies and widens** (§3.4). The 12 new event arms (§3.4) and the four new registration seams land here. `[modproperties]` interop needs the sign-off from §3.5.

⚠ **The two loader nodes are the only place Archetypes' 5 PAL drivers have a NeoForge artifact (`ReDTdA0C`) and, on Forge, none at all** — so `1.20.1-forge` inherits `1.20.1-fabric`'s animation decision automatically, and `1.21.1-neoforge` inherits `1.21.1-fabric`'s 1.1.x source fork.

#### 5.7.1 Stage 6 groundwork, as landed — the scaffold, and the two lanes it opens

Four commits, mirroring Skill Proficiencies' own Stage-6 groundwork (`653fc5b`, `3213a15`, `b0a4ea4`, `2201836`) file for file:

1. **buildSrc** — the NeoForge artifact mutex, from the template (R-14).
2. **Register `1.21.1-neoforge`** — bottleneck files + `build.neoforge.gradle.kts`.
3. **Register `1.20.1-forge`** — bottleneck files + `build.forge.gradle.kts` + `versions/1.20.1-forge/gradle.properties`.
4. **Shared-tree loader forks** — 41 files, wiring only.

**R-01 was already handled** (the `[fabric]` table) and is now proven in the real tree rather than in a probe: at configure, the five Fabric nodes print `Fabric Loom: 1.17.17`, `1.20.1-forge` prints `Architectury Loom: 1.17.491`, and `1.21.1-neoforge` prints no loom at all.

**Gate at every commit, all four green:** five Fabric nodes build; every class instruction-identical and every resource byte-identical to the pre-Stage-6 jars (244 / 244 / 250 / 250 / 240 classes; 385 / 385 / 385 / 361 / 359 resources; +0/-0 shape); both loader nodes' `stonecutterGenerate` + `stonecutterGenerateClient` green; **zero live `net.fabricmc` references anywhere in either loader node's generated tree**, outside the `Fabric*.java` their node scripts exclude by glob.

##### The event helpers the two lanes must write

SP's ten give **five reused unchanged** — `playerJoin`, `endServerTick`, item registration, `creativeTabOutput`, `registerCommands` — plus its three client screen helpers (`afterScreenInit`, `addWidget`, `afterScreenTick`), because the bookmark surface is the same one. **Twelve are new**, and every one of them has its contract written at the call site rather than left to be inferred:

| Helper | Sites | Contract the caller depends on |
|---|---|---|
| `allowDeath` | 2 | **false = the entity SURVIVES at current health**, not that damage was voided. Carries Last Shadow. An inverted helper is an immortality bug. |
| `afterDeath` | 4 | fires once, server-side, after the death is final. Same `LivingDeathEvent` as `allowDeath` on both loaders — post must not fire when the death was cancelled. Must support several listeners in registration order. |
| `entityLoad` | 2 | once per entity added to a **server** level; the `tickCount > 0` filter stays in the shared body. |
| `serverStopped` / `serverStopping` | 1 + 1 | genuinely different events. `PENDING.clear()` needs STOPPING (while the server still owns the schedule); the bleed list needs STOPPED. |
| `denyUseItem` / `denyUseBlock` | 1 + 1 | a **deny** predicate — the loaders' interact events are cancellable and have no result to return. `true` cancels that hand and nothing else. |
| `brewingRecipes` | 2 | takes `platform/BrewingSink`, so the loader axis shares ONE copy of the recipe table. |
| `creativeTabBuilder` | 1 | returns a `CreativeModeTab.Builder`; the whole title/icon/`displayItems` chain stays shared. |
| `endClientTick` | 6 files | **LexForge's `TickEvent.ClientTickEvent` fires TWICE per tick.** An unchecked phase drains every `consumeClick` twice and sends two payloads per keypress. |
| `registerKeyMapping` | 7 keys | must return **the same instance**, or seven keys are bound in the controls screen and dead in game. |
| `entityRenderer`, `particleProvider` | 1 + 1 | MOD event bus, client side. |

`afterDamage` is **not** in that list for the Forge node: `platform/LegacyDamageEvents` is a `<1.20.5` whole-file unit, so `1.20.1-forge` already has it, and the shared `LivingEntityMixin.archetypes$afterDamage` already fires it on the same `hurt(DamageSource,F)Z`. Only NeoForge needs a helper.

##### Ownership — 6a and 6b are disjoint

**Stage 6a owns `1.21.1-neoforge`:**

```
build.neoforge.gradle.kts                                    (edit)
src/main/java/com/archetypes/platform/NeoForgeArchetypeStore.java
src/main/java/com/archetypes/platform/NeoForgeNet.java
src/main/java/com/archetypes/platform/NeoForgePlatform.java
src/main/java/com/archetypes/platform/NeoForgeEvents.java
src/main/java/com/archetypes/platform/ArchetypesNeoForge.java        @Mod entrypoint
src/client/java/com/archetypes/client/NeoForgeClientEvents.java
src/client/java/com/archetypes/client/NeoForge*.java                 the second, Dist.CLIENT entrypoint
versions/1.21.1-neoforge/src/**                                      every per-node override
```

**Stage 6b owns `1.20.1-forge`:**

```
build.forge.gradle.kts                                       (edit)
versions/1.20.1-forge/gradle.properties                      (edit)
src/main/java/com/archetypes/platform/ForgeArchetypeStore.java
src/main/java/com/archetypes/platform/ForgeNet.java
src/main/java/com/archetypes/platform/ForgePlatform.java
src/main/java/com/archetypes/platform/ForgeEvents.java
src/main/java/com/archetypes/platform/ArchetypesForge.java           @Mod entrypoint
src/client/java/com/archetypes/client/ForgeClientEvents.java
src/client/java/com/archetypes/client/Forge*.java                    the client bootstrap
versions/1.20.1-forge/src/**                                         every per-node override,
                                                                     incl. client/mixin/ForgeGuiMixin.java
```

`Forge*` is anchored and does not match `NeoForge*`; the two `versions/` trees and the two node scripts do not overlap. **Neither lane may edit shared `src/`, the three bottleneck files, `build.fabric.gradle.kts`, either shared mixin config, or `buildSrc/`** — a further shared fork is an integration commit with one writer.

##### What each lane owes beyond the seam, and must not discover late

- **The per-node resource overrides do not inherit.** Each lane must copy its Fabric sibling's — `1.21.1-neoforge` needs the **20 recipe overrides** at `versions/1.21.1-fabric/src/main/resources/data/archetypes/recipe/`, and `1.20.1-forge` needs 1.20.1-fabric's **20 recipes + 20 recipe-advancements + `tags/item/meat.json`**. Nothing warns about this: a datapack directory the loader does not walk is an absence, not a parse failure (R-B9's lesson, one node over).
- **`pack.mcmeta` is load-bearing**, not cosmetic: without it neither loader mounts `assets/` or `data/` and the R-16 cascade fires silently.
- **The client mixin config is a per-node override on both.** NeoForge's is 1.21.1-fabric's list **minus `GuiMixin`** (R-11: the HUD is `RegisterGuiLayersEvent.wrapLayer`; the class ships but must not be listed). Forge's is 1.20.1-fabric's list with `GuiMixin` **replaced** by a per-node `ForgeGuiMixin`. And LexForge has no per-config `environment` key, so a client mixin left in the common list is a dedicated-server boot crash under `defaultRequire: 1`.
- **R-22 has not been resolved, only recorded.** Experiment E-R22-1 — which of `Item.<init>`, `EntityType.Builder.build`, `SimpleParticleType`, the three `MobEffect`s and the four `Potion`s asks for an intrusive holder — is each lane's first job, before the seam. The failure is `<clinit>`-time and lands far from the call that moved.
- **`new SimpleParticleType(false)` is unverified on both loaders.** Vanilla's constructor is protected (that is why fabric-api ships `simple()`); if the loader does not widen it, the answer is an access transformer or an accessor mixin, never `alwaysShow = true`.
- **`1.20.1-forge` has no state sync at all.** `platform/LegacyStateSync` is now scoped `fabric && <1.20.5` and its header records what that node owes in its place: 16 `ALL_TRACKING` keys and 31 `TARGET_ONLY` ones, moved onto the `Net` seam with the frozen wire format kept. It is that lane's largest single piece of work, and Stage 5 proved the failure is **silent** — server state stays perfect and the whole visible half is blank, which a dedicated-server smoke cannot see by construction.
- **The Forge client bootstrap.** Skill Proficiencies shipped its 1.20.1-forge jar with every client helper present and nothing invoking them. `@Mod.EventBusSubscriber(Dist.CLIENT, bus = MOD)` + `FMLClientSetupEvent`, and **the `@SubscribeEvent` method must be public** or it silently never fires.
- **`[modproperties.archetypes] specialities_skills`** goes in each node's metadata file and is blocked on the R-A4 sign-off. Without it, neither loader node registers Spellcasting.

#### 5.7.2 Stage 6 AS BUILT — seven nodes, and what the integration merge had to decide

Three lane commits plus two merges. `a6-neoforge` (the NeoForge half), `b6-forge` (a shared-tree
integration commit, the Forge seam, and the mod-order fix), merged in that order because the
ownership split above made them disjoint — except in one place, recorded below.

**The merge conflict, and the cross-note under it.** Both lanes independently found
`client/RadianceLight.initialize()` registering `ClientTickEvents` unconditionally under a
`fabric`-gated import, and both wrote the SAME three-arm chain; the conflict was two comments,
not two fixes. The real collision was one file further out: Stage 6a rescoped
`ClientHandDown.install()`'s call site AND `SpecialitiesBridge.installClientHudShift` to
`fabric && <26.1` (the split-environment jar is a fabric-loom fact, not a version one), while
Stage 6b had solved the same mismatch for its own node with a per-node
`versions/1.20.1-forge/src/client/java/com/archetypes/client/ClientHandDown.java`. After the
merge that override no longer compiled — `installClientHudShift` does not exist on the loader
axis — and it was deleted rather than re-gated, because Stage 6a's fix is the wider one and
`:1.20.1-forge:compileJava` resolves the direct `SpecialitiesClient.hudShift()` call it relies
on. `LegacyStateSyncClient` stays a per-node override: that node genuinely needs a client
receiver, it just cannot be the fabric-api one.

**Prior-node identity across the whole integration.** Five Fabric jars, pre-merge vs
post-merge: **0 resource differences, +0/−0 class shape, 0 instruction differences** on all
five (244 / 244 / 250 / 250 / 240 classes; 385 / 385 / 385 / 361 / 359 resources). Jar bytes
moved by 3–9 bytes and the cause was measured rather than assumed: rebuilding the 26.2 jar
from the pre-merge `src/` and diffing byte for byte gives exactly five differing classes —
`ArchetypesClient`, `RadianceLight`, `RadianceLight$Placement`, `SpecialitiesBridge$Linked`,
`ItemStackMixin` — and `javap -l` shows the difference is **LineNumberTable entries only**,
i.e. the comment lines Stage 6a/6b added. That is R-20's javac caveat in its second form, and
it is why this gate compares instructions.

**The build.** One unqualified `./gradlew buildAndCollect` → **seven jars**, no file-name
collision (`archetypes-neoforge-…` / `archetypes-forge-…`).

**Both loader servers, on the merged tree.** Headless dedicated servers, `-Dmixin.checks`
`-Dmixin.debug.countInjections` `-Dmixin.debug.export`, each with Skill Proficiencies' matching
loader jar in `mods/`:

| | `1.21.1-neoforge` | `1.20.1-forge` |
|---|---|---|
| Boot / stop / exit | `Done (1.4s)`, clean stop, exit 0 | `Done (2.4s)`, clean stop, exit 0 |
| Common mixins applied | **18** of 19 declared | **17** of 19 declared |
| … vs its same-MC Fabric sibling | sibling applies 17 — this node applies one MORE (`PlayerAdvancementsMixin`; the class loads here and not there) | **exactly the sibling's 17** |
| Never applied | `FoodDataMixin` (target never loads on a playerless server) | `FoodDataMixin`, `PlayerAdvancementsMixin` (same reason) |
| Mixin failures | 0 | 0 |
| Hard ERROR lines | 0 | 0 (4 benign: the two documented LexForge `minVersion` lines, ×2 mods) |
| Probes that resolved | 5/5 item tags, all items | 5/5 item tags, all 28 items, entity type, 4 mob effects, particle |
| Bogus controls that fired | item tag, item | item tag, item, mob effect, particle, entity type |
| Recipes / advancements | `Loaded 1310 recipes` | `Loaded 7 recipes / 1290 advancements` — **byte-for-byte its Fabric sibling's numbers**, which log the same pair |
| MixinExtras | 0.5.3 platform library | **0.5.4 via jar-in-jar** — `Initializing MixinExtras … (version=0.5.4)` in the log (R-10) |
| Interop | `Registered skill 'spellcasting' from archetypes` | same, and `Archetypes initialized` PRECEDES it (the mod-order fix holds) |

⚠ **The `recipe give` / `advancement grant` / potion probes are INCONCLUSIVE on a playerless
server and must not be reported as passes.** `@a` resolves to nobody and brigadier throws "No
player was found" before the id argument is read, so the bogus control does not fire either —
measured on both loader nodes and on both Fabric siblings. What those families rest on instead
is the load-count comparison in the table and the absence of any parse-error line.

**All FIVE Fabric servers re-run on the final 1.2.0 jars too**, so "seven nodes boot" rests on
seven fresh logs rather than four old ones: 26.2 `Done (0.45s)`, 26.1 `(0.40s)`, 1.21.11
`(0.36s)`, 1.21.1 `(1.15s)`, 1.20.1 `(2.73s)`; every one exit 0, zero mixin failures,
Spellcasting registered. The 26.x/1.21.11 nodes apply **18** of the 19 declared common mixins
and the two legacy nodes **17** — the same class-load story as the loader nodes, not a drift.

⚠ **One benign log line named here because nothing else names it**: `[main/ERROR]: No data
fixer registered for spell_projectile`, on `1.21.1-fabric` and `1.20.1-fabric` ONLY. It is
vanilla's own `Util.fetchChoiceType` complaint at `EntityType.Builder.build` time for any
modded entity id — measured absent on 26.2/26.1/1.21.11 (Mojang gated the check higher up) and
absent on both loader nodes. **Do not "fix" it**; the only way to silence it is to register a
DFU schema for a modded entity.

**Pre-remap class parity** (SP's method: compare `versions/<node>/build/classes/java`, because
the shipped jars differ by construction — mojmap vs intermediary vs SRG):

| | `neoforge` vs `1.21.1-fabric` | `forge` vs `1.20.1-fabric` |
|---|---|---|
| `main` byte-identical | 165 / 194 common | 159 / 188 common |
| `main` mixin classes | **18 / 19** | **18 / 19** |
| the one that differs | `ItemStackMixin` — NeoForge split `hurtAndBreak`, so the handler's last parameter is `LivingEntity` where Fabric has `ServerPlayer` | `FoodPropertiesMixin` — LexForge patches `getFoodData().eat(…)` to a three-argument call |
| `client` byte-identical | 37 / 45 | 37 / 41 |
| `client` mixin classes | **9 / 9** | **9 / 9** |
| loader-only classes | 11 main + 3 client | 19 main + 3 client |

The 29 differing common classes are the same list on both loaders (bar the one mixin), and
"loader-forked wiring" is a **measurement**, not a claim. Splitting each differing class into
methods (`javap -c -p -constants`, constant-pool indices AND javap's comment column
normalised — it pads by the width of the index it just printed) gives, on `main`: **336
members identical / 30 differing** on NeoForge and **333 / 31** on Forge. Of those:

* 21 × `initialize()`, 5 × `static {}`, 1 × `onInitialize()` — registration, i.e. the seam.
* `SpecialitiesBridge$Linked.hudShift()` — the hand-down-vs-direct arm, by design.
* `SlayerActives.bladestorm`, `SlayerActives.resolve`, and on Forge `ShadowTicker.invisDuration`
  — **`ldc` vs `ldc_w` and nothing else.** Same constant, same operands, same branch targets;
  the two-byte form is reachable on one node because its constant pool index is under 256.
  Zero balance-relevant instructions differ anywhere.

On `client` the whole delta is registration too: 9 differing members on NeoForge (7 ×
`initialize()`, `onInitializeClient()`, and the screen-init lambda where
`Screens.getButtons`/`ScreenEvents.afterTick` become `NeoForgeClientEvents.addWidget`/
`afterScreenTick` — every line that anchors or draws is untouched), 6 on Forge (the same two
plus `LegacyStateSyncClient`, which is a per-node file by design).

**The damage funnel.** Both loader nodes' transformed `LivingEntity.hurt` matches its same-MC
Fabric sibling **handler for handler** for the first 32 entries — 18 Archetypes shapers, then
Skill Proficiencies' `applyCombatDamage` / `uncapFallProtection` / `stealthCrit`, then
`archetypes$flense`. The only structural difference is ONE extra `traceFinish`(500) →
`hardened`(900) RETURN cluster on each, which is the loader's own early return patched into
`hurt`; both handlers fire at it and in the right order. `1.20.1-forge` additionally carries
`archetypes$afterDamage` → `specialities$afterDamageXp` in the final cluster, in its sibling's
order.

⚠ **One measured, accepted residue on `1.21.1-neoforge` only.** Its AFTER_DAMAGE substitute is
`LivingDamageEvent.Post`, which NeoForge posts from inside `actuallyHurt` — i.e. BEFORE `hurt`
returns, and therefore before `archetypes$hardened`, where fabric-api's TAIL handler runs
after it. `HardenedMixin`'s own javadoc already states why that is not a balance channel
("nothing here reads or writes state either neighbour touches … no AFTER_DAMAGE listener in
this mod reads the plates"), and `NeoForgeEvents.afterDamage` reproduces the event's other
three semantics — the pre-armour figure, the death gate and firing for players — with the
artifact lines that prove each. Recorded because the ORDER is genuinely different, not because
anything measured moved.

**Tuning propagation across SEVEN jars.** One character in `Tuning.slamMultiplier`
(`rank / 3.0F` → `rank / 4.0F`), rebuild, whole-jar compare: **exactly one class changed in
every one of the seven jars — `com.archetypes.Tuning` — and zero resources.** The single
instruction is `ldc float 3.0f` → `ldc float 4.0f` at the same offset on all seven. Reverting
and rebuilding restores all seven to the byte-for-byte snapshots. Run in the main worktree
rather than a scratch one (a cold worktree cannot reuse the loom caches); the revert side of
the proof is what makes that safe, and `git status` was clean before and after.

**Publishing, wired and dry-run only.** `me.modmuss50.mod-publish-plugin` 2.1.1 in all three
node scripts, `order("publishModrinth")` in the controller. Project id **`47EMhuFl`**, read
back off `GET /v2/project/archetypes`. Version numbers: bare on the newest Fabric node,
`+<node key>` below it, whole node directory name on both loader nodes. The **dependency set
is per node and is the one delta from SP**, whose live versions declare none: both live
Archetypes versions declare `P7dR8mSH` (Fabric API, required), `ha1mEyJS` (PAL, required) and
`d4TtjlpN` (Skill Proficiencies, optional) — so Fabric API is declared on the five Fabric nodes
only, PAL only where `deps.pal` exists (i.e. not on either 1.20.1 node, Option B), and Skill
Proficiencies optional everywhere.

### 5.8 Stage 7 — parity review, in-game passes, release

#### 5.8.1 Stage 7, first in-game finding — **the menu-blur phase trap on 1.21.1**

**Reported:** on `1.21.1-fabric` the Archetype PICKER renders fully blurred — panel, text and
item icons alike. 26.2 has been in daily use and is fine.

**The defect, named rather than guessed.** Both screens (`ArchetypePickerScreen`,
`ArchetypeScreen`) draw their whole body and *then* call `super`, with the comment "Widgets
last … anything drawn after it covers the buttons". That is correct at 26.1+, where the draw
method is `extractRenderState` and `Screen.extractRenderState` only walks the renderables. It
is correct on 1.21.11 too. It is **wrong on 1.21.1**, and the reason is a phase contract that
moves twice across the legacy band. Measured in the mapped 1.21.1 client jar:

```
1.21.1     Screen.render          = renderBackground(g,mx,my,a) THEN the renderable walk
           Screen.renderBackground= [renderPanorama if level==null] -> renderBlurredBackground
                                    -> renderMenuBackground
           renderBlurredBackground= GameRenderer.processBlurEffect(f)   <- post-pass over the
                                    then mainRenderTarget.bindWrite       WHOLE main target
1.21.11    Screen.render          = the renderable walk ONLY. The background moved up into the
                                    final renderWithTooltipAndSubtitles, which runs
                                    nextStratum -> renderBackground -> nextStratum -> render
1.20.1     Screen.render          = the renderable walk ONLY, and renderBackground is the
                                    1-arg (GuiGraphics) form that NOTHING calls for you
```

So on 1.21.1 the mod drew its panel, then `super.render(...)` ran `processBlurEffect` over a
framebuffer that already contained it. `GuiGraphics.fill`, `drawString`, `fillGradient` and
`renderItem` all `flushIfUnmanaged` on that version — verified in the same jar — so the
content really is resident when the post-pass runs, which is why *everything* came back
blurred rather than just the parts that happen to flush late.

**A second, quieter defect the same reading exposed: 1.20.1 had no backdrop at all.** There
`Screen.render` never draws one and the screens never called the 1-arg
`renderBackground(GuiGraphics)` themselves, so the world showed through around the panel at
full brightness. That is SP's `>=1.20.5` "`Screen.render` drawing own background" row biting
in the other direction, and no gate could see it either.

**The fix, in the `<1.21.11` arms only.** Vanilla's own shape on 1.21.1 is
`AbstractContainerScreen`: background first, content on top of it, widgets last. Both screens
now draw the background explicitly at the top of the draw method —
`super.renderBackground(g,mx,my,a)` at `>=1.20.5`, `super.renderBackground(g)` below it — and
on 1.21.1 **only**, override `renderBackground` to a no-op so `Screen.render`'s own later call
cannot run the blur a second time over the content and darken it twice. `Screen.render` is
that method's only caller on our instances, so the neutered override costs exactly the
duplicate pass. Predicate bands are the existing frozen rows; **no new vocabulary**.

**Gate, measured.** `26.2` / `26.1` / `1.21.11`: **0 classes changed, 0 resource diffs** — the
proven pipeline is untouched. The four changed jars differ in exactly two classes each
(`ArchetypePickerScreen`, `ArchetypeScreen`), **insertion-only, zero instructions removed**
once bytecode offsets and branch targets are normalised away (`arch-gate/offsetdiff.py`, added
for this — a raw `javap` diff reads a top-of-method insertion as a whole-method rewrite): the
1.21.1 pair gains the 6-instruction `super` call plus an empty `renderBackground`, the 1.20.1
pair gains the 3-instruction 1-arg call. Both loader pairs stay byte-identical to their Fabric
sibling on both screens.

**The lesson, and it is §5.9's item 7 again.** This was invisible to every gate the port
runs — it builds, it resolves, it boots, the bytecode is right, and the two screens are
byte-identical across each loader pair *while being wrong on four nodes*. The bug is not in
what the code does but in **when** it does it, and only a launched client can see a phase.

#### 5.8.2 Stage 7, second in-game finding — **the screens were authored at GUI scale 2**

**Reported:** on `1.21.1-fabric`, at GUI scale 3 the tree screen draws its node squares with
**no icons in them**; at GUI scale 1 the nodes are tiny with huge gaps. Scale 2 is right. The
report is version-shaped only by accident — the layout is shared code with no `//?` in it, so
all seven nodes had it, 26.2 included.

**Not a rendering defect. A units defect, and one the code stated out loud.** Everything a
`Screen` measures is already in GUI-scaled pixels, so a constant like `MAX_NODE = 18` does not
mean "18 pixels of screen", it means "18 pixels *at whatever scale the player picked*". The
tree's geometry was written in those constants, and the two failures are the two ends of that:

```
                       scale 1            scale 2 (authored)   scale 3
surface (user's)       2000x1040          1000x520             665x347
section box            578x815            278x347              178x191
OLD pitch / node       30 / 18 (clamped)  30 / 18              15-20 / 11-16
NEW pitch / node       62-74 / 38-44      27-32 / 16-19        15-19 /  9-11
```

1. **`if (size >= 16)` around the icon draw.** `VanillaUi.nodeIcon` could only draw its native
   16x16 — a sprite blit and an item render side by side, and the item render has no width
   argument — so the tree guarded the call rather than scaling it. At scale 3 the fitted node
   is 11-15px on **eight of the nine base sub-trees** (measured per grid: staff 11, ankh 12,
   bow/dagger/moon 13, sword/mace/flame 15); only the Protector shield, the one 9x9 grid,
   reaches 16. So eight of nine sections drew empty squares. Not a threshold that was
   *too high* — a threshold that should never have existed.
2. **`MAX_SPACING = 30` and `MAX_NODE = 18` bind at scale 1.** Both clamps are absolute, so a
   578x815 section drew the same 258px tree of 18px squares it draws in a 278px one, at half
   the physical size, with the rest of the section empty. The old fit *did* shrink to fit
   (that is why scale 3 stayed on screen) but could never grow.
3. **The pitch was an integer.** `(available + gap) / grid` truncates, and nine columns of a
   shield lose up to 8px off the width — the tree reads as having drifted off its centre.
4. **The section title was a hard 1.5x** with a fixed 22px reserve, so on a narrow surface a
   long epic name ("Colossus Crusher") runs over the divider into the next section.
5. **`ArchetypePickerScreen`'s panel is a fixed 380 wide.** Minecraft's own
   `Window.calculateScale` clamps the GUI scale so the scaled surface is **at least 320x240**
   and gives no upper guarantee, so 380 is off the end of the guaranteed width: at the largest
   scale a player can pick on a small window, `panelLeft()` is negative and the outer cards run
   off both edges. 212 tall clears the 240 floor, so it is a width-only defect.

**The model, and the line it draws.** Chrome and text stay FIXED — header, buttons, epic
switchers, progress bars, tooltips are text-sized things, and vanilla's own screens do not grow
them either; at a smaller GUI scale you are asking for more room, not bigger text. The
constellation is a *diagram*, so it is FLUID and derived from **one** unit, `pitch()`: the cell
at which the largest grid this archetype can show fills the tighter axis of a section. Node
size, halo ring, connection stroke and icon size all come off that unit, clamped on the NODE
and never on the pitch (clamping the pitch up on a cramped screen would push the bottom rows
out through the canvas floor; a node that has stopped shrinking merely closes its gaps). The
unit is sized against every sub-tree the archetype owns, **base and epic**, so nothing can
overflow whatever the switchers are set to and flipping one section does not resize its
neighbours. The picker takes the same treatment one level up: its card width is the unit, a
ceiling of the authored 112 that shrinks to fit, with the ability row and the crest quoted as
fractions of it.

**Icons at any size go through the pose**, and that it works on all four draw pipelines is
measured, not assumed: 26.x `GuiGraphicsExtractor.fakeItem` → `item(...)` builds
`new GuiItemRenderState(new Matrix3x2f(this.pose), …)`; 1.21.11 `GuiGraphics.renderItem` does
the same; 1.21.1 and 1.20.1 `renderItem` `pushPose()` **this.pose** and then translate by
(x+8, y+8) and scale by 16 — so an outer scale multiplies through in every one. The sprite
blits carry the pose the same way. Drawing at 16 and scaling is what keeps the sprite path and
the item path identical.

**Scale 2 is preserved as the reference on purpose.** At an 18px node the ring, the stroke and
the icon inset all evaluate to 1px and the icon comes out at its native 16 — the authored draw,
unchanged. Re-fitted, a scale-2 section lands on pitch 27-32 / node 16-19 against the old fixed
30 / 18.

**Predicates: none added, none moved.** The only new `//?` in the change is `nodeIcon`'s
push/scale/pop on the existing `>=1.21.11` 2-D-pose boundary — the same fork the section title
and the picker's crest already carry. All seven nodes build; both loader pairs' `ArchetypeScreen`,
`ArchetypePickerScreen` and `VanillaUi` stay **byte-identical** to their same-MC Fabric sibling
at the pre-remap class level.

**The lesson, and it is a different one from §5.8.1's.** That bug was a phase — *when* the draw
happens. This one is a **unit**: GUI-scaled pixels are not screen pixels, and any constant in
them is a claim about the player's GUI scale setting. Every gate this port runs is scale-blind
(a headless server has no GUI scale at all, and a bytecode diff cannot see that 18 is the wrong
kind of 18), and the in-game pass that found it was run at ONE scale. **An in-game pass now has
to be run at more than one GUI scale to count** — scale 1, the authored scale, and the largest
the window allows.

#### 5.8.3 Stage 7, third in-game finding — **`blitSprite`'s six-argument form is two different calls**

**Reported:** on `1.21.1-fabric`, Well Fed's banked-hunger halo — the bevelled silver ring
`BankedHungerHud` draws around the drumsticks the bank is holding — does not render. 26.2 is in
daily use and draws it.

**Nothing about the anchor, the state or the sprite was wrong.** All four suspects cleared
before the real one was found, and they are worth recording because the next report of "a HUD
element is missing on a legacy node" will start at the same four:

* **The anchor fires.** Stage 4-D5 replaced the eight `HudElementRegistry` calls with a client
  `GuiMixin` on four vanilla anchors, and the halo's is the `@WrapMethod` on
  `Gui.renderFood(GuiGraphics,Player,II)V`. That method exists on 1.21.1 (`javap -p -s`) and is
  called unconditionally from `renderPlayerHealth` (offset 426), and `injectors.defaultRequire`
  is 1, so a target that did not resolve would be a boot crash rather than a silent skip.
* **The state is there.** The bank is server-side `FoodPropertiesMixin` raising `foodLevel` past
  20 on the `>=1.20.5` arm, and `ClientboundSetHealthPacket` carries the raised number to the
  owning client on every node.
* **The sprite is there.** `processResources`' sprite relocation is gated `< 1.21`, so on this
  node both rings ship at `textures/gui/sprites/hud/` — confirmed in the built jar — and a
  sprite that failed to resolve would draw the missing-texture chequer, not nothing.
* **Blend does not matter.** Vanilla's `renderFood` ends with `RenderSystem.disableBlend()`
  (offset 198, unconditional), so our draw does run with blending off — but both rings are
  hard-edged (alpha is 0 or 255 and nothing between, measured on the PNGs) and 1.21.1's
  `position_tex.fsh` discards `a == 0.0`. The margin cannot show through.

**The defect is one argument, and it is the shape `HudMixin`'s header already warned about.**
Stage 4 wrote the 1.21.1 arm of the halo blit by taking the 1.21.11 call and dropping only the
`RenderPipeline`, and left a comment asserting that "the sprite/x/y/w/h/colour tail is declared
identically on both, tint included". It is not. Measured with `javap -c` on both mojmap jars:

```
1.21.11  blitSprite(RenderPipeline, Identifier, I,I,I,I,I)
         four-int form appends iconst_m1        -> ints are (x, y, width, height, COLOUR)
1.21.1   blitSprite(Identifier,               I,I,I,I,I)
         four-int form inserts iconst_0 THIRD  -> ints are (x, y, Z, width, height)
         private tail: if (width==0||height==0) return;
                       innerBlit(atlas, x, x+width, y, y+height, z, u0,u1,v0,v1)
```

The colour parameter does not exist below 1.21.11; a **z offset** sits in the middle instead. So
the copied-down call passed `RING_SPRITE` (11) as z and `NO_TINT` (`0xFFFFFFFF`, i.e. **-1**) as
the height. A negative height clears the zero guard and reaches `innerBlit` with `y1 = y`,
`y2 = y - 1` — an inverted quad one pixel tall with the whole 11x11 ring squeezed into it.
Nothing readable is drawn, which is exactly how it was reported.

**The fix is the four-int overload**, which is what vanilla's own `renderFood`, `renderArmor` and
`renderHeart` call on this node and the same overload `HudMixin`'s legacy arm wraps. It passes
z = 0 — the z the drumsticks under it are drawn at, and vanilla overdraws `food_full` on
`food_empty` at that same z in the same loop — and the tint is dropped rather than moved:
`NO_TINT` is white, and an untinted blit already draws white.

**Also latently broken: `1.21.1-neoforge`.** `NeoForgeGuiMixin`'s food anchor is its own file but
it calls the same shared `BankedHungerHud.render`, so the halo was dead on that node too and for
the same instruction. No other node is affected — `>=1.21.11` uses the pipeline form (verified
correct) and `<1.21` uses `blit(id, x, y, u, v, w, h, tw, th)`, whose 1.20.1 overload set has
exactly one eight-int candidate, so it cannot silently resolve to a different one.

**Gate, measured.** `26.2` / `26.1` / `1.21.11`: entries, resources and **all 244 / 244 / 250
classes instruction-identical**. `1.20.1-fabric` (240) and `1.20.1-forge` (251) likewise
unchanged. The two changed jars differ in exactly one class, `BankedHungerHud`, by exactly one
instruction: `iconst_m1` removed and the call retargeted from the five-int overload to the
four-int one (`method_52707` → `method_52706` after remap on Fabric — proof they are genuinely
different methods, not one method read two ways).

One footnote on that gate, because the jar SIZES did move by a byte on nodes with zero
instruction changes and the next reader will notice: the fix carries a long comment, and adding
comment lines shifts the **`LineNumberTable`** of everything below them. On 26.2 the whole
`BankedHungerHud.class` delta is `line 155 → 182` and `line 170 → 197`, with the instruction
stream identical — the same class of artefact as R-20's javac-17 caveat, and the reason §5.9's
gate 1 compares instructions rather than bytes on *every* node, not only 1.20.1.

**The lesson.** §5.8.1 was a phase and §5.8.2 was a unit; this one is an **arity**. An overload
chain that keeps its name and its argument *count* across a boundary while changing what the
arguments MEAN is invisible to javac, to the remapper and to every bytecode gate — the call
compiles, resolves, remaps and executes. The rule this leaves behind: **when porting a call down
a version boundary, decompile the overload that is actually being bound, not the one with the
matching shape.** The repo has now been bitten by this exact family twice on `blitSprite` alone
(the first is recorded in `HudMixin`'s header, where a six-argument target string was caught by
the boot instead), and both times the tell was an argument list that still fit.

#### 5.8.4 Stage 7, fourth in-game finding — **another mod's HUD offset is a question about COMPOSITION, not about the version**

**Reported:** on `1.21.1-fabric`, with §5.8.3's fix in, Well Fed's banked-hunger halo finally blits —
but the rings come out as a row floating **above** the drumsticks instead of outlining them. 26.2 is
in daily use and outlines them exactly.

**The basis was right. The x half was never in question, and that was measured before anything was
changed** — because the suspicion on the table was §5.8.3's own family, a parameter list read from
the wrong side of a boundary. It is not that. From `javap -c` of the 1.21.1 mojmap `Gui`:

```
renderPlayerHealth(GuiGraphics)V
  local  9 = guiWidth()  / 2 + 91          the hunger row's RIGHT edge
  local 10 = guiHeight() - 39              the hunger row's TOP
  offset 426: renderFood(g, player, iload 10, iload 9)
renderFood(GuiGraphics, Player, int y, int x)      <- Y THIRD, X FOURTH
  offset  26: iload_3 -> istore 8          the icon y IS param 3
  offset 115: iload 4 - j*8 - 9            the icon x IS param 4
  offset 128/153/178: blitSprite(Identifier, l, k, 9, 9)
```

`GuiGraphics.guiWidth()`/`guiHeight()` are one-line forwards to
`Minecraft.getWindow().getGuiScaled{Width,Height}()` on this version, so `BankedHungerHud`'s
`right = w/2 + 91` and `BOTTOM = 39` already reproduce vanilla's own basis to the pixel, and both
wrap handlers (`GuiMixin`, `NeoForgeGuiMixin`) already declare `(graphics, player, y, x)` in the
right order.

**The defect is that the mod subtracts Skill Proficiencies' `HUD_SHIFT` a SECOND time on exactly two
nodes**, because their raise is a **pose translate** and on those two nodes our draw runs *inside*
it. Written out, because the whole point is that it is not a version boundary:

| node | where the banked halo draws | where Skill Proficiencies raises | relation |
|---|---|---|---|
| `26.2` / `26.1` / `1.21.11` | `HudElementRegistry.attachElementAfter(FOOD_BAR, banked_hunger)` | `replaceElement(FOOD_BAR, …)` | **sibling** — subtract |
| `1.21.1-fabric` | `@WrapMethod Gui.renderFood` | `@WrapMethod renderPlayerHealth` + `pose().translate(0,-shift,0)` | **NESTED** — do not |
| `1.21.1-neoforge` | `@WrapMethod Gui.renderFood` (own file, same shared body) | `wrapLayer(FOOD_LEVEL)` + the same translate | **NESTED** — do not |
| `1.20.1-fabric` | TAIL of `Gui.render(GuiGraphics,F)V` | `@WrapMethod renderPlayerHealth` | **sibling** — subtract |
| `1.20.1-forge` | TAIL of `ForgeGui.render(GuiGraphics,F)V` | seven wrapped `ForgeGui` methods | **sibling** — subtract |

Vanilla calls `renderFood` from inside `renderPlayerHealth`, and `renderFoodLevel` (the NeoForge
`FOOD_LEVEL` layer body) calls it too — so on both 1.21.1 nodes the pose is already shifted when our
handler runs and the ring landed `HUD_SHIFT` = 7 px above its drumstick. Below and above that band
the mod's own draw is a *sibling* of the raised element and has to apply the shift itself, which is
what it has always done and what 26.2 proves correct.

**The fix** forks the one statement that computes `y`, on the existing frozen rows, with the
`>=1.21.11` and `<1.21` arms emitting today's statement character for character and the `>=1.21` arm
dropping the subtraction. The `SpecialitiesBridge` import is gated with it so no node carries an
import it never resolves a reference through. **No new predicate, no new call site, no change to
`ManaHud`** — that one anchors to the hotbar (`renderItemHotbar` TAIL / the `HOTBAR` element), which
Skill Proficiencies does not raise, so it is a sibling on every node and its own live read stays.

**Gate, measured.** `26.2` / `26.1` / `1.21.11` / `1.20.1-fabric` / `1.20.1-forge`: **0 resource
differences and 0 changed classes** (244 / 244 / 250 / 240 / 251 instruction-identical). The two
1.21.1 jars differ in exactly one class, `BankedHungerHud`, by exactly **two instructions removed** —
`invokestatic SpecialitiesBridge.hudShift:()I` and the `isub` — with every following branch target
sliding by the same four bytes and nothing else moving.

**The lesson.** §5.8.1 was a *phase*, §5.8.2 a *unit*, §5.8.3 an *arity*; this one is a
**composition**. `SpecialitiesBridge.hudShift()` is read live and is correct on every node — what
differs is whether this mod's draw is a SIBLING of the element the other mod moved or is NESTED
inside it, and the port chose a different answer per node for reasons that had nothing to do with
this number (there is no `HudElementRegistry` below 1.21.11, and no food method to wrap below 1.21).
The rule this leaves behind: **an element that reads another mod's HUD offset must state, per node,
whether it draws inside or beside the thing that offset moved** — a live read is not enough, and a
pose translate applies to everything downstream of it including handlers that were written as if
they were peers. Every gate this port runs is blind to it for the same reason as the other three:
the arithmetic is shared, the bytecode is right, and only a launched client can see where a draw
ended up.

#### 5.8.5 Stage 7, fifth in-game finding — **a render type that kept its name changed its BLEND**

**Reported:** on `1.21.1-fabric`, the Dark Ritual's eye glow draws as **one solid glowing band
across the whole face** instead of two distinct eyes. 26.2 is the reference and draws two eyes.

**Everything upstream of the blend was correct, and all of it was cleared first**, because the
report reads like a UV bug and the previous three findings all lived in the call:

* **The mesh is right on the legacy node.** `bakeEyes()` is shared, outside every `//?`, and its
  API is byte-for-byte the same class on all four versions (`CubeListBuilder.addBox(f,f,f,f,f,f,
  Set<Direction>)`, `LayerDefinition.create(mesh,int,int)` and the `CubeDefinition` constructor
  are declared identically on 26.2 / 1.21.11 / 1.21.1 / 1.20.1). Proven by *running* it: the
  1.21.1 `MeshDefinition`→`LayerDefinition`→`bakeRoot` chain executed against the mapped 1.21.1
  jar yields **one** polygon, normal (0,0,-1), UVs exactly `(0,0)…(1,1)` — the whole sheet on the
  quad, which is what the doc says it should be.
* **The overload is right.** `ModelPart.render(PoseStack,VertexConsumer,int,int,int)` exists on
  1.21.11 **and** 1.21.1 with the last int meaning colour on both; only 1.20.1 drops to the
  four-float tail, and that arm was already forked. Not §5.8.3's family.
* **The texture ships.** `assets/archetypes/textures/entity/night_form_eyes.png` is present in
  every legacy jar; a texture that failed to resolve would draw the missing-texture chequer.
* **The shader reads the tint.** 1.21.1's `rendertype_eyes.fsh` is
  `texture(Sampler0, texCoord0) * vertexColor`, so the ARGB the layer passes does arrive.

**The defect is one state shard, and it is invisible from the call site.** Measured with `javap -c`
on both mojmap jars:

```
>=1.21.11 / 26.x  RenderPipelines.EYES  -> BlendFunction.TRANSLUCENT, depthWrite false,
                  cull default(on), defines EMISSIVE + NO_CARDINAL_LIGHTING
<1.21.11          RenderType.eyes       -> ADDITIVE_TRANSPARENCY, and that shard is
                  RenderSystem.blendFunc(SourceFactor.ONE, DestFactor.ONE)
```

`ONE/ONE` is **pure** additive: source alpha is not a factor in the blend equation at all. The
artwork carries its entire shape in alpha — **1272 of its 2048 texels are `(255,255,255, a=0)`**
and the falloff ring is white at `a = 1…250` — so on the legacy nodes every texel of the 8×4 quad
added full white × the tint. That is the band, at exactly the quad's size. The same fact made the
`FAINT`/`BRIGHT` alpha dial inert there: both states drew at maximum.

The port had actually *recorded* this boundary in `NightEyesLayer`'s header — and recorded it
wrong, as "the blend is SRC_ALPHA/ONE", which would have scaled the glow. It is ONE/ONE.

**The fix is a new below-1.21.11-only compilation unit**, `client/NightEyeRenderType.java`, that
takes vanilla's own `eyes` composite — all thirteen shards, texture binding, eyes shader, LEQUAL
depth test, cull, `COLOR_WRITE` — and re-applies exactly **one** shard on top of it,
`TRANSLUCENT_TRANSPARENCY`, whose `setupRenderState` overwrites the blend func the additive shard
just set; the clear runs the two in reverse and both halves are idempotent (`disableBlend` +
`defaultBlendFunc`). Nothing is reimplemented, so no vanilla state this file does not name can
drift. `TRANSLUCENT_TRANSPARENCY` is `protected static` on `RenderStateShard`, and extending
`RenderType` is what makes it reachable — **no access widener, no accessor mixin, no
mixin-config entry on any node**, and no new predicate.

**No stock render type below the boundary would have done.** `entityTranslucentEmissive` blends
translucently but its vertex shader runs `minecraft_mix_light` (the glow would dim as the head
turns) and its fragment shader discards below `a = 0.1` (the falloff would harden); a scan of
every `TRANSLUCENT_TRANSPARENCY` composite in 1.21.1's `RenderType` finds nothing that pairs a
translucent blend with an emissive, unlit shader.

**Gate, measured.** `26.2` / `26.1` / `1.21.11` jars are **byte-identical** to the pre-fix build
(sha256 equal, whole jar) — the whole-file `//? if <1.21.11` form emits no `.class` there, and the
header edit in `NightEyesLayer` was kept line-count-neutral so not even the `LineNumberTable`
moves. The four legacy jars differ in exactly **two entries**: the new `NightEyeRenderType.class`
and `NightEyesLayer.class`. The protected inherited field survives every remapper — `field_21370`
on both Fabric legacy jars, `f_110139_` on Forge, `TRANSLUCENT_TRANSPARENCY` on NeoForge — and
each was cross-checked against the mapping file to confirm it is TRANSLUCENT and not
`field_21366`/`f_110135_`, which is ADDITIVE.

**The lesson.** §5.8.1 was a *phase*, §5.8.2 a *unit*, §5.8.3 an *arity*, §5.8.4 a *composition*;
this one is **state**. The call site was identical in every respect a compiler, a remapper or a
bytecode gate can see — same method name, same argument, same resolved target, correctly
remapped — and the pixel difference lived in a `blendFunc` two levels inside a vanilla constant.
The rule this leaves behind: **when a port reuses a vanilla render type by name across a version
boundary, read its blend, cull, depth and shader state out of the jar on both sides, and read the
ARTWORK against them.** An alpha-keyed texture is a silent dependency on the blend equation, and
the failure is total rather than subtle — which is why the only gate that can catch it is a
launched client.

#### 5.8.6 Stage 7, sixth in-game finding — **LexForge REORDERED the method the hook reads state from**

**Reported:** on `1.20.1-forge`, Well Fed's faster-eating half (a 25%/50% cut) does nothing. The
banked-hunger half of the same node works. `1.20.1-fabric` — same MC, same shared tree, same
generated source but for one `//?` arm — is correct.

**Why nothing before this caught it.** The mixin is listed, applied and *resolved*: the forge
node's transformed `LivingEntity` carries
`modifyExpressionValue$…$archetypes$wellFedDuration` at all four
`ItemStack.getUseDuration()I` sites, `injectors.defaultRequire: 1` is satisfied, the boot log is
clean and the export audit shows the handler in exactly the places §5.6 said it would be. Every
build-shaped, server-shaped and bytecode-shaped gate we have passes, because the handler is
*there* and it *runs*. What it does not do is return a different number.

**The defect.** The legacy arm has to re-root off `ItemStack` — below 1.20.5
`ItemStack.getUseDuration()` takes no user and Well Fed is per-player — so it lives in
`LivingEntity`, where `this` is the user, and it reads the stack out of `getUseItem()`. That is
sound on vanilla 1.20.1 and the arm says so explicitly: `startUsingItem` does `putfield useItem`
at offset 23 and calls `getUseDuration` at 28. **LexForge reverses those two.** Its patch routes
the duration through `ForgeEventFactory.onItemUseStart` and rewrites the method to do it:

```
1.20.1-fabric  method_6019   23: putfield useItem      <- assigned FIRST
                             28: getUseDuration        <- handler here, useItem is the food
1.20.1-forge   m_6672_       23: getUseDuration        <- handler here, useItem is still EMPTY
                             31: ForgeEventFactory.onItemUseStart
                             48: putfield useItem
                             53: putfield useItemRemaining
1.21.1-neoforge startUsingItem 25: getUseDuration ... 39: putfield useItem   (same reorder)
```

So on Forge the handler runs while `this.useItem` is the *previous* stack — `ItemStack.EMPTY` in
the normal case — `Items.AIR.getFoodProperties()` is null, the guard returns `original`, and the
one write that governs how long eating takes never sees the cut. The other three sites
(`getTicksUsingItem`, `shouldTriggerItemUseEffects`, `onSyncedDataUpdated`) read the field after
it is assigned and were scaling correctly the whole time, which is why the bug presents as "only
the speed half of Well Fed is missing" rather than as anything visibly broken.

**NeoForge reorders identically and is NOT affected**, and the reason is worth keeping: at and
above 1.20.5 the hook is `ItemStackMixin.archetypes$wellFed`, a `@ModifyReturnValue` on
`ItemStack.getUseDuration(LivingEntity)` *itself*, which takes the user as the target method's own
argument and never consults `useItem`. Verified in that node's transformed `ItemStack` — the
handler sits at the RETURN, so every caller including NeoForge's reordered one gets the scaled
value. The modern arm is immune by construction; only the legacy re-root depends on ambient state.

**The fix**, a `//? if >=1.20.5 { //?} elif forge { //?} else {` chain in `LivingEntityMixin`. The
forge arm splits the four sites in two: the three field-readers keep the `getUseItem()` shell, and
`startUsingItem` gets its own handler taking the stack from
`getItemInHand(hand)` via `@Local(argsOnly = true)` — the same expression Forge's own first line
uses to produce the stack it then measures, i.e. the identical object one instruction earlier.
`startUsingItem` **must** leave the shared handler's method list on that node: leaving it in would
double-scale the client's optimistic second call, which re-enters with `useItem` already holding
the food because the using flag is server-synced and has not arrived yet. Both arms call
`ColossusProtector.eatSpeedFactor` and nothing else — only the shell is written twice, for the
same reason `ItemStackMixin`'s is.

**Gate, measured.** Five Fabric nodes: `LivingEntityMixin.class` **instruction-identical** before
and after on `26.2-fabric` and `1.20.1-fabric` (`javap -c -p -constants`, empty diff), and the
non-comment source diff against `HEAD` touches nothing outside the new forge arm — the arms below
the boundary are unchanged character for character. `mixincheck.py`: 114/114 resolve on
`1.20.1-forge`, 112/112 on `1.20.1-fabric`. Forge dedicated-server smoke clean, exit 0, and the
export shows `m_6672_` now calling
`archetypes$wellFedStartDuration:(ILnet/minecraft/world/InteractionHand;)I` at offset 29 while the
other three keep `archetypes$wellFedDuration:(I)I`.

**The lesson.** §5.8.1 was a *phase*, §5.8.2 a *unit*, §5.8.3 an *arity*, §5.8.4 a *composition*,
§5.8.5 a *state*; this one is **order**. SP's conventions already warn that a loader can patch a
call site so a mixin on the vanilla method never runs; this is the quieter sibling — the call site
is intact and the injector resolves, but the loader moved a *field assignment* across it, so a
handler that reads ambient state reads it one instruction too early. The rule: **a re-rooted hook
that reads anything other than its own arguments has a precondition, and that precondition is a
byte offset. Write it down, and re-measure it on every loader node**, because a loader patch is
free to reorder statements the vanilla method kept in one order and no gate short of the real
behaviour will say so.

### 5.9 Per-stage gates — **same discipline as SP, one addition**

Every stage, without exception:

1. **Instruction-identity regression on every prior node.** `javap -c -p -constants` per class + resource-byte compare, via `scratchpad/gate/snap.sh`. ⚠ **R-20's javac-17 caveat**: once `1.20.1-fabric` exists, compare **instructions, not bytes** — javac 17 names pattern-match temporaries by source character offset, so editing a *comment* changes that node's class bytes with zero instructions changed.
2. **Headless dedicated-server smoke** per node: `-Dmixin.checks`, 19/19 common mixins applied, zero mixin errors, **zero `missing following references` / `Couldn't load tag`**, plus R-16's **positive** tag probe with a bogus control (absence of an error line does not rule out the cascade — a `required:false` drop is silent by design), plus an item-registry probe for the 30 items, clean stop, exit 0.
3. **Mixin export audit** — `-Dmixin.debug.export=true`, `javap -c` on the transformed targets. Per node, verify: all 77 injectors resolved (`injectors.defaultRequire: 1` is the drift detector — **keep it on every variant**), and the `hurtServer`/`hurt` handler **order** matches 26.2's source order.
4. **`injectors.defaultRequire: 1` on every node's config, both common and client.**
5. **NEW, no SP precedent — the damage-shaper ordering audit.** Archetypes has 15 `@ModifyVariable`s on one method and they do **not** all commute (§4.2b). Per node, read the transformed `hurtServer`/`hurt` and assert the handler offsets are in source order and that `FlenseMixin` (priority 1500) is last and `DamageTraceMixin` (priority 500) is first. **This is Archetypes' R-07.**
6. **Cross-mod ordering gate** (Archetypes-only): with Skill Proficiencies also installed, re-verify by bytecode export that `FlenseMixin` still lands after SP's Combat multiplier and stealth crit, and that `UseDurationMixin` still divides out exactly SP's applied Archery reduction. Per node — mixin ordering is not guaranteed by priority docs, which is why `ARCHITECTURE.md` says it was verified by export.
7. **In-game pass per node family** — not deferred to the end. SP's Stage-5 item-model bug and Stage-6 missing-Forge-client-bootstrap were both in-game-only, and Archetypes' client half is 3× the risk. **At more than one GUI scale** (§5.8.2): a screen is measured in GUI-scaled pixels, every gate here is scale-blind, and the first in-game pass ran at one scale and passed a screen that drew no node icons at another. **With Skill Proficiencies installed and its HUD bar both ON and OFF** (§5.8.4): `SpecialitiesBridge.hudShift()` is 0 with the bar hidden, so a node that double-counts the shift looks perfect in that state and only misdraws in the other.
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
| **R-A5** | **The `BlocksAttacks` shield subsystem has no host below 1.21.11** (`applyItemBlocking` absent; disable paths plural). Affects `archetypes$bulwark`, `archetypes$instinctiveGuard`, `BlocksAttacksMixin`, `DamageTraceMixin`'s blocking leg — i.e. the Colossus Protector epic tree's core, on 4 of 7 nodes. | ✅ **CLOSED — ALL FOUR NODES ARE LIVE ON ALL FOUR LEGACY NODES, NOTHING EXCISED.** The recommendation here (excise rather than approximate) was right about approximation and wrong about this being one. Measured, in two rounds: `disableShield` is ONE chokepoint (Immovable Object, Unstoppable Force); the vanilla shield's `DamageReduction(90°,0,1)` at the literal angle 0 is `blockable == amount` and `ItemDamageFunction(3,1,1)` is `hurtCurrentlyUsedShield` byte for byte (Instinctive Guard — a reimplementation, not an approximation); `Vec3.dot` in `isDamageSourceBlocked` occurs exactly once in the whole `LivingEntity` class and is the facing test's entire contribution (Omni Block). Only `BlocksAttacksMixin` and `DamageTraceMixin`'s blocking leg stay stripped — a host and a dev-tool observation, not a node. `inertNodeKeys` is gone from all three node scripts. Full account in §5.5.1's two ⚠⚠ corrections. |
| **R-A6** | **`Equippable`/`DataComponents.GLIDER` is `>=1.21.2`** — Magic Armaments' "conjured weapon doubles as an Elytra" has no component on **1.21.1 and 1.20.1** (the design under-stated the boundary). | ✅ **CLOSED — LIVE ON ALL FOUR LEGACY NODES, WITH REAL VANILLA FALL-FLYING.** Neither branch of the question was taken: not excision, and not a Levitation-effect stand-in (which would have been a different mechanic wearing the node's name). The component route has no host, but the CHEST-slot read does — one `getItemBySlot` occurrence in each of three target methods on all four arms, zero annotation fork, handing vanilla a throwaway unbreakable elytra while the channel is up. The crash premise that drove the excision (`Util.getRandom` on an empty glider-slot list) is 1.21.11+-only. Deploy, boosts, physics, landing, wind sound and the wingless look are all stock. Full account in §5.5.1's R-A6 ⚠⚠ correction. |

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
4. ~~**Colossus Protector shield subsystem on 1.21.1 + 1.20.1** — excise, or approximate? (§4.2, R-A5)~~ **ANSWERED: neither.** All four members reimplemented against vanilla's own legacy chokepoints, measured to produce the same numbers. See R-A5's row and §5.5.1.
5. ~~**Magic Armaments glide on 1.20.1** — excise, or Levitation stand-in? (R-A6)~~ **ANSWERED: neither.** Real vanilla fall-flying on all four legacy nodes, through the chest-slot read. See R-A6's row and §5.5.1.
6. **ESP wall-piercing on 6 of 7 nodes** — accept the loss? (R-B3)

Everything else in this design is decided, or is decided by an experiment that costs less than an hour.