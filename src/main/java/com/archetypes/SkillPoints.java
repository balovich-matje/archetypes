package com.archetypes;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Archetype progression, banked out of the player's own experience.
 *
 * <p>The XP is <em>mirrored</em>, not spent: earning experience feeds levels
 * in parallel, so archetype progress never competes with enchanting. Each
 * level is one skill point, but the price climbs a quadratic curve —
 * {@code cost(L) = round(1.2 L² + 15)} — so the first fifteen levels are a
 * few in-game days of normal play while the last fifteen are 69% of the
 * whole 38,349-XP road. Level 45 is a milestone on the order of slaying the
 * dragon.
 *
 * <p>The mirror also runs faster the further the player has actually gotten
 * in the game: every completed non-recipe advancement adds
 * {@link #XP_PER_ADVANCEMENT} to the banking rate, tripling it at 80. The
 * multiplier applies at banking time, so past XP keeps its historical rate
 * and a grinder parked at a frozen game stage levels legally, just slowly.
 * The three sub-tree caps still bound how much of a tree one build can own;
 * the curve now paces how fast it gets there.
 */
public final class SkillPoints {
	/** Brawler at 0, Colossus at the epic cap of 60. One point per level. */
	public static final int MAX_LEVEL = 60;

	/**
	 * The end of the normal tier: levels 1-45 grant one normal point each,
	 * spendable in the base sub-trees, and 45 is where the peak tier name
	 * ("Oracle") unlocks. Levels 46-60 grant epic points instead.
	 */
	public static final int BASE_LEVEL_CAP = 45;

	/** Levels 46-60 each grant one epic point, {@value} in all. */
	public static final int MAX_EPIC_POINTS = MAX_LEVEL - BASE_LEVEL_CAP;

	/**
	 * Most points any one base sub-tree accepts. Below its node count on
	 * purpose, so the Protector picks utility, defence, or a compromise —
	 * never all of it.
	 */
	public static final int MAX_POINTS_PER_SUB_TREE = 15;

	/**
	 * Most points any one epic sub-tree accepts. The 15 epic points are meant
	 * to spread across the epic trees, not sink into a single one.
	 */
	public static final int MAX_POINTS_PER_EPIC_SUB_TREE = 5;

	/**
	 * The curve, in exact integer math: {@code 15 + round(6L²/5)}, where
	 * {@code 6L² mod 5} is only ever 0, 1 or 4, so adding 2 before the
	 * integer divide rounds half-up correctly. COST[L] is the price of level
	 * L; CUM[L] the total banked XP to reach it. The same formula runs the
	 * whole way to the epic cap.
	 */
	private static final int[] COST = new int[MAX_LEVEL + 1];
	private static final int[] CUM = new int[MAX_LEVEL + 1];

	static {
		for (int level = 1; level <= MAX_LEVEL; level++) {
			COST[level] = 15 + (6 * level * level + 2) / 5;
			CUM[level] = CUM[level - 1] + COST[level];
		}

		// Anchors from the design doc; a drifted curve should fail loudly. The
		// peak-tier and epic-cap totals both come straight from the formula.
		if (CUM[BASE_LEVEL_CAP] != 38_349 || CUM[15] != 1_713 || COST[1] != 16
				|| COST[45] != 2_445 || CUM[MAX_LEVEL] != 89_472) {
			throw new IllegalStateException("XP curve drifted: cum(45)=" + CUM[BASE_LEVEL_CAP]
					+ " cum(60)=" + CUM[MAX_LEVEL]);
		}
	}

	/**
	 * The advancement rate: +2.5% per completed non-recipe advancement,
	 * capped at triple speed (80 advancements of vanilla's ~126 — a thorough
	 * playthrough, not a completionist one). Vanilla-tuned: datapacks and
	 * mods grow the pool, so revisit per-modpack.
	 */
	public static final float XP_PER_ADVANCEMENT = 0.025F;
	public static final float MAX_XP_MULTIPLIER = 3.0F;

	private SkillPoints() {
	}

	/** Total XP banked toward levels over this player's life. */
	public static int bankedXp(final Player player) {
		Integer xp = ((AttachmentTarget) player).getAttached(ModAttachments.ARCHETYPE_XP);
		return xp == null ? 0 : xp;
	}

	/** Normal points committed to base sub-trees. */
	public static int spent(final Player player) {
		Integer used = ((AttachmentTarget) player).getAttached(ModAttachments.SPENT_POINTS);
		return used == null ? 0 : used;
	}

	/** Epic points committed to epic sub-trees. */
	public static int epicSpent(final Player player) {
		Integer used = ((AttachmentTarget) player).getAttached(ModAttachments.EPIC_SPENT_POINTS);
		return used == null ? 0 : used;
	}

	/** Archetype level, 0 to {@link #MAX_LEVEL}. Also the total points earned. */
	public static int level(final Player player) {
		int xp = bankedXp(player);
		int level = 0;

		while (level < MAX_LEVEL && xp >= CUM[level + 1]) {
			level++;
		}

		return level;
	}

	/** Normal points available to commit right now. Levels past 45 add no
	 * normal points, so the base pool tops out at 45 minus what's spent. */
	public static int available(final Player player) {
		return Math.max(Math.min(level(player), BASE_LEVEL_CAP) - spent(player), 0);
	}

	/** Epic points available to commit right now: one per level past 45,
	 * minus what's already sunk into epic trees. */
	public static int epicAvailable(final Player player) {
		return Math.max(Math.max(level(player) - BASE_LEVEL_CAP, 0) - epicSpent(player), 0);
	}

	/** XP banked into the current level. */
	public static int xpIntoLevel(final Player player) {
		int level = level(player);
		return level >= MAX_LEVEL ? COST[MAX_LEVEL] : bankedXp(player) - CUM[level];
	}

	/** What the next level costs — the short bar's denominator. */
	public static int costForNextLevel(final Player player) {
		return COST[Math.min(level(player) + 1, MAX_LEVEL)];
	}

	/** Progress toward the next level, 0..1. Flat 1 once maxed. */
	public static float levelProgress(final Player player) {
		if (level(player) >= MAX_LEVEL) {
			return 1.0F;
		}

		return xpIntoLevel(player) / (float) costForNextLevel(player);
	}

	/** Progress from start tier to peak tier, 0..1. Full at level 45 — the
	 * epic levels beyond it are a separate journey and hold the bar at 1. */
	public static float archetypeProgress(final Player player) {
		return Math.min(level(player), BASE_LEVEL_CAP) / (float) BASE_LEVEL_CAP;
	}

	/** Which tier's name to show: 0 = start, 1 = peak, reached at level 45
	 * (the epic levels keep the peak name, they don't add a third tier). */
	public static int tier(final Player player) {
		return level(player) >= BASE_LEVEL_CAP ? 1 : 0;
	}

	/** The banking rate for a given advancement count. Pure — the client
	 * runs the same formula on the synced count. */
	public static float xpMultiplier(final int advancementCount) {
		return Math.min(1.0F + XP_PER_ADVANCEMENT * advancementCount, MAX_XP_MULTIPLIER);
	}

	/** The synced cached count; absent means not yet computed. */
	public static int advancementCount(final Player player) {
		Integer count = ((AttachmentTarget) player).getAttached(ModAttachments.ADVANCEMENT_COUNT);

		if (count != null) {
			return count;
		}

		// Lazy fallback (server only): the join hook normally beat us here.
		if (player instanceof ServerPlayer serverPlayer) {
			return refreshAdvancementCount(serverPlayer);
		}

		return 0;
	}

	/**
	 * Recount completed non-recipe advancements (the ones with a display
	 * block; hidden-ness is UI-only and still counts) and cache the result
	 * on the synced attachment. Called on join and from the award/revoke
	 * mixin, so {@link #bank} stays O(1) on the hot path.
	 */
	public static int refreshAdvancementCount(final ServerPlayer player) {
		ServerAdvancementManager manager = player.level().getServer().getAdvancements();
		PlayerAdvancements progress = player.getAdvancements();
		int count = 0;

		for (AdvancementHolder holder : manager.getAllAdvancements()) {
			if (holder.value().display().isPresent() && progress.getOrStartProgress(holder).isDone()) {
				count++;
			}
		}

		((AttachmentTarget) player).setAttached(ModAttachments.ADVANCEMENT_COUNT, count);
		return count;
	}

	/**
	 * Bank experience toward levels, scaled by the advancement rate at this
	 * moment. Banking-time scaling keeps ARCHETYPE_XP an append-only ledger:
	 * raising the rate later never inflates XP already earned, and a revoked
	 * advancement never deflates it.
	 */
	public static void bank(final Player player, final int amount) {
		if (amount <= 0 || level(player) >= MAX_LEVEL || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		int scaled = Math.round(amount * xpMultiplier(advancementCount(serverPlayer)));
		((AttachmentTarget) player).setAttached(ModAttachments.ARCHETYPE_XP, bankedXp(player) + scaled);
	}

	/**
	 * Join-time guard: if a retune ever leaves a player with more committed
	 * points than their banked XP now justifies, raise the bank to exactly
	 * cover them. Only raises, never lowers; a no-op once satisfied.
	 */
	public static void ensureBankCoversSpent(final Player player) {
		// Every committed point — normal and epic — needs a level under it.
		int needed = CUM[Math.min(spent(player) + epicSpent(player), MAX_LEVEL)];

		if (bankedXp(player) < needed) {
			((AttachmentTarget) player).setAttached(ModAttachments.ARCHETYPE_XP, needed);
		}
	}

	/** Amnesia's price: keep only this fraction of earned levels, the bank
	 * cut to exactly the kept level's cumulative cost. */
	public static void shaveLevels(final Player player, final float keepFraction) {
		int kept = Mth.clamp((int) Math.floor(level(player) * keepFraction), 0, MAX_LEVEL);
		((AttachmentTarget) player).setAttached(ModAttachments.ARCHETYPE_XP, CUM[kept]);
	}

	public static void grantLevels(final Player player, final int levels) {
		((AttachmentTarget) player).setAttached(ModAttachments.ARCHETYPE_XP,
				CUM[Math.min(level(player) + levels, MAX_LEVEL)]);
	}
}
