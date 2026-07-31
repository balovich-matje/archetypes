package com.archetypes.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hardened: a blow taken plates the Colossus for two or four seconds, +1 with a
 * mace and +2 bare-fisted, and the plates stack without ever refreshing each
 * other.
 *
 * <h2>Why RETURN</h2>
 * At RETURN, not at HEAD like the rest of {@link LivingEntityMixin}, and it is
 * the one hook of ours that wants to be: every other hook shapes or cancels the
 * hit and so has to run before vanilla resolves it, while this one asks a
 * question only the finished call can answer — {@code hurtServer} returns true
 * exactly when damage was actually taken, so an i-frame'd or fully refused hit
 * hands out no plate. Lethal hits still count (the method returns true for
 * them), which is why this is not an {@code AFTER_DAMAGE} listener.
 *
 * <h2>Why its own class, and why priority 900</h2>
 * This is the mirror image of {@link DamageTraceMixin}, and it is here for the
 * same structural reason: an injection ORDER that matters must be stated as a
 * number, not inherited from which file a handler happens to live in.
 *
 * <p>Three handlers land on the RETURN of {@code hurtServer} on a Fabric node,
 * and the middle one is ours:
 * <ol>
 *   <li>{@code archetypes$traceFinish} — {@link DamageTraceMixin}, priority 500</li>
 *   <li>{@code archetypes$hardened} — this class</li>
 *   <li>{@code fabric-entity-events-v1}'s {@code afterDamage}, which is what
 *       backs {@code ServerLivingEntityEvents.AFTER_DAMAGE}</li>
 * </ol>
 *
 * <p>Fabric API's own {@code LivingEntityMixin} declares NO priority (verified
 * by disassembling the shipped {@code fabric-entity-events-v1} jar — there is no
 * {@code priority} member on its {@code @Mixin} annotation), so it sits at the
 * default 1000. While this handler lived in {@link LivingEntityMixin} it was at
 * the default 1000 too, i.e. a genuine TIE, and Mixin breaks a priority tie on
 * registration order — which is mod load order, not something this repo
 * controls. Nine Stage-0 exports all came out with Hardened ahead of
 * {@code afterDamage}, but "it has always come out that way here" is not a
 * guarantee, and the failure mode of the other ordering is silent.
 *
 * <p>900 pins the order that was measured and shipped, and it is a strict
 * inequality on both sides rather than a tie of its own: above
 * {@link DamageTraceMixin}'s 500 so the trace still brackets the blow, below
 * Fabric API's 1000 so the plate is handed out before AFTER_DAMAGE listeners
 * run. The direction is the counter-intuitive one and it is measured, not
 * reasoned: application order is priority ASCENDING, each application inserts
 * its callback immediately before the same {@code return} instruction, and so
 * the mixin applied FIRST runs FIRST. The Stage-0 export shows it — traceFinish
 * (500) at offset 1273, hardened at 1300, {@code afterDamage} (1000) at 1312.
 *
 * <p>Nothing here reads or writes state either neighbour touches, so this pins
 * an order rather than fixing a bug: {@link com.archetypes.Hardened#onHurt}
 * grants armour for the NEXT blow and moves no health, and no AFTER_DAMAGE
 * listener in this mod reads the plates. The point is that the order stops being
 * a coin flip.
 *
 * <p>{@link com.archetypes.Hardened#onHurt} owns the rest of the test — the
 * rank, the weapon, and the "an entity must have thrown it" clause that keeps
 * fire, fall and drowning out.
 */
@Mixin(value = LivingEntity.class, priority = 900)
public abstract class HardenedMixin {
	// `hurtServer` -> `hurt` below 1.21.2: the signature-only fork documented in full at
	// LivingEntityMixin's greatsword handler. Legacy arm early-outs on a client level,
	// which is what makes it the same funnel and not a wider one.
	//? if >=1.21.2 {
	@Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("RETURN"))
	private void archetypes$hardened(final ServerLevel level, final DamageSource source,
			final float amount,
			final CallbackInfoReturnable<Boolean> cir) {
	//?} else {
	/*@Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("RETURN"))
	private void archetypes$hardened(final DamageSource source, final float amount,
			final CallbackInfoReturnable<Boolean> cir) {
		if (((LivingEntity) (Object) this).level().isClientSide()) {
			return;
		}

	*///?}
		archetypes$hardenedImpl(source, cir);
	}

	/** Shared implementation of {@link #archetypes$hardened}. */
	@Unique
	private void archetypes$hardenedImpl(final DamageSource source,
			final CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ServerPlayer player
				&& Boolean.TRUE.equals(cir.getReturnValue())) {
			com.archetypes.Hardened.onHurt(player, source);
		}
	}
}
