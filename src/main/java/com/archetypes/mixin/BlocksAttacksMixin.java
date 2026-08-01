package com.archetypes.mixin;

// R-A5: THE WHOLE COMPILATION UNIT IS 1.21.11-AND-UP. `BlocksAttacks` is a data
// component that does not exist below the boundary, so there is no class to mix into.
//
// THE NODE ITSELF IS NOT GONE BELOW THE BOUNDARY — only this host is. `BlocksAttacks.disable`
// is `Player.disableShield` there, and that is ONE chokepoint and not two (this comment used
// to say "plus a raw `ItemCooldowns` write, two places rather than one"; the write is the
// body of `disableShield`, and `disableShield` has exactly one caller in the whole jar).
// `PlayerMixin.archetypes$immovableObject` is the legacy host and it asks the SAME
// `ColossusProtector.immovableObject` this one asks.
//
// A compilation unit with no type declaration is legal and produces no `.class`, which
// is what lets the ENTRY leave the mixin config too — see `strippedMixinEntries` in
// build.fabric.gradle.kts, where a listed-but-absent mixin is a hard boot failure.
// Javadoc became line comments for the Stage-2 reason: a `*` followed by `/` inside a
// disabled branch closes Stonecutter's own block comment early.
//? if >=1.21.11 {

import com.archetypes.ColossusProtector;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlocksAttacks.class)
public abstract class BlocksAttacksMixin {
	// Immovable Object: the guard that normal means cannot break.
	//
	// {@code disable} is the whole of "your shield is knocked aside" — the
	// axe's disable and the Warden's both arrive by way of
	// {@code Player.blockUsingItem} feeding it
	// {@code getSecondsToDisableBlocking}, and this mod's own Unstoppable Force
	// calls it directly for the reason its hook explains. Cancelling at the head
	// of the one method they share is what makes the node a rule rather than a
	// list of attackers, and it is why Unstoppable Force is refused too: that
	// clash is authored on the Crusher's side, not won by omission here.
	//
	// The second parameter is the <em>blocker</em>, not the attacker —
	// {@code blockUsingItem} passes its own {@code this}. Verified against the
	// bytecode, the same trap {@code LivingEntityMixin.archetypes$onShieldBlocked}
	// documents.
	@Inject(method = "disable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
	private void archetypes$immovableObject(final ServerLevel level, final LivingEntity entity,
			final float seconds, final ItemStack stack, final CallbackInfo ci) {
		if (entity instanceof ServerPlayer player
				&& ColossusProtector.immovableObject(player, level)) {
			ci.cancel();
		}
	}
}
//?}
