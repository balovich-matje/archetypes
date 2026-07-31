package com.archetypes.mixin;

import com.archetypes.SkillPoints;

//? if >=1.20.5 {
import net.minecraft.advancements.AdvancementHolder;
//?}
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the cached advancement tally (the XP-rate multiplier's input — the
 * total plus the goal and challenge counts, since the rate is frame-weighted)
 * in step: a full recount only when a REAL advancement lands or is revoked —
 * the 1,500+ silent recipe unlocks have no display block and are skipped,
 * so the hot path never pays for them.
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
	@Shadow
	private ServerPlayer player;

	// STAGE 5: 1.20.5 wrapped every advancement in an `AdvancementHolder` and moved the
	// display behind an `Optional`. The two hooks, the return-value gate, the null-player
	// guard and the "has a display block" test are the same three questions either way —
	// only the type of the thing being asked moves, which is why the shell forks and
	// `archetypes$maybeRecount`'s CALLERS do not care.
	//? if >=1.20.5 {
	@Inject(method = "award(Lnet/minecraft/advancements/AdvancementHolder;Ljava/lang/String;)Z", at = @At("RETURN"))
	private void archetypes$countOnAward(final AdvancementHolder holder, final String criterion,
			final CallbackInfoReturnable<Boolean> cir) {
		this.archetypes$maybeRecount(holder, cir);
	}

	@Inject(method = "revoke(Lnet/minecraft/advancements/AdvancementHolder;Ljava/lang/String;)Z", at = @At("RETURN"))
	private void archetypes$countOnRevoke(final AdvancementHolder holder, final String criterion,
			final CallbackInfoReturnable<Boolean> cir) {
		this.archetypes$maybeRecount(holder, cir);
	}

	private void archetypes$maybeRecount(final AdvancementHolder holder,
			final CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && this.player != null && holder.value().display().isPresent()) {
			SkillPoints.refreshAdvancementCount(this.player);
		}
	}
	//?} else {
	/*@Inject(method = "award(Lnet/minecraft/advancements/Advancement;Ljava/lang/String;)Z", at = @At("RETURN"))
	private void archetypes$countOnAward(final net.minecraft.advancements.Advancement advancement,
			final String criterion, final CallbackInfoReturnable<Boolean> cir) {
		this.archetypes$maybeRecount(advancement, cir);
	}

	@Inject(method = "revoke(Lnet/minecraft/advancements/Advancement;Ljava/lang/String;)Z", at = @At("RETURN"))
	private void archetypes$countOnRevoke(final net.minecraft.advancements.Advancement advancement,
			final String criterion, final CallbackInfoReturnable<Boolean> cir) {
		this.archetypes$maybeRecount(advancement, cir);
	}

	private void archetypes$maybeRecount(final net.minecraft.advancements.Advancement advancement,
			final CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && this.player != null && advancement.getDisplay() != null) {
			SkillPoints.refreshAdvancementCount(this.player);
		}
	}
	*///?}
}
