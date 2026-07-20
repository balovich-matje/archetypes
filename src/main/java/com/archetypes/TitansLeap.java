package com.archetypes;

import net.minecraft.server.level.ServerPlayer;

/**
 * Titan's Leap — the Colossus Crusher's epic active, ability slot 6 for a
 * Brawler. Slot 6 is shared with the Cutpurse's Dark Ritual; the dispatch picks
 * on archetype, so the two never collide.
 *
 * <p>Wiring only: the tree renders and sells, and the key reaches this method,
 * but nothing happens yet. The mechanics pass owns the leap, its cooldown
 * attachment and the landing nodes that read off it.
 */
public final class TitansLeap {
	private TitansLeap() {
	}

	/** Entry point for ability slot 6. No effect until the mechanics pass. */
	public static void leap(final ServerPlayer player) {
	}
}
