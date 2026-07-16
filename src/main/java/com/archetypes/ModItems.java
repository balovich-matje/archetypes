package com.archetypes;

import com.archetypes.items.SkillTokenItem;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
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
	 * A greatsword hits for 1.5x a sword of the same material and swings half as
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
	private static final float GREATSWORD_ATTACK_SPEED = -3.2F;

	/** All seven greatswords; kept out of minecraft:swords so sword-scoped
	 * passives (bleed, lunge) never trigger from the two-hander. */
	public static final TagKey<Item> GREATSWORDS = TagKey.create(Registries.ITEM, Archetypes.id("greatswords"));

	public static boolean isGreatsword(final net.minecraft.world.item.ItemStack stack) {
		return stack.is(GREATSWORDS);
	}

	/** A one-handed sword: the vanilla tag minus our greatswords. */
	public static boolean isSword(final net.minecraft.world.item.ItemStack stack) {
		return stack.is(ItemTags.SWORDS) && !stack.is(GREATSWORDS);
	}

	/** Creative-only: one skill point per use. See {@link SkillTokenItem}. */
	public static final Item SKILL_TOKEN = registerSkillToken();

	public static final Item WOODEN_GREATSWORD = greatsword("wooden", ToolMaterial.WOOD);
	public static final Item STONE_GREATSWORD = greatsword("stone", ToolMaterial.STONE);
	public static final Item COPPER_GREATSWORD = greatsword("copper", ToolMaterial.COPPER);
	public static final Item IRON_GREATSWORD = greatsword("iron", ToolMaterial.IRON);
	public static final Item GOLDEN_GREATSWORD = greatsword("golden", ToolMaterial.GOLD);
	public static final Item DIAMOND_GREATSWORD = greatsword("diamond", ToolMaterial.DIAMOND);
	public static final Item NETHERITE_GREATSWORD = greatsword("netherite", ToolMaterial.NETHERITE);

	private ModItems() {
	}

	/** Base damage that makes this material's greatsword exactly 1.5x its sword. */
	private static float baseDamageFor(final ToolMaterial material) {
		float bonus = material.attackDamageBonus();
		return DAMAGE_MULTIPLIER * (1.0F + SWORD_BASE_DAMAGE + bonus) - 1.0F - bonus;
	}

	private static Item registerSkillToken() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Archetypes.id("skill_token"));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new SkillTokenItem(new Item.Properties().setId(key)));
	}

	private static Item greatsword(final String prefix, final ToolMaterial material) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Archetypes.id(prefix + "_greatsword"));
		Item.Properties properties = material.applySwordProperties(
				new Item.Properties().setId(key), baseDamageFor(material), GREATSWORD_ATTACK_SPEED);
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties));
	}

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.accept(SKILL_TOKEN));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(WOODEN_GREATSWORD);
			output.accept(STONE_GREATSWORD);
			output.accept(COPPER_GREATSWORD);
			output.accept(IRON_GREATSWORD);
			output.accept(GOLDEN_GREATSWORD);
			output.accept(DIAMOND_GREATSWORD);
			output.accept(NETHERITE_GREATSWORD);
		});
	}
}
