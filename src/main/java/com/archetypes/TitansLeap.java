package com.archetypes;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Titan's Leap — the Colossus Crusher's epic active, ability slot 6 for a
 * Brawler, and the two landings that read off it. Slot 6 is shared with the
 * Cutpurse's Dark Ritual; the dispatch picks on archetype, so the two never
 * collide.
 *
 * <h2>The state machine</h2>
 * One flight at a time, held as two server-side stamps: {@code LEAP_AT} (the
 * tick the player left the ground — presence IS "in the air on our account")
 * and {@code LEAP_PEAK_Y} (the highest Y reached so far). {@link #leap} sets
 * them, {@link #tick} raises the peak and watches for the ground, {@link #land}
 * spends them, and {@link #clear} drops them for a respec or a relog.
 *
 * <p>Nothing about the flight is synced. The only client-visible half is the
 * cooldown ({@code LEAP_READY_AT}, target-only, which the cooldown bar reads)
 * and the particles and sounds this class sends.
 *
 * <h2>Why the fall is measured, not asked for</h2>
 * Aftershock pays per block fallen, and by the time an END_SERVER_TICK listener
 * sees {@code onGround()} vanilla has already zeroed {@code fallDistance} in
 * {@code Entity.checkFallDamage} — the same trap {@code SMASH_AT} exists to
 * dodge for the base tree's Meteor. So the flight tracks its own peak Y and the
 * landing subtracts. That also keeps the leap from disarming the systems it
 * exists to feed: {@code fallDistance} is never reset, only waived at the damage
 * hook ({@code LivingEntityMixin#archetypes$titansLeapFall}), so Meteor,
 * Shockwave and vanilla's own mace smash all still see the full drop.
 */
public final class TitansLeap {
	private TitansLeap() {
	}

	// ------------------------------------------------------------------
	// Predicates. Server-side in practice — neither stamp is synced.
	// ------------------------------------------------------------------

	/** Whether this player is in the air on a leap of ours. */
	public static boolean isLeaping(final Player player) {
		return ((AttachmentTarget) player).getAttached(ModAttachments.LEAP_AT) != null;
	}

	/** Owned rank in a Colossus Crusher family. */
	public static int rank(final Player player, final ColossusCrusherNodes.Family family) {
		return ColossusCrusherNodes.rank(player, family);
	}

	// ------------------------------------------------------------------
	// The press.
	// ------------------------------------------------------------------

	/**
	 * Ability slot 6 for a Brawler: up and forward, with the mace or bare
	 * fists in hand — the same weapon gate every Crusher active uses. A press
	 * made while a leap is already in the air is ignored; the cooldown outlasts
	 * any flight, so that only matters for a leap that ended somewhere the
	 * landing test cannot see.
	 */
	public static void leap(final ServerPlayer player) {
		if (rank(player, ColossusCrusherNodes.Family.TITAN_LEAP) <= 0) {
			return;
		}

		WeaponClass weapon = WeaponClass.of(player);

		if (weapon != WeaponClass.MACE && weapon != WeaponClass.HANDS) {
			return;
		}

		AttachmentTarget target = (AttachmentTarget) player;
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		Long ready = target.getAttached(ModAttachments.LEAP_READY_AT);

		if ((ready != null && now < ready) || isLeaping(player)) {
			return;
		}

		// Flat look: the leap's height is fixed and its reach is a direction,
		// so aiming at your feet must not shorten the jump.
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		Vec3 forward = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO
				: flat.normalize().scale(Tuning.TITAN_LEAP_FORWARD_IMPULSE);

		// Y is SET, not added: a leap taken out of a jump is still one leap.
		Vec3 movement = player.getDeltaMovement();
		player.setDeltaMovement(movement.x + forward.x, Tuning.TITAN_LEAP_UP_IMPULSE,
				movement.z + forward.z);
		player.hurtMarked = true;

		target.setAttached(ModAttachments.LEAP_READY_AT, now + Tuning.TITAN_LEAP_COOLDOWN_TICKS);
		target.setAttached(ModAttachments.LEAP_AT, now);
		target.setAttached(ModAttachments.LEAP_PEAK_Y, player.getY());

		// The ground gives way where you pushed off, and the mace's own air
		// note pitched down to half.
		var ground = player.getBlockStateOn();

		if (!ground.isAir()) {
			for (int i = 0; i < 24; i++) {
				double angle = Math.PI * 2.0 * i / 24.0;
				level.sendParticles(
						new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, ground),
						player.getX() + Math.cos(angle) * 1.2,
						player.getY() + 0.1,
						player.getZ() + Math.sin(angle) * 1.2,
						2, 0.1, 0.2, 0.1, 0.08);
			}
		}

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 1.2F, 0.5F);
	}

	/** Drop an in-flight leap without landing it. Reached by the JOIN handler
	 * and by {@code ModAttachments.forgetNodes}; safe on a player in no leap. */
	public static void clear(final ServerPlayer player) {
		AttachmentTarget target = (AttachmentTarget) player;
		target.removeAttached(ModAttachments.LEAP_AT);
		target.removeAttached(ModAttachments.LEAP_PEAK_Y);
	}

	// ------------------------------------------------------------------
	// The flight. Driven by CrusherTicker.
	// ------------------------------------------------------------------

	static void tick(final ServerPlayer player) {
		AttachmentTarget target = (AttachmentTarget) player;
		Long since = target.getAttached(ModAttachments.LEAP_AT);

		if (since == null) {
			return;
		}

		if (!player.isAlive() || rank(player, ColossusCrusherNodes.Family.TITAN_LEAP) <= 0) {
			clear(player);
			return;
		}

		Double peak = target.getAttached(ModAttachments.LEAP_PEAK_Y);

		if (peak == null || player.getY() > peak) {
			target.setAttached(ModAttachments.LEAP_PEAK_Y, player.getY());
			peak = player.getY();
		}

		long now = player.level().getGameTime();

		if (now - since < Tuning.TITAN_LEAP_LAUNCH_GRACE_TICKS) {
			return;
		}

		// A leap that never comes down anywhere this test can see it — landed
		// on a boat, took off flying, got carried — must not leave the
		// fall-damage waiver standing.
		if (now - since > Tuning.TITAN_LEAP_MAX_FLIGHT_TICKS) {
			clear(player);
			return;
		}

		// Water and lava end the leap the way ground does, but nothing lands:
		// a slam needs something to hit.
		if (player.isInWater() || player.isInLava()) {
			clear(player);
			return;
		}

		if (player.onGround()) {
			land(player, (ServerLevel) player.level(), (float) Math.max(0.0, peak - player.getY()));
			clear(player);
		}
	}

	/**
	 * The landing. Both branches read the same fall and neither can fire
	 * without its node, so a root-only Colossus simply comes down.
	 *
	 * <p>Aftershock first, then Gravity Well: a hybrid who bought both gets the
	 * damage resolved against where the crowd stood, not against where the pull
	 * put them.
	 */
	private static void land(final ServerPlayer player, final ServerLevel level, final float fell) {
		if (rank(player, ColossusCrusherNodes.Family.AFTERSHOCK) > 0) {
			aftershock(player, level, fell);
		}

		if (rank(player, ColossusCrusherNodes.Family.GRAVITY_WELL) > 0) {
			gravityWell(player, level);
		}
	}

	/**
	 * Aftershock: the landing that hits. Mace in hand — the branch's whole
	 * premise, and the reason the tooltip says so (a fists Colossus spends the
	 * leap on engaging instead).
	 */
	private static void aftershock(final ServerPlayer player, final ServerLevel level,
			final float fell) {
		if (WeaponClass.of(player) != WeaponClass.MACE) {
			return;
		}

		int rank = rank(player, ColossusCrusherNodes.Family.AFTERSHOCK);
		double radius = Tuning.AFTERSHOCK_RADIUS_BASE + Tuning.AFTERSHOCK_RADIUS_PER_RANK * rank;
		float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE)
				* Tuning.AFTERSHOCK_DAMAGE_MULTIPLIER)
				+ Math.min(fell, Tuning.AFTERSHOCK_MAX_FALL)
						* Tuning.AFTERSHOCK_PER_BLOCK_PER_RANK * rank;

		CrusherActives.slam(player, level, radius, damage, Tuning.AFTERSHOCK_LAUNCH);
		CrusherActives.slamFx(player, level, radius);
		ProcIndicators.send(player, SubTree.COLOSSUS_CRUSHER,
				ColossusCrusherNodes.Family.AFTERSHOCK);
	}

	/**
	 * Gravity Well: the landing that holds. No damage and no launch by design —
	 * this is the Anchor branch's whole brief, the way Ghost Form is the
	 * Shadow's.
	 *
	 * <p>The pull is capped ({@link Tuning#GRAVITY_WELL_MAX_PULL}) and skips
	 * anything already inside {@link Tuning#GRAVITY_WELL_DEAD_ZONE}, so nothing
	 * is fired through the player and out the far side. It works on other
	 * players too: {@code hurtMarked} is what publishes forced motion, and
	 * {@code ServerEntity} sends the motion packet to tracking players AND to
	 * the moved entity itself.
	 */
	private static void gravityWell(final ServerPlayer player, final ServerLevel level) {
		double radiusSq = Tuning.GRAVITY_WELL_RADIUS * Tuning.GRAVITY_WELL_RADIUS;
		Vec3 centre = player.position();

		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(Tuning.GRAVITY_WELL_RADIUS),
				entity -> entity != player && entity.isAlive() && !entity.isSpectator()
						&& entity.distanceToSqr(player) <= radiusSq)) {
			Vec3 toCentre = centre.subtract(victim.position());
			double distance = toCentre.length();

			victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
					Tuning.GRAVITY_WELL_SLOW_TICKS, Tuning.GRAVITY_WELL_SLOW_AMPLIFIER), player);

			if (distance <= Tuning.GRAVITY_WELL_DEAD_ZONE) {
				continue;
			}

			Vec3 pull = toCentre.normalize().scale(Math.min(Tuning.GRAVITY_WELL_MAX_PULL,
					distance * Tuning.GRAVITY_WELL_PULL_PER_BLOCK));
			victim.setDeltaMovement(pull.x, pull.y + 0.1, pull.z);
			victim.hurtMarked = true;

			// The tell: sculk streaming inward along the vector it was dragged
			// down. Drawn at the victim, so a crowd reads as a crowd moving.
			level.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
					victim.getX(), victim.getY(0.5), victim.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
		}

		level.sendParticles(ParticleTypes.PORTAL,
				player.getX(), player.getY() + 0.5, player.getZ(), 60, 0.4, 0.6, 0.4, 1.2);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 0.6F);
		ProcIndicators.send(player, SubTree.COLOSSUS_CRUSHER,
				ColossusCrusherNodes.Family.GRAVITY_WELL);
	}

	// ------------------------------------------------------------------
	// The Anchor branch's two passives, read from the damage funnel.
	// ------------------------------------------------------------------

	/**
	 * Bulwark's condition: the node is owned, Battle Trance is owned, and the
	 * trance is actually holding health right now. Absorption from anywhere
	 * else — a golden apple, the Oracle Wizard's Magic Armor — must not switch
	 * the reduction on, which is what the Battle Trance test is for.
	 */
	public static boolean bulwarkHolding(final Player player) {
		return rank(player, ColossusCrusherNodes.Family.BULWARK) > 0
				&& CrusherNodes.rank(SubTree.CRUSHER, NodePurchases.owned(player, SubTree.CRUSHER),
						CrusherNodes.Family.BATTLE_TRANCE) > 0
				&& player.getAbsorptionAmount() > 0.0F;
	}

	/**
	 * Immovable's anvil, at most once a second. The node is otherwise silent
	 * and invisible, so without this a player has no way to learn that the shove
	 * they did not take was theirs.
	 */
	public static void immovableCue(final ServerPlayer player) {
		AttachmentTarget target = (AttachmentTarget) player;
		long now = player.level().getGameTime();
		Long last = target.getAttached(ModAttachments.IMMOVABLE_CUE_AT);

		if (last != null && now - last < Tuning.IMMOVABLE_CUE_PERIOD_TICKS) {
			return;
		}

		target.setAttached(ModAttachments.IMMOVABLE_CUE_AT, now);
		((ServerLevel) player.level()).playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4F, 0.7F);
		ProcIndicators.send(player, SubTree.COLOSSUS_CRUSHER,
				ColossusCrusherNodes.Family.IMMOVABLE);
	}

	/**
	 * Unstoppable Force's cue, sent from the blocking hook once a raised shield
	 * has been knocked aside. {@code BlocksAttacks.disable} already plays the
	 * shield's own disable note, so this is the mace's half of it.
	 */
	public static void unstoppableCue(final ServerPlayer player, final ServerLevel level,
			final LivingEntity blocker) {
		level.sendParticles(ParticleTypes.CRIT,
				blocker.getX(), blocker.getY(0.8), blocker.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
		level.playSound(null, blocker.getX(), blocker.getY(), blocker.getZ(),
				SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7F, 0.8F);
		ProcIndicators.send(player, SubTree.COLOSSUS_CRUSHER,
				ColossusCrusherNodes.Family.SIEGEBREAKER);
	}
}
