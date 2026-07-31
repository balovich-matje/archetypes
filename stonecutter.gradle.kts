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

		// STAGE 4. 1.21.11 broke `net.minecraft.world.entity.projectile` into
		// sub-packages; below it every one of these classes sits in the flat package.
		// MEASURED with `unzip -l` on the mojmap common jar of 1.21.1 — all five are
		// `world/entity/projectile/<Class>.class` there, and `windcharge` exists as a
		// sub-package but one level higher than 26.x puts it.
		//
		// A replacement rather than a `//?` on each import, for the reason the
		// `Instantenous` rule above records: a textual rule catches every spelling of the
		// name, and two of these are written FULLY QUALIFIED INLINE rather than imported
		// (`instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow` in
		// ArmourMath and DamageTrace), which an import fork cannot reach without the
		// inline directive form. Twelve `//?` blocks carrying zero semantic content would
		// also bury the ones in the same files that carry a great deal.
		//
		// WHOLE CLASS NAMES, never the package prefix alone, and that is load-bearing:
		// replacements are DIRECTIONAL, so a rule written
		// `replace("…entity.projectile.", "…entity.projectile.hurtingprojectile.")` would
		// on 26.x rewrite EVERY `projectile.` occurrence — `projectile.Projectile`,
		// `projectile.ProjectileUtil`, `projectile.ProjectileDeflection` — into a package
		// that does not contain them. One rule per class is the only safe shape.
		//
		// `Projectile`, `ProjectileUtil`, `ProjectileDeflection` and `ShulkerBullet` did
		// NOT move and deliberately have no rule.
		//
		// STAGE 4, second half of the same idea: 1.21.2 renamed five `MobEffects`
		// constants to the in-game ids. This is Skill Proficiencies' frozen `>=1.21.2`
		// row, which already names `MobEffects.SPEED` — the other four ride the same
		// release and are recorded in docs/MULTIVERSION.md §4.1 rather than given a
		// boundary of their own.
		//
		// MEASURED on the mojmap common jar of 1.21.1 and of 1.20.1, which agree:
		//   SPEED <- MOVEMENT_SPEED     SLOWNESS <- MOVEMENT_SLOWDOWN
		//   STRENGTH <- DAMAGE_BOOST    RESISTANCE <- DAMAGE_RESISTANCE
		// Twenty-four call sites across fifteen files, all of them a bare constant read.
		//
		// A replacement rather than 24 `//?` blocks, and for this one the safety argument
		// runs the OTHER way from the usual: the failure mode of a bad replacement is a
		// mangled identifier, i.e. a compile error on the node that has it, while the
		// failure mode of 24 hand-written else arms is one of them drifting — which for a
		// mob effect is a silent balance change. Two arms that cannot drift beat two arms
		// that a reviewer has to diff.
		//
		// THE FIFTH RENAME, `JUMP_BOOST <- JUMP`, IS NOT HERE AND MUST NOT BE. Its legacy
		// spelling is a PREFIX of its modern one, and replacements are directional: on
		// every node at or above the boundary the rule would run `MobEffects.JUMP` ->
		// `MobEffects.JUMP_BOOST` over source that already reads `MobEffects.JUMP_BOOST`,
		// producing `MobEffects.JUMP_BOOST_BOOST`. Its one call site (ShadowTicker) takes
		// an ordinary `//?` block. Stage 5 inherits this constraint unchanged.
		string(current.parsed >= "1.21.2") {
			replace("MobEffects.MOVEMENT_SPEED", "MobEffects.SPEED")
			replace("MobEffects.MOVEMENT_SLOWDOWN", "MobEffects.SLOWNESS")
			replace("MobEffects.DAMAGE_BOOST", "MobEffects.STRENGTH")
			replace("MobEffects.DAMAGE_RESISTANCE", "MobEffects.RESISTANCE")
		}

		string(current.parsed >= "1.21.11") {
			replace(
				"net.minecraft.world.entity.projectile.AbstractArrow",
				"net.minecraft.world.entity.projectile.arrow.AbstractArrow",
			)
			replace(
				"net.minecraft.world.entity.projectile.Arrow",
				"net.minecraft.world.entity.projectile.arrow.Arrow",
			)
			replace(
				"net.minecraft.world.entity.projectile.ThrowableItemProjectile",
				"net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile",
			)
			replace(
				"net.minecraft.world.entity.projectile.AbstractHurtingProjectile",
				"net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile",
			)
			replace(
				"net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge",
				"net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge",
			)
		}
	}
}
