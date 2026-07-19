package com.archetypes.items;

import com.archetypes.MagicArmaments;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The conjured sword of Magic Armaments. Beyond its stats (set in
 * {@code ModItems.registerMagicSword}) its one behavior is self-destruction:
 * a stack ticking in any inventory whose holder isn't mid-channel — a swap
 * into a stashed slot, another player's pickup, a mob's grab — is voided, so
 * no juggling turns the summon into a keepable weapon.
 */
public class MagicSwordItem extends Item {
	public MagicSwordItem(final Properties properties) {
		super(properties);
	}

	@Override
	public void inventoryTick(final ItemStack stack, final ServerLevel level, final Entity entity,
			final @Nullable EquipmentSlot slot) {
		MagicArmaments.purgeStray(stack, entity);
	}
}
