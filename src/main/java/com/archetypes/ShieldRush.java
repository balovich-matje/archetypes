package com.archetypes;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Shield Rush: hold block, press sprint, lunge. The impulse is a plain
 * velocity shove along the look direction; {@code hurtMarked} makes the server
 * push reach the client. Its own short cooldown keeps a 6-block lunge from
 * becoming a movement exploit.
 */
public final class ShieldRush {
	private ShieldRush() {
	}

	public static void execute(final ServerPlayer player) {
		var owned = NodePurchases.owned(player, SubTree.PROTECTOR);
		int rank = ProtectorNodes.rank(SubTree.PROTECTOR, owned, ProtectorNodes.Family.RUSH);

		if (rank <= 0 || !player.isBlocking()) {
			return;
		}

		AttachmentTarget target = (AttachmentTarget) player;
		long now = player.level().getGameTime();
		Long readyAt = target.getAttached(ModAttachments.RUSH_READY_AT);

		if (readyAt != null && now < readyAt) {
			return;
		}

		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize();
		double impulse = Tuning.rushBlocks(rank) * Tuning.RUSH_IMPULSE_PER_BLOCK;

		player.setDeltaMovement(player.getDeltaMovement().add(flat.x * impulse, 0.1, flat.z * impulse));
		player.hurtMarked = true;
		target.setAttached(ModAttachments.RUSH_READY_AT, now + Tuning.RUSH_COOLDOWN_TICKS);

		((ServerLevel) player.level()).playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.8F, 1.4F);
	}
}
