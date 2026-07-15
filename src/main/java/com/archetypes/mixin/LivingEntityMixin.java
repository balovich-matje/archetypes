package com.archetypes.mixin;

import com.archetypes.ModAttachments;
import com.archetypes.NodePurchases;
import com.archetypes.ProtectorNodes;
import com.archetypes.SubTree;
import com.archetypes.Tuning;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	/**
	 * Iron Spikes: vanilla calls {@code blockedByItem} with the attacker when a
	 * melee hit lands on a raised shield (projectiles never reach it — their
	 * direct entity is the arrow, not a LivingEntity), so this TAIL is exactly
	 * "an enemy hit my block". Thorns-by-the-numbers: 15% proc per effective
	 * enchant level, 1-4 damage plus (level-10) above X, 2 durability per proc —
	 * and no knockback beyond the recoil vanilla already applied.
	 */
	@Inject(method = "blockedByItem", at = @At("TAIL"))
	private void archetypes$ironSpikes(final LivingEntity attacker, final DamageSource source,
			final float blocked, final CallbackInfo ci) {
		if (!((Object) this instanceof ServerPlayer player)) {
			return;
		}

		var owned = NodePurchases.owned(player, SubTree.PROTECTOR);

		// Braced: a blocked hit shaves a second off the bash's countdown — the
		// tree's loop closing: blocking feeds bashing feeds blocking.
		if (ProtectorNodes.rank(SubTree.PROTECTOR, owned, ProtectorNodes.Family.BRACED) > 0) {
			var target = (net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player;
			Long readyAt = target.getAttached(ModAttachments.BASH_READY_AT);
			long now = player.level().getGameTime();

			if (readyAt != null && readyAt > now) {
				target.setAttached(ModAttachments.BASH_READY_AT,
						Math.max(now, readyAt - Tuning.BRACED_REFUND_TICKS));
			}
		}

		int rank = ProtectorNodes.rank(SubTree.PROTECTOR, owned, ProtectorNodes.Family.SPIKES);

		if (rank <= 0 || !attacker.isAlive()) {
			return;
		}

		int level = Tuning.spikesThornsLevel(rank);

		if (player.getRandom().nextFloat() >= level * 0.15F) {
			return;
		}

		int damage = 1 + player.getRandom().nextInt(4) + Math.max(0, level - 10);
		attacker.hurtServer((ServerLevel) player.level(),
				player.damageSources().thorns(player), damage);
		player.getItemBlockingWith().hurtAndBreak(Tuning.SPIKES_DURABILITY_COST,
				player, player.getUsedItemHand());
	}
}
