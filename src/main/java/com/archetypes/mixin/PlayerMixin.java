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
	// ---- R-20 DECISION: `isSweepAttack` DOES NOT EXIST below 1.21.11. ----
	//
	// It is not a rename either: below the boundary the decision is not a method at all, it is
	// five conditions and a boolean local inside `Player.attack`. Read out of the 1.21.1
	// bytecode (offsets 368-424, local slot 11 `bl4`, live 356..1343):
	//
	//     if (fullStrength && !crit && !knockback && this.onGround() && d0 < this.getSpeed()) {
	//         ItemStack itemStack2 = this.getItemInHand(InteractionHand.MAIN_HAND);   // 405
	//         if (itemStack2.getItem() instanceof SwordItem) {                        // 415
	//             bl4 = true;                                                         // 422
	//         }
	//     }
	//
	// THE CONTRACT, not a plausible fire-site: what the node promises is that the sweep FLAG is
	// false for a dagger — the javadoc above says why it has to be the flag and not the
	// `doSweepAttack` call (the flag also gates the ordinary strong/weak hit sound, so
	// suppressing the call alone leaves the swing silent). This arm makes the flag false, by
	// answering vanilla's own weapon question with an empty hand.
	//
	// WHY THAT ANCHOR. `getItemInHand` is called EXACTLY ONCE in the whole of `attack` (so no
	// ordinal, and `defaultRequire: 1` pins it), and the stack it returns is consumed by
	// nothing but the `instanceof` two instructions later: the local it is stored in (slot 14)
	// is overwritten by a float at offset 424, i.e. the scope ends with the if-block. So an
	// empty stack here reaches the sweep test and nothing else. `ItemStack.EMPTY.getItem()` is
	// `Items.AIR`, which is not a SwordItem, so `bl4` stays false and the whole cleave — extra
	// damage, sweep sound, sweep particle — never happens, while the thud does.
	//
	// It is also the SAME QUESTION: `original` IS `getItemInHand(MAIN_HAND)`, which is what
	// `getMainHandItem()` returns, so both arms ask `ModItems.isDagger` about the same stack.
	//
	// AND THE NODE IS REACHABLE THERE, which is what makes this worth doing rather than
	// deleting: on this version vanilla gates the sweep on `instanceof SwordItem`, and
	// ModItems' legacy arm builds the daggers AS `SwordItem`s deliberately (see the note there
	// — repairability and enchantment value still live in the class below 1.21.11). Without
	// this arm a legacy dagger would cleave where a 26.x dagger does not.
	//? if >=1.21.11 {
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
	//?} else {
	/*@com.llamalad7.mixinextras.injector.ModifyExpressionValue(
			method = "attack(Lnet/minecraft/world/entity/Entity;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Player;getItemInHand("
							+ "Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;"))
	private net.minecraft.world.item.ItemStack archetypes$daggersNeverSweep(
			final net.minecraft.world.item.ItemStack original) {
		return ModItems.isDagger(original) ? net.minecraft.world.item.ItemStack.EMPTY : original;
	}
	*///?}

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
	//
	// STAGE 4 NARROWED THE PREDICATE from a bare `<26.2`: `causeExtraKnockback` is itself
	// 1.21.11-and-up. Below that the extra shove is inline in `attack` and the arm after this
	// one covers it.
	//? if >=1.21.11 && <26.2 {
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

	// ---- R-20 DECISION: below 1.21.11 the two legs above are ONE site, and it closes the
	// open item the arm above names. ----
	//
	// `causeExtraKnockback` is gone and so is `doSweepAttack`. On 1.21.1 both the melee extra
	// shove and the sweep's shove are inline in `attack`, as the only two
	// `LivingEntity.knockback(DDD)V` calls in the method — offsets 542 (the sprint bonus plus
	// the Knockback enchantment, inside `if (target.hurt(...))`) and 794 (the sweep loop).
	// One un-ordinal'd wrap therefore covers exactly what the two arms above cover between
	// them, with `defaultRequire: 1` still pinned by the first.
	//
	// `damageSource` is slot 4, live 55..1344 (`javap -l`), so it is in scope at BOTH offsets
	// and it is the only DamageSource local in the method — `@Local` resolves without an
	// ordinal, exactly as on 26.1.
	//
	// No client early-out, and that is measured rather than trusted: `Player.attack` DOES run
	// on the client (MultiPlayerGameMode calls it for prediction), but both knockback sites
	// sit inside the `if (target.hurt(...))` branch that opens at offset 463, and
	// `LivingEntity.hurt` returns false for a client level at its offset 21. Neither site is
	// reachable off the server thread, so KnockbackSource's single-writer contract holds
	// without one. The same structure is why the 26.1 arm above never needed one either.
	//
	// Ordering is safe for the arm above's reason, one frame further out: the `hurt` call at
	// 458 runs to completion — with LivingEntityMixin's own push/pop around the knockback
	// INSIDE it — before either of these, and push/pop is save-and-restore.
	//
	// The predicate is `<1.21.11` and not `>=1.21 && <1.21.11` on purpose: 1.20.1 spells the
	// sweep the same way, so Stage 5 should inherit this rather than rediscover it, and if the
	// shape turns out to differ there `injectors.defaultRequire: 1` fails loudly at that
	// node's first boot instead of dropping the leg in silence.
	//? if <1.21.11 {
	/*@com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
			method = "attack(Lnet/minecraft/world/entity/Entity;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"))
	private void archetypes$stashAttackKnockback(final net.minecraft.world.entity.LivingEntity victim,
			final double strength, final double x, final double z,
			final com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original,
			@com.llamalad7.mixinextras.sugar.Local final net.minecraft.world.damagesource.DamageSource source) {
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
