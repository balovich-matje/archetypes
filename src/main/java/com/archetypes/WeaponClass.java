package com.archetypes;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * The melee families that own custom swing animations. Each class carries its
 * variant count: consecutive swings cycle through the variants (arc left, arc
 * right, overhead...) via the synced swing counter, deterministically on every
 * client. NONE keeps vanilla behaviour untouched.
 */
public enum WeaponClass {
	GREATSWORD(3),
	SWORD(2),
	MACE(2),
	HANDS(2),
	NONE(0);

	private final int variants;

	WeaponClass(final int variants) {
		this.variants = variants;
	}

	public int variants() {
		return this.variants;
	}

	public static WeaponClass of(final Player player) {
		var mainhand = player.getMainHandItem();

		if (ModItems.isGreatsword(mainhand)) {
			return GREATSWORD;
		}

		if (ModItems.isSword(mainhand)) {
			return SWORD;
		}

		if (mainhand.is(Items.MACE)) {
			return MACE;
		}

		// Bare hands means BARE: an offhand item turns punching back vanilla.
		if (mainhand.isEmpty() && player.getOffhandItem().isEmpty()) {
			return HANDS;
		}

		return NONE;
	}
}
