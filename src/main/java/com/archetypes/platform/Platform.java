package com.archetypes.platform;

/**
 * Seam 3 of three (design {@code docs/MULTIVERSION.md} §3.3) — everything the mod
 * asks the mod LOADER, as opposed to the game.
 *
 * <p>Small, and small on purpose. Skill Proficiencies' {@code Platform} carries
 * three methods; Archetypes needs one. It has no config file, so there is no
 * {@code configDir()}, and it is the CONSUMER of the external-skill contract
 * rather than its host, so there is no {@code skillProviders()} — its own
 * {@code specialities:skills} entrypoint is declared in metadata and instantiated
 * by Skill Proficiencies, not by anything here.
 *
 * <p>Why a seam for one method: {@code FabricLoader} is a loader class with no
 * counterpart anywhere else ({@code ModList.get().isLoaded(id)} on both other
 * loaders), and it was named at nine sites across four files, three of them in
 * client render paths.
 */
public interface Platform {
	// Same three-arm BLOCK chain as the other two seams; see ArchetypeStore.
	Platform INSTANCE = new FabricPlatform();

	/**
	 * True if a mod with this id is loaded.
	 *
	 * <p>Every caller asks about {@code specialities}, and every one of them asks on
	 * a hot path (HUD rows every frame, bookmark re-anchoring every screen tick), so
	 * an implementation is expected to answer from a cached value rather than walk
	 * the mod list.
	 */
	boolean isModLoaded(String id);
}
