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

	/** How Elementalist passives and the held wand discount a cast.
	 * Client-safe: the cooldown bar prices its tiles with the same
	 * arithmetic. */
	public static float elementCost(final net.minecraft.world.entity.player.Player player,
			final float base, final boolean fire, final boolean ice, final boolean holy) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.ELEMENTALIST);
		float cost = base;

		if (fire) {
			cost -= Tuning.KINDLING_DISCOUNT_PER_RANK
					* ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.KINDLING);
		}

		if (ice) {
			cost -= Tuning.CHILL_DISCOUNT_PER_RANK
					* ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.CHILL);
		}

		// The wand in hand: the apprentice's discounts everything a little,
		// the specialists their own school a lot.
		ItemStack wand = player.getMainHandItem();

		if (wand.is(ModItems.APPRENTICE_WAND)) {
			cost -= Tuning.WAND_APPRENTICE_DISCOUNT;
		} else if (fire && wand.is(ModItems.BLAZE_WAND)) {
			cost -= Tuning.WAND_SPECIALIST_DISCOUNT;
		} else if (ice && wand.is(ModItems.BREEZE_WAND)) {
			cost -= Tuning.WAND_SPECIALIST_DISCOUNT;
		} else if (holy && wand.is(ModItems.HOLY_WAND)) {
			cost -= Tuning.WAND_SPECIALIST_DISCOUNT;
		}

		if (ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.SPELLWEAVER) > 0) {
			cost *= Tuning.SPELLWEAVER_FACTOR;
		}

		return Math.max(1.0F, cost);
	}

	/** The specialist wands' x1.5 on their own school's damage. */
	private static float wandPower(final ServerPlayer player, final boolean fire, final boolean ice) {
		ItemStack wand = player.getMainHandItem();

		if ((fire && wand.is(ModItems.BLAZE_WAND)) || (ice && wand.is(ModItems.BREEZE_WAND))) {
			return Tuning.WAND_SPECIALIST_POWER;
		}

		return 1.0F;
	}

	private static float arcane(final Set<Integer> owned, final float damage) {
		return ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.ARCANE_POWER) > 0
				? damage * Tuning.ARCANE_POWER_FACTOR : damage;
	}

	/** The elementalist key: whichever element you opened with, upgraded by
	 * whatever you've learned since. Channel capstones cast via the channel
	 * payload stream instead; a single press from them does nothing. */
	public static void castElementalist(final ServerPlayer player) {
		if (!ModItems.isWand(player.getMainHandItem())) {
			return;
		}

		Set<Integer> owned = NodePurchases.owned(player, SubTree.ELEMENTALIST);

		if (ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.ICE_BLAST) > 0) {
			if (ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.BLIZZARD) > 0) {
				return;
			}

			iceBlast(player, owned,
					ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.GLACIAL_SPIKE) > 0);
			return;
		}

		if (ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.FIREBALL) <= 0) {
			return;
		}

		if (ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.METEORITE) > 0) {
			meteorite(player);
			return;
		}

		if (ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.FLAMETHROWER) > 0) {
			return;
		}

		if (!Mana.spend(player, elementCost(player, Tuning.FIREBALL_COST, true, false, false))) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		SpellProjectile fireball = new SpellProjectile(player, level,
				SpellProjectile.Mode.FIREBALL, new ItemStack(Items.FIRE_CHARGE))
				.withDamage(wandPower(player, true, false) * arcane(owned, Tuning.FIREBALL_DAMAGE + Tuning.SCORCH_PER_RANK
						* ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.SCORCH)))
				.withIgnite(Tuning.FIREBALL_FIRE_SECONDS + Tuning.IGNITION_SECONDS_PER_RANK
						* ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.IGNITION))
				.withShatter(ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.SHATTER));

		if (ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.VAPORIZE) > 0) {
			fireball.withVaporize();
		}

		fireball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
				Tuning.FIREBALL_SPEED, 0.5F);
		level.addFreshEntity(fireball);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 0.7F, 1.3F);
	}

	/**
	 * Ice Blast, and its Glacial Spike transformation: the other opening
	 * element — a freezing bolt that slows what it hits (harder and longer
	 * with Frostbite), glazes water with Permafrost, and as the Spike simply
	 * hits like winter itself, leaving the target freezing in place.
	 */
	private static void iceBlast(final ServerPlayer player, final Set<Integer> owned, final boolean glacial) {
		if (!Mana.spend(player, elementCost(player, Tuning.ICE_BLAST_COST, false, true, false))) {
			return;
		}

		int frostbite = ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.FROSTBITE);
		float damage = wandPower(player, false, true)
				* arcane(owned, Tuning.ICE_BLAST_DAMAGE * (glacial ? Tuning.GLACIAL_MULTIPLIER : 1.0F));
		ServerLevel level = (ServerLevel) player.level();
		SpellProjectile blast = new SpellProjectile(player, level, SpellProjectile.Mode.ICE_BLAST,
				new ItemStack(glacial ? Items.BLUE_ICE : Items.ICE))
				.withDamage(damage)
				.withSlow(Tuning.ICE_SLOW_AMP + frostbite, Tuning.ICE_SLOW_TICKS + 20 * frostbite)
				.withShatter(ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.SHATTER));

		if (glacial) {
			blast.withFreeze(Tuning.GLACIAL_FREEZE_TICKS);
		}

		if (ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.PERMAFROST) > 0) {
			blast.withPermafrost();
		}

		blast.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
				Tuning.ICE_BLAST_SPEED, 0.5F);
		level.addFreshEntity(blast);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0F, 1.4F);
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
				SpellProjectile.Mode.METEOR, new ItemStack(Items.MAGMA_BLOCK))
				.withPower(wandPower(player, true, false)
						* arcane(NodePurchases.owned(player, SubTree.ELEMENTALIST), spent));
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
		if (!ModItems.isWand(player.getMainHandItem())) {
			return;
		}

		Set<Integer> owned = NodePurchases.owned(player, SubTree.ELEMENTALIST);
		boolean flame = ElementalistNodes.rank(SubTree.ELEMENTALIST, owned,
				ElementalistNodes.Family.FLAMETHROWER) > 0
				&& ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.FIREBALL) > 0;
		boolean blizzard = ElementalistNodes.rank(SubTree.ELEMENTALIST, owned,
				ElementalistNodes.Family.BLIZZARD) > 0
				&& ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.ICE_BLAST) > 0;

		if (!flame && !blizzard) {
			return;
		}

		AttachmentTarget target = (AttachmentTarget) player;
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		Long last = target.getAttached(ModAttachments.FLAME_LAST_TICK);
		boolean fresh = last == null || now - last > 3;

		if (!Mana.spend(player, fresh
				? elementCost(player, Tuning.FLAME_START_COST, flame, blizzard, false)
				: Tuning.FLAME_COST_PER_TICK)) {
			return;
		}

		target.setAttached(ModAttachments.FLAME_LAST_TICK, now);

		if (now % Tuning.FLAME_BOLT_PERIOD_TICKS != 0) {
			return;
		}

		int shatter = ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.SHATTER);
		SpellProjectile bolt;

		if (flame) {
			bolt = new SpellProjectile(player, level,
					SpellProjectile.Mode.FLAME_BOLT, new ItemStack(Items.BLAZE_POWDER))
					.withDamage(wandPower(player, true, false) * arcane(owned, Tuning.FLAME_BOLT_DAMAGE + 0.5F * Tuning.SCORCH_PER_RANK
							* ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.SCORCH)))
					.withIgnite(Tuning.FLAME_BOLT_FIRE_SECONDS + Tuning.IGNITION_SECONDS_PER_RANK
							* ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.IGNITION))
					.withShatter(shatter);

			if (ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.VAPORIZE) > 0) {
				bolt.withVaporize();
			}
		} else {
			bolt = new SpellProjectile(player, level,
					SpellProjectile.Mode.SNOW_BOLT, new ItemStack(Items.SNOWBALL))
					.withDamage(wandPower(player, false, true) * arcane(owned, Tuning.SNOW_BOLT_DAMAGE))
					.withSlow(1, Tuning.SNOW_BOLT_SLOW_TICKS)
					.withShatter(shatter);

			if (ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.PERMAFROST) > 0) {
				bolt.withPermafrost();
			}
		}

		bolt.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
				Tuning.FLAME_BOLT_SPEED, 4.0F);
		level.addFreshEntity(bolt);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				flame ? SoundEvents.BLAZE_SHOOT : SoundEvents.SNOW_HIT,
				SoundSource.PLAYERS, 0.4F, flame ? 1.4F : 1.2F);
	}

	/** Magic Missile: straight line, sixteen blocks, wand in hand. */
	public static void castMissile(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.WIZARD);

		if (!PlaceholderNodes.owns(SubTree.WIZARD, owned, PlaceholderNodes.Kind.ACTIVE)
				|| !ModItems.isWand(player.getMainHandItem())
				|| !Mana.spend(player, elementCost(player, Tuning.MISSILE_COST, false, false, false))) {
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
		if (!ModItems.isWand(player.getMainHandItem())) {
			return;
		}

		Set<Integer> owned = NodePurchases.owned(player, SubTree.PRIEST);

		if (!PlaceholderNodes.owns(SubTree.PRIEST, owned, PlaceholderNodes.Kind.ACTIVE)
				|| !Mana.spend(player, elementCost(player, Tuning.HOLY_COST, false, false, true))) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		SpellProjectile light = new SpellProjectile(player, level,
				SpellProjectile.Mode.HOLY_LIGHT, new ItemStack(Items.GLOWSTONE_DUST))
				.withHeal(Tuning.HOLY_AMOUNT * (player.getMainHandItem().is(ModItems.HOLY_WAND)
						? Tuning.WAND_HOLY_HEAL_FACTOR : 1.0F))
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
