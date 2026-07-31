package com.archetypes;

// STAGE 6 — the loader-event helpers live in `com.archetypes.platform`, the one package
// allowed to name loader API (conventions §5g). Only this import and the registration line
// below fork; the tick body is one implementation on all seven nodes.
//? if fabric {
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
//?} elif neoforge {
/*import com.archetypes.platform.NeoForgeEvents;
*///?} elif forge {
/*import com.archetypes.platform.ForgeEvents;
*///?}
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
		// Registration only; the body below is shared. A loader helper fires its consumer ONCE
		// per server tick, at the END of it, with the `MinecraftServer` — the END_SERVER_TICK
		// contract, which is what R-20 says a re-rooted event has to reproduce rather than
		// merely fire somewhere plausible.
		//? if fabric {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
		//?} elif neoforge {
		/*NeoForgeEvents.endServerTick(server -> {
		*///?} elif forge {
		/*ForgeEvents.endServerTick(server -> {
		*///?}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (ModState.get(player) == Archetype.AGILITY) {
					NightForm.tick(player);
				} else if (NightForm.isActive(player) || NightForm.isChannelling(player)) {
					// Belt and braces: every archetype-losing path already ends
					// the form (ModState.forgetNodes), but the form's own
					// effects read the stamp while only this ticker clears it —
					// so a stamp without the archetype would strand a vampire.
					NightForm.end(player);
				}
			}

			NightForm.tickBleeds();
		});

		// A static list of live entities must never survive its server: the
		// next singleplayer world would tick bleeds on stale references.
		// A loader helper must fire AFTER the server has stopped, once, per server — the
		// SERVER_STOPPED contract. Firing it on a reload or once per level would clear a live
		// bleed list, which is a silent loss of a damage-over-time and exactly the R-20 class.
		//? if fabric {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED
				.register(server -> NightForm.clearBleeds());
		//?} elif neoforge {
		/*NeoForgeEvents.serverStopped(server -> NightForm.clearBleeds());
		*///?} elif forge {
		/*ForgeEvents.serverStopped(server -> NightForm.clearBleeds());
		*///?}
	}
}
