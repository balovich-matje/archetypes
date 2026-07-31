// Script plugin applied by `build.neoforge.gradle.kts` only. Skill Proficiencies' design
// R-14, adopted here unchanged.
//
// Body is the maintained multiloader template's own
// `buildSrc/src/main/kotlin/neoforge-mutex.gradle.kts`, taken from Skill Proficiencies'
// copy — which was re-fetched from the template and diffed on 2026-07-25 via
// `gh api repos/stonecutter-versioning/stonecutter-template-multiloader/contents/...`.
// Only the indentation is changed (tabs, both repos' style); every token is the template's,
// its own comment included. NEVER fetch stonecutter.kikugie.dev (R-13).
//
// WHAT IT IS FOR: this repo runs with `org.gradle.parallel=true`, and ModDevGradle's
// `createMinecraftArtifacts` runs the NeoForm decompile+recompile pipeline — measured next
// door at 63s of wall time and ~290% CPU for ONE node. Two NeoForge nodes doing that
// concurrently is what the template's author means by "frying your computer". With a single
// NeoForge node it still earns its place: `maxParallelUsages = 1` also serialises the task
// against itself across source-set variants and against a second invocation sharing the
// daemon, which is exactly what an unqualified `./gradlew buildAndCollect` across seven
// nodes does.
//
// `tasks.named { it == "createMinecraftArtifacts" }` is a name PREDICATE, not
// `tasks.named("...")`, and that is deliberate in the template: this script plugin is applied
// to the node project before MDG has necessarily registered the task, and the predicate form
// does not fail when the task is absent.

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

// This whole thing prevents neoforge from frying your computer by recompiling Minecraft on multiple versions
interface NeoForgeMutex : BuildService<BuildServiceParameters.None>

val mutex = gradle.sharedServices.registerIfAbsent("createMinecraftArtifactsMutex", NeoForgeMutex::class.java) {
	maxParallelUsages.set(1)
}

tasks.named { it == "createMinecraftArtifacts" }.configureEach {
	usesService(mutex)
}
