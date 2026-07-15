package com.archetypes;

import com.archetypes.items.SkillTokenItem;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/**
 * Items. Each one amplifies an archetype; none of them switches it on — see
 * "items amplify, they never gate" in notes/design.md.
 */
public final class ModItems {
	/**
	 * A claymore hits for 1.5x a sword of the same material and swings half as
	 * often, so it lands at 0.75x the sword's DPS: a rhythm, not an upgrade —
	 * big commitment, big recovery. If it ever out-DPSes a sword it has become
	 * power creep in a trade-off costume.
	 *
	 * <p>Vanilla's damage attribute is {@code 1 (player) + weapon base + material
	 * bonus}, and a sword's base is a flat 3. A flat base here would drift with
	 * the material (1.75x on wood, 1.38x on netherite), so the base is derived
	 * from the material instead: {@code 5 + bonus/2} solves
	 * {@code 1 + base + bonus == 1.5 * (1 + 3 + bonus)} for every material.
	 */
	private static final float SWORD_BASE_DAMAGE = 3.0F;
	private static final float DAMAGE_MULTIPLIER = 1.5F;
	/** Sword is -2.4 (1.6 swings/s off a 4.0 base); -3.2 gives 0.8/s, half. */
	private static final float CLAYMORE_ATTACK_SPEED = -3.2F;

	/** Creative-only: one skill point per use. See {@link SkillTokenItem}. */
	public static final Item SKILL_TOKEN = registerSkillToken();

	public static final Item WOODEN_CLAYMORE = claymore("wooden", ToolMaterial.WOOD);
	public static final Item STONE_CLAYMORE = claymore("stone", ToolMaterial.STONE);
	public static final Item COPPER_CLAYMORE = claymore("copper", ToolMaterial.COPPER);
	public static final Item IRON_CLAYMORE = claymore("iron", ToolMaterial.IRON);
	public static final Item GOLDEN_CLAYMORE = claymore("golden", ToolMaterial.GOLD);
	public static final Item DIAMOND_CLAYMORE = claymore("diamond", ToolMaterial.DIAMOND);
	public static final Item NETHERITE_CLAYMORE = claymore("netherite", ToolMaterial.NETHERITE);

	private ModItems() {
	}

	/** Base damage that makes this material's claymore exactly 1.5x its sword. */
	private static float baseDamageFor(final ToolMaterial material) {
		float bonus = material.attackDamageBonus();
		return DAMAGE_MULTIPLIER * (1.0F + SWORD_BASE_DAMAGE + bonus) - 1.0F - bonus;
	}

	private static Item registerSkillToken() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Archetypes.id("skill_token"));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new SkillTokenItem(new Item.Properties().setId(key)));
	}

	private static Item claymore(final String prefix, final ToolMaterial material) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Archetypes.id(prefix + "_claymore"));
		Item.Properties properties = material.applySwordProperties(
				new Item.Properties().setId(key), baseDamageFor(material), CLAYMORE_ATTACK_SPEED);
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties));
	}

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.accept(SKILL_TOKEN));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(WOODEN_CLAYMORE);
			output.accept(STONE_CLAYMORE);
			output.accept(COPPER_CLAYMORE);
			output.accept(IRON_CLAYMORE);
			output.accept(GOLDEN_CLAYMORE);
			output.accept(DIAMOND_CLAYMORE);
			output.accept(NETHERITE_CLAYMORE);
		});
	}
}
