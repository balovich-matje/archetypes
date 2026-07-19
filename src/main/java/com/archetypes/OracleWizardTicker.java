package com.archetypes;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;

/** Drives the Magic Armaments channel: upkeep, its grants, and the guards that
 * end it, for every Seeker each tick (mirrors SeekerTicker's shape). */
public final class OracleWizardTicker {
	private OracleWizardTicker() {
	}

	public static void initialize() {
		// A conjured weapon must never exist as a world item: a Q-drop, a
		// broken container, or a chunk saved before this guard shipped all
		// surface here, and the entity is voided before anyone reaches it.
		// (Death drops don't: the death hook ends the channel first.)
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item && ModItems.isSummoned(item.getItem())) {
				item.discard();
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (ModAttachments.get(player) == Archetype.INTELLECT) {
					MagicArmaments.tick(player);
				}
			}
		});
	}
}
