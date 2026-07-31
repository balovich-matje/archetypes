package com.archetypes.items;

import com.archetypes.MagicArmaments;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
//? if >=1.21.11 {
import org.jspecify.annotations.Nullable;
//?} else {
/*import org.jetbrains.annotations.Nullable;
*///?}

/**
 * The conjured sword of Magic Armaments. Beyond its stats (set in
 * {@code ModItems.registerMagicSword}) its one behavior is self-destruction:
 * a stack ticking in any inventory whose holder isn't mid-channel — a swap
 * into a stashed slot, another player's pickup, a mob's grab — is voided, so
 * no juggling turns the summon into a keepable weapon.
 */
// EXTENDS `SwordItem` BELOW 1.21.11, AND THAT IS A BALANCE DECISION, NOT A STYLE ONE.
// 1.21.1's `Player.attack` gates the sweep on
// `getItemInHand(MAIN_HAND).getItem() instanceof SwordItem` (read out of its bytecode at
// offset 415); 26.x gates it on the item's own sword profile, which the conjured sword has.
// A plain `Item` here would silently drop the sweep the same weapon performs on 26.x.
// `Tiers.DIAMOND` matches `ToolMaterial.DIAMOND`, which is what ModItems applies above.
//? if >=1.21.11 {
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
//?} else {
/*public class MagicSwordItem extends net.minecraft.world.item.SwordItem {
	public MagicSwordItem(final Properties properties) {
		super(net.minecraft.world.item.Tiers.DIAMOND, properties);
	}

	// The modern hook is server-only BY SIGNATURE (`ServerLevel`); the legacy one runs on
	// both logical sides, so the side test it gives away for free is made explicit here.
	@Override
	public void inventoryTick(final ItemStack stack, final net.minecraft.world.level.Level level,
			final Entity entity, final int slotId, final boolean selected) {
		if (level.isClientSide()) {
			return;
		}

		MagicArmaments.purgeStray(stack, entity);
	}
}
*///?}
