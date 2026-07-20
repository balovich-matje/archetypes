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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Deadeye — the Nemesis Marksman's epic active, ability slot 4 for a Cutpurse,
 * and every node that reads inside its window.
 *
 * <h2>The state machine</h2>
 * One state, derived from one synced game-tick stamp: {@code DEADEYE_END} set
 * and in the future means the stance holds. {@link #activate} enters it,
 * {@link #tick} lapses it, and a press made while it holds does nothing — like
 * the night form, you do not get to end it early. There is no persistence to
 * reconcile: fifteen seconds cannot survive a relog, so {@code Archetypes}'
 * JOIN handler clears the stamp the way it clears the Dark Ritual's channel.
 *
 * <h2>Full draw without a draw mixin</h2>
 * The stance is sold as "arrows leave at full draw however briefly you pull",
 * and that is implemented as a spawn-time velocity rescale in
 * {@link #onArrowSpawn}, NOT as a mixin on {@code BowItem}'s draw. Two reasons:
 * <ul>
 *   <li>{@code AbstractArrow.onHitEntity} computes its damage as
 *       {@code ceil(deltaMovement.length() * baseDamage)}, so raising an
 *       underdrawn arrow's velocity to full-draw speed raises its damage with
 *       it, exactly and for free. Swift Flight's comment in
 *       {@code MarksmanCombat} says the same thing and divides {@code
 *       baseDamage} back down precisely because it does NOT want that.</li>
 *   <li>Draw time is client-predicted. {@code MagicBowItem}'s draw work needs
 *       {@code UseDurationMixin} on BOTH sides and a shared
 *       {@code MagicArmaments.drawTimeFactor} to keep them agreeing; a
 *       server-only velocity write, published with {@code hurtMarked}, has no
 *       second opinion to disagree with.</li>
 * </ul>
 * The cost is cosmetic and known: the bow's string still animates half-drawn on
 * a snap release. That is a client-side lie about an arrow that has already
 * left at full speed, and it is not worth a second draw mixin for a
 * fifteen-second stance.
 *
 * <h2>What the arrow carries</h2>
 * A Deadeye arrow can outlive the stance — 64 blocks is over three seconds of
 * flight — so everything it is owed is stamped on the arrow at spawn
 * ({@code DEADEYE_ARROW}, {@code DEADEYE_SIEGE_ARROW}) rather than asked of the
 * shooter at impact. Long Shot measures from the same {@code TRUE_SHOT_ORIGIN}
 * the True Shot despawn already uses.
 */
public final class Deadeye {
	private Deadeye() {
	}

	// ------------------------------------------------------------------
	// Predicates — safe on either side; DEADEYE_END syncs to every client.
	// ------------------------------------------------------------------

	/** Whether the Deadeye stance holds on this player right now. */
	public static boolean isActive(final Player player) {
		Long end = ((AttachmentTarget) player).getAttached(ModAttachments.DEADEYE_END);
		return end != null && player.level().getGameTime() < end;
	}

	/** Ticks of stance left, or 0 when none is running. */
	public static int remainingTicks(final Player player) {
		Long end = ((AttachmentTarget) player).getAttached(ModAttachments.DEADEYE_END);
		long now = player.level().getGameTime();
		return end == null || now >= end ? 0 : (int) (end - now);
	}

	/** Owned rank in a Nemesis Marksman family; 0 for anyone but the owner on a
	 * client, since ownership is target-synced. */
	public static int rank(final Player player, final NemesisMarksmanNodes.Family family) {
		return NemesisMarksmanNodes.rank(player, family);
	}

	/**
	 * Siege is bought, the stance holds, and the player has stood still long
	 * enough to arm it — the one gate behind both the planted multiplier and
	 * the knockback immunity. Server-side in practice: the still stamp is not
	 * synced, so a client always reads false.
	 */
	public static boolean isPlanted(final Player player) {
		if (!isActive(player) || rank(player, NemesisMarksmanNodes.Family.SIEGE) <= 0) {
			return false;
		}

		Long since = ((AttachmentTarget) player).getAttached(ModAttachments.DEADEYE_STILL_SINCE);
		return since != null && player.level().getGameTime() - since >= Tuning.SIEGE_ARM_TICKS;
	}

	/** How long a press buys, Long Watch included. */
	public static int durationTicks(final Player player) {
		return rank(player, NemesisMarksmanNodes.Family.LONG_WATCH) > 0
				? Tuning.DEADEYE_LONG_WATCH_TICKS : Tuning.DEADEYE_TICKS;
	}

	// ------------------------------------------------------------------
	// The press.
	// ------------------------------------------------------------------

	/**
	 * Ability slot 4 for a Cutpurse. Needs the node, a bow or crossbow in the
	 * main hand (the same gate shape as {@code AgilityActives.trueShot}) and a
	 * spent cooldown; a press made while the stance already holds is ignored.
	 */
	public static void activate(final ServerPlayer player) {
		ItemStack weapon = player.getMainHandItem();

		if (rank(player, NemesisMarksmanNodes.Family.DEADEYE) <= 0
				|| isActive(player)
				|| !(weapon.is(Items.BOW) || weapon.is(Items.CROSSBOW))) {
			return;
		}

		AttachmentTarget target = (AttachmentTarget) player;
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		Long ready = target.getAttached(ModAttachments.DEADEYE_READY_AT);

		if (ready != null && now < ready) {
			return;
		}

		target.setAttached(ModAttachments.DEADEYE_END, now + durationTicks(player));
		target.setAttached(ModAttachments.DEADEYE_READY_AT, now + Tuning.DEADEYE_COOLDOWN_TICKS);
		// The still test needs a baseline, or the first tick reads as movement
		// and Siege's arm restarts a tick late.
		target.setAttached(ModAttachments.DEADEYE_LAST_POS, player.position());
		target.removeAttached(ModAttachments.DEADEYE_STILL_SINCE);

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 0.7F);
	}

	/**
	 * End the stance now, unconditionally. Reached by the ticker's lapse, by
	 * its strand-guard when the archetype goes, and by
	 * {@code ModAttachments.forgetNodes} when the root is respecced away. Safe
	 * to call on a player who has no stance. The Slowness is left to lapse
	 * rather than removed — see {@link #tick}.
	 */
	public static void end(final ServerPlayer player) {
		AttachmentTarget target = (AttachmentTarget) player;

		if (target.getAttached(ModAttachments.DEADEYE_END) == null) {
			return;
		}

		target.removeAttached(ModAttachments.DEADEYE_END);
		target.removeAttached(ModAttachments.DEADEYE_STILL_SINCE);
		target.removeAttached(ModAttachments.DEADEYE_LAST_POS);

		if (player.isAlive()) {
			// Smaller than the arrival, the rule NightForm.end follows.
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.SPYGLASS_STOP_USING, SoundSource.PLAYERS, 0.5F, 0.9F);
		}
	}

	// ------------------------------------------------------------------
	// The per-tick machine. Driven by AgilityTicker.
	// ------------------------------------------------------------------

	static void tick(final ServerPlayer player) {
		AttachmentTarget target = (AttachmentTarget) player;
		Long endAt = target.getAttached(ModAttachments.DEADEYE_END);

		if (endAt == null) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();

		if (level.getGameTime() >= endAt || !player.isAlive()
				|| rank(player, NemesisMarksmanNodes.Family.DEADEYE) <= 0) {
			end(player);
			return;
		}

		Set<Integer> owned = NodePurchases.owned(player, SubTree.NEMESIS_MARKSMAN);

		// Re-asserted every tick, hidden, and left to lapse rather than
		// removed: an explicit remove would eat a Slowness or a Speed the
		// player had from somewhere else (the ShadowTicker Night Stalker
		// lesson). Fleet buys the slow off and pays back Speed II instead.
		if (NemesisMarksmanNodes.rank(SubTree.NEMESIS_MARKSMAN, owned,
				NemesisMarksmanNodes.Family.FLEET) > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, Tuning.DEADEYE_EFFECT_TICKS,
					Tuning.FLEET_SPEED_AMPLIFIER, true, false));
		} else {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, Tuning.DEADEYE_EFFECT_TICKS,
					Tuning.DEADEYE_SLOWNESS_AMPLIFIER, true, false));
		}

		if (NemesisMarksmanNodes.rank(SubTree.NEMESIS_MARKSMAN, owned,
				NemesisMarksmanNodes.Family.ON_THE_WING) > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,
					Tuning.DEADEYE_EFFECT_TICKS, 0, true, false));
		}

		still(player, level, NemesisMarksmanNodes.rank(SubTree.NEMESIS_MARKSMAN, owned,
				NemesisMarksmanNodes.Family.SIEGE) > 0);
	}

	/**
	 * Siege's stand-still test. Compared as a POSITION delta between server
	 * ticks, not as {@code getDeltaMovement()}: the client is authoritative
	 * about a player's movement and the server's copy of the vector is not
	 * trustworthy tick to tick, while the position it reports is.
	 *
	 * <p>The arm announces itself, because losing it is silent: moving simply
	 * stops paying, and a player who never saw it arrive would never learn the
	 * rule.
	 */
	private static void still(final ServerPlayer player, final ServerLevel level, final boolean siege) {
		AttachmentTarget target = (AttachmentTarget) player;
		Vec3 pos = player.position();
		Vec3 last = target.getAttached(ModAttachments.DEADEYE_LAST_POS);
		target.setAttached(ModAttachments.DEADEYE_LAST_POS, pos);

		boolean moved = last == null
				|| last.distanceToSqr(pos) > Tuning.SIEGE_STILL_TOLERANCE * Tuning.SIEGE_STILL_TOLERANCE;

		if (moved) {
			target.removeAttached(ModAttachments.DEADEYE_STILL_SINCE);
			return;
		}

		Long since = target.getAttached(ModAttachments.DEADEYE_STILL_SINCE);

		if (since == null) {
			target.setAttached(ModAttachments.DEADEYE_STILL_SINCE, level.getGameTime());
			return;
		}

		if (siege && level.getGameTime() - since == Tuning.SIEGE_ARM_TICKS) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
					player.getX(), player.getY() + 0.1, player.getZ(), 16, 0.35, 0.05, 0.35, 0.02);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.7F, 0.6F);
		}
	}

	// ------------------------------------------------------------------
	// The arrow, leaving.
	// ------------------------------------------------------------------

	/**
	 * Called from {@code MarksmanCombat.onArrowSpawn} BEFORE Swift Flight, so
	 * the two compose instead of overwriting: this one normalises an underdrawn
	 * arrow up to full-draw speed, and Swift Flight's damage-neutral multiplier
	 * then applies on top of whatever it finds.
	 */
	public static void onArrowSpawn(final ServerPlayer player, final AbstractArrow arrow) {
		if (!isActive(player)) {
			return;
		}

		AttachmentTarget onArrow = (AttachmentTarget) arrow;
		onArrow.setAttached(ModAttachments.DEADEYE_ARROW, true);

		if (isPlanted(player)) {
			onArrow.setAttached(ModAttachments.DEADEYE_SIEGE_ARROW, true);
		}

		// Full draw. Only ever upward: a crossbow already leaves at 3.15
		// (CrossbowItem.ARROW_POWER) and must not be slowed to the bow's 3.0.
		double speed = arrow.getDeltaMovement().length();

		if (speed > 1.0E-4 && speed < Tuning.DEADEYE_FULL_DRAW_SPEED) {
			arrow.setDeltaMovement(arrow.getDeltaMovement()
					.scale(Tuning.DEADEYE_FULL_DRAW_SPEED / speed));
			// Without this the client never sees the new velocity and integrates
			// the old one until the next position update (Swift Flight's lesson).
			arrow.hurtMarked = true;
			// A full draw is a crit arrow in vanilla (BowItem passes
			// {@code pow == 1.0F} as the crit flag); an arrow that leaves at
			// full draw leaves crit, or "full draw" would be half true.
			arrow.setCritArrow(true);
		}

		// Flat flight and the 64-block despawn, which is exactly what True Shot
		// already does — no multiplier, because the root grants no damage.
		AgilityActives.empower(arrow, 1.0F, false);

		// Punch Through: vanilla's own piercing, so the ignore set and the
		// killed-by-arrow criterion come with it. Never lowers what an enchanted
		// crossbow already had.
		if (rank(player, NemesisMarksmanNodes.Family.PUNCH_THROUGH) > 0
				&& arrow.getPierceLevel() < Tuning.PUNCH_THROUGH_PIERCE_LEVEL) {
			((AbstractArrowAccessor) arrow)
					.archetypes$setPierceLevel(Tuning.PUNCH_THROUGH_PIERCE_LEVEL);
		}

		// Not consumed: the Conservation refund path at 100%, including the
		// creative-only downgrade that stops the flying arrow being collected a
		// second time. You still need an arrow to fire — nothing is conjured.
		if (arrow.pickup == AbstractArrow.Pickup.ALLOWED) {
			ItemStack refund = ((AbstractArrowAccessor) arrow).archetypes$getPickupItemStack().copy();
			refund.setCount(1);
			arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;

			if (!player.getInventory().add(refund)) {
				player.drop(refund, false);
			}
		}
	}

	// ------------------------------------------------------------------
	// The arrow, landing.
	// ------------------------------------------------------------------

	/**
	 * Long Shot's distance ramp, Siege's planted multiplier and Punch Through's
	 * armour bypass, folded into the damage {@code MarksmanCombat.onArrowHit}
	 * returns — so they land on killing blows too (see that class's comment on
	 * why on-hit work does not ride AFTER_DAMAGE).
	 *
	 * <p>Long Shot and Siege refuse a True Shot or Snap Shot arrow: the base
	 * tree owns the one big shot, the epic tree buffs the stream. Without that
	 * refusal Snap Shot's x4.0 times Long Shot 2's x3.0 times Siege's x2.0 is
	 * x24 on one armour-bypassed arrow. {@link Tuning#DEADEYE_MAX_MULTIPLIER}
	 * is the second fence around the same failure.
	 */
	public static float shapeArrowHit(final ServerPlayer player, final LivingEntity victim,
			final AbstractArrow arrow, final float amount) {
		AttachmentTarget onArrow = (AttachmentTarget) arrow;

		if (!Boolean.TRUE.equals(onArrow.getAttached(ModAttachments.DEADEYE_ARROW))) {
			return amount;
		}

		Set<Integer> owned = NodePurchases.owned(player, SubTree.NEMESIS_MARKSMAN);
		boolean bigShot = Boolean.TRUE.equals(onArrow.getAttached(ModAttachments.TRUE_SHOT_ARROW));
		float multiplier = 1.0F;

		int longShot = NemesisMarksmanNodes.rank(SubTree.NEMESIS_MARKSMAN, owned,
				NemesisMarksmanNodes.Family.LONG_SHOT);
		Vec3 origin = onArrow.getAttached(ModAttachments.TRUE_SHOT_ORIGIN);

		if (longShot > 0 && !bigShot && origin != null) {
			double flown = Math.min(arrow.position().distanceTo(origin), Tuning.LONG_SHOT_CAP_BLOCKS);
			multiplier *= 1.0F + Tuning.LONG_SHOT_PER_BLOCK_PER_RANK * longShot * (float) flown;
		}

		if (!bigShot && Boolean.TRUE.equals(onArrow.getAttached(ModAttachments.DEADEYE_SIEGE_ARROW))) {
			multiplier *= Tuning.SIEGE_MULTIPLIER;
		}

		// Punch Through: Piercing Tips' compensation with the two-point clamp
		// removed — each armour point eats about 4%, so hand all of it back and
		// the shot lands as if the armour were not there. Nothing against an
		// unarmoured target, the same shape the base node has.
		if (NemesisMarksmanNodes.rank(SubTree.NEMESIS_MARKSMAN, owned,
				NemesisMarksmanNodes.Family.PUNCH_THROUGH) > 0) {
			float armor = (float) victim.getAttributeValue(Attributes.ARMOR);
			multiplier *= 1.0F + 0.04F * armor;
		}

		return amount * Math.min(multiplier, Tuning.DEADEYE_MAX_MULTIPLIER);
	}

	/**
	 * On the Wing: an arrow that hits hands back two seconds of Acrobatics.
	 * Gated on the stance like the rest of the tree — the epic tree adds no
	 * permanent numbers, everything it grants lives inside the window.
	 */
	public static void onArrowHit(final ServerPlayer player) {
		if (!isActive(player) || rank(player, NemesisMarksmanNodes.Family.ON_THE_WING) <= 0) {
			return;
		}

		AttachmentTarget target = (AttachmentTarget) player;
		Long readyAt = target.getAttached(ModAttachments.DISENGAGE_READY_AT);
		long now = player.level().getGameTime();

		if (readyAt != null && readyAt > now) {
			target.setAttached(ModAttachments.DISENGAGE_READY_AT,
					Math.max(now, readyAt - Tuning.ON_THE_WING_REFUND_TICKS));
		}
	}

	// ------------------------------------------------------------------
	// Evasion.
	// ------------------------------------------------------------------

	/** Whether projectiles pass straight through this player — Evasion bought
	 * and the stance holding. The second predicate on the same
	 * {@code canHitEntity} hook Incorporeal already uses. */
	public static boolean isEvading(final Player player) {
		return isActive(player) && rank(player, NemesisMarksmanNodes.Family.EVASION) > 0;
	}
}
