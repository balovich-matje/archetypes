package com.archetypes;

import java.util.List;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * The Slayer's on-hit and on-kill passives, hung off Fabric's entity events.
 * Weapon scoping is the tree's split made literal: sword-only passives check
 * {@link ModItems#isSword}, the shared ones accept the greatsword too.
 */
public final class SlayerCombat {
	/** True while a Blade Dance strike is being dealt, so the dance's own
	 * damage event can never proc another dance. */
	private static boolean dancing;

	private SlayerCombat() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, taken, blocked) -> {
			if (blocked || taken <= 0 || !(source.getDirectEntity() instanceof ServerPlayer player)) {
				return;
			}

			ItemStack weapon = player.getMainHandItem();
			boolean sword = ModItems.isSword(weapon);
			boolean greatsword = ModItems.isGreatsword(weapon);

			if (!sword && !greatsword) {
				return;
			}

			var owned = NodePurchases.owned(player, SubTree.SLAYER);

			// Hamstring: both weapons cripple.
			int slow = SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.SLOWNESS);

			if (slow > 0 && entity.isAlive()) {
				entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, Tuning.SLOWNESS_TICKS, slow - 1),
						player);
			}

			// Rend: sword-only — a wound the greatsword's single blow does not need.
			int bleed = SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.BLEED);

			if (bleed > 0 && sword && entity.isAlive()) {
				SlayerTicker.startBleed(entity, player, bleed);
			}

			// Blade Dance: a manual sword strike may lash out at someone else
			// nearby — any direction. Bladestorm's volleys are excluded; the
			// storm already is that fantasy.
			if (sword && !dancing
					&& SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.BLADE_DANCE) > 0) {
				Long stormEnd = ((AttachmentTarget) player).getAttached(ModAttachments.BLADESTORM_END);

				if ((stormEnd == null || stormEnd <= player.level().getGameTime())
						&& player.getRandom().nextFloat() < Tuning.BLADE_DANCE_CHANCE) {
					bladeDance(player, entity);
				}
			}
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(source.getEntity() instanceof ServerPlayer player)) {
				return;
			}

			ItemStack weapon = player.getMainHandItem();
			boolean sword = ModItems.isSword(weapon);
			boolean greatsword = ModItems.isGreatsword(weapon);

			if (!sword && !greatsword) {
				return;
			}

			var owned = NodePurchases.owned(player, SubTree.SLAYER);

			// Taste of Blood: half a heart per rank on any melee kill.
			int taste = SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.TASTE_OF_BLOOD);

			if (taste > 0) {
				player.heal(taste * Tuning.TASTE_OF_BLOOD_HEAL_PER_RANK);
				((ServerLevel) player.level()).sendParticles(ParticleTypes.HEART,
						player.getX(), player.getY() + 1.5, player.getZ(), taste, 0.3, 0.3, 0.3, 0.0);
			}

			// Flurry: sword kills reset the lunge.
			if (sword && SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.FLURRY) > 0) {
				((AttachmentTarget) player).removeAttached(ModAttachments.LUNGE_READY_AT);
			}

			// Bloodlust: the momentum passive at the very tip of the sword.
			if (SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.BLOODLUST) > 0) {
				player.addEffect(new MobEffectInstance(MobEffects.SPEED, Tuning.BLOODLUST_TICKS, 0));
			}
		});
	}

	/** The dance itself: full attack damage to a random other foe in reach. */
	private static void bladeDance(final ServerPlayer player, final LivingEntity struck) {
		ServerLevel level = (ServerLevel) player.level();
		List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(Tuning.BLADE_DANCE_RANGE, 1.0, Tuning.BLADE_DANCE_RANGE),
				other -> other != player && other != struck && other.isAlive()
						&& !other.isSpectator() && player.hasLineOfSight(other));

		if (nearby.isEmpty()) {
			return;
		}

		LivingEntity target = nearby.get(player.getRandom().nextInt(nearby.size()));
		float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);

		dancing = true;
		try {
			target.hurtServer(level, player.damageSources().playerAttack(player), damage);
		} finally {
			dancing = false;
		}

		level.sendParticles(ParticleTypes.SWEEP_ATTACK,
				target.getX(), target.getY(0.5), target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.3F);
	}
}
