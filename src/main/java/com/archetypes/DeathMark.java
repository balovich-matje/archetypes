package com.archetypes;

import net.minecraft.server.level.ServerPlayer;

/**
 * Death Mark — the Nemesis Assassin's epic active, ability slot 5 for a
 * Cutpurse.
 *
 * <p>Wiring only: the tree renders and sells, and the key reaches this method,
 * but nothing happens yet. The mechanics pass owns the mark, its lifetime and
 * every node that reads off it.
 */
public final class DeathMark {
	private DeathMark() {
	}

	/** Entry point for ability slot 5. No effect until the mechanics pass. */
	public static void mark(final ServerPlayer player) {
	}
}
