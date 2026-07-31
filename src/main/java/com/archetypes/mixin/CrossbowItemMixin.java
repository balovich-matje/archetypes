package com.archetypes.mixin;

import com.archetypes.MarksmanNodes;
import com.archetypes.ModState;
import com.archetypes.NodePurchases;
import com.archetypes.SubTree;
import com.archetypes.Tuning;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import com.archetypes.platform.ArchetypeStore;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rapid Reload: while a crossbow kill's prime is banked, the next charge runs
 * faster — up to instant (floored at one tick, because vanilla divides by the
 * duration). Both sides compute the same number: the prime flag and the owned
 * nodes are synced to the owning client, which predicts the draw animation.
 * Only the server clears the prime, once the load actually lands.
 */
@Mixin(CrossbowItem.class)
public abstract class CrossbowItemMixin {
	// ─── STAGE 5: EXCISED BELOW 1.21, and the reason is that the hook has no SHOOTER ─────
	// `getChargeDuration` took only the stack there; the `LivingEntity` arrived with the
	// release that made Quick Charge a per-entity read. Both effects on this hook are
	// per-player — Rapid Reload's primed reload and Deadeye's instant charge — and a static
	// with no entity cannot ask whose crossbow it is.
	//
	// The alternatives were both worse than the loss. Asking the client's own player would
	// make a mob's crossbow read the local player's nodes (and `src/main` cannot name
	// `Minecraft` on a remapped node anyway, conventions §5g). Re-rooting onto
	// `LivingEntity.startUsingItem`'s `getUseDuration` read WOULD have a shooter, but it
	// shortens the countdown WITHOUT shortening the charge that vanilla computes from
	// `getPowerForTime(getUseDuration(stack) - timeLeft)` — the draw would end before the
	// bolt was loaded, which is a different mechanic, not the same one. R-20 is exactly this
	// distinction, so the node no-ops here and says so.
	//
	// What survives on this node: Rapid Reload still PRIMES (the flag is set on a crossbow
	// kill and cleared on load, and nothing else reads it), and Deadeye keeps every other
	// half of itself. What is lost is the reload SPEED, on 1.20.1 only.
	//? if >=1.21 {
	@ModifyReturnValue(method = "getChargeDuration(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)I", at = @At("RETURN"))
	private static int archetypes$rapidReload(final int original, final ItemStack stack,
			final LivingEntity entity) {
		if (!(entity instanceof Player player)) {
			return original;
		}

		// Deadeye charges instantly. Floored at one tick because vanilla
		// DIVIDES by this duration (CrossbowItem's charge-progress property).
		// Computed on both sides: DEADEYE_END syncs to every client, so the
		// owner's draw prediction and the server agree.
		if (com.archetypes.Deadeye.isActive(player)) {
			return 1;
		}

		if (!Boolean.TRUE.equals(ArchetypeStore.INSTANCE.get(player, ModState.CROSSBOW_PRIMED))) {
			return original;
		}

		int rank = MarksmanNodes.rank(SubTree.MARKSMAN, NodePurchases.owned(player, SubTree.MARKSMAN),
				MarksmanNodes.Family.RAPID_RELOAD);

		if (rank <= 0) {
			return original;
		}

		return Math.max(1, Math.round(original * (1.0F - Tuning.RAPID_RELOAD_PER_RANK * rank)));
	}
	//?}

	// `releaseUsing` returns `void` below 1.21.2 — the boolean ("did the item consume the
	// release") arrived with the same rework that split `hurt`. Only the callback type moves
	// with it, from CallbackInfoReturnable to CallbackInfo; the handler never read the return
	// value, and TAIL means the same instruction either way. Fully qualified rather than a new
	// import, so the arm above keeps the import list it compiles with today.
	//
	// `releaseUsing` runs on both logical sides on every node, and always has: the
	// `instanceof ServerPlayer` in the body is the guard, unchanged, on all four.
	//? if >=1.21.2 {
	@Inject(method = "releaseUsing(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)Z", at = @At("TAIL"))
	private void archetypes$consumePrime(final ItemStack stack, final Level level,
			final LivingEntity entity, final int timeLeft, final CallbackInfoReturnable<Boolean> cir) {
	//?} else {
	/*@Inject(method = "releaseUsing(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)V", at = @At("TAIL"))
	private void archetypes$consumePrime(final ItemStack stack, final Level level,
			final LivingEntity entity, final int timeLeft,
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
	*///?}
		if (entity instanceof ServerPlayer player && CrossbowItem.isCharged(stack)) {
			ArchetypeStore.INSTANCE.remove(player, ModState.CROSSBOW_PRIMED);
		}
	}
}
