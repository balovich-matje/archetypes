package com.archetypes;

import com.archetypes.compat.SpecialitiesBridge;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The Seeker's resource. Spells have no cooldowns — mana is the throttle: a
 * pool that regenerates every tick and empties into casts. Pool size and
 * regen grow with the Spellcasting skill (Specialities), which itself levels
 * from the mana spent here — the loop that makes casting its own progression.
 * Without Specialities the pool simply stays at its base size.
 */
public final class Mana {
	private Mana() {
	}

	public static float max(final Player player) {
		var wizard = NodePurchases.owned(player, SubTree.WIZARD);
		float nodes = Tuning.ARCANE_ORB_MANA
				* WizardNodes.rank(SubTree.WIZARD, wizard, WizardNodes.Family.ARCANE_ORB)
				+ Tuning.BEACON_MANA
				* PriestNodes.rank(SubTree.PRIEST, NodePurchases.owned(player, SubTree.PRIEST),
						PriestNodes.Family.BEACON);
		float base = Tuning.MANA_BASE + nodes
				+ Tuning.MANA_PER_SPELLCASTING_LEVEL * SpecialitiesBridge.spellcastingLevel(player);
		// Oracle's Wisdom scales the whole pool: +50/100% by rank.
		int wisdom = OracleElementalistNodes.rank(SubTree.ORACLE_ELEMENTALIST,
				NodePurchases.owned(player, SubTree.ORACLE_ELEMENTALIST),
				OracleElementalistNodes.Family.ORACLE_WISDOM);
		return base * (1.0F + Tuning.ORACLE_WISDOM_PER_RANK * wisdom);
	}

	public static float regenPerSecond(final Player player) {
		// A hand full of steel is a mind empty of magic: any weapon or
		// shield in either hand stops regeneration entirely — the guard
		// against sword-and-sorcery double-dipping.
		if (ModItems.holdingCombatWeapon(player)) {
			return 0.0F;
		}

		float nodes = Tuning.FOCUSED_MIND_REGEN * ElementalistNodes.rank(SubTree.ELEMENTALIST,
				NodePurchases.owned(player, SubTree.ELEMENTALIST),
				ElementalistNodes.Family.FOCUSED_MIND)
				+ Tuning.FLOW_REGEN_PER_RANK
				* WizardNodes.rank(SubTree.WIZARD, NodePurchases.owned(player, SubTree.WIZARD),
						WizardNodes.Family.FLOW)
				+ Tuning.DEVOTION_REGEN
				* PriestNodes.rank(SubTree.PRIEST, NodePurchases.owned(player, SubTree.PRIEST),
						PriestNodes.Family.DEVOTION);
		// Oracle's Focus regenerates a fraction of the (Wisdom-scaled) pool a
		// second — 2.5/5% by rank. A wand isn't a combat weapon, so this flows
		// while the Oracle holds one to cast.
		float oracleFocus = Tuning.ORACLE_FOCUS_REGEN_PER_RANK
				* OracleElementalistNodes.rank(SubTree.ORACLE_ELEMENTALIST,
						NodePurchases.owned(player, SubTree.ORACLE_ELEMENTALIST),
						OracleElementalistNodes.Family.ORACLE_FOCUS)
				* max(player);
		return Tuning.MANA_REGEN_BASE_PER_SECOND + nodes + oracleFocus
				+ SpecialitiesBridge.spellcastingLevel(player) / Tuning.MANA_REGEN_LEVELS_PER_POINT;
	}

	/** Absent attachment means full: a fresh Seeker starts topped up. */
	public static float current(final Player player) {
		Float mana = ((AttachmentTarget) player).getAttached(ModAttachments.MANA);
		return mana == null ? max(player) : Math.min(mana, max(player));
	}

	/** True (and paid, and XP awarded) if the player can afford it. */
	public static boolean spend(final ServerPlayer player, final float cost) {
		float current = current(player);

		if (current < cost) {
			return false;
		}

		((AttachmentTarget) player).setAttached(ModAttachments.MANA, current - cost);
		awardXp(player, cost);
		return true;
	}

	/**
	 * Meteorite's price: everything, but only if there is at least {@code min}
	 * to give. Returns the amount actually spent, 0 if the cast can't happen.
	 */
	public static float spendAll(final ServerPlayer player, final float min) {
		float current = current(player);

		if (current < min) {
			return 0.0F;
		}

		((AttachmentTarget) player).setAttached(ModAttachments.MANA, 0.0F);
		awardXp(player, current);
		return current;
	}

	/** Potions and other refunds: straight in, clamped to the pool. */
	public static void add(final ServerPlayer player, final float amount) {
		if (amount > 0.0F) {
			((AttachmentTarget) player).setAttached(ModAttachments.MANA,
					Math.min(max(player), current(player) + amount));
		}
	}

	/**
	 * Mana Shield's toll: pure drain, no XP — tanking a hit is not casting.
	 * Returns how much was actually taken.
	 */
	public static float drain(final ServerPlayer player, final float amount) {
		float current = current(player);
		float drained = Math.min(current, amount);
		((AttachmentTarget) player).setAttached(ModAttachments.MANA, current - drained);
		return drained;
	}

	/**
	 * Meteorite's modifier refund: the pool always empties in full — the
	 * impact math keeps the whole number — and the cost modifiers hand
	 * their share back once the rock is away. No XP here: the full spend
	 * already paid it.
	 */
	public static void refund(final ServerPlayer player, final float amount) {
		((AttachmentTarget) player).setAttached(ModAttachments.MANA,
				Math.min(max(player), current(player) + amount));
	}

	/** Once per tick from the Seeker ticker. */
	public static void regenTick(final ServerPlayer player) {
		float current = current(player);
		float max = max(player);

		if (current < max) {
			((AttachmentTarget) player).setAttached(ModAttachments.MANA,
					Math.min(max, current + regenPerSecond(player) / 20.0F));
		}
	}

	/**
	 * Mana-to-XP with a fractional carry, so the flamethrower's 1.25-a-tick
	 * drip pays the same rate as one big fireball.
	 */
	private static void awardXp(final ServerPlayer player, final float mana) {
		AttachmentTarget target = (AttachmentTarget) player;
		Float carried = target.getAttached(ModAttachments.MANA_XP_REMAINDER);
		float total = (carried == null ? 0.0F : carried) + mana * Tuning.XP_PER_MANA;
		int whole = (int) total;

		target.setAttached(ModAttachments.MANA_XP_REMAINDER, total - whole);
		SpecialitiesBridge.awardSpellcastingXp(player, whole);
	}
}
