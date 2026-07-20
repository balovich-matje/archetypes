package com.archetypes;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The buff-list face of Aura of Radiance: an icon and a countdown on the
 * effect list, nothing more.
 *
 * <p>It is deliberately inert — no {@code applyEffectTick}, no attributes.
 * {@link RadianceAura} owns every mechanic and {@code RADIANCE_END} is the
 * only clock; this effect is laid alongside that stamp for exactly the same
 * duration and is never read back, so it cannot become a second source of
 * truth. Registered here rather than in {@link ManaEffects} because it belongs
 * to the Priest, not to the mana potions, but registration follows the same
 * shape.
 */
public final class RadianceEffect {
	/** The Priest's gold, the same 0xFFD75E the halo motes and Holy Light's
	 * own dust are tinted — the effect swirl reads as the aura's light. */
	public static final Holder<MobEffect> AURA_OF_RADIANCE = register("aura_of_radiance",
			new Cosmetic(MobEffectCategory.BENEFICIAL, 0xFFD75E));

	private RadianceEffect() {
	}

	public static void initialize() {
		// Forces static initialization at mod init time.
	}

	/**
	 * Lay the badge for the whole of the aura's run. Removed first so a
	 * re-cast that SHORTENS the aura (a Beacon aura re-armed by a caster who
	 * has since respecced the node away) still leaves badge and stamp agreeing
	 * — vanilla's own refresh keeps the longer of the two durations.
	 */
	public static void show(final ServerPlayer player, final int ticks) {
		player.removeEffect(AURA_OF_RADIANCE);
		// Particles off: the aura already draws its own halo, and vanilla's
		// beneficial swirl on top of it is noise. Icon on — that is the point.
		player.addEffect(new MobEffectInstance(AURA_OF_RADIANCE, ticks, 0, false, false, true));
	}

	public static void hide(final ServerPlayer player) {
		player.removeEffect(AURA_OF_RADIANCE);
	}

	private static Holder<MobEffect> register(final String path, final MobEffect effect) {
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
				ResourceKey.create(Registries.MOB_EFFECT, Archetypes.id(path)), effect);
	}

	/** A MobEffect that does nothing at all: it exists to be displayed. */
	private static final class Cosmetic extends MobEffect {
		private Cosmetic(final MobEffectCategory category, final int color) {
			super(category, color);
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(final int duration, final int amplifier) {
			return false;
		}

		@Override
		public boolean applyEffectTick(final ServerLevel level, final LivingEntity target,
				final int amplifier) {
			return true;
		}
	}
}
