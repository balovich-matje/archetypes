package com.archetypes;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.world.entity.player.Player;

/**
 * Skill points, banked out of the player's own experience.
 *
 * <p>A point costs {@link #XP_PER_POINT} XP — exactly vanilla levels 0 to 10.
 * The XP is <em>mirrored</em>, not spent: earning experience feeds points in
 * parallel, so archetype progress never competes with enchanting.
 *
 * <p>Because the cost is flat while vanilla's per-level cost is not, a point is
 * ~23 levels of work at level 0 and about half a level at 50. That is deliberate
 * but it means **the point cap, not the XP price, is what balances the tree** —
 * an XP farm changes how quickly you reach the ceiling, never how high it is.
 */
public final class SkillPoints {
	/** Vanilla levels 0 to 10: sum of (7 + 2L) for L in 0..9. */
	public static final int XP_PER_POINT = 160;

	/**
	 * Most points any one archetype may ever commit. Deliberately below the node
	 * count so a tree cannot be bought outright — you specialise or you compromise.
	 */
	public static final int MAX_POINTS = 12;

	private SkillPoints() {
	}

	/** Total XP banked toward points over this player's life. */
	public static int bankedXp(final Player player) {
		Integer xp = ((AttachmentTarget) player).getAttached(ModAttachments.ARCHETYPE_XP);
		return xp == null ? 0 : xp;
	}

	public static int spent(final Player player) {
		Integer used = ((AttachmentTarget) player).getAttached(ModAttachments.SPENT_POINTS);
		return used == null ? 0 : used;
	}

	/** Points the banked XP has paid for, before spending — capped. */
	public static int earned(final Player player) {
		return Math.min(bankedXp(player) / XP_PER_POINT, MAX_POINTS);
	}

	/** Points available to commit right now. */
	public static int available(final Player player) {
		return Math.max(earned(player) - spent(player), 0);
	}

	/**
	 * Bank experience toward points. Called with the same amount vanilla awards,
	 * so the player keeps their XP; this only shadows it.
	 */
	public static void bank(final Player player, final int amount) {
		if (amount <= 0) {
			return;
		}

		// Stop banking once the cap is paid for, so the number cannot run away
		// and quietly overflow on a long-lived world.
		if (earned(player) >= MAX_POINTS) {
			return;
		}

		((AttachmentTarget) player).setAttached(ModAttachments.ARCHETYPE_XP, bankedXp(player) + amount);
	}

	/** Testing affordance: hand over one point's worth of XP outright. */
	public static void grantPoint(final Player player) {
		((AttachmentTarget) player).setAttached(ModAttachments.ARCHETYPE_XP,
				bankedXp(player) + XP_PER_POINT);
	}
}
