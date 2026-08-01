// Node script for EVERY LexForge node. Today that is `1.20.1-forge` only.
//
// THERE IS NO TEMPLATE FOR THIS FILE. The maintained multiloader template ships
// build.fabric.gradle.kts and build.neoforge.gradle.kts and nothing else, and the abandoned
// Architectury template predates the wall (design R-12). So this script is Skill
// Proficiencies' bespoke one, adapted — and every line in it that is not obvious was measured
// there rather than guessed. NEVER fetch stonecutter.kikugie.dev (R-13).
//
// `//?` does not work in build scripts (conventions §5f).
//
// PUBLISHING landed with the Stage-6 integration, on all three node scripts in the same
// commit. The block is at the bottom of this file and mirrors build.fabric.gradle.kts, with
// the loader-axis deltas: always-suffixed version number, and a dependency set with neither
// Fabric API nor Player Animation Library (this node has no artifact for either).
plugins {
	// Applied DIRECTLY in the node script, not in settings.gradle.kts. Together with
	// `loom.platform=forge` in versions/1.20.1-forge/gradle.properties (read during plugin
	// apply, which is why it is a per-node property file and not a toml key), this is what
	// keeps exactly one loom on this node's buildscript classpath. The other half of that is
	// the R-01 `[fabric]` table in stonecutter.properties.toml — see the note there.
	id("dev.architectury.loom") version "1.17.491"
	// Modrinth uploads, one version per node. Dry run unless `-PpublishLive=true`.
	id("me.modmuss50.mod-publish-plugin") version "2.1.1"
}

version = "${property("mod.version")}+${sc.current.version}"
// Loader suffix, same reason as the NeoForge node: `1.20.1-fabric` and `1.20.1-forge` would
// otherwise both write `archetypes-1.1.0+1.20.1.jar` into build/libs/<mod.version>/.
base.archivesName = "${property("mod.id") as String}-forge"

// 1.20.1 is Java 17 — which is also the shared-code ceiling for the whole tree (conventions
// §5e). Written as a constant rather than as the Fabric script's ladder because this script
// has exactly one node and inventing a ladder would imply a 26.x LexForge that cannot exist.
val requiredJava: JavaVersion = JavaVersion.VERSION_17

repositories {
	// mixinextras-common / mixinextras-forge live on Maven Central. Loom adds its own
	// repositories (MavenRepo included) but naming it here keeps the resolution explicit.
	mavenCentral()
	/** Restricts [groups] to one maven so every other lookup skips it. */
	fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
		forRepository { maven(url) { name = alias } }
		filter { groups.forEach(::includeGroup) }
	}
	// Kept even though this node declares no Modrinth dependency: it is the repository the PAL
	// coordinate would come from, and its absence would turn "PAL has no LexForge build" into a
	// repository error instead of the deliberate `deps.pal` omission it is.
	strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

// MEASURED HARD FAILURE next door: `loom.splitEnvironmentSourceSets()` throws on the forge
// platform —
//   > Failed to setup Minecraft, java.lang.UnsupportedOperationException:
//     Using Forge with split jars is not supported!
// (Arch Loom 1.17.491). So `client` is a PLAIN source set inheriting main's classpath, exactly
// as the NeoForge node does for the merged-dev-jar reason. Runtime separation is
// `Dist`/`@OnlyIn`'s job. Declaring the source set is still mandatory: it is what makes
// Stonecutter preprocess `src/client` on this node.
val clientSourceSet: SourceSet = sourceSets.create("client") {
	compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
	runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

loom {
	silentMojangMappingsLicense()

	mods {
		create(sc.properties.get<String>("mod.id")) {
			sourceSet(sourceSets.main.get())
			sourceSet(clientSourceSet)
		}
	}

	forge {
		// LexForge finds mixin configs through the jar MANIFEST attribute `MixinConfigs`
		// (mixin-0.8.5.jar: `Constants$ManifestAttributes.MIXINCONFIGS = "MixinConfigs"`), NOT
		// through mods.toml — Forge's own mods.toml has no [[mixins]] table, unlike NeoForge's.
		// This call is what writes the attribute.
		//
		// BOTH configs are listed, and there is no per-config `environment` key the way
		// fabric.mod.json has. A CLIENT mixin whose target is absent on a dedicated server is
		// therefore a boot crash under `injectors.defaultRequire: 1`, and this repo's client
		// list is nine entries deep against `Gui`, `LevelRenderer`, `LocalPlayer`, `Minecraft`
		// and the two renderer accessors. That is the single most likely way this node dies on
		// its first Tier-2 run, and it is the node agent's first thing to check.
		mixinConfig(
			"archetypes.mixins.json",
			"archetypes.client.mixins.json",
		)
	}

	runConfigs.all {
		// Every node shares the repo-root `run/`, which is where the dev world and run/mods/
		// already live.
		runDirectory = rootProject.file("run")
		jvmArguments.add("-Dmixin.debug.export=true") // dumps transformed classes to run/.mixin.out
	}
}

dependencies {
	minecraft("com.mojang:minecraft:${sc.current.version}")
	mappings(loom.officialMojangMappings())
	// `forge` is Arch Loom's own configuration name; the string form avoids depending on a
	// generated Kotlin accessor.
	"forge"("net.minecraftforge:forge:${sc.current.version}-${sc.properties.get<String>("deps.forge")}")

	// ---- R-10: MixinExtras is NOT bundled by LexForge, and the design's one-liner
	// (`jarJar(implementation(...))`) is wrong in two ways. Both corrections are next door's,
	// measured on the artifacts:
	//
	// 1. WHICH ARTIFACT CARRIES WHAT. `mixinextras-forge-0.5.4.jar` is a THIN CONTAINER: 13
	//    entries, one class (com/llamalad7/mixinextras/platform/forge/MixinExtrasConfigPlugin),
	//    its `mixinextras.init.mixins.json`, `META-INF/jarjar/metadata.json`, and a NESTED
	//    `META-INF/jars/MixinExtras-0.5.4.jar` (715 KB) holding the real classes. Its MANIFEST
	//    carries `MixinConfigs: mixinextras.init.mixins.json` and `FMLModType: GAMELIBRARY`,
	//    which is the self-bootstrapping R-10 describes. Putting THAT on compileOnly gives the
	//    compiler nothing — `@WrapOperation` is not in it. `mixinextras-common-0.5.4.jar` is the
	//    one with the annotations AND with
	//    `META-INF/services/javax.annotation.processing.Processor` -> `MixinExtrasAP`. So
	//    `-common` goes on compileOnly AND annotationProcessor; `-forge` is the JiJ payload and
	//    nothing else.
	// 2. HOW TO JAR-IN-JAR IT. `jarJar(...)` is ForgeGradle syntax and does not exist here. Arch
	//    Loom's own JiJ configuration is `include`, and it emits the Forge layout
	//    (`JarNester.handleForgeJarJar`). It is the CONTAINER that gets nested, so the result is
	//    doubly nested (our jar -> mixinextras-forge -> MixinExtras) — the shape MixinExtras
	//    publishes for exactly this purpose. THE NODE AGENT MUST CONFIRM ON A REAL SERVER BOOT
	//    that Forge's JarJarSelector recurses into it and that `mixinextras.init.mixins.json`
	//    applies; no artifact read can answer that, and this repo has 18 injectors riding on it
	//    where next door had five.
	val mixinExtras: String = sc.properties["deps.mixinextras"]
	compileOnly("io.github.llamalad7:mixinextras-common:$mixinExtras")
	annotationProcessor("io.github.llamalad7:mixinextras-common:$mixinExtras")
	"include"("io.github.llamalad7:mixinextras-forge:$mixinExtras")
	// ---- end R-10

	// ---- NO PLAYER ANIMATION LIBRARY, and the absence is the decision ----
	//
	// `player-animation-library` declares loaders ["fabric","neoforge"] and has no LexForge
	// build at any Minecraft version; its lowest `game_version` is 1.21.1 anyway. So this node
	// takes design §2.2's Option B exactly as `1.20.1-fabric` does, and it needs no code of its
	// own to do it: the five `*Animations` drivers are whole-unit gated on the ABSENCE of
	// `deps.pal`, and this node's toml section declares none. Ten cosmetic animations across
	// five layers are quiet here; the loss table is design §2.2.

	// ---- Skill Proficiencies, compile-only ----
	//
	// Same per-node FILE dependency the other two node scripts use, same reason (design §3.5,
	// the fallback mechanism), and the FILE NAME forks the same way the NeoForge one does:
	// `specialities-forge-<v>+<mc>.jar`.
	//
	// `modCompileOnly` and not plain `compileOnly`, and this is the one configuration choice on
	// this node the design flagged as "verify, do not assume": that jar carries official class
	// names with SRG MEMBER names, and Arch Loom's remapper turns SRG into named. This repo only
	// IMPLEMENTS Skill Proficiencies' interfaces and calls its own methods — which are never
	// remapped — and the only Minecraft types in those signatures are class references, so plain
	// `compileOnly` would very likely also compile. `modCompileOnly` is the answer that is
	// correct for the same reason on every remapped node, so it is what the tree says
	// everywhere; if it ever fails here, the trial compile is the evidence to change it with.
	val specialitiesVersion: String = sc.properties["deps.specialities"]
	modCompileOnly(
		files(
			rootProject.file(
				"../specialities/build/libs/$specialitiesVersion/" +
					"specialities-forge-$specialitiesVersion+${sc.current.version}.jar",
			),
		),
	)
}

// LOADER-AXIS EXCLUSIONS (conventions §5e-ter), the mirror of build.fabric.gradle.kts's block:
// this node compiles the Forge seam implementations and neither the Fabric nor the NeoForge
// ones. `Forge*` is anchored, so it does NOT match `NeoForge*` — which is exactly why the two
// loader nodes can exclude each other by glob. The naming rule is documented in the Fabric
// script.
sourceSets["main"].java.exclude(
	"com/archetypes/platform/Fabric*.java",
	"com/archetypes/platform/NeoForge*.java",
	"com/archetypes/platform/ArchetypesNeoForge.java",
)
sourceSets["client"].java.exclude(
	"com/archetypes/client/NeoForge*.java",
)

java {
	withSourcesJar()
	sourceCompatibility = requiredJava
	targetCompatibility = requiredJava
	toolchain {
		languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
	}
}

// TRAP worth repeating because this file is new: `expand` runs the WHOLE file, comments
// included, through Groovy's SimpleTemplateEngine, so a dollar sign anywhere in mods.toml is
// template syntax. A literal `${…}` written inside a comment fails this task with
// "SimpleTemplateScript1.groovy: Unexpected input: '('", and a bare `Foo$Inner` in a comment
// would be a missing-property error. Nothing before processResources catches either.
val metadataProps: Map<String, String> = mapOf(
	"id" to sc.properties["mod.id"],
	"name" to sc.properties["mod.name"],
	"version" to project.version.toString(),
	"minecraft_range" to sc.properties["mod.mc_range"],
	"forge_floor" to sc.properties["deps.forge_floor"],
	"java_floor" to requiredJava.majorVersion,
)

// Same mechanism and same reason as the other two node scripts: a mixin listed in a config
// whose class is not in the jar is a HARD BOOT FAILURE, and `//?` cannot reach a `.json`.
// Blanking the line leaves valid JSON because the entry is never the array's last element.
//
// Only the COMMON config is touched here — this node's CLIENT config is a per-node override
// (see the note at the end of processResources), so its list is written by hand.
val strippedMixinEntries: List<String> = buildList {
	if (sc.current.parsed < "26.2") add("\"LevelExtractorMixin\"")
	if (sc.current.parsed < "1.21.11") add("\"BlocksAttacksMixin\"")
}

tasks.withType<ProcessResources>().configureEach {
	// A per-node override directory is a RESOURCE ROOT, so anything documenting it ships inside
	// that node's jar. Same glob build.fabric.gradle.kts uses, for the same reason.
	exclude("README-override*.md")

	// Mixin 0.8.5's CompatibilityLevel enum tops out at JAVA_18, so `JAVA_17` is a legal value.
	//
	// MEASURED CORRECTION next door, from the Tier-2 boot: the enum having the constant is not
	// the same as the runtime ACCEPTING it. LexForge's bundled Mixin logs, once per config,
	//   [main/WARN] [mixin/]: Compatibility level JAVA_17 specified by archetypes.mixins.json
	//   is higher than the maximum level supported by this version of mixin (JAVA_13).
	// and then clamps. It is benign — every mixin still applied — because the level governs
	// which mixin-class features Mixin will vouch for, not whether ASM can read a
	// class-file-major-61 mixin. DO NOT "fix" it by writing JAVA_13 into the shared config: that
	// lowers the level for every node and moves five Fabric jars' resource bytes for a log line.
	val mixinJava = "JAVA_${requiredJava.majorVersion}"
	metadataProps.forEach { (k, v) -> inputs.property(k, v) }
	inputs.property("mixinJava", mixinJava)
	inputs.property("strippedMixinEntries", strippedMixinEntries)
	// Conventions §5j: copy-spec ACTIONS are invisible to up-to-date checking, so every
	// conditional transform below declares its decision as an input under its OWN key.
	inputs.property("legacyTagDir", sc.current.parsed < "1.21")
	inputs.property("spriteRelocation", sc.current.parsed < "1.21")
	inputs.property("legacyDatapackDirs", sc.current.parsed < "1.21")
	inputs.property("legacyItemModels", sc.current.parsed < "1.21.4")

	filesMatching("META-INF/mods.toml") { expand(metadataProps) }
	filesMatching("*.mixins.json") {
		expand("java" to mixinJava)
		if (strippedMixinEntries.isNotEmpty()) {
			filter { line -> if (strippedMixinEntries.any(line::contains)) "" else line }
		}
	}

	// ---- R-A5 / R-A6 ARE CLOSED: this node marks NOTHING inert ----
	//
	// The `inertNodeKeys` filter that used to sit here is GONE, in step with
	// build.fabric.gradle.kts and build.neoforge.gradle.kts (build-script logic does not
	// inherit across node scripts, so the three copies emptied together). Every node the port
	// once could not host now has a legacy host; the long-form account, and the shape to reuse
	// if a key ever has to come back, is over the fabric script's copy.
	//
	// The ONE thing this node still qualifies is the mace clause immediately below, and that
	// is a different mechanism for a different reason: the node WORKS here, it is one weapon
	// short of its full promise because `Items.MACE` does not exist on 1.20.1.

	// ---- Unstoppable Force drops its MACE clause below 1.21 ----
	//
	// LIVE here, copied from build.fabric.gradle.kts — build-script logic does not inherit
	// across node scripts. The node itself works on this node (LivingEntityMixin's legacy
	// `isDamageSourceBlocked` arm); only its mace half is unreachable, because `Items.MACE`
	// does not exist on 1.20.1 and `WeaponClass.of` can therefore never answer MACE.
	// The long-form reasoning, including why this is not a per-node `en_us.json` override,
	// is over the fabric script's copy.
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

	// ---- The four `< 1.21` relocations, ALL FOUR LIVE ON THIS NODE ----
	//
	// Copied from build.fabric.gradle.kts; the long-form reasoning is at its copies. The short
	// version, and each one was a real bug somewhere first:
	//
	//   tags/item -> tags/items          1.20.1's tag loader hardcodes the plural literal, so
	//                                    the shared tag files are read by NOTHING without this.
	//   recipe -> recipes,               the whole datapack registry directory set renamed at
	//   advancement -> advancements      1.21. Found only by a POSITIVE probe on the 1.20.1
	//                                    Fabric rig — a directory the loader does not walk is an
	//                                    absence, not a parse failure, so there is no log line.
	//   textures/gui/sprites -> gui      there is no GUI sprite atlas at all below 1.21; a
	//                                    drawable is a FILE named by its full texture path.
	//   breeze_wand recipe excluded      `minecraft:breeze_rod` does not EXIST on 1.20.1 and
	//                                    substituting a different ingredient would make one item
	//                                    cost something different on one node — a silent balance
	//                                    divergence, which is what R-20 exists to catch. The
	//                                    ITEM stays registered and obtainable by command.
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

	// ---- The item MODEL DEFINITION layer is `>= 1.21.4`, LIVE here too ----
	//
	// Without it twenty-eight items ship with no model at all — invisible to every build and
	// visible only to a launched client. Ordered AFTER the tag rename above and anchored on the
	// full path, so a loose `/items/` test cannot catch a renamed tag JSON.
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

	// fabric.mod.json is in the shared src/main/resources and must not ship here.
	exclude("fabric.mod.json")

	// PACK.MCMETA, MODS.TOML AND THE CLIENT MIXIN CONFIG, and where they are NOT: all three are
	// PER-NODE OVERRIDES at versions/1.20.1-forge/src/{main,client}/resources/, not in the shared
	// `src/`, because the shared tree is read by all seven nodes. Fabric needs no pack.mcmeta at
	// all, and neither Fabric nor NeoForge wants `META-INF/mods.toml` — putting either in
	// `src/main/resources` would ship it inside all five Fabric jars and the NeoForge one, moving
	// their resource bytes and breaking the reproduction gate. There is no
	// `exclude("META-INF/mods.toml")` in build.fabric.gradle.kts to lean on, which is exactly why
	// the override route is taken instead of the shared-plus-exclude route `fabric.mod.json` uses
	// in the other direction (that one predates the loader axis).
	//
	// NOT CREATED BY THIS COMMIT — they are the node agent's (Stage 6b), and each needs a
	// measurement this commit has not made:
	//
	//   META-INF/mods.toml              `pack_format`-free, `expand`ed above, and where
	//                                   `[modproperties.archetypes] specialities_skills` goes —
	//                                   the external-skill entrypoint mechanism, which design
	//                                   §3.5 / R-A4 says needs the user's sign-off first.
	//   pack.mcmeta                     LOAD-BEARING, not cosmetic: without it this loader mounts
	//                                   neither the mod's `assets/` nor its `data/` root, so
	//                                   every tag, recipe and advancement is read by nothing and
	//                                   the R-16 cascade fires silently. The value is what
	//                                   `unzip -p forge-1.20.1-47.4.22-universal.jar pack.mcmeta`
	//                                   reports for Forge's own file — read it, do not copy the
	//                                   NeoForge node's.
	//   archetypes.client.mixins.json   1.20.1-fabric's list with ONE substitution: R-11's HUD
	//                                   answer on this loader is a per-node
	//                                   `client/mixin/ForgeGuiMixin.java` against `ForgeGui`, not
	//                                   the shared `GuiMixin` against `Gui`.
}

tasks {
	jar {
		val modId: String = sc.properties["mod.id"]
		inputs.property("modId", modId)
		from(rootProject.file("LICENSE")) { rename { "${it}_$modId" } }
		// The `client` source set's output is not in `jar` by default. Measured next door:
		// adding this put the client classes in the jar and they survived remapJar.
		from(clientSourceSet.output)
	}

	register<Copy>("buildAndCollect") {
		group = "build"
		description = "Builds the mod jar and collects it into build/libs/<mod version>/"
		inputs.property("version", project.property("mod.version"))
		// NOT loomx.modJar — loom-back-compat is not applied on this node. Arch Loom's remapped
		// outputs are `remapJar` / `remapSourcesJar`.
		//
		// MEASURED: the type argument must be `org.gradle.jvm.tasks.Jar`, NOT the `Jar` the
		// Kotlin DSL imports by default. `AbstractRemapJarTask extends
		// org.gradle.jvm.tasks.Jar`, and `org.gradle.api.tasks.bundling.Jar` is a SUBclass of
		// that, so `named<Jar>("remapJar")` fails at configuration time with "The task 'remapJar'
		// (net.fabricmc.loom.task.RemapJarTask) is not a subclass of the given type".
		from(
			named<org.gradle.jvm.tasks.Jar>("remapJar").flatMap { it.archiveFile },
			named<org.gradle.jvm.tasks.Jar>("remapSourcesJar").flatMap { it.archiveFile },
		)
		into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
	}
}

// ---------------------------------------------------------------------------
// MODRINTH RELEASE UPLOAD — mirrors build.fabric.gradle.kts, with the loader suffix that
// script's own comment demands: `1.20.1-fabric` and `1.20.1-forge` would otherwise both claim
// `<version>+1.20.1`.
val nodeKey: String = sc.current.project.substringBeforeLast('-')
val modVersion: String = sc.properties["mod.version"]

/** Game versions this node's jar gets marked compatible with when published. */
val compatibleVersions: List<String> = sc.properties.rawOrNull("mod", "mc_releases")
	?.asList().orEmpty().map { it.toString() }

// ALWAYS suffixed with the WHOLE node directory name, never bare.
val modrinthVersion: String = "$modVersion+$nodeKey-forge"

val changelogPath = "changelogs/$modVersion.md"
val changelogText: Provider<String> = providers
	.fileContents(rootProject.layout.projectDirectory.file(changelogPath))
	.asBytes.map { String(it, Charsets.UTF_8) }
	.orElse(providers.provider<String> { error("No release notes at $changelogPath — write them before publishing") })

val releaseTitle: Provider<String> = changelogText.map {
	it.trim().lineSequence().firstOrNull()?.takeIf { line -> line.startsWith("# ") }?.removePrefix("# ")?.trim().orEmpty()
}

val releaseNotes: Provider<String> = changelogText.map {
	val lines = it.trim().lines()
	(if (lines.firstOrNull()?.startsWith("# ") == true) lines.drop(1) else lines).joinToString("\n").trim()
}

val modrinthDisplayName: Provider<String> = releaseTitle.map { title ->
	buildString {
		append(modVersion)
		if (title.isNotEmpty()) append(" — ").append(title)
		append(" (").append(nodeKey).append(" Forge)")
	}
}

val publishLive: Boolean = providers.gradleProperty("publishLive").map(String::toBoolean).getOrElse(false)

val modrinthNameMax = 64
val modrinthNameSuffixLength = " ($nodeKey Forge)".length
val modrinthTitleBudget = modrinthNameMax - modVersion.length - " — ".length - modrinthNameSuffixLength

val checkedDisplayName: Provider<String> = modrinthDisplayName.map { name ->
	require(!publishLive || name.length <= modrinthNameMax) {
		"Modrinth caps a version name at $modrinthNameMax characters; this one is ${name.length}: " +
			"\"$name\". Shorten the `# ` title line in $changelogPath to at most " +
			"$modrinthTitleBudget characters — that is the budget every node can carry."
	}
	name
}

// The dependency set, and this node is the one with the SMALLEST one — both of the live
// versions' required rows are dropped here and each absence is a decision recorded elsewhere:
// no Fabric API because this is not a Fabric loader, and no Player Animation Library because
// the project declares loaders ["fabric","neoforge"] and has no LexForge build at ANY
// Minecraft version (design §2.2 Option B, the same absence that drops `deps.pal` from the
// toml, the five animation drivers and the metadata line). Skill Proficiencies stays optional.
publishMods {
	dryRun = !publishLive
	// Arch Loom's remapped output. The type argument must be `org.gradle.jvm.tasks.Jar` for
	// the reason `buildAndCollect` above records.
	file = tasks.named<org.gradle.jvm.tasks.Jar>("remapJar").flatMap { it.archiveFile }
	version = modrinthVersion
	displayName = checkedDisplayName
	changelog = releaseNotes
	type = STABLE
	// Literal: this script is build.forge.gradle.kts.
	modLoaders.add("forge")

	modrinth {
		projectId = "47EMhuFl"
		accessToken = providers.environmentVariable("MODRINTH_TOKEN")
		minecraftVersions.addAll(
			providers.provider {
				compatibleVersions.ifEmpty { error("`mod.mc_releases` is not declared for node ${sc.current.project}") }
			},
		)
		// NOT featured, same as the NeoForge node.
		featured = false
		optional("d4TtjlpN")
	}
}

tasks.register("printPublishMetadata") {
	group = "publishing"
	description = "Prints the Modrinth metadata this node would upload. Builds nothing, uploads nothing."
	val rows = listOf(
		"node" to sc.current.project,
		"minecraft (jar)" to sc.current.version,
		"version_number" to modrinthVersion,
		"game_versions" to compatibleVersions.toString(),
		"loaders" to "[forge]",
		"dependencies" to "required=[] optional=[d4TtjlpN skill-proficiencies]",
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
