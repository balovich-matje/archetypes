package com.archetypes.mixin;

import com.archetypes.SkillPoints;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the cached advancement count (the XP-rate multiplier's input) in
 * step: a full recount only when a REAL advancement lands or is revoked —
 * the 1,500+ silent recipe unlocks have no display block and are skipped,
 * so the hot path never pays for them.
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
	@Shadow
	private ServerPlayer player;

	@Inject(method = "award", at = @At("RETURN"))
	private void archetypes$countOnAward(final AdvancementHolder holder, final String criterion,
			final CallbackInfoReturnable<Boolean> cir) {
		this.archetypes$maybeRecount(holder, cir);
	}

	@Inject(method = "revoke", at = @At("RETURN"))
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
}
