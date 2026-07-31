package com.archetypes.items;

// STAGE 5 — the whole compilation unit is below-1.21 only (conventions §4's whole-file form).
//
// WHAT IS MISSING AND WHY A CLASS IS THE ANSWER. From 1.21 up a weapon's attribute block is
// the `ItemAttributeModifiers` COMPONENT, hung on `Item.Properties.attributes(...)`, and this
// mod builds it from DERIVED FLOATS (`0.6 * (1 + 3 + bonus) - 1 - bonus` and friends). Below
// 1.21 there is no component: the block is whatever `Item.getDefaultAttributeModifiers` hands
// back, and vanilla's own `SwordItem` builds it in its constructor from an INT damage
// parameter. Rounding those floats to vanilla's int would be a balance change on every
// legacy node — a copper greatsword is 5.5 damage on 26.x and would become 5 or 6 — so the
// item overrides the map instead and keeps the exact float.
//
// The two modifier UUIDs and the ADDITION operation are vanilla's own
// (`Item.BASE_ATTACK_DAMAGE_UUID` / `BASE_ATTACK_SPEED_UUID`), which is what makes these
// modifiers REPLACE the tooltip's base numbers rather than stack on top of them — exactly
// what `ItemAttributeModifiers`' `BASE_ATTACK_DAMAGE_ID` does above the boundary.
//
// `super(tier, 0, speed, …)`'s zero is deliberate: `SwordItem.getDamage()` is the only thing
// that reads it, vanilla never calls it in a damage path (`Player.attack` reads the ATTRIBUTE),
// and the attribute is what this class overrides. Passing a rounded int there would put a
// wrong number somewhere a reader could believe.
//
// Being a `SwordItem` at all is load-bearing and is Stage 4's finding, not a style choice:
// `Player.attack` gates the sweep on `getItemInHand(MAIN_HAND).getItem() instanceof SwordItem`.
//? if <1.21 {
/*import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

public class LegacySword extends SwordItem {
	private final Multimap<Attribute, AttributeModifier> modifiers;

	public LegacySword(final Tier tier, final float damage, final float speed,
			final Properties properties) {
		super(tier, 0, speed, properties);
		this.modifiers = ImmutableMultimap.<Attribute, AttributeModifier>builder()
				.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID,
						"Weapon modifier", damage + tier.getAttackDamageBonus(),
						AttributeModifier.Operation.ADDITION))
				.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID,
						"Weapon modifier", speed, AttributeModifier.Operation.ADDITION))
				.build();
	}

	@Override
	public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(final EquipmentSlot slot) {
		return slot == EquipmentSlot.MAINHAND ? this.modifiers : super.getDefaultAttributeModifiers(slot);
	}
}
*///?}
