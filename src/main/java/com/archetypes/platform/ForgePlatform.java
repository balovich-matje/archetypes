package com.archetypes.platform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraftforge.fml.ModList;

/**
 * LexForge implementation of {@link Platform} for the {@code 1.20.1-forge} node.
 *
 * <p>ARTIFACT PROVENANCE — read out of {@code fmlcore-1.20.1-47.4.22-sources.jar},
 * {@code net/minecraftforge/fml/ModList.java}: {@code static ModList get()} and
 * {@code boolean isLoaded(String)}. Not recalled.
 *
 * <p>The only file in this mod that names {@code ModList} — the seam-hygiene rule
 * (Skill Proficiencies' conventions §5g), and a grep is the review gate for it. It is
 * also the whole of this seam here: Archetypes has no config file and is the CONSUMER
 * of the external-skill contract rather than its host, so neither of Skill
 * Proficiencies' other two {@code Platform} methods exists to implement.
 *
 * <p>The cache is the interface's own instruction rather than an optimisation invented
 * here: every caller asks about {@code specialities} and every one of them asks on a
 * hot path — three HUD rows per frame and a bookmark re-anchor per screen tick.
 * {@code ModList} cannot change while the game runs, so the answer cannot go stale.
 *
 * <p><b>Where the {@code [modproperties]} interop lives, and why it is NOT here.</b>
 * Skill Proficiencies' {@code ForgePlatform} carries a {@code skillProviders()} that
 * walks {@code ModList.get().getMods()} looking for a
 * {@code [modproperties.<modid>] specialities_skills} key. Archetypes is on the other
 * end of that contract: it DECLARES the key, in
 * {@code versions/1.20.1-forge/src/main/resources/META-INF/mods.toml}, and Skill
 * Proficiencies instantiates {@code com.archetypes.compat.SpellcastingEntrypoint}
 * itself. So the interop costs this node one metadata line and no code at all.
 */
final class ForgePlatform implements Platform {
	private final Map<String, Boolean> loaded = new ConcurrentHashMap<>();

	ForgePlatform() {
	}

	@Override
	public boolean isModLoaded(final String id) {
		return this.loaded.computeIfAbsent(id, ModList.get()::isLoaded);
	}
}
