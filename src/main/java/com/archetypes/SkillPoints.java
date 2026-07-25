package com.archetypes;

import java.util.Optional;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
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
 * level is one skill point, and the price runs two curves back to back: the
 * base tier 1-45 is the original quadratic {@code round(1.2 L² + 15)},
 * untouched so no existing save shifts, and the epic tier 46-60 is a separate
 * table that climbs about 20% a level, from 7,000 to 88,000. The base tier is
 * 38,349 XP; the epic tier is 497,500 — the levels past 45 are 93% of the
 * whole road, which is the point. Level 45 is where the game changes shape,
 * not where it ends.
 *
 * <p>The mirror runs faster the further the player has actually gotten in the
 * game, and how much further is read off the advancement's own frame: a task
 * is worth {@link #XP_PER_TASK}, a goal {@link #XP_PER_GOAL}, a challenge
 * {@link #XP_PER_CHALLENGE}. There is no cap and no penalty — the rate starts
 * at x1 for everyone and only ever climbs, so a farm-parked player still
 * levels legally, just on the slow road, while a player who actually beat the
 * game runs it in half the time. The multiplier applies at banking time, so
 * past XP keeps its historical rate. The three sub-tree caps still bound how
 * much of a tree one build can own; the curve paces how fast it gets there.
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
	 * The epic tier's price list, levels 46 to 60 in order. Roughly x1.20 a
	 * level, hand-rounded so the numbers read as prices rather than as output
	 * from a formula, and starting at 7,000 — a deliberate step up from level
	 * 45's 2,445, because 46 is the first level of a different tier and should
	 * cost like one. Sums to 497,500, which is what makes the whole 5-to-60
	 * climb land at roughly 65 hours for a farm-parked player and half that
	 * for one who has actually played the game.
	 */
	private static final int[] EPIC_COST = {
		7_000, 8_500, 10_000, 12_000, 14_500,
		17_500, 21_000, 25_000, 30_000, 36_000,
		43_000, 51_000, 61_000, 73_000, 88_000,
	};

	/**
	 * The curve. Levels 1-45 in exact integer math: {@code 15 + round(6L²/5)},
	 * where {@code 6L² mod 5} is only ever 0, 1 or 4, so adding 2 before the
	 * integer divide rounds half-up correctly. Levels 46-60 come from
	 * {@link #EPIC_COST}. COST[L] is the price of level L; CUM[L] the total
	 * banked XP to reach it.
	 */
	private static final int[] COST = new int[MAX_LEVEL + 1];
	private static final int[] CUM = new int[MAX_LEVEL + 1];

	static {
		for (int level = 1; level <= BASE_LEVEL_CAP; level++) {
			COST[level] = 15 + (6 * level * level + 2) / 5;
		}

		for (int level = BASE_LEVEL_CAP + 1; level <= MAX_LEVEL; level++) {
			COST[level] = EPIC_COST[level - BASE_LEVEL_CAP - 1];
		}

		for (int level = 1; level <= MAX_LEVEL; level++) {
			CUM[level] = CUM[level - 1] + COST[level];
		}

		// Anchors from the design doc; a drifted curve should fail loudly. The
		// base-tier anchors are the pre-v2 ones and must never move — every
		// existing save's level is read off them.
		if (EPIC_COST.length != MAX_EPIC_POINTS || CUM[BASE_LEVEL_CAP] != 38_349
				|| CUM[15] != 1_713 || COST[1] != 16 || COST[BASE_LEVEL_CAP] != 2_445
				|| COST[BASE_LEVEL_CAP + 1] != 7_000 || CUM[50] != 90_349
				|| COST[MAX_LEVEL] != 88_000 || CUM[MAX_LEVEL] != 535_849) {
			throw new IllegalStateException("XP curve drifted: cum(45)=" + CUM[BASE_LEVEL_CAP]
					+ " cum(50)=" + CUM[50] + " cum(60)=" + CUM[MAX_LEVEL]);
		}
	}

	/**
	 * The advancement rate, weighted by the advancement's own frame. A task is
	 * the small change of progression (vanilla has 91 of them), a goal is a
	 * chapter (10), a challenge is an accomplishment (25) — so they are paid
	 * 1 : 15 : 40. Nothing here is capped and nothing subtracts: the rate is
	 * {@code 1 + 0.05·tasks + 0.75·goals + 2.00·challenges}, which is x1.00 for
	 * a fresh player, x1.60 for a farm-parked one holding twelve tasks, x24.80
	 * for a thorough playthrough (66/6/8) and x63.05 for the full 126.
	 * Vanilla-tuned: datapacks and mods grow the pool, so revisit per-modpack.
	 */
	public static final float XP_PER_TASK = 0.05F;
	public static final float XP_PER_GOAL = 0.75F;
	public static final float XP_PER_CHALLENGE = 2.00F;

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

	/** The banking rate for a frame-by-frame advancement tally. Pure, never
	 * below 1, never capped — the client runs the same formula on the synced
	 * counts. */
	public static float xpMultiplier(final int tasks, final int goals, final int challenges) {
		return 1.0F + XP_PER_TASK * tasks + XP_PER_GOAL * goals + XP_PER_CHALLENGE * challenges;
	}

	/** The banking rate this player is earning at right now. */
	public static float xpMultiplier(final Player player) {
		// Reading the total first is what triggers the lazy server-side
		// recount, so the two frame counts below are never stale against it.
		int total = advancementCount(player);
		int goals = attached(player, ModAttachments.ADVANCEMENT_GOALS);
		int challenges = attached(player, ModAttachments.ADVANCEMENT_CHALLENGES);

		return xpMultiplier(Math.max(total - goals - challenges, 0), goals, challenges);
	}

	private static int attached(final Player player,
			final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Integer> type) {
		Integer value = ((AttachmentTarget) player).getAttached(type);
		return value == null ? 0 : value;
	}

	/** The synced cached count, all frames together; absent means not yet
	 * computed. */
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
	 * block; hidden-ness is UI-only and still counts), split by frame, and
	 * cache all three numbers on the synced attachments. Called on join and
	 * from the award/revoke mixin, so {@link #bank} stays O(1) on the hot
	 * path. Returns the total, which is what the tree screen shows.
	 */
	public static int refreshAdvancementCount(final ServerPlayer player) {
		ServerAdvancementManager manager = player.level().getServer().getAdvancements();
		PlayerAdvancements progress = player.getAdvancements();
		int count = 0;
		int goals = 0;
		int challenges = 0;

		for (AdvancementHolder holder : manager.getAllAdvancements()) {
			Optional<DisplayInfo> display = holder.value().display();

			if (display.isEmpty() || !progress.getOrStartProgress(holder).isDone()) {
				continue;
			}

			count++;

			switch (display.get().getType()) {
				case GOAL -> goals++;
				case CHALLENGE -> challenges++;
				default -> {
					// A task; it is the remainder, so nothing to tally.
				}
			}
		}

		AttachmentTarget target = (AttachmentTarget) player;
		target.setAttached(ModAttachments.ADVANCEMENT_GOALS, goals);
		target.setAttached(ModAttachments.ADVANCEMENT_CHALLENGES, challenges);
		target.setAttached(ModAttachments.ADVANCEMENT_COUNT, count);
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

		int scaled = Math.round(amount * xpMultiplier(serverPlayer));
		((AttachmentTarget) player).setAttached(ModAttachments.ARCHETYPE_XP, bankedXp(player) + scaled);
	}

	/**
	 * Join-time guard: if a retune ever leaves a player with more committed
	 * points than their banked XP now justifies, raise the bank to exactly
	 * cover them. Only raises, never lowers; a no-op once satisfied.
	 */
	public static void ensureBankCoversSpent(final Player player) {
		// Every committed point needs a level under it: a normal point one of
		// levels 1-45, an epic point one past the base cap — e epic points are
		// only justified from level 45+e, however few normal points are spent.
		int epicSpent = epicSpent(player);
		int neededLevel = Math.max(spent(player),
				epicSpent > 0 ? BASE_LEVEL_CAP + epicSpent : 0);
		int needed = CUM[Math.min(neededLevel, MAX_LEVEL)];

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

	/**
	 * Testing only ({@code /archetypes level}): put the player exactly at
	 * {@code level} by writing the XP the curve says that level costs.
	 *
	 * <p>One write rather than a loop of {@link #bank} calls, and the difference
	 * matters: banking is scaled by the advancement multiplier at deposit time, and
	 * that multiplier is uncapped, so awarding {@code CUM[level]} in pieces would
	 * overshoot by whatever rate the tester happens to be on and land them
	 * somewhere near the level they asked for. Writing the
	 * cumulative cost lands on it, and everything downstream — {@link #available},
	 * {@link #epicAvailable}, {@link #tier} — is derived from the bank, so the
	 * points and the epic pool that follow are the ones the curve actually owes.
	 *
	 * <p>Lowering a level is allowed and is not undone here: the point pools clamp
	 * at zero rather than going negative, and {@link #ensureBankCoversSpent} will
	 * raise the bank back on the next join if committed points now outrun it.
	 */
	public static void setLevel(final Player player, final int level) {
		((AttachmentTarget) player).setAttached(ModAttachments.ARCHETYPE_XP,
				CUM[Mth.clamp(level, 0, MAX_LEVEL)]);
	}

	public static void grantLevels(final Player player, final int levels) {
		((AttachmentTarget) player).setAttached(ModAttachments.ARCHETYPE_XP,
				CUM[Math.min(level(player) + levels, MAX_LEVEL)]);
	}
}
