package com.archetypes;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/**
 * The Cutpurse's event hooks: True Shot empowerment on arrow spawn, the two
 * kill-triggered capstones, and Last Shadow's cheat-death.
 */
public final class AgilityCombat {
	private AgilityCombat() {
	}

	public static void initialize() {
		// A newly-spawned arrow from an armed player picks up its True Shot.
		// ENTITY_LOAD also fires for chunk-loaded and dimension-hopped
		// entities, so only a genuinely fresh arrow (age 0) qualifies.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!(entity instanceof AbstractArrow arrow) || arrow.tickCount > 0
					|| !(arrow.getOwner() instanceof ServerPlayer player)) {
				return;
			}

			MarksmanCombat.onArrowSpawn(player, arrow);

			AttachmentTarget target = (AttachmentTarget) player;

			if (!Boolean.TRUE.equals(target.getAttached(ModAttachments.TRUE_SHOT_ARMED))) {
				return;
			}

			target.removeAttached(ModAttachments.TRUE_SHOT_ARMED);

			boolean homing = MarksmanNodes.rank(SubTree.MARKSMAN,
					NodePurchases.owned(player, SubTree.MARKSMAN), MarksmanNodes.Family.SEEKER_ARROW) > 0;
			AgilityActives.empower(arrow,
					homing ? Tuning.TRUE_SHOT_HOMING_MULTIPLIER : Tuning.TRUE_SHOT_MULTIPLIER, homing);

			// The Seeker Arrow aims itself: whatever the player was pointing
			// at, the shot leaves toward the nearest visible hostile. Flight
			// homing (the arrow mixin) does the rest; non-hostiles are ghosts
			// to it — canHitEntity waves them through.
			if (homing) {
				LivingEntity quarry = null;
				double best = Double.MAX_VALUE;

				for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
						player.getBoundingBox().inflate(Tuning.SEEKER_AIM_RANGE),
						living -> living instanceof Enemy && living.isAlive()
								&& !living.isSpectator() && player.hasLineOfSight(living))) {
					double distance = candidate.distanceToSqr(player);

					if (distance < best) {
						best = distance;
						quarry = candidate;
					}
				}

				if (quarry != null) {
					double speed = arrow.getDeltaMovement().length();
					arrow.setDeltaMovement(quarry.getBoundingBox().getCenter()
							.subtract(arrow.position()).normalize().scale(speed));
					arrow.hurtMarked = true;
				}
			}
		});

		// Kills feed two capstones: Predator refreshes a running invisibility,
		// Momentum hands Shadow Step straight back.
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (!(source.getEntity() instanceof ServerPlayer player)) {
				return;
			}

			AttachmentTarget target = (AttachmentTarget) player;

			if (source.getDirectEntity() instanceof AbstractArrow arrow) {
				MarksmanCombat.onArrowKill(player, arrow);

				// Night's Gift: any bow or crossbow kill lights the dark.
				if (MarksmanCombat.fromMarksmanWeapon(arrow)
						&& MarksmanNodes.rank(SubTree.MARKSMAN, NodePurchases.owned(player, SubTree.MARKSMAN),
								MarksmanNodes.Family.NIGHT_VISION) > 0) {
					player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
							Tuning.NIGHT_VISION_TICKS));
				}
			}

			if (player.hasEffect(MobEffects.INVISIBILITY)
					&& PlaceholderNodes.owns(SubTree.SHADOW,
							NodePurchases.owned(player, SubTree.SHADOW), PlaceholderNodes.Kind.CAPSTONE_A)) {
				player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, Tuning.INVIS_TICKS));
			}

			if (PlaceholderNodes.owns(SubTree.ASSASSIN,
					NodePurchases.owned(player, SubTree.ASSASSIN), PlaceholderNodes.Kind.CAPSTONE_B)) {
				target.removeAttached(ModAttachments.SHADOW_STEP_READY_AT);
			}
		});

		// Last Shadow: the death that wasn't. Cleanse, two seconds of grace
		// (see the hurtServer mixin), vanish — then both this and the invis
		// active share one long cooldown.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return true;
			}

			AttachmentTarget target = (AttachmentTarget) player;
			long now = player.level().getGameTime();
			Long ready = target.getAttached(ModAttachments.CHEAT_DEATH_READY_AT);

			if ((ready != null && now < ready)
					|| !PlaceholderNodes.owns(SubTree.SHADOW,
							NodePurchases.owned(player, SubTree.SHADOW), PlaceholderNodes.Kind.CAPSTONE_B)) {
				return true;
			}

			player.setHealth(1.0F);

			List<Holder<MobEffect>> harmful = new ArrayList<>();

			for (var active : player.getActiveEffectsMap().keySet()) {
				if (active.value().getCategory() == MobEffectCategory.HARMFUL) {
					harmful.add(active);
				}
			}

			harmful.forEach(player::removeEffect);

			target.setAttached(ModAttachments.IMMUNE_UNTIL, now + Tuning.CHEAT_DEATH_IMMUNE_TICKS);
			player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, Tuning.INVIS_TICKS));
			target.setAttached(ModAttachments.INVIS_READY_AT, now + Tuning.CHEAT_DEATH_COOLDOWN_TICKS);
			target.setAttached(ModAttachments.CHEAT_DEATH_READY_AT, now + Tuning.CHEAT_DEATH_COOLDOWN_TICKS);

			ServerLevel level = (ServerLevel) player.level();
			level.sendParticles(ParticleTypes.LARGE_SMOKE,
					player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.7, 0.4, 0.05);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.4F);
			return false;
		});
	}
}
