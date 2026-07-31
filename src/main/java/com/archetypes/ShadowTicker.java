package com.archetypes;

import java.util.Set;

import com.archetypes.platform.ArchetypeStore;

// STAGE 6 — the loader-event helpers live in `com.archetypes.platform`, the one package
// allowed to name loader API (conventions §5g). Only this import and the registration line
// below fork; the tick body is one implementation on all seven nodes.
//? if fabric {
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
//?} elif neoforge {
/*import com.archetypes.platform.NeoForgeEvents;
*///?} elif forge {
/*import com.archetypes.platform.ForgeEvents;
*///?}
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
		// Registration only; the body below is shared. A loader helper fires its consumer ONCE
		// per server tick, at the END of it, with the `MinecraftServer` — the END_SERVER_TICK
		// contract, which is what R-20 says a re-rooted event has to reproduce rather than
		// merely fire somewhere plausible.
		//? if fabric {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
		//?} elif neoforge {
		/*NeoForgeEvents.endServerTick(server -> {
		*///?} elif forge {
		/*ForgeEvents.endServerTick(server -> {
		*///?}
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
		//
		// STAGE 5, AND THIS IS AN R-20 RE-ROOT RATHER THAN A RENAME. `Attributes
		// .SNEAKING_SPEED` is `>=1.21`; below it the sneak factor is not an attribute at
		// all. It is computed CLIENT-side, in `LocalPlayer.aiStep`, as
		// `Mth.clamp(0.3F + EnchantmentHelper.getSneakingSpeedBonus(this), 0, 1)` fed
		// straight into `Input.tick(boolean, float)` — measured in the 1.20.1 mojmap jar,
		// offsets 117-139. That expression IS the modern attribute's whole contract,
		// clamp included, so the node moves onto it: `client/mixin/LocalPlayerMixin`
		// carries the legacy arm, reading the same rank off the same synced purchases.
		// Nothing server-side can host it — the server never computes a sneak factor.
		//? if >=1.21 {
		apply(player.getAttribute(Attributes.SNEAKING_SPEED), SWIFT_ID,
				ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.SWIFT_SHADOW) > 0,
				Tuning.SWIFT_SHADOW_SNEAK_REFUND_PER_RANK
						* ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.SWIFT_SHADOW));
		//?}

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
			// The one MobEffects rename that cannot ride the controller's replacement
			// rule: `JUMP` is a PREFIX of `JUMP_BOOST`, so a directional textual rule
			// would rewrite the modern spelling into itself twice over on every node at
			// or above the boundary. See stonecutter.gradle.kts.
			//? if >=1.21.2 {
			player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, Tuning.NIGHT_STALKER_TICKS, 1, true, false));
			//?} else {
			/*player.addEffect(new MobEffectInstance(MobEffects.JUMP, Tuning.NIGHT_STALKER_TICKS, 1, true, false));
			*///?}
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
		// 26.x moved the day/night clock behind `WorldClock` holders: `getDayTime()` is
		// gone and `getOverworldClockTime()` is the reader that still means "the overworld's
		// clock, whatever dimension you are standing in". Below 26.1 `getDayTime()` IS that
		// reader — `Level.getDayTime()` returns `levelData.getDayTime()`, which the server
		// keeps on the overworld's value for every dimension. Same number, same intent.
		//? if >=26.1 {
		long t = level.getOverworldClockTime() % 24000L;
		//?} else {
		/*long t = level.getDayTime() % 24000L;
		*///?}
		return t >= 13000L && t < 23000L;
	}

	/** Keep a transient modifier in step with whether it should exist. */
	private static void apply(final AttributeInstance attribute, final Identifier id,
			final boolean should, final double value) {
		if (attribute == null) {
			return;
		}

		//? if >=1.21 {
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
		//?} else {
		/*boolean has = LegacyAttributes.has(attribute, id);

		if (should && !has) {
			attribute.addTransientModifier(LegacyAttributes.modifier(id, value,
					AttributeModifier.Operation.ADD_VALUE));
		} else if (!should && has) {
			LegacyAttributes.remove(attribute, id);
		} else if (should) {
			AttributeModifier current = LegacyAttributes.get(attribute, id);

			if (current == null || current.getAmount() != value) {
				LegacyAttributes.remove(attribute, id);
				attribute.addTransientModifier(LegacyAttributes.modifier(id, value,
						AttributeModifier.Operation.ADD_VALUE));
			}
		}
		*///?}
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
		/*? if >=1.21 {*/java.util.List<Holder<net.minecraft.world.effect.MobEffect>> harmful = new java.util.ArrayList<>();
		/*?} else *///java.util.List<net.minecraft.world.effect.MobEffect> harmful = new java.util.ArrayList<>();

		for (var active : player.getActiveEffectsMap().keySet()) {
			/*? if >=1.21 {*/if (active.value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
			/*?} else *///if (active.getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
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
