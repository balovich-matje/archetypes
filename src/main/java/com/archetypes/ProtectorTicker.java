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
		ServerTickEvents.END_SERVER_TICK.register(server -> {
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
			}
		});
	}
}
