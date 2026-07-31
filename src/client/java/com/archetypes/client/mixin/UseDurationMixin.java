package com.archetypes.client.mixin;

// THE WHOLE COMPILATION UNIT IS 1.21.11-AND-UP, and the class it targets is the reason:
// `client.renderer.item.properties.numeric.UseDuration` is part of the item-model property
// system that arrived after 1.21.1, and `world.entity.ItemOwner` with it — neither package
// exists in the 1.21.1 mojmap jar. Skill Proficiencies' identically-targeted mixin takes
// exactly this gate on exactly these nodes.
//
// WHAT IS LOST, stated rather than hidden: the Spellbow still ANIMATES its draw below the
// boundary — the three `magic_bow_pulling_*` models and vanilla's `pull`/`pulling` model
// predicates are version-independent — but the animation is not compressed to match the
// quarter-length draw the server actually fires at, so a full-power shot leaves at a
// partly-drawn string. Cosmetic, one weapon, and a legacy host does exist if it is ever
// wanted (re-register `minecraft:pull` for MAGIC_BOW through `ItemProperties`), which is a
// registration rather than a mixin and so is not a `//?` arm of this file.
//
// The ENTRY has to leave the client mixin config too, and it is the LAST element of that
// array — which the line-blanking transform cannot handle without leaving a trailing comma.
// This node therefore takes a per-node override of the whole client config at
// versions/1.21.1-fabric/src/client/resources/. Keep the two in step.
//? if >=1.21.11 {

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

//  * The Spellbow's pull animation. Vanilla drives a bow's draw from the
// {@code minecraft:use_duration} item model property — elapsed use ticks — and
// has no quick-charge for bows, so it never scales; a bow that fires at full
// power in five ticks would otherwise still show a barely-drawn string.
//
// <p>This scales the reported elapsed time by the SAME factor
// {@code MagicBowItem.releaseUsing} divides the draw by, so what the model
// shows is what the server fires.
//
// <p>Specialities' own {@code UseDurationMixin} matches any {@link
// net.minecraft.world.item.BowItem}, which includes ours, and has already
// applied its Archery scaling to {@code original}. Its factor is divided back
// out here rather than left to compound: {@link MagicArmaments#drawTimeFactor}
// already counts Archery, under a cap that a second application would break.
// Both hooks are pure multiplications, so this holds whichever order Mixin
// applies them in.
@Mixin(UseDuration.class)
public abstract class UseDurationMixin {
	@ModifyReturnValue(method = "get(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/entity/ItemOwner;I)F", at = @At("RETURN"))
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
//?}
