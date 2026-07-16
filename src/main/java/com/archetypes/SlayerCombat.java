package com.archetypes;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

/**
 * The Slayer's on-hit and on-kill passives, hung off Fabric's entity events.
 * Weapon scoping is the tree's split made literal: sword-only passives check
 * {@link ModItems#isSword}, the shared ones accept the greatsword too.
 */
public final class SlayerCombat {
	private SlayerCombat() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, taken, blocked) -> {
			if (blocked || taken <= 0 || !(source.getDirectEntity() instanceof ServerPlayer player)) {
				return;
			}

			ItemStack weapon = player.getMainHandItem();
			boolean sword = ModItems.isSword(weapon);
			boolean greatsword = ModItems.isGreatsword(weapon);

			if (!sword && !greatsword) {
				return;
			}

			var owned = NodePurchases.owned(player, SubTree.SLAYER);

			// Hamstring: both weapons cripple. Ranks 1-2 deepen the effect,
			// rank 3 stretches it — Slowness III would be a root, not a slow.
			int slow = SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.SLOWNESS);

			if (slow > 0 && entity.isAlive()) {
				int duration = slow >= 3 ? Tuning.SLOWNESS_LONG_TICKS : Tuning.SLOWNESS_TICKS;
				entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration,
						Math.min(slow, 2) - 1), player);
			}

			// Rend: sword-only — a wound the greatsword's single blow does not need.
			int bleed = SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.BLEED);

			if (bleed > 0 && sword && entity.isAlive()) {
				SlayerTicker.startBleed(entity, player, bleed);
			}
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(source.getEntity() instanceof ServerPlayer player)) {
				return;
			}

			ItemStack weapon = player.getMainHandItem();
			boolean sword = ModItems.isSword(weapon);
			boolean greatsword = ModItems.isGreatsword(weapon);

			if (!sword && !greatsword) {
				return;
			}

			var owned = NodePurchases.owned(player, SubTree.SLAYER);

			// Taste of Blood: half a heart per rank on any melee kill.
			int taste = SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.TASTE_OF_BLOOD);

			if (taste > 0) {
				player.heal(taste * Tuning.TASTE_OF_BLOOD_HEAL_PER_RANK);
				((ServerLevel) player.level()).sendParticles(ParticleTypes.HEART,
						player.getX(), player.getY() + 1.5, player.getZ(), taste, 0.3, 0.3, 0.3, 0.0);
			}

			// Flurry: sword kills reset the lunge.
			if (sword && SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.FLURRY) > 0) {
				((AttachmentTarget) player).removeAttached(ModAttachments.LUNGE_READY_AT);
			}

			// Bloodlust: the momentum passive at the very tip of the sword.
			if (SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.BLOODLUST) > 0) {
				player.addEffect(new MobEffectInstance(MobEffects.SPEED, Tuning.BLOODLUST_TICKS, 0));
			}
		});
	}

}
