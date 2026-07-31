package com.archetypes.mixin;

import com.archetypes.ColossusProtector;
import com.archetypes.MagicArmaments;
import com.archetypes.ModItems;
import com.archetypes.SkillPoints;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
	/**
	 * Shadow every XP award into the archetype's bank.
	 *
	 * <p>Injected rather than wrapped because we do not touch the amount: the
	 * player keeps all of their experience and the archetype banks a copy, so
	 * levelling the tree never costs an enchant.
	 */
	@Inject(method = "giveExperiencePoints(I)V", at = @At("TAIL"))
	private void archetypes$bankExperience(final int amount, final CallbackInfo ci) {
		archetypes$bankExperienceImpl(amount);
	}

	/** Shared implementation of {@link #archetypes$bankExperience}. */
	@Unique
	private void archetypes$bankExperienceImpl(final int amount) {
		Player player = (Player) (Object) this;

		if (!player.level().isClientSide()) {
			SkillPoints.bank(player, amount);
		}
	}

	/**
	 * Well Fed: a bar that can hold more must also be fillable past 20.
	 *
	 * <p>{@code canEat} rather than {@code FoodData.needsFood} because
	 * {@code FoodData} does not know whose it is, and because this is the gate
	 * both sides consult — {@code Consumable.canConsume} refuses to start the
	 * meal on the client too, so a client that still thought 20 was full would
	 * never send the use at all.
	 */
	@Inject(method = "canEat(Z)Z", at = @At("HEAD"), cancellable = true)
	private void archetypes$bankedHungerIsEdible(final boolean canAlwaysEat,
			final CallbackInfoReturnable<Boolean> cir) {
		archetypes$bankedHungerIsEdibleImpl(cir);
	}

	/** Shared implementation of {@link #archetypes$bankedHungerIsEdible}. */
	@Unique
	private void archetypes$bankedHungerIsEdibleImpl(final CallbackInfoReturnable<Boolean> cir) {
		Player player = (Player) (Object) this;

		if (player.getFoodData().getFoodLevel() < ColossusProtector.hungerCeiling(player)) {
			cir.setReturnValue(true);
		}
	}

	/**
	 * A dagger is a single-target weapon, so it never cleaves.
	 *
	 * <p>Vanilla decides a swing sweeps in exactly one place:
	 * {@code Player.isSweepAttack} returns true when the swing was full
	 * strength, was neither a crit nor a sprint-knockback, the player is on the
	 * ground and moving under {@code getSpeed() * 2.5} — and the MAIN HAND item
	 * {@code is(ItemTags.SWORDS)}. That last clause is the whole gate: the
	 * Sweeping Edge enchantment is not consulted here at all, it only feeds
	 * {@code Attributes.SWEEPING_DAMAGE_RATIO}, which is read later inside
	 * {@code doSweepAttack} to scale damage that by then has already been
	 * decided upon. Our daggers deliberately sit in {@code ItemTags.SWORDS}
	 * (see {@link ModItems#isSword}), which is why they sweep today.
	 *
	 * <p>Returning false here — rather than cancelling {@code doSweepAttack} —
	 * is what kills every part of the cleave at once, because that one boolean
	 * is threaded through {@code attack}: it gates the {@code doSweepAttack}
	 * call, and that method is the sole source of the extra damage to
	 * neighbours, of {@code SoundEvents.PLAYER_ATTACK_SWEEP}, and of the
	 * {@code ParticleTypes.SWEEP_ATTACK} flash (all three live in its body; the
	 * client renders no sweep of its own). Suppressing only the call would
	 * leave the swing SILENT instead: {@code attackVisualEffects} plays the
	 * normal strong/weak hit sound only when the sweep flag is false, so the
	 * flag has to be false for the dagger to keep its ordinary thud.
	 *
	 * <p>Main hand, not {@code getWeaponItem()}, to mirror the very check being
	 * replaced. This also covers Shadow Step's scripted blow, which reaches its
	 * victim through {@code Player.attack} with a forced full-strength ticker
	 * (see {@code AgilityActives.strike}) and would otherwise sweep on landing.
	 */
	@Inject(method = "isSweepAttack(ZZZ)Z", at = @At("HEAD"), cancellable = true)
	private void archetypes$daggersNeverSweep(final boolean fullStrengthAttack,
			final boolean criticalAttack, final boolean knockbackAttack,
			final CallbackInfoReturnable<Boolean> cir) {
		archetypes$daggersNeverSweepImpl(cir);
	}

	/** Shared implementation of {@link #archetypes$daggersNeverSweep}. */
	@Unique
	private void archetypes$daggersNeverSweepImpl(final CallbackInfoReturnable<Boolean> cir) {
		Player player = (Player) (Object) this;

		if (ModItems.isDagger(player.getMainHandItem())) {
			cir.setReturnValue(false);
		}
	}

	/**
	 * Mark the one path a real swing takes, so the Slayer's on-hit passives can
	 * tell a swing from everything else a player's damage source can be — see
	 * {@link com.archetypes.MeleeSwing} for what that was costing.
	 *
	 * <p>Wrapped rather than a HEAD/RETURN pair, for the reason the Barbarian's
	 * healing flag learned the hard way: an exception anywhere under this call
	 * would leave the flag standing, and every later hit on the server would
	 * read as a swing until something else overwrote it. The {@code finally}
	 * makes that impossible.
	 */
	@com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "attack(Lnet/minecraft/world/entity/Entity;)V")
	private void archetypes$markSwing(final net.minecraft.world.entity.Entity target,
			final com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
		archetypes$markSwingImpl(target, original);
	}

	/** Shared implementation of {@link #archetypes$markSwing}. */
	@Unique
	private void archetypes$markSwingImpl(final net.minecraft.world.entity.Entity target, final com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
		net.minecraft.world.entity.Entity previous =
				com.archetypes.MeleeSwing.begin((Player) (Object) this);

		try {
			original.call(target);
		} finally {
			com.archetypes.MeleeSwing.end(previous);
		}
	}

	// ---- Legacy-only knockback-source stash (see com.archetypes.KnockbackSource). ----
	//
	// The melee EXTRA knockback — the sprint bonus and the Knockback enchant — reaches
	// LivingEntity.knockback through Player.causeExtraKnockback, which carries the source
	// on 26.2 and does not on 26.1. Without this, a dagger's and a Clinch fist's extra
	// shove would keep their full strength below 26.2 while the base shove was reduced:
	// a silent balance divergence, which is exactly what the census exists to prevent.
	//
	// `damageSource` is the ONLY DamageSource local in Player.attack on 26.1 (slot 4, live
	// from bytecode offset 44 to 351, and the call sits at 276), so @Local resolves it
	// without an ordinal. Touches no balance itself: set, call unchanged, restore.
	//? if <26.2 {
	/*@com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
			method = "attack(Lnet/minecraft/world/entity/Entity;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Player;causeExtraKnockback(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/phys/Vec3;)V"))
	private void archetypes$stashExtraKnockback(final Player self,
			final net.minecraft.world.entity.Entity target, final float strength,
			final net.minecraft.world.phys.Vec3 direction,
			final com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original,
			@com.llamalad7.mixinextras.sugar.Local final net.minecraft.world.damagesource.DamageSource source) {
		net.minecraft.world.damagesource.DamageSource previous =
				com.archetypes.KnockbackSource.push(source);

		try {
			original.call(self, target, strength, direction);
		} finally {
			com.archetypes.KnockbackSource.pop(previous);
		}
	}
	*///?}

	// ---- The sweep leg of the same stash: STAGE 3 CLOSES STAGE 2's RESIDUE. ----
	//
	// Stage 2 wrapped three sites (hurtServer x2, Player.attack -> causeExtraKnockback) and
	// left `Player.doSweepAttack` open, on the argument that vanilla only sweeps with a
	// sweeping-ratio weapon so no dagger, fist or spell reaches it. That argument covers four
	// of the impl's five behaviours and NOT the fifth: the three global pulse flags
	// (`BlizzardZones.isPulsing`, `SlayerTicker.isBleeding`, `RadianceAura.isPulsing`) are
	// read for ANY non-null source. On 26.2 a sweep's knockback carries a source, so a sweep
	// swung during a Blizzard/Rend/Radiance pulse is zeroed; below 26.2, with nothing stashed,
	// `KnockbackSource.current()` is null and the impl returns early with the shove intact.
	// That is a real, if narrow, balance divergence, and it is why the handover said to close
	// it here rather than reason about it four more times.
	//
	// MEASURED, on 26.1.2 AND 1.21.11 (`javap -c` on both mojmap `Player`s): `doSweepAttack`
	// has the identical descriptor `(Entity, float, DamageSource, float)V` on both, and each
	// contains EXACTLY ONE `LivingEntity.knockback(DDD)V`, at the same offset 222. The
	// DamageSource is a parameter, so this needs no `@Local` — the four target args are
	// captured after the Operation.
	//
	// WHY THE PREDICATE IS A CONJUNCTION and not a bare `<26.2`: 1.21.1 and 1.20.1 do not have
	// this method at all. They spell it `Player.sweepAttack()`, with NO parameters and
	// therefore no source in scope (measured on both). So this arm covers exactly the two
	// nodes whose sweep carries a source but whose knockback does not, and the 1.21.1/1.20.1
	// sweep leg stays an open item for Stage 4 — where it needs a different re-rooting, not
	// this one with a different descriptor.
	//
	// Ordering inside the target is safe by construction: the `hurtServer` call at offset 184
	// runs to completion (with LivingEntityMixin's own push/pop around ITS knockback) before
	// the sweep's own knockback at 222, and push/pop is save-and-restore.
	//? if >=1.21.11 && <26.2 {
	/*@com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
			method = "doSweepAttack(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/damagesource/DamageSource;F)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"))
	private void archetypes$stashSweepKnockback(final net.minecraft.world.entity.LivingEntity victim,
			final double strength, final double x, final double z,
			final com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original,
			final net.minecraft.world.entity.Entity target, final float damage,
			final net.minecraft.world.damagesource.DamageSource source, final float sweepRatio) {
		net.minecraft.world.damagesource.DamageSource previous =
				com.archetypes.KnockbackSource.push(source);

		try {
			original.call(victim, strength, x, z);
		} finally {
			com.archetypes.KnockbackSource.pop(previous);
		}
	}
	*///?}
}
