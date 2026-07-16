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
		return Tuning.MANA_BASE
				+ Tuning.MANA_PER_SPELLCASTING_LEVEL * SpecialitiesBridge.spellcastingLevel(player);
	}

	public static float regenPerSecond(final Player player) {
		float focused = ElementalistNodes.rank(SubTree.ELEMENTALIST,
				NodePurchases.owned(player, SubTree.ELEMENTALIST),
				ElementalistNodes.Family.FOCUSED_MIND) > 0 ? Tuning.FOCUSED_MIND_REGEN : 0.0F;
		return Tuning.MANA_REGEN_BASE_PER_SECOND + focused
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
