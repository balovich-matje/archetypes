package com.archetypes.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Shadow Step's strikes must land at full charge regardless of what the
 * player's swing timer says — vanilla only exposes resets (to zero, the
 * weakest state), so the ticker is set high directly before each strike.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("attackStrengthTicker")
	void archetypes$setAttackStrengthTicker(int ticks);
}
