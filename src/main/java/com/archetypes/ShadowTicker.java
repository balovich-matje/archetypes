package com.archetypes;

import java.util.Set;

import com.archetypes.platform.ArchetypeStore;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;

/**
 * The Shadow tree's steady-state passives, once per tick per player: the
 * invisibility buffs (speed, mending, stillness), the sneaking senses, the
 * killing-spree window, and the ghost-armor flag the client renderers read.
 */
public final class ShadowTicker {
	private static final Identifier SWIFT_ID = Archetypes.id("swift_shadow");

	/**
	 * Bloodrush's window: player uuid to the game tick it closes.
	 *
	 * <p>A plain static map rather than an attachment, and the reason is what
	 * the window is FOR: it is read once, by {@code archetypes$daggerDamage} on
	 * the server thread, in the same tick loop that writes it, and no client
	 * renders it. It is transient by intent — a killing spree does not survive a
	 * restart — and {@link #initialize} drops closed entries every tick, so it
	 * is bounded by the number of players currently mid-spree.
	 */
	private static final java.util.Map<java.util.UUID, Long> BLOODRUSH_UNTIL =
			new java.util.HashMap<>();

	private ShadowTicker() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long now = server.overworld().getGameTime();
			BLOODRUSH_UNTIL.values().removeIf(until -> until <= now);

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
	}

	/**
	 * Bloodrush's term in the ambush box: its per-rank value while the window a
	 * kill from the dark opened is still open, zero otherwise.
	 *
	 * <p>Deliberately NOT gated on still being invisible. The kill is what the
	 * node is about — Predator refreshes the dark on a kill anyway, so an
	 * invisibility test would either be redundant or would silently delete the
	 * node for a Shadow who has not bought Predator.
	 */
	public static float bloodrushBonus(final ServerPlayer player) {
		Long until = BLOODRUSH_UNTIL.get(player.getUUID());

		if (until == null || player.level().getGameTime() >= until) {
			return 0.0F;
		}

		return Tuning.BLOODRUSH_PER_RANK * ShadowNodes.rank(SubTree.SHADOW,
				NodePurchases.owned(player, SubTree.SHADOW), ShadowNodes.Family.BLOODRUSH);
	}

	private static void tick(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.SHADOW);
		final Entity target = player;
		boolean invisible = player.hasEffect(MobEffects.INVISIBILITY);
		long now = player.level().getGameTime();

		// Swift Shadow: the sneak penalty refunded — half, then all of it. A
		// flat ADD_VALUE onto SNEAKING_SPEED's 0.3 base, so rank 2 (+0.7) lands
		// at 1.0 — sneaking at full walking speed, active whenever owned.
		apply(player.getAttribute(Attributes.SNEAKING_SPEED), SWIFT_ID,
				ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.SWIFT_SHADOW) > 0,
				Tuning.SWIFT_SHADOW_SNEAK_REFUND_PER_RANK
						* ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.SWIFT_SHADOW));

		// Dark Mending: a heart every 8/6/4/2 seconds of invisibility.
		int mending = ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.DARK_MENDING);

		if (invisible && mending > 0 && player.getHealth() < player.getMaxHealth()) {
			int interval = (10 - 2 * mending) * 20;

			if (now % interval == 0) {
				player.heal(Tuning.DARK_MENDING_HEAL);
			}
		}

		// Umbral Sight: prey nearby is outlined while you sneak — 8 blocks at
		// rank one, 16 at rank two.
		int sight = ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.UMBRAL_SIGHT);

		if (sight > 0 && player.isCrouching() && now % 10 == 0) {
			for (LivingEntity hostile : player.level().getEntitiesOfClass(LivingEntity.class,
					player.getBoundingBox().inflate(Tuning.UMBRAL_SIGHT_RADIUS * sight),
					living -> living instanceof Monster && living.isAlive())) {
				hostile.addEffect(new MobEffectInstance(MobEffects.GLOWING, 25, 0, true, false));
			}
		}

		// First Strike is no longer asserted here, and nothing replaces it in
		// this file. It was Strength I/II for as long as the dark held, and
		// before that a damage multiplier on the hurtServer funnel; it is now a
		// SUMMAND in the ambush box (archetypes$daggerDamage), gated on the same
		// invisibility this ticker used to test.
		//
		// The Strength form was the worse of the three, not the better:
		// MobEffects.STRENGTH is +3.0 ADD_VALUE per level, so rank 2 was +6.0 on
		// a weapon whose entire ATTACK_DAMAGE is 4.8, and it sat BELOW the box —
		// which then multiplied it by up to 7.55. A flat term underneath a
		// multiplier is exactly the shape the box was built to abolish, and it
		// alone was eating ~44% of the PvE damage budget. See
		// Tuning.FIRST_STRIKE_PER_RANK.

		// Night Stalker: invisible under a night sky, you move like a hunter —
		// Jump Boost II and Slow Falling. Re-asserted each tick while the hunt
		// holds, then simply left to lapse (never removeEffect): the short
		// duration makes teardown near-instant, and letting it EXPIRE lets
		// vanilla restore any beacon/potion effect ours was layered over — an
		// explicit remove would discard that buried effect with it.
		boolean nightStalker = ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.NIGHT_STALKER) > 0
				&& invisible && isNight(player.level());

		if (nightStalker) {
			player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, Tuning.NIGHT_STALKER_TICKS, 1, true, false));
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,
					Tuning.NIGHT_STALKER_TICKS, 0, true, false));
		}

		// Ghost Armor: a flag every client's renderer reads (see the avatar
		// renderer mixin) — armor pieces vanish with their wearer.
		boolean hideArmor = invisible
				&& ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.GHOST_ARMOR) > 0;

		if (hideArmor != Boolean.TRUE.equals(ArchetypeStore.INSTANCE.get(target, ModState.ARMOR_HIDDEN))) {
			if (hideArmor) {
				ArchetypeStore.INSTANCE.set(target, ModState.ARMOR_HIDDEN, true);
			} else {
				ArchetypeStore.INSTANCE.remove(target, ModState.ARMOR_HIDDEN);
			}
		}
	}

	/** Overworld clock says it's night — monsters-spawn range, any dimension. */
	private static boolean isNight(final net.minecraft.world.level.Level level) {
		long t = level.getOverworldClockTime() % 24000L;
		return t >= 13000L && t < 23000L;
	}

	/** Keep a transient modifier in step with whether it should exist. */
	private static void apply(final AttributeInstance attribute, final Identifier id,
			final boolean should, final double value) {
		if (attribute == null) {
			return;
		}

		boolean has = attribute.hasModifier(id);

		if (should && !has) {
			attribute.addTransientModifier(new AttributeModifier(id, value,
					AttributeModifier.Operation.ADD_VALUE));
		} else if (!should && has) {
			attribute.removeModifier(id);
		} else if (should) {
			// Rank may have changed while active; cheap to re-assert.
			AttributeModifier current = attribute.getModifier(id);

			if (current == null || current.amount() != value) {
				attribute.removeModifier(id);
				attribute.addTransientModifier(new AttributeModifier(id, value,
						AttributeModifier.Operation.ADD_VALUE));
			}
		}
	}

	/** Shared by Invisibility, Predator's renewals and Last Shadow: how long
	 * this player's dark lasts. Stillness stretches it 50% a rank. */
	public static int invisDuration(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.SHADOW);
		return Math.round(Tuning.INVIS_TICKS * (1.0F + Tuning.STILLNESS_DURATION_PER_RANK
				* ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.STILLNESS)));
	}

	/** Cleansing Veil and Last Shadow both scrub the harmful effects off. */
	public static void cleanse(final ServerPlayer player) {
		java.util.List<Holder<net.minecraft.world.effect.MobEffect>> harmful = new java.util.ArrayList<>();

		for (var active : player.getActiveEffectsMap().keySet()) {
			if (active.value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
				harmful.add(active);
			}
		}

		harmful.forEach(player::removeEffect);
	}

	/** Bloodrush and Reaper, both keyed on killing from inside the dark;
	 * called from the kill hook. */
	public static void onKill(final ServerPlayer player) {
		if (!player.hasEffect(MobEffects.INVISIBILITY)) {
			return;
		}

		Set<Integer> owned = NodePurchases.owned(player, SubTree.SHADOW);
		int bloodrush = ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.BLOODRUSH);

		if (bloodrush > 0) {
			// A stamp, not a Strength grant. Bloodrush was
			// addEffect(STRENGTH, 80, rank - 1) — the identical construct to the
			// one First Strike just lost, on the identical arc, and it broke the
			// ambush box's declared ceilings the same way: Strength II is +6.0
			// on a 4.8 dagger, so the amount entering the funnel became 13.8
			// rather than 7.8 and the crouched opener reached 320 raw, which
			// one-shots the 300 HP Wither the PvE window exists to protect.
			// The node now pays as a summand inside the box (Tuning.
			// BLOODRUSH_PER_RANK), which is worth its face value instead of its
			// face value times everything else.
			BLOODRUSH_UNTIL.put(player.getUUID(),
					player.level().getGameTime() + Tuning.BLOODRUSH_TICKS);
		}

		if (ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.REAPER) > 0) {
			player.heal(Tuning.REAPER_HEAL);
			((ServerLevel) player.level()).sendParticles(
					net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
					player.getX(), player.getY() + 1.0, player.getZ(), 5, 0.3, 0.4, 0.3, 0.0);
		}
	}
}
