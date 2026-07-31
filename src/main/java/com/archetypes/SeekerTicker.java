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

/** Mana regeneration for every Seeker, every tick. */
public final class SeekerTicker {
	private SeekerTicker() {
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
				if (ModState.get(player) == Archetype.INTELLECT) {
					Mana.regenTick(player);
				}
			}
		});
	}
}
