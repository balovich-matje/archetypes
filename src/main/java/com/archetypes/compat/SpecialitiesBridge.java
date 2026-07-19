package com.archetypes.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The only place archetype code touches Specialities from. The mod is a soft
 * dependency: every call is gated on it being loaded, and the classes that
 * import its API live behind this class's inner holder so the JVM never
 * resolves them otherwise. Without Specialities the Seeker still works — the
 * mana pool just stays at its base size, growing nowhere.
 */
public final class SpecialitiesBridge {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("specialities");

	private SpecialitiesBridge() {
	}

	/** The player's Spellcasting level, or 0 without Specialities. */
	public static int spellcastingLevel(final Player player) {
		return LOADED ? Linked.level(player) : 0;
	}

	/** The player's Archery level, or 0 without Specialities. */
	public static int archeryLevel(final Player player) {
		return LOADED ? Linked.archery(player) : 0;
	}

	/**
	 * The draw-time reduction Specialities' Archery skill grants at a level, as
	 * a fraction of the normal draw (0 without the mod, or at level 0). Read
	 * from their published curve rather than mirrored here, so a retune on
	 * their side moves this with it.
	 *
	 * <p>Their {@code BowItemMixin} applies this by dividing the held time,
	 * which never reaches the Spellbow: {@code MagicBowItem} overrides
	 * {@code releaseUsing} and never calls super. The Spellbow adds it back
	 * itself (see {@code MagicArmaments.drawTimeFactor}).
	 */
	public static float archeryDrawTimeReduction(final int level) {
		return LOADED && level > 0 ? Linked.archeryReduction(level) : 0.0F;
	}

	/** Award Spellcasting XP for mana spent; silently a no-op without the mod. */
	public static void awardSpellcastingXp(final ServerPlayer player, final int amount) {
		if (LOADED && amount > 0) {
			Linked.award(player, amount);
		}
	}

	/**
	 * The tomes: jump ahead whole Spellcasting levels. Returns the level
	 * reached, or -1 without Specialities (there is no skill to level).
	 */
	public static int grantSpellcastingLevels(final ServerPlayer player, final int levels) {
		return LOADED ? Linked.grantLevels(player, levels) : -1;
	}

	/** Everything that names a Specialities class, loaded lazily and only
	 * behind the LOADED check above. */
	private static final class Linked {
		private static int level(final Player player) {
			return com.specialities.skills.SkillManager.get(player).level(SpellcastingSkill.INSTANCE);
		}

		private static int archery(final Player player) {
			return com.specialities.skills.SkillManager.get(player)
					.level(com.specialities.skills.Skill.ARCHERY);
		}

		/** Their curve is a time MULTIPLIER (1.0 at level 0, falling with
		 * level); the reduction is its complement. */
		private static float archeryReduction(final int level) {
			return 1.0F - com.specialities.skills.Tuning.recoveryTimeMultiplier(level);
		}

		private static void award(final ServerPlayer player, final int amount) {
			com.specialities.skills.SkillManager.addXp(player, SpellcastingSkill.INSTANCE, amount);
		}

		private static int grantLevels(final ServerPlayer player, final int levels) {
			com.specialities.skills.SkillManager.addLevels(player, SpellcastingSkill.INSTANCE, levels);
			return level(player);
		}
	}
}
