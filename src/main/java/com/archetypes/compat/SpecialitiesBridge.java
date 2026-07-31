package com.archetypes.compat;

import com.archetypes.platform.Platform;

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
	private static final boolean LOADED = Platform.INSTANCE.isModLoaded("specialities");

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

	/**
	 * How far Skill Proficiencies has raised the vanilla bottom HUD <b>this frame</b>,
	 * or 0 without the mod.
	 *
	 * <p>Read live, never mirrored. Both of this mod's bottom-HUD rows used to carry
	 * their own hardcoded {@code SPECIALITIES_SHIFT = 7} applied on mere PRESENCE of
	 * the other mod, and since its 1.6.0 HUD-bar toggle that number can be 0 at
	 * runtime — a divergence neither build could see, because both compile and both
	 * draw, just seven pixels apart (design R-C4).
	 */
	public static int hudShift() {
		return LOADED ? Linked.hudShift() : 0;
	}

	// From 1.21.11 down `src/main` cannot name `com.specialities.client` AT ALL, and it is
	// not an API change: Skill Proficiencies' jar is split-environment, the node runs
	// fabric-loom-remap from here down, and the remapping loom honours the split and puts
	// only the common half on this source set (the full measurement is in
	// platform/ClientNetHooks). So on those nodes the value is handed down from client init
	// — the same shape as `Net#clientReceivers` and `ClientNetHooks` — and every CALLER
	// keeps the one spelling `SpecialitiesBridge.hudShift()` on all seven nodes, which is
	// the point: `ManaHud` and `BankedHungerHud` get re-forked at three more boundaries
	// before this port is done, and none of those forks should have to know about this one.
	//
	// STAGE 6a scoped this to `fabric &&`, matching `client/ClientHandDown`, which had already
	// been scoped that way and is the only thing that calls the installer. The reason is in the
	// paragraph above and is worth stating as the rule it is: THE SPLIT IS A FABRIC-LOOM FACT,
	// NOT A VERSION ONE. Under ModDevGradle there is no split-environment jar and no remapper,
	// so `src/main` on the loader axis sees the whole Skill Proficiencies jar including its
	// client half — which is why `Linked.hudShift` below takes the direct call there.
	//? if fabric && <26.1 {
	/*private static volatile java.util.function.IntSupplier clientHudShift = () -> 0;

	// Called once from client init, and only when Skill Proficiencies is actually loaded —
	// the supplier resolves one of its client classes.
	public static void installClientHudShift(final java.util.function.IntSupplier supplier) {
		clientHudShift = supplier;
	}
	*///?}

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

		private static int hudShift() {
			// The arms are ordered hand-down-first so the ELSE is the direct call, which is
			// what both 26.x nodes and both loader nodes take. Same two arms as before Stage
			// 6a, same statements on every Fabric node — only the predicate moved from
			// `>=26.1` to its `fabric && <26.1` complement.
			//? if fabric && <26.1 {
			/*return clientHudShift.getAsInt();
			*///?} else {
			return com.specialities.client.SpecialitiesClient.hudShift();
			//?}
		}

		private static int grantLevels(final ServerPlayer player, final int levels) {
			com.specialities.skills.SkillManager.addLevels(player, SpellcastingSkill.INSTANCE, levels);
			return level(player);
		}
	}
}
