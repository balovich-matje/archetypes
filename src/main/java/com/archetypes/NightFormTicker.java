package com.archetypes;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * Drives the Dark Ritual channel and the night form for every Cutpurse each
 * tick, plus the Feast bleeds in flight (mirrors OracleWizardTicker's shape).
 * Bleeds are ticked outside the player loop because their victims are mobs.
 */
public final class NightFormTicker {
	private NightFormTicker() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (ModAttachments.get(player) == Archetype.AGILITY) {
					NightForm.tick(player);
				}
			}

			NightForm.tickBleeds();
		});
	}
}
