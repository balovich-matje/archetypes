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
				} else if (NightForm.isActive(player) || NightForm.isChannelling(player)) {
					// Belt and braces: every archetype-losing path already ends
					// the form (ModAttachments.forgetNodes), but the form's own
					// effects read the stamp while only this ticker clears it —
					// so a stamp without the archetype would strand a vampire.
					NightForm.end(player);
				}
			}

			NightForm.tickBleeds();
		});

		// A static list of live entities must never survive its server: the
		// next singleplayer world would tick bleeds on stale references.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED
				.register(server -> NightForm.clearBleeds());
	}
}
