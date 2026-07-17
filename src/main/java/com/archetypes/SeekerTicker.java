package com.archetypes;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;

/** Mana regeneration and the Priest's Vitality hearts, every tick. */
public final class SeekerTicker {
	private static final net.minecraft.resources.Identifier VITALITY_ID =
			Archetypes.id("vitality");

	private SeekerTicker() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (ModAttachments.get(player) != Archetype.INTELLECT) {
					continue;
				}

				Mana.regenTick(player);

				int vitality = PriestNodes.rank(SubTree.PRIEST,
						NodePurchases.owned(player, SubTree.PRIEST), PriestNodes.Family.VITALITY);
				var attribute = player.getAttribute(
						net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);

				if (attribute == null) {
					continue;
				}

				boolean has = attribute.hasModifier(VITALITY_ID);

				if (vitality > 0 && !has) {
					attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
							VITALITY_ID, Tuning.VITALITY_HEALTH_PER_RANK * vitality,
							net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
				} else if (vitality <= 0 && has) {
					attribute.removeModifier(VITALITY_ID);
				}
			}
		});
	}
}
