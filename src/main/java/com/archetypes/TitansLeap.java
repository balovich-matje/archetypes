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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Titan's Leap — the Colossus Crusher's epic active, ability slot 6 for a
 * Brawler, and the Aftershock landing that reads off it. Slot 6 is shared with
 * the Cutpurse's Dark Ritual; the dispatch picks on archetype, so the two never
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
 * and the particles and sounds this class sends. The bare-fisted landing's
 * eight seconds ({@code LEAP_STOMP_END}) are unsynced too — the client sees
 * them as the ATTACK_DAMAGE attribute vanilla already syncs.
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
	 * The landing, and the fork the whole tree is drawn around: what is in the
	 * player's hands when they hit the ground decides which one fires. A mace
	 * slams (Aftershock, and only with that node bought); bare fists stomp,
	 * which is the leap's own payload and needs nothing beyond the root. The
	 * two weapon gates are mutually exclusive by construction — {@code MACE} is
	 * a main-hand test and {@code HANDS} demands both hands empty — so a
	 * landing is never both.
	 */
	private static void land(final ServerPlayer player, final ServerLevel level, final float fell) {
		WeaponClass weapon = WeaponClass.of(player);

		if (weapon == WeaponClass.MACE && rank(player, ColossusCrusherNodes.Family.AFTERSHOCK) > 0) {
			aftershock(player, level, fell);
		} else if (weapon == WeaponClass.HANDS) {
			stomp(player, level);
		}
	}

	/**
	 * The bare-fisted landing: everything hostile inside
	 * {@link Tuning#TITAN_LEAP_STOMP_RADIUS} is slowed, and the fists that did
	 * it hit harder for {@link Tuning#TITAN_LEAP_STOMP_TICKS}.
	 *
	 * <p>Two things it deliberately is NOT. It deals no damage — Aftershock is
	 * the column that pays for a landing that hits, and a fists landing that
	 * also hit would have made the left column optional the way a second
	 * capstone key would. And the damage half is not a hook: it writes a stamp
	 * that {@code CrusherTicker} turns into a second ATTACK_DAMAGE modifier
	 * beside Bare-Knuckle's, under that node's own {@code hands} gate, so
	 * picking up a weapon inside the eight seconds simply stops paying and
	 * dropping it again resumes.
	 *
	 * <p>The radius is enforced twice on purpose. {@code inflate} builds a BOX,
	 * and a box that covers a 6-block disc reaches 8.49 at its corners, so the
	 * predicate re-tests real distance — the same clamp the Protector's Shield
	 * Sweep needs and for the same reason.
	 */
	private static void stomp(final ServerPlayer player, final ServerLevel level) {
		double radius = Tuning.TITAN_LEAP_STOMP_RADIUS;
		double reach = radius * radius;

		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(radius),
				entity -> entity != player && entity.isAlive() && !entity.isSpectator()
						&& entity instanceof Enemy)) {
			if (player.distanceToSqr(victim) > reach) {
				continue;
			}

			victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
					Tuning.TITAN_LEAP_STOMP_TICKS, Tuning.TITAN_LEAP_STOMP_SLOW_AMPLIFIER), player);
		}

		((AttachmentTarget) player).setAttached(ModAttachments.LEAP_STOMP_END,
				level.getGameTime() + Tuning.TITAN_LEAP_STOMP_TICKS);

		stompFx(player, level, radius);
		ProcIndicators.send(player, SubTree.COLOSSUS_CRUSHER,
				ColossusCrusherNodes.Family.TITAN_LEAP);
	}

	/** The fists' answer to {@code CrusherActives.slamFx}: the same ring of
	 * this ground's own debris, no explosion — nothing detonated, something
	 * very heavy just arrived. */
	private static void stompFx(final ServerPlayer player, final ServerLevel level,
			final double radius) {
		var ground = player.getBlockStateOn();

		if (!ground.isAir()) {
			for (int i = 0; i < 32; i++) {
				double angle = Math.PI * 2.0 * i / 32.0;
				level.sendParticles(
						new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, ground),
						player.getX() + Math.cos(angle) * radius * 0.8,
						player.getY() + 0.2,
						player.getZ() + Math.sin(angle) * radius * 0.8,
						3, 0.15, 0.25, 0.15, 0.1);
			}
		}

		level.sendParticles(ParticleTypes.CLOUD,
				player.getX(), player.getY() + 0.2, player.getZ(), 20, 1.2, 0.1, 1.2, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2F, 1.2F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.PLAYERS, 0.8F, 0.7F);
	}

	/**
	 * Aftershock: the landing that hits. Mace in hand — the branch's whole
	 * premise, and the reason the tooltip says so. The weapon test itself lives
	 * in {@link #land}, which is where the mace and the fists fork.
	 */
	private static void aftershock(final ServerPlayer player, final ServerLevel level,
			final float fell) {
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

	// ------------------------------------------------------------------
	// The two column-top passives, read from the damage funnel. Hardened,
	// the third, keeps its own bookkeeping and lives in {@link Hardened}.
	// ------------------------------------------------------------------

	// Bulwark's old condition — node owned AND Battle Trance owned AND the
	// trance actually holding health — lived here, and it existed for one
	// reason: to keep a golden apple or the Oracle Wizard's Magic Armor from
	// switching the damage reduction on. There is no damage reduction any
	// more (the node is a standing MAX_HEALTH modifier, asserted in
	// CrusherTicker), so there is nothing left for the test to gate and it is
	// gone with it. Bulwark still raises Battle Trance's ceiling, and THAT
	// half needs no precondition — a ceiling nobody banks against is inert on
	// its own.

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
