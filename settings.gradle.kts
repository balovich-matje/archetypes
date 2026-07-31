pluginManagement {
	repositories {
		mavenCentral()
		gradlePluginPortal()
		maven("https://maven.fabricmc.net/") { name = "FabricMC" }
		maven("https://maven.kikugie.dev/releases") { name = "KikuGieReleases" }
		// Loader-axis mavens (Stage 6). Kept here so adding a NeoForge/Forge node is a
		// one-line change in the tree below. `maven.minecraftforge.net` is NOT optional
		// for Architectury Loom: its own buildscript classpath needs
		// `de.oceanlabs.mcp:mcinjector` from there (Skill Proficiencies R-01 / design §1.1).
		maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
		maven("https://maven.architectury.dev/") { name = "Architectury" }
		maven("https://maven.minecraftforge.net/") { name = "Forge" }
	}
}

plugins {
	id("dev.kikugie.stonecutter") version "0.9.7"
	// Picks fabric-loom (26.x, unobfuscated) or fabric-loom-remap (<=1.21.11) per node.
	id("dev.kikugie.loom-back-compat") version "0.4.1"
	// Provisions the JDK a node's toolchain asks for (17/21/25).
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
	create(rootProject) {
		/**
		 * Creates `versions/<project>-<loader>` nodes, each on `build.<loader>.gradle.kts`.
		 * `project` is the folder name part, `version` is the real Minecraft version.
		 */
		fun match(project: String, vararg loaders: String, version: String = project) {
			for (loader in loaders) version("$project-$loader", version).buildscript("build.$loader.gradle.kts")
		}

		// ---- Stage 1: the home node. ----
		match("26.2", "fabric")
		// ---- Stage 2's beachhead, registered here as its own commit per the
		// bottleneck-file rule (conventions §1) so the stage can start.
		//
		// This is a deliberate deviation from Skill Proficiencies, whose beachhead was
		// 1.21.11 because its 26.1 was nearly free. Archetypes' 26.1 is NOT free, and the
		// measurements are in docs/MULTIVERSION.md §5.3: `LivingEntity.knockback(…,
		// DamageSource, …)`, the 3-arg `blockedByItem`, the 4-arg `Player.blockUsingItem`,
		// `net.minecraft.client.gui.Hud` and `net.minecraft.client.renderer.extract.
		// LevelExtractor` are ALL 26.2-only — the damage funnel's knockback trio, the
		// shield-block hook, the undead heart swap and ESP's wall-piercing, breaking at the
		// very first step down. Those are worth finding on the node that is otherwise
		// closest to home (Java 25, jspecify, Identifier, extract-render, the render-state
		// architecture, HudElementRegistry, and a PAL artifact that needs no
		// dependency-configuration fork).
		//
		// The shared tree does NOT compile for this node yet — that IS Stage 2 — so
		// `:26.1-fabric:build` and an unqualified `build`/`buildAndCollect` fail by design
		// until the `//?` forks land. `:26.1-fabric:stonecutterGenerate` is green, and
		// every task on the 26.2 node is unaffected.
		match("26.1", "fabric", version = "26.1.2")

		// ---- Stage 3: the first REMAPPED node, registered as its own commit for the
		// same single-writer reason (conventions §1).
		//
		// What is new here and nowhere above it, all of it measured rather than assumed:
		//
		//  * The `>=26.1` boundary lands whole. 26.x GUI rendering is EXTRACT-based
		//    (`GuiGraphicsExtractor`, `extractRenderState`, `text()`, `fakeItem()`); from
		//    1.21.11 down it is immediate (`GuiGraphics`, `render`, `drawString()`,
		//    `renderFakeItem()`). Twelve client files carry that type.
		//  * Loom REMAPS this node to intermediary, so every mixin `method =` is resolved
		//    through mappings.tiny for the first time. Stage 0-E's full descriptors are
		//    what make that safe; a bare name would resolve here by luck, not by rule.
		//  * Player Animation Library's dependency CONFIGURATION forks here and the
		//    failure is silent, not loud: 1.1.9 `BXYewCJb` declares
		//    `Fabric-Mapping-Namespace: intermediary` (checked in the jar), so it must go
		//    through `modImplementation`, where the 26.x Merged jars must NOT.
		//    Its API needs no source fork — all six types this repo imports are
		//    signature-identical to 1.2.5 (`javap -p` on both jars, §2.1).
		//  * Skill Proficiencies' matching artifact is `intermediary` too, so the interop
		//    dependency becomes `modCompileOnly` (design §3.5).
		//
		// Java drops to 21 on this node (`requiredJava` in the node script), which is the
		// first time the shared tree is compiled below 25.
		match("1.21.11", "fabric")

		// The node whose state the shared `src/` is committed in.
		vcsVersion = "26.2-fabric"
	}
}

// Keeps the jar base name and the mod id aligned.
rootProject.name = "archetypes"
