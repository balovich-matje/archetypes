package com.archetypes;

import net.minecraft.world.entity.player.Player;

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

		// `#archetypes:maces`, not `Items.MACE` (GitHub #3). No version block: the tag exists
		// on every node, and on 1.20.1 — where `minecraft:mace` does not — its default is
		// empty, so vanilla still never answers MACE there while a pack's own mace can.
		if (ModItems.isMace(mainhand)) {
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
