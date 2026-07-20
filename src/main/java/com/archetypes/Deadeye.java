package com.archetypes;

import net.minecraft.server.level.ServerPlayer;

/**
 * Deadeye — the Nemesis Marksman's epic active, ability slot 4 for a Cutpurse.
 *
 * <p>Wiring only: the tree renders and sells, and the key reaches this method,
 * but nothing happens yet. The mechanics pass owns the window itself, its
 * cooldown attachment and every node that reads off it.
 */
public final class Deadeye {
	private Deadeye() {
	}

	/** Entry point for ability slot 4. No effect until the mechanics pass. */
	public static void activate(final ServerPlayer player) {
	}
}
