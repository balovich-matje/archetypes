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

	/**
	 * A dagger is the sword's opposite trade to the greatsword: 0.6x the
	 * damage at 1.5x the swings, landing at 0.9x the sword's DPS — quick,
	 * cheap hits for the Assassin, whose tree pays the missing tenth back
	 * with interest. Derived from the material like the greatsword's, so the
	 * ratio holds from wood to netherite.
	 */
	private static final float DAGGER_MULTIPLIER = 0.6F;
	/** Sword is -2.4 (1.6 swings/s off a 4.0 base); -1.6 gives 2.4/s, 1.5x. */
	private static final float DAGGER_ATTACK_SPEED = -1.6F;

	/** All seven greatswords; kept out of minecraft:swords so sword-scoped
	 * passives (bleed, lunge) never trigger from the two-hander. */
	public static final TagKey<Item> GREATSWORDS = TagKey.create(Registries.ITEM, Archetypes.id("greatswords"));
	public static final TagKey<Item> DAGGERS = TagKey.create(Registries.ITEM, Archetypes.id("daggers"));
	/** Just the starting wand for now, but casting will check the tag, so
	 * better wands are a texture and a recipe, not a code change. */
	public static final TagKey<Item> WANDS = TagKey.create(Registries.ITEM, Archetypes.id("wands"));

	public static boolean isGreatsword(final net.minecraft.world.item.ItemStack stack) {
		return stack.is(GREATSWORDS);
	}

	public static boolean isDagger(final net.minecraft.world.item.ItemStack stack) {
		return stack.is(DAGGERS);
	}

	public static boolean isWand(final net.minecraft.world.item.ItemStack stack) {
		return stack.is(WANDS);
	}

	/** Anything martial enough to disqualify a Seeker from regenerating
	 * mana: real weapons and shields, in either hand. */
	public static boolean isCombatWeapon(final net.minecraft.world.item.ItemStack stack) {
		return stack.is(ItemTags.SWORDS) // greatswords and daggers live in this tag too
				|| stack.is(ItemTags.SPEARS)
				|| stack.is(net.minecraft.world.item.Items.MACE)
				|| stack.is(net.minecraft.world.item.Items.BOW)
				|| stack.is(net.minecraft.world.item.Items.CROSSBOW)
				|| stack.is(net.minecraft.world.item.Items.SHIELD)
				|| stack.is(net.minecraft.world.item.Items.TRIDENT);
	}

	public static boolean holdingCombatWeapon(final net.minecraft.world.entity.player.Player player) {
		return isCombatWeapon(player.getMainHandItem()) || isCombatWeapon(player.getOffhandItem());
	}

	/** A one-handed sword: the vanilla tag minus our own blades in it. */
	public static boolean isSword(final net.minecraft.world.item.ItemStack stack) {
		return stack.is(ItemTags.SWORDS) && !stack.is(GREATSWORDS) && !stack.is(DAGGERS);
	}

	/** Creative-only: skill points per use. See {@link SkillTokenItem}. */
	public static final Item SKILL_TOKEN = registerSkillToken("skill_token", 1);
	public static final Item SKILL_TOKEN_45 = registerSkillToken("skill_token_45", 45);

	/** Creative-only Spellcasting boosts, the twin of Specialities' books. */
	public static final Item SPELLCASTING_TOME_25 = registerTome(25);
	public static final Item SPELLCASTING_TOME_100 = registerTome(100);

	public static final Item WOODEN_GREATSWORD = greatsword("wooden", ToolMaterial.WOOD);
	public static final Item STONE_GREATSWORD = greatsword("stone", ToolMaterial.STONE);
	public static final Item COPPER_GREATSWORD = greatsword("copper", ToolMaterial.COPPER);
	public static final Item IRON_GREATSWORD = greatsword("iron", ToolMaterial.IRON);
	public static final Item GOLDEN_GREATSWORD = greatsword("golden", ToolMaterial.GOLD);
	public static final Item DIAMOND_GREATSWORD = greatsword("diamond", ToolMaterial.DIAMOND);
	public static final Item NETHERITE_GREATSWORD = greatsword("netherite", ToolMaterial.NETHERITE);

	public static final Item WOODEN_DAGGER = dagger("wooden", ToolMaterial.WOOD);
	public static final Item STONE_DAGGER = dagger("stone", ToolMaterial.STONE);
	public static final Item COPPER_DAGGER = dagger("copper", ToolMaterial.COPPER);
	public static final Item IRON_DAGGER = dagger("iron", ToolMaterial.IRON);
	public static final Item GOLDEN_DAGGER = dagger("golden", ToolMaterial.GOLD);
	public static final Item DIAMOND_DAGGER = dagger("diamond", ToolMaterial.DIAMOND);
	public static final Item NETHERITE_DAGGER = dagger("netherite", ToolMaterial.NETHERITE);

	/** The Seeker's casting foci. No melee stats: a wand casts, it does not
	 * club — whacking with one is exactly as effective as an empty fist.
	 * Every spell requires SOME wand in the main hand; the specialist wands
	 * discount and empower their school (see SeekerSpells). */
	public static final Item MAGIC_WAND = registerWand("magic_wand");
	public static final Item APPRENTICE_WAND = registerWand("apprentice_wand");
	public static final Item BLAZE_WAND = registerWand("blaze_wand");
	public static final Item BREEZE_WAND = registerWand("breeze_wand");
	public static final Item HOLY_WAND = registerWand("holy_wand");

	private ModItems() {
	}

	/** Base damage that makes this material's greatsword exactly 1.5x its sword. */
	private static float baseDamageFor(final ToolMaterial material) {
		float bonus = material.attackDamageBonus();
		return DAMAGE_MULTIPLIER * (1.0F + SWORD_BASE_DAMAGE + bonus) - 1.0F - bonus;
	}

	private static Item registerSkillToken(final String path, final int levels) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Archetypes.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new SkillTokenItem(new Item.Properties().setId(key), levels));
	}

	private static Item registerTome(final int levels) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
				Archetypes.id("spellcasting_tome_" + levels));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new com.archetypes.items.SpellcastingTomeItem(new Item.Properties().setId(key), levels));
	}

	private static Item greatsword(final String prefix, final ToolMaterial material) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Archetypes.id(prefix + "_greatsword"));
		Item.Properties properties = material.applySwordProperties(
				new Item.Properties().setId(key), baseDamageFor(material), GREATSWORD_ATTACK_SPEED)
				// Three times the ingots, three times the life in it.
				.durability(material.durability() * 3);
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties));
	}

	/** Base damage that makes this material's dagger exactly 0.6x its sword. */
	private static float daggerDamageFor(final ToolMaterial material) {
		float bonus = material.attackDamageBonus();
		return DAGGER_MULTIPLIER * (1.0F + SWORD_BASE_DAMAGE + bonus) - 1.0F - bonus;
	}

	private static Item dagger(final String prefix, final ToolMaterial material) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Archetypes.id(prefix + "_dagger"));
		Item.Properties properties = material.applySwordProperties(
				new Item.Properties().setId(key), daggerDamageFor(material), DAGGER_ATTACK_SPEED);
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties));
	}

	private static Item registerWand(final String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Archetypes.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new com.archetypes.items.WandItem(new Item.Properties().setId(key).stacksTo(1),
						path.equals("magic_wand") ? null : "item.archetypes." + path + ".tooltip"));
	}

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> {
					output.accept(SKILL_TOKEN);
					output.accept(SPELLCASTING_TOME_25);
					output.accept(SPELLCASTING_TOME_100);
				});

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(WOODEN_GREATSWORD);
			output.accept(STONE_GREATSWORD);
			output.accept(COPPER_GREATSWORD);
			output.accept(IRON_GREATSWORD);
			output.accept(GOLDEN_GREATSWORD);
			output.accept(DIAMOND_GREATSWORD);
			output.accept(NETHERITE_GREATSWORD);
			output.accept(WOODEN_DAGGER);
			output.accept(STONE_DAGGER);
			output.accept(COPPER_DAGGER);
			output.accept(IRON_DAGGER);
			output.accept(GOLDEN_DAGGER);
			output.accept(DIAMOND_DAGGER);
			output.accept(NETHERITE_DAGGER);
			output.accept(MAGIC_WAND);
			output.accept(APPRENTICE_WAND);
			output.accept(BLAZE_WAND);
			output.accept(BREEZE_WAND);
			output.accept(HOLY_WAND);
		});

		// The mod's own creative tab: weapons, wands, potions, testing items.
		net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab> tabKey =
				net.minecraft.resources.ResourceKey.create(Registries.CREATIVE_MODE_TAB,
						Archetypes.id("archetypes"));
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tabKey,
				net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab.builder()
						.title(net.minecraft.network.chat.Component.translatable("itemGroup.archetypes.archetypes"))
						.icon(() -> new net.minecraft.world.item.ItemStack(MAGIC_WAND))
						.displayItems((parameters, output) -> {
							output.accept(WOODEN_GREATSWORD);
							output.accept(STONE_GREATSWORD);
							output.accept(COPPER_GREATSWORD);
							output.accept(IRON_GREATSWORD);
							output.accept(GOLDEN_GREATSWORD);
							output.accept(DIAMOND_GREATSWORD);
							output.accept(NETHERITE_GREATSWORD);
							output.accept(WOODEN_DAGGER);
							output.accept(STONE_DAGGER);
							output.accept(COPPER_DAGGER);
							output.accept(IRON_DAGGER);
							output.accept(GOLDEN_DAGGER);
							output.accept(DIAMOND_DAGGER);
							output.accept(NETHERITE_DAGGER);
							output.accept(MAGIC_WAND);
							output.accept(APPRENTICE_WAND);
							output.accept(BLAZE_WAND);
							output.accept(BREEZE_WAND);
							output.accept(HOLY_WAND);
							output.accept(net.minecraft.world.item.alchemy.PotionContents.createItemStack(
									net.minecraft.world.item.Items.POTION, ManaPotions.MANA_RESTORE));
							output.accept(net.minecraft.world.item.alchemy.PotionContents.createItemStack(
									net.minecraft.world.item.Items.POTION, ManaPotions.STRONG_MANA_RESTORE));
							output.accept(net.minecraft.world.item.alchemy.PotionContents.createItemStack(
									net.minecraft.world.item.Items.POTION, ManaPotions.MANA_REGENERATION));
							output.accept(net.minecraft.world.item.alchemy.PotionContents.createItemStack(
									net.minecraft.world.item.Items.POTION, ManaPotions.STRONG_MANA_REGENERATION));
							output.accept(net.minecraft.world.item.alchemy.PotionContents.createItemStack(
									net.minecraft.world.item.Items.SPLASH_POTION, ManaPotions.MANA_RESTORE));
							output.accept(net.minecraft.world.item.alchemy.PotionContents.createItemStack(
									net.minecraft.world.item.Items.SPLASH_POTION, ManaPotions.STRONG_MANA_RESTORE));
							output.accept(net.minecraft.world.item.alchemy.PotionContents.createItemStack(
									net.minecraft.world.item.Items.SPLASH_POTION, ManaPotions.MANA_REGENERATION));
							output.accept(net.minecraft.world.item.alchemy.PotionContents.createItemStack(
									net.minecraft.world.item.Items.SPLASH_POTION, ManaPotions.STRONG_MANA_REGENERATION));
							output.accept(SKILL_TOKEN);
							output.accept(SKILL_TOKEN_45);
							output.accept(SPELLCASTING_TOME_25);
							output.accept(SPELLCASTING_TOME_100);
						})
						.build());
	}
}
