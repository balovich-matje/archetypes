package com.archetypes;

import java.util.List;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Slayer capstone actives. Both ride the shared ability key: the payload
 * receiver dispatches on the mainhand item, so G is bash with a shield,
 * Decimate with a greatsword, Bladestorm with a sword.
 */
public final class SlayerActives {
	private SlayerActives() {
	}

	/**
	 * Decimate: one massive tilted cleave. Double attribute damage to every
	 * living thing in the front arc, and instant-break clutter — torches,
	 * grass, fire — is swept from its path. Nothing sturdier: swinging this
	 * inside your own base must never redecorate it.
	 */
	public static void decimate(final ServerPlayer player) {
		var owned = NodePurchases.owned(player, SubTree.SLAYER);

		if (SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.DECIMATE) == 0
				|| !ModItems.isGreatsword(player.getMainHandItem())) {
			return;
		}

		AttachmentTarget target = (AttachmentTarget) player;
		long now = player.level().getGameTime();
		Long readyAt = target.getAttached(ModAttachments.DECIMATE_READY_AT);

		if (readyAt != null && now < readyAt) {
			return;
		}

		int decimateCooldown = Tuning.DECIMATE_COOLDOWN_TICKS
				- (SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.RELENTLESS) > 0
						? Tuning.RELENTLESS_REDUCTION_TICKS : 0);
		target.setAttached(ModAttachments.DECIMATE_READY_AT, now + decimateCooldown);

		ServerLevel level = (ServerLevel) player.level();
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize();
		float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE)
				* Tuning.DECIMATE_DAMAGE_MULTIPLIER);

		// No vanilla swing: the PAL cleave pose owns the body for the swing's
		// duration, on every client that can see us.
		target.setAttached(ModAttachments.DECIMATE_SWING_AT, now);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 1.0F, 0.8F);

		// Entities: everything in the front half-disc.
		double range = Tuning.DECIMATE_RANGE;
		List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().expandTowards(flat.scale(range)).inflate(1.2, 0.5, 1.2),
				entity -> entity != player && entity.isAlive() && !entity.isSpectator()
						&& inFront(player, entity, flat));

		for (LivingEntity victim : victims) {
			victim.hurtServer(level, player.damageSources().playerAttack(player), damage);
			Vec3 away = victim.position().subtract(player.position());
			Vec3 push = new Vec3(away.x, 0.0, away.z).normalize();
			victim.push(push.x * 0.8, 0.2, push.z * 0.8);
		}

		// Blocks: only instant-break clutter is swept. destroyBlock drops loot
		// and plays each block's own break effect, which doubles as our debris.
		BlockPos centre = player.blockPosition();
		int destroyed = 0;

		outer:
		for (int dy = 0; dy <= 1; dy++) {
			for (int dx = (int) -range; dx <= range; dx++) {
				for (int dz = (int) -range; dz <= range; dz++) {
					if (dx * dx + dz * dz > range * range) {
						continue;
					}

					BlockPos pos = centre.offset(dx, dy, dz);
					Vec3 toBlock = Vec3.atCenterOf(pos).subtract(player.position());
					Vec3 toBlockFlat = new Vec3(toBlock.x, 0.0, toBlock.z);

					if (toBlockFlat.lengthSqr() > 0.1
							&& toBlockFlat.normalize().dot(flat) < 0.3) {
						continue;
					}

					BlockState state = level.getBlockState(pos);

					if (state.isAir()) {
						continue;
					}

					if (state.getDestroySpeed(level, pos) != 0.0F) {
						continue;
					}

					level.destroyBlock(pos, true, player);

					if (++destroyed >= Tuning.DECIMATE_MAX_BLOCKS) {
						break outer;
					}
				}
			}
		}

		// The cleave itself: one greatsword-wide flash centred in front, stretched
		// along the swing's tangent and tilted down across it (~25 degrees) to
		// carry the greatsword's weight. With count 0 the offset triple becomes
		// the particle's velocity, which greatsword_sweep reads as its stretch
		// direction instead.
		double angle = Math.toRadians(player.getYRot()) + Math.PI / 2.0;
		level.sendParticles(ModParticles.GREATSWORD_SWEEP,
				player.getX() + Math.cos(angle) * 2.6,
				player.getY() + 1.35,
				player.getZ() + Math.sin(angle) * 2.6,
				0, -Math.sin(angle), 0.0, Math.cos(angle), 1.0);
	}

	/** Bladestorm: start the channel; SlayerTicker runs the volleys. */
	public static void bladestorm(final ServerPlayer player) {
		var owned = NodePurchases.owned(player, SubTree.SLAYER);

		if (SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.BLADESTORM) == 0
				|| !ModItems.isSword(player.getMainHandItem())) {
			return;
		}

		AttachmentTarget target = (AttachmentTarget) player;
		long now = player.level().getGameTime();
		Long readyAt = target.getAttached(ModAttachments.BLADESTORM_READY_AT);

		if (readyAt != null && now < readyAt) {
			return;
		}

		int stormCooldown = Tuning.BLADESTORM_COOLDOWN_TICKS
				- (SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.RELENTLESS) > 0
						? Tuning.RELENTLESS_REDUCTION_TICKS : 0);
		target.setAttached(ModAttachments.BLADESTORM_READY_AT, now + stormCooldown);
		target.setAttached(ModAttachments.BLADESTORM_END, now + Tuning.BLADESTORM_CHANNEL_TICKS);

		((ServerLevel) player.level()).playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.7F);
	}

	private static boolean inFront(final ServerPlayer player, final LivingEntity target, final Vec3 flat) {
		Vec3 toTarget = target.position().subtract(player.position());
		Vec3 toTargetFlat = new Vec3(toTarget.x, 0.0, toTarget.z);

		if (toTargetFlat.lengthSqr() < 1.0E-4) {
			return true;
		}

		return toTargetFlat.normalize().dot(flat) > 0.2;
	}
}
