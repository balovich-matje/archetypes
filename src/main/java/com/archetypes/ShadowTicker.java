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
	private static final Identifier BLOODRUSH_ID = Archetypes.id("bloodrush");

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

		// Swift Shadow: +20% a rank while unseen.
		apply(player.getAttribute(Attributes.MOVEMENT_SPEED), SWIFT_ID,
				invisible && ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.SWIFT_SHADOW) > 0,
				Tuning.SWIFT_SHADOW_PER_RANK
						* ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.SWIFT_SHADOW));

		// Bloodrush: the kill-window attack speed (stamped in AgilityCombat).
		Long rushUntil = target.getAttached(ModAttachments.BLOODRUSH_UNTIL);
		apply(player.getAttribute(Attributes.ATTACK_SPEED), BLOODRUSH_ID,
				rushUntil != null && now < rushUntil,
				Tuning.BLOODRUSH_PER_RANK
						* ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.BLOODRUSH));

		// Dark Mending: a heart every 8/6/4/2 seconds of invisibility.
		int mending = ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.DARK_MENDING);

		if (invisible && mending > 0 && player.getHealth() < player.getMaxHealth()) {
			int interval = (10 - 2 * mending) * 20;

			if (now % interval == 0) {
				player.heal(Tuning.DARK_MENDING_HEAL);
			}
		}

		// Stillness: an unmoving shadow decays slower — or not at all.
		int stillness = ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.STILLNESS);

		if (invisible && stillness > 0 && stationary(player)
				&& (stillness >= 2 || now % 2 == 0)) {
			MobEffectInstance invis = player.getEffect(MobEffects.INVISIBILITY);

			if (invis != null && invis.getDuration() > 1 && !invis.isInfiniteDuration()) {
				player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
						invis.getDuration() + 1, invis.getAmplifier()));
			}
		}

		// Night Eyes: held far above the 10-second mark, where vanilla's
		// night vision starts flickering; it fades out after the sneak.
		if (player.isCrouching()
				&& ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.NIGHT_EYES) > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
					Tuning.NIGHT_EYES_TICKS, 0, true, false));
		}

		// Umbral Sight: prey nearby is outlined while you sneak or hide.
		if ((player.isCrouching() || invisible) && now % 10 == 0
				&& ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.UMBRAL_SIGHT) > 0) {
			for (LivingEntity hostile : player.level().getEntitiesOfClass(LivingEntity.class,
					player.getBoundingBox().inflate(Tuning.UMBRAL_SIGHT_RADIUS),
					living -> living instanceof Monster && living.isAlive())) {
				hostile.addEffect(new MobEffectInstance(MobEffects.GLOWING, 25, 0, true, false));
			}
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

	private static boolean stationary(final ServerPlayer player) {
		double dx = player.getX() - player.xOld;
		double dy = player.getY() - player.yOld;
		double dz = player.getZ() - player.zOld;
		return dx * dx + dy * dy + dz * dz < 1.0E-5;
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
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else if (!should && has) {
			attribute.removeModifier(id);
		} else if (should) {
			// Rank may have changed while active; cheap to re-assert.
			AttributeModifier current = attribute.getModifier(id);

			if (current == null || current.amount() != value) {
				attribute.removeModifier(id);
				attribute.addTransientModifier(new AttributeModifier(id, value,
						AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
	}

	/** Shared by Invisibility, Predator's renewals and Last Shadow: how long
	 * this player's dark lasts. */
	public static int invisDuration(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.SHADOW);
		return Tuning.INVIS_TICKS
				+ (ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.UMBRAL_MASTERY) > 0
						? Tuning.UMBRAL_MASTERY_BONUS_TICKS : 0);
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

	/** Reaper + Bloodrush, called from the kill hook. */
	public static void onKill(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.SHADOW);

		if (ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.BLOODRUSH) > 0) {
			((AttachmentTarget) player).setAttached(ModAttachments.BLOODRUSH_UNTIL,
					player.level().getGameTime() + Tuning.BLOODRUSH_TICKS);
		}

		if (player.hasEffect(MobEffects.INVISIBILITY)
				&& ShadowNodes.rank(SubTree.SHADOW, owned, ShadowNodes.Family.REAPER) > 0) {
			player.heal(Tuning.REAPER_HEAL);
			((ServerLevel) player.level()).sendParticles(
					net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
					player.getX(), player.getY() + 1.0, player.getZ(), 5, 0.3, 0.4, 0.3, 0.0);
		}
	}
}
