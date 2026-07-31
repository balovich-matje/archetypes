// Node script for EVERY Fabric node. There are no per-node build scripts and there
// must never be: version-conditional build logic goes in plain Kotlin `if`s below.
//
// NOTE: Stonecutter `//?` comments do NOT work in build scripts — the preprocessor
// only walks the source sets. Use `sc.current.parsed >= "…"` here and `//?` only
// inside `src/`. (Skill Proficiencies' conventions §5f, which binds this repo.)
//
// NOT here yet, on purpose: `maven-publish` and `me.modmuss50.mod-publish-plugin`.
// Archetypes publishes no mavenLocal coordinate (it is the CONSUMER of Skill
// Proficiencies' API, not a producer), and the Modrinth release wiring is design §5.11
// — a stage of its own, which needs the project id read back off the API and a
// `changelogs/<version>.md`. Landing it here would be untested weight.

plugins {
	// Applies fabric-loom on 26.x (unobfuscated) or fabric-loom-remap below it.
	id("dev.kikugie.loom-back-compat")
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
	compileOnly(files(specialitiesJar))
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
val strippedMixinEntries: List<String> =
	if (sc.current.parsed >= "26.2") emptyList()
	// `client.renderer.extract` does not exist below 26.2, so LevelExtractorMixin's whole
	// compilation unit is `//?`-ed out and produces no class. See that file's header.
	else listOf("\"LevelExtractorMixin\"")

tasks.withType<ProcessResources>().configureEach {
	val mixinJava = "JAVA_${requiredJava.majorVersion}"
	metadataProps.forEach { (k, v) -> inputs.property(k, v) }
	inputs.property("mixinJava", mixinJava)
	inputs.property("strippedMetadataLines", strippedMetadataLines)
	inputs.property("strippedMixinEntries", strippedMixinEntries)

	// Copy-spec ACTIONS are not task inputs — `eachFile`/`filter` blocks are invisible to
	// up-to-date checking, so without this property a node that gained or lost the
	// transform below would keep serving the previous run's output. Measured next door:
	// the first build after a transform was added reported processResources UP-TO-DATE
	// and shipped the untransformed resources. (Conventions §5j.)
	inputs.property("legacyTagDir", sc.current.parsed < "1.21")

	filesMatching("fabric.mod.json") {
		expand(metadataProps)
		if (strippedMetadataLines.isNotEmpty()) {
			filter { line -> if (strippedMetadataLines.any(line::contains)) "" else line }
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

	// ---- NOT YET: the item MODEL DEFINITION relocation (`< 1.21.4`) ----
	//
	// `assets/<ns>/items/<id>.json` (the model-definition layer) arrived in 1.21.4; below
	// it an item is bound to `assets/<ns>/models/item/<id>.json` by id and the file is a
	// model, not a definition. Skill Proficiencies converts its thirty definitions with a
	// path move plus a regex rewrite, and Stage 5 there found that without it a node
	// silently ships items with no model — invisible to every build, visible only to a
	// launched client.
	//
	// This repo cannot copy that transform as-is and the difference is measured, not
	// assumed: it ships 28 definitions in `assets/archetypes/items/` AND 27 legacy models
	// in `assets/archetypes/models/item/`. 24 definitions have a legacy twin and must be
	// DROPPED rather than relocated (relocating them would overwrite a hand-authored
	// model with a generated one); 4 — skill_token, skill_token_60, spellcasting_tome_25,
	// spellcasting_tome_100 — have no twin and need the rewrite. Three models
	// (magic_bow_pulling_0/1/2) have no definition at all and must survive untouched.
	//
	// Writing the transform now would be writing it blind: it is inert on every node
	// registered so far, so nothing here could prove it right. It belongs to the node
	// that first needs it (design §4.4, Stage 4/5), where a launched client can see it.
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
