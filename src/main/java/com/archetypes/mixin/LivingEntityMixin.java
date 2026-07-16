package com.archetypes.mixin;

import com.archetypes.CrusherNodes;
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

		int rank = CrusherNodes.rank(SubTree.CRUSHER,
				NodePurchases.owned(player, SubTree.CRUSHER), CrusherNodes.Family.SUNDER);

		if (rank == 0) {
			return amount;
		}

		int levels = rank * (weapon == com.archetypes.WeaponClass.HANDS ? 2 : 1);
		LivingEntity victim = (LivingEntity) (Object) this;
		float absorbed = Math.min(0.8F,
				(float) victim.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)
						* 0.04F);

		return amount + amount * absorbed * Tuning.SUNDER_PER_LEVEL * levels;
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
