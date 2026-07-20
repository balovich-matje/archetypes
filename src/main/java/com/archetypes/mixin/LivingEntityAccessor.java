package com.archetypes.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Two pieces of vanilla bookkeeping the mod has to write directly.
 *
 * <p>{@code attackStrengthTicker}: Shadow Step's strikes must land at full
 * charge regardless of what the player's swing timer says — vanilla only
 * exposes resets (to zero, the weakest state), so the ticker is set high
 * directly before each strike.
 *
 * <p>{@code lastHurt}: half of {@code hurtServer}'s damage-cooldown state (the
 * other half, {@code invulnerableTime}, is already public on {@code Entity}).
 * {@link com.archetypes.RadianceAura} saves both around a pulse so a
 * sub-second aura tick lands without ever changing how the victim's i-frames
 * behave for anyone else's damage.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("attackStrengthTicker")
	void archetypes$setAttackStrengthTicker(int ticks);

	@Accessor("lastHurt")
	float archetypes$getLastHurt();

	@Accessor("lastHurt")
	void archetypes$setLastHurt(float amount);
}
