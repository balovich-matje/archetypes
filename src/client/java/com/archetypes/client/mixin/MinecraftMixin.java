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

	@Inject(method = "startAttack", at = @At("HEAD"))
	private void archetypes$combatSwing(final CallbackInfoReturnable<Boolean> cir) {
		if (this.player == null
				|| (this.hitResult != null && this.hitResult.getType() == HitResult.Type.BLOCK)) {
			return;
		}

		// Only charged swings pose; a spam-click stays a vanilla flick, the
		// same way the damage system already discounts it.
		if (this.player.getAttackStrengthScale(0.0F) < 0.9F
				|| WeaponClass.of(this.player) == WeaponClass.NONE) {
			return;
		}

		ClientPlayNetworking.send(new MeleeSwingPayload());
	}
}
