package com.archetypes.platform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.neoforged.fml.ModList;

/**
 * {@link Platform} on NeoForge. The only file on this node that names
 * {@code ModList} — the seam-hygiene rule (Skill Proficiencies' conventions §5g),
 * and a grep is the review gate for it.
 *
 * <p><b>No {@code //?} anywhere in this file, and that is the naming rule doing its
 * job rather than an oversight.</b> {@code com/archetypes/platform/NeoForge*.java} is
 * excluded from every Fabric and Forge node's source set by anchored glob (see the
 * three node scripts), so this compilation unit only ever sees a NeoForge node —
 * today exactly one, {@code 1.21.1-neoforge}. A second NeoForge node at a different
 * Minecraft version is where the version predicates would arrive; there is no point
 * writing them before that node exists and can measure them.
 *
 * <p>Archetypes needs one method where Skill Proficiencies' {@code Platform} carries
 * three: there is no config file, and — the interesting half — <b>no
 * {@code skillProviders()}</b>. Archetypes is the CONSUMER of the external-skill
 * contract, not its host. On this loader that contract is a
 * {@code [modproperties.archetypes] specialities_skills = "…"} key in this node's
 * {@code neoforge.mods.toml}, which Skill Proficiencies' own {@code NeoForgePlatform}
 * walks and instantiates. Nothing in this repo reads it; the whole Archetypes side of
 * the mechanism is four lines of metadata.
 */
final class NeoForgePlatform implements Platform {
	/**
	 * Cached for the same reason the Fabric implementation caches: the callers are
	 * per-frame HUD rows and a per-tick screen re-anchor. {@code ModList.isLoaded}
	 * is a map lookup rather than a scan, so this is cheap insurance rather than a
	 * necessity — but the two implementations answering at the same cost is worth
	 * more than the eight bytes.
	 *
	 * <p>It cannot go stale: the mod list is fixed once the game is running.
	 */
	private final Map<String, Boolean> loaded = new ConcurrentHashMap<>();

	NeoForgePlatform() {
	}

	@Override
	public boolean isModLoaded(final String id) {
		return this.loaded.computeIfAbsent(id, ModList.get()::isLoaded);
	}
}
