package com.archetypes.mixin;

import com.archetypes.CrusherNodes;
import com.archetypes.WeaponClass;
import com.archetypes.ModState;
import com.archetypes.ModItems;
import com.archetypes.NodePurchases;
import com.archetypes.ProcIndicators;
import com.archetypes.ProtectorNodes;
import com.archetypes.SlayerNodes;
import com.archetypes.SubTree;
import com.archetypes.Tuning;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import com.archetypes.platform.ArchetypeStore;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
	// ARITY FORK, conventions §5a: the annotation and the parameter list move, the shared
	// impl never does. 26.2 hands `blockedByItem` the source and the blocked amount; 26.1
	// declares `blockedByItem(LivingEntity)` and has neither. Nothing is lost — the impl
	// takes `blocked` and never reads it (Braced keys on the block happening at all, Iron
	// Spikes rolls vanilla's own thorns numbers), so the legacy arm passes 0.
	//
	// STAGE 4 adds the third arm: 1.21.11 RENAMED the pair. `blockUsingShield(attacker)` is
	// called ON THE BLOCKER and does `attacker.blockedByShield(blocker)` — byte for byte
	// the same shape as `blockUsingItem` -> `blockedByItem` (both read out of the two
	// jars), so `this` is still the ATTACKER and the parameter is still the BLOCKER. Iron
	// Spikes and Braced therefore port with a name change and nothing else; they are NOT
	// part of the excised shield-modifier cluster.
	//
	// TRAP, learned here: a line comment placed inside a DISABLED arm but outside its
	// `/* */` is eaten when the arm is enabled — Stonecutter strips a leading `//` as part
	// of un-commenting. Notes go above the whole chain, or inside the block comment.
	//? if >=26.2 {
	@Inject(method = "blockedByItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)V", at = @At("TAIL"))
	private void archetypes$onShieldBlocked(final LivingEntity blocker, final DamageSource source,
			final float blocked, final CallbackInfo ci) {
		archetypes$onShieldBlockedImpl(blocker, blocked);
	}
	//?} elif >=1.21.11 {
	/*@Inject(method = "blockedByItem(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("TAIL"))
	private void archetypes$onShieldBlocked(final LivingEntity blocker, final CallbackInfo ci) {
		archetypes$onShieldBlockedImpl(blocker, 0.0F);
	}
	*///?} else {
	/*@Inject(method = "blockedByShield(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("TAIL"))
	private void archetypes$onShieldBlocked(final LivingEntity blocker, final CallbackInfo ci) {
		archetypes$onShieldBlockedImpl(blocker, 0.0F);
	}
	*///?}

	/** Shared implementation of {@link #archetypes$onShieldBlocked}. */
	@Unique
	private void archetypes$onShieldBlockedImpl(final LivingEntity blocker, final float blocked) {
		if (!(blocker instanceof ServerPlayer player)) {
			return;
		}

		LivingEntity attacker = (LivingEntity) (Object) this;
		var owned = NodePurchases.owned(player, SubTree.PROTECTOR);

		// Braced: a blocked hit shaves a second off the bash's countdown — the
		// tree's loop closing: blocking feeds bashing feeds blocking.
		if (ProtectorNodes.rank(SubTree.PROTECTOR, owned, ProtectorNodes.Family.BRACED) > 0) {
			final Entity target = player;
			Long readyAt = ArchetypeStore.INSTANCE.get(target, ModState.BASH_READY_AT);
			long now = player.level().getGameTime();

			if (readyAt != null && readyAt > now) {
				ArchetypeStore.INSTANCE.set(target, ModState.BASH_READY_AT,
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
		//? if >=1.21.2 {
		attacker.hurtServer((ServerLevel) player.level(),
				player.damageSources().thorns(player), damage);
		//?} else {
		/*attacker.hurt(
				player.damageSources().thorns(player), damage);
		*///?}
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
		archetypes$lungeImpl(hand);
	}

	/** Shared implementation of {@link #archetypes$lunge}. */
	@Unique
	private void archetypes$lungeImpl(final net.minecraft.world.InteractionHand hand) {
		if (!((Object) this instanceof ServerPlayer player)
				|| hand != net.minecraft.world.InteractionHand.MAIN_HAND
				|| !player.isSprinting()
				|| !ModItems.isSword(player.getMainHandItem())) {
			return;
		}

		final Entity target = player;

		if (ArchetypeStore.INSTANCE.get(target, ModState.BLADESTORM_END) != null) {
			return;
		}

		int rank = SlayerNodes.rank(SubTree.SLAYER,
				NodePurchases.owned(player, SubTree.SLAYER), SlayerNodes.Family.LUNGE);

		if (rank <= 0) {
			return;
		}

		long now = player.level().getGameTime();
		Long readyAt = ArchetypeStore.INSTANCE.get(target, ModState.LUNGE_READY_AT);

		if (readyAt != null && now < readyAt) {
			return;
		}

		double impulse = rank * Tuning.LUNGE_BLOCKS_PER_RANK * Tuning.RUSH_IMPULSE_PER_BLOCK;
		var look = player.getLookAngle();
		player.setDeltaMovement(player.getDeltaMovement().add(look.scale(impulse)));
		player.hurtMarked = true;
		ArchetypeStore.INSTANCE.set(target, ModState.LUNGE_READY_AT, now + Tuning.LUNGE_COOLDOWN_TICKS);
		((ServerLevel) player.level()).sendParticles(
				net.minecraft.core.particles.ParticleTypes.CLOUD,
				player.getX(), player.getY() + 0.1, player.getZ(), 3, 0.15, 0.02, 0.15, 0.01);
	}

	/**
	 * The greatsword's damage shaping, all in one place on the victim's intake:
	 * Heavy Blows' flat bonus, First Blood's opener bonus against the unhurt,
	 * the Executioner's finisher on anything already below the threshold, and
	 * Blade Master's armour penetration.
	 *
	 * <p>Blade Master runs LAST, Flense's placement and for Flense's reason: the
	 * node ignores a share of the victim's armour, and what armour is worth
	 * depends on how big the blow is — vanilla degrades it by
	 * {@code damage/toughness} before applying it. The value fed to the curve has
	 * to be the damage that will actually arrive, which is everything above this
	 * line and nothing below it.
	 *
	 * <p>Its interaction with the Executioner clamp is deliberate and worth
	 * naming: the clamp writes {@code health + 100} as a RAW number that vanilla
	 * then mitigates, so against a full suit the finisher used to deliver a
	 * couple of points and fail to finish. Compensating it afterwards is what
	 * makes the clamp mean what it says.
	 *
	 * <p>A Decimate reaches this hook too — {@code SlayerActives.resolve} opens
	 * a {@code MeleeSwing} around its damage precisely so it does.
	 */
	// ---- THE `hurtServer` FAMILY FORK. Read this once; the other sixteen in this file, the
	// two in DamageTraceMixin, the one in FlenseMixin and the one in HardenedMixin are the
	// same shape and carry no note of their own. ----
	//
	// 1.21.2 split `hurt` into `hurtServer`/`hurtClient`. Below it the one method is
	// `hurt(DamageSource,F)Z`, it is the same funnel, and it RUNS ON BOTH LOGICAL SIDES —
	// vanilla's own `return false` for a client level sits at bytecode offset 10-21, i.e.
	// AFTER every HEAD injection. So the legacy arm needs an `isClientSide()` early-out that
	// the arm above must NOT have, and that early-out is what makes the two arms mean the
	// same thing: "this handler sees exactly the calls `hurtServer` would have seen".
	// Without it ten of these Impls throw on the first client-side hit — they open with
	// `(ServerLevel) this.level()`.
	//
	// THE FORK IS THE SIGNATURE ONLY: the annotation, the parameter list and the opening
	// brace. The body stays outside the block, shared, exactly as conventions §5a requires —
	// one implementation for every node, and no balance expression written twice. The
	// `ServerLevel level` parameter is not lost, it was never used: Stage 0-D pushed it off
	// every impl, which derives it from `this.level()`.
	//
	// It also has to be the signature only for a MEASURED reason (§5.5.1 finding 3): the
	// three prior nodes' transformed classes must not move an instruction, and hoisting the
	// early-out or the delegation into a shared helper adds a method and a call to all of
	// them. The knockback stash below is the same lesson, learned the same way.
	//
	// `@ModifyVariable` handlers capture the value plus a PREFIX of the target's arguments
	// (this is what the shipped 26.2 jar already does — value + 2 of `hurtServer`'s 3), so
	// the legacy arm's `(float, DamageSource)` is the same construction one argument shorter.
	//? if >=1.21.2 {
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$greatswordDamage(final float amount, final ServerLevel level,
			final DamageSource source) {
	//?} else {
	/*@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$greatswordDamage(final float amount, final DamageSource source) {
		if (((LivingEntity) (Object) this).level().isClientSide()) {
			return amount;
		}

	*///?}
		return archetypes$greatswordDamageImpl(amount, source);
	}

	/** Shared implementation of {@link #archetypes$greatswordDamage}. */
	@Unique
	private float archetypes$greatswordDamageImpl(final float amount, final DamageSource source) {
		ServerLevel level = (ServerLevel) ((LivingEntity) (Object) this).level();

		if (!(source.getEntity() instanceof ServerPlayer player)
				|| source.getDirectEntity() != player
				|| !com.archetypes.MeleeSwing.isSwinging(player)
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

		// Blade Master, LAST — see the class comment above and Flense's twin in
		// archetypes$daggerDamage.
		result *= com.archetypes.ColossusSlayer.bladeMasterFactor(player, level, victim, source,
				result);

		com.archetypes.DamageTrace.record(com.archetypes.DamageTrace.STAGE_GREATSWORD, amount, result);
		return result;
	}

	/**
	 * Blade Master's armour half on an ordinary sword — the same 25%/rank armour
	 * ignore and 4/rank Protection bite the greatsword gets, through the same
	 * {@code ArmourMath} pre-compensation, and nothing else. The swing-time
	 * penalty is greatsword-only and lives on the attribute (see
	 * {@code ColossusSlayer.tickStance}); a sword's speed is the reason to carry
	 * one, so the node buys plate-cutting on both blades and charges for it on
	 * only one.
	 *
	 * <p><b>Why a hook of its own rather than a branch of
	 * {@link #archetypes$greatswordDamage}.</b> The weapon gates are disjoint by
	 * construction — {@code ModItems.isSword} is the vanilla {@code swords} tag
	 * MINUS this mod's greatswords and daggers, both of which are added to that
	 * tag by {@code data/minecraft/tags/item/swords.json} — so no stack can
	 * satisfy both and the node can never apply twice to one blow. Keeping them
	 * apart is what makes that readable: this method has one stage, the other has
	 * four, and the trace prints them as separate stages for the same reason.
	 *
	 * <p><b>The gate is the greatsword path's, verbatim</b>, and it is what keeps
	 * the tree's own damage-over-time out: a Rend bleed pulses from
	 * {@code SlayerTicker.tickBleeds} on the server tick, dealt as
	 * {@code playerAttack(source)} so the kill still credits the blade that
	 * opened the wound — attacker and direct entity both the player, which is
	 * exactly what a swing looks like to a {@code DamageSource}. No
	 * {@code MeleeSwing} is open around it, so it is not one, and a wound does not
	 * cut plate. Same for every {@code indirectMagic(player, player)} pulse in the
	 * mod.
	 *
	 * <p>Runs against {@code amount} at HEAD, which for a plain sword is the whole
	 * blow: no other shaper this mod owns answers to {@code isSword}. A sibling
	 * mod's multipliers on the same funnel are a coin flip against ours (see
	 * {@code archetypes$daggerDamage}'s note and {@code FlenseMixin}), so the
	 * armour compensation may be computed either side of Skill Proficiencies'
	 * combat multiplier — the same open question the greatsword path already has,
	 * and deliberately not answered differently here.
	 */
	//? if >=1.21.2 {
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$swordDamage(final float amount, final ServerLevel level,
			final DamageSource source) {
	//?} else {
	/*@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$swordDamage(final float amount, final DamageSource source) {
		if (((LivingEntity) (Object) this).level().isClientSide()) {
			return amount;
		}

	*///?}
		return archetypes$swordDamageImpl(amount, source);
	}

	/** Shared implementation of {@link #archetypes$swordDamage}. */
	@Unique
	private float archetypes$swordDamageImpl(final float amount, final DamageSource source) {
		ServerLevel level = (ServerLevel) ((LivingEntity) (Object) this).level();

		if (!(source.getEntity() instanceof ServerPlayer player)
				|| source.getDirectEntity() != player
				|| !com.archetypes.MeleeSwing.isSwinging(player)
				|| !ModItems.isSword(player.getMainHandItem())) {
			return amount;
		}

		LivingEntity victim = (LivingEntity) (Object) this;
		float result = amount * com.archetypes.ColossusSlayer.bladeMasterFactor(player, level,
				victim, source, amount);

		com.archetypes.DamageTrace.record(com.archetypes.DamageTrace.STAGE_SWORD, amount, result);
		return result;
	}

	/**
	 * The Marksman's on-hit passives ride the victim's damage intake, before
	 * death resolution — Combustion on a kill-shot must still detonate, and
	 * AFTER_DAMAGE never fires for lethal hits.
	 */
	//? if >=1.21.2 {
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$marksmanArrowHit(final float amount, final ServerLevel level,
			final DamageSource source) {
	//?} else {
	/*@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$marksmanArrowHit(final float amount, final DamageSource source) {
		if (((LivingEntity) (Object) this).level().isClientSide()) {
			return amount;
		}

	*///?}
		return archetypes$marksmanArrowHitImpl(amount, source);
	}

	/** Shared implementation of {@link #archetypes$marksmanArrowHit}. */
	@Unique
	private float archetypes$marksmanArrowHitImpl(final float amount, final DamageSource source) {
		ServerLevel level = (ServerLevel) ((LivingEntity) (Object) this).level();

		if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow
				&& source.getEntity() instanceof ServerPlayer player
				&& com.archetypes.MarksmanCombat.fromMarksmanWeapon(arrow)) {
			float result = com.archetypes.MarksmanCombat.onArrowHit(player,
					(LivingEntity) (Object) this, level, arrow, amount);
			com.archetypes.DamageTrace.record(com.archetypes.DamageTrace.STAGE_MARKSMAN, amount,
					result);
			return result;
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
	 *
	 * <p>The sword's branch is swing-gated, the arrow's is not, and the split is
	 * the whole point of {@link com.archetypes.MeleeSwing}: "the conjured sword
	 * hit them" is a claim about a swing, and being the direct entity is not
	 * evidence of one. A wielder standing in their own Aura of Radiance or
	 * bleeding someone with Feast is the direct entity of every pulse — both are
	 * dealt as {@code indirectMagic(player, player)}, which names the player as
	 * direct entity AND causing entity — so without the gate a channelled sword
	 * doubled a DoT it had nothing to do with, once a second. The Spellbow's
	 * arrow is a projectile and can never be confused for a swing, so it keeps
	 * answering on its own marked-arrow evidence.
	 */
	//? if >=1.21.2 {
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$magicArmamentHit(final float amount, final ServerLevel level,
			final DamageSource source) {
	//?} else {
	/*@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$magicArmamentHit(final float amount, final DamageSource source) {
		if (((LivingEntity) (Object) this).level().isClientSide()) {
			return amount;
		}

	*///?}
		return archetypes$magicArmamentHitImpl(amount, source);
	}

	/** Shared implementation of {@link #archetypes$magicArmamentHit}. */
	@Unique
	private float archetypes$magicArmamentHitImpl(final float amount, final DamageSource source) {
		ServerLevel level = (ServerLevel) ((LivingEntity) (Object) this).level();

		if (!(source.getEntity() instanceof ServerPlayer player)) {
			return amount;
		}

		float result;

		if (source.getDirectEntity() == player) {
			result = com.archetypes.MeleeSwing.isSwinging(player)
					&& com.archetypes.ModItems.isMagicSword(player.getMainHandItem())
							? com.archetypes.MagicArmaments.shapeHit(player, level, amount, false)
							: amount;
		} else {
			result = source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow
					&& Boolean.TRUE.equals(
							ArchetypeStore.INSTANCE.get(arrow, ModState.SPELLBOW_ARROW))
									? com.archetypes.MagicArmaments.shapeHit(player, level, amount, true)
									: amount;
		}

		com.archetypes.DamageTrace.record(com.archetypes.DamageTrace.STAGE_ARMAMENTS, amount, result);
		return result;
	}

	/**
	 * The dagger's damage shaping and DoTs, all on the victim's intake:
	 * Razor Edge's steel, Expose's finishing bonus, one summed ambush box for
	 * everything Death Mark, the dark and the Shadow Step are worth — and the
	 * Venom/Blight coatings applied here so they land even on killing blows.
	 *
	 * <p>Nothing left in this method is order-sensitive: two multiplies, one
	 * sum, one multiply, and a floor. The one stage that WAS, Flense, has moved
	 * out to {@link FlenseMixin} — it has to run after every other HEAD shaper
	 * on this funnel including a sibling mod's, and a position inside this
	 * method could only ever order it against our own.
	 *
	 * <p>Swing-gated, and this branch needed it worst of all, because the coatings
	 * are APPLIED here rather than merely scaled. A dagger is the Nemesis Shadow's
	 * weapon too, and the night form's Feast pulses a bleed as
	 * {@code indirectMagic(feeder, feeder)} — which names the player as the
	 * direct entity, exactly like a swing — so every second of every bleed used to
	 * re-poison, re-wither and re-slow the victim and take Razor Edge, Expose,
	 * Flense and Death Mark's multipliers on the pulse. Aura of Radiance, the
	 * Blizzard, Contagion and a lightning chain all wear the same source and did
	 * the same. Shadow Step's strike is unaffected: it goes through
	 * {@code Player.attack} ({@code AgilityActives.strike}) and so is a swing by
	 * the only definition that counts.
	 */
	//? if >=1.21.2 {
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$daggerDamage(final float amount, final ServerLevel level,
			final DamageSource source) {
	//?} else {
	/*@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$daggerDamage(final float amount, final DamageSource source) {
		if (((LivingEntity) (Object) this).level().isClientSide()) {
			return amount;
		}

	*///?}
		return archetypes$daggerDamageImpl(amount, source);
	}

	/** Shared implementation of {@link #archetypes$daggerDamage}. */
	@Unique
	private float archetypes$daggerDamageImpl(final float amount, final DamageSource source) {
		ServerLevel level = (ServerLevel) ((LivingEntity) (Object) this).level();

		if (!(source.getEntity() instanceof ServerPlayer player)
				|| source.getDirectEntity() != player
				|| !com.archetypes.MeleeSwing.isSwinging(player)
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

		// --- the ambush box ---
		//
		// One sum, one multiply. Death Mark and Headhunter ride every dagger
		// hit; the rest are Shadow Step only. They used to be six separate
		// multiplications and the product had no ceiling — x25.3 with all of
		// them lit, which is how one wrong Flense constant became a 377-damage
		// opener into a 40 HP pool. Adding a seventh term to a sum costs its
		// own face value; adding a seventh multiplier cost its face value times
		// everything already there.
		//
		// Both mark stamps are checked, not just the flag on the body: the
		// ticker takes a lapsed mark off a tick later than it expires.
		boolean marked = com.archetypes.DeathMark.isMarkedBy(victim, player)
				&& com.archetypes.DeathMark.hasMark(player);
		float ambush = 1.0F;

		if (marked) {
			ambush += Tuning.DEATH_MARK_DAMAGE_FACTOR;
			ambush += Tuning.HEADHUNTER_PER_RANK
					* com.archetypes.NemesisAssassinNodes.rank(player,
							com.archetypes.NemesisAssassinNodes.Family.HEADHUNTER);
		}

		// First Strike and Bloodrush, the two terms that used to be Strength on
		// the ATTACK_DAMAGE attribute and are now summands like everything else.
		//
		// The move is the box's founding argument applied to its last two
		// exceptions: a flat +6 sitting BELOW this multiply was worth +6 x 7.55,
		// which is not a node, it is a second capstone. Their gates are
		// unchanged — invisibility for First Strike, a kill from the dark inside
		// the last four seconds for Bloodrush (ShadowTicker owns the window).
		int firstStrike = com.archetypes.ShadowNodes.rank(SubTree.SHADOW,
				NodePurchases.owned(player, SubTree.SHADOW),
				com.archetypes.ShadowNodes.Family.FIRST_STRIKE);

		// hasEffect rather than isInvisible(): the ticker's gate, kept verbatim,
		// so the node still means "from the dark" and not "from a spectator".
		if (firstStrike > 0
				&& player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
			ambush += Tuning.FIRST_STRIKE_PER_RANK * firstStrike;
		}

		ambush += com.archetypes.ShadowTicker.bloodrushBonus(player);

		Long stepStrike = ArchetypeStore.INSTANCE.get(player, ModState.STEP_STRIKE_AT);
		boolean stepping = stepStrike != null && stepStrike == level.getGameTime();
		boolean executing = false;

		if (stepping) {
			// The night form adds nothing here: the Dark Ritual no longer
			// empowers the Shadow Step, so a transformed player's opener is
			// the same blow a mortal one throws.

			// Shadow Flurry lands the strike with several daggers' weight; Twin
			// Fangs brings the off-hand dagger into the same blow at its own
			// weight against the main hand's, so a bare off-hand gives nothing
			// and the node nudges toward actually wearing two knives.
			if (com.archetypes.AssassinNodes.rank(SubTree.ASSASSIN, owned,
					com.archetypes.AssassinNodes.Family.SHADOW_FLURRY) > 0) {
				ambush += Tuning.SHADOW_FLURRY_BONUS;
			}

			if (com.archetypes.AssassinNodes.rank(SubTree.ASSASSIN, owned,
					com.archetypes.AssassinNodes.Family.TWIN_FANGS) > 0) {
				float main = ModItems.daggerSwingDamage(player.getMainHandItem());
				float off = ModItems.daggerSwingDamage(player.getOffhandItem());

				if (main > 0.0F && off > 0.0F) {
					ambush += Tuning.TWIN_FANGS_OFFHAND_BONUS * off / main;
				}
			}

			// Coup de Grace, on the Shadow Step strike only. Players are never
			// deleted outright — the rule Executioner already follows — so the
			// execute pays them out as weight in the box, and unconditionally:
			// an execute a player only ever meets under a third health would be
			// no node at all in the fight the branch is built for. In the box,
			// not over it: it is the one player-exclusive term, and leaving it
			// outside would hand the whole collapse back to the single build
			// that owns the capstone.
			if (marked && com.archetypes.NemesisAssassinNodes.rank(player,
					com.archetypes.NemesisAssassinNodes.Family.COUP_DE_GRACE) > 0) {
				boolean isPlayer = victim instanceof net.minecraft.world.entity.player.Player;
				boolean finishable = victim.getHealth()
						<= victim.getMaxHealth() * Tuning.COUP_DE_GRACE_THRESHOLD;

				if (isPlayer) {
					ambush += Tuning.COUP_DE_GRACE_PLAYER_BONUS;
				} else if (finishable) {
					executing = true;
				}

				if (isPlayer || finishable) {
					// The Executioner's own cue, dropped a fifth, so the player
					// learns which blow was the execute.
					level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
							net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT,
							net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.6F);
					ProcIndicators.send(player, SubTree.NEMESIS_ASSASSIN,
							com.archetypes.NemesisAssassinNodes.Family.COUP_DE_GRACE);
				}
			}
		}

		result *= ambush;

		if (executing) {
			// Executioner's idiom verbatim: past every resistance.
			//
			// Ordering vs Skill Proficiencies' multipliers is a COIN FLIP, not a
			// fact: both mods' hurtServer handlers sit at priority 1000, and the
			// 2026-07-26 trace session measured SP running FIRST on that install
			// (dagger stage's `before` was 7.80 x 4.50 = 35.10) — the opposite of
			// what this comment used to assert. So do not rely on the floor being
			// multiplied up by SP; what reaches vanilla may be the bare
			// (health + 100), which then eats armour and Protection (x0.84 x0.36
			// on a full netherite kit = x0.302). Consequence, measured: on an
			// armoured mob the "guaranteed" execute only actually kills while
			// health < ~43; a beefier armoured mob at 30% can survive it.
			// Compensating through armour here would need ArmourMath against the
			// victim's live stats — parked; mob-only path, low frequency.
			result = Math.max(result, victim.getHealth() + 100.0F);
		}

		// Flense USED to run here, last, and it no longer lives in this method
		// at all — see FlenseMixin, which is the same code at priority 1500 so
		// that it runs after every other hurtServer HEAD shaper on the funnel,
		// this mod's and Skill Proficiencies' alike. The reason it had to move
		// is in that class's comment: at the magnitudes this box now produces,
		// the armour curve's answer depends on which side of a sibling's x1.5
		// the stage is fed, to the tune of 13%.

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

		com.archetypes.DamageTrace.record(com.archetypes.DamageTrace.STAGE_DAGGER, amount, result);
		return result;
	}

	/**
	 * Daggers shove half as hard: a knife fighter wants the target IN reach,
	 * and the flurry was pushing its own victim away. All knockback funnels
	 * through this one overload (the five-arg delegates here).
	 */
	// RE-ROOTED below 26.2, and the source arrives out of band — see KnockbackSource for
	// the caller census that picked the wrap sites and for what is deliberately NOT
	// covered. The injection POINT is unchanged (HEAD of knockback, first double), so the
	// factor is still applied in exactly one place; only how the source gets there moves.
	//? if >=26.2 {
	@org.spongepowered.asm.mixin.injection.ModifyVariable(
			method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double archetypes$daggerKnockback(final double strength, final double strengthArg,
			final double x, final double z, final DamageSource source, final float yStrength,
			final boolean spinAttack) {
		return archetypes$daggerKnockbackImpl(strength, source);
	}
	//?} else {
	/*@org.spongepowered.asm.mixin.injection.ModifyVariable(
			method = "knockback(DDD)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double archetypes$daggerKnockback(final double strength) {
		return archetypes$daggerKnockbackImpl(strength, com.archetypes.KnockbackSource.current());
	}
	*///?}

	/** Shared implementation of {@link #archetypes$daggerKnockback}. */
	@Unique
	private double archetypes$daggerKnockbackImpl(final double strength, final DamageSource source) {
		if (source == null) {
			return strength;
		}

		// A blizzard pulse doesn't shove at all: the storm was pushing its
		// victims out of its own square every second (user call).
		if (com.archetypes.BlizzardZones.isPulsing()) {
			return 0.0;
		}

		// Nor does a Rend pulse (author's call): a wound is not a blow, and a
		// bleeding mob was being nudged back once a second by its own bleed.
		if (com.archetypes.SlayerTicker.isBleeding()) {
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
	//? if >=1.21.2 {
	@Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
	private void archetypes$sidestep(final ServerLevel level, final DamageSource source,
			final float amount, final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
	//?} else {
	/*@Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
	private void archetypes$sidestep(final DamageSource source, final float amount,
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if (((LivingEntity) (Object) this).level().isClientSide()) {
			return;
		}

	*///?}
		archetypes$sidestepImpl(source, cir);
	}

	/** Shared implementation of {@link #archetypes$sidestep}. */
	@Unique
	private void archetypes$sidestepImpl(final DamageSource source, final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		ServerLevel level = (ServerLevel) ((LivingEntity) (Object) this).level();

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
	//? if >=1.21.2 {
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$manaShield(final float amount, final ServerLevel level,
			final DamageSource source) {
	//?} else {
	/*@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$manaShield(final float amount, final DamageSource source) {
		if (((LivingEntity) (Object) this).level().isClientSide()) {
			return amount;
		}

	*///?}
		return archetypes$manaShieldImpl(amount);
	}

	/** Shared implementation of {@link #archetypes$manaShield}. */
	@Unique
	private float archetypes$manaShieldImpl(final float amount) {
		ServerLevel level = (ServerLevel) ((LivingEntity) (Object) this).level();

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

		com.archetypes.DamageTrace.record(com.archetypes.DamageTrace.STAGE_MANA_SHIELD, amount,
				amount - absorbed);
		return amount - absorbed;
	}

	// First Strike has its own hook no longer, and no attribute grant either:
	// it is a summand inside the ambush box above, next to Bloodrush. The
	// Strength detour it took in between was the worst of the three forms —
	// a flat +6 UNDER a x7.55 multiply — and both nodes came back into the box
	// in the same pass. DamageTrace's mirror moved with them.

	/**
	 * Dim Presence: while sneaking, mobs notice this player 20% per rank less
	 * — the same channel sneaking and invisibility already use, so it stacks
	 * multiplicatively with both (and with Specialities' Sneaking, which
	 * modifies the same return).
	 */
	@com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "getVisibilityPercent(Lnet/minecraft/world/entity/Entity;)D",
			at = @At("RETURN"))
	private double archetypes$dimPresence(final double original) {
		return archetypes$dimPresenceImpl(original);
	}

	/** Shared implementation of {@link #archetypes$dimPresence}. */
	@Unique
	private double archetypes$dimPresenceImpl(final double original) {
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
	 *
	 * <p>Swing-gated, and the gate guards far more than Sunder's own number: this
	 * hook is also the only call site of {@link com.archetypes.CrusherCombat#onCrusherHit},
	 * so Shockwave's splash and every point of Battle Trance absorption hang off
	 * it. A mace Colossus can carry a shield — {@code WeaponClass.MACE} asks only
	 * about the main hand — so an Iron Spikes reflect was a full Crusher on-hit
	 * batch: armour shredded, a wave rung out, and a plate of absorption banked,
	 * for a blow the player answered by standing still. Bare fists escaped that
	 * one by accident ({@code HANDS} demands an empty off-hand, so no shield), but
	 * not the DoTs, which name the player as direct entity whatever is held.
	 *
	 * <p>Quake, Haymaker and Aftershock all mark themselves as swings for this
	 * hook's benefit — they are the mace doing its job, and Meteor and the batch
	 * are supposed to ride them.
	 */
	//? if >=1.21.2 {
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$sunderDamage(final float amount, final ServerLevel level,
			final DamageSource source) {
	//?} else {
	/*@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$sunderDamage(final float amount, final DamageSource source) {
		if (((LivingEntity) (Object) this).level().isClientSide()) {
			return amount;
		}

	*///?}
		return archetypes$sunderDamageImpl(amount, source);
	}

	/** Shared implementation of {@link #archetypes$sunderDamage}. */
	@Unique
	private float archetypes$sunderDamageImpl(final float amount, final DamageSource source) {
		ServerLevel level = (ServerLevel) ((LivingEntity) (Object) this).level();

		if (!(source.getEntity() instanceof ServerPlayer player)
				|| source.getDirectEntity() != player
				|| !com.archetypes.MeleeSwing.isSwinging(player)) {
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
		Long stamp = ArchetypeStore.INSTANCE.get(player, ModState.SMASH_AT);
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

		if (rank > 0) {
			int levels = rank * (weapon == com.archetypes.WeaponClass.HANDS ? 2 : 1);
			float absorbed = Math.min(0.8F,
					(float) victim.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)
							* 0.04F);

			result = result + result * absorbed * Tuning.SUNDER_PER_LEVEL * levels;
		}

		// Recorded before the batch, not after: onCrusherHit can deal its own
		// damage (Shockwave's splash), and a trace line has to belong to the blow
		// that produced it rather than to whatever that splash opened.
		com.archetypes.DamageTrace.record(com.archetypes.DamageTrace.STAGE_SUNDER, amount, result);
		com.archetypes.CrusherCombat.onCrusherHit(player, victim, level, result, weapon,
				owned, smashing);
		return result;
	}

	/**
	 * Last Shadow's grace period: for two seconds after cheating death the
	 * player takes nothing at all — the capstone's promise is "you got away",
	 * and a skeleton double-tapping the escape would break it.
	 */
	//? if >=1.21.2 {
	@Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
	private void archetypes$cheatDeathGrace(final ServerLevel level, final DamageSource source,
			final float amount, final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
	//?} else {
	/*@Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
	private void archetypes$cheatDeathGrace(final DamageSource source, final float amount,
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if (((LivingEntity) (Object) this).level().isClientSide()) {
			return;
		}

	*///?}
		archetypes$cheatDeathGraceImpl(cir);
	}

	/** Shared implementation of {@link #archetypes$cheatDeathGrace}. */
	@Unique
	private void archetypes$cheatDeathGraceImpl(final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		ServerLevel level = (ServerLevel) ((LivingEntity) (Object) this).level();

		if (!((Object) this instanceof ServerPlayer player)) {
			return;
		}

		Long immuneUntil = ArchetypeStore.INSTANCE.get(player, ModState.IMMUNE_UNTIL);

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
	@Inject(method = "isInvertedHealAndHarm()Z", at = @At("HEAD"), cancellable = true)
	private void archetypes$nightFormIsUndead(
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		archetypes$nightFormIsUndeadImpl(cir);
	}

	/** Shared implementation of {@link #archetypes$nightFormIsUndead}. */
	@Unique
	private void archetypes$nightFormIsUndeadImpl(final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
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
	//? if >=1.21.2 {
	@Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
	private void archetypes$ghostForm(final ServerLevel level, final DamageSource source,
			final float amount,
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
	//?} else {
	/*@Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
	private void archetypes$ghostForm(final DamageSource source, final float amount,
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if (((LivingEntity) (Object) this).level().isClientSide()) {
			return;
		}

	*///?}
		archetypes$ghostFormImpl(source, cir);
	}

	/** Shared implementation of {@link #archetypes$ghostForm}. */
	@Unique
	private void archetypes$ghostFormImpl(final DamageSource source, final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		ServerLevel level = (ServerLevel) ((LivingEntity) (Object) this).level();

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
	 * Feast: a melee swing landed by a transformed player opens a bleed on the
	 * victim. Hung off the victim's intake rather than an AFTER_DAMAGE listener
	 * for the usual reason — that event is gated on the victim surviving, and a
	 * bleed opened by the blow that killed something else in the same swing
	 * would go missing.
	 *
	 * <p>Melee AND swung, and neither half can be read off the damage source
	 * alone. Requiring the player to be the direct entity is what makes it melee:
	 * an arrow is its own direct entity, so a vampire can no more bleed someone
	 * across a field than a Slayer can Rend them there. But "the attacker IS the
	 * damage" is equally true of armour's Thorns and of every splash and pulse
	 * this mod deals in a player's own name — {@code playerAttack(player)} and
	 * {@code indirectMagic(player, player)} both name them twice, deliberately, so
	 * that a kill still credits them. So the swing gate carries the rest: the
	 * damage must be resolving inside {@code Player.attack} (see
	 * {@link com.archetypes.MeleeSwing}). Without it, and staying inside the one
	 * archetype that can own Feast at all, a Combustion detonation opened a bleed
	 * on every creature standing near the burning one, Death's Head opened one on
	 * everything around a detonated mark, and every Thorns reflect opened one on
	 * whoever was hitting the vampire — none of them anything the player swung
	 * for (author's report).
	 *
	 * <p>Nothing the player actually swings is lost to the gate: the archetype's
	 * own Shadow Step strike is dealt through {@code Player.attack}
	 * ({@code AgilityActives.strike}) and so is a swing by the only definition
	 * that counts here.
	 */
	//? if >=1.21.2 {
	@Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"))
	private void archetypes$feast(final ServerLevel level, final DamageSource source,
			final float amount,
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
	//?} else {
	/*@Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"))
	private void archetypes$feast(final DamageSource source, final float amount,
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if (((LivingEntity) (Object) this).level().isClientSide()) {
			return;
		}

	*///?}
		archetypes$feastImpl(source);
	}

	/** Shared implementation of {@link #archetypes$feast}. */
	@Unique
	private void archetypes$feastImpl(final DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer player
				&& source.getDirectEntity() == player
				&& com.archetypes.MeleeSwing.isSwinging(player)
				&& (Object) this instanceof LivingEntity victim) {
			com.archetypes.NightForm.onHit(player, victim);
		}
	}

	/**
	 * Incorporeal: nothing shoves a body that isn't there. Its own hook rather
	 * than a branch of the dagger funnel below, because knockback immunity has
	 * to hold for a sourceless shove too.
	 */
	// Arity-only fork: this one reads nothing but `this`, so only the descriptor moves.
	//? if >=26.2 {
	@org.spongepowered.asm.mixin.injection.ModifyVariable(
			method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0)
	//?} else {
	/*@org.spongepowered.asm.mixin.injection.ModifyVariable(
			method = "knockback(DDD)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0)
	*///?}
	private double archetypes$incorporealKnockback(final double strength) {
		return archetypes$incorporealKnockbackImpl(strength);
	}

	/** Shared implementation of {@link #archetypes$incorporealKnockback}. */
	@Unique
	private double archetypes$incorporealKnockbackImpl(final double strength) {
		return (Object) this instanceof ServerPlayer player
				&& com.archetypes.NightForm.isIncorporeal(player) ? 0.0 : strength;
	}

	/**
	 * Siege: a planted archer is not moved. The third condition on this
	 * overload, next to Steadfast's and Incorporeal's — the same reason as
	 * theirs, that knockback immunity has to hold for a sourceless shove too.
	 */
	// Arity-only fork, same as Incorporeal's directly above.
	//? if >=26.2 {
	@org.spongepowered.asm.mixin.injection.ModifyVariable(
			method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0)
	//?} else {
	/*@org.spongepowered.asm.mixin.injection.ModifyVariable(
			method = "knockback(DDD)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0)
	*///?}
	private double archetypes$siegeKnockback(final double strength) {
		return archetypes$siegeKnockbackImpl(strength);
	}

	// ---- The two legacy-only stash sites inside hurtServer (see KnockbackSource). ----
	//
	// Neither touches balance: each sets the source, calls the original unchanged, and
	// restores in a finally. They exist so `archetypes$daggerKnockback` above sees on 26.1
	// exactly the source 26.2 would have handed it as a parameter.
	//? if >=1.21.2 && <26.2 {
	/*@com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
			method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"))
	private void archetypes$stashHurtKnockback(final LivingEntity self, final double strength,
			final double x, final double z,
			final com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original,
			@com.llamalad7.mixinextras.sugar.Local(argsOnly = true) final DamageSource source) {
		DamageSource previous = com.archetypes.KnockbackSource.push(source);

		try {
			original.call(self, strength, x, z);
		} finally {
			com.archetypes.KnockbackSource.pop(previous);
		}
	}
	*///?}
	// STAGE 4: the same stash, re-targeted onto the legacy entry point — and NOT sharing an
	// implementation with the arm above, which is a deliberate exception to §5a rather than
	// an oversight. Two reasons, and the second is the binding one:
	//
	//   * `hurt` runs on BOTH logical sides where `hurtServer` cannot, and KnockbackSource
	//     is a static whose contract is "single-threaded, server thread only". A
	//     client-thread push in singleplayer would race the server thread's, so this arm
	//     needs an early-out the arm above must NOT have.
	//   * Extracting the common tail into a `@Unique` helper would add a method and a
	//     delegation to the 26.1 node's transformed class. The per-stage gate forbids that,
	//     and it CAUGHT it: the first draft of this block did exactly that and 26.1's
	//     LivingEntityMixin came back one instruction-diff dirty.
	//? if <1.21.2 {
	/*@com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
			method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"))
	private void archetypes$stashHurtKnockback(final LivingEntity self, final double strength,
			final double x, final double z,
			final com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original,
			@com.llamalad7.mixinextras.sugar.Local(argsOnly = true) final DamageSource source) {
		if (self.level().isClientSide()) {
			original.call(self, strength, x, z);
			return;
		}

		DamageSource previous = com.archetypes.KnockbackSource.push(source);

		try {
			original.call(self, strength, x, z);
		} finally {
			com.archetypes.KnockbackSource.pop(previous);
		}
	}
	*///?}

	// The shield-block leg: applyItemBlocking -> blockUsingItem -> blockedByItem ->
	// knockback, three frames down, none of which carries the source on 26.1. The host
	// method is 1.21.11-and-up only (R-A5), so this leg stops existing one node lower —
	// and it has nothing to cover there, because the shield-modifier cluster it feeds is
	// excised on that family too.
	//? if >=1.21.11 && <26.2 {
	/*@com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
			method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;applyItemBlocking(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)F"))
	private float archetypes$stashBlockKnockback(final LivingEntity self,
			final net.minecraft.server.level.ServerLevel level, final DamageSource source,
			final float amount,
			final com.llamalad7.mixinextras.injector.wrapoperation.Operation<Float> original) {
		DamageSource previous = com.archetypes.KnockbackSource.push(source);

		try {
			return original.call(self, level, source, amount);
		} finally {
			com.archetypes.KnockbackSource.pop(previous);
		}
	}
	*///?}

	/** Shared implementation of {@link #archetypes$siegeKnockback}. */
	@Unique
	private double archetypes$siegeKnockbackImpl(final double strength) {
		return (Object) this instanceof ServerPlayer player
				&& com.archetypes.Deadeye.isPlanted(player) ? 0.0 : strength;
	}

	/**
	 * On the Wing: no fall damage while the Deadeye stance holds. Its own
	 * cancelling injection rather than a branch of Ghost Form's, because Ghost
	 * Form is a dice roll on every source and this is a certainty about one.
	 * Slow Falling, the node's other half, is re-asserted by the ticker.
	 */
	//? if >=1.21.2 {
	@Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
	private void archetypes$onTheWing(final ServerLevel level, final DamageSource source,
			final float amount,
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
	//?} else {
	/*@Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
	private void archetypes$onTheWing(final DamageSource source, final float amount,
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if (((LivingEntity) (Object) this).level().isClientSide()) {
			return;
		}

	*///?}
		archetypes$onTheWingImpl(source, cir);
	}

	/** Shared implementation of {@link #archetypes$onTheWing}. */
	@Unique
	private void archetypes$onTheWingImpl(final DamageSource source, final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
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
	// R-A5: EXCISED BELOW 1.21.11. `applyItemBlocking` does not exist there and the arc test
	// it hosts is not a separable step — below the boundary the facing check lives inside
	// `LivingEntity.isDamageSourceBlocked`, mixed into the same expression as the "is a
	// shield raised at all" test, so there is no `Math.acos` whose result is only the angle.
	// Omni Block therefore no-ops on that node family; see ColossusProtector's header.
	//? if >=1.21.11 {
	@ModifyExpressionValue(method = "applyItemBlocking(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)F",
			at = @At(value = "INVOKE", target = "Ljava/lang/Math;acos(D)D"))
	private double archetypes$bulwark(final double angle) {
		return archetypes$bulwarkImpl(angle);
	}

	/** Shared implementation of {@link #archetypes$bulwark}. */
	@Unique
	private double archetypes$bulwarkImpl(final double angle) {
		if ((Object) this instanceof ServerPlayer player
				&& ProtectorNodes.rank(SubTree.PROTECTOR, NodePurchases.owned(player, SubTree.PROTECTOR),
						ProtectorNodes.Family.OMNI_BLOCK) > 0) {
			return 0.0;
		}

		return angle;
	}
	//?}

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
	 * not make an arrow unblockable. Nor does a mace in hand make a DoT
	 * unblockable, which is the swing gate's job here: "the attacker IS the
	 * damage" is true of a thorns reflect and of every
	 * {@code indirectMagic(player, player)} pulse this mod deals, and a Blizzard
	 * or an Aura of Radiance stripping a guard once a second is not a siege. The
	 * node's promise is about a blow being driven home, so a blow is what it
	 * costs. The Crusher's own actives mark themselves, so a Quake or a Haymaker
	 * still breaks the shield it lands on.
	 */
	// R-A5, the cluster's fourth member — the design's R-A5 row named three, and this is the
	// one it missed. Siegebreaker is a Colossus CRUSHER node, not a Protector one, but it
	// lives in the same host: it intercepts `BlocksAttacks.resolveBlockedDamage` INSIDE
	// `applyItemBlocking`, and calls `BlocksAttacks.disable` by hand. Both are gone below
	// 1.21.11. Excised with the rest of the cluster rather than half-rooted onto
	// `isDamageSourceBlocked`: Siegebreaker's whole point is that it MEETS Immovable Object
	// as an authored event, and Immovable Object cannot exist there — one of the pair
	// working and the other silently not is worse than neither.
	//? if >=1.21.11 {
	@ModifyExpressionValue(method = "applyItemBlocking(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)F",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/item/component/BlocksAttacks;resolveBlockedDamage("
							+ "Lnet/minecraft/world/damagesource/DamageSource;FD)F"))
	private float archetypes$unstoppableForce(final float blocked, final ServerLevel level,
			final DamageSource source, final float damage) {
		return archetypes$unstoppableForceImpl(blocked, level, source);
	}

	/** Shared implementation of {@link #archetypes$unstoppableForce}. */
	@Unique
	private float archetypes$unstoppableForceImpl(final float blocked, final ServerLevel level, final DamageSource source) {
		if (blocked <= 0.0F || !(source.getEntity() instanceof ServerPlayer attacker)
				|| source.getDirectEntity() != attacker
				|| !com.archetypes.MeleeSwing.isSwinging(attacker)) {
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
	//?}

	// Hardened's `hurtServer` RETURN hook used to live here. It moved to
	// mixin/HardenedMixin so its ORDER against Fabric API's own AFTER_DAMAGE
	// handler — which lands on the same RETURN at the same default priority —
	// is a stated number instead of a load-order coin flip. Same reason
	// DamageTraceMixin is its own class; the javadoc there and there explains
	// both directions.

	/**
	 * An unstoppable force has met an immovable object. Neither node wins: the
	 * guard is not broken (the Protector's own hook refuses the disable in any
	 * case) and the blow is not driven through — the ground between the two of
	 * them goes instead.
	 *
	 * <p>A cancelling head on {@code hurtServer} rather than a clause inside
	 * {@code archetypes$unstoppableForce}, where this started, and the reason is
	 * everything vanilla does to a blocked blow AFTER that hook returns:
	 * {@code hurtBlockingItem} charges the shield the whole unblocked damage,
	 * and {@code blockUsingItem} → {@code blockedByItem} knocks the blocker
	 * along the axis between the two players — which lands after the clash's own
	 * shove and overwrites it, dragging the Protector towards the Crusher
	 * instead of five blocks clear. A blow the clash eats has to stop before
	 * vanilla begins resolving it.
	 *
	 * <p>{@code ProtectorClash} owns every test that matters (both node ranks,
	 * the weapon, the raised guard, and the re-entry guard its own blast damage
	 * needs); the cheap instance checks are here only to keep the common case —
	 * every hit taken by every creature on the server — off that call.
	 *
	 * <p>The swing gate is here rather than in {@code ProtectorClash} because it
	 * belongs to the same sentence as the two instance checks: the clash is the
	 * far end of {@code archetypes$unstoppableForce} and has to answer the same
	 * question the same way, or a source that could not break the guard could
	 * still crater the ground between them. Both hooks are Siegebreaker, and
	 * Siegebreaker is about a blow.
	 */
	//? if >=1.21.2 {
	@Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
	private void archetypes$clash(final ServerLevel level, final DamageSource source,
			final float amount,
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
	//?} else {
	/*@Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
	private void archetypes$clash(final DamageSource source, final float amount,
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if (((LivingEntity) (Object) this).level().isClientSide()) {
			return;
		}

	*///?}
		archetypes$clashImpl(source, cir);
	}

	/** Shared implementation of {@link #archetypes$clash}. */
	@Unique
	private void archetypes$clashImpl(final DamageSource source, final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		ServerLevel level = (ServerLevel) ((LivingEntity) (Object) this).level();

		if ((Object) this instanceof ServerPlayer defender
				&& source.getEntity() instanceof ServerPlayer attacker
				&& source.getDirectEntity() == attacker
				&& com.archetypes.MeleeSwing.isSwinging(attacker)
				&& com.archetypes.ProtectorClash.clash(attacker, defender, level)) {
			cir.setReturnValue(false);
		}
	}

	/**
	 * No fall damage from a Titan's Leap — the leap's own waiver, and nothing
	 * else's: it lasts exactly as long as the flight stamp does. A cancelling
	 * inject rather than a {@code fallDistance} reset, which is the whole
	 * point: the leap exists to feed Meteor, Shockwave and vanilla's own mace
	 * smash, and every one of them reads the fall it would have zeroed. Same
	 * shape as On the Wing's.
	 */
	//? if >=1.21.2 {
	@Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
	private void archetypes$titansLeapFall(final ServerLevel level, final DamageSource source,
			final float amount,
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
	//?} else {
	/*@Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
	private void archetypes$titansLeapFall(final DamageSource source, final float amount,
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if (((LivingEntity) (Object) this).level().isClientSide()) {
			return;
		}

	*///?}
		archetypes$titansLeapFallImpl(source, cir);
	}

	/** Shared implementation of {@link #archetypes$titansLeapFall}. */
	@Unique
	private void archetypes$titansLeapFallImpl(final DamageSource source, final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ServerPlayer player
				&& source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)
				&& com.archetypes.TitansLeap.isLeaping(player)) {
			cir.setReturnValue(false);
		}
	}

	// Bulwark used to hang a victim-side ModifyVariable here — a flat 20%
	// per rank off the raw amount while Battle Trance held banked health. It
	// is gone and nothing replaced it on the funnel: the node is Battle
	// Trance's ceiling now (Tuning#COLOSSUS_BULWARK_TRANCE_CAP_PER_RANK, read
	// by CrusherTicker and CrusherCombat), plus a pause on that bank's decay
	// while the fists are bare. Cutting the raw number at hurtServer's HEAD is
	// pre-armour, so it was also buying a second, unadvertised reduction out
	// of vanilla's shred term; an absorption bank cannot do that to a damage
	// number because it never touches one — vanilla spends absorption after
	// armour, in its own step, and this mixin never sees it.

	/**
	 * Instinctive Guard: a carried shield blocks a share of every hit without
	 * ever being raised. A victim-side {@code ModifyVariable} on the shared
	 * {@code amount}, Mana Shield's shape, so it composes with the rest of the
	 * funnel and lands before armour — which is where vanilla's own blocking
	 * lands too. The whole of the rule is in
	 * {@link com.archetypes.ColossusProtector#instinctiveGuard}.
	 */
	//? if >=1.21.2 {
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$instinctiveGuard(final float amount, final ServerLevel level,
			final DamageSource source) {
	//?} else {
	/*@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$instinctiveGuard(final float amount, final DamageSource source) {
		if (((LivingEntity) (Object) this).level().isClientSide()) {
			return amount;
		}

	*///?}
		return archetypes$instinctiveGuardImpl(amount, source);
	}

	/** Shared implementation of {@link #archetypes$instinctiveGuard}. */
	@Unique
	private float archetypes$instinctiveGuardImpl(final float amount, final DamageSource source) {
		ServerLevel level = (ServerLevel) ((LivingEntity) (Object) this).level();

		float result = (Object) this instanceof ServerPlayer player
				? com.archetypes.ColossusProtector.instinctiveGuard(player, level, source, amount)
				: amount;
		com.archetypes.DamageTrace.record(com.archetypes.DamageTrace.STAGE_INSTINCTIVE_GUARD, amount,
				result);
		return result;
	}

	/**
	 * Parry: a hit landing inside the Colossus Slayer's window is voided, and
	 * pays. A cancelling inject rather than a shaper because a parry is not a
	 * discount — the blow does not happen.
	 *
	 * <p>Ordered after the shapers only by accident of file position; it does
	 * not matter, because the amount a sword parry sends back is read from the
	 * argument at HEAD either way, and every shaper on this funnel that could
	 * touch it belongs to a different archetype.
	 */
	//? if >=1.21.2 {
	@Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
	private void archetypes$parry(final ServerLevel level, final DamageSource source,
			final float amount,
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
	//?} else {
	/*@Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
	private void archetypes$parry(final DamageSource source, final float amount,
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if (((LivingEntity) (Object) this).level().isClientSide()) {
			return;
		}

	*///?}
		archetypes$parryImpl(source, amount, cir);
	}

	/** Shared implementation of {@link #archetypes$parry}. */
	@Unique
	private void archetypes$parryImpl(final DamageSource source, final float amount, final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		ServerLevel level = (ServerLevel) ((LivingEntity) (Object) this).level();

		if ((Object) this instanceof ServerPlayer player
				&& com.archetypes.ColossusSlayer.tryParry(player, level, source, amount)) {
			cir.setReturnValue(false);
		}
	}

	/**
	 * Barbarian's damage half: a share of every magical hit, gone. Mana
	 * Shield's shape — a victim-side shaper on the shared {@code amount}, so it
	 * composes multiplicatively with whatever else shaped the blow.
	 */
	//? if >=1.21.2 {
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$barbarian(final float amount, final ServerLevel level,
			final DamageSource source) {
	//?} else {
	/*@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$barbarian(final float amount, final DamageSource source) {
		if (((LivingEntity) (Object) this).level().isClientSide()) {
			return amount;
		}

	*///?}
		return archetypes$barbarianImpl(amount, source);
	}

	/** Shared implementation of {@link #archetypes$barbarian}. */
	@Unique
	private float archetypes$barbarianImpl(final float amount, final DamageSource source) {
		float result = (Object) this instanceof ServerPlayer player
				? com.archetypes.ColossusSlayer.barbarianDamage(player, source, amount)
				: amount;
		com.archetypes.DamageTrace.record(com.archetypes.DamageTrace.STAGE_BARBARIAN, amount, result);
		return result;
	}

	/**
	 * Barbarian's healing half. {@code heal} is vanilla's single healing
	 * funnel, so the node lands on potions, Priest light and Oracle mending at
	 * once — but only on the heals declared magical (see
	 * {@code ColossusSlayer}'s magical-heal flag), which is what leaves food,
	 * natural regeneration and Taste of Blood whole.
	 */
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "heal(F)V",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$barbarianHealing(final float amount) {
		return archetypes$barbarianHealingImpl(amount);
	}

	/** Shared implementation of {@link #archetypes$barbarianHealing}. */
	@Unique
	private float archetypes$barbarianHealingImpl(final float amount) {
		return (Object) this instanceof ServerPlayer player
				? com.archetypes.ColossusSlayer.barbarianHealing(player, amount)
				: amount;
	}

	// ---------------------------------------------------------------------------
	// STAGE 5, and the only handler in this file that exists on one node alone:
	// `ServerLivingEntityEvents.AFTER_DAMAGE` is a `>=1.20.5` fabric-api row and 0.92.11
	// has nothing that reports the damage a hit actually did. This reproduces the event at
	// the site fabric-api's own implementation uses, with fabric-api's own three semantics
	// — the recipe Skill Proficiencies arrived at AFTER a first attempt on `actuallyHurt`
	// got each of them wrong (its R-20 regression), copied rather than re-derived:
	//
	//  1. IT HAS TO FIRE FOR PLAYERS. `Player` overrides `actuallyHurt` on 1.20.1 and never
	//     calls super, so a hook there is dead for every player. `hurt` is the one site
	//     every entity shares — `Player.hurt` ends in `invokespecial LivingEntity.hurt`.
	//  2. THE AMOUNT IS THE PRE-ARMOUR ONE. The event reports damage after shields and
	//     freezing and explicitly NOT after armour, enchantments or absorption hearts —
	//     i.e. `hurt`'s own float argument as mutated in place, which is what a Mixin
	//     callback's parameter loads at the injection point.
	//  3. IT MUST NOT FIRE ON A KILLING BLOW. fabric-api enforces that with an
	//     `isDeadOrDying()` guard inside its handler; vanilla's own second `isDeadOrDying`
	//     has already run by TAIL.
	//
	// The consumer's own two filters (positive damage, not blocked) are applied by the
	// consumer on every node and are passed through here rather than re-decided.
	//
	// `baseDamage` and `damageTaken` are handed the SAME value, and that is honest rather
	// than lazy: fabric-api's `baseDamage` is the argument before the shield/freezing
	// mutations, this mixin's only consumer never reads it, and inventing a second local
	// to carry a number nothing consumes would be a claim this node cannot support.
	//
	// PRIORITY IS THE DEFAULT 1000 ON PURPOSE — the same slot fabric-api's handler occupies
	// on every other node, so HardenedMixin's pinned 900 still runs first here (design
	// §5.9 gate 5, and the funnel audit is what checks it).
	//? if >=1.20.5 {
	//?} else {
	/*@Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("TAIL"))
	private void archetypes$afterDamage(final DamageSource source, final float amount,
			final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir,
			@com.llamalad7.mixinextras.sugar.Local(ordinal = 0) final boolean blocked) {
		LivingEntity self = (LivingEntity) (Object) this;

		if (self.level().isClientSide() || self.isDeadOrDying()) {
			return;
		}

		com.archetypes.platform.LegacyDamageEvents.fireAfterDamage(self, source, amount, amount, blocked);
	}

	// STAGE 5 — Well Fed's faster eating, re-rooted off `ItemStack` because below 1.20.5
	// `ItemStack.getUseDuration()` takes no user and Well Fed is per-player (see
	// mixin/ItemStackMixin, which owns the modern arm and explains why the shell is written
	// twice). `this` is the user at all FOUR sites that read a use duration in 1.20.1's
	// `LivingEntity` — `startUsingItem`, `getTicksUsingItem`, `shouldTriggerItemUseEffects`
	// and `onSyncedDataUpdated` (`javap -c`: the only `ItemStack.getUseDuration()I` calls in
	// the class) — so covering all four is what reproduces the modern contract, where the
	// countdown AND everything measured against it move together.
	//
	// `getUseItem()` is the stack in every one of them, and it is already assigned when the
	// earliest of them reads it: `startUsingItem` does `putfield useItem` at offset 23 and
	// calls `getUseDuration` at 28.
	@ModifyExpressionValue(method = { "startUsingItem(Lnet/minecraft/world/InteractionHand;)V",
			"getTicksUsingItem()I", "shouldTriggerItemUseEffects()Z",
			"onSyncedDataUpdated(Lnet/minecraft/network/syncher/EntityDataAccessor;)V" },
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getUseDuration()I"))
	private int archetypes$wellFedDuration(final int original) {
		LivingEntity self = (LivingEntity) (Object) this;

		if (original <= 0 || !((Object) self instanceof net.minecraft.world.entity.player.Player player)
				|| self.getUseItem().getItem().getFoodProperties() == null) {
			return original;
		}

		float factor = com.archetypes.ColossusProtector.eatSpeedFactor(player);
		return factor >= 1.0F ? original : Math.max(1, Math.round(original * factor));
	}
	*///?}
}
