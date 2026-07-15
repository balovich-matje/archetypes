package com.archetypes;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.world.entity.player.Player;

/**
 * Archetype progression, banked out of the player's own experience.
 *
 * <p>A level costs {@link #XP_PER_LEVEL} XP — exactly vanilla levels 0 to 10 —
 * and each level is one skill point. The XP is <em>mirrored</em>, not spent:
 * earning experience feeds levels in parallel, so archetype progress never
 * competes with enchanting.
 *
 * <p>{@link #MAX_LEVEL} levels carry you from Brawler to Colossus, and each of
 * the three sub-trees accepts at most {@link #MAX_POINTS_PER_SUB_TREE} — so a
 * fully-levelled archetype has exactly enough points to fill every sub-tree's
 * budget, but no sub-tree's budget covers all of its nodes. You specialise
 * inside each tree; you never buy one out.
 *
 * <p>Because the cost is flat while vanilla's per-level cost is not, a level is
 * ~23 vanilla levels of work at level 0 and about half a level at 50. That means
 * <strong>the caps, not the XP price, are what balance the tree</strong> — an XP
 * farm changes how quickly you reach the ceiling, never how high it is.
 */
public final class SkillPoints {
	/** Vanilla levels 0 to 10: sum of (7 + 2L) for L in 0..9. */
	public static final int XP_PER_LEVEL = 160;

	/** Brawler at 0, Colossus at 45. One point per level. */
	public static final int MAX_LEVEL = 45;

	/**
	 * Most points any one sub-tree accepts. Below its node count on purpose, so
	 * the Protector picks utility, defence, or a compromise — never all of it.
	 */
	public static final int MAX_POINTS_PER_SUB_TREE = 15;

	private SkillPoints() {
	}

	/** Total XP banked toward levels over this player's life. */
	public static int bankedXp(final Player player) {
		Integer xp = ((AttachmentTarget) player).getAttached(ModAttachments.ARCHETYPE_XP);
		return xp == null ? 0 : xp;
	}

	public static int spent(final Player player) {
		Integer used = ((AttachmentTarget) player).getAttached(ModAttachments.SPENT_POINTS);
		return used == null ? 0 : used;
	}

	/** Archetype level, 0 to {@link #MAX_LEVEL}. Also the total points earned. */
	public static int level(final Player player) {
		return Math.min(bankedXp(player) / XP_PER_LEVEL, MAX_LEVEL);
	}

	/** Points available to commit right now. */
	public static int available(final Player player) {
		return Math.max(level(player) - spent(player), 0);
	}

	/** Progress toward the next level, 0..1. Flat 1 once maxed. */
	public static float levelProgress(final Player player) {
		if (level(player) >= MAX_LEVEL) {
			return 1.0F;
		}

		return (bankedXp(player) % XP_PER_LEVEL) / (float) XP_PER_LEVEL;
	}

	/** XP banked into the current level, and what it needs. */
	public static int xpIntoLevel(final Player player) {
		return level(player) >= MAX_LEVEL ? XP_PER_LEVEL : bankedXp(player) % XP_PER_LEVEL;
	}

	/** Progress from start tier to peak tier, 0..1. */
	public static float archetypeProgress(final Player player) {
		return level(player) / (float) MAX_LEVEL;
	}

	/** Which tier's name to show: 0 = start, 1 = peak, reached only at max level. */
	public static int tier(final Player player) {
		return level(player) >= MAX_LEVEL ? 1 : 0;
	}

	/**
	 * Bank experience toward levels. Called with the same amount vanilla awards,
	 * so the player keeps their XP; this only shadows it.
	 */
	public static void bank(final Player player, final int amount) {
		if (amount <= 0 || level(player) >= MAX_LEVEL) {
			return;
		}

		((AttachmentTarget) player).setAttached(ModAttachments.ARCHETYPE_XP, bankedXp(player) + amount);
	}

	/** Testing affordance: hand over one level's worth of XP outright. */
	public static void grantPoint(final Player player) {
		((AttachmentTarget) player).setAttached(ModAttachments.ARCHETYPE_XP,
				bankedXp(player) + XP_PER_LEVEL);
	}
}
