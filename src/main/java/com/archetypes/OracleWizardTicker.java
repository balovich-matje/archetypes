package com.archetypes;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;

/** Drives the Magic Armaments channel: upkeep, its grants, and the guards that
 * end it, for every Seeker each tick (mirrors SeekerTicker's shape). */
public final class OracleWizardTicker {
	private OracleWizardTicker() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (ModAttachments.get(player) == Archetype.INTELLECT) {
					MagicArmaments.tick(player);
				}
			}
		});
	}
}
