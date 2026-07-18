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

	/** Missile price with Clarity and the held wand; the HUD uses it too. */
	public static float missileCost(final net.minecraft.world.entity.player.Player player) {
		float base = Tuning.MISSILE_COST - Tuning.CLARITY_DISCOUNT
				* WizardNodes.rank(SubTree.WIZARD, NodePurchases.owned(player, SubTree.WIZARD),
						WizardNodes.Family.CLARITY);
		return elementCost(player, base, false, false, false);
	}

	/** Holy Light's price with Grace and the held wand; the HUD uses it too. */
	public static float holyCost(final net.minecraft.world.entity.player.Player player) {
		float base = Tuning.HOLY_COST - Tuning.GRACE_DISCOUNT
				* PriestNodes.rank(SubTree.PRIEST, NodePurchases.owned(player, SubTree.PRIEST),
						PriestNodes.Family.GRACE);
		return elementCost(player, base, false, false, true);
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
	 * whatever you've learned since. The Flamethrower casts via the channel
	 * payload stream instead, so a single press from it does nothing;
	 * Blizzard is a press-cast again — it calls a storm. */
	public static void castElementalist(final ServerPlayer player) {
		if (!ModItems.isWand(player.getMainHandItem())) {
			return;
		}

		Set<Integer> owned = NodePurchases.owned(player, SubTree.ELEMENTALIST);

		if (ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.ICE_BLAST) > 0) {
			if (ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.BLIZZARD) > 0) {
				blizzard(player, owned);
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

		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
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

		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		blast.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
				Tuning.ICE_BLAST_SPEED, 0.5F);
		level.addFreshEntity(blast);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0F, 1.4F);
	}

	/**
	 * Meteorite: the WHOLE pool (100 minimum) buys a rock over the targeted
	 * block, sixteen up, coming down fast — and the cost modifiers refund
	 * their share after the cast, so discounts never shrink the impact.
	 * Won't cast into a ceiling — the column above the target must be air.
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

		float power = wandPower(player, true, false)
				* arcane(NodePurchases.owned(player, SubTree.ELEMENTALIST), spent);
		SpellProjectile meteor = new SpellProjectile(player, level,
				SpellProjectile.Mode.METEOR, new ItemStack(Items.MAGMA_BLOCK))
				.withPower(power)
				// The rock is drawn as big as the mana that bought it.
				.withVisualScale(Math.min(power / Tuning.METEOR_MIN_MANA, Tuning.METEOR_FX_SCALE_CAP));
		meteor.setPos(targetPos.getX() + 0.5, targetPos.getY() + 1 + Tuning.METEOR_HEIGHT,
				targetPos.getZ() + 0.5);
		meteor.setDeltaMovement(0.0, -Tuning.METEOR_SPEED, 0.0);
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		level.addFreshEntity(meteor);
		// A loud whoosh, not a ghast's scream (user call) — scaled with the
		// spend like everything else about the rock.
		level.playSound(null, targetPos.getX(), targetPos.getY(), targetPos.getZ(),
				SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS,
				1.2F * Math.min(power / Tuning.METEOR_MIN_MANA, Tuning.METEOR_FX_SCALE_CAP), 0.75F);

		// The whole pool went into the rock; the cost modifiers (Kindling,
		// the held wand, Spellweaver) refund their share now that it's away.
		// elementCost over the full spend is exactly "what this SHOULD have
		// cost", so the refund is the difference.
		float refund = spent - elementCost(player, spent, true, false, false);

		if (refund > 0.0F) {
			Mana.refund(player, refund);
		}
	}

	/**
	 * Flamethrower: fed by one payload per client tick while the key is held.
	 * A gap in the stream means the channel ended, so a fresh press pays the
	 * base cost and the stream itself pays the per-second drain. (Blizzard
	 * left the channel — it's a called storm now, see blizzard().)
	 */
	public static void channelFlame(final ServerPlayer player) {
		if (!ModItems.isWand(player.getMainHandItem())) {
			return;
		}

		Set<Integer> owned = NodePurchases.owned(player, SubTree.ELEMENTALIST);

		if (ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.FLAMETHROWER) <= 0
				|| ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.FIREBALL) <= 0) {
			return;
		}

		AttachmentTarget target = (AttachmentTarget) player;
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		Long last = target.getAttached(ModAttachments.FLAME_LAST_TICK);
		boolean fresh = last == null || now - last > 3;

		if (!Mana.spend(player, fresh
				? elementCost(player, Tuning.FLAME_START_COST, true, false, false)
				: Tuning.FLAME_COST_PER_TICK)) {
			return;
		}

		if (fresh) {
			player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		}

		target.setAttached(ModAttachments.FLAME_LAST_TICK, now);

		if (now % Tuning.FLAME_BOLT_PERIOD_TICKS != 0) {
			return;
		}

		SpellProjectile bolt = new SpellProjectile(player, level,
				SpellProjectile.Mode.FLAME_BOLT, new ItemStack(Items.BLAZE_POWDER))
				.withDamage(wandPower(player, true, false) * arcane(owned, Tuning.FLAME_BOLT_DAMAGE + 0.5F * Tuning.SCORCH_PER_RANK
						* ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.SCORCH)))
				.withIgnite(Tuning.FLAME_BOLT_FIRE_SECONDS + Tuning.IGNITION_SECONDS_PER_RANK
						* ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.IGNITION))
				.withShatter(ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.SHATTER));

		if (ElementalistNodes.rank(SubTree.ELEMENTALIST, owned, ElementalistNodes.Family.VAPORIZE) > 0) {
			bolt.withVaporize();
		}

		bolt.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
				Tuning.FLAME_BOLT_SPEED, 4.0F);
		level.addFreshEntity(bolt);
		// A pitch-jittered whoosh per bolt reads as a roaring stream; the
		// old flat blaze screech got tiring in combat (user call).
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.3F,
				0.85F + player.getRandom().nextFloat() * 0.3F);
	}

	/**
	 * Blizzard: the Meteorite's AOE opposite — not a channel but a storm
	 * called over the targeted ground, raking its 5x5 with icicles for
	 * eight seconds while the caster does something else. One storm per
	 * caster; recasting moves it (see BlizzardZones).
	 */
	private static void blizzard(final ServerPlayer player, final Set<Integer> owned) {
		ServerLevel level = (ServerLevel) player.level();
		HitResult hit = player.pick(Tuning.METEOR_TARGET_RANGE, 1.0F, false);

		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}

		if (!Mana.spend(player, elementCost(player, Tuning.BLIZZARD_COST, false, true, false))) {
			return;
		}

		BlockPos targetPos = ((net.minecraft.world.phys.BlockHitResult) hit).getBlockPos();
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		BlizzardZones.place(player, level,
				new net.minecraft.world.phys.Vec3(targetPos.getX() + 0.5, targetPos.getY() + 1.0,
						targetPos.getZ() + 0.5),
				wandPower(player, false, true) * arcane(owned, Tuning.BLIZZARD_TOTAL_DAMAGE));
		level.playSound(null, targetPos.getX(), targetPos.getY(), targetPos.getZ(),
				SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.2F, 0.7F);
	}

	/** Magic Missile: straight line, wand in hand — sharpened by the whole
	 * staff: Force damage, Clarity's price, Range's reach, Velocity, the
	 * two-sided Overwhelm/Shatterpoint conditionals, Concussion's weakness,
	 * Echo's free twin, and the Archmage's fifth on top. Mind Well and Echo
	 * announce themselves through the proc display. */
	public static void castMissile(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.WIZARD);
		var target = (net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player;
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		Long lastCast = target.getAttached(ModAttachments.MISSILE_CAST_AT);

		// The 200ms breath between casts, checked before any mana leaves.
		if (WizardNodes.rank(SubTree.WIZARD, owned, WizardNodes.Family.MAGIC_MISSILE) <= 0
				|| !ModItems.isWand(player.getMainHandItem())
				|| (lastCast != null && now - lastCast < Tuning.MISSILE_CAST_GAP_TICKS)
				|| !Mana.spend(player, missileCost(player))) {
			return;
		}

		target.setAttached(ModAttachments.MISSILE_CAST_AT, now);
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);

		// Mind Well: every Nth cast leaves empowered, half again harder.
		int mindWell = WizardNodes.rank(SubTree.WIZARD, owned, WizardNodes.Family.MIND_WELL);
		boolean empowered = false;

		if (mindWell > 0) {
			int every = mindWell >= 2 ? Tuning.MIND_WELL_EVERY_RANK_2 : Tuning.MIND_WELL_EVERY_RANK_1;
			Integer count = target.getAttached(ModAttachments.MISSILE_CAST_COUNT);
			int next = (count == null ? 0 : count) + 1;
			empowered = next >= every;
			target.setAttached(ModAttachments.MISSILE_CAST_COUNT, empowered ? 0 : next);
		}

		SpellProjectile missile = buildMissile(player, level, owned, empowered);
		missile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
				missileSpeed(owned), 0.0F);
		level.addFreshEntity(missile);

		if (empowered) {
			ProcIndicators.send(player, SubTree.WIZARD, WizardNodes.Family.MIND_WELL);
		}

		// Echo: sometimes the shard leaves with a free twin, a hair wide.
		if (WizardNodes.rank(SubTree.WIZARD, owned, WizardNodes.Family.ECHO) > 0
				&& player.getRandom().nextFloat() < Tuning.ECHO_CHANCE) {
			SpellProjectile twin = buildMissile(player, level, owned, false);
			twin.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
					missileSpeed(owned), 2.0F);
			level.addFreshEntity(twin);
			ProcIndicators.send(player, SubTree.WIZARD, WizardNodes.Family.ECHO);
		}

		// Missile FX variant A: a light jittered chime (a fixed pitch heard
		// four times a second turns into a drone), with a deep resonate
		// bloom underneath only when the cast is empowered — the
		// eyes-closed tell.
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
				empowered ? 0.55F : 0.4F,
				1.35F + (player.getRandom().nextFloat() - 0.5F) * (empowered ? 0.2F : 0.3F));

		if (empowered) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6F,
					0.85F + (player.getRandom().nextFloat() - 0.5F) * 0.1F);
		}
	}

	private static float missileSpeed(final Set<Integer> owned) {
		boolean homing = WizardNodes.rank(SubTree.WIZARD, owned, WizardNodes.Family.SEEKER_MISSILE) > 0;
		float speed = Tuning.MISSILE_SPEED * (homing ? Tuning.MISSILE_HOMING_SPEED_FACTOR : 1.0F);
		return WizardNodes.rank(SubTree.WIZARD, owned, WizardNodes.Family.VELOCITY) > 0
				? speed * Tuning.VELOCITY_FACTOR : speed;
	}

	private static SpellProjectile buildMissile(final ServerPlayer player, final ServerLevel level,
			final Set<Integer> owned, final boolean empowered) {
		float damage = Tuning.MISSILE_DAMAGE
				+ Tuning.FORCE_PER_RANK * WizardNodes.rank(SubTree.WIZARD, owned, WizardNodes.Family.FORCE)
				+ (empowered ? Tuning.MIND_WELL_EMPOWER_BONUS : 0.0F);

		if (WizardNodes.rank(SubTree.WIZARD, owned, WizardNodes.Family.ARCHMAGE) > 0) {
			damage *= Tuning.ARCHMAGE_FACTOR;
		}

		SpellProjectile missile = new SpellProjectile(player, level,
				SpellProjectile.Mode.MISSILE,
				new ItemStack(empowered ? ModItems.MAGIC_BOLT_EMPOWERED : ModItems.MAGIC_BOLT))
				.withDamage(damage)
				.withRange(Tuning.MISSILE_RANGE + Tuning.RANGE_PER_RANK
						* WizardNodes.rank(SubTree.WIZARD, owned, WizardNodes.Family.RANGE));

		if (empowered) {
			missile.withEmpowered();
		}

		if (WizardNodes.rank(SubTree.WIZARD, owned, WizardNodes.Family.SEEKER_MISSILE) > 0) {
			missile.withHoming();
		}

		if (WizardNodes.rank(SubTree.WIZARD, owned, WizardNodes.Family.LANCE) > 0) {
			missile.withPierce();
		}

		if (WizardNodes.rank(SubTree.WIZARD, owned, WizardNodes.Family.OVERWHELM) > 0) {
			missile.withOverwhelm(Tuning.OVERWHELM_BONUS);
		}

		if (WizardNodes.rank(SubTree.WIZARD, owned, WizardNodes.Family.SHATTERPOINT) > 0) {
			missile.withShatterpoint(Tuning.SHATTERPOINT_BONUS);
		}

		if (WizardNodes.rank(SubTree.WIZARD, owned, WizardNodes.Family.CONCUSSION) > 0) {
			missile.withWeakness(Tuning.CONCUSSION_WEAKNESS_TICKS);
		}

		return missile;
	}

	/** Holy Light: a lobbed burst that heals the living and burns the
	 * undead, ministered by the whole ankh — Lumen both ways, Mercy the
	 * heal, Wrath the harm, Radiance the reach, Fervent Cast the flight,
	 * Aegis and Sanctuary shelling caster and friends, Immolation's fire
	 * and Judgement's weakness riding the harm side. */
	public static void castHolyLight(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.PRIEST);

		if (PriestNodes.rank(SubTree.PRIEST, owned, PriestNodes.Family.HOLY_LIGHT) <= 0
				|| !ModItems.isWand(player.getMainHandItem())) {
			return;
		}

		if (!Mana.spend(player, holyCost(player))) {
			return;
		}

		float ascend = PriestNodes.rank(SubTree.PRIEST, owned, PriestNodes.Family.ASCENDANT) > 0
				? Tuning.ASCENDANT_FACTOR : 1.0F;
		int lumen = PriestNodes.rank(SubTree.PRIEST, owned, PriestNodes.Family.LUMEN);
		float heal = (Tuning.HOLY_AMOUNT + Tuning.LUMEN_PER_RANK * lumen
				+ Tuning.MERCY_PER_RANK * PriestNodes.rank(SubTree.PRIEST, owned, PriestNodes.Family.MERCY))
				* ascend
				* (player.getMainHandItem().is(ModItems.HOLY_WAND) ? Tuning.WAND_HOLY_HEAL_FACTOR : 1.0F);
		float harm = (Tuning.HOLY_AMOUNT + Tuning.LUMEN_PER_RANK * lumen
				+ Tuning.WRATH_PER_RANK * PriestNodes.rank(SubTree.PRIEST, owned, PriestNodes.Family.WRATH))
				* ascend;

		ServerLevel level = (ServerLevel) player.level();
		SpellProjectile light = new SpellProjectile(player, level,
				SpellProjectile.Mode.HOLY_LIGHT, new ItemStack(Items.GLOWSTONE_DUST))
				.withHeal(heal)
				.withHarm(harm)
				.withRadius(Tuning.HOLY_RADIUS + Tuning.RADIANCE_BONUS
						* PriestNodes.rank(SubTree.PRIEST, owned, PriestNodes.Family.RADIANCE))
				.withAegis(PriestNodes.rank(SubTree.PRIEST, owned, PriestNodes.Family.AEGIS))
				.withSanctuary(PriestNodes.rank(SubTree.PRIEST, owned, PriestNodes.Family.SANCTUARY))
				.withImmolation(PriestNodes.rank(SubTree.PRIEST, owned, PriestNodes.Family.IMMOLATION))
				.withJudgement(PriestNodes.rank(SubTree.PRIEST, owned, PriestNodes.Family.JUDGEMENT))
				.withBlessing(
						PriestNodes.rank(SubTree.PRIEST, owned, PriestNodes.Family.RENEWAL) > 0,
						PriestNodes.rank(SubTree.PRIEST, owned, PriestNodes.Family.BENEDICTION) > 0);

		boolean fervent = PriestNodes.rank(SubTree.PRIEST, owned, PriestNodes.Family.FERVENT_CAST) > 0;

		if (fervent) {
			light.withFlatArc();
		}

		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		light.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
				Tuning.HOLY_SPEED * (fervent ? Tuning.FERVENT_FACTOR : 1.0F), 0.5F);
		level.addFreshEntity(light);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 1.0F, 1.2F);
	}
}
