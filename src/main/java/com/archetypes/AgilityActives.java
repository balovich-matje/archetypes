package com.archetypes;

import java.util.Set;

import com.archetypes.mixin.LivingEntityAccessor;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** The Cutpurse's three actives. Cooldowns and ownership all check server-side. */
public final class AgilityActives {
	private AgilityActives() {
	}

	/**
	 * True Shot: arm the next bow shot — flat trajectory, doubled damage (the
	 * arrow quietly stops existing 64 blocks out, which the tooltip does not
	 * mention). The Snap Shot capstone skips the arming: the arrow leaves NOW,
	 * no draw, at four times base.
	 */
	public static void trueShot(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.MARKSMAN);

		boolean snapShot = MarksmanNodes.rank(SubTree.MARKSMAN, owned, MarksmanNodes.Family.SNAP_SHOT) > 0;
		// The base skill is a bow's; Snap Shot conjures its own shot and
		// serves the crossbow branch too.
		boolean weaponOk = player.getMainHandItem().is(Items.BOW)
				|| (snapShot && player.getMainHandItem().is(Items.CROSSBOW));

		if (MarksmanNodes.rank(SubTree.MARKSMAN, owned, MarksmanNodes.Family.TRUE_SHOT) <= 0
				|| !weaponOk
				|| onCooldown(player, ModAttachments.TRUE_SHOT_READY_AT)) {
			return;
		}

		AttachmentTarget target = (AttachmentTarget) player;
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();

		if (snapShot) {
			ItemStack projectile = player.getProjectile(player.getMainHandItem());

			if (projectile.isEmpty()) {
				return;
			}

			ArrowItem arrowItem = projectile.getItem() instanceof ArrowItem item
					? item : (ArrowItem) Items.ARROW;
			AbstractArrow arrow = arrowItem.createArrow(level, projectile, player, player.getMainHandItem());
			arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
					Tuning.TRUE_SHOT_SNAP_SPEED, 1.0F);
			empower(arrow, Tuning.TRUE_SHOT_SNAP_MULTIPLIER, false);

			if (!player.hasInfiniteMaterials()) {
				projectile.shrink(1);
			}

			level.addFreshEntity(arrow);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.2F);
		} else {
			target.setAttached(ModAttachments.TRUE_SHOT_ARMED, true);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 0.8F, 1.3F);
		}

		boolean seeker = MarksmanNodes.rank(SubTree.MARKSMAN, owned, MarksmanNodes.Family.SEEKER_ARROW) > 0;
		target.setAttached(ModAttachments.TRUE_SHOT_READY_AT, now
				+ (seeker ? Tuning.TRUE_SHOT_SEEKER_COOLDOWN_TICKS : Tuning.TRUE_SHOT_COOLDOWN_TICKS));
	}

	/** Applied to an armed player's arrow the moment it enters the world. */
	public static void empower(final AbstractArrow arrow, final float multiplier, final boolean homing) {
		AttachmentTarget target = (AttachmentTarget) arrow;

		arrow.setNoGravity(true);
		((com.archetypes.mixin.AbstractArrowAccessor) arrow).archetypes$setBaseDamage(
				((com.archetypes.mixin.AbstractArrowAccessor) arrow).archetypes$getBaseDamage() * multiplier);
		target.setAttached(ModAttachments.TRUE_SHOT_ORIGIN, arrow.position());

		if (homing) {
			target.setAttached(ModAttachments.TRUE_SHOT_HOMING, true);
		}
	}

	/** Invisibility: eight seconds of the vanilla effect on a half-minute clock. */
	public static void invisibility(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.SHADOW);

		if (!PlaceholderNodes.owns(SubTree.SHADOW, owned, PlaceholderNodes.Kind.ACTIVE)
				|| onCooldown(player, ModAttachments.INVIS_READY_AT)) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, Tuning.INVIS_TICKS));
		((AttachmentTarget) player).setAttached(ModAttachments.INVIS_READY_AT,
				level.getGameTime() + Tuning.INVIS_COOLDOWN_TICKS);
		level.sendParticles(ParticleTypes.LARGE_SMOKE,
				player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.3, 0.5, 0.3, 0.01);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.CANDLE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	/**
	 * Shadow Step: blink behind whatever the crosshair rests on within 16
	 * blocks and land one full-strength dagger strike. The Shadow Flurry
	 * capstone turns the one strike into four (the ticker delivers the rest)
	 * for a doubled cooldown.
	 */
	public static void shadowStep(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.ASSASSIN);

		if (!PlaceholderNodes.owns(SubTree.ASSASSIN, owned, PlaceholderNodes.Kind.ACTIVE)
				|| !ModItems.isDagger(player.getMainHandItem())
				|| onCooldown(player, ModAttachments.SHADOW_STEP_READY_AT)) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		Vec3 from = player.getEyePosition();
		Vec3 to = from.add(player.getLookAngle().scale(Tuning.SHADOW_STEP_RANGE));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, from, to,
				player.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0),
				entity -> entity instanceof LivingEntity living && living.isAlive()
						&& !living.isSpectator() && living != player, 0.3F);

		if (hit == null || !(hit.getEntity() instanceof LivingEntity victim)) {
			return;
		}

		// Behind means behind THEIR back: step out along the reverse of the
		// victim's facing, with a rise fallback if that spot is inside a wall.
		Vec3 behind = Vec3.directionFromRotation(0.0F, victim.getYRot()).scale(-1.0)
				.normalize().scale(victim.getBbWidth() + 0.75);
		Vec3 dest = victim.position().add(behind);

		if (!level.noCollision(player, player.getBoundingBox()
				.move(dest.subtract(player.position())))) {
			dest = dest.add(0.0, 1.0, 0.0);

			if (!level.noCollision(player, player.getBoundingBox()
					.move(dest.subtract(player.position())))) {
				dest = victim.position();
			}
		}

		level.sendParticles(ParticleTypes.PORTAL,
				player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.6, 0.3, 0.05);

		float yaw = (float) (Math.toDegrees(Math.atan2(
				victim.getZ() - dest.z, victim.getX() - dest.x)) - 90.0);
		player.teleportTo(level, dest.x, dest.y, dest.z, java.util.Set.of(), yaw, player.getXRot(), false);

		level.sendParticles(ParticleTypes.PORTAL,
				dest.x, dest.y + 1.0, dest.z, 20, 0.3, 0.6, 0.3, 0.05);
		level.playSound(null, dest.x, dest.y, dest.z,
				SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.2F);

		strike(player, victim);

		AttachmentTarget target = (AttachmentTarget) player;
		boolean flurry = PlaceholderNodes.owns(SubTree.ASSASSIN, owned, PlaceholderNodes.Kind.CAPSTONE_A);

		if (flurry) {
			target.setAttached(ModAttachments.STEP_STRIKES_LEFT, Tuning.FLURRY_EXTRA_STRIKES);
			target.setAttached(ModAttachments.STEP_TARGET, victim.getId());
		}

		target.setAttached(ModAttachments.SHADOW_STEP_READY_AT, level.getGameTime()
				+ (flurry ? Tuning.SHADOW_STEP_FLURRY_COOLDOWN_TICKS : Tuning.SHADOW_STEP_COOLDOWN_TICKS));
	}

	/**
	 * Disengage: sprint while the bowstring is drawn to spring backwards —
	 * 3 blocks per rank, on a one-second clock so it reads as footwork, not
	 * flight. The draw survives the hop; the aim is yours to recover.
	 */
	public static void disengage(final ServerPlayer player) {
		if (MarksmanNodes.rank(SubTree.MARKSMAN, NodePurchases.owned(player, SubTree.MARKSMAN),
				MarksmanNodes.Family.DISENGAGE) <= 0
				|| !player.isUsingItem() || !player.getUseItem().is(Items.BOW)
				|| onCooldown(player, ModAttachments.DISENGAGE_READY_AT)) {
			return;
		}

		int rank = MarksmanNodes.rank(SubTree.MARKSMAN, NodePurchases.owned(player, SubTree.MARKSMAN),
				MarksmanNodes.Family.DISENGAGE);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 look = player.getLookAngle();
		Vec3 back = new Vec3(-look.x, 0.0, -look.z);

		if (back.lengthSqr() < 1.0E-4) {
			return;
		}

		double impulse = rank * Tuning.DISENGAGE_BLOCKS_PER_RANK * Tuning.RUSH_IMPULSE_PER_BLOCK;
		player.setDeltaMovement(player.getDeltaMovement()
				.add(back.normalize().scale(impulse).add(0.0, 0.25, 0.0)));
		player.hurtMarked = true;
		((AttachmentTarget) player).setAttached(ModAttachments.DISENGAGE_READY_AT,
				level.getGameTime() + Tuning.DISENGAGE_COOLDOWN_TICKS);
		level.sendParticles(ParticleTypes.CLOUD,
				player.getX(), player.getY() + 0.1, player.getZ(), 5, 0.2, 0.02, 0.2, 0.01);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.RABBIT_JUMP, SoundSource.PLAYERS, 1.0F, 0.7F);
	}

	/** One authentic full-charge attack: enchants, crits and all. */
	public static void strike(final ServerPlayer player, final LivingEntity victim) {
		((LivingEntityAccessor) player).archetypes$setAttackStrengthTicker(1000);
		player.attack(victim);
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
	}

	private static boolean onCooldown(final ServerPlayer player,
			final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Long> readyAt) {
		Long ready = ((AttachmentTarget) player).getAttached(readyAt);
		return ready != null && player.level().getGameTime() < ready;
	}
}
