package com.archetypes.mixin;

import com.archetypes.CrusherNodes;
import com.archetypes.WeaponClass;
import com.archetypes.ModAttachments;
import com.archetypes.ModItems;
import com.archetypes.NodePurchases;
import com.archetypes.ProcIndicators;
import com.archetypes.ProtectorNodes;
import com.archetypes.SlayerNodes;
import com.archetypes.SubTree;
import com.archetypes.Tuning;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
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
	 * Iron Spikes + Braced, both keyed on a hit landing on a raised shield.
	 *
	 * <p>Direction matters and it bit us once: vanilla's {@code blockUsingItem}
	 * calls {@code attacker.blockedByItem(blocker, ...)} — so {@code this} is the
	 * <em>attacker</em> and the parameter is the <em>blocker</em>. The first
	 * version of this hook guarded on {@code this instanceof ServerPlayer}, which
	 * meant it only ever fired for player attackers hitting mobs' shields, i.e.
	 * never. Verified against the bytecode: receiver aload_2 (attacker), arg
	 * aload_0 (blocker).
	 *
	 * <p>Melee-only comes free — a projectile's direct entity is the projectile,
	 * so vanilla never routes arrows here.
	 */
	@Inject(method = "blockedByItem", at = @At("TAIL"))
	private void archetypes$onShieldBlocked(final LivingEntity blocker, final DamageSource source,
			final float blocked, final CallbackInfo ci) {
		if (!(blocker instanceof ServerPlayer player)) {
			return;
		}

		LivingEntity attacker = (LivingEntity) (Object) this;
		var owned = NodePurchases.owned(player, SubTree.PROTECTOR);

		// Braced: a blocked hit shaves a second off the bash's countdown — the
		// tree's loop closing: blocking feeds bashing feeds blocking.
		if (ProtectorNodes.rank(SubTree.PROTECTOR, owned, ProtectorNodes.Family.BRACED) > 0) {
			AttachmentTarget target = (AttachmentTarget) player;
			Long readyAt = target.getAttached(ModAttachments.BASH_READY_AT);
			long now = player.level().getGameTime();

			if (readyAt != null && readyAt > now) {
				target.setAttached(ModAttachments.BASH_READY_AT,
						Math.max(now, readyAt - Tuning.BRACED_REFUND_TICKS));
				((ServerLevel) player.level()).sendParticles(
						net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
						player.getX(), player.getY() + 1.1, player.getZ(),
						2, 0.25, 0.25, 0.25, 0.0);
				ProcIndicators.send(player, SubTree.PROTECTOR, ProtectorNodes.Family.BRACED);
			}
		}

		// Iron Spikes: thorns by vanilla's own numbers — 15% proc per effective
		// level (certain from rank 2), 1-4 damage plus level-10 above X. No extra
		// durability cost: blocking spam (slimes) already drains shields fast
		// enough that our surcharge only punished the node's own fantasy.
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
		((ServerLevel) player.level()).playSound(null,
				attacker.getX(), attacker.getY(), attacker.getZ(),
				net.minecraft.sounds.SoundEvents.THORNS_HIT,
				net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
		ProcIndicators.send(player, SubTree.PROTECTOR, ProtectorNodes.Family.SPIKES);
	}

	/**
	 * Lunge: sword swings while sprinting hop the player a short step along the
	 * look vector — including upward. Sprint-gated by playtest: firing on every
	 * swing was disruptive (mining with a sword, casual whacks). Whiffs still
	 * lunge — it is a gap-closer, not a hit reward. Suppressed during a
	 * bladestorm, whose volleys also swing.
	 */
	@Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"))
	private void archetypes$lunge(final net.minecraft.world.InteractionHand hand,
			final boolean broadcast, final CallbackInfo ci) {
		if (!((Object) this instanceof ServerPlayer player)
				|| hand != net.minecraft.world.InteractionHand.MAIN_HAND
				|| !player.isSprinting()
				|| !ModItems.isSword(player.getMainHandItem())) {
			return;
		}

		var target = (AttachmentTarget) player;

		if (target.getAttached(ModAttachments.BLADESTORM_END) != null) {
			return;
		}

		int rank = SlayerNodes.rank(SubTree.SLAYER,
				NodePurchases.owned(player, SubTree.SLAYER), SlayerNodes.Family.LUNGE);

		if (rank <= 0) {
			return;
		}

		long now = player.level().getGameTime();
		Long readyAt = target.getAttached(ModAttachments.LUNGE_READY_AT);

		if (readyAt != null && now < readyAt) {
			return;
		}

		double impulse = rank * Tuning.LUNGE_BLOCKS_PER_RANK * Tuning.RUSH_IMPULSE_PER_BLOCK;
		var look = player.getLookAngle();
		player.setDeltaMovement(player.getDeltaMovement().add(look.scale(impulse)));
		player.hurtMarked = true;
		target.setAttached(ModAttachments.LUNGE_READY_AT, now + Tuning.LUNGE_COOLDOWN_TICKS);
		((ServerLevel) player.level()).sendParticles(
				net.minecraft.core.particles.ParticleTypes.CLOUD,
				player.getX(), player.getY() + 0.1, player.getZ(), 3, 0.15, 0.02, 0.15, 0.01);
	}

	/**
	 * The greatsword's damage shaping, all in one place on the victim's intake:
	 * Heavy Blows' flat bonus, First Blood's opener bonus against the unhurt,
	 * and the Executioner's finisher on anything already below the threshold.
	 */
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurtServer",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$greatswordDamage(final float amount, final ServerLevel level,
			final DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player)
				|| source.getDirectEntity() != player
				|| !ModItems.isGreatsword(player.getMainHandItem())) {
			return amount;
		}

		LivingEntity victim = (LivingEntity) (Object) this;
		var owned = NodePurchases.owned(player, SubTree.SLAYER);
		float result = amount;

		int heavy = SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.HEAVY);
		result *= 1.0F + Tuning.HEAVY_PER_RANK * heavy;

		int firstBlood = SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.FIRSTBLOOD);

		if (firstBlood > 0 && victim.getHealth() >= victim.getMaxHealth() - 0.01F) {
			result *= 1.0F + Tuning.FIRSTBLOOD_PER_RANK * firstBlood;
			ProcIndicators.send(player, SubTree.SLAYER, SlayerNodes.Family.FIRSTBLOOD);
		}

		if (SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.EXECUTIONER) > 0
				&& victim.getHealth() <= victim.getMaxHealth() * Tuning.EXECUTE_THRESHOLD) {
			result = Math.max(result, victim.getHealth() + 100.0F);
			ProcIndicators.send(player, SubTree.SLAYER, SlayerNodes.Family.EXECUTIONER);
		}

		return result;
	}

	/**
	 * The Marksman's on-hit passives ride the victim's damage intake, before
	 * death resolution — Combustion on a kill-shot must still detonate, and
	 * AFTER_DAMAGE never fires for lethal hits.
	 */
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurtServer",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$marksmanArrowHit(final float amount, final ServerLevel level,
			final DamageSource source) {
		if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow
				&& source.getEntity() instanceof ServerPlayer player
				&& com.archetypes.MarksmanCombat.fromMarksmanWeapon(arrow)) {
			return com.archetypes.MarksmanCombat.onArrowHit(player, (LivingEntity) (Object) this, level, amount);
		}

		return amount;
	}

	/**
	 * The dagger's damage shaping and DoTs, all on the victim's intake:
	 * Razor Edge's steel, Expose's finishing bonus, Flense clawing back what
	 * armor would eat, Deathblow recognising a Shadow Step strike by its
	 * same-tick stamp — and the Venom/Blight coatings applied here so they
	 * land even on killing blows.
	 */
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurtServer",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$daggerDamage(final float amount, final ServerLevel level,
			final DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player)
				|| source.getDirectEntity() != player
				|| !ModItems.isDagger(player.getMainHandItem())) {
			return amount;
		}

		LivingEntity victim = (LivingEntity) (Object) this;
		var owned = NodePurchases.owned(player, SubTree.ASSASSIN);
		float result = amount;

		result *= 1.0F + Tuning.RAZOR_EDGE_PER_RANK
				* com.archetypes.AssassinNodes.rank(SubTree.ASSASSIN, owned,
						com.archetypes.AssassinNodes.Family.RAZOR_EDGE);

		if (victim.getHealth() < victim.getMaxHealth() * 0.5F) {
			result *= 1.0F + Tuning.EXPOSE_PER_RANK
					* com.archetypes.AssassinNodes.rank(SubTree.ASSASSIN, owned,
							com.archetypes.AssassinNodes.Family.EXPOSE);
		}

		int flense = com.archetypes.AssassinNodes.rank(SubTree.ASSASSIN, owned,
				com.archetypes.AssassinNodes.Family.FLENSE);

		if (flense > 0) {
			// Exact compensation: post-armor damage comes out as if the
			// ignored fraction of armor's absorption wasn't there.
			float absorbed = Math.min(0.8F, (float) victim.getAttributeValue(
					net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) * 0.04F);
			float ignored = Math.min(1.0F, Tuning.FLENSE_PER_RANK * flense);
			result = result * (1.0F - absorbed * (1.0F - ignored)) / (1.0F - absorbed);
		}

		Long stepStrike = ((AttachmentTarget) player).getAttached(ModAttachments.STEP_STRIKE_AT);

		if (stepStrike != null && stepStrike == level.getGameTime()) {
			// A Shadow Step strike: Shadow Flurry lands it with three
			// daggers' weight; Twin Fangs brings the off-hand dagger into
			// the same blow at half its weight — identical daggers give the
			// old Deathblow's x1.5, and a bare off-hand gives nothing, so
			// the node nudges toward actually wearing two knives.
			if (com.archetypes.AssassinNodes.rank(SubTree.ASSASSIN, owned,
					com.archetypes.AssassinNodes.Family.SHADOW_FLURRY) > 0) {
				result *= Tuning.SHADOW_FLURRY_MULTIPLIER;
			}

			if (com.archetypes.AssassinNodes.rank(SubTree.ASSASSIN, owned,
					com.archetypes.AssassinNodes.Family.TWIN_FANGS) > 0) {
				float main = ModItems.daggerSwingDamage(player.getMainHandItem());
				float off = ModItems.daggerSwingDamage(player.getOffhandItem());

				if (main > 0.0F && off > 0.0F) {
					result *= 1.0F + Tuning.TWIN_FANGS_OFFHAND_FACTOR * off / main;
				}
			}
		}

		int venom = com.archetypes.AssassinNodes.rank(SubTree.ASSASSIN, owned,
				com.archetypes.AssassinNodes.Family.VENOM);

		if (venom > 0) {
			victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
					net.minecraft.world.effect.MobEffects.POISON, Tuning.VENOM_TICKS, venom - 1));
		}

		int blight = com.archetypes.AssassinNodes.rank(SubTree.ASSASSIN, owned,
				com.archetypes.AssassinNodes.Family.BLIGHT);

		if (blight > 0) {
			victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
					net.minecraft.world.effect.MobEffects.WITHER, Tuning.BLIGHT_TICKS, blight - 1));
		}

		int crippling = com.archetypes.AssassinNodes.rank(SubTree.ASSASSIN, owned,
				com.archetypes.AssassinNodes.Family.CRIPPLING_POISON);

		if (crippling > 0) {
			victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
					net.minecraft.world.effect.MobEffects.SLOWNESS, Tuning.CRIPPLING_SLOW_TICKS,
					crippling - 1));
		}

		return result;
	}

	/**
	 * Daggers shove half as hard: a knife fighter wants the target IN reach,
	 * and the flurry was pushing its own victim away. All knockback funnels
	 * through this one overload (the five-arg delegates here).
	 */
	@org.spongepowered.asm.mixin.injection.ModifyVariable(
			method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double archetypes$daggerKnockback(final double strength, final double strengthArg,
			final double x, final double z, final DamageSource source, final float yStrength,
			final boolean spinAttack) {
		if (source == null) {
			return strength;
		}

		// A blizzard pulse doesn't shove at all: the storm was pushing its
		// victims out of its own square every second (user call).
		if (com.archetypes.BlizzardZones.isPulsing()) {
			return 0.0;
		}

		// Clinch: a bare-fisted Crusher's blows shove 50/100% less — the
		// fight stays in punching range. (Haymaker's send-off is a direct
		// push impulse, not knockback(), so it stays untouched.)
		if (source.getEntity() instanceof ServerPlayer clincher
				&& source.getDirectEntity() == clincher
				&& WeaponClass.of(clincher) == WeaponClass.HANDS) {
			int clinch = com.archetypes.CrusherNodes.rank(SubTree.CRUSHER,
					NodePurchases.owned(clincher, SubTree.CRUSHER), com.archetypes.CrusherNodes.Family.CLINCH);

			if (clinch > 0) {
				return strength * Math.max(0.0,
						1.0 - Tuning.CLINCH_KNOCKBACK_REDUCTION_PER_RANK * clinch);
			}
		}

		// Magic Missiles shove half as hard too — a spam spell that juggles
		// its target out of its own range was self-defeating (user call) —
		// and the Flamethrower doesn't shove at all: its stream was pushing
		// victims out of the stream (user call too).
		if (source.getDirectEntity() instanceof com.archetypes.SpellProjectile spell) {
			if (spell.mode() == com.archetypes.SpellProjectile.Mode.MISSILE) {
				return strength * Tuning.DAGGER_KNOCKBACK_FACTOR;
			}

			if (spell.mode() == com.archetypes.SpellProjectile.Mode.FLAME_BOLT) {
				return 0.0;
			}
		}

		return source.getEntity() instanceof ServerPlayer player
				&& ModItems.isDagger(player.getMainHandItem())
				? strength * Tuning.DAGGER_KNOCKBACK_FACTOR : strength;
	}

	/**
	 * Sidestep: sometimes the blow simply meets air. Melee only — the
	 * attacker must BE the damage, not have thrown it.
	 */
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void archetypes$sidestep(final ServerLevel level, final DamageSource source,
			final float amount, final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if (!((Object) this instanceof ServerPlayer player)
				|| !ModItems.isDagger(player.getMainHandItem())
				|| !(source.getDirectEntity() instanceof LivingEntity)
				|| source.getDirectEntity() != source.getEntity()) {
			return;
		}

		int rank = com.archetypes.AssassinNodes.rank(SubTree.ASSASSIN,
				NodePurchases.owned(player, SubTree.ASSASSIN), com.archetypes.AssassinNodes.Family.SIDESTEP);

		if (rank > 0 && player.getRandom().nextFloat() < Tuning.SIDESTEP_PER_RANK * rank) {
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
					player.getX(), player.getY() + 1.0, player.getZ(), 4, 0.2, 0.3, 0.2, 0.01);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
					net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.6F);
			cir.setReturnValue(false);
		}
	}

	/**
	 * Mana Shield: part of every hit drains the pool instead of the blood —
	 * but only while a wand is actually in hand; a Seeker gone sword-mode
	 * gave up the ward with the regen.
	 */
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurtServer",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$manaShield(final float amount, final ServerLevel level,
			final DamageSource source) {
		if (!((Object) this instanceof ServerPlayer player)
				|| !ModItems.isWand(player.getMainHandItem())) {
			return amount;
		}

		int rank = com.archetypes.WizardNodes.rank(SubTree.WIZARD,
				NodePurchases.owned(player, SubTree.WIZARD), com.archetypes.WizardNodes.Family.MANA_SHIELD);

		if (rank <= 0) {
			return amount;
		}

		float absorbable = amount * Tuning.MANA_SHIELD_ABSORB;
		float drained = com.archetypes.Mana.drain(player,
				absorbable * Tuning.MANA_SHIELD_MANA_PER_DAMAGE);
		float absorbed = drained / Tuning.MANA_SHIELD_MANA_PER_DAMAGE;

		if (absorbed > 0.0F) {
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
					player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.3, 0.5, 0.3, 0.1);
		}

		return amount - absorbed;
	}

	/**
	 * First Strike: melee out of invisibility opens +25% per rank harder.
	 * Vanilla breaks the invisibility right after the hit lands, so this
	 * naturally pays once per vanishing.
	 */
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurtServer",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$firstStrike(final float amount, final ServerLevel level,
			final DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player)
				|| source.getDirectEntity() != player
				|| !player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
			return amount;
		}

		int rank = com.archetypes.ShadowNodes.rank(SubTree.SHADOW,
				NodePurchases.owned(player, SubTree.SHADOW), com.archetypes.ShadowNodes.Family.FIRST_STRIKE);
		return rank > 0 ? amount * (1.0F + Tuning.FIRST_STRIKE_PER_RANK * rank) : amount;
	}

	/**
	 * Dim Presence: while sneaking, mobs notice this player 20% per rank less
	 * — the same channel sneaking and invisibility already use, so it stacks
	 * multiplicatively with both (and with Specialities' Sneaking, which
	 * modifies the same return).
	 */
	@com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "getVisibilityPercent",
			at = @At("RETURN"))
	private double archetypes$dimPresence(final double original) {
		if ((Object) this instanceof ServerPlayer player && player.isCrouching()) {
			int rank = com.archetypes.ShadowNodes.rank(SubTree.SHADOW,
					NodePurchases.owned(player, SubTree.SHADOW), com.archetypes.ShadowNodes.Family.DIM_PRESENCE);

			if (rank > 0) {
				return original * (1.0 - Tuning.DIM_PRESENCE_PER_RANK * rank);
			}
		}

		return original;
	}

	/**
	 * Sunder: virtual Breach levels for the Crusher's weapons — rank for the
	 * mace, doubled for bare fists. Armor absorbs roughly 4% per point
	 * (capped at 80%); each Sunder level claws 15% of that absorption back as
	 * bonus damage, which stacks naturally with the real Breach enchantment
	 * doing its own work inside the armor formula.
	 */
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurtServer",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$sunderDamage(final float amount, final ServerLevel level,
			final DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player)
				|| source.getDirectEntity() != player) {
			return amount;
		}

		com.archetypes.WeaponClass weapon = com.archetypes.WeaponClass.of(player);

		if (weapon != com.archetypes.WeaponClass.MACE && weapon != com.archetypes.WeaponClass.HANDS) {
			return amount;
		}

		var owned = NodePurchases.owned(player, SubTree.CRUSHER);
		float result = amount;

		// A real smash: live fallDistance, or the ticker's mid-fall stamp
		// (belt and suspenders — vanilla resets fallDistance somewhere in the
		// mace pipeline and the exact point is version-dependent).
		Long stamp = ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player)
				.getAttached(ModAttachments.SMASH_AT);
		boolean smashing = weapon == com.archetypes.WeaponClass.MACE
				&& (player.fallDistance > Tuning.SMASH_MIN_FALL
						|| (stamp != null && player.level().getGameTime() - stamp <= 3));

		// Meteor: Density by another name — smash bonus per fallen block.
		int meteor = CrusherNodes.rank(SubTree.CRUSHER, owned, CrusherNodes.Family.METEOR);

		if (meteor > 0 && smashing) {
			result += player.fallDistance * Tuning.METEOR_PER_BLOCK_PER_RANK * meteor;
		}

		LivingEntity victim = (LivingEntity) (Object) this;
		int rank = CrusherNodes.rank(SubTree.CRUSHER, owned, CrusherNodes.Family.SUNDER);

		if (rank == 0) {
			com.archetypes.CrusherCombat.onCrusherHit(player, victim, level, result, weapon,
					owned, smashing);
			return result;
		}

		int levels = rank * (weapon == com.archetypes.WeaponClass.HANDS ? 2 : 1);
		float absorbed = Math.min(0.8F,
				(float) victim.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)
						* 0.04F);

		result = result + result * absorbed * Tuning.SUNDER_PER_LEVEL * levels;
		com.archetypes.CrusherCombat.onCrusherHit(player, victim, level, result, weapon,
				owned, smashing);
		return result;
	}

	/**
	 * Last Shadow's grace period: for two seconds after cheating death the
	 * player takes nothing at all — the capstone's promise is "you got away",
	 * and a skeleton double-tapping the escape would break it.
	 */
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void archetypes$cheatDeathGrace(final ServerLevel level, final DamageSource source,
			final float amount, final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if (!((Object) this instanceof ServerPlayer player)) {
			return;
		}

		Long immuneUntil = ((AttachmentTarget) player).getAttached(ModAttachments.IMMUNE_UNTIL);

		if (immuneUntil != null && level.getGameTime() < immuneUntil) {
			cir.setReturnValue(false);
		}
	}

	/**
	 * Bulwark: vanilla's block-angle check boils down to one Math.acos in
	 * applyItemBlocking — forcing the angle to 0 makes every direction count as
	 * "in front" for a capstone holder. {@code this} is the blocker here.
	 */
	@ModifyExpressionValue(method = "applyItemBlocking",
			at = @At(value = "INVOKE", target = "Ljava/lang/Math;acos(D)D"))
	private double archetypes$bulwark(final double angle) {
		if ((Object) this instanceof ServerPlayer player
				&& ProtectorNodes.rank(SubTree.PROTECTOR, NodePurchases.owned(player, SubTree.PROTECTOR),
						ProtectorNodes.Family.OMNI_BLOCK) > 0) {
			return 0.0;
		}

		return angle;
	}
}
