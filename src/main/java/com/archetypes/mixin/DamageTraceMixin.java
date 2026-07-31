package com.archetypes.mixin;

import com.archetypes.DamageTrace;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The brackets around a blow, and the vanilla steps between them — the only part
 * of {@link DamageTrace} that has to be a mixin.
 *
 * <h2>Why its own class, and why the lowered priority</h2>
 * The trace's first line is meant to be the damage vanilla handed to
 * {@code hurtServer}, before this mod touched it. Every shaper in
 * {@link LivingEntityMixin} is a {@code @ModifyVariable} at that same HEAD, so
 * "first" is a question about mixin application order — and the direction is the
 * opposite of the intuitive one. HEAD resolves to the first instruction of the
 * <em>original</em> method body, and each application inserts its callback
 * immediately before that same instruction, i.e. <em>after</em> everything
 * already inserted. So the mixin applied FIRST runs FIRST, and application order
 * is priority <em>ascending</em> — hence 500 rather than the default 1000, and
 * hence a class of its own, where the number is stated once instead of depending
 * on where in {@code LivingEntityMixin} the method happened to be written (within
 * one mixin the tie is broken by declaration order, which is not something a
 * shaping handler should have to think about).
 *
 * <p>Verified, not assumed: {@code -Dmixin.debug.export=true} writes the merged
 * class to {@code run/.mixin.out}, and disassembling {@code hurtServer} there
 * shows {@code traceBegin} ahead of {@code archetypes$greatswordDamage}. The
 * first draft of this class used a raised priority on exactly the reasoning above
 * run backwards, and the dump is what caught it.
 *
 * <p>At RETURN the same rule puts {@code finish} before
 * {@code archetypes$hardened} instead of after it, which is harmless: Hardened
 * hands the victim armour plates for the <em>next</em> blow and moves no health,
 * so the number this reports is still the one the hit left behind.
 *
 * <h2>Nothing here changes a number</h2>
 * Both ends are plain {@code @Inject}s that read arguments and return values and
 * hand them to a void method. Deliberately not {@code @ModifyReturnValue}: an
 * observer that is structurally incapable of writing back cannot be the reason a
 * traced hit differs from an untraced one, which is the property that lets this
 * ship enabled-but-idle in a release build.
 */
@Mixin(value = LivingEntity.class, priority = 500)
public abstract class DamageTraceMixin {
	/**
	 * Open the frame. Runs ahead of the cancelling heads too (Sidestep, Ghost
	 * Form, the parry), so a hit one of them voids opens a frame that never
	 * closes — {@link DamageTrace} expects that and drains it.
	 */
	@Inject(method = "hurtServer", at = @At("HEAD"))
	private void archetypes$traceBegin(final ServerLevel level, final DamageSource source,
			final float amount, final CallbackInfoReturnable<Boolean> cir) {
		archetypes$traceBeginImpl(source, amount);
	}

	/** Shared implementation of {@link #archetypes$traceBegin}. */
	@Unique
	private void archetypes$traceBeginImpl(final DamageSource source, final float amount) {
		ServerLevel level = (ServerLevel) ((LivingEntity) (Object) this).level();

		DamageTrace.begin(level, source, (LivingEntity) (Object) this, amount);
	}

	/**
	 * Close the frame and print it, with the health the blow actually left.
	 *
	 * <p>{@code cir.getReturnValueZ()} is forwarded because it is the only
	 * honest way to tell "this blow landed for nothing" from "this blow never
	 * happened". {@code hurtServer} returns false from four separate early exits
	 * — invulnerable, already dying, fire-immune, and inside i-frames with
	 * nothing above {@code lastHurt} to spare — and every one of them leaves a
	 * frame whose stages all ran at HEAD and whose victim lost no health. Without
	 * the flag the unaccounted alarm would read that as a stranger reducing the
	 * blow to zero, and would cry wolf on every second swing of a fast weapon.
	 *
	 * <p>Priority 500 also decides that this runs before the other RETURN
	 * injections on {@code hurtServer} (Hardened, and the kill bookkeeping),
	 * which is what the landed-damage measurement needs: it samples health and
	 * absorption, and it must sample them before anything downstream of the hit
	 * gets to move either.
	 */
	@Inject(method = "hurtServer", at = @At("RETURN"))
	private void archetypes$traceFinish(final ServerLevel level, final DamageSource source,
			final float amount, final CallbackInfoReturnable<Boolean> cir) {
		archetypes$traceFinishImpl(cir);
	}

	/** Shared implementation of {@link #archetypes$traceFinish}. */
	@Unique
	private void archetypes$traceFinishImpl(final CallbackInfoReturnable<Boolean> cir) {
		DamageTrace.finish((LivingEntity) (Object) this, cir.getReturnValueZ());
	}

	/**
	 * Vanilla's shield step, observed as the first thing {@code hurtServer} does
	 * to the amount after every HEAD shaper has had it. Here so that a blocked
	 * blow is an accounted shrink: without it, raising a shield would make the
	 * unaccounted alarm announce a phantom mod reducing the hit.
	 *
	 * <p>The method returns the amount it took off rather than what is left, so
	 * the stage's {@code after} is the subtraction, not the return value.
	 */
	@Inject(method = "applyItemBlocking", at = @At("RETURN"))
	private void archetypes$traceBlocking(final ServerLevel level, final DamageSource source,
			final float damage, final CallbackInfoReturnable<Float> cir) {
		archetypes$traceBlockingImpl(damage, cir);
	}

	/** Shared implementation of {@link #archetypes$traceBlocking}. */
	@Unique
	private void archetypes$traceBlockingImpl(final float damage, final CallbackInfoReturnable<Float> cir) {
		DamageTrace.observe((LivingEntity) (Object) this, DamageTrace.STAGE_BLOCKING,
				damage, damage - cir.getReturnValueF());
	}

	/**
	 * Vanilla's armour step. Observed at its own RETURN rather than reconstructed
	 * from the ARMOR attribute, because the armour formula is where enchantments
	 * like Breach and this mod's virtual Sunder levels actually land — a
	 * recomputed number would agree with the design doc and disagree with the
	 * game, which is the exact failure this whole kit exists to catch.
	 */
	@Inject(method = "getDamageAfterArmorAbsorb", at = @At("RETURN"))
	private void archetypes$traceArmor(final DamageSource source, final float damage,
			final CallbackInfoReturnable<Float> cir) {
		archetypes$traceArmorImpl(damage, cir);
	}

	/** Shared implementation of {@link #archetypes$traceArmor}. */
	@Unique
	private void archetypes$traceArmorImpl(final float damage, final CallbackInfoReturnable<Float> cir) {
		DamageTrace.observe((LivingEntity) (Object) this, DamageTrace.STAGE_ARMOUR,
				damage, cir.getReturnValueF());
	}

	/** Vanilla's enchantment/resistance step: Protection IV lives here. */
	@Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"))
	private void archetypes$traceMagic(final DamageSource source, final float damage,
			final CallbackInfoReturnable<Float> cir) {
		archetypes$traceMagicImpl(damage, cir);
	}

	/** Shared implementation of {@link #archetypes$traceMagic}. */
	@Unique
	private void archetypes$traceMagicImpl(final float damage, final CallbackInfoReturnable<Float> cir) {
		DamageTrace.observe((LivingEntity) (Object) this, DamageTrace.STAGE_PROTECTION,
				damage, cir.getReturnValueF());
	}
}
