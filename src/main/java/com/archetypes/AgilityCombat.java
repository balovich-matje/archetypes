package com.archetypes;

import com.archetypes.platform.ArchetypeStore;

// STAGE 6 — the loader-event helpers live in `com.archetypes.platform`, the one package
// allowed to name loader API (conventions §5g). Only these imports and the four registration
// lines below fork; every lambda body is one implementation on all seven nodes.
//? if fabric {
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
//?} elif neoforge {
/*import com.archetypes.platform.NeoForgeEvents;
*///?} elif forge {
/*import com.archetypes.platform.ForgeEvents;
*///?}
import net.minecraft.world.entity.Entity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/**
 * The Cutpurse's event hooks: True Shot empowerment on arrow spawn, the two
 * kill-triggered capstones, and Last Shadow's cheat-death.
 */
public final class AgilityCombat {
	private AgilityCombat() {
	}

	public static void initialize() {
		// A newly-spawned arrow from an armed player picks up its True Shot.
		// ENTITY_LOAD also fires for chunk-loaded and dimension-hopped
		// entities, so only a genuinely fresh arrow (age 0) qualifies.
		// A loader helper fires once per entity added to a SERVER level. The `tickCount > 0`
		// filter below is what makes a chunk-loaded or dimension-hopped arrow not qualify, and
		// it stays in the shared body — a helper that fired only for genuinely new entities
		// would be a DIFFERENT event and the filter would silently stop meaning anything.
		//? if fabric {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
		//?} elif neoforge {
		/*NeoForgeEvents.entityLoad((entity, level) -> {
		*///?} elif forge {
		/*ForgeEvents.entityLoad((entity, level) -> {
		*///?}
			if (!(entity instanceof AbstractArrow arrow) || arrow.tickCount > 0
					|| !(arrow.getOwner() instanceof ServerPlayer player)) {
				return;
			}

			MarksmanCombat.onArrowSpawn(player, arrow);

			final Entity target = player;

			if (!Boolean.TRUE.equals(ArchetypeStore.INSTANCE.get(target, ModState.TRUE_SHOT_ARMED))) {
				return;
			}

			ArchetypeStore.INSTANCE.remove(target, ModState.TRUE_SHOT_ARMED);

			boolean homing = MarksmanNodes.rank(SubTree.MARKSMAN,
					NodePurchases.owned(player, SubTree.MARKSMAN), MarksmanNodes.Family.SEEKER_ARROW) > 0;
			AgilityActives.empower(arrow,
					homing ? Tuning.TRUE_SHOT_HOMING_MULTIPLIER : Tuning.TRUE_SHOT_MULTIPLIER, homing);
			AgilityActives.markTrueShot(arrow);

			// The Seeker Arrow aims itself: whatever the player was pointing
			// at, the shot leaves toward the nearest visible hostile. Flight
			// homing (the arrow mixin) does the rest; non-hostiles are ghosts
			// to it — canHitEntity waves them through.
			if (homing) {
				LivingEntity quarry = null;
				double best = Double.MAX_VALUE;

				for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
						player.getBoundingBox().inflate(Tuning.SEEKER_AIM_RANGE),
						living -> living instanceof Enemy && living.isAlive()
								&& !living.isSpectator() && player.hasLineOfSight(living))) {
					double distance = candidate.distanceToSqr(player);

					if (distance < best) {
						best = distance;
						quarry = candidate;
					}
				}

				if (quarry != null) {
					double speed = arrow.getDeltaMovement().length();
					arrow.setDeltaMovement(quarry.getBoundingBox().getCenter()
							.subtract(arrow.position()).normalize().scale(speed));
					arrow.hurtMarked = true;
				}
			}
		});

		// A mark dying is its own event and not only a player's kill: the
		// cooldown clears whoever landed the blow, and Death's Head and
		// Contagion fire either way. Registered separately from the capstone
		// hook below because that one refuses every non-player kill.
		// Registration only; the body is shared. Skill Proficiencies has no AFTER_DEATH at all,
		// so this contract is written down rather than inherited: fire ONCE per entity death,
		// server-side, AFTER the death is final, with the entity and the `DamageSource`. Both
		// loaders' natural host is `LivingDeathEvent` — the same event their `allowDeath` uses —
		// so the helper has to distinguish "post" from "veto" and must not fire the post arm when
		// the death was cancelled.
		//? if fabric {
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) ->
				DeathMark.onDeath(victim, source.getEntity()));
		//?} elif neoforge {
		/*NeoForgeEvents.afterDeath((victim, source) ->
				DeathMark.onDeath(victim, source.getEntity()));
		*///?} elif forge {
		/*ForgeEvents.afterDeath((victim, source) ->
				DeathMark.onDeath(victim, source.getEntity()));
		*///?}

		// Kills feed two capstones: Predator refreshes a running invisibility,
		// Momentum hands Shadow Step straight back.
		// Second AFTER_DEATH listener, registered separately from the one above on purpose —
		// see its comment. A loader helper must therefore support MORE THAN ONE listener and
		// keep them in registration order.
		//? if fabric {
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
		//?} elif neoforge {
		/*NeoForgeEvents.afterDeath((victim, source) -> {
		*///?} elif forge {
		/*ForgeEvents.afterDeath((victim, source) -> {
		*///?}
			if (!(source.getEntity() instanceof ServerPlayer player)) {
				return;
			}

			final Entity target = player;

			if (source.getDirectEntity() instanceof AbstractArrow arrow) {
				MarksmanCombat.onArrowKill(player, arrow);
			}

			ShadowTicker.onKill(player);
			// The night form feeds on what it kills: a quarter of the victim's
			// maximum health, no node beyond the ritual required.
			NightForm.onKill(player, victim);

			if (player.hasEffect(MobEffects.INVISIBILITY)
					&& ShadowNodes.rank(SubTree.SHADOW, NodePurchases.owned(player, SubTree.SHADOW),
							ShadowNodes.Family.PREDATOR) > 0) {
				player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
						ShadowTicker.invisDuration(player)));
			}

			if (AssassinNodes.rank(SubTree.ASSASSIN, NodePurchases.owned(player, SubTree.ASSASSIN),
					AssassinNodes.Family.MOMENTUM) > 0) {
				ArchetypeStore.INSTANCE.remove(target, ModState.SHADOW_STEP_READY_AT);
			}
		});

		// Last Shadow: the death that wasn't. Cleanse, two seconds of grace
		// (see the hurtServer mixin), vanish — then both this and the invis
		// active share one long cooldown.
		// THE R-20 TRAP DESIGN §3.4 NAMES: returning false means THE ENTITY SURVIVES AT ITS
		// CURRENT HEALTH, not that the damage was voided. This is Last Shadow — the cheat-death
		// — so the body below heals and cleanses before it returns false, and it depends on
		// that meaning exactly. A helper that mapped false onto "cancel the damage" would
		// hand out an immortality bug rather than a lost proc.
		//? if fabric {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
		//?} elif neoforge {
		/*NeoForgeEvents.allowDeath((entity, source, amount) -> {
		*///?} elif forge {
		/*ForgeEvents.allowDeath((entity, source, amount) -> {
		*///?}
			if (!(entity instanceof ServerPlayer player)) {
				return true;
			}

			final Entity target = player;
			long now = player.level().getGameTime();
			Long ready = ArchetypeStore.INSTANCE.get(target, ModState.CHEAT_DEATH_READY_AT);

			if ((ready != null && now < ready)
					|| ShadowNodes.rank(SubTree.SHADOW, NodePurchases.owned(player, SubTree.SHADOW),
							ShadowNodes.Family.LAST_SHADOW) <= 0) {
				return true;
			}

			player.setHealth(1.0F);
			ShadowTicker.cleanse(player);

			ArchetypeStore.INSTANCE.set(target, ModState.IMMUNE_UNTIL, now + Tuning.CHEAT_DEATH_IMMUNE_TICKS);
			player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
					ShadowTicker.invisDuration(player)));
			ArchetypeStore.INSTANCE.set(target, ModState.INVIS_READY_AT, now + Tuning.CHEAT_DEATH_COOLDOWN_TICKS);
			ArchetypeStore.INSTANCE.set(target, ModState.CHEAT_DEATH_READY_AT, now + Tuning.CHEAT_DEATH_COOLDOWN_TICKS);

			ServerLevel level = (ServerLevel) player.level();
			level.sendParticles(ParticleTypes.LARGE_SMOKE,
					player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.7, 0.4, 0.05);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.4F);
			return false;
		});
	}
}
