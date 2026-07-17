package com.archetypes;

import java.util.Set;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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

	private ShadowTicker() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
	}

	private static void tick(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.SHADOW);
		AttachmentTarget target = (AttachmentTarget) player;
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

		if (hideArmor != Boolean.TRUE.equals(target.getAttached(ModAttachments.ARMOR_HIDDEN))) {
			if (hideArmor) {
				target.setAttached(ModAttachments.ARMOR_HIDDEN, true);
			} else {
				target.removeAttached(ModAttachments.ARMOR_HIDDEN);
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
			player.addEffect(new MobEffectInstance(
					MobEffects.STRENGTH, Tuning.BLOODRUSH_TICKS, bloodrush - 1));
		}

		if (ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.REAPER) > 0) {
			player.heal(Tuning.REAPER_HEAL);
			((ServerLevel) player.level()).sendParticles(
					net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
					player.getX(), player.getY() + 1.0, player.getZ(), 5, 0.3, 0.4, 0.3, 0.0);
		}
	}
}
