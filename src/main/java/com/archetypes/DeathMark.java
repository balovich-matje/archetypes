package com.archetypes;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Death Mark — the Nemesis Assassin's epic active, ability slot 5 for a
 * Cutpurse, and every node that reads off the mark.
 *
 * <h2>The state machine</h2>
 * One mark at a time per assassin, held as two stamps on the OWNER
 * ({@code MARK_TARGET}, {@code MARK_END}) and one flag on the MARKED body
 * ({@code MARKED_BY}). The three are written and cleared together and only
 * here, so "who is marked" has exactly one answer. The mark ends when it
 * lapses, when the body dies, leaves the level or stops existing, or when the
 * assassin names another.
 *
 * <p>The cooldown is the mark's inverse: 45 seconds from the press, cleared the
 * moment the mark dies (by any hand). Kill what you named and you may name
 * another at once; let it walk and you wait.
 *
 * <h2>The contract for renderers</h2>
 * The mark's client-visible half rides {@code MARKED_BY} on the marked entity —
 * the channel {@code BULWARK_ACTIVE} and {@code DEADEYE_ARROW} already use, and
 * the reason there is no mark packet. A client asks the body who marked it
 * ({@link #markedBy}) rather than being handed anyone's roster, which is all
 * Stalk's through-wall outline needs. The smoke over a mark's head is sent from
 * {@link #tick} as ordinary particles, so every player in range sees the tell
 * whether or not they own anything.
 *
 * <h2>Where the rest of the tree lives</h2>
 * The mark is read, never written, by four other places: the dagger multiplier
 * and Coup de Grace in {@code LivingEntityMixin#archetypes$daggerDamage}, the
 * retarget in {@code AgilityActives.shadowStep}, the per-tick upkeep in
 * {@code AgilityTicker}, and the death hook in {@code AgilityCombat}.
 */
public final class DeathMark {
	private DeathMark() {
	}

	// ------------------------------------------------------------------
	// Predicates — safe on either side.
	// ------------------------------------------------------------------

	/** Whether this player's mark is live: named, not lapsed. Says nothing
	 * about the body still being there — {@link #target} answers that. */
	public static boolean hasMark(final Player player) {
		AttachmentTarget owner = (AttachmentTarget) player;
		Long end = owner.getAttached(ModAttachments.MARK_END);
		return owner.getAttached(ModAttachments.MARK_TARGET) != null
				&& end != null && player.level().getGameTime() < end;
	}

	/** Ticks of mark left, or 0 when none is live. */
	public static int remainingTicks(final Player player) {
		Long end = ((AttachmentTarget) player).getAttached(ModAttachments.MARK_END);
		long now = player.level().getGameTime();
		return end == null || now >= end ? 0 : (int) (end - now);
	}

	/** The entity id of whoever marked this creature, or null. The one thing a
	 * client needs, and the only reason {@code MARKED_BY} is synced. */
	public static @Nullable Integer markedBy(final Entity entity) {
		return ((AttachmentTarget) entity).getAttached(ModAttachments.MARKED_BY);
	}

	/** Whether this creature wears the given player's mark. */
	public static boolean isMarkedBy(final Entity entity, final Player player) {
		Integer by = markedBy(entity);
		return by != null && by == player.getId();
	}

	/**
	 * The live marked body, or null if the mark has lapsed, the body is gone,
	 * dead, or in another level. Server-side: an entity id only resolves where
	 * the level can be asked for it.
	 */
	public static @Nullable LivingEntity target(final ServerPlayer player) {
		if (!hasMark(player)) {
			return null;
		}

		Integer id = ((AttachmentTarget) player).getAttached(ModAttachments.MARK_TARGET);
		Entity entity = id == null ? null : ((ServerLevel) player.level()).getEntity(id);

		return entity instanceof LivingEntity living && living.isAlive() ? living : null;
	}

	/** Owned rank in a Nemesis Assassin family; 0 for anyone but the owner on a
	 * client, since ownership is target-synced. */
	public static int rank(final Player player, final NemesisAssassinNodes.Family family) {
		return NemesisAssassinNodes.rank(player, family);
	}

	// ------------------------------------------------------------------
	// The press.
	// ------------------------------------------------------------------

	/**
	 * Ability slot 5 for a Cutpurse: name whatever the crosshair rests on
	 * within 32 blocks. No weapon gate — naming a victim is not a swing, and
	 * the dagger requirement lives on what the mark then does. A press with a
	 * live mark and a spent cooldown MOVES the mark; the cooldown is what stops
	 * that being free.
	 */
	public static void mark(final ServerPlayer player) {
		if (rank(player, NemesisAssassinNodes.Family.DEATH_MARK) <= 0) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		AttachmentTarget owner = (AttachmentTarget) player;
		Long ready = owner.getAttached(ModAttachments.DEATH_MARK_READY_AT);

		if (ready != null && now < ready) {
			return;
		}

		Vec3 from = player.getEyePosition();
		Vec3 to = from.add(player.getLookAngle().scale(Tuning.DEATH_MARK_RANGE));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, from, to,
				player.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0),
				entity -> entity instanceof LivingEntity living && living.isAlive()
						&& !living.isSpectator() && living != player, 0.3F);

		if (hit == null || !(hit.getEntity() instanceof LivingEntity victim)) {
			return;
		}

		// The old body loses its flag before the new one gets it, or a moved
		// mark leaves an outline standing on a creature nobody is hunting.
		clear(player);

		owner.setAttached(ModAttachments.MARK_TARGET, victim.getId());
		owner.setAttached(ModAttachments.MARK_END, now + Tuning.DEATH_MARK_TICKS);
		owner.setAttached(ModAttachments.DEATH_MARK_READY_AT,
				now + Tuning.DEATH_MARK_COOLDOWN_TICKS);
		((AttachmentTarget) victim).setAttached(ModAttachments.MARKED_BY, player.getId());

		// A name being written, not a spell being cast: a dry click at the
		// victim and one low note under it.
		level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, victim.getX(),
				victim.getY() + victim.getBbHeight() * 0.9, victim.getZ(), 18, 0.3, 0.3, 0.3, 0.05);
		level.sendParticles(ParticleTypes.SMOKE, victim.getX(),
				victim.getY() + victim.getBbHeight() * 0.9, victim.getZ(), 12, 0.25, 0.25, 0.25, 0.01);
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
				SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 0.8F, 1.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.5F, 0.5F);
	}

	/**
	 * Drop this player's mark now, wherever it is. Reached by the ticker's
	 * lapse and its strand-guards, by a re-cast, by the death hook, by
	 * {@code ModAttachments.forgetNodes} and by the JOIN handler. Safe to call
	 * on a player with no mark.
	 *
	 * <p>The flag on the BODY is the part that matters: it lives on another
	 * entity, so nothing else in the mod would ever take it back off.
	 */
	public static void clear(final ServerPlayer player) {
		AttachmentTarget owner = (AttachmentTarget) player;
		Integer id = owner.getAttached(ModAttachments.MARK_TARGET);

		if (id != null && player.level() instanceof ServerLevel level
				&& level.getEntity(id) instanceof Entity body
				&& isMarkedBy(body, player)) {
			((AttachmentTarget) body).removeAttached(ModAttachments.MARKED_BY);
		}

		owner.removeAttached(ModAttachments.MARK_TARGET);
		owner.removeAttached(ModAttachments.MARK_END);
	}

	// ------------------------------------------------------------------
	// The per-tick machine. Driven by AgilityTicker.
	// ------------------------------------------------------------------

	static void tick(final ServerPlayer player) {
		AttachmentTarget owner = (AttachmentTarget) player;

		if (owner.getAttached(ModAttachments.MARK_TARGET) == null) {
			return;
		}

		if (!player.isAlive() || rank(player, NemesisAssassinNodes.Family.DEATH_MARK) <= 0) {
			clear(player);
			return;
		}

		LivingEntity mark = target(player);

		// Lapsed, dead, unloaded or left the level: all one ending, and the
		// flag on the body has to come off in the first three cases too.
		if (mark == null || mark.level() != player.level()) {
			clear(player);
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();

		// A body that came back from a chunk reload wearing nothing (the flag is
		// transient) is re-flagged rather than dropped — the owner's stamps are
		// the mark's truth and this is only their published copy.
		if (!isMarkedBy(mark, player)) {
			((AttachmentTarget) mark).setAttached(ModAttachments.MARKED_BY, player.getId());
		}

		// The tell, for everyone: a slow drip over the mark's head.
		if (now % Tuning.DEATH_MARK_SMOKE_PERIOD_TICKS == 0) {
			level.sendParticles(ParticleTypes.SMOKE, mark.getX(),
					mark.getY() + mark.getBbHeight() + 0.35, mark.getZ(), 3, 0.12, 0.05, 0.12, 0.01);
		}

		if (rank(player, NemesisAssassinNodes.Family.STALK) > 0) {
			stalk(player, mark);
		}

		if (rank(player, NemesisAssassinNodes.Family.CARRIER) > 0
				&& now % Tuning.CARRIER_PERIOD_TICKS == 0) {
			carrier(player, level, mark);
		}
	}

	/**
	 * Stalk: a sneaking hunter simply stops being the mark's problem past eight
	 * blocks. Done by dropping the mob's target server-side rather than by
	 * appending a {@code lookingEntity} to
	 * {@code LivingEntityMixin#archetypes$dimPresence} — that hook is a
	 * {@code ModifyReturnValue} on {@code getVisibilityPercent} that does not
	 * capture the looker today, and this needs no mixin at all.
	 */
	private static void stalk(final ServerPlayer player, final LivingEntity mark) {
		if (!player.isCrouching() || !(mark instanceof Mob mob) || mob.getTarget() != player) {
			return;
		}

		if (mark.distanceToSqr(player) > Tuning.STALK_UNAWARE_BLOCKS * Tuning.STALK_UNAWARE_BLOCKS) {
			mob.setTarget(null);
		}
	}

	/** The three effects Carrier moves. Poison and Wither are the base tree's
	 * Venom and Blight; Slowness is Crippling Poison. */
	private static final List<Holder<MobEffect>> CARRIED =
			List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.SLOWNESS);

	/**
	 * Carrier: once a second the mark's own poisons are copied outward. Fresh
	 * instances rather than the copy constructor, so a hidden effect underneath
	 * (the sixth constructor argument) is never carried along with them, and
	 * amplifier plus remaining duration are all that travels.
	 *
	 * <p>Nothing here deals damage, so no re-entrancy guard is needed.
	 */
	private static void carrier(final ServerPlayer player, final ServerLevel level,
			final LivingEntity mark) {
		List<MobEffectInstance> carried = new ArrayList<>();

		for (Holder<MobEffect> effect : CARRIED) {
			MobEffectInstance instance = mark.getEffect(effect);

			if (instance != null) {
				carried.add(instance);
			}
		}

		if (carried.isEmpty()) {
			return;
		}

		double radiusSq = Tuning.CARRIER_RADIUS * Tuning.CARRIER_RADIUS;

		for (LivingEntity neighbour : level.getEntitiesOfClass(LivingEntity.class,
				mark.getBoundingBox().inflate(Tuning.CARRIER_RADIUS),
				living -> living.isAlive() && living != mark && living != player
						&& !living.isSpectator() && living.distanceToSqr(mark) <= radiusSq)) {
			for (MobEffectInstance instance : carried) {
				neighbour.addEffect(new MobEffectInstance(instance.getEffect(),
						instance.getDuration(), instance.getAmplifier(), false, true));
			}
		}

		level.sendParticles(ParticleTypes.SCULK_SOUL, mark.getX(),
				mark.getY() + mark.getBbHeight() * 0.5, mark.getZ(), 6, 0.6, 0.4, 0.6, 0.01);
	}

	// ------------------------------------------------------------------
	// The mark's death.
	// ------------------------------------------------------------------

	/**
	 * True only while a Death's Head detonation is resolving. The pulse is
	 * attributed to the assassin so a detonation kill still credits them, which
	 * makes every body it drops look like a fresh mark death; without the guard
	 * a dense pack would re-enter this hook and cascade. Same idiom as
	 * {@code NightForm.bleeding} and {@code RadianceAura.isPulsing}.
	 */
	private static boolean detonating;

	/**
	 * Any death of any creature, called from {@code AgilityCombat}. Everything
	 * downstream is keyed on the victim's own {@code MARKED_BY} flag, so this is
	 * an attachment read for the 99.9% of deaths that are not a mark's.
	 *
	 * <p>Order is fixed and the design says why: detonate first (Death's Head
	 * fires on the pack as it stood), then hand the assassin their four seconds,
	 * then hop the mark to a survivor. The cooldown clears whoever landed the
	 * blow — the mark being dead is the condition, not who killed it — while
	 * Vanishing Act is the assassin's own kill only.
	 */
	public static void onDeath(final LivingEntity victim, final @Nullable Entity killer) {
		if (detonating) {
			return;
		}

		Integer by = markedBy(victim);

		if (by == null || !(victim.level() instanceof ServerLevel level)
				|| !(level.getEntity(by) instanceof ServerPlayer player)) {
			// A marked body whose assassin is gone: take the flag off so it
			// cannot outlive them on a corpse that gets revived or reloaded.
			((AttachmentTarget) victim).removeAttached(ModAttachments.MARKED_BY);
			return;
		}

		((AttachmentTarget) victim).removeAttached(ModAttachments.MARKED_BY);

		if (rank(player, NemesisAssassinNodes.Family.DEATHS_HEAD) > 0) {
			detonate(player, level, victim);
		}

		if (killer == player && rank(player, NemesisAssassinNodes.Family.VANISHING_ACT) > 0) {
			vanish(player, level);
		}

		// The mark's remaining seconds travel with it; Contagion buys the hop,
		// not a fresh minute.
		if (rank(player, NemesisAssassinNodes.Family.CONTAGION) > 0
				&& hop(player, level, victim)) {
			((AttachmentTarget) player).removeAttached(ModAttachments.DEATH_MARK_READY_AT);
			return;
		}

		clear(player);
		// "Cleared when the mark dies" — by any hand, so an assassin whose mark
		// is taken from them is not punished for it.
		((AttachmentTarget) player).removeAttached(ModAttachments.DEATH_MARK_READY_AT);
	}

	/** Death's Head: 5 hearts to everything else standing next to the corpse,
	 * through the indirect-magic source the night form's bleeds use, so a
	 * detonation kill still credits the assassin. */
	private static void detonate(final ServerPlayer player, final ServerLevel level,
			final LivingEntity corpse) {
		double radiusSq = Tuning.DEATHS_HEAD_RADIUS * Tuning.DEATHS_HEAD_RADIUS;
		List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class,
				corpse.getBoundingBox().inflate(Tuning.DEATHS_HEAD_RADIUS),
				living -> living.isAlive() && living != corpse && living != player
						&& !living.isSpectator() && living.distanceToSqr(corpse) <= radiusSq);

		detonating = true;

		try {
			for (LivingEntity caughtEntity : caught) {
				caughtEntity.hurtServer(level, level.damageSources().indirectMagic(player, player),
						Tuning.DEATHS_HEAD_DAMAGE);
			}
		} finally {
			detonating = false;
		}

		// Loud on purpose: this is the crowd branch's payoff, and the ring is
		// drawn at the radius so the next mark can be picked inside it.
		for (int step = 0; step < 24; step++) {
			double angle = step * Math.PI / 12.0;
			level.sendParticles(ParticleTypes.SOUL,
					corpse.getX() + Math.cos(angle) * Tuning.DEATHS_HEAD_RADIUS,
					corpse.getY() + 0.3,
					corpse.getZ() + Math.sin(angle) * Tuning.DEATHS_HEAD_RADIUS,
					1, 0.0, 0.05, 0.0, 0.01);
		}

		level.sendParticles(ParticleTypes.SCULK_SOUL, corpse.getX(),
				corpse.getY() + 0.8, corpse.getZ(), 40, 0.6, 0.6, 0.6, 0.08);
		level.playSound(null, corpse.getX(), corpse.getY(), corpse.getZ(),
				SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	/** Vanishing Act: the Shadow tree's own vanishing cue, so the four seconds
	 * read as the same thing Invisibility already taught. */
	private static void vanish(final ServerPlayer player, final ServerLevel level) {
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
				Tuning.VANISHING_ACT_TICKS, 0, true, false));
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, Tuning.VANISHING_ACT_TICKS,
				Tuning.VANISHING_ACT_SPEED_AMPLIFIER, true, false));

		level.sendParticles(ParticleTypes.LARGE_SMOKE,
				player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.3, 0.5, 0.3, 0.01);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.CANDLE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	/**
	 * Contagion: the mark walks to the nearest enemy still standing, keeping
	 * whatever is left of its minute. Hostiles and players only — the design's
	 * own balance note, and without it a chain through a cow field would out-farm
	 * any fight. The description says "enemy" for exactly that reason.
	 *
	 * @return whether the mark found somewhere to go
	 */
	private static boolean hop(final ServerPlayer player, final ServerLevel level,
			final LivingEntity corpse) {
		double radiusSq = Tuning.CONTAGION_HOP_RADIUS * Tuning.CONTAGION_HOP_RADIUS;
		LivingEntity next = null;
		double best = Double.MAX_VALUE;

		for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
				corpse.getBoundingBox().inflate(Tuning.CONTAGION_HOP_RADIUS),
				living -> (living instanceof Enemy || living instanceof Player)
						&& living.isAlive() && living != corpse && living != player
						&& !living.isSpectator() && living.distanceToSqr(corpse) <= radiusSq)) {
			double distance = candidate.distanceToSqr(corpse);

			if (distance < best) {
				best = distance;
				next = candidate;
			}
		}

		if (next == null) {
			return false;
		}

		// clear() would take MARK_END with it and the hop is supposed to keep
		// it, so only the target stamp and the old flag move.
		((AttachmentTarget) player).setAttached(ModAttachments.MARK_TARGET, next.getId());
		((AttachmentTarget) next).setAttached(ModAttachments.MARKED_BY, player.getId());

		level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, next.getX(),
				next.getY() + next.getBbHeight() * 0.9, next.getZ(), 18, 0.3, 0.3, 0.3, 0.05);
		level.playSound(null, next.getX(), next.getY(), next.getZ(),
				SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 0.8F, 1.4F);
		ProcIndicators.send(player, SubTree.NEMESIS_ASSASSIN,
				NemesisAssassinNodes.Family.CONTAGION);
		return true;
	}
}
