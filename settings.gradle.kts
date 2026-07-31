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

		// ---- Stage 1: the home node, and nothing else yet. ----
		match("26.2", "fabric")

		// The node whose state the shared `src/` is committed in.
		vcsVersion = "26.2-fabric"
	}
}

// Keeps the jar base name and the mod id aligned.
rootProject.name = "archetypes"
