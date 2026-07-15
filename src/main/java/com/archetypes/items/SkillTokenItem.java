package com.archetypes.items;

import com.archetypes.SkillPoints;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Testing affordance: grants one skill point on use. Creative only — the server
 * checks the game mode itself rather than trusting that the item is hard to get.
 */
public class SkillTokenItem extends Item {
	public SkillTokenItem(final Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (!player.isCreative()) {
			return InteractionResult.PASS;
		}

		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		SkillPoints.grantPoint(player);
		// Action bar rather than chat: this fires on every click while testing.
		// 26.2 renamed displayClientMessage(component, true) to sendOverlayMessage.
		player.sendOverlayMessage(Component.translatable("item.archetypes.skill_token.granted",
				SkillPoints.level(player), SkillPoints.MAX_LEVEL));
		return InteractionResult.SUCCESS;
	}
}
