package com.archetypes.mixin;

import com.archetypes.SkillPoints;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin {
	/**
	 * Bank the slice Mending eats. Vanilla routes an orb through
	 * {@code repairPlayerItems} first and hands only the leftover to
	 * {@code Player.giveExperiencePoints} — where PlayerMixin banks — so an
	 * endgame player in damaged Mending gear was crediting the archetype a
	 * fraction of what they earned. Banking the repaired portion here means
	 * the ledger always sees the full orb, Mending or not.
	 */
	@WrapOperation(method = "playerTouch", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/ExperienceOrb;repairPlayerItems(Lnet/minecraft/server/level/ServerPlayer;I)I"))
	private int archetypes$bankMendedExperience(final ExperienceOrb orb, final ServerPlayer player,
			final int value, final Operation<Integer> original) {
		int leftover = original.call(orb, player, value);
		int consumed = value - leftover;

		if (consumed > 0) {
			SkillPoints.bank(player, consumed);
		}

		return leftover;
	}
}
