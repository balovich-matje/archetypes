package com.archetypes;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Delivers Shadow Flurry's owed strikes, one every few ticks, so the four
 * hits read as a flurry rather than one big number — and keeps the
 * Assassin's dagger-stance modifiers (Lightfoot's feet, Frenzy's hands) in
 * step with what's in the main hand.
 */
public final class AgilityTicker {
	private static final net.minecraft.resources.Identifier LIGHTFOOT_ID =
			Archetypes.id("lightfoot");
	private static final net.minecraft.resources.Identifier FRENZY_ID =
			Archetypes.id("frenzy");

	private AgilityTicker() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				AttachmentTarget target = (AttachmentTarget) player;

				boolean dagger = ModItems.isDagger(player.getMainHandItem());
				var owned = NodePurchases.owned(player, SubTree.ASSASSIN);
				stance(player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED),
						LIGHTFOOT_ID, dagger,
						Tuning.LIGHTFOOT_PER_RANK
								* AssassinNodes.rank(SubTree.ASSASSIN, owned, AssassinNodes.Family.LIGHTFOOT));
				stance(player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED),
						FRENZY_ID, dagger,
						Tuning.FRENZY_PER_RANK
								* AssassinNodes.rank(SubTree.ASSASSIN, owned, AssassinNodes.Family.FRENZY));
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

	/** Keep a transient stance modifier in step with rank and hand. */
	private static void stance(final net.minecraft.world.entity.ai.attributes.AttributeInstance attribute,
			final net.minecraft.resources.Identifier id, final boolean active, final double value) {
		if (attribute == null) {
			return;
		}

		boolean should = active && value > 0.0;
		boolean has = attribute.hasModifier(id);

		if (should && !has) {
			attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
					id, value, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else if (!should && has) {
			attribute.removeModifier(id);
		}
	}
}
