package com.archetypes;

import net.minecraft.core.Holder;
//? if >=1.21 {
import net.minecraft.core.component.DataComponents;
//?}
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
//? if >=1.21 {
import net.minecraft.world.item.enchantment.ItemEnchantments;
//?}

/**
 * Reinforced Straps: a shield in this player's hands wears as though it carried
 * one more level of Unbreaking than it does.
 *
 * <h2>Why the level and not the durability</h2>
 * Unbreaking is not a percentage. Vanilla's {@code unbreaking.json} answers the
 * {@code minecraft:item_damage} effect with a {@code remove_binomial} whose
 * chance is a {@code fraction} of two linears — for anything that is not armour
 * (a shield included) {@code level / (level + 1)}, rolled once per point of
 * damage. So "half again as much durability" and "one more level" are different
 * curves at every level, and only one of them is the thing the description says.
 * Shaving the amount by hand would also silently disagree with any datapack
 * that retunes the enchantment.
 *
 * <p>So the node hands the vanilla roll a BOOSTED STACK instead. The whole
 * probability formula is vanilla's, evaluated at {@code level + 1}, whatever
 * that formula currently is.
 *
 * <h2>Where the roll actually is</h2>
 * One place, and it is not {@code BlocksAttacks.hurtBlockingItem}:
 * {@code hurtBlockingItem} only decides how many points of damage a block costs
 * ({@code itemDamage.apply}) and then calls {@code ItemStack.hurtAndBreak}.
 * Every damage path in the game — blocking, mining, attacking, Instinctive
 * Guard's passive block — funnels through {@code ItemStack.processDurabilityChange},
 * whose single {@code EnchantmentHelper.processDurabilityChange(level, this,
 * amount)} call is where enchantments get to shrink the number. That is the one
 * call {@code ItemStackMixin} wraps, so the node covers every way a held shield
 * can lose durability rather than just the one it was written for.
 *
 * <h2>The cap</h2>
 * {@link Tuning#REINFORCED_STRAPS_LEVEL_CAP} is a cap on the TOTAL effective
 * level, not on the bonus: the node stacks additively with the real enchant, and
 * Unbreaking III plus this is IV and stops there. It is written as a
 * {@code max} of the original so a level already past the cap — a datapack's, a
 * command's — is never quietly reduced by owning the node.
 */
public final class ReinforcedStraps {
	private ReinforcedStraps() {
	}

	/**
	 * The stack the durability roll should be run against: {@code stack} itself
	 * unless this is a shield in the hands of a Straps owner, in which case a
	 * copy carrying the higher level.
	 *
	 * <p>A COPY, never the real stack: the boost is a property of who is holding
	 * the shield, so writing it onto the item would follow the shield into a
	 * chest and into another player's hands. Copying costs a component map on
	 * the rare tick a guarded shield takes damage.
	 */
	public static ItemStack forDurabilityRoll(final ServerLevel level, final Player player,
			final ItemStack stack) {
		if (!heldShieldOf(player, stack)
				|| ProtectorNodes.rank(SubTree.PROTECTOR,
						NodePurchases.owned(player, SubTree.PROTECTOR),
						ProtectorNodes.Family.SPEARWALL) <= 0) {
			return stack;
		}

		// Deliberately DataComponents.ENCHANTMENTS rather than
		// EnchantmentHelper.getComponentType: the roll's own iteration
		// (runIterationOnItem) reads that component unconditionally, and the
		// only stack the two disagree about is an enchanted book, which is not
		// a shield anybody blocks with.
		// STAGE 5: below 1.21 there is no component map — enchantments live in the stack's
		// NBT and `EnchantmentHelper` is the whole accessor. Same three steps (read the
		// level, raise it, write it onto a COPY), same cap, same early-out.
		//? if >=1.21 {
		ItemEnchantments current =
				stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		Holder<Enchantment> unbreaking = unbreaking(level);
		int owned = current.getLevel(unbreaking);
		int boosted = Math.max(owned,
				Math.min(owned + Tuning.REINFORCED_STRAPS_LEVELS, Tuning.REINFORCED_STRAPS_LEVEL_CAP));

		if (boosted == owned) {
			return stack;
		}

		ItemEnchantments.Mutable raised = new ItemEnchantments.Mutable(current);
		raised.set(unbreaking, boosted);

		ItemStack copy = stack.copy();
		copy.set(DataComponents.ENCHANTMENTS, raised.toImmutable());

		return copy;
		//?} else {
		/*Enchantment unbreaking = unbreaking(level);
		int owned = net.minecraft.world.item.enchantment.EnchantmentHelper
				.getItemEnchantmentLevel(unbreaking, stack);
		int boosted = Math.max(owned,
				Math.min(owned + Tuning.REINFORCED_STRAPS_LEVELS, Tuning.REINFORCED_STRAPS_LEVEL_CAP));

		if (boosted == owned) {
			return stack;
		}

		java.util.Map<Enchantment, Integer> raised = new java.util.LinkedHashMap<>(
				net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(stack));
		raised.put(unbreaking, boosted);

		ItemStack copy = stack.copy();
		net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(raised, copy);

		return copy;
		*///?}
	}

	/**
	 * Whether this is one of the player's two hand stacks and blocks with.
	 *
	 * <p>Reference identity, because that is what the durability path carries:
	 * {@code getItemBlockingWith} returns {@code useItem}, which
	 * {@code startUsingItem} assigned straight from {@code getItemInHand}, and
	 * Instinctive Guard passes {@code getItemInHand} itself. A shield in a chest
	 * or on a hotbar slot the player is not holding is somebody else's problem.
	 *
	 * <p>"Shield" is {@code BLOCKS_ATTACKS}, the same definition Instinctive
	 * Guard and Immovable Object use — a modded shield is a shield.
	 */
	private static boolean heldShieldOf(final Player player, final ItemStack stack) {
		// "Shield" is the BLOCKS_ATTACKS component from 1.21.11 and the BLOCK use animation
		// below it — which is vanilla's OWN definition on those versions
		// (`LivingEntity.isBlocking` reads exactly that), so a modded shield is still a
		// shield either way.
		//? if >=1.21.11 {
		return stack.has(DataComponents.BLOCKS_ATTACKS)
		//?} else {
		/*return stack.getUseAnimation() == net.minecraft.world.item.UseAnim.BLOCK
		*///?}
				&& (player.getMainHandItem() == stack || player.getOffhandItem() == stack);
	}

	/** The Holder must come from the level's registries — enchantments are
	 * datapack content and {@code Enchantments.UNBREAKING} is only a key. */
	// Below 1.21 enchantments are NOT datapack content: `Enchantments.UNBREAKING` is the
	// enchantment itself and no registry is involved, so the level parameter goes unread
	// rather than the method going away — every caller still has one to hand.
	//? if >=1.21 {
	private static Holder<Enchantment> unbreaking(final ServerLevel level) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.UNBREAKING);
	}
	//?} else {
	/*private static Enchantment unbreaking(final ServerLevel level) {
		return Enchantments.UNBREAKING;
	}
	*///?}
}
