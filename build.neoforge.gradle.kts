// Node script for EVERY NeoForge node. Today that is `1.21.1-neoforge` only.
//
// BASE: Skill Proficiencies' `build.neoforge.gradle.kts`, which is itself the maintained
// multiloader template's own file (fetched verbatim 2026-07-25 next door via
//   gh api repos/stonecutter-versioning/stonecutter-template-multiloader/contents/build.neoforge.gradle.kts
// and diffed there). Deltas from that copy are marked "DELTA:". NEVER fetch
// stonecutter.kikugie.dev (design R-13).
//
// NOTE, same as build.fabric.gradle.kts: Stonecutter `//?` comments do NOT work in build
// scripts (conventions §5f) — use `sc.current.parsed >= "…"`.
//
// NOT here, on purpose, and it is the largest delta from the file next door: no
// `me.modmuss50.mod-publish-plugin` and no `publishMods`/`printPublishMetadata` block.
// build.fabric.gradle.kts has none either — Archetypes' Modrinth wiring is design §5.11, a
// stage of its own that needs the project id read back off the API and a
// `changelogs/<version>.md`. Adding it on ONE node would put publishing on the loader axis
// before it exists on the Fabric one.
plugins {
	id("net.neoforged.moddev") version "2.0.142"
	// buildSrc script plugin, design R-14. See buildSrc/src/main/kotlin/neoforge-mutex.gradle.kts.
	id("neoforge-mutex")
}

version = "${property("mod.version")}+${sc.current.version}"
// DELTA-CRITICAL: the loader suffix is not cosmetic. `1.21.1-fabric` and `1.21.1-neoforge`
// would otherwise both produce `archetypes-1.1.0+1.21.1.jar` and overwrite each other in the
// shared `build/libs/<mod.version>/` collection directory — the same hazard the `+<mc>` suffix
// exists to close one axis up. Fabric keeps the bare `mod.id` so its file names never change.
base.archivesName = "${property("mod.id") as String}-neoforge"

val requiredJava: JavaVersion = when {
	sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
	sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
	else -> JavaVersion.VERSION_17
}

// DESIGN R-09 AS A BUILD GATE, not as a comment, and this mod needs it far more than the one
// next door. Data-attachment SYNC — `AttachmentType.Builder.sync(...)` and the initial push a
// patched PlayerList makes on login — arrived at NeoForge 21.1.200. Skill Proficiencies had
// ONE synced attachment; this mod has 47, sixteen of them broadcast to every tracking client,
// and every renderer flag another player reads travels on them. Below the floor the node
// builds, loads, and then shows a blank tree screen, a blank cooldown bar, no mana row and no
// other player's Bulwark — no crash and no log line. That is the exact failure class this
// project fails the build on instead, hence a configuration-time `require`.
val neoForgeVersion: String = sc.properties["deps.neoforge"]

run {
	val parts = neoForgeVersion.split('.', '-')
	val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
	val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
	val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
	val hasAttachmentSync = major > 21 || (major == 21 && minor > 1) || (major == 21 && minor == 1 && patch >= 200)
	require(hasAttachmentSync) {
		"deps.neoforge for node ${sc.current.project} is $neoForgeVersion, which is below the " +
			"21.1.200 data-attachment-sync floor (design R-09). Either raise the pin or make " +
			"ArchetypeStore.resyncAll/syncOnStartTracking real on this node — which means porting " +
			"platform/LegacyStateSync's whole broadcast channel here and needs the user's go-ahead."
	}
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

// DELTA: a `client` source set, created BEFORE the neoForge block because
// `enable { enabledSourceSets = … }` resolves it eagerly (measured next door: putting it after
// fails).
//
// This is NOT `loom.splitEnvironmentSourceSets()`. MDG has no such concept — NeoForge dev is a
// merged jar — so `client` is a plain source set that inherits main's classpath, and runtime
// separation is `Dist`/`@OnlyIn`'s job. Keeping the source set at all is what makes Stonecutter
// preprocess `src/client` on this node (StonecutterBuildImpl walks `project.sourceSets.all`),
// so it is mandatory, not optional.
val clientSourceSet: SourceSet = sourceSets.create("client") {
	compileClasspath += sourceSets.main.get().output
	runtimeClasspath += sourceSets.main.get().output
}

// The Modrinth coordinate for Player Animation Library on this node, or null where no artifact
// exists. Read exactly as build.fabric.gradle.kts reads it, so the absence of the key stays the
// decision it is there (design §2.2 Option B) rather than becoming a special case here.
val palVersionId: String? = sc.properties.rawOrNull("deps", "pal")?.toString()
val palCoordinate: String? = palVersionId?.let { "maven.modrinth:player-animation-library:$it" }

neoForge {
	// `version = "…"` is shorthand for `enable { version = "…" }` (NeoForgeExtension.setVersion,
	// read from moddev-gradle-2.0.142-sources.jar), so the long form is required whenever
	// enabledSourceSets is also set — calling both enables modding twice.
	enable {
		version = neoForgeVersion
		enabledSourceSets = setOf(sourceSets.main.get(), clientSourceSet)
	}

	mods {
		register(sc.properties.get<String>("mod.id")) {
			sourceSet(sourceSets.main.get())
			sourceSet(clientSourceSet)
		}
	}

	runs {
		register("client") {
			// The same shared run dir every Fabric node uses, so run/mods/ and
			// run/logs/latest.log stay in one place across all seven nodes.
			gameDirectory = rootProject.layout.projectDirectory.dir("run")
			client()

			// DELTA: PAL ON THE DEV CLIENT'S RUNTIME, and this is the half `compileOnly`
			// below cannot do. MDG gives every run its own configuration named
			// `<run>AdditionalRuntimeClasspath` — `RunModel`'s constructor calls
			// `InternalModelHelper.nameOfRun(this, "", "additionalRuntimeClasspath")`, read
			// out of moddev-gradle-2.0.142.jar rather than from docs — and this is the only
			// way to put a non-mod library on a run's classpath under MDG. It is reached
			// through the accessor rather than by spelling `clientAdditionalRuntimeClasspath`
			// so a rename in MDG is a compile error here and not a silently absent library.
			//
			// THE FAILURE MODE THE NODE'S AGENT MUST CHECK ON A REAL `runClient`: this block
			// only runs when the run is realised. If it ever is not, PAL is absent from the
			// dev client and the five animation drivers throw NoClassDefFoundError at first
			// use — nothing at configuration time says so.
			if (palCoordinate != null) {
				project.dependencies.add(additionalRuntimeClasspathConfiguration.name, palCoordinate)
			}
		}

		register("server") {
			gameDirectory = rootProject.layout.projectDirectory.dir("run")
			server()
		}
	}
}

dependencies {
	// ---- Player Animation Library ----
	//
	// A PLAIN `compileOnly`, and the asymmetry with build.fabric.gradle.kts is the point
	// (design §2.1, R-C5). `ReDTdA0C` is the NEOFORGE build of PAL 1.1.5: mojmap classes, no
	// `Fabric-Mapping-Namespace` header, and no remapper anywhere on this node's toolchain —
	// there is no `modImplementation` here to get wrong. What CAN be got wrong is the
	// coordinate: `1.1.5` is published on both listings and only the version id tells the
	// Fabric jar (`FkO8Scek`, the sibling node's) from this one, which is why the toml pins ids.
	//
	// `compileOnly` and not `implementation` because the runtime half is the run configuration
	// above; the shipped jar declares PAL as a `mods.toml` dependency and never bundles it.
	if (palCoordinate != null) {
		compileOnly(palCoordinate)
	}

	// ---- Skill Proficiencies, compile-only ----
	//
	// Same per-node FILE dependency build.fabric.gradle.kts uses and for the same reason
	// (design §3.5, the fallback mechanism): only that repo's 26.2 node owns a mavenLocal
	// coordinate, and one artifact provably cannot serve seven nodes — `SkillType.iconTexture()`
	// returns `Identifier` on 26.x, `class_2960` on the legacy Fabric jars and `ResourceLocation`
	// on this one.
	//
	// The FILE NAME forks here and nowhere else: that repo's loader nodes carry the loader in
	// `base.archivesName` for exactly the collision reason this script's own `archivesName` does,
	// so the artifact is `specialities-neoforge-<v>+<mc>.jar` and not `specialities-<v>+<mc>.jar`.
	//
	// PLAIN `compileOnly`, not `modCompileOnly`: MDG has no remapper and no `mod*`
	// configurations at all. The jar is already mojmap (its manifest carries no namespace
	// header — measured on the shipped 1.6.0 artifact), so there is nothing to remap.
	//
	// The cost, stated so nobody is surprised: `build/` is gitignored next door, so a fresh
	// clone or a `clean` there breaks THIS repo's CONFIGURATION phase. `./gradlew
	// buildAndCollect` in that repo fixes it.
	val specialitiesVersion: String = sc.properties["deps.specialities"]
	compileOnly(
		files(
			rootProject.file(
				"../specialities/build/libs/$specialitiesVersion/" +
					"specialities-neoforge-$specialitiesVersion+${sc.current.version}.jar",
			),
		),
	)
}

// LOADER-AXIS EXCLUSIONS (conventions §5e-ter), the mirror of build.fabric.gradle.kts's block:
// this node compiles the NeoForge seam implementations and neither the Fabric nor the Forge
// ones. `Forge*` is anchored, so it does NOT match `NeoForge*` — which is exactly what lets the
// two loader nodes exclude each other by glob. The naming rule is documented in the Fabric
// script: a file that exists for one loader only is named after that loader.
sourceSets["main"].java.exclude(
	"com/archetypes/platform/Fabric*.java",
	"com/archetypes/platform/Forge*.java",
	"com/archetypes/platform/ArchetypesForge.java",
)
sourceSets["client"].java.exclude(
	"com/archetypes/client/Forge*.java",
)

java {
	withSourcesJar()
	sourceCompatibility = requiredJava
	targetCompatibility = requiredJava
	// DELTA: no `vendor = ADOPTIUM`, matching build.fabric.gradle.kts's reasoning — an
	// exact-version local JDK is preferred over a freshly provisioned Temurin. foojay already
	// has 17 and 21 under ~/.gradle/jdks on this machine.
	toolchain {
		languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
	}
}

// DELTA: the same metadata map build.fabric.gradle.kts builds, plus the two values only the
// loader-axis metadata file needs. KEY NAMES are kept identical to the Fabric script's so the
// two never drift on `${version}`/`${name}` semantics.
//
// TRAP, measured on the other repo and worth repeating because this file is new: anything
// passed through `expand` has its `#` comments treated as Groovy template source, so a literal
// `${…}` or a bare `Foo$Inner` anywhere in `neoforge.mods.toml` fails the COPY and nothing
// earlier catches it.
val metadataProps: Map<String, String> = mapOf(
	"id" to sc.properties["mod.id"],
	"name" to sc.properties["mod.name"],
	"version" to project.version.toString(),
	// `mod.mc_compat` is FABRIC range syntax (">=1.21 <=1.21.1"); neoforge.mods.toml wants a
	// MAVEN range, so the loader-axis nodes get their own toml key.
	"minecraft_range" to sc.properties["mod.mc_range"],
	"neoforge_floor" to neoForgeVersion,
	"java_floor" to requiredJava.majorVersion,
)

// Same mechanism and same reason as build.fabric.gradle.kts's copy: a mixin listed in a config
// whose class is not in the jar is a HARD BOOT FAILURE, and `//?` cannot reach a `.json`.
// Blanking the line leaves valid JSON as long as the entry is not the array's last element —
// it is not, the lists are alphabetical and ProjectileMixin/UseDurationMixin are last.
//
// Only the COMMON config is touched here. This node's CLIENT config is a per-node override
// (see the processResources note below), so its list is written out by hand rather than
// filtered.
val strippedMixinEntries: List<String> = buildList {
	if (sc.current.parsed < "26.2") add("\"LevelExtractorMixin\"")
	if (sc.current.parsed < "1.21.11") add("\"BlocksAttacksMixin\"")
}

tasks.withType<ProcessResources>().configureEach {
	// A per-node override directory is a RESOURCE ROOT, so anything documenting it ships inside
	// that node's jar. Same glob build.fabric.gradle.kts uses, for the same reason.
	exclude("README-override*.md")

	val mixinJava = "JAVA_${requiredJava.majorVersion}"
	metadataProps.forEach { (k, v) -> inputs.property(k, v) }
	inputs.property("mixinJava", mixinJava)
	inputs.property("strippedMixinEntries", strippedMixinEntries)
	// Conventions §5j: copy-spec ACTIONS are invisible to up-to-date checking, so every
	// conditional transform below declares its decision as an input, under its OWN key — an
	// up-to-date check compares the property SET, so two transforms sharing one key is the same
	// bug as declaring neither. Measured twice next door and twice in this repo's Stage 5.
	inputs.property("legacyTagDir", sc.current.parsed < "1.21")
	inputs.property("spriteRelocation", sc.current.parsed < "1.21")
	inputs.property("legacyDatapackDirs", sc.current.parsed < "1.21")
	inputs.property("legacyItemModels", sc.current.parsed < "1.21.4")

	filesMatching("META-INF/neoforge.mods.toml") { expand(metadataProps) }
	// Mixin 0.8.7 ships with NeoForge 21.1.243 (POM: net.fabricmc:sponge-mixin:0.15.2+mixin.0.8.7)
	// and its CompatibilityLevel enum goes up to JAVA_22, so JAVA_21 is accepted. Measured next
	// door; do not copy the Forge node's clamp warning here, it is a different Mixin.
	filesMatching("*.mixins.json") {
		expand("java" to mixinJava)
		if (strippedMixinEntries.isNotEmpty()) {
			filter { line -> if (strippedMixinEntries.any(line::contains)) "" else line }
		}
	}

	// ---- R-A5 / R-A6: the excised nodes SAY SO, on the nodes where they are inert ----
	//
	// Copied from build.fabric.gradle.kts, keys and all, because this node lands on exactly the
	// same excisions as its 1.21.1-fabric sibling: no `world.item.component.BlocksAttacks` and
	// no `DataComponents.GLIDER` below 1.21.11/1.21.2. Build-script logic does not inherit
	// across node scripts, so the two copies must be kept in step — a node whose lang file
	// silently stopped saying "inactive" is a player bug report, not a build failure.
	val inertNodeKeys: List<String> =
		if (sc.current.parsed >= "1.21.11") emptyList()
		else listOf(
			"node.archetypes.protector.omni_block.desc",
			"node.archetypes.colossus_protector.instinctive_guard.desc",
			"node.archetypes.colossus_protector.immovable_object.desc",
			"node.archetypes.colossus_crusher.siegebreaker.desc",
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

	// ---- The four `< 1.21` datapack/asset relocations, copied from build.fabric.gradle.kts ----
	//
	// ALL FOUR ARE INERT ON 1.21.1 and are written anyway, because this script configures every
	// future NeoForge node and a 1.20.1-neoforge would need every one of them. Read the
	// long-form reasoning at the Fabric script's copies: the datapack tag directory, the recipe
	// and advancement directories and the GUI sprite subtree are all PLURAL / unrelocated below
	// 1.21, and `minecraft:breeze_rod` does not exist there at all.
	if (sc.current.parsed < "1.21") {
		eachFile {
			if (path.contains("/tags/item/")) {
				path = path.replace("/tags/item/", "/tags/items/")
			}

			if (path.contains("/recipe/")) {
				path = path.replace("/recipe/", "/recipes/")
			}

			if (path.contains("/advancement/")) {
				path = path.replace("/advancement/", "/advancements/")
			}

			if (path.contains("/textures/gui/sprites/")) {
				path = path.replace("/textures/gui/sprites/", "/textures/gui/")
			}
		}

		exclude("data/*/recipe/breeze_wand.json", "data/*/advancement/recipes/breeze_wand.json")
	}

	// ---- The item MODEL DEFINITION layer is `>= 1.21.4`, and this one IS LIVE HERE ----
	//
	// 1.21.1 < 1.21.4, so without this block twenty-eight items ship with no model at all —
	// invisible to every build and visible only to a launched client, which is the bug Skill
	// Proficiencies' Stage 5 found and this repo's Stage 4 found again.
	//
	// Copied from build.fabric.gradle.kts including its measurement: 28 definitions, 27 legacy
	// models, 24 a matching pair. The 24 with a twin are DROPPED (relocating would overwrite a
	// hand-authored model with a generated one); the 4 without are REWRITTEN. Anchored on the
	// full path and ordered AFTER the tag rename above, so a loose `/items/` test cannot catch
	// a renamed tag JSON.
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

	// DELTA-CRITICAL: fabric.mod.json lives in the SHARED src/main/resources and would otherwise
	// ship in this jar. Fabric-only metadata in a NeoForge jar is not fatal, but it is wrong and
	// it defeats the resource-byte gate. The template excludes it for the same reason.
	exclude("fabric.mod.json")

	// EVERY LOADER-ONLY RESOURCE ON THIS NODE IS A PER-NODE OVERRIDE (conventions §4 mechanism
	// 2), living under `versions/1.21.1-neoforge/src/{main,client}/resources/` and NOT in the
	// shared `src/`. The reason is the same for all of them: the shared resource roots ship into
	// every node's jar, build.fabric.gradle.kts has no resource exclusion, and that script is not
	// this node's to edit — so a shared copy would land in all five Fabric jars and move their
	// resource bytes, which is precisely the reproduction gate this stage has to keep.
	//
	// THE THREE THIS NODE OWES, written down here rather than created by this commit because
	// they are the node agent's (Stage 6a) and each needs a measurement this commit has not made:
	//
	//   META-INF/neoforge.mods.toml     the mod metadata, `expand`ed above. Different FILE NAME
	//                                   from LexForge's META-INF/mods.toml, so the two loader
	//                                   nodes need no rename, only their own copies. It is also
	//                                   where `[modproperties.archetypes] specialities_skills`
	//                                   goes — the external-skill entrypoint mechanism, which
	//                                   design §3.5 / R-A4 says needs the user's sign-off first.
	//   pack.mcmeta                     this loader mounts neither assets/ nor data/ without one.
	//                                   Missing it reproduces the R-16 tag cascade by another
	//                                   route, silently. `pack_format` is read out of the
	//                                   loader's own universal jar, not guessed.
	//   archetypes.client.mixins.json   the shared client config lists the `>=1.21.11` mixins;
	//                                   this node needs 1.21.1-fabric's list. It differs from
	//                                   that one in ONE entry — R-11: NeoForge's HUD is
	//                                   `RegisterGuiLayersEvent.wrapLayer`, so `GuiMixin.class`
	//                                   is in the jar but must NOT be listed in the config.
	//
	// Cost, and it is the usual one: an override does not inherit. Anything added to a shared
	// resource that this node needs has to be mirrored here by hand.
}

tasks {
	jar {
		// `project.name` is the NODE name here ("1.21.1-neoforge"), so the license entry has to
		// be keyed off mod.id to stay `LICENSE_archetypes`.
		val modId: String = sc.properties["mod.id"]
		inputs.property("modId", modId)
		from(rootProject.file("LICENSE")) { rename { "${it}_$modId" } }
		// DELTA: the `jar` task only bundles `main` by default. Measured next door: the produced
		// jar contained the common classes and NOT the client ones until this line was added.
		// Without it the whole client half of the mod silently vanishes from the artifact and the
		// mod loads but renders nothing — which for this mod is the tree screen, seven keybinds,
		// four HUD elements and five render layers.
		from(clientSourceSet.output)
	}

	// DELTA-CRITICAL, from the template: MDG's artifact task has no idea Stonecutter exists, so
	// on a NON-ACTIVE node (which this always is — the active node is 26.2-fabric and never
	// changes, conventions §2) it would run before the generated sources exist.
	named("createMinecraftArtifacts") {
		dependsOn("stonecutterGenerate")
	}

	register<Copy>("buildAndCollect") {
		group = "build"
		description = "Builds the mod jar and collects it into build/libs/<mod version>/"
		inputs.property("version", project.property("mod.version"))
		// No loomx here — there is no loom on this node and no remap step. MDG produces the final
		// jar as plain `jar`.
		from(jar.flatMap { it.archiveFile }, named<Jar>("sourcesJar").flatMap { it.archiveFile })
		into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
	}
}
