package com.archetypes.items;

import com.archetypes.compat.SpecialitiesBridge;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Testing affordance, the twin of Specialities' knowledge books: +25 or +100
 * Spellcasting levels on use. Creative-only, checked server-side; without
 * Specialities installed there is no skill to level and the tome says so.
 */
public class SpellcastingTomeItem extends Item {
	private final int levels;

	public SpellcastingTomeItem(final Properties properties, final int levels) {
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

		int reached = SpecialitiesBridge.grantSpellcastingLevels((ServerPlayer) player, this.levels);
		player.sendOverlayMessage(reached < 0
				? Component.translatable("item.archetypes.spellcasting_tome.no_specialities")
				: Component.translatable("item.archetypes.spellcasting_tome.granted", reached));
		return InteractionResult.SUCCESS;
	}
}
