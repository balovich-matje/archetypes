package com.archetypes;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * Keeps {@link ModAttachments#BULWARK_ACTIVE} true exactly while a capstone
 * holder blocks. The attachment syncs to every client, where a render layer
 * draws ghost shields orbiting the player — set/removed only on change, so the
 * common case costs one boolean check per player per tick and no traffic.
 */
public final class ProtectorTicker {
	private ProtectorTicker() {
	}

	public static void initialize() {
		// The formation list is static and an integrated server is rebuilt inside
		// one JVM on every world exit, so it is emptied with the server.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED
				.register(server -> SpearPhalanx.forget());

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			// Spear Phalanx's planted spears: thrust, then sweep. They ride
			// this ticker rather than owning one because they are decoration
			// with a countdown, and the list is empty on almost every tick.
			SpearPhalanx.tick(server);

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				boolean should = player.isBlocking()
						&& ProtectorNodes.rank(SubTree.PROTECTOR,
								NodePurchases.owned(player, SubTree.PROTECTOR),
								ProtectorNodes.Family.OMNI_BLOCK) > 0;

				AttachmentTarget target = (AttachmentTarget) player;
				Boolean current = target.getAttached(ModAttachments.BULWARK_ACTIVE);

				if (should && current == null) {
					target.setAttached(ModAttachments.BULWARK_ACTIVE, true);
				} else if (!should && current != null) {
					target.removeAttached(ModAttachments.BULWARK_ACTIVE);
				}

				// Spearwall's guard, on the same terms and for the same reason:
				// onlookers cannot run guardingShield for somebody else's build,
				// so the answer is published rather than recomputed.
				boolean guarding = Spearwall.guardingShield(player) != null;
				Boolean raised = target.getAttached(ModAttachments.SPEARWALL_GUARD);

				if (guarding && raised == null) {
					target.setAttached(ModAttachments.SPEARWALL_GUARD, true);
				} else if (!guarding && raised != null) {
					target.removeAttached(ModAttachments.SPEARWALL_GUARD);
				}
			}
		});
	}
}
