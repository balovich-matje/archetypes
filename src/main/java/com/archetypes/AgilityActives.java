package com.archetypes;

import java.util.Set;

import com.archetypes.mixin.LivingEntityAccessor;

import com.archetypes.platform.ArchetypeStore;
import com.archetypes.state.StateKey;

import net.minecraft.world.entity.Entity;
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
//? if >=1.21.11 {
import org.jspecify.annotations.Nullable;
//?} else {
/*import org.jetbrains.annotations.Nullable;
*///?}

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
				|| onCooldown(player, ModState.TRUE_SHOT_READY_AT)) {
			return;
		}

		final Entity target = player;
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
			markTrueShot(arrow);

			if (!player.hasInfiniteMaterials()) {
				projectile.shrink(1);
			}

			level.addFreshEntity(arrow);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.2F);
		} else {
			ArchetypeStore.INSTANCE.set(target, ModState.TRUE_SHOT_ARMED, true);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 0.8F, 1.3F);
		}

		boolean seeker = MarksmanNodes.rank(SubTree.MARKSMAN, owned, MarksmanNodes.Family.SEEKER_ARROW) > 0;
		ArchetypeStore.INSTANCE.set(target, ModState.TRUE_SHOT_READY_AT, now
				+ (seeker ? Tuning.TRUE_SHOT_SEEKER_COOLDOWN_TICKS : Tuning.TRUE_SHOT_COOLDOWN_TICKS));
	}

	/** Applied to an armed player's arrow the moment it enters the world. */
	public static void empower(final AbstractArrow arrow, final float multiplier, final boolean homing) {
		final Entity target = arrow;

		arrow.setNoGravity(true);
		((com.archetypes.mixin.AbstractArrowAccessor) arrow).archetypes$setBaseDamage(
				((com.archetypes.mixin.AbstractArrowAccessor) arrow).archetypes$getBaseDamage() * multiplier);
		ArchetypeStore.INSTANCE.set(target, ModState.TRUE_SHOT_ORIGIN, arrow.position());

		if (homing) {
			ArchetypeStore.INSTANCE.set(target, ModState.TRUE_SHOT_HOMING, true);
		}
	}

	/**
	 * Mark an arrow as the base tree's one big shot. Deadeye also calls
	 * {@link #empower} (for the flat flight and the origin stamp), so the mark
	 * cannot live in there — and the Nemesis Marksman multipliers refuse a
	 * marked arrow, which is what keeps Snap Shot x Long Shot x Siege off the
	 * same projectile.
	 */
	public static void markTrueShot(final AbstractArrow arrow) {
		ArchetypeStore.INSTANCE.set(arrow, ModState.TRUE_SHOT_ARROW, true);
	}

	/** Invisibility: eight seconds of the vanilla effect on a half-minute
	 * clock — longer with Umbral Mastery, cleaner with Cleansing Veil. */
	public static void invisibility(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.SHADOW);

		if (ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.INVISIBILITY) <= 0
				|| onCooldown(player, ModState.INVIS_READY_AT)) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();

		if (ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.CLEANSING_VEIL) > 0) {
			ShadowTicker.cleanse(player);
		}

		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ShadowTicker.invisDuration(player)));
		ArchetypeStore.INSTANCE.set(player, ModState.INVIS_READY_AT,
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
				|| onCooldown(player, ModState.SHADOW_STEP_READY_AT)) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		LivingEntity victim = markedVictim(player);

		if (victim == null) {
			Vec3 from = player.getEyePosition();
			Vec3 to = from.add(player.getLookAngle().scale(Tuning.SHADOW_STEP_RANGE));
			EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, from, to,
					player.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0),
					entity -> entity instanceof LivingEntity living && living.isAlive()
							&& !living.isSpectator() && living != player, 0.3F);

			if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) {
				return;
			}

			victim = target;
		}

		// Behind means behind THEIR back: step out along the reverse of the
		// victim's facing, with a rise fallback if that spot is inside a wall.
		Vec3 behind = Vec3.directionFromRotation(0.0F, victim.getYRot()).scale(-1.0)
				.normalize().scale(victim.getBbWidth() + 0.75);
		Vec3 dest = victim.position().add(behind);

		if (!level.noCollision(player, player.getBoundingBox()
				.move(dest.subtract(player.position())))) {
			dest = dest.add(0.0, 1.0, 0.0);

			// Third probe, for the marked jump: 32 blocks lands inside a build
			// far more often than 16 did, and stepping back along the approach
			// is the one spot the assassin is known to have come through.
			if (!level.noCollision(player, player.getBoundingBox()
					.move(dest.subtract(player.position())))) {
				Vec3 approach = victim.position().subtract(player.position());
				dest = approach.horizontalDistanceSqr() < 1.0E-4 ? victim.position()
						: victim.position().subtract(new Vec3(approach.x, 0.0, approach.z)
								.normalize().scale(victim.getBbWidth() + 0.75));
			}

			if (!level.noCollision(player, player.getBoundingBox()
					.move(dest.subtract(player.position())))) {
				dest = victim.position();
			}
		}

		level.sendParticles(ParticleTypes.PORTAL,
				player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.6, 0.3, 0.05);

		float yaw = (float) (Math.toDegrees(Math.atan2(
				victim.getZ() - dest.z, victim.getX() - dest.x)) - 90.0);
		// 1.21.11 added the trailing `setCamera` flag and renamed `RelativeMovement` to
		// `Relative`; the relative set is EMPTY at both call sites, so the type name never
		// has to be written and only the flag forks. `false` is the legacy behaviour.
		//? if >=1.21.11 {
		player.teleportTo(level, dest.x, dest.y, dest.z, java.util.Set.of(), yaw, player.getXRot(), false);
		//?} else {
		/*player.teleportTo(level, dest.x, dest.y, dest.z, java.util.Set.of(), yaw, player.getXRot());
		*///?}

		level.sendParticles(ParticleTypes.PORTAL,
				dest.x, dest.y + 1.0, dest.z, 20, 0.3, 0.6, 0.3, 0.05);
		level.playSound(null, dest.x, dest.y, dest.z,
				SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.2F);

		// Arm the cooldown BEFORE the strike. If the strike is a kill, its
		// death event fires synchronously inside attack() and Momentum wipes
		// the cooldown there — so this write has to happen first, or a
		// one-shot re-arms the very cooldown Momentum just cleared (user bug).
		boolean flurry = AssassinNodes.rank(SubTree.ASSASSIN, owned, AssassinNodes.Family.SHADOW_FLURRY) > 0;
		ArchetypeStore.INSTANCE.set(player, ModState.SHADOW_STEP_READY_AT, level.getGameTime()
				+ (flurry ? Tuning.SHADOW_STEP_FLURRY_COOLDOWN_TICKS : Tuning.SHADOW_STEP_COOLDOWN_TICKS));

		strike(player, victim);
	}

	/**
	 * Death Mark's retarget: with a live mark inside 32 blocks, that body IS
	 * the Shadow Step's victim — no crosshair, no line of sight, twice the
	 * range. Null when there is no mark or it has walked out of range, and the
	 * step falls back to today's raycast.
	 */
	private static @Nullable LivingEntity markedVictim(
			final ServerPlayer player) {
		LivingEntity mark = DeathMark.target(player);

		return mark != null && mark.level() == player.level()
				&& mark.distanceToSqr(player) <= Tuning.DEATH_MARK_RANGE * Tuning.DEATH_MARK_RANGE
						? mark : null;
	}

	/**
	 * Acrobatics: sprint while the bowstring is drawn to roll forward — 2
	 * blocks per rank, kept low so it reads as a tumble, not a lunge. The
	 * draw survives the roll; the aim is yours to recover.
	 */
	public static void acrobatics(final ServerPlayer player) {
		int rank = MarksmanNodes.rank(SubTree.MARKSMAN, NodePurchases.owned(player, SubTree.MARKSMAN),
				MarksmanNodes.Family.ACROBATICS);
		boolean vault = NemesisMarksmanNodes.rank(player, NemesisMarksmanNodes.Family.VAULT) > 0;

		if (rank <= 0 || !readyToRoll(player, vault)
				|| onCooldown(player, ModState.DISENGAGE_READY_AT)) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		Vec3 look = player.getLookAngle();
		Vec3 forward = new Vec3(look.x, 0.0, look.z);

		if (forward.lengthSqr() < 1.0E-4) {
			return;
		}

		// Vault replaces the per-rank distance with a flat eight blocks and the
		// eight-second clock with three.
		double blocks = vault ? Tuning.VAULT_BLOCKS : rank * Tuning.ACROBATICS_BLOCKS_PER_RANK;
		double impulse = blocks * Tuning.RUSH_IMPULSE_PER_BLOCK;
		player.setDeltaMovement(player.getDeltaMovement()
				.add(forward.normalize().scale(impulse).add(0.0, 0.15, 0.0)));
		player.hurtMarked = true;
		ArchetypeStore.INSTANCE.set(player, ModState.DISENGAGE_READY_AT, level.getGameTime()
				+ (vault ? Tuning.VAULT_COOLDOWN_TICKS : Tuning.DISENGAGE_COOLDOWN_TICKS));
		level.sendParticles(ParticleTypes.CLOUD,
				player.getX(), player.getY() + 0.1, player.getZ(), vault ? 12 : 5, 0.2, 0.02, 0.2, 0.01);
		// The same cue either way; Vault's is pitched up, because a roll four
		// times as long should not sound like the short one.
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.RABBIT_JUMP, SoundSource.PLAYERS, 1.0F, vault ? 1.2F : 0.7F);
	}

	/**
	 * Whether the weapon is ready enough to roll behind. The base skill wants a
	 * bow actually drawn; Vault also takes a crossbow, and for a crossbow
	 * "drawn" has to mean CHARGED — the draw is a moment, the loaded state is
	 * the aim, and requiring {@code isUsingItem} would make the node
	 * unreachable with the weapon it names. Nothing here tests the ground:
	 * Acrobatics never did, so Vault's mid-air clause costs no code.
	 */
	private static boolean readyToRoll(final ServerPlayer player, final boolean vault) {
		if (player.isUsingItem() && player.getUseItem().is(Items.BOW)) {
			return true;
		}

		if (!vault) {
			return false;
		}

		ItemStack main = player.getMainHandItem();
		return (player.isUsingItem() && player.getUseItem().is(Items.CROSSBOW))
				|| (main.is(Items.CROSSBOW)
						&& net.minecraft.world.item.CrossbowItem.isCharged(main));
	}

	/** One authentic full-charge attack: enchants, crits and all. The stamp
	 * lets Deathblow's shaping recognise it mid-pipeline. */
	public static void strike(final ServerPlayer player, final LivingEntity victim) {
		((LivingEntityAccessor) player).archetypes$setAttackStrengthTicker(1000);
		ArchetypeStore.INSTANCE.set(player, ModState.STEP_STRIKE_AT,
				player.level().getGameTime());

		// The step is not a fall. A teleport carries fallDistance and onGround
		// across untouched (Entity.teleportSetPosition writes position, rotation
		// and delta movement and nothing else), so jumping before the key and
		// arriving mid-air handed the strike vanilla's x1.5 crit on top of the
		// whole ambush bucket — enough to kill a full-netherite Protection IV
		// player the bucket is tuned to leave standing. Clearing it here rather
		// than reconstructing canCriticalAttack's private predicate inside the
		// damage shaper keeps one number in one place.
		player.resetFallDistance();
		player.attack(victim);
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
	}

	private static boolean onCooldown(final ServerPlayer player, final StateKey<Long> readyAt) {
		Long ready = ArchetypeStore.INSTANCE.get(player, readyAt);
		return ready != null && player.level().getGameTime() < ready;
	}
}
