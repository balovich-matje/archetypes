// VERBATIM from the maintained multiloader template
// (`gh api repos/stonecutter-versioning/stonecutter-template-multiloader/contents/buildSrc/build.gradle.kts`),
// by way of Skill Proficiencies' copy of it — which was re-fetched and diffed against the
// template on 2026-07-25 and is byte-identical to this. Do not embellish it: buildSrc is
// compiled before every other script in the build, so anything added here is paid on every
// invocation of every node, including the five Fabric ones that never look at it.
//
// NEVER fetch stonecutter.kikugie.dev for this (design R-13) — the template repo and the
// stonecutter sources jar are the ground truth.
plugins {
	`kotlin-dsl`
}

repositories {
	mavenCentral()
}
