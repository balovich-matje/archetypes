package com.archetypes;

// STAGE 6 — see AgilityCombat for the rule: only the import and the registration line fork.
//? if fabric {
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
//?} elif neoforge {
/*import com.archetypes.platform.NeoForgeEvents;
*///?} elif forge {
/*import com.archetypes.platform.ForgeEvents;
*///?}
import net.minecraft.server.level.ServerPlayer;

/**
 * The Seeker's kill hooks. Siphon: a missile that ends a life hands part of
 * its price back — the artillerist's rhythm section.
 */
public final class SeekerCombat {
	private SeekerCombat() {
	}

	public static void initialize() {
		// Registration only; the body is shared. Skill Proficiencies has no AFTER_DEATH at all,
		// so this contract is written down rather than inherited: fire ONCE per entity death,
		// server-side, AFTER the death is final, with the entity and the `DamageSource`. Both
		// loaders' natural host is `LivingDeathEvent` — the same event their `allowDeath` uses —
		// so the helper has to distinguish "post" from "veto" and must not fire the post arm when
		// the death was cancelled.
		//? if fabric {
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
		//?} elif neoforge {
		/*NeoForgeEvents.afterDeath((victim, source) -> {
		*///?} elif forge {
		/*ForgeEvents.afterDeath((victim, source) -> {
		*///?}
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
