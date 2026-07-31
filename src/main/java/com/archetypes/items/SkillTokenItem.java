package com.archetypes.items;

import com.archetypes.SkillPoints;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Testing affordance: grants archetype levels on use — one for the plain token,
 * the full 60 for the greater one, which is {@code SkillPoints.MAX_LEVEL} and so
 * lands a test account in the epic tier rather than one tier short of it.
 * Creative only — the server checks the game mode itself rather than trusting
 * that the item is hard to get.
 */
public class SkillTokenItem extends Item {
	private final int levels;

	public SkillTokenItem(final Properties properties, final int levels) {
		super(properties);
		this.levels = levels;
	}

	// 1.21.2 changed `Item.use`'s return from `InteractionResultHolder<ItemStack>` to a bare
	// `InteractionResult`, and the two vocabularies do not overlap: below the boundary
	// SUCCESS/PASS carry the stack, and the "succeed on this side only" case is
	// `sidedSuccess(stack, isClientSide)` rather than a plain SUCCESS. Only the signature
	// and the three returns fork; the body between them is shared.
	//? if >=1.21.2 {
	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (!player.isCreative()) {
			return InteractionResult.PASS;
		}

		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
	//?} else {
	/*@Override
	public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(
			final Level level, final Player player, final InteractionHand hand) {
		if (!player.isCreative()) {
			return net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand));
		}

		if (level.isClientSide()) {
			return net.minecraft.world.InteractionResultHolder.success(player.getItemInHand(hand));
		}
	*///?}

		SkillPoints.grantLevels(player, this.levels);
		// Action bar rather than chat: this fires on every click while testing.
		// 26.1 renamed displayClientMessage(component, true) to sendOverlayMessage — the
		// boundary is 26.1, not 26.2: both 26.x jars carry only the new spelling and
		// 1.21.11 only the old one (measured).
		//? if >=26.1 {
		player.sendOverlayMessage(Component.translatable("item.archetypes.skill_token.granted",
				SkillPoints.level(player), SkillPoints.MAX_LEVEL));
		//?} else {
		/*player.displayClientMessage(Component.translatable("item.archetypes.skill_token.granted",
				SkillPoints.level(player), SkillPoints.MAX_LEVEL), true);
		*///?}
		//? if >=1.21.2 {
		return InteractionResult.SUCCESS;
		//?} else {
		/*return net.minecraft.world.InteractionResultHolder.success(player.getItemInHand(hand));
		*///?}
	}
}
