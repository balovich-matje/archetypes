package com.archetypes;

import java.util.Set;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;

/**
 * The Seeker's casts. No cooldowns anywhere — mana is the only gate (see
 * {@link Mana}); every method checks ownership and price server-side and
 * spawns a {@link SpellProjectile} in the right mode.
 */
public final class SeekerSpells {
	private SeekerSpells() {
	}

	/** The elementalist key: Meteorite replaces Fireball once owned. */
	public static void castElementalist(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.ELEMENTALIST);

		if (!PlaceholderNodes.owns(SubTree.ELEMENTALIST, owned, PlaceholderNodes.Kind.ACTIVE)) {
			return;
		}

		if (PlaceholderNodes.owns(SubTree.ELEMENTALIST, owned, PlaceholderNodes.Kind.CAPSTONE_A)) {
			meteorite(player);
			return;
		}

		// Flamethrower holders channel through SpellChannelPayload instead;
		// a single press from them casts nothing.
		if (PlaceholderNodes.owns(SubTree.ELEMENTALIST, owned, PlaceholderNodes.Kind.CAPSTONE_B)) {
			return;
		}

		if (!Mana.spend(player, Tuning.FIREBALL_COST)) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		SpellProjectile fireball = new SpellProjectile(player, level,
				SpellProjectile.Mode.FIREBALL, new ItemStack(Items.FIRE_CHARGE));
		fireball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
				Tuning.FIREBALL_SPEED, 0.5F);
		level.addFreshEntity(fireball);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 0.7F, 1.3F);
	}

	/**
	 * Meteorite: all current mana (100 minimum) buys a rock over the targeted
	 * block, sixteen up, coming down fast. Won't cast into a ceiling — the
	 * whole column above the target must be air.
	 */
	private static void meteorite(final ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		HitResult hit = player.pick(Tuning.METEOR_TARGET_RANGE, 1.0F, false);

		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}

		BlockPos targetPos = ((net.minecraft.world.phys.BlockHitResult) hit).getBlockPos();

		for (int dy = 1; dy <= Tuning.METEOR_HEIGHT; dy++) {
			if (!level.getBlockState(targetPos.above(dy)).isAir()) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 0.8F);
				return;
			}
		}

		float spent = Mana.spendAll(player, Tuning.METEOR_MIN_MANA);

		if (spent <= 0.0F) {
			return;
		}

		SpellProjectile meteor = new SpellProjectile(player, level,
				SpellProjectile.Mode.METEOR, new ItemStack(Items.MAGMA_BLOCK)).withPower(spent);
		meteor.setPos(targetPos.getX() + 0.5, targetPos.getY() + 1 + Tuning.METEOR_HEIGHT,
				targetPos.getZ() + 0.5);
		meteor.setDeltaMovement(0.0, -Tuning.METEOR_SPEED, 0.0);
		level.addFreshEntity(meteor);
		level.playSound(null, targetPos.getX(), targetPos.getY(), targetPos.getZ(),
				SoundEvents.GHAST_WARN, SoundSource.PLAYERS, 1.2F, 0.7F);
	}

	/**
	 * Flamethrower: fed by one payload per client tick while the key is held.
	 * A gap in the stream means the channel ended, so a fresh press pays the
	 * base cost and the stream itself pays the per-second drain.
	 */
	public static void channelFlame(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.ELEMENTALIST);

		if (!PlaceholderNodes.owns(SubTree.ELEMENTALIST, owned, PlaceholderNodes.Kind.ACTIVE)
				|| !PlaceholderNodes.owns(SubTree.ELEMENTALIST, owned, PlaceholderNodes.Kind.CAPSTONE_B)) {
			return;
		}

		AttachmentTarget target = (AttachmentTarget) player;
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		Long last = target.getAttached(ModAttachments.FLAME_LAST_TICK);
		boolean fresh = last == null || now - last > 3;

		if (!Mana.spend(player, fresh ? Tuning.FLAME_START_COST : Tuning.FLAME_COST_PER_TICK)) {
			return;
		}

		target.setAttached(ModAttachments.FLAME_LAST_TICK, now);

		if (now % Tuning.FLAME_BOLT_PERIOD_TICKS != 0) {
			return;
		}

		SpellProjectile bolt = new SpellProjectile(player, level,
				SpellProjectile.Mode.FLAME_BOLT, new ItemStack(Items.BLAZE_POWDER));
		bolt.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
				Tuning.FLAME_BOLT_SPEED, 4.0F);
		level.addFreshEntity(bolt);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.4F, 1.4F);
	}

	/** Magic Missile: straight line, sixteen blocks, wand in hand. */
	public static void castMissile(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.WIZARD);

		if (!PlaceholderNodes.owns(SubTree.WIZARD, owned, PlaceholderNodes.Kind.ACTIVE)
				|| !ModItems.isWand(player.getMainHandItem())
				|| !Mana.spend(player, Tuning.MISSILE_COST)) {
			return;
		}

		boolean homing = PlaceholderNodes.owns(SubTree.WIZARD, owned, PlaceholderNodes.Kind.CAPSTONE_A);
		boolean pierce = PlaceholderNodes.owns(SubTree.WIZARD, owned, PlaceholderNodes.Kind.CAPSTONE_B);
		ServerLevel level = (ServerLevel) player.level();
		SpellProjectile missile = new SpellProjectile(player, level,
				SpellProjectile.Mode.MISSILE, new ItemStack(Items.AMETHYST_SHARD));

		if (homing) {
			missile.withHoming();
		}

		if (pierce) {
			missile.withPierce();
		}

		missile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
				Tuning.MISSILE_SPEED * (homing ? Tuning.MISSILE_HOMING_SPEED_FACTOR : 1.0F), 0.0F);
		level.addFreshEntity(missile);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.5F);
	}

	/** Holy Light: a lobbed burst that heals the living and burns the undead. */
	public static void castHolyLight(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.PRIEST);

		if (!PlaceholderNodes.owns(SubTree.PRIEST, owned, PlaceholderNodes.Kind.ACTIVE)
				|| !Mana.spend(player, Tuning.HOLY_COST)) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		SpellProjectile light = new SpellProjectile(player, level,
				SpellProjectile.Mode.HOLY_LIGHT, new ItemStack(Items.GLOWSTONE_DUST))
				.withBlessing(
						PlaceholderNodes.owns(SubTree.PRIEST, owned, PlaceholderNodes.Kind.CAPSTONE_A),
						PlaceholderNodes.owns(SubTree.PRIEST, owned, PlaceholderNodes.Kind.CAPSTONE_B));
		light.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
				Tuning.HOLY_SPEED, 0.5F);
		level.addFreshEntity(light);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 1.0F, 1.2F);
	}
}
