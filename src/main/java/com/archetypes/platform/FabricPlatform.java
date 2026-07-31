package com.archetypes.platform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.loader.api.FabricLoader;

/**
 * {@link Platform} on Fabric. The only file in the mod that names
 * {@code FabricLoader} — the seam-hygiene rule (Skill Proficiencies' conventions
 * §5g), and a grep is the review gate for it.
 */
final class FabricPlatform implements Platform {
	/**
	 * Answers are cached because the callers are per-frame HUD rows and a per-tick
	 * screen re-anchor. The mod list cannot change while the game is running, so a
	 * cache here can never go stale — which is more than the call sites it replaces
	 * could say: three of them cached the answer in their own {@code static final}
	 * and one did not.
	 */
	private final Map<String, Boolean> loaded = new ConcurrentHashMap<>();

	FabricPlatform() {
	}

	@Override
	public boolean isModLoaded(final String id) {
		return this.loaded.computeIfAbsent(id, FabricLoader.getInstance()::isModLoaded);
	}
}
