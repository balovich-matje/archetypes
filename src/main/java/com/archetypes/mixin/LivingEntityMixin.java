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
			return com.archetypes.MarksmanCombat.onArrowHit(player, (LivingEntity) (Object) this,
					level, arrow, amount);
		}

		return amount;
	}

	/**
	 * The conjured armaments' on-hit shaping: Mind over Matter's doubling for
	 * both weapons, and Mana Siphon's refund for the bow. The sword arrives as
	 * a direct player attack, the Spellbow as its own marked arrow — the same
	 * hook answers both so one node cannot mean two different numbers.
	 *
	 * <p>Mind over Matter's other half, the armor bypass, is NOT here: it is a
	 * real Breach stamp on the conjured weapon, applied inside vanilla's own
	 * armor formula (see {@code MagicArmaments.enchant}).
	 */
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurtServer",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$magicArmamentHit(final float amount, final ServerLevel level,
			final DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			return amount;
		}

		if (source.getDirectEntity() == player) {
			return com.archetypes.ModItems.isMagicSword(player.getMainHandItem())
					? com.archetypes.MagicArmaments.shapeHit(player, level, amount, false)
					: amount;
		}

		return source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow
				&& Boolean.TRUE.equals(
						((AttachmentTarget) arrow).getAttached(ModAttachments.SPELLBOW_ARROW))
								? com.archetypes.MagicArmaments.shapeHit(player, level, amount, true)
								: amount;
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

		// Death Mark: the root's quarter and Headhunter's ranks, and only on the
		// one creature this player named. Multiplied separately rather than
		// summed — the design prices the Hunt line at 1.25 x 1.5 = x1.875, not
		// x1.75. Both stamps are checked, not just the flag on the body: the
		// ticker takes a lapsed mark off a tick later than it expires.
		boolean marked = com.archetypes.DeathMark.isMarkedBy(victim, player)
				&& com.archetypes.DeathMark.hasMark(player);

		if (marked) {
			result *= 1.0F + Tuning.DEATH_MARK_DAMAGE_FACTOR;
			result *= 1.0F + Tuning.HEADHUNTER_PER_RANK
					* com.archetypes.NemesisAssassinNodes.rank(player,
							com.archetypes.NemesisAssassinNodes.Family.HEADHUNTER);
		}

		Long stepStrike = ((AttachmentTarget) player).getAttached(ModAttachments.STEP_STRIKE_AT);

		if (stepStrike != null && stepStrike == level.getGameTime()) {
			// Coup de Grace, on the Shadow Step strike only. Players are never
			// deleted outright — the rule Executioner already follows — so the
			// execute pays them out as a doubling, and unconditionally: an
			// execute a player only ever meets under a third health would be no
			// node at all in the fight the branch is built for.
			if (marked && com.archetypes.NemesisAssassinNodes.rank(player,
					com.archetypes.NemesisAssassinNodes.Family.COUP_DE_GRACE) > 0) {
				boolean isPlayer = victim instanceof net.minecraft.world.entity.player.Player;

				if (isPlayer) {
					result *= Tuning.COUP_DE_GRACE_PLAYER_MULTIPLIER;
				} else if (victim.getHealth()
						<= victim.getMaxHealth() * Tuning.COUP_DE_GRACE_THRESHOLD) {
					// Executioner's idiom verbatim: past every resistance.
					result = Math.max(result, victim.getHealth() + 100.0F);
				}

				if (isPlayer || victim.getHealth()
						<= victim.getMaxHealth() * Tuning.COUP_DE_GRACE_THRESHOLD) {
					// The Executioner's own cue, dropped a fifth, so the player
					// learns which blow was the execute.
					level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
							net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT,
							net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.6F);
					ProcIndicators.send(player, SubTree.NEMESIS_ASSASSIN,
							com.archetypes.NemesisAssassinNodes.Family.COUP_DE_GRACE);
				}
			}

			// Stalker's Step: the same blink and strike, landing half again
			// harder while the night form holds.
			if (com.archetypes.NightForm.isActive(player)) {
				result *= Tuning.NIGHT_FORM_SHADOW_STEP_FACTOR;
			}

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

		// Aura of Radiance doesn't shove either (author's explicit spec): an
		// aura that punts the undead out of its own eight blocks would spend
		// its ten seconds undoing itself.
		if (com.archetypes.RadianceAura.isPulsing()) {
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
	 * The night form's undead-ness, at the one place 26.2 actually models it.
	 * {@code isInvertedHealAndHarm} is a tag test on the entity type; it is the
	 * single question vanilla's Instant Health / Instant Damage effect asks
	 * ({@code HealOrHarmMobEffect}), and the same one this mod's Holy Light
	 * burst and Aura of Radiance already ask. Answering "yes" for a transformed
	 * player therefore inverts healing potions AND every Priest heal at once,
	 * with nothing special-cased per potion.
	 *
	 * <p>Deliberately gated on {@link net.minecraft.world.entity.player.Player}
	 * rather than ServerPlayer: the attachment behind it syncs to every client,
	 * and client code asks this question too.
	 */
	@Inject(method = "isInvertedHealAndHarm", at = @At("HEAD"), cancellable = true)
	private void archetypes$nightFormIsUndead(
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof net.minecraft.world.entity.player.Player player
				&& com.archetypes.NightForm.isActive(player)) {
			cir.setReturnValue(true);
		}
	}

	/**
	 * Ghost Form: 25/50/75% of incoming hits simply pass through a body that
	 * is only half there. Rolled on the victim's intake like Sidestep, so it
	 * voids the whole hit whatever its source.
	 *
	 * <p>Sources tagged {@code bypasses_invulnerability} are the one exception:
	 * that tag is {@code out_of_world} and {@code generic_kill}, i.e. the void
	 * and {@code /kill}. Nothing this mod grants may make a hard kill fail —
	 * at rank 3 a /kill would miss three times in four.
	 */
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void archetypes$ghostForm(final ServerLevel level, final DamageSource source,
			final float amount,
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if (!((Object) this instanceof ServerPlayer player)
				|| !com.archetypes.NightForm.isActive(player)
				|| source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return;
		}

		int rank = com.archetypes.NightForm.rank(player,
				com.archetypes.NemesisShadowNodes.Family.GHOST_FORM);

		if (rank > 0 && player.getRandom().nextFloat() < Tuning.GHOST_FORM_NEGATE_PER_RANK * rank) {
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
					player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.25, 0.4, 0.25, 0.01);
			cir.setReturnValue(false);
		}
	}

	/**
	 * Feast: every attack a transformed player lands opens a bleed on the
	 * victim. Hung off the victim's intake rather than an AFTER_DAMAGE listener
	 * for the usual reason — that event is gated on the victim surviving, and a
	 * bleed opened by the blow that killed something else in the same swing
	 * would go missing.
	 */
	@Inject(method = "hurtServer", at = @At("HEAD"))
	private void archetypes$feast(final ServerLevel level, final DamageSource source,
			final float amount,
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if (source.getEntity() instanceof ServerPlayer player
				&& (Object) this instanceof LivingEntity victim) {
			com.archetypes.NightForm.onHit(player, victim);
		}
	}

	/**
	 * Incorporeal: nothing shoves a body that isn't there. Its own hook rather
	 * than a branch of the dagger funnel below, because knockback immunity has
	 * to hold for a sourceless shove too.
	 */
	@org.spongepowered.asm.mixin.injection.ModifyVariable(
			method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double archetypes$incorporealKnockback(final double strength) {
		return (Object) this instanceof ServerPlayer player
				&& com.archetypes.NightForm.isIncorporeal(player) ? 0.0 : strength;
	}

	/**
	 * Siege: a planted archer is not moved. The third condition on this
	 * overload, next to Steadfast's and Incorporeal's — the same reason as
	 * theirs, that knockback immunity has to hold for a sourceless shove too.
	 */
	@org.spongepowered.asm.mixin.injection.ModifyVariable(
			method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double archetypes$siegeKnockback(final double strength) {
		return (Object) this instanceof ServerPlayer player
				&& com.archetypes.Deadeye.isPlanted(player) ? 0.0 : strength;
	}

	/**
	 * On the Wing: no fall damage while the Deadeye stance holds. Its own
	 * cancelling injection rather than a branch of Ghost Form's, because Ghost
	 * Form is a dice roll on every source and this is a certainty about one.
	 * Slow Falling, the node's other half, is re-asserted by the ticker.
	 */
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void archetypes$onTheWing(final ServerLevel level, final DamageSource source,
			final float amount,
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ServerPlayer player
				&& source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)
				&& com.archetypes.Deadeye.isActive(player)
				&& com.archetypes.Deadeye.rank(player,
						com.archetypes.NemesisMarksmanNodes.Family.ON_THE_WING) > 0) {
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

	/**
	 * Unstoppable Force: a mace or a bare fist is not blocked, and the shield
	 * raised against it is knocked aside the way the Warden knocks it aside.
	 *
	 * <p>Both halves, in one hook, and the reason they cannot be split: vanilla
	 * routes the shield-disable through {@code Player.blockUsingItem}, which
	 * {@code applyItemBlocking} only calls when {@code damageBlocked > 0} — so
	 * an attack that blocks for nothing can never reach it. We therefore
	 * intercept {@code resolveBlockedDamage} itself, run the Warden's own call
	 * ({@code BlocksAttacks.disable}, the exact method
	 * {@code Warden.getSecondsToDisableBlocking}'s 5 seconds feed) by hand, and
	 * hand back zero.
	 *
	 * <p>Zeroing here rather than at the return of {@code applyItemBlocking} is
	 * deliberate: the blocked amount is also what pays the shield's durability
	 * ({@code hurtBlockingItem}) and what gates Iron Spikes and Braced. A hit
	 * that was never blocked must cost the blocker no durability and proc
	 * neither of their block rewards.
	 *
	 * <p>Melee only, and the attacker must BE the damage — a mace in hand does
	 * not make an arrow unblockable.
	 */
	@ModifyExpressionValue(method = "applyItemBlocking",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/item/component/BlocksAttacks;resolveBlockedDamage("
							+ "Lnet/minecraft/world/damagesource/DamageSource;FD)F"))
	private float archetypes$unstoppableForce(final float blocked, final ServerLevel level,
			final DamageSource source, final float damage) {
		if (blocked <= 0.0F || !(source.getEntity() instanceof ServerPlayer attacker)
				|| source.getDirectEntity() != attacker) {
			return blocked;
		}

		com.archetypes.WeaponClass weapon = com.archetypes.WeaponClass.of(attacker);

		if ((weapon != com.archetypes.WeaponClass.MACE && weapon != com.archetypes.WeaponClass.HANDS)
				|| com.archetypes.TitansLeap.rank(attacker,
						com.archetypes.ColossusCrusherNodes.Family.SIEGEBREAKER) <= 0) {
			return blocked;
		}

		LivingEntity blocker = (LivingEntity) (Object) this;
		net.minecraft.world.item.ItemStack blockingWith = blocker.getItemBlockingWith();
		var blocksAttacks = blockingWith == null ? null
				: blockingWith.get(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS);

		if (blocksAttacks != null) {
			blocksAttacks.disable(level, blocker, Tuning.UNSTOPPABLE_DISABLE_SECONDS, blockingWith);
		}

		com.archetypes.TitansLeap.unstoppableCue(attacker, level, blocker);
		return 0.0F;
	}

	/**
	 * Immovable: nothing shoves a Colossus. Its own hook next to Incorporeal's
	 * and Siege's, for their reason — knockback immunity has to hold for a
	 * sourceless shove too — rather than a KNOCKBACK_RESISTANCE modifier, so
	 * the node can announce itself when it actually eats something. Explosions
	 * and wind charges never reach this method; that clause lives on
	 * EXPLOSION_KNOCKBACK_RESISTANCE in {@code CrusherTicker}.
	 */
	@org.spongepowered.asm.mixin.injection.ModifyVariable(
			method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double archetypes$immovableKnockback(final double strength) {
		if (strength <= 0.0 || !((Object) this instanceof ServerPlayer player)
				|| com.archetypes.TitansLeap.rank(player,
						com.archetypes.ColossusCrusherNodes.Family.IMMOVABLE) <= 0) {
			return strength;
		}

		com.archetypes.TitansLeap.immovableCue(player);
		return 0.0;
	}

	/**
	 * No fall damage for an Immovable Colossus, and none from a Titan's Leap.
	 * A cancelling inject rather than a {@code fallDistance} reset, which is
	 * the whole point: the leap exists to feed Meteor, Shockwave and vanilla's
	 * own mace smash, and every one of them reads the fall it would have
	 * zeroed. Same shape as On the Wing's.
	 */
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void archetypes$titansLeapFall(final ServerLevel level, final DamageSource source,
			final float amount,
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ServerPlayer player
				&& source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)
				&& (com.archetypes.TitansLeap.isLeaping(player)
						|| com.archetypes.TitansLeap.rank(player,
								com.archetypes.ColossusCrusherNodes.Family.IMMOVABLE) > 0)) {
			cir.setReturnValue(false);
		}
	}

	/**
	 * Bulwark: 20% per rank off everything, but only while Battle Trance is
	 * actually holding banked health. Same shape as Mana Shield's — a
	 * victim-side {@code ModifyVariable} on the shared {@code amount}, so it
	 * composes multiplicatively with whatever else shaped the hit.
	 */
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurtServer",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$colossusBulwark(final float amount, final ServerLevel level,
			final DamageSource source) {
		if (!((Object) this instanceof ServerPlayer player)
				|| !com.archetypes.TitansLeap.bulwarkHolding(player)) {
			return amount;
		}

		int rank = com.archetypes.TitansLeap.rank(player,
				com.archetypes.ColossusCrusherNodes.Family.BULWARK);

		// The flash is for hits, not for drowning or a burning tick: those
		// arrive every tick and would strobe the indicator. The reduction
		// itself still applies to every source.
		if (source.getEntity() != null) {
			ProcIndicators.send(player, SubTree.COLOSSUS_CRUSHER,
					com.archetypes.ColossusCrusherNodes.Family.BULWARK);
		}

		return amount * Math.max(0.0F, 1.0F - Tuning.COLOSSUS_BULWARK_DR_PER_RANK * rank);
	}

	/**
	 * Instinctive Guard: a carried shield blocks a share of every hit without
	 * ever being raised. A victim-side {@code ModifyVariable} on the shared
	 * {@code amount}, Mana Shield's shape, so it composes with the rest of the
	 * funnel and lands before armour — which is where vanilla's own blocking
	 * lands too. The whole of the rule is in
	 * {@link com.archetypes.ColossusProtector#instinctiveGuard}.
	 */
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurtServer",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$instinctiveGuard(final float amount, final ServerLevel level,
			final DamageSource source) {
		return (Object) this instanceof ServerPlayer player
				? com.archetypes.ColossusProtector.instinctiveGuard(player, level, source, amount)
				: amount;
	}

	/**
	 * Free Hand: the shield stays up while the hands eat.
	 *
	 * <p>Cancelling at the head of {@code getItemBlockingWith} rather than
	 * anywhere in the damage path on purpose — that method is the single
	 * definition of "is this entity blocking, and with what", so answering it
	 * puts the whole of vanilla's blocking back on at once: the arc test, the
	 * durability, the block sound, {@code blockUsingItem} and with it Iron
	 * Spikes and Braced, and the shield-up pose every watching client draws.
	 * Nothing about blocking is re-implemented.
	 *
	 * <p>Common, not server-side: the client asks the same question to pose the
	 * player and to decide its own hits, and it holds the synced node list.
	 */
	@Inject(method = "getItemBlockingWith", at = @At("HEAD"), cancellable = true)
	private void archetypes$freeHand(
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<
					net.minecraft.world.item.ItemStack> cir) {
		if (!((Object) this instanceof net.minecraft.world.entity.player.Player player)) {
			return;
		}

		net.minecraft.world.item.ItemStack shield =
				com.archetypes.ColossusProtector.freeHandBlock(player);

		if (shield != null) {
			cir.setReturnValue(shield);
		}
	}

	/**
	 * Free Hand's durability, charged to the hand that actually blocked.
	 * {@code applyItemBlocking} passes {@code getUsedItemHand()} straight into
	 * {@code hurtBlockingItem}, which turns it into the equipment slot the
	 * break is reported against — for a free-hand block that is the hand
	 * holding the food, not the shield.
	 */
	@ModifyExpressionValue(method = "applyItemBlocking",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;getUsedItemHand()"
							+ "Lnet/minecraft/world/InteractionHand;"))
	private net.minecraft.world.InteractionHand archetypes$freeHandDurability(
			final net.minecraft.world.InteractionHand hand) {
		if (!((Object) this instanceof net.minecraft.world.entity.player.Player player)) {
			return hand;
		}

		net.minecraft.world.InteractionHand blocking =
				com.archetypes.ColossusProtector.freeHandBlockHand(player);
		return blocking == null ? hand : blocking;
	}

	/**
	 * Free Hand's block sound. {@code hurtServer} reads the component it plays
	 * {@code onBlocked} from off {@code getUseItem()} rather than off whatever
	 * {@code applyItemBlocking} decided had blocked — same assumption, that the
	 * two are one item. Handing it the shield is what makes a free-hand block
	 * sound like the block it is.
	 */
	@ModifyExpressionValue(method = "hurtServer",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;getUseItem()"
							+ "Lnet/minecraft/world/item/ItemStack;"))
	private net.minecraft.world.item.ItemStack archetypes$freeHandBlockSound(
			final net.minecraft.world.item.ItemStack itemInUse) {
		if (!((Object) this instanceof net.minecraft.world.entity.player.Player player)) {
			return itemInUse;
		}

		net.minecraft.world.item.ItemStack shield =
				com.archetypes.ColossusProtector.freeHandBlock(player);
		return shield == null ? itemInUse : shield;
	}
}
