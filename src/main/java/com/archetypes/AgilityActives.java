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
			empower(arrow, nightFactor(player) * Tuning.TRUE_SHOT_SNAP_MULTIPLIER, false);

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

	/**
	 * True Shot's night-form multiplier — x1.5 while transformed (the shot is
	 * called Heart-piercing Shot then), x1 otherwise. Folded into the arrow's
	 * multiplier rather than applied on hit, so both the armed shot and Snap
	 * Shot's conjured one pick it up from the one place damage is set.
	 */
	public static float nightFactor(final ServerPlayer player) {
		return NightForm.isActive(player) ? Tuning.NIGHT_FORM_TRUE_SHOT_FACTOR : 1.0F;
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

	/** Invisibility: eight seconds of the vanilla effect on a half-minute
	 * clock — longer with Umbral Mastery, cleaner with Cleansing Veil. */
	public static void invisibility(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.SHADOW);

		if (ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.INVISIBILITY) <= 0
				|| onCooldown(player, ModAttachments.INVIS_READY_AT)) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();

		if (ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.CLEANSING_VEIL) > 0) {
			ShadowTicker.cleanse(player);
		}

		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ShadowTicker.invisDuration(player)));
		((AttachmentTarget) player).setAttached(ModAttachments.INVIS_READY_AT,
				level.getGameTime() + Tuning.INVIS_COOLDOWN_TICKS);
		level.sendParticles(ParticleTypes.LARGE_SMOKE,
				player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.3, 0.5, 0.3, 0.01);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.CANDLE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	/**
	 * Shadow Step: blink behind whatever the crosshair rests on within 16
	 * blocks and land one full-strength dagger strike. Shadow Flurry lands
	 * it with three daggers' weight for a doubled cooldown, and Twin Fangs
	 * folds the off-hand dagger into the blow — both applied in the damage
	 * shaping, not here.
	 */
	public static void shadowStep(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.ASSASSIN);

		if (AssassinNodes.rank(SubTree.ASSASSIN, owned, AssassinNodes.Family.SHADOW_STEP) <= 0
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

		// Arm the cooldown BEFORE the strike. If the strike is a kill, its
		// death event fires synchronously inside attack() and Momentum wipes
		// the cooldown there — so this write has to happen first, or a
		// one-shot re-arms the very cooldown Momentum just cleared (user bug).
		boolean flurry = AssassinNodes.rank(SubTree.ASSASSIN, owned, AssassinNodes.Family.SHADOW_FLURRY) > 0;
		((AttachmentTarget) player).setAttached(ModAttachments.SHADOW_STEP_READY_AT, level.getGameTime()
				+ (flurry ? Tuning.SHADOW_STEP_FLURRY_COOLDOWN_TICKS : Tuning.SHADOW_STEP_COOLDOWN_TICKS));

		strike(player, victim);
	}

	/**
	 * Acrobatics: sprint while the bowstring is drawn to roll forward — 2
	 * blocks per rank, kept low so it reads as a tumble, not a lunge. The
	 * draw survives the roll; the aim is yours to recover.
	 */
	public static void acrobatics(final ServerPlayer player) {
		int rank = MarksmanNodes.rank(SubTree.MARKSMAN, NodePurchases.owned(player, SubTree.MARKSMAN),
				MarksmanNodes.Family.ACROBATICS);

		if (rank <= 0 || !player.isUsingItem() || !player.getUseItem().is(Items.BOW)
				|| onCooldown(player, ModAttachments.DISENGAGE_READY_AT)) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		Vec3 look = player.getLookAngle();
		Vec3 forward = new Vec3(look.x, 0.0, look.z);

		if (forward.lengthSqr() < 1.0E-4) {
			return;
		}

		double impulse = rank * Tuning.ACROBATICS_BLOCKS_PER_RANK * Tuning.RUSH_IMPULSE_PER_BLOCK;
		player.setDeltaMovement(player.getDeltaMovement()
				.add(forward.normalize().scale(impulse).add(0.0, 0.15, 0.0)));
		player.hurtMarked = true;
		((AttachmentTarget) player).setAttached(ModAttachments.DISENGAGE_READY_AT,
				level.getGameTime() + Tuning.DISENGAGE_COOLDOWN_TICKS);
		level.sendParticles(ParticleTypes.CLOUD,
				player.getX(), player.getY() + 0.1, player.getZ(), 5, 0.2, 0.02, 0.2, 0.01);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.RABBIT_JUMP, SoundSource.PLAYERS, 1.0F, 0.7F);
	}

	/** One authentic full-charge attack: enchants, crits and all. The stamp
	 * lets Deathblow's shaping recognise it mid-pipeline. */
	public static void strike(final ServerPlayer player, final LivingEntity victim) {
		((LivingEntityAccessor) player).archetypes$setAttackStrengthTicker(1000);
		((AttachmentTarget) player).setAttached(ModAttachments.STEP_STRIKE_AT,
				player.level().getGameTime());
		player.attack(victim);
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
	}

	private static boolean onCooldown(final ServerPlayer player,
			final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Long> readyAt) {
		Long ready = ((AttachmentTarget) player).getAttached(readyAt);
		return ready != null && player.level().getGameTime() < ready;
	}
}
