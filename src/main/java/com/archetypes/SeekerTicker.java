package com.archetypes;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;

/** Mana regeneration, every tick, for Seekers only. */
public final class SeekerTicker {
	private SeekerTicker() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (ModAttachments.get(player) == Archetype.INTELLECT) {
					Mana.regenTick(player);
				}
			}
		});
	}
}
