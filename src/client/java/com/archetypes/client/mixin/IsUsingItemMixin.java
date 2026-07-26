package com.archetypes.client.mixin;

import com.archetypes.Spearwall;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.IsUsingItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Spearwall's shield wears its blocking model.
 *
 * <p>A raised shield does not LOOK raised because of an arm transform — in
 * first person the {@code BLOCK} branch of {@code ItemInHandRenderer} skips a
 * {@code ShieldItem} entirely. It looks raised because
 * {@code assets/minecraft/items/shield.json} is a {@code minecraft:condition}
 * on the {@code minecraft:using_item} property, swapping
 * {@code item/shield} for {@code item/shield_blocking} — a different model with
 * different display transforms. That property is this class, and its whole body
 * is {@code owner.isUsingItem() && owner.getUseItem() == itemStack}.
 *
 * <p>Under Spearwall the use item is the spear, so the shield answered no and
 * rendered flat in every view at once. Answering yes for the guard stack fixes
 * first person, third person, and anything else that draws a held item, in one
 * place — which is why this is here and not in two renderers.
 *
 * <p>Read off the published {@code SPEARWALL_GUARD} flag, not recomputed: see
 * {@link Spearwall#guardRaised}. A renderer runs for every player on screen and
 * only the owner's client can evaluate the owner's build.
 */
@Mixin(IsUsingItem.class)
public abstract class IsUsingItemMixin {
	@ModifyReturnValue(method = "get", at = @At("RETURN"))
	private boolean archetypes$spearwallGuardCountsAsUsed(final boolean using,
			final ItemStack itemStack, final @Nullable ClientLevel level,
			final @Nullable LivingEntity owner, final int seed,
			final ItemDisplayContext displayContext) {
		return using || (owner != null && Spearwall.isRaisedGuard(owner, itemStack));
	}
}
