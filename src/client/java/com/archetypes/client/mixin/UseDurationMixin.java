package com.archetypes.client.mixin;

import com.archetypes.MagicArmaments;
import com.archetypes.ModItems;
import com.archetypes.compat.SpecialitiesBridge;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.UseDuration;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Spellbow's pull animation. Vanilla drives a bow's draw from the
 * {@code minecraft:use_duration} item model property — elapsed use ticks — and
 * has no quick-charge for bows, so it never scales; a bow that fires at full
 * power in five ticks would otherwise still show a barely-drawn string.
 *
 * <p>This scales the reported elapsed time by the SAME factor
 * {@code MagicBowItem.releaseUsing} divides the draw by, so what the model
 * shows is what the server fires.
 *
 * <p>Specialities' own {@code UseDurationMixin} matches any {@link
 * net.minecraft.world.item.BowItem}, which includes ours, and has already
 * applied its Archery scaling to {@code original}. Its factor is divided back
 * out here rather than left to compound: {@link MagicArmaments#drawTimeFactor}
 * already counts Archery, under a cap that a second application would break.
 * Both hooks are pure multiplications, so this holds whichever order Mixin
 * applies them in.
 */
@Mixin(UseDuration.class)
public abstract class UseDurationMixin {
	@ModifyReturnValue(method = "get", at = @At("RETURN"))
	private float archetypes$spellbowPull(final float original, final ItemStack itemStack,
			final ClientLevel level, final ItemOwner owner, final int seed) {
		if (original <= 0.0F || !ModItems.isMagicBow(itemStack)) {
			return original;
		}

		// The "remaining" variant counts down and is left alone, exactly as
		// Specialities leaves it — nothing must be divided back out of it.
		if (((UseDuration) (Object) this).remaining()) {
			return original;
		}

		LivingEntity entity = owner == null ? null : owner.asLivingEntity();

		if (!(entity instanceof Player player)) {
			return original;
		}

		float archeryApplied = 1.0F
				- SpecialitiesBridge.archeryDrawTimeReduction(SpecialitiesBridge.archeryLevel(player));

		return original * archeryApplied / MagicArmaments.drawTimeFactor(player);
	}
}
