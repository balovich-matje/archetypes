package com.archetypes.items;

import com.archetypes.SkillPoints;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Testing affordance: grants skill points on use — one for the plain token,
 * the full 45 for the greater one. Creative only — the server checks the
 * game mode itself rather than trusting that the item is hard to get.
 */
public class SkillTokenItem extends Item {
	private final int levels;

	public SkillTokenItem(final Properties properties, final int levels) {
		super(properties);
		this.levels = levels;
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (!player.isCreative()) {
			return InteractionResult.PASS;
		}

		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		SkillPoints.grantLevels(player, this.levels);
		// Action bar rather than chat: this fires on every click while testing.
		// 26.2 renamed displayClientMessage(component, true) to sendOverlayMessage.
		player.sendOverlayMessage(Component.translatable("item.archetypes.skill_token.granted",
				SkillPoints.level(player), SkillPoints.MAX_LEVEL));
		return InteractionResult.SUCCESS;
	}
}
