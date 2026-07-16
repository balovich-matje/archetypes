package com.archetypes;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Delivers Shadow Flurry's owed strikes, one every few ticks, so the four
 * hits read as a flurry rather than one big number. Stops early if the
 * target dies, unloads or slips out of reach.
 */
public final class AgilityTicker {
	private AgilityTicker() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				AttachmentTarget target = (AttachmentTarget) player;
				Integer left = target.getAttached(ModAttachments.STEP_STRIKES_LEFT);

				if (left == null || left <= 0) {
					continue;
				}

				long now = player.level().getGameTime();

				if (now % Tuning.FLURRY_STRIKE_PERIOD_TICKS != 0) {
					continue;
				}

				Integer targetId = target.getAttached(ModAttachments.STEP_TARGET);
				LivingEntity victim = targetId == null ? null
						: player.level().getEntity(targetId) instanceof LivingEntity living ? living : null;

				if (victim == null || !victim.isAlive()
						|| player.distanceTo(victim) > Tuning.FLURRY_REACH) {
					target.removeAttached(ModAttachments.STEP_STRIKES_LEFT);
					target.removeAttached(ModAttachments.STEP_TARGET);
					continue;
				}

				AgilityActives.strike(player, victim);

				if (left <= 1) {
					target.removeAttached(ModAttachments.STEP_STRIKES_LEFT);
					target.removeAttached(ModAttachments.STEP_TARGET);
				} else {
					target.setAttached(ModAttachments.STEP_STRIKES_LEFT, left - 1);
				}
			}
		});
	}
}
