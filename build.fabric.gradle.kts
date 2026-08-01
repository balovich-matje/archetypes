// Node script for EVERY Fabric node. There are no per-node build scripts and there
// must never be: version-conditional build logic goes in plain Kotlin `if`s below.
//
// NOTE: Stonecutter `//?` comments do NOT work in build scripts — the preprocessor
// only walks the source sets. Use `sc.current.parsed >= "…"` here and `//?` only
// inside `src/`. (Skill Proficiencies' conventions §5f, which binds this repo.)
//
// STILL not here, on purpose: `maven-publish`. Archetypes publishes no mavenLocal
// coordinate — it is the CONSUMER of Skill Proficiencies' API, not a producer.
// `me.modmuss50.mod-publish-plugin` IS here as of the Stage-6 integration; the block is at
// the bottom of this file.

plugins {
	// Applies fabric-loom on 26.x (unobfuscated) or fabric-loom-remap below it.
	id("dev.kikugie.loom-back-compat")
	// Modrinth uploads, one version per node. Dry run unless `-PpublishLive=true`.
	id("me.modmuss50.mod-publish-plugin") version "2.1.1"
}

// The `+<mc>` suffix is structural, not decoration: every node writes its jar into the
// same `build/libs/<mod.version>/` collection directory, so without it two nodes would
// produce the same file name and silently overwrite each other.
version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = property("mod.id") as String

val requiredJava: JavaVersion = when {
	sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
	sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
	else -> JavaVersion.VERSION_17
}

repositories {
	/** Restricts [groups] to one maven so every other lookup skips it. */
	fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
		forRepository { maven(url) { name = alias } }
		filter { groups.forEach(::includeGroup) }
	}
	// Player Animation Library, pinned by Modrinth version id.
	strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

loom {
	splitEnvironmentSourceSets()

	mods {
		create("archetypes") {
			sourceSet(sourceSets.main.get())
			sourceSet(sourceSets["client"])
		}
	}

	runConfigs.all {
		preferGradleTask = true
		// Nodes are subprojects, so the default run directory would be
		// `versions/<node>/run` — a fresh, empty world per node. Every node shares the
		// repo-root `run/`, which is where the dev world and `run/mods/` already live.
		runDirectory = rootProject.file("run")
		jvmArguments.add("-Dmixin.debug.export=true") // dumps transformed classes to run/.mixin.out
	}
}

dependencies {
	/**
	 * Pulls only the Fabric API modules the mod imports, per node — COMPILE
	 * classpath only, which is what makes a module that vanishes on an older node a
	 * loud compile failure instead of a silent one.
	 *
	 * The dev RUNTIME gets the full umbrella below instead: the shipped
	 * fabric.mod.json declares `depends: fabric-api`, and only the umbrella jar
	 * carries that mod id — per-module jars declare their own ids, so a modules-only
	 * dev run dies at loader resolution. Modules must NOT also be on the runtime
	 * classpath or every one of them duplicates its copy nested in the umbrella.
	 * (Both halves are Skill Proficiencies' measured 2026-07-26 launcher lesson.)
	 */
	fun fapi(vararg modules: String) {
		for (it in modules) modCompileOnly(fabricApi.module(it, sc.properties["deps.fabric_api"]))
	}

	// The whole thing, dev runs only. Production never sees this.
	modLocalRuntime("net.fabricmc.fabric-api:fabric-api:${sc.properties["deps.fabric_api"] as String}")

	minecraft("com.mojang:minecraft:${sc.current.version}")
	// No-op on 26.x (already unobfuscated); layers Mojang mappings on obfuscated nodes.
	loomx.applyMojangMappings()
	// `mod*` configurations exist on both loom pipelines — loom-back-compat aliases them.
	modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")

	// The ten modules `src/` imports under a name that is spelled the same on every
	// node in scope. Derived from the tree, not from a template: every
	// `net.fabricmc.fabric.api.*` import in src/main and src/client maps into this list.
	fapi(
		"fabric-data-attachment-api-v1", // attachment.v1 (the ArchetypeStore seam)
		"fabric-networking-api-v1", // networking.v1 + client.networking.v1 (the Net seam)
		"fabric-entity-events-v1", // entity.event.v1
		"fabric-lifecycle-events-v1", // event.lifecycle.v1 + client.event.lifecycle.v1
		"fabric-events-interaction-v0", // event.player.Use{Item,Block}Callback
		"fabric-command-api-v2", // command.v2
		"fabric-screen-api-v1", // client.screen.v1 (the bookmark tab)
		"fabric-rendering-v1", // client.rendering.v1{,.hud} + FabricRenderState (absent < 1.21.11)
		"fabric-particles-v1", // particle.v1 + client.particle.v1
		"fabric-content-registries-v0", // registry.FabricPotionBrewingBuilder
	)

	// TWO modules are renamed across the range, so each is a swap and not an addition:
	// no fabric-api version ships both names.
	if (sc.current.parsed >= "26.1") {
		fapi("fabric-creative-tab-api-v1") // creativetab.v1.CreativeModeTabEvents
	} else {
		fapi("fabric-item-group-api-v1") // itemgroup.v1.ItemGroupEvents
	}
	if (sc.current.parsed >= "26.1") {
		fapi("fabric-key-mapping-api-v1") // client.keymapping.v1.KeyMappingHelper
	} else {
		fapi("fabric-key-binding-api-v1") // client.keybinding.v1.KeyBindingHelper
	}

	// ---- Player Animation Library ----
	//
	// The dependency CONFIGURATION forks, not just the coordinate, and getting it
	// backwards is a silent mis-remap rather than a build failure (design §2.1, R-C5):
	//   * 26.x ships "Merged" jars — mojmap classes, NO `Fabric-Mapping-Namespace`
	//     header — so they go through a PLAIN `implementation`. Putting one through
	//     `modImplementation` would have Loom remap it as if it were intermediary.
	//   * 1.21.11 and 1.21.1 ship Fabric jars that declare
	//     `Fabric-Mapping-Namespace: intermediary`, so on those nodes Loom MUST remap
	//     them -> `modImplementation`.
	//   * 1.20.1 has no artifact on either loader at any version. That node takes the
	//     no-op animation seam (design §2.2 Option B, the decision in force), so the
	//     dependency is absent and `deps.pal` is simply not declared for it.
	//
	// The coordinate is the Modrinth VERSION ID, never the version number: `1.1.5` is
	// duplicated across the Fabric and NeoForge listings and only the id disambiguates.
	val palVersionId: String? = sc.properties.rawOrNull("deps", "pal")?.toString()
	if (palVersionId != null) {
		if (sc.current.parsed >= "26.1") {
			implementation("maven.modrinth:player-animation-library:$palVersionId")
		} else {
			modImplementation("maven.modrinth:player-animation-library:$palVersionId")
		}
	}

	// ---- Skill Proficiencies, compile-only ----
	//
	// The Spellcasting skill registers into its engine through the `specialities:skills`
	// entrypoint when both mods are installed; at runtime every touch goes through
	// compat/SpecialitiesBridge's guard, so this never has to be present.
	//
	// A per-node FILE dependency, not a mavenLocal coordinate, and that is the decision
	// in force (design §3.5, the fallback mechanism): only Skill Proficiencies' 26.2 node
	// owns the `com.specialities:specialities` mavenLocal coordinate, and one artifact
	// provably cannot serve seven nodes — the API's own signatures move with the mapping
	// namespace. `build/libs/<version>/` is that repo's `buildAndCollect` output.
	//
	// The cost of the fallback, stated so nobody is surprised: `build/` is gitignored
	// there, so a fresh clone or a `clean` in that repo breaks THIS repo's CONFIGURATION
	// phase, not just its compile. `./gradlew buildAndCollect` next door fixes it.
	//
	// Configuration per node (design §3.5): 26.x jars are mojmap, so plain `compileOnly`
	// is correct and is what this repo has always used. From 1.21.11 down the Fabric jars
	// are `intermediary` and this MUST become `modCompileOnly` — that fork lands with the
	// first remapped node, alongside the file-name fork for the two loader jars
	// (`specialities-neoforge-…` / `specialities-forge-…`).
	val specialitiesVersion: String = sc.properties["deps.specialities"]
	val specialitiesJar = rootProject.file(
		"../specialities/build/libs/$specialitiesVersion/" +
			"specialities-$specialitiesVersion+${sc.current.version}.jar",
	)
	if (sc.current.parsed >= "26.1") {
		compileOnly(files(specialitiesJar))
	} else {
		// From 1.21.11 down the jar is `Fabric-Mapping-Namespace: intermediary` (checked in
		// its manifest), so it has to go through Loom's remapper or every Minecraft type in
		// its signatures stays spelled `class_2960`. Same fork, same reason as PAL's above,
		// and the same silent failure mode if it is missed — `SkillType.iconTexture()`
		// returning `class_2960` does not fail to resolve, it fails to MATCH the
		// `Identifier` this repo's SpellcastingSkill declares, one compile error away from
		// looking like an unrelated typo.
		modCompileOnly(files(specialitiesJar))
	}
}

// LOADER-AXIS EXCLUSIONS (conventions §5e-ter). They are here BEFORE the first
// NeoForge/Forge file lands, because the moment one does every Fabric node breaks
// without them.
//
// NAMING RULE these globs depend on: a file that exists for one loader only is named
// after that loader. `Forge*` does not match `NeoForge*` (the pattern is anchored),
// which is what lets each node exclude exactly the other's files.
sourceSets["main"].java.exclude(
	"com/archetypes/platform/NeoForge*.java",
	"com/archetypes/platform/Forge*.java",
	"com/archetypes/platform/ArchetypesNeoForge.java",
	"com/archetypes/platform/ArchetypesForge.java",
)
sourceSets["client"].java.exclude(
	"com/archetypes/client/NeoForge*.java",
	"com/archetypes/client/Forge*.java",
)

java {
	// Loom attaches this to remapSourcesJar and to `build` automatically.
	withSourcesJar()
	sourceCompatibility = requiredJava
	targetCompatibility = requiredJava

	// Deliberately no `vendor = ADOPTIUM`: an exact-version local JDK is preferred, which
	// keeps 26.x bytecode identical to the pre-workspace jar instead of recompiling it
	// with a freshly provisioned Temurin.
	toolchain {
		languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
	}
}

// fabric.mod.json lives in `main`, but archetypes.client.mixins.json lives in `client`
// — with split source sets there are TWO ProcessResources tasks and both need the
// substitutions, or the client mixin config ships a literal `${java}`.
//
// TRAP, measured on the other repo: anything passed through `expand` has its `#`
// comments treated as Groovy template source, so a literal `${…}` or a bare `Foo$Inner`
// anywhere in one of these files fails the COPY and nothing earlier catches it.
val metadataProps: Map<String, String> = mapOf(
	"id" to sc.properties["mod.id"],
	"name" to sc.properties["mod.name"],
	"minecraft" to sc.properties["mod.mc_compat"],
	"loader" to sc.properties["mod.loader_floor"],
	// `1.1.0+26.2`, not bare `1.1.0` — the jar has to name its game version.
	"version" to project.version.toString(),
	// fabric.mod.json wants `>=25`; the mixin configs want `JAVA_25`.
	"java_floor" to requiredJava.majorVersion,
)

// `//?` DOES NOT WORK IN `fabric.mod.json` and the failure is an unloadable mod, not a
// build error: Fabric parses it with its own JsonReader at `lenient = false`, so a
// leftover directive line throws on the leading `/`. Line-stripping here is the
// mechanism (Skill Proficiencies uses the identical one for its `modmenu` entrypoint).
//
// PAL has no artifact on the 1.20.1 pair at any version, so below 1.21.1 the hard
// `depends` on it has to go or the mod cannot load there. Blanking the line leaves valid
// JSON. The 26.x nodes get no transform at all, which is what keeps their resource bytes
// identical to the pre-workspace jar.
val strippedMetadataLines: List<String> =
	if (sc.current.parsed >= "1.21.1") emptyList()
	else listOf("player_animation_library")

// STAGE 5, AND IT IS THE HALF SKILL PROFICIENCIES' MECHANISM DID NOT NEED. Its stripped line
// (the `modmenu` entrypoint) had a sibling after it, so blanking it left valid JSON. THIS one
// is the LAST entry of `depends`, and blanking it leaves the previous line's trailing comma
// with nothing to separate — `{"a": "b",}` is not JSON, and the failure is loud in the right
// way: `remapJar` refuses the jar with `Expected name at line 41` rather than shipping it.
//
// So the line before it loses its comma on exactly the nodes where the strip happens. Keyed
// on the full text of that line, which couples this to the ORDER of the `depends` block —
// deliberately, and safely: reorder the block and this stops matching, which puts the comma
// back on a line that no longer needs one, which is the same loud remap failure again.
val metadataCommaFixups: List<String> =
	if (strippedMetadataLines.isEmpty()) emptyList()
	else listOf("\"fabric-api\": \"*\",")

// Same mechanism, same reason, different file: a mixin listed in the config whose class is
// not in the jar is a HARD BOOT FAILURE, so a mixin that only exists on some nodes has to
// leave the list on the others. `//?` cannot do it — the config is `.json`.
//
// Skill Proficiencies solves this with a per-node override of the whole client mixin
// config. That is the right shape when a node has to ADD an entry; it is the wrong shape
// here, where six of the seven nodes will DROP the same one and each override would be a
// full copy that silently goes stale the next time a client mixin is added. Blanking the
// line leaves valid JSON (whitespace inside an array is insignificant) and keeps ONE list.
//
// CONSTRAINT this depends on, stated so it is not discovered the hard way: the entry must
// not be the LAST element of its array, or blanking it leaves a trailing comma. It is not
// — the client list is alphabetical and UseDurationMixin is last.
//
// 26.2 gets no transform at all, which is what keeps its resource bytes where they were.
val strippedMixinEntries: List<String> = buildList {
	// `client.renderer.extract` does not exist below 26.2, so LevelExtractorMixin's whole
	// compilation unit is `//?`-ed out and produces no class. See that file's header.
	if (sc.current.parsed < "26.2") add("\"LevelExtractorMixin\"")
	// STAGE 4 / R-A5: `world.item.component.BlocksAttacks` does not exist below 1.21.11, so
	// BlocksAttacksMixin's whole compilation unit goes with it. ONLY THE HOST GOES — Immovable
	// Object itself is live on that node family, from PlayerMixin's `disableShield` head; see
	// the ⚠ above `inertNodeKeys`. Same constraint as the entry above — the entry must not be
	// the LAST element of its array or blanking it would leave a trailing comma. It is not:
	// the common list is alphabetical and ProjectileMixin is last.
	if (sc.current.parsed < "1.21.11") add("\"BlocksAttacksMixin\"")
}

tasks.withType<ProcessResources>().configureEach {
	// A per-node override directory is a RESOURCE ROOT, so anything documenting it ships
	// inside that node's jar. Measured on the Stage-4 artifact: `archetypes-1.1.0+1.21.1.jar`
	// carries a 2,797-byte `README-override.md` at its root, which no other node has — a
	// resource difference between two jars of the same mod that is pure prose.
	//
	// Excluded rather than moved out of the resource tree: the file has to sit NEXT TO the
	// config it explains or it goes stale the first time someone edits one without the other,
	// and that config is the one whose drift is a hard boot failure. Stage 5 adds a second
	// override directory wanting the same README, so the fix is written once, here, for every
	// node — the pattern is a file NAME, so it catches `versions/*/src/*/resources/` alike.
	//
	// STAGE 5 WIDENED IT TO A GLOB, and the reason is a constraint this comment did not
	// anticipate: the two legacy nodes now have a SECOND override root (`src/main/resources`,
	// for the recipes) wanting its own README, and two files named `README-override.md` in
	// two source sets are one jar-root entry each — which `processResources` de-duplicates
	// silently (a `SourceDirectorySet` keeps the last srcDir's copy) but `sourcesJar` does
	// NOT: it fails the build outright with `Entry README-override.md is a duplicate but no
	// duplicate handling strategy has been set`. So the data-side file is
	// `README-override-data.md` and the pattern below catches both.
	exclude("README-override*.md")

	val mixinJava = "JAVA_${requiredJava.majorVersion}"
	metadataProps.forEach { (k, v) -> inputs.property(k, v) }
	inputs.property("mixinJava", mixinJava)
	inputs.property("strippedMetadataLines", strippedMetadataLines)
	inputs.property("metadataCommaFixups", metadataCommaFixups)
	inputs.property("strippedMixinEntries", strippedMixinEntries)

	// Copy-spec ACTIONS are not task inputs — `eachFile`/`filter` blocks are invisible to
	// up-to-date checking, so without this property a node that gained or lost the
	// transform below would keep serving the previous run's output. Measured next door:
	// the first build after a transform was added reported processResources UP-TO-DATE
	// and shipped the untransformed resources. (Conventions §5j.)
	inputs.property("legacyTagDir", sc.current.parsed < "1.21")
	// Same reason as the line above, for the two transforms Stage 5 added: a copy-spec
	// ACTION is invisible to up-to-date checking, so the node that gains or loses one has to
	// say so through a property or it keeps serving the previous run's output. Measured
	// again here — the first build after the sprite relocation landed reported
	// processResources UP-TO-DATE and shipped the sprites at their atlas path.
	inputs.property("spriteRelocation", sc.current.parsed < "1.21")
	inputs.property("legacyItemModels", sc.current.parsed < "1.21.4")
	// Same reason a third time, for R-B9's recipe/advancement relocation. Its value is
	// identical to `legacyTagDir`'s and it still has to exist under its OWN name: an
	// up-to-date check compares the property SET, so a new copy-spec action whose predicate
	// already appears under another key changes nothing Gradle can see and the node ships
	// the previous run's tree. Measured twice in Stage 5 already; not measured a third time.
	inputs.property("legacyDatapackDirs", sc.current.parsed < "1.21")

	filesMatching("fabric.mod.json") {
		expand(metadataProps)
		if (strippedMetadataLines.isNotEmpty()) {
			filter { line ->
				when {
					strippedMetadataLines.any(line::contains) -> ""
					metadataCommaFixups.any { line.trim() == it } -> line.trimEnd().dropLast(1)
					else -> line
				}
			}
		}
	}
	// ---- R-A5 / R-A6: the excised nodes SAY SO, on the nodes where they are inert ----
	//
	// Instinctive Guard and Bulwark — the two members of the shield-modifier cluster whose
	// whole effect is a NUMBER taken off a blocked hit — and Magic Armaments' glide have no
	// host below 1.21.11; the reasoning is in ColossusProtector's header and
	// MagicArmaments.fitGlider. The design's prescription is that the NODES stay purchasable
	// (a hole mid-constellation would strand the branch beyond it) and that their
	// descriptions say the effect is inactive here.
	//
	// ⚠ TWO KEYS LEFT THIS LIST. Immovable Object and Unstoppable Force (Siegebreaker) were
	// listed here on a measured-wrong premise — that the legacy shield-disable path is two
	// chokepoints rather than one. It is one (`Player.disableShield`, a single caller in the
	// whole jar on every legacy target), so both nodes now have real legacy hosts:
	// PlayerMixin's `archetypes$immovableObject` and LivingEntityMixin's legacy
	// `archetypes$unstoppableForce`. They are ACTIVE on all four legacy nodes and must not
	// be re-marked inert. (One caveat, and it is not a reason to re-mark: `Items.MACE` does
	// not exist on 1.20.1, so Unstoppable Force is the unarmed half of its promise there.)
	//
	// A `filter` and not `//?`, for the reason that is now a rule in this repo: Stonecutter
	// does not process `.json` at all, and a leftover directive in a lang file is a silent
	// half-loaded resource. Keyed on the full `.desc` key so nothing else can match, and
	// appended INSIDE the closing quote so the file stays valid JSON.
	val inertNodeKeys: List<String> =
		if (sc.current.parsed >= "1.21.11") emptyList()
		else listOf(
			"node.archetypes.protector.omni_block.desc",
			"node.archetypes.colossus_protector.instinctive_guard.desc",
			"node.archetypes.oracle_wizard.levitation.desc",
		)
	inputs.property("inertNodeKeys", inertNodeKeys)

	if (inertNodeKeys.isNotEmpty()) {
		filesMatching("assets/*/lang/*.json") {
			filter { line ->
				if (inertNodeKeys.none { line.contains("\"$it\"") }) {
					line
				} else {
					val end = line.lastIndexOf('"')
					line.substring(0, end) +
						" \\u00a77(Inactive on this Minecraft version.)\\u00a7r" +
						line.substring(end)
				}
			}
		}
	}

	// ---- Unstoppable Force drops its MACE clause below 1.21 ----
	//
	// The node is LIVE on both 1.20.1 nodes (LivingEntityMixin's legacy
	// `isDamageSourceBlocked` arm), but only for its unarmed half: `Items.MACE` does not
	// exist there, so `WeaponClass.of` can never answer MACE — see that class's own `>=1.21`
	// arm. A string promising the mace clause would over-promise by exactly one weapon.
	//
	// NOT a per-node override of `en_us.json`: the shared resource root ships into every
	// node's jar, so a `versions/<node>/src/main/resources` copy would move all five Fabric
	// jars' resource bytes. Same filter machinery as `inertNodeKeys` above, and it needs its
	// OWN `inputs.property` name — a copy-spec action is invisible to the up-to-date check,
	// and a predicate that merely repeats another key's value changes nothing Gradle can see.
	val macelessSiegebreaker = sc.current.parsed < "1.21"
	inputs.property("macelessSiegebreaker", macelessSiegebreaker)

	if (macelessSiegebreaker) {
		filesMatching("assets/*/lang/*.json") {
			filter { line ->
				if (line.contains("\"node.archetypes.colossus_crusher.siegebreaker.desc\"")) {
					line.replace("Your mace and unarmed attacks", "Your unarmed attacks")
				} else {
					line
				}
			}
		}
	}

	filesMatching("*.mixins.json") {
		expand("java" to mixinJava)
		if (strippedMixinEntries.isNotEmpty()) {
			filter { line -> if (strippedMixinEntries.any(line::contains)) "" else line }
		}
	}

	// ---- R-16: the datapack tag directory is PLURAL below 1.21 ----
	//
	// The flip is at exactly 1.21 (last plural release 1.20.6, first singular 1.21), and
	// 1.20.1's tag loader hardcodes the plural literal, so the shared tag files under
	// `data/*/tags/item/` are read by NOTHING on that node unless they move. Done here
	// rather than as a per-node override so there stays exactly ONE copy of each JSON:
	// `FileCopyDetails.path` is settable, and the nodes at or above 1.21 get no transform
	// at all, which is what keeps their resource bytes identical.
	if (sc.current.parsed < "1.21") {
		eachFile {
			if (path.contains("/tags/item/")) {
				path = path.replace("/tags/item/", "/tags/items/")
			}
		}
	}

	// ---- R-B9: `recipe/` and `advancement/` are PLURAL below 1.21 too, and this one was
	// MISSED until the 1.20.1 smoke rig existed to ask ----
	//
	// The rename that took `tags/items` to `tags/item` took the whole datapack registry
	// directory set with it, at the same release. MEASURED, on the two cached vanilla server
	// jars rather than from memory, because "1.21.2" is the plausible wrong answer and both
	// this repo and Skill Proficiencies would have shipped it:
	//
	//   1.20.1 `data/minecraft/` -> advancements/ loot_tables/ recipes/ structures/ tags/
	//   1.21.1 `data/minecraft/` -> advancement/  loot_table/  recipe/  structure/  tags/
	//
	// So the 20 recipes and their 20 recipe-advancements shipped by the 1.20.1 node sat in
	// `data/archetypes/recipe/` and `data/archetypes/advancement/recipes/`, which nothing on
	// that version reads. There is no error line for this — a directory the loader does not
	// walk is not a parse failure, it is an absence — which is why the probe that found it is
	// a POSITIVE one (`/recipe give @a archetypes:<id>`, with a bogus control) and not a
	// log grep. Exactly R-16's lesson, one directory over.
	//
	// This mod has no loot tables, predicates or structures, so the two rules below are the
	// whole of the exposure; a third directory arriving means a third rule here.
	if (sc.current.parsed < "1.21") {
		eachFile {
			if (path.contains("/recipe/")) {
				path = path.replace("/recipe/", "/recipes/")
			}

			if (path.contains("/advancement/")) {
				path = path.replace("/advancement/", "/advancements/")
			}
		}
	}

	// ---- R-B9's second half: `minecraft:breeze_rod` does not EXIST on 1.20.1. ----
	//
	// The other nineteen recipes are a schema problem and are solved by the per-node
	// overrides in `versions/1.2*.*-fabric/src/main/resources/` (read the README beside
	// them). This one is not: the Breeze Wand's only ingredient is an item added in 1.21, so
	// there is no legacy spelling to write. The ITEM stays registered and obtainable by
	// command — only the recipe and its unlock advancement go, which is the same shape as
	// Stage 5's other excisions and is stated where it happens rather than in a list.
	//
	// Excluded rather than overridden with something else, deliberately: substituting a
	// different ingredient would make the same item cost something different on one node,
	// which is a silent balance divergence and exactly what R-20 exists to catch.
	if (sc.current.parsed < "1.21") {
		exclude("data/*/recipe/breeze_wand.json", "data/*/advancement/recipes/breeze_wand.json")
	}

	// ---- R-16's neighbour: the item MODEL DEFINITION layer is `>= 1.21.4` ----
	//
	// `assets/<ns>/items/<id>.json` (a model DEFINITION) arrived in 1.21.4; below it an item
	// is bound to `assets/<ns>/models/item/<id>.json` by id and that file is the model
	// itself. Skill Proficiencies' Stage 5 found what happens without this transform, and
	// it is the reason it is written here rather than later: thirty items shipped with no
	// model at all, invisible to every build and visible only to a launched client.
	//
	// This repo's shape is different from that one's and is MEASURED, not assumed: 28
	// definitions, 27 legacy models, 24 of them a matching pair.
	//
	//   * The 24 with a twin are DROPPED. Relocating them would overwrite a hand-authored
	//     model with a generated one — the exact opposite of the fix.
	//   * The 4 without one — skill_token, skill_token_60, spellcasting_tome_25,
	//     spellcasting_tome_100 — are REWRITTEN into legacy models. Each is a plain
	//     `{"model": {"type": "minecraft:model", "model": "<id>"}}` pointing at a VANILLA
	//     model (experience_bottle, book, enchanted_book), so the legacy equivalent is
	//     `{"parent": "<id>"}` and nothing about the item's look moves.
	//   * The 3 models with no definition (magic_bow_pulling_0/1/2) are untouched on every
	//     node — they are referenced by the bow's own overrides, not by an item id.
	//
	// Anchored on the full path so the tag rename above cannot be caught by it (SP's comment
	// explains why a loose `/items/` test is dangerous), and ordered after it for the same
	// reason.
	// ---- R-17: no GUI SPRITE ATLAS below 1.21, so the sprites become plain textures ----
	//
	// From 1.21 up, `assets/<ns>/textures/gui/sprites/**` is an atlas source and a drawable
	// is named by its sprite id (`archetypes:hud/banked_food_ring`). Below it there is no
	// atlas at all: a drawable is a FILE, named by its full texture path. Moving the subtree
	// up one level is what makes `Archetypes.id("textures/gui/hud/banked_food_ring.png")` —
	// the id the legacy arm of `BankedHungerHud` builds — resolve to the same pixels.
	//
	// The eight grey heart sprites ride along and are unused on this node (see
	// `client/mixin/HudMixin`, which excises the substitution they feed). They are left in
	// rather than excluded: the exclusion would be a second rule to keep in step with a
	// feature that may come back, and the whole set is 3 KB.
	if (sc.current.parsed < "1.21") {
		eachFile {
			if (path.contains("/textures/gui/sprites/")) {
				path = path.replace("/textures/gui/sprites/", "/textures/gui/")
			}
		}
	}

	if (sc.current.parsed < "1.21.4") {
		val legacyModels: Set<String> =
			rootProject.file("src/main/resources/assets/archetypes/models/item")
				.list()?.toSet() ?: emptySet()
		inputs.property("legacyModels", legacyModels.sorted())

		filesMatching("assets/*/items/*.json") {
			if (legacyModels.contains(name)) {
				exclude()
			} else {
				path = path.replace("/items/", "/models/item/")
				// Six lines in, three out. The definition's shape is fixed and checked in:
				//   {                          -> kept
				//     "model": {               -> dropped
				//       "type": "…:model",     -> dropped
				//       "model": "<id>"        -> rewritten to "parent": "<id>"
				//     }                        -> dropped (two-space indent; the file's LAST
				//   }                             brace has none, which is what tells them apart)
				filter { line ->
					val model = Regex("\"model\": \"([^\"]+)\"").find(line)

					when {
						line.contains("\"type\"") -> null
						line.trim() == "\"model\": {" -> null
						line == "  }" -> null
						model != null -> "  \"parent\": \"${model.groupValues[1]}\""
						else -> line
					}
				}
			}
		}
	}
}

tasks {
	jar {
		// `project.name` is the NODE name here ("26.2-fabric"), so the license entry has
		// to be keyed off mod.id to stay `LICENSE_archetypes`.
		val modId: String = sc.properties["mod.id"]
		inputs.property("modId", modId)
		from(rootProject.file("LICENSE")) { rename { "${it}_$modId" } }
	}

	register<Copy>("buildAndCollect") {
		group = "build"
		description = "Builds the mod jar and collects it into build/libs/<mod version>/"
		inputs.property("version", project.property("mod.version"))
		// loomx.mod(Sources)Jar resolves to jar/sourcesJar or remapJar/remapSourcesJar.
		from(loomx.modJar.flatMap { it.archiveFile }, loomx.modSourcesJar.flatMap { it.archiveFile })
		into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
	}
}

// ---------------------------------------------------------------------------
// MODRINTH RELEASE UPLOAD — one Modrinth version per node (design §5.11).
//
// Skill Proficiencies' `build.fabric.gradle.kts` block, adapted. Plugin
// `me.modmuss50.mod-publish-plugin` 2.1.1 registers one `PublishModTask` per platform —
// ours is `publishModrinth` — plus an aggregate `publishMods` that only `dependsOn` it.
// `stonecutter.gradle.kts` orders `publishModrinth` across nodes so the seven uploads do not
// race Modrinth's rate limiter under `org.gradle.parallel=true`, ascending, so the newest
// node lands on top of the version list.
//
// NOTHING here can run during a normal build: both tasks are in the `publishing` group and
// no lifecycle task depends on them. Beyond that the CHECKED-IN configuration is a DRY RUN —
// a real upload needs BOTH `-PpublishLive=true` and a `MODRINTH_TOKEN` in the environment,
// and in dry run the plugin never reads the token at all.

/** The `<version>` half of the node name: `26.1-fabric` -> `26.1`. */
val nodeKey: String = sc.current.project.substringBeforeLast('-')

/** True when no registered node targets a newer Minecraft version than this one. */
val isNewestNode: Boolean = sc.versions.none { it.parsed > sc.current.parsed }

val modVersion: String = sc.properties["mod.version"]

/** Game versions this node's jar gets marked compatible with when published. */
val compatibleVersions: List<String> = sc.properties.rawOrNull("mod", "mc_releases")
	?.asList().orEmpty().map { it.toString() }

// SP's scheme, adopted whole (design §5.11): the newest FABRIC node uploads the BARE mod
// version and every other node appends its node key. Read back off
// `GET /v2/project/47EMhuFl/version` rather than reconstructed — the two live versions are
// `1.0.0` and `1.1.0`, both bare, both 26.2, because 26.2-fabric was the only node there was.
// That is also why the bare number MOVES when a newer node registers, and why a release must
// bump `mod.version`: `1.1.0` is taken.
//
// The NODE KEY, not `sc.current.version`: the 26.1 node's Minecraft version is 26.1.2 but its
// version number is `+26.1`. The jar FILE name keeps `project.version` (with the full
// `+26.1.2`) and is deliberately unaffected — Modrinth does not care what the file is called.
//
// The two loader nodes are always suffixed with their WHOLE node directory name and never
// consult `isNewestNode`; their scripts own that. Without it `1.20.1-fabric` and
// `1.20.1-forge` would both claim `<version>+1.20.1`.
val modrinthVersion: String = if (isNewestNode) modVersion else "$modVersion+$nodeKey"

// Release notes live in `changelogs/<mod.version>.md` — ONE file for every node, so the notes
// cannot drift between the seven uploads of a release. If the first line is an `# H1` it
// becomes the Modrinth version name and is stripped from the body; everything else is
// uploaded verbatim. Read as bytes and decoded as UTF-8 explicitly: the notes contain `—` and
// `→`, and `asText` would use the platform default.
val changelogPath = "changelogs/$modVersion.md"
val changelogText: Provider<String> = providers
	.fileContents(rootProject.layout.projectDirectory.file(changelogPath))
	.asBytes.map { String(it, Charsets.UTF_8) }
	.orElse(providers.provider<String> { error("No release notes at $changelogPath — write them before publishing") })

/** The `# H1` title of the release notes, or `""` when there is none. */
val releaseTitle: Provider<String> = changelogText.map {
	it.trim().lineSequence().firstOrNull()?.takeIf { line -> line.startsWith("# ") }?.removePrefix("# ")?.trim().orEmpty()
}

/** The release notes with the title line removed. */
val releaseNotes: Provider<String> = changelogText.map {
	val lines = it.trim().lines()
	(if (lines.firstOrNull()?.startsWith("# ") == true) lines.drop(1) else lines).joinToString("\n").trim()
}

/** Matches the published naming: `1.1.0 — The balance update`, plus ` (<node>)` below the top. */
val modrinthDisplayName: Provider<String> = releaseTitle.map { title ->
	buildString {
		append(modVersion)
		if (title.isNotEmpty()) append(" — ").append(title)
		if (!isNewestNode) append(" (").append(nodeKey).append(')')
	}
}

// A live upload is opt-in, per invocation. `PublishModTask` copies the extension's `dryRun`
// into itself and finalises it as the task is created, so this has to be set on the extension.
val publishLive: Boolean = providers.gradleProperty("publishLive").map(String::toBoolean).getOrElse(false)

// MODRINTH'S 64-CHARACTER VERSION-NAME CAP, AS A BUILD GATE. Measured against the LIVE
// versions of THIS project rather than read off the docs: `1.1.0` is named
// `1.1.0 — The balance update` (26 chars) and `1.0.0` is named `1.0.0 — launch` (14), both
// unsuffixed because 26.2-fabric was the only node. From this release on, ONE `# ` title has
// to fit EVERY node's suffix, and the longest is ` (1.21.1 NeoForge)` at 18 characters — so
// the budget is 64 − 5 (version) − 3 (` — `) − 18 = 38 title characters.
//
// Deliberately NOT auto-truncated: silently renaming a release is not this script's call. And
// it fails the LIVE upload only, so the dry run stays usable as the pre-flight that prints the
// numbers — `printPublishMetadata` reports `name length` and marks anything over.
//
// The title budget is arithmetic on plain Strings on purpose. Reading `releaseTitle` here
// would move the "no release notes" failure to CONFIGURATION time, i.e. a missing changelog
// would break `./gradlew build`.
val modrinthNameMax = 64
val modrinthNameSuffixLength = if (isNewestNode) 0 else " ($nodeKey)".length
val modrinthTitleBudget = modrinthNameMax - modVersion.length - " — ".length - modrinthNameSuffixLength

val checkedDisplayName: Provider<String> = modrinthDisplayName.map { name ->
	require(!publishLive || name.length <= modrinthNameMax) {
		"Modrinth caps a version name at $modrinthNameMax characters; this one is ${name.length}: " +
			"\"$name\". Shorten the `# ` title line in $changelogPath to at most " +
			"$modrinthTitleBudget characters — that is the budget every node can carry."
	}
	name
}

// The dependency set, PER NODE, and it is not decoration: both published Archetypes versions
// declare three dependencies (`GET /v2/project/47EMhuFl/version`), and two of the three are
// NOT true on every node of this port.
//
//   P7dR8mSH  Fabric API                   required — this script is the Fabric one, so it
//                                          holds on all five of its nodes and on neither
//                                          loader node (their scripts declare no such row).
//   ha1mEyJS  Player Animation Library     required WHERE THERE IS ONE. `deps.pal` is absent
//                                          for 1.20.1 (design §2.2 Option B — no artifact
//                                          exists on any loader at that version), and the
//                                          same absence that drops the dependency, the
//                                          `depends` line and the five animation drivers has
//                                          to drop this row too. Declaring it would tell the
//                                          launcher to install a mod that cannot exist.
//   d4TtjlpN  Skill Proficiencies          optional, every node. The interop is compile-only
//                                          and guarded at runtime by compat/SpecialitiesBridge.
val palVersionId: String? = sc.properties.rawOrNull("deps", "pal")?.toString()

publishMods {
	dryRun = !publishLive
	// The mod jar for this node — `loomx.modJar` resolves to `jar` (26.x) or `remapJar`
	// (remapped nodes) and carries the task dependency with it. NEVER the `-sources` jar:
	// `additionalFiles` is deliberately left empty.
	file = loomx.modJar.flatMap { it.archiveFile }
	version = modrinthVersion
	displayName = checkedDisplayName
	changelog = releaseNotes
	type = STABLE
	// Literal: this script is `build.fabric.gradle.kts`, so every node it configures is Fabric.
	modLoaders.add("fabric")

	modrinth {
		// Slug `archetypes`; the id is frozen, the slug is not. Read back off
		// `GET /v2/project/archetypes` (design §5.11 says to read it rather than guess).
		projectId = "47EMhuFl"
		// Read from the environment at publish time. The PAT lives at ~/.config/modrinth/token
		// and is never checked in, never printed, and never created by tooling.
		accessToken = providers.environmentVariable("MODRINTH_TOKEN")
		// `mod.mc_releases` from stonecutter.properties.toml — the 26.1 node's jar covers
		// 26.1/26.1.1/26.1.2, the 1.21.1 node's covers 1.21/1.21.1. Lazy so a node that forgot
		// the key fails when publishing rather than when building.
		minecraftVersions.addAll(
			providers.provider {
				compatibleVersions.ifEmpty { error("`mod.mc_releases` is not declared for node ${sc.current.project}") }
			},
		)
		// Both live versions are featured. Modrinth keeps older featured versions featured, so
		// this is the knob to turn down once seven versions per release crowd the page.
		featured = true
		requires("P7dR8mSH")
		if (palVersionId != null) requires("ha1mEyJS")
		optional("d4TtjlpN")
		// `environment` is deliberately unset: neither live version declares it, and a
		// publishing stage must not change release metadata.
	}
}

// Pre-flight check for the release: prints exactly what each node would upload, without
// building a jar or touching the network. This is the only publishing check that works on a
// node whose jar cannot be produced yet, which is why it exists alongside the plugin's dry run.
tasks.register("printPublishMetadata") {
	group = "publishing"
	description = "Prints the Modrinth metadata this node would upload. Builds nothing, uploads nothing."
	// Everything the action needs is captured here, at configuration time — the action itself
	// touches no project state, and the token is reported as present/absent, never printed.
	val rows = listOf(
		"node" to sc.current.project,
		"minecraft (jar)" to sc.current.version,
		"version_number" to modrinthVersion,
		"game_versions" to compatibleVersions.toString(),
		"loaders" to "[fabric]",
		"dependencies" to buildString {
			append("required=[P7dR8mSH fabric-api")
			if (palVersionId != null) append(", ha1mEyJS player-animation-library")
			append("] optional=[d4TtjlpN skill-proficiencies]")
		},
		"changelog file" to changelogPath,
		"mode" to if (publishLive) "LIVE UPLOAD" else "dry run",
		"MODRINTH_TOKEN" to if (providers.environmentVariable("MODRINTH_TOKEN").isPresent) "present" else "absent",
	)
	val name = modrinthDisplayName
	val notes = releaseNotes
	doLast {
		rows.forEach { (k, v) -> logger.lifecycle("%-16s %s".format(k, v)) }
		val rendered = name.get()
		logger.lifecycle("%-16s %s".format("name", rendered))
		logger.lifecycle("%-16s %d / %d%s".format("name length", rendered.length, modrinthNameMax,
			if (rendered.length > modrinthNameMax) "   *** OVER THE CAP — a live upload will be refused ***" else ""))
		logger.lifecycle("--- changelog ---")
		logger.lifecycle(notes.get())
	}
}
