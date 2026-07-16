package com.archetypes.client.mixin;

import com.archetypes.MeleeSwingPayload;
import com.archetypes.WeaponClass;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jspecify.annotations.Nullable;

/**
 * Combat swings, caught at the source: startAttack is the one entry point for
 * every attack click. When it's a combat swing (not aimed at a block) with a
 * charged weapon we own animations for, tell the server — it bumps the synced
 * counter and every client plays the pose.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Shadow
	public @Nullable HitResult hitResult;

	@Shadow
	public @Nullable LocalPlayer player;

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void archetypes$combatSwing(final CallbackInfoReturnable<Boolean> cir) {
		if (this.player == null
				|| (this.hitResult != null && this.hitResult.getType() == HitResult.Type.BLOCK)) {
			return;
		}

		if (WeaponClass.of(this.player) == WeaponClass.NONE) {
			return;
		}

		// No flailing mid-bladestorm: the storm owns the blade.
		Long stormEnd = ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) this.player)
				.getAttached(com.archetypes.ModAttachments.BLADESTORM_END);

		if (stormEnd != null && stormEnd > this.player.level().getGameTime()) {
			cir.setReturnValue(false);
			return;
		}

		// A recharging weapon doesn't swing at all — no half-charged flicks.
		// Mining is untouched: block targets returned before this.
		if (this.player.getAttackStrengthScale(0.0F) < 1.0F) {
			cir.setReturnValue(false);
			return;
		}

		ClientPlayNetworking.send(new MeleeSwingPayload());
	}
}
