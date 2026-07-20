package com.archetypes;

import java.util.Set;

import com.archetypes.mixin.AbstractArrowAccessor;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Marksman's passives, at their two natural moments: the arrow leaving
 * (spawn-time shaping) and the arrow landing (on-hit effects). The on-hit
 * batch is called from the damage-shaping mixin, NOT from AFTER_DAMAGE —
 * same lesson as the Crusher: Fabric's after-damage event never fires on
 * killing blows, and Combustion on a kill-shot is the whole fantasy.
 */
public final class MarksmanCombat {
	/** True while Combustion's blast is being dealt, so its own hits can
	 * never chain a second blast. */
	private static boolean detonating;

	private MarksmanCombat() {
	}

	/** Whether this arrow left a weapon the Marksman tree cares about. */
	public static boolean fromMarksmanWeapon(final AbstractArrow arrow) {
		ItemStack weapon = arrow.getWeaponItem();
		return weapon != null && (weapon.is(Items.BOW) || weapon.is(Items.CROSSBOW));
	}

	/** Spawn-time passives: Swift Flight and Conservation. */
	public static void onArrowSpawn(final ServerPlayer player, final AbstractArrow arrow) {
		if (!fromMarksmanWeapon(arrow)) {
			return;
		}

		// Deadeye goes FIRST, and the order is load-bearing: it normalises an
		// underdrawn arrow up to full-draw speed, and Swift Flight's multiplier
		// then rides whatever it finds. Reversed, the normalisation would stomp
		// Swift Flight's speed-up back down to 3.0.
		Deadeye.onArrowSpawn(player, arrow);

		Set<Integer> owned = NodePurchases.owned(player, SubTree.MARKSMAN);

		// Swift Flight, damage-neutral: arrow damage scales with impact speed,
		// so the base is divided back down by exactly the speed-up.
		int swift = MarksmanNodes.rank(SubTree.MARKSMAN, owned, MarksmanNodes.Family.SWIFT_FLIGHT);

		if (swift > 0) {
			float factor = 1.0F + Tuning.SWIFT_FLIGHT_PER_RANK * swift;
			arrow.setDeltaMovement(arrow.getDeltaMovement().scale(factor));
			arrow.hurtMarked = true;
			AbstractArrowAccessor accessor = (AbstractArrowAccessor) arrow;
			accessor.archetypes$setBaseDamage(accessor.archetypes$getBaseDamage() / factor);
		}

		// Conservation: the shot flies, but the quiver doesn't notice. The
		// flying arrow is downgraded to creative-only pickup so the refunded
		// one can't be collected twice.
		int conservation = MarksmanNodes.rank(SubTree.MARKSMAN, owned, MarksmanNodes.Family.CONSERVATION);

		if (conservation > 0 && arrow.pickup == AbstractArrow.Pickup.ALLOWED
				&& player.getRandom().nextFloat() < Tuning.CONSERVATION_PER_RANK * conservation) {
			ItemStack refund = ((AbstractArrowAccessor) arrow).archetypes$getPickupItemStack().copy();
			refund.setCount(1);
			arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;

			if (!player.getInventory().add(refund)) {
				player.drop(refund, false);
			}
		}
	}

	/**
	 * On-hit passives, pre-death: Piercing Tips' armor bypass (returned as
	 * shaped damage), Pinning's slow, Focus's cooldown refund, and
	 * Combustion's blast on burning targets. {@code amount} is what the
	 * victim is about to take; the blast copies it.
	 */
	public static float onArrowHit(final ServerPlayer player, final LivingEntity victim,
			final ServerLevel level, final AbstractArrow arrow, final float amount) {
		if (detonating) {
			return amount;
		}

		Set<Integer> owned = NodePurchases.owned(player, SubTree.MARKSMAN);
		// The epic tree's own shaping — Long Shot, Siege and Punch Through —
		// rides the same returned value, so the two trees' numbers compose on
		// one figure instead of two hooks racing on the same argument.
		float result = Deadeye.shapeArrowHit(player, victim, arrow, amount);
		Deadeye.onArrowHit(player);

		// Piercing Tips: compensate what two armor points would have eaten
		// (~4% each, the Sunder trick), so the shot lands as if they weren't
		// there. No effect on the unarmored.
		if (MarksmanNodes.rank(SubTree.MARKSMAN, owned, MarksmanNodes.Family.PIERCING_TIPS) > 0) {
			float armor = (float) victim.getAttributeValue(
					net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
			result += amount * 0.04F * Math.min(Tuning.PIERCING_TIPS_ARMOR, armor);
		}

		int pinning = MarksmanNodes.rank(SubTree.MARKSMAN, owned, MarksmanNodes.Family.PINNING);

		if (pinning > 0) {
			victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, Tuning.PINNING_TICKS, pinning - 1));
		}

		if (MarksmanNodes.rank(SubTree.MARKSMAN, owned, MarksmanNodes.Family.FOCUS) > 0) {
			AttachmentTarget target = (AttachmentTarget) player;
			Long readyAt = target.getAttached(ModAttachments.TRUE_SHOT_READY_AT);
			long now = level.getGameTime();

			if (readyAt != null && readyAt > now) {
				target.setAttached(ModAttachments.TRUE_SHOT_READY_AT,
						Math.max(now, readyAt - Tuning.FOCUS_REFUND_TICKS));
			}
		}

		if (MarksmanNodes.rank(SubTree.MARKSMAN, owned, MarksmanNodes.Family.COMBUSTION) > 0
				&& victim.isOnFire()) {
			detonating = true;
			try {
				for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class,
						victim.getBoundingBox().inflate(Tuning.COMBUSTION_RADIUS),
						living -> living != player && living != victim && living.isAlive()
								&& !living.isSpectator())) {
					other.hurtServer(level, player.damageSources().playerAttack(player), result);
				}
			} finally {
				detonating = false;
			}

			level.sendParticles(ParticleTypes.EXPLOSION,
					victim.getX(), victim.getY(0.5), victim.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
					SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
		}

		return result;
	}

	/** Rapid Reload's prime: a crossbow kill charges the next reload. */
	public static void onArrowKill(final ServerPlayer player, final AbstractArrow arrow) {
		ItemStack weapon = arrow.getWeaponItem();

		if (weapon == null || !weapon.is(Items.CROSSBOW)) {
			return;
		}

		if (MarksmanNodes.rank(SubTree.MARKSMAN, NodePurchases.owned(player, SubTree.MARKSMAN),
				MarksmanNodes.Family.RAPID_RELOAD) > 0) {
			((AttachmentTarget) player).setAttached(ModAttachments.CROSSBOW_PRIMED, true);
		}
	}
}
