package com.archetypes;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * The melee families our combat rules apply to: swing gating (no half-charged
 * flicks), hold-to-attack, and per-class flavour like the greatsword's whoosh.
 * NONE keeps vanilla behaviour untouched. (The custom swing poses these once
 * drove were deprecated in favour of future Better Combat compatibility.)
 */
public enum WeaponClass {
	GREATSWORD,
	SWORD,
	DAGGER,
	MACE,
	WAND,
	HANDS,
	NONE;

	public static WeaponClass of(final Player player) {
		var mainhand = player.getMainHandItem();

		if (ModItems.isGreatsword(mainhand)) {
			return GREATSWORD;
		}

		if (ModItems.isDagger(mainhand)) {
			return DAGGER;
		}

		if (ModItems.isSword(mainhand)) {
			return SWORD;
		}

		if (mainhand.is(Items.MACE)) {
			return MACE;
		}

		if (ModItems.isWand(mainhand)) {
			return WAND;
		}

		// Bare hands means BARE: an offhand item turns punching back vanilla.
		if (mainhand.isEmpty() && player.getOffhandItem().isEmpty()) {
			return HANDS;
		}

		return NONE;
	}
}
