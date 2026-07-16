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

	/** Award Spellcasting XP for mana spent; silently a no-op without the mod. */
	public static void awardSpellcastingXp(final ServerPlayer player, final int amount) {
		if (LOADED && amount > 0) {
			Linked.award(player, amount);
		}
	}

	/** Everything that names a Specialities class, loaded lazily and only
	 * behind the LOADED check above. */
	private static final class Linked {
		private static int level(final Player player) {
			return com.specialities.skills.SkillManager.get(player).level(SpellcastingSkill.INSTANCE);
		}

		private static void award(final ServerPlayer player, final int amount) {
			com.specialities.skills.SkillManager.addXp(player, SpellcastingSkill.INSTANCE, amount);
		}
	}
}
