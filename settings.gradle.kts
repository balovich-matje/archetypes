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

		// ---- Stage 4: the biggest single step in the port, registered as its own commit
		// for the same single-writer reason (conventions §1).
		//
		// The `>=1.21.11` boundary lands here WHOLE, and unlike Stage 3's it is deep as
		// well as wide. What is new here and nowhere above it:
		//
		//  * `hurtServer(ServerLevel,DamageSource,F)Z` does not exist. Every damage
		//    handler in the mod re-targets `hurt(DamageSource,F)Z`, which runs on BOTH
		//    logical sides — so each one needs the `isClientSide()` early-out Skill
		//    Proficiencies settled in its R-08. 18 handlers in LivingEntityMixin plus
		//    DamageTraceMixin's pair and FlenseMixin.
		//  * `world.item.component.BlocksAttacks` and `LivingEntity.applyItemBlocking`
		//    are gone, and the shield-disable path is plural rather than one call. The
		//    decision in force is EXCISION on this node family (design R-A5): the two
		//    Colossus Protector nodes stay purchasable and their effects no-op, and the
		//    descriptions say so. Approximating a defensive multiplier through a
		//    different chokepoint is the silent-divergence class R-20 exists to catch.
		//  * Player Animation Library's API forks for the first time — 1.1.5 spells the
		//    accessor `IAnimatedPlayer` and takes `AbstractClientPlayer` where 1.1.9/1.2.x
		//    spell it `IAnimatedAvatar` and take `Avatar`. One arm, because the library
		//    rename coincides exactly with the vanilla one. The dependency CONFIGURATION
		//    does not fork again: `FkO8Scek` is a Fabric jar declaring
		//    `Fabric-Mapping-Namespace: intermediary`, same as 1.21.11's, so
		//    `modImplementation` carries over unchanged.
		//  * `client.rendering.v1.hud` does not exist, so the HUD registration becomes a
		//    client `GuiMixin` — Skill Proficiencies' proven file shape, not a new one.
		//
		// Java stays at 21 (`requiredJava`), so this node adds no toolchain move.
		match("1.21.1", "fabric")

		// ---- Stage 5: the oldest node in the port, registered as its own commit for the
		// same single-writer reason (conventions §1).
		//
		// TWO frozen boundaries land here TOGETHER, which is what makes this node
		// different in kind from Stage 4's one deep boundary:
		//
		//  * `>=1.21` — no data components, no GUI sprite atlas at all, id-keyed
		//    `AttributeModifier` becomes the UUID+name form at every call site, the
		//    datapack tag directory is PLURAL (`tags/items/`, R-16), and looting/sweeping
		//    move off `EnchantmentHelper`'s modern accessors.
		//  * `>=1.20.5` — no payload stack (`FabricPacket`/`PacketType` inside the `Net`
		//    seam), no `StreamCodec`, no attachment SYNC AT ALL (fabric-api 0.92.11 has a
		//    persistent builder and nothing else), Java 21 -> 17, and
		//    `ServerLivingEntityEvents.AFTER_DAMAGE` does not exist.
		//
		// The Java level moves for the second time in the port and this is the one that
		// bites: `requiredJava` drops to 17, which is the SHARED-CODE CEILING for the whole
		// tree from here on (conventions §5e). Stage 0-G audited for it and fixed the two
		// real violations, so this is a toolchain assertion rather than an expedition.
		//
		// PAL is ABSENT on this node — no artifact exists for 1.20.1 on any loader — and
		// the decision in force is design §2.2's Option B: a no-op animation seam, the five
		// `*Animations` drivers gated out as whole compilation units, the dependency and its
		// `depends` line dropped. See the toml for the loss table's pointer.
		//
		// The shared tree does NOT compile for this node yet — that IS Stage 5 — so
		// `:1.20.1-fabric:build` and an unqualified `build` fail by design until the `//?`
		// forks land. `:1.20.1-fabric:stonecutterGenerate` is green, and every task on the
		// four registered nodes is unaffected.
		match("1.20.1", "fabric")

		// The node whose state the shared `src/` is committed in.
		vcsVersion = "26.2-fabric"
	}
}

// Keeps the jar base name and the mod id aligned.
rootProject.name = "archetypes"
