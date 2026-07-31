package com.archetypes;

import com.archetypes.platform.Net;
import com.archetypes.state.WireId;

import net.minecraft.server.level.ServerPlayer;

/**
 * One-liner for proc sites: tell the owning client a passive just fired, so
 * the HUD can flash the node's icon at the crosshair. Fire-and-forget — the
 * indicator is cosmetic, the proc itself already happened server-side.
 */
public final class ProcIndicators {
	private ProcIndicators() {
	}

	public static void send(final ServerPlayer player, final SubTree tree, final Enum<?> family) {
		Net.INSTANCE.sendToClient(player, WireId.PASSIVE_PROC, buf -> {
			buf.writeUtf(tree.id());
			buf.writeUtf(family.name());
		});
	}
}
