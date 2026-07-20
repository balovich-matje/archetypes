package com.archetypes;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Slayer capstone actives. Both ride the shared ability key: the payload
 * receiver dispatches on the mainhand item, so G is bash with a shield,
 * Decimate with a greatsword, Bladestorm with a sword.
 */
public final class SlayerActives {
	/** Decimates in flight — cast, not yet landed. One entry per caster; the
	 * cooldown makes a second one impossible, and the parry's riposte never
	 * enters the list because it does not wind up at all. Server thread only,
	 * like the tick that drains it. */
	private static final List<Pending> PENDING = new ArrayList<>();

	private SlayerActives() {
	}

	/**
	 * A cast Decimate waiting on its wind-up.
	 *
	 * <p>The FACING is captured at cast and the POSITION is not, and the split
	 * is the whole counterplay: the arc points where the greatsword was hauled
	 * back to point, so stepping out of it sideways works even against a caster
	 * who spins, while the caster's own last second of walking still moves the
	 * blow with them — as far as Slowness II lets them walk, which is not far.
	 */
	private record Pending(ServerPlayer player, long resolveAt, Vec3 flat) {
	}

	/** Registered from {@code Archetypes.onInitialize}: drains {@link #PENDING},
	 * paints the wind-up, and lands the blow. */
	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (PENDING.isEmpty()) {
				return;
			}

			for (Iterator<Pending> it = PENDING.iterator(); it.hasNext();) {
				Pending pending = it.next();
				ServerPlayer player = pending.player();

				// The caster stopped being a caster: died, logged out, changed
				// their mind about the greatsword. The cooldown was paid at
				// cast and is not refunded — the swing is what is dropped.
				if (player.isRemoved() || !player.isAlive()
						|| !ModItems.isGreatsword(player.getMainHandItem())) {
					it.remove();
					continue;
				}

				ServerLevel level = (ServerLevel) player.level();
				long now = level.getGameTime();

				if (now < pending.resolveAt()) {
					paintWindup(player, level, pending.flat(), now);
					continue;
				}

				it.remove();
				resolve(player, level, pending.flat());
			}
		});
	}

	/**
	 * Decimate: one massive tilted cleave, a second after you commit to it.
	 * Every living thing left in the front arc when it lands takes a share of
	 * its own maximum health on {@code archetypes:decimate} — through armour,
	 * through Protection, through a shield — and instant-break clutter is swept
	 * from its path. Nothing sturdier: swinging this inside your own base must
	 * never redecorate it.
	 *
	 * @return whether the cast was accepted (node, weapon and cooldown all
	 *     satisfied), so the parry's riposte can fall back to the sword's answer
	 *     when it was not
	 */
	public static boolean decimate(final ServerPlayer player) {
		return decimate(player, false);
	}

	/**
	 * @param free the Colossus Slayer's greatsword parry, which the author
	 *     spelled out: "automatically cast Decimates will not incur a cooldown,
	 *     and can happen while the skill is already on cooldown". So a free
	 *     cast neither reads nor writes {@code DECIMATE_READY_AT} — a parry is
	 *     never the swing you could not afford, and it never eats the swing you
	 *     were saving.
	 *
	 *     <p>Two things a free cast does differently beyond the stamp. It lands
	 *     IMMEDIATELY, with no wind-up and no Slowness: "the Decimate from parry
	 *     should remain immediate", and a parry is already a reaction — it does
	 *     not owe a second one. And it answers to a clock of its own,
	 *     {@link Tuning#DECIMATE_FREE_COOLDOWN_TICKS}, because a landed parry
	 *     refunds its own key ({@code ColossusSlayer.pay}); without that fence
	 *     the riposte is an unbounded armour-ignoring nuke whose rate is set by
	 *     how often someone swings at you.
	 */
	public static boolean decimate(final ServerPlayer player, final boolean free) {
		var owned = NodePurchases.owned(player, SubTree.SLAYER);

		if (SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.DECIMATE) == 0
				|| !ModItems.isGreatsword(player.getMainHandItem())) {
			return false;
		}

		AttachmentTarget target = (AttachmentTarget) player;
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize();

		if (free) {
			Long freeReadyAt = target.getAttached(ModAttachments.DECIMATE_FREE_READY_AT);

			if (freeReadyAt != null && now < freeReadyAt) {
				return false;
			}

			target.setAttached(ModAttachments.DECIMATE_FREE_READY_AT,
					now + Tuning.DECIMATE_FREE_COOLDOWN_TICKS);
			pose(player, now);
			resolve(player, level, flat);
			return true;
		}

		Long readyAt = target.getAttached(ModAttachments.DECIMATE_READY_AT);

		if (readyAt != null && now < readyAt) {
			return false;
		}

		int decimateCooldown = Tuning.DECIMATE_COOLDOWN_TICKS
				- (SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.RELENTLESS) > 0
						? Tuning.RELENTLESS_REDUCTION_TICKS : 0);
		target.setAttached(ModAttachments.DECIMATE_READY_AT, now + decimateCooldown);

		// The telegraph. Everything here is for the person about to be hit:
		// the pose is synced to every client that can see the caster, the
		// charge is one of the loudest cues vanilla owns, and paintWindup
		// draws the arc on the ground every tick until the blow lands.
		pose(player, now);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.6F, 1.4F);

		// The caster's half of the bargain: a second of commitment, at a speed
		// that cannot chase a target who reads it and leaves.
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, Tuning.DECIMATE_WINDUP_TICKS,
				Tuning.DECIMATE_WINDUP_SLOWNESS_AMPLIFIER, false, true, true));

		PENDING.add(new Pending(player, now + Tuning.DECIMATE_WINDUP_TICKS, flat));
		return true;
	}

	/** No vanilla swing: the PAL cleave pose owns the body for the swing's
	 * duration, on every client that can see us. */
	private static void pose(final ServerPlayer player, final long now) {
		((AttachmentTarget) player).setAttached(ModAttachments.DECIMATE_SWING_AT, now);
	}

	/**
	 * One tick of wind-up, drawn where the victim is standing rather than where
	 * the caster is: a dotted line along the outer edge of the arc, at foot
	 * height, so "am I inside it" is a question you answer by looking down.
	 */
	private static void paintWindup(final ServerPlayer player, final ServerLevel level,
			final Vec3 flat, final long now) {
		if ((now & 1L) != 0L) {
			return;
		}

		double range = Tuning.DECIMATE_RANGE;

		// The same half-disc inFront() tests, walked in eight steps: dot > 0.2
		// is +-78 degrees either side of the facing.
		for (int step = 0; step <= 8; step++) {
			double angle = Math.toRadians(-78.0 + 78.0 * step / 4.0);
			double sin = Math.sin(angle);
			double cos = Math.cos(angle);
			Vec3 edge = new Vec3(flat.x * cos - flat.z * sin, 0.0, flat.x * sin + flat.z * cos)
					.scale(range);
			level.sendParticles(ParticleTypes.CRIT,
					player.getX() + edge.x, player.getY() + 0.1, player.getZ() + edge.z,
					1, 0.05, 0.0, 0.05, 0.0);
		}
	}

	/**
	 * The blow itself. The victim list is recomputed here and not at cast, so a
	 * target who spent the wind-up leaving is genuinely gone — that is what the
	 * wind-up is for.
	 *
	 * <h2>What is deliberately not here</h2>
	 * There is no {@code MeleeSwing.begin/end} around the damage any more, and
	 * that is the point rather than an oversight. Wrapped, Decimate rode
	 * {@code LivingEntityMixin.archetypes$greatswordDamage} — Heavy Blows,
	 * First Blood and the Executioner clamp — on top of a number the attacker
	 * already inflated through {@code ATTACK_DAMAGE} (the item, Blade Master,
	 * Strength). A hit that reads differently to every attacker cannot be given
	 * a single tunable number, and "you don't want to be near it when it's cast"
	 * has to be true of everyone. (Sharpness and Specialities' Combat multiplier
	 * never rode it in the first place: both are gated on {@code Player.attack},
	 * which a Decimate has never gone through. Rend, Blade Dance, Expose and
	 * Flense are sword- or dagger-gated and never saw a greatsword.)
	 *
	 * <p>What the wrapper WAS buying Decimate besides that is the Slayer's own
	 * greatsword-legal on-hit and on-kill passives, and those are unrelated to
	 * the damage chain, so they are re-applied by hand in {@link #slayerPassives}
	 * rather than lost.
	 */
	private static void resolve(final ServerPlayer player, final ServerLevel level, final Vec3 flat) {
		var owned = NodePurchases.owned(player, SubTree.SLAYER);
		double range = Tuning.DECIMATE_RANGE;

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 1.0F, 0.8F);

		// Entities: everything in the front half-disc, as it stands NOW.
		List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().expandTowards(flat.scale(range)).inflate(1.2, 0.5, 1.2),
				entity -> entity != player && entity.isAlive() && !entity.isSpectator()
						&& inFront(player, entity, flat));

		for (LivingEntity victim : victims) {
			// The whole number, read off the victim: their own bar and nothing
			// of the attacker's. Above 1.0 so full health is not a survivable
			// state, floored so a small pool is not a cheap one, capped so a
			// figure calibrated against a 40 HP player does not delete a Warden.
			float damage = Mth.clamp(victim.getMaxHealth() * Tuning.DECIMATE_MAX_HEALTH_FRACTION,
					Tuning.DECIMATE_MIN_DAMAGE, Tuning.DECIMATE_MAX_DAMAGE);
			// The return value is the old AFTER_DAMAGE gate's "taken > 0": a hit
			// a parry, Ghost Form or Sidestep voided must not still cripple.
			if (victim.hurtServer(level, ModDamageTypes.decimate(level, player), damage)) {
				slayerPassives(player, level, victim, owned);
			}

			Vec3 away = victim.position().subtract(player.position());
			Vec3 push = new Vec3(away.x, 0.0, away.z).normalize();
			victim.push(push.x * 0.8, 0.2, push.z * 0.8);
		}

		sweepBlocks(player, level, flat, range);

		// The cleave itself: one greatsword-wide flash centred in front,
		// stretched along the swing's tangent and tilted down across it to
		// carry the greatsword's weight. With count 0 the offset triple becomes
		// the particle's velocity, which greatsword_sweep reads as its stretch
		// direction instead.
		level.sendParticles(ModParticles.GREATSWORD_SWEEP,
				player.getX() + flat.x * 2.6,
				player.getY() + 1.35,
				player.getZ() + flat.z * 2.6,
				0, -flat.z, 0.0, flat.x, 1.0);
	}

	/**
	 * The Slayer's greatsword-legal on-hit and on-kill passives, applied by hand
	 * because Decimate no longer opens a {@code MeleeSwing} for
	 * {@code SlayerCombat}'s event hooks to recognise.
	 *
	 * <p>This is a deliberate copy of the greatsword-eligible half of
	 * {@code SlayerCombat}: Hamstring, Taste of Blood and Bloodlust. Rend and
	 * Blade Dance are not here because they are sword-only there, so a Decimate
	 * has never applied them. Note that {@code AFTER_DAMAGE} never fired on a
	 * lethal blow anyway (Fabric gates it on {@code !isDeadOrDying}), so
	 * Hamstring on a survivor is the only on-hit half that ever ran.
	 */
	private static void slayerPassives(final ServerPlayer player, final ServerLevel level,
			final LivingEntity victim, final java.util.Set<Integer> owned) {
		if (victim.isAlive()) {
			int slow = SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.SLOWNESS);

			if (slow > 0) {
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, Tuning.SLOWNESS_TICKS,
						slow - 1), player);
			}

			return;
		}

		int taste = SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.TASTE_OF_BLOOD);

		if (taste > 0) {
			player.heal(taste * Tuning.TASTE_OF_BLOOD_HEAL_PER_RANK);
			level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.5,
					player.getZ(), taste, 0.3, 0.3, 0.3, 0.0);
			ProcIndicators.send(player, SubTree.SLAYER, SlayerNodes.Family.TASTE_OF_BLOOD);
		}

		if (SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.BLOODLUST) > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, Tuning.BLOODLUST_TICKS, 0));
		}
	}

	/** Blocks: only instant-break clutter is swept. destroyBlock drops loot and
	 * plays each block's own break effect, which doubles as our debris. */
	private static void sweepBlocks(final ServerPlayer player, final ServerLevel level,
			final Vec3 flat, final double range) {
		BlockPos centre = player.blockPosition();
		int destroyed = 0;

		outer:
		for (int dy = 0; dy <= 1; dy++) {
			for (int dx = (int) -range; dx <= range; dx++) {
				for (int dz = (int) -range; dz <= range; dz++) {
					if (dx * dx + dz * dz > range * range) {
						continue;
					}

					BlockPos pos = centre.offset(dx, dy, dz);
					Vec3 toBlock = Vec3.atCenterOf(pos).subtract(player.position());
					Vec3 toBlockFlat = new Vec3(toBlock.x, 0.0, toBlock.z);

					if (toBlockFlat.lengthSqr() > 0.1
							&& toBlockFlat.normalize().dot(flat) < 0.3) {
						continue;
					}

					BlockState state = level.getBlockState(pos);

					if (state.isAir()) {
						continue;
					}

					if (state.getDestroySpeed(level, pos) != 0.0F) {
						continue;
					}

					level.destroyBlock(pos, true, player);

					if (++destroyed >= Tuning.DECIMATE_MAX_BLOCKS) {
						break outer;
					}
				}
			}
		}
	}

	/** Bladestorm: start the channel; SlayerTicker runs the volleys. */
	public static void bladestorm(final ServerPlayer player) {
		var owned = NodePurchases.owned(player, SubTree.SLAYER);

		if (SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.BLADESTORM) == 0
				|| !ModItems.isSword(player.getMainHandItem())) {
			return;
		}

		AttachmentTarget target = (AttachmentTarget) player;
		long now = player.level().getGameTime();
		Long readyAt = target.getAttached(ModAttachments.BLADESTORM_READY_AT);

		if (readyAt != null && now < readyAt) {
			return;
		}

		int stormCooldown = Tuning.BLADESTORM_COOLDOWN_TICKS
				- (SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.RELENTLESS) > 0
						? Tuning.RELENTLESS_REDUCTION_TICKS : 0);
		target.setAttached(ModAttachments.BLADESTORM_READY_AT, now + stormCooldown);
		target.setAttached(ModAttachments.BLADESTORM_END, now + Tuning.BLADESTORM_CHANNEL_TICKS);

		((ServerLevel) player.level()).playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.7F);
	}

	private static boolean inFront(final ServerPlayer player, final LivingEntity target, final Vec3 flat) {
		Vec3 toTarget = target.position().subtract(player.position());
		Vec3 toTargetFlat = new Vec3(toTarget.x, 0.0, toTarget.z);

		if (toTargetFlat.lengthSqr() < 1.0E-4) {
			return true;
		}

		return toTargetFlat.normalize().dot(flat) > 0.2;
	}
}
