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
 * {@link ModItems#isSword}, the shared ones accept the claymore too.
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
			boolean claymore = ModItems.isClaymore(weapon);

			if (!sword && !claymore) {
				return;
			}

			var owned = NodePurchases.owned(player, SubTree.SLAYER);

			// Hamstring: both weapons cripple.
			int slow = SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.SLOWNESS);

			if (slow > 0 && entity.isAlive()) {
				entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, Tuning.SLOWNESS_TICKS, slow - 1),
						player);
			}

			// Rend: sword-only — a wound the claymore's single blow does not need.
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
			boolean claymore = ModItems.isClaymore(weapon);

			if (!sword && !claymore) {
				return;
			}

			var owned = NodePurchases.owned(player, SubTree.SLAYER);

			// Vampirism: half a heart per rank on any melee kill.
			int vamp = SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.VAMP);

			if (vamp > 0) {
				player.heal(vamp * Tuning.VAMP_HEAL_PER_RANK);
				((ServerLevel) player.level()).sendParticles(ParticleTypes.HEART,
						player.getX(), player.getY() + 1.5, player.getZ(), vamp, 0.3, 0.3, 0.3, 0.0);
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
