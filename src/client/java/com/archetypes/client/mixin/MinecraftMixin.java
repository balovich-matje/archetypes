package com.archetypes.client.mixin;

import com.archetypes.WeaponClass;

import com.archetypes.platform.ArchetypeStore;

import com.archetypes.platform.Net;
import com.archetypes.state.WireId;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

		// A conjured sword is WeaponClass.NONE but still announces its swing, so
		// the server can resolve Blink.
		boolean magicSword = com.archetypes.ModItems.isMagicSword(this.player.getMainHandItem());

		if (WeaponClass.of(this.player) == WeaponClass.NONE && !magicSword) {
			return;
		}

		// No flailing mid-bladestorm: the storm owns the blade.
		Long stormEnd = ArchetypeStore.INSTANCE.get(this.player, com.archetypes.ModState.BLADESTORM_END);

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

		Net.INSTANCE.sendToServer(WireId.MELEE_SWING, buf -> { });
	}

	/**
	 * Free Hand: the node that only ever needed the input lock taken off.
	 *
	 * <p>Nothing on the server forbids swinging while blocking —
	 * {@code Player.attack} runs while an item is in use and does not end the
	 * use. The whole prohibition is the {@code if (this.player.isUsingItem())}
	 * arm of {@code handleKeybinds}, which drains the attack-click queue into an
	 * empty {@code while} loop and throws it away.
	 *
	 * <p>So the clicks are spent here, at the head, before that arm can eat
	 * them — and only while a shield is actually up. The arm itself is left
	 * alone, which is the point: it is also what releases the shield when the
	 * use key comes up, and a player who could never lower their guard would be
	 * worse off than one who could not swing.
	 */
	@Inject(method = "handleKeybinds", at = @At("HEAD"))
	private void archetypes$freeHand(final CallbackInfo ci) {
		if (this.player != null
				&& com.archetypes.ColossusProtector.canAttackWhileBlocking(this.player)) {
			while (((Minecraft) (Object) this).options.keyAttack.consumeClick()) {
				this.archetypes$startAttack();
			}
		}
	}

	@org.spongepowered.asm.mixin.gen.Invoker("startAttack")
	abstract boolean archetypes$startAttack();
}
