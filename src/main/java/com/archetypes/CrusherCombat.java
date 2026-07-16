package com.archetypes;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Crusher's on-hit passives. Weapon scoping is the tree's premise: the
 * shared families serve both the mace and bare fists, with fists at double
 * effect — the compensation for bringing knuckles to a sword fight.
 */
public final class CrusherCombat {
	private CrusherCombat() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, taken, blocked) -> {
			if (blocked || taken <= 0 || !(source.getDirectEntity() instanceof ServerPlayer player)) {
				return;
			}

			WeaponClass weapon = WeaponClass.of(player);

			if (weapon != WeaponClass.MACE && weapon != WeaponClass.HANDS) {
				return;
			}

			var owned = NodePurchases.owned(player, SubTree.CRUSHER);
			AttachmentTarget target = (AttachmentTarget) player;
			long now = player.level().getGameTime();

			// Adrenaline: landing blows keeps the frenzy window open; the
			// ticker turns the window into an attack-speed modifier.
			if (CrusherNodes.rank(SubTree.CRUSHER, owned, CrusherNodes.Family.ADRENALINE) > 0) {
				target.setAttached(ModAttachments.ADRENALINE_UNTIL, now + Tuning.ADRENALINE_TICKS);
			}

			// Battle Trance: every landed hit banks absorption, capped by
			// rank, doubled for fists. The ticker drains it once idle.
			int trance = CrusherNodes.rank(SubTree.CRUSHER, owned, CrusherNodes.Family.BATTLE_TRANCE);

			if (trance > 0) {
				float gain = Tuning.TRANCE_ABSORPTION_PER_HIT * (weapon == WeaponClass.HANDS ? 2.0F : 1.0F);
				float cap = Tuning.TRANCE_CAP_PER_RANK * trance;
				player.setAbsorptionAmount(Math.min(cap,
						Math.max(player.getAbsorptionAmount(), 0.0F) + gain));
				target.setAttached(ModAttachments.TRANCE_HIT_AT, now);
				ProcIndicators.send(player, SubTree.CRUSHER, CrusherNodes.Family.BATTLE_TRANCE);
			}
		});
	}
}
