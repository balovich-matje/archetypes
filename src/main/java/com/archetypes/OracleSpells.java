package com.archetypes;

import net.minecraft.server.level.ServerPlayer;

/**
 * The epic Oracle actives. These are deliberate no-op stubs: the keybind and
 * dispatch plumbing lands now, the real effects (and their node-ownership and
 * mana checks) land in a later pass. Each active has one clear method so the
 * dispatch reads the same as the base Seeker spells.
 */
public final class OracleSpells {
	private OracleSpells() {
	}

	/**
	 * Oracle Elementalist active: Lightning Strike — a targeted bolt for
	 * massive single-target damage, later chaining, recurring and going AOE by
	 * node. Node-ownership and mana gating are intentionally left out of this
	 * stub until the effect exists.
	 */
	public static void lightningStrike(final ServerPlayer player) {
	}

	/**
	 * Oracle Wizard active: Magic Armaments — a channelled conjured weapon in
	 * place of the wand. Node-ownership and mana gating are intentionally left
	 * out of this stub until the effect exists.
	 */
	public static void magicArmaments(final ServerPlayer player) {
	}
}
