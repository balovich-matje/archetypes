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
	GREATSWORD(3, 0.8F),
	SWORD(2, 1.6F),
	MACE(2, 0.6F),
	HANDS(2, 4.0F),
	NONE(0, 1.0F);

	private final int variants;
	private final float baseAttackSpeed;

	WeaponClass(final int variants, final float baseAttackSpeed) {
		this.variants = variants;
		this.baseAttackSpeed = baseAttackSpeed;
	}

	public int variants() {
		return this.variants;
	}

	/** The class's unmodified attack-speed attribute; swing animations were
	 * authored against it, so playback scales by actual/base. */
	public float baseAttackSpeed() {
		return this.baseAttackSpeed;
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
