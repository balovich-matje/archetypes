package com.archetypes;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Ongoing Protector presentation. Currently just the Bulwark aura: while a
 * capstone holder is actively blocking, a slow ring of light orbits them — the
 * honest signal, to them and to anyone circling for a backstab, that there is
 * no behind to hit.
 */
public final class ProtectorTicker {
	/** Every 4 ticks: visible as a ring, cheap on the particle budget. */
	private static final int AURA_PERIOD = 4;
	private static final double AURA_RADIUS = 1.1;
	private static final int AURA_POINTS = 3;

	private ProtectorTicker() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long time = server.overworld().getGameTime();

			if (time % AURA_PERIOD != 0) {
				return;
			}

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (!player.isBlocking()) {
					continue;
				}

				var owned = NodePurchases.owned(player, SubTree.PROTECTOR);

				if (ProtectorNodes.rank(SubTree.PROTECTOR, owned, ProtectorNodes.Family.OMNI_BLOCK) == 0) {
					continue;
				}

				// A few points of a ring that rotates with time, drifting upward.
				ServerLevel level = (ServerLevel) player.level();
				double base = (time % 80) / 80.0 * Math.PI * 2.0;

				for (int i = 0; i < AURA_POINTS; i++) {
					double angle = base + i * (Math.PI * 2.0 / AURA_POINTS);
					level.sendParticles(ParticleTypes.END_ROD,
							player.getX() + Math.cos(angle) * AURA_RADIUS,
							player.getY() + 0.9,
							player.getZ() + Math.sin(angle) * AURA_RADIUS,
							0, 0.0, 0.04, 0.0, 1.0);
				}
			}
		});
	}
}
