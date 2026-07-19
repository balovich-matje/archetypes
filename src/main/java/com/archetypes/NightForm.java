package com.archetypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Nemesis Shadow's night form: the Dark Ritual's ten-second channel, the
 * hour of vampirism it commits the player to, and every mechanic that hangs off
 * being transformed.
 *
 * <h2>The state machine</h2>
 * Three states, all derived from two synced game-tick stamps and nothing else:
 * <ul>
 *   <li><b>Mortal</b> — neither stamp set.</li>
 *   <li><b>Channelling</b> — {@code NIGHT_CHANNEL_END} set and in the future.
 *       {@link #beginRitual} enters it, {@link #interrupt} leaves it with no
 *       cooldown charged (author's spec: an interrupted ritual costs nothing),
 *       and any action other than moving interrupts — see
 *       {@link #interrupted}.</li>
 *   <li><b>Transformed</b> — {@code NIGHT_FORM_END} set and in the future. The
 *       form's length and its cooldown are ONE clock, so there is no way out
 *       before it lapses and no way to re-cast until it does.</li>
 * </ul>
 * The two states are mutually exclusive: {@link #complete} clears the channel
 * stamp as it writes the form stamp.
 *
 * <h2>The contract for renderers and HUDs</h2>
 * Everything a client needs is a synced attachment read through a static
 * predicate here, so no bespoke packet is ever required:
 * <table>
 *   <caption>The client-readable API</caption>
 *   <tr><th>Call</th><th>Scope</th><th>Meaning</th></tr>
 *   <tr><td>{@link #isActive}</td><td>every client</td>
 *       <td>this player is transformed right now</td></tr>
 *   <tr><td>{@link #remainingTicks}</td><td>every client</td>
 *       <td>ticks of vampirism left (0 when mortal)</td></tr>
 *   <tr><td>{@link #isChannelling}</td><td>every client</td>
 *       <td>a Dark Ritual is running — drives the 1st/3rd person animation</td></tr>
 *   <tr><td>{@link #channelProgress}</td><td>every client</td>
 *       <td>0.0 at the ritual's first tick to 1.0 at its last</td></tr>
 *   <tr><td>{@link #isSunlit}</td><td>every client</td>
 *       <td>standing in burning sunlight — drives the blinding overlay</td></tr>
 *   <tr><td>{@link #sensed} / {@link #sensedPlayers}</td><td>owner only</td>
 *       <td>Extra Sensory Perception's rosters, as entity ids</td></tr>
 *   <tr><td>{@link #empoweredNameKey}</td><td>anywhere</td>
 *       <td>the vampire name of an empowered Cutpurse active</td></tr>
 * </table>
 * The predicates take {@link Player}, not {@code ServerPlayer}, and are safe on
 * either side. Nothing here writes state off a client.
 *
 * <h2>Undead-ness</h2>
 * A transformed player is undead in the one way 26.2 actually models it:
 * {@code LivingEntity.isInvertedHealAndHarm} (see {@code LivingEntityMixin}),
 * which is the single question vanilla's Instant Health / Instant Damage effect
 * asks and the same one this mod's Priest tree and Aura of Radiance already ask.
 * Nothing special-cases a potion. Natural regeneration is halted separately, in
 * {@code FoodDataMixin}, because vanilla's food-driven heal never consults
 * undead-ness at all.
 */
public final class NightForm {
	private NightForm() {
	}

	// ------------------------------------------------------------------
	// Predicates — the client-readable face.
	// ------------------------------------------------------------------

	/** Whether this player is transformed right now. Safe on both sides. */
	public static boolean isActive(final Player player) {
		Long end = ((AttachmentTarget) player).getAttached(ModAttachments.NIGHT_FORM_END);
		return end != null && player.level().getGameTime() < end;
	}

	/** Ticks of night form left, or 0 when mortal. */
	public static int remainingTicks(final Player player) {
		Long end = ((AttachmentTarget) player).getAttached(ModAttachments.NIGHT_FORM_END);
		long now = player.level().getGameTime();
		return end == null || now >= end ? 0 : (int) Math.min(Integer.MAX_VALUE, end - now);
	}

	/** Whether a Dark Ritual channel is running on this player. */
	public static boolean isChannelling(final Player player) {
		Long end = ((AttachmentTarget) player).getAttached(ModAttachments.NIGHT_CHANNEL_END);
		return end != null && player.level().getGameTime() < end;
	}

	/** Ticks of channel left, or 0 when none is running. */
	public static int channelRemainingTicks(final Player player) {
		Long end = ((AttachmentTarget) player).getAttached(ModAttachments.NIGHT_CHANNEL_END);
		long now = player.level().getGameTime();
		return end == null || now >= end ? 0 : (int) (end - now);
	}

	/** The channel's progress, 0.0 at its first tick to 1.0 at its last — the
	 * number the ritual animation and any cast bar should ramp on. 0 when no
	 * channel is running. */
	public static float channelProgress(final Player player) {
		int left = channelRemainingTicks(player);
		return left <= 0 ? 0.0F
				: 1.0F - (float) left / Tuning.DARK_RITUAL_CHANNEL_TICKS;
	}

	/** Whether this transformed player is standing in sunlight strong enough to
	 * burn them — the flag behind the sketch's blinding on-screen effect. */
	public static boolean isSunlit(final Player player) {
		return Boolean.TRUE.equals(((AttachmentTarget) player).getAttached(ModAttachments.NIGHT_SUNLIT));
	}

	/** Extra Sensory Perception: entity ids of every living thing sensed in
	 * range, players included. Owner-synced; empty without the node. */
	public static List<Integer> sensed(final Player player) {
		List<Integer> ids = ((AttachmentTarget) player).getAttached(ModAttachments.NIGHT_SENSED);
		return ids == null ? List.of() : ids;
	}

	/** The subset of {@link #sensed} that is players — kept apart so the
	 * renderer can mark them out distinctly (author's spec). */
	public static List<Integer> sensedPlayers(final Player player) {
		List<Integer> ids = ((AttachmentTarget) player).getAttached(ModAttachments.NIGHT_SENSED_PLAYERS);
		return ids == null ? List.of() : ids;
	}

	/** Owned rank in a Nemesis Shadow family; 0 for anyone but the owner on a
	 * client, since ownership is target-synced. */
	public static int rank(final Player player, final NemesisShadowNodes.Family family) {
		return NemesisShadowNodes.rank(player, family);
	}

	/** Incorporeal is bought AND the form is up — the gate for knockback
	 * immunity and projectile pass-through. */
	public static boolean isIncorporeal(final Player player) {
		return isActive(player) && rank(player, NemesisShadowNodes.Family.INCORPOREAL) > 0;
	}

	/**
	 * The lang key of a Cutpurse active's empowered name while transformed, or
	 * null if the active is unchanged (Invisibility) or the player is mortal.
	 * The icon swap is the renderer's; this is the wording that goes with it.
	 */
	public static @Nullable String empoweredNameKey(final Player player, final SubTree tree,
			final Enum<?> family) {
		if (!isActive(player)) {
			return null;
		}

		if (tree == SubTree.MARKSMAN && family == MarksmanNodes.Family.TRUE_SHOT) {
			return "node.archetypes.marksman.true_shot.night";
		}

		if (tree == SubTree.ASSASSIN && family == AssassinNodes.Family.SHADOW_STEP) {
			return "node.archetypes.assassin.shadow_step.night";
		}

		return null;
	}

	// ------------------------------------------------------------------
	// The ritual.
	// ------------------------------------------------------------------

	/**
	 * The ability-7 press. Starts the channel, or cancels a running one — a
	 * deliberate cancel is just another interrupt and costs nothing. While
	 * transformed the press does nothing at all: the hour is the node's price.
	 */
	public static void beginRitual(final ServerPlayer player) {
		if (rank(player, NemesisShadowNodes.Family.DARK_RITUAL) <= 0 || isActive(player)) {
			return;
		}

		if (isChannelling(player)) {
			interrupt(player);
			return;
		}

		AttachmentTarget target = (AttachmentTarget) player;
		ServerLevel level = (ServerLevel) player.level();
		target.setAttached(ModAttachments.NIGHT_CHANNEL_END,
				level.getGameTime() + Tuning.DARK_RITUAL_CHANNEL_TICKS);
		target.setAttached(ModAttachments.NIGHT_CHANNEL_SLOT, player.getInventory().getSelectedSlot());
		target.setAttached(ModAttachments.NIGHT_CHANNEL_HURT, player.hurtTime);

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.5F);
	}

	/** Abandon a running channel. No cooldown is charged — that is the whole
	 * point of the author's note. Safe to call when nothing is running. */
	public static void interrupt(final ServerPlayer player) {
		AttachmentTarget target = (AttachmentTarget) player;

		if (target.getAttached(ModAttachments.NIGHT_CHANNEL_END) == null) {
			return;
		}

		clearChannel(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 0.5F);
	}

	/** The channel ran its course: the hour starts here. */
	private static void complete(final ServerPlayer player) {
		clearChannel(player);

		ServerLevel level = (ServerLevel) player.level();
		((AttachmentTarget) player).setAttached(ModAttachments.NIGHT_FORM_END,
				level.getGameTime() + Tuning.NIGHT_FORM_TICKS);

		// The one FX kept here rather than left to the renderer: the moment of
		// transformation is the state change, and a silent one reads as a bug.
		level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0, player.getZ(),
				40, 0.4, 0.8, 0.4, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.7F, 1.6F);
	}

	/**
	 * End the night form now. The ability key can never reach this — the hour
	 * is not cancellable — but the ticker calls it when the clock lapses and
	 * {@code ModAttachments.forgetNodes} calls it when the node is respecced
	 * away. Safe to call on a mortal player.
	 */
	public static void end(final ServerPlayer player) {
		AttachmentTarget target = (AttachmentTarget) player;

		if (target.getAttached(ModAttachments.NIGHT_FORM_END) == null) {
			return;
		}

		target.removeAttached(ModAttachments.NIGHT_FORM_END);
		target.removeAttached(ModAttachments.NIGHT_SUNLIT);
		target.removeAttached(ModAttachments.NIGHT_SENSED);
		target.removeAttached(ModAttachments.NIGHT_SENSED_PLAYERS);

		if (player.isAlive()) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.7F, 1.4F);
		}
	}

	private static void clearChannel(final ServerPlayer player) {
		AttachmentTarget target = (AttachmentTarget) player;
		target.removeAttached(ModAttachments.NIGHT_CHANNEL_END);
		target.removeAttached(ModAttachments.NIGHT_CHANNEL_SLOT);
		target.removeAttached(ModAttachments.NIGHT_CHANNEL_HURT);
	}

	// ------------------------------------------------------------------
	// The per-tick machine. Driven by NightFormTicker.
	// ------------------------------------------------------------------

	static void tick(final ServerPlayer player) {
		if (((AttachmentTarget) player).getAttached(ModAttachments.NIGHT_CHANNEL_END) != null) {
			tickChannel(player);
		}

		if (((AttachmentTarget) player).getAttached(ModAttachments.NIGHT_FORM_END) != null) {
			tickForm(player);
		}
	}

	private static void tickChannel(final ServerPlayer player) {
		if (!player.isAlive() || rank(player, NemesisShadowNodes.Family.DARK_RITUAL) <= 0) {
			clearChannel(player);
			return;
		}

		if (interrupted(player)) {
			interrupt(player);
			return;
		}

		if (!isChannelling(player)) {
			complete(player);
		}
	}

	/**
	 * Any action but moving ends the ritual: a swing, an item use (eating,
	 * drawing, blocking), a hit landing on you, or changing the held slot.
	 * Walking, sprinting, jumping and looking around are all fine.
	 *
	 * <p>Hits are detected as a RISE in hurtTime rather than as a non-zero one:
	 * vanilla counts hurtTime down from ten over the half-second after a hit, so
	 * a flat test would let the tail of a pre-ritual hit kill the channel.
	 */
	private static boolean interrupted(final ServerPlayer player) {
		AttachmentTarget target = (AttachmentTarget) player;

		Integer lastHurt = target.getAttached(ModAttachments.NIGHT_CHANNEL_HURT);
		target.setAttached(ModAttachments.NIGHT_CHANNEL_HURT, player.hurtTime);

		if (lastHurt != null && player.hurtTime > lastHurt) {
			return true;
		}

		Integer slot = target.getAttached(ModAttachments.NIGHT_CHANNEL_SLOT);

		if (slot != null && slot != player.getInventory().getSelectedSlot()) {
			return true;
		}

		// The swing and use tests wait out the grace window, or a press made in
		// the last frames of the swing that ended the fight would cancel itself.
		Long end = target.getAttached(ModAttachments.NIGHT_CHANNEL_END);
		long elapsed = end == null ? 0
				: Tuning.DARK_RITUAL_CHANNEL_TICKS - (end - player.level().getGameTime());

		return elapsed > Tuning.DARK_RITUAL_GRACE_TICKS
				&& (player.swinging || player.isUsingItem());
	}

	private static void tickForm(final ServerPlayer player) {
		if (!isActive(player) || rank(player, NemesisShadowNodes.Family.DARK_RITUAL) <= 0) {
			end(player);
			return;
		}

		Set<Integer> owned = NodePurchases.owned(player, SubTree.NEMESIS_SHADOW);
		ServerLevel level = (ServerLevel) player.level();

		// Hunger is pinned full and stays there; the halted regeneration is
		// FoodDataMixin's half of the same rule.
		player.getFoodData().setFoodLevel(20);

		sunlight(player, level);

		// Slow Falling is the form's own (the author's comment corrects the
		// sketch's "fall damage immunity"), so it rides Ghost Form rank 1.
		// Re-asserted and left to lapse, never removed — see ShadowTicker's
		// Night Stalker for why an explicit remove would eat a buried effect.
		if (NemesisShadowNodes.rank(SubTree.NEMESIS_SHADOW, owned,
				NemesisShadowNodes.Family.GHOST_FORM) > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,
					Tuning.NIGHT_FORM_EFFECT_TICKS, 0, true, false));
		}

		if (NemesisShadowNodes.rank(SubTree.NEMESIS_SHADOW, owned,
				NemesisShadowNodes.Family.NIGHT_EYES) > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
					Tuning.NIGHT_FORM_EFFECT_TICKS, 0, true, false));
		}

		if (NemesisShadowNodes.rank(SubTree.NEMESIS_SHADOW, owned,
				NemesisShadowNodes.Family.EXTRA_SENSORY_PERCEPTION) > 0) {
			senses(player, level);
		}
	}

	/**
	 * The undead sun-burn, ported honestly rather than approximated: this is
	 * {@code Mob.isSunBurnTick} plus {@code Mob.burnUndead}, whose bodies were
	 * read for exactly this (both are private on {@code Mob}, which a player is
	 * not). The same MONSTERS_BURN environment attribute, the same light-level
	 * roll, the same water/rain/powder-snow let-off, the same sky check, and a
	 * helmet still shields its wearer at the cost of its own durability.
	 *
	 * <p>The sunlit FLAG is set off the exposure alone, without the random
	 * roll: the on-screen effect has to be steady while the player stands in
	 * the open, not strobe with the ignition dice.
	 */
	@SuppressWarnings("deprecation")
	private static void sunlight(final ServerPlayer player, final ServerLevel level) {
		// Deprecated in 26.2 but still the exact call Mob.isSunBurnTick makes;
		// matching vanilla's burn is the whole point, so the warning is eaten
		// rather than the behaviour re-derived from a different light source.
		float brightness = player.getLightLevelDependentMagicValue();
		BlockPos eye = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
		boolean sheltered = player.isInWaterOrRain() || player.isInPowderSnow;
		boolean exposed = level.environmentAttributes()
				.getValue(EnvironmentAttributes.MONSTERS_BURN, player.position())
				&& brightness > 0.5F && !sheltered && level.canSeeSky(eye);

		AttachmentTarget target = (AttachmentTarget) player;

		if (exposed != isSunlit(player)) {
			if (exposed) {
				target.setAttached(ModAttachments.NIGHT_SUNLIT, true);
			} else {
				target.removeAttached(ModAttachments.NIGHT_SUNLIT);
			}
		}

		if (!exposed || player.getRandom().nextFloat() * 30.0F >= (brightness - 0.4F) * 2.0F) {
			return;
		}

		ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);

		if (helmet.isEmpty()) {
			player.igniteForSeconds(Tuning.NIGHT_FORM_SUN_BURN_SECONDS);
		} else if (helmet.isDamageableItem()) {
			helmet.hurtAndBreak(player.getRandom().nextInt(2), level, player,
					broken -> player.onEquippedItemBroken(broken, EquipmentSlot.HEAD));
		}
	}

	/** Extra Sensory Perception: rebuild the two rosters the renderer reads. */
	private static void senses(final ServerPlayer player, final ServerLevel level) {
		if (level.getGameTime() % Tuning.ESP_REFRESH_TICKS != 0) {
			return;
		}

		double radiusSq = Tuning.ESP_RADIUS * Tuning.ESP_RADIUS;
		List<Integer> all = new ArrayList<>();
		List<Integer> players = new ArrayList<>();

		// Hostile and passive alike (author's spec) — the only filter is being
		// alive, in range, and not the sensing player.
		for (LivingEntity creature : level.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(Tuning.ESP_RADIUS),
				living -> living.isAlive() && living != player && !living.isSpectator()
						&& living.distanceToSqr(player) <= radiusSq)) {
			all.add(creature.getId());

			if (creature instanceof Player) {
				players.add(creature.getId());
			}
		}

		AttachmentTarget target = (AttachmentTarget) player;
		target.setAttached(ModAttachments.NIGHT_SENSED, List.copyOf(all));
		target.setAttached(ModAttachments.NIGHT_SENSED_PLAYERS, List.copyOf(players));
	}

	// ------------------------------------------------------------------
	// Ghost Form's dash.
	// ------------------------------------------------------------------

	/**
	 * The sprint-while-sneaking dash: 2/4/6 blocks along the full look vector
	 * (up and down included — the sketch says "fly you", not "slide you").
	 * Server-validated like every other active; the client only reports the
	 * key edge.
	 */
	public static void dash(final ServerPlayer player) {
		int rank = rank(player, NemesisShadowNodes.Family.GHOST_FORM);

		if (!isActive(player) || rank <= 0) {
			return;
		}

		AttachmentTarget target = (AttachmentTarget) player;
		ServerLevel level = (ServerLevel) player.level();
		Long ready = target.getAttached(ModAttachments.NIGHT_DASH_READY_AT);

		if (ready != null && level.getGameTime() < ready) {
			return;
		}

		Vec3 look = player.getLookAngle();

		if (look.lengthSqr() < 1.0E-4) {
			return;
		}

		double impulse = rank * Tuning.GHOST_DASH_BLOCKS_PER_RANK * Tuning.RUSH_IMPULSE_PER_BLOCK;
		player.setDeltaMovement(player.getDeltaMovement().add(look.normalize().scale(impulse)));
		player.hurtMarked = true;
		player.resetFallDistance();
		target.setAttached(ModAttachments.NIGHT_DASH_READY_AT,
				level.getGameTime() + Tuning.GHOST_DASH_COOLDOWN_TICKS);

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.8F, 1.5F);
	}

	// ------------------------------------------------------------------
	// Kills and Feast.
	// ------------------------------------------------------------------

	/** A kill while transformed restores a quarter of the victim's maximum
	 * health. Called from the Cutpurse's death hook. */
	public static void onKill(final ServerPlayer player, final LivingEntity victim) {
		if (!isActive(player)) {
			return;
		}

		player.heal(victim.getMaxHealth() * Tuning.NIGHT_FORM_KILL_HEAL_SHARE);
	}

	/**
	 * One running Feast bleed. Held in a server-side list rather than on the
	 * victim: a bleed is four seconds long, so nothing about it is worth
	 * persisting, and walking a handful of live records once a second is far
	 * cheaper than scanning the world for marked entities.
	 */
	private static final class Bleed {
		private final ServerPlayer feeder;
		private final LivingEntity victim;
		private final float perPulse;
		private long endTick;
		/** Each bleed keeps its OWN beat rather than riding a global modulo, so
		 * a bleed opened on any tick still pays its full four pulses. */
		private long nextPulse;

		private Bleed(final ServerPlayer feeder, final LivingEntity victim, final float perPulse,
				final long now) {
			this.feeder = feeder;
			this.victim = victim;
			this.perPulse = perPulse;
			this.endTick = now + Tuning.FEAST_TICKS;
			this.nextPulse = now + Tuning.FEAST_PULSE_TICKS;
		}
	}

	private static final List<Bleed> BLEEDS = new ArrayList<>();

	/** Start (or refresh) the bleed on a victim this transformed player just
	 * hit. Refreshing rather than stacking keeps the ceiling at the advertised
	 * rate no matter how fast the attacks land. */
	public static void onHit(final ServerPlayer player, final LivingEntity victim) {
		int rank = rank(player, NemesisShadowNodes.Family.FEAST);

		if (bleeding || !isActive(player) || rank <= 0 || victim == player || !victim.isAlive()) {
			return;
		}

		long now = player.level().getGameTime();

		for (Bleed bleed : BLEEDS) {
			if (bleed.feeder == player && bleed.victim == victim) {
				bleed.endTick = now + Tuning.FEAST_TICKS;
				return;
			}
		}

		float perPulse = rank * Tuning.FEAST_HP_PER_SECOND_PER_RANK
				* Tuning.FEAST_PULSE_TICKS / 20.0F;
		BLEEDS.add(new Bleed(player, victim, perPulse, now));
	}

	/**
	 * True only for the instant a bleed pulse is resolving. The pulse is
	 * attributed to its feeder so a bleed kill still credits them — which makes
	 * it look exactly like one of that player's own attacks to {@link #onHit},
	 * and a bleed that refreshed itself would never end. Same guard idiom as
	 * {@code RadianceAura.isPulsing}.
	 */
	private static boolean bleeding;

	/** Resolve every running bleed. Called once a tick from the ticker. */
	static void tickBleeds() {
		if (BLEEDS.isEmpty()) {
			return;
		}

		BLEEDS.removeIf(bleed -> !bleed.victim.isAlive() || !bleed.feeder.isAlive()
				|| bleed.victim.level() != bleed.feeder.level()
				|| bleed.victim.level().getGameTime() >= bleed.endTick);

		for (Bleed bleed : BLEEDS) {
			ServerLevel level = (ServerLevel) bleed.victim.level();

			if (level.getGameTime() < bleed.nextPulse) {
				continue;
			}

			bleed.nextPulse = level.getGameTime() + Tuning.FEAST_PULSE_TICKS;
			bleeding = true;

			try {
				// Attributed to the feeder so a bleed kill still credits them,
				// and through the same indirect-magic source the aura uses so
				// it reads as a DoT rather than a swing.
				bleed.victim.hurtServer(level,
						level.damageSources().indirectMagic(bleed.feeder, bleed.feeder),
						bleed.perPulse);
			} finally {
				bleeding = false;
			}

			bleed.feeder.heal(bleed.perPulse);
			level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
					bleed.victim.getX(), bleed.victim.getY() + bleed.victim.getBbHeight() * 0.5,
					bleed.victim.getZ(), 4, 0.2, 0.2, 0.2, 0.0);
		}
	}
}
