plugins {
	id("dev.kikugie.stonecutter")
}

// The node the IDE edits, and the state `src/` is physically in on disk.
// Stonecutter compiles the ACTIVE node straight from `src/` with no preprocessing,
// so `src/` must always be valid for whatever this line names.
// Switch with `./gradlew stonecutterSwitchTo26.1-fabric` (rewrites `src/` in place).
stonecutter active "26.2-fabric"

stonecutter parameters {
	val (version, loader) = current.project.split('-', limit = 2)

	// Unlocks the `["<version>"]` and `[<loader>."<version>"]` sections of
	// stonecutter.properties.toml as plain project properties.
	properties {
		tags(version, loader)
	}

	// FROZEN LOADER CONSTANTS — see specialities/docs/MULTIVERSION-CONVENTIONS.md §3,
	// which binds this repo too. Usable as `//? if fabric {` / `//?} elif neoforge {` /
	// `//?} elif forge {`.
	constants {
		match(loader, "fabric", "neoforge", "forge")
	}

	// `/*$ mod_version*/ "1.1.0"` style substitutions. Unused today; declared so the
	// version string never has to be duplicated into Java when it is needed.
	swaps["mod_version"] = "\"${properties.get<String>("mod.version")}\";"
	swaps["minecraft"] = "\"${node.metadata.version}\";"

	// Enables `//? if fapi: >=0.100 {` predicates.
	dependencies["fapi"] = properties.getOrNull<String>("deps.fabric_api") ?: "0"

	// Copied from Skill Proficiencies' controller, and the reasoning is copied with them
	// because both are load-bearing here rather than inert.
	//
	// Replacements are DIRECTIONAL, not one-way: `string(cond) { replace(a, b) }` rewrites
	// a->b when `cond` holds and b->a when it does not. So the shared tree may be authored
	// in either spelling — it is authored in the 26.x spelling, matching the active node.
	//
	// Both entries are inert on every node registered so far and stay inert until 1.21.1
	// lands: 1.21.11 is the OLDEST version that already spells it `Identifier`, AND the
	// oldest that already spells it `net.minecraft.util.Util`.
	//
	// `Identifier` is NOT cosmetic for this repo: `compat/SpellcastingSkill` implements
	// Skill Proficiencies' `SkillType`, whose `iconTexture()`/`icon()`/`displayName()`
	// return `Identifier` on 26.x and `ResourceLocation` on every jar below 1.21.11
	// (measured on the shipped 1.6.0 artifacts). Skill Proficiencies' own source carries
	// the unconditional `Identifier` import and this same rule; the consumer needs it too.
	//
	// The Util boundary is a package MOVE at exactly 1.21.11 and it is directional in the
	// dangerous direction: below 1.21.11 the rule rewrites `net.minecraft.util.Util` DOWN
	// to `net.minecraft.Util`. Importers in this tree: client/SunBlindOverlay.java,
	// client/ArchetypePickerScreen.java, client/DeadeyeOverlay.java. A `//?` block cannot
	// fix this from the source side — replacements are applied to the generated text
	// regardless of branch state, so only this condition can.
	replacements {
		string(current.parsed >= "1.21.11") {
			replace("ResourceLocation", "Identifier")
		}

		string(current.parsed >= "1.21.11") {
			replace("net.minecraft.Util", "net.minecraft.util.Util")
		}

		// STAGE 2, and the first replacement this repo owns rather than inherits.
		//
		// 26.2 fixed a nine-year-old vanilla TYPO. Three names change and all three are
		// the same substring, so one rule covers them:
		//
		//     InstantaneousMobEffect       <- InstantenousMobEffect
		//     MobEffect.applyInstantaneousEffect  <- applyInstantenousEffect
		//     MobEffect.isInstantaneous    <- isInstantenous
		//
		// MEASURED, not assumed: `unzip -l` on the mojmap common jar of every version
		// this port targets says `InstantenousMobEffect` on 1.20.1, 1.21.1, 1.21.11 and
		// 26.1.2, and `InstantaneousMobEffect` on 26.2 alone — one boundary, at exactly
		// 26.2, good for every node still to come.
		//
		// A replacement rather than `//?` deliberately: the rename reaches a mixin
		// `method =` STRING (client-facing descriptors in HealOrHarmMobEffectMixin) as
		// well as an import, a class header and two `@Override`s. A textual rule catches
		// the string literal for free; five `//?` blocks would each have to remember it.
		// Nothing in this repo's own vocabulary contains the substring, so the rule
		// cannot hit anything but vanilla's spelling.
		string(current.parsed >= "26.2") {
			replace("Instantenous", "Instantaneous")
		}
	}
}
