package com.archetypes;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Seeker's kill hooks. Siphon: a missile that ends a life hands part of
 * its price back — the artillerist's rhythm section.
 */
public final class SeekerCombat {
	private SeekerCombat() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (!(source.getDirectEntity() instanceof SpellProjectile spell)
					|| spell.mode() != SpellProjectile.Mode.MISSILE
					|| !(spell.getOwner() instanceof ServerPlayer player)) {
				return;
			}

			if (WizardNodes.rank(SubTree.WIZARD, NodePurchases.owned(player, SubTree.WIZARD),
					WizardNodes.Family.SIPHON) > 0) {
				Mana.refund(player, Tuning.SIPHON_REFUND);
			}
		});
	}
}
