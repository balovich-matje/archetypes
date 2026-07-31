package com.archetypes;

import com.archetypes.items.SkillTokenItem;

// 26.1 renamed the module and every type in it: `fabric-item-group-api-v1` /
// `itemgroup.v1` became `fabric-creative-tab-api-v1` / `creativetab.v1`, and with it
// `ItemGroupEvents.modifyEntriesEvent` -> `CreativeModeTabEvents.modifyOutputEvent` and
// `FabricItemGroup.builder()` -> `FabricCreativeModeTab.builder()`. The node script swaps
// the MODULE at the same boundary. Only the two head lines and the builder call fork; every
// `output.accept(...)` below is shared, because both callback types implement vanilla's
// `CreativeModeTab.Output` and inherit the same `accept(ItemLike)` (measured on both jars).
// STAGE 6: both loaders hand their creative-tab callback a `BuildCreativeModeTabContentsEvent`,
// which `implements CreativeModeTab.Output` on each — so the accepts stay outside the
// conditional there too and only the two registration lines fork.
//? if fabric && >=26.1 {
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
//?} elif fabric {
/*import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
*///?} elif neoforge {
/*import com.archetypes.platform.NeoForgeEvents;
*///?} elif forge {
/*import com.archetypes.platform.ForgeEvents;
*///?}
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
// 1.21.11 replaced the `Tier` INTERFACE + `Tiers` enum with the `ToolMaterial` RECORD, and
// moved everything a tiered item needs — durability, repairability, enchantment value, the
// TOOL component, the attack attributes — from the `TieredItem` class hierarchy onto data
// components applied by `ToolMaterial.applySwordProperties`. Both classes are ABSENT below
// the boundary (measured on the mojmap common jars of 1.21.1 and 1.20.1, which agree), so
// this is a genuine two-shape fork rather than a rename, and it reaches five methods below.
//? if >=1.21.11 {
import net.minecraft.world.item.ToolMaterial;
//?} else {
/*import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
*///?}

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

	/** The Oracle Wizard's conjured weapons. Never craftable, never in a tab —
	 * they exist only in a Magic Armaments channel and vanish with it. */
	public static boolean isMagicSword(final net.minecraft.world.item.ItemStack stack) {
		return stack.is(MAGIC_SWORD);
	}

	public static boolean isMagicBow(final net.minecraft.world.item.ItemStack stack) {
		return stack.is(MAGIC_BOW);
	}

	public static boolean isSummoned(final net.minecraft.world.item.ItemStack stack) {
		return stack.is(MAGIC_SWORD) || stack.is(MAGIC_BOW);
	}

	/** Anything martial enough to disqualify a Seeker from regenerating
	 * mana: real weapons and shields, in either hand. */
	public static boolean isCombatWeapon(final net.minecraft.world.item.ItemStack stack) {
		return stack.is(ItemTags.SWORDS) // greatswords and daggers live in this tag too
				// Spears arrived with 1.21.11 and the tag does not exist below it — the
				// clause is dropped rather than substituted, because there is nothing on
				// the older versions it could be describing.
				//? if >=1.21.11 {
				|| stack.is(ItemTags.SPEARS)
				//?}
				//? if >=1.21 {
				|| stack.is(net.minecraft.world.item.Items.MACE)
				//?}
				|| stack.is(net.minecraft.world.item.Items.BOW)
				|| stack.is(net.minecraft.world.item.Items.CROSSBOW)
				|| stack.is(net.minecraft.world.item.Items.SHIELD)
				|| stack.is(net.minecraft.world.item.Items.TRIDENT)
				// A conjured weapon is martial too: mana must not regenerate under
				// the channel or its 10/second upkeep would run partly for free.
				|| isSummoned(stack);
	}

	public static boolean holdingCombatWeapon(final net.minecraft.world.entity.player.Player player) {
		return isCombatWeapon(player.getMainHandItem()) || isCombatWeapon(player.getOffhandItem());
	}

	/** A one-handed sword: the vanilla tag minus our own blades in it. */
	public static boolean isSword(final net.minecraft.world.item.ItemStack stack) {
		return stack.is(ItemTags.SWORDS) && !stack.is(GREATSWORDS) && !stack.is(DAGGERS);
	}

	/**
	 * Creative-only: skill points per use. See {@link com.archetypes.items.SkillTokenItem}.
	 *
	 * <p>The greater token grants 60, which is {@link SkillPoints#MAX_LEVEL} — the
	 * EPIC cap, not the base one. It granted 45 (the base cap,
	 * {@link SkillPoints#BASE_LEVEL_CAP}) until the epic trees became the thing
	 * most worth testing, and a token that stopped one tier short meant a second
	 * command every time.
	 */
	public static final Item SKILL_TOKEN = registerSkillToken("skill_token", 1);
	public static final Item SKILL_TOKEN_60 = registerSkillToken("skill_token_60", 60);

	/** Creative-only Spellcasting boosts, the twin of Specialities' books. */
	public static final Item SPELLCASTING_TOME_25 = registerTome(25);
	public static final Item SPELLCASTING_TOME_100 = registerTome(100);

	// COPPER HAS NO `Tiers` CONSTANT BELOW 1.21.11 — copper tools are part of the same
	// release that introduced `ToolMaterial` — so the copper greatsword and dagger, which
	// this mod ships on every node, need their tier spelled out. Every number here is READ
	// OFF 26.x's own `ToolMaterial.COPPER` (`javap -c` on the static initialiser: durability
	// 190, speed 5.0, attack bonus 1.0, enchantment value 13, repair
	// `#minecraft:copper_tool_materials`), so the legacy item is the same item, not an
	// approximation.
	//
	// Two of the six fields are provably inert for a SWORD and are filled with the nearest
	// valid value rather than being invented: `getSpeed` and `getIncorrectBlocksForDrops`
	// feed `Tier.createToolProperties`, and neither 1.21.1's `SwordItem.createToolProperties`
	// nor 26.x's `ToolMaterial.applySwordProperties` calls it — both build the sword's TOOL
	// component from COBWEB and `#minecraft:sword_efficient` alone (measured in both jars).
	//
	// STAGE 5 SPLITS THE ARM AGAIN: 1.21 replaced `Tier.getLevel()` — a mining LEVEL, an int
	// — with `getIncorrectBlocksForDrops()`, a block tag. Copper's level is stone's, 1, and
	// that is vanilla's own answer for it: 1.21's `INCORRECT_FOR_STONE_TOOL` is the tag
	// that replaced level 1.
	//? if <1.21.11 && >=1.21 {
	/*private static final Tier COPPER_TIER = new Tier() {
		@Override
		public int getUses() {
			return 190;
		}

		@Override
		public float getSpeed() {
			return 5.0F;
		}

		@Override
		public float getAttackDamageBonus() {
			return 1.0F;
		}

		@Override
		public net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> getIncorrectBlocksForDrops() {
			return net.minecraft.tags.BlockTags.INCORRECT_FOR_STONE_TOOL;
		}

		@Override
		public int getEnchantmentValue() {
			return 13;
		}

		@Override
		public net.minecraft.world.item.crafting.Ingredient getRepairIngredient() {
			return net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.COPPER_INGOT);
		}
	};
	*///?} elif <1.21 {
	/*private static final Tier COPPER_TIER = new Tier() {
		@Override
		public int getUses() {
			return 190;
		}

		@Override
		public float getSpeed() {
			return 5.0F;
		}

		@Override
		public float getAttackDamageBonus() {
			return 1.0F;
		}

		@Override
		public int getLevel() {
			return 1;
		}

		@Override
		public int getEnchantmentValue() {
			return 13;
		}

		@Override
		public net.minecraft.world.item.crafting.Ingredient getRepairIngredient() {
			return net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.COPPER_INGOT);
		}
	};
	*///?}

	//? if >=1.21.11 {
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
	//?} else {
	/*public static final Item WOODEN_GREATSWORD = greatsword("wooden", Tiers.WOOD);
	public static final Item STONE_GREATSWORD = greatsword("stone", Tiers.STONE);
	public static final Item COPPER_GREATSWORD = greatsword("copper", COPPER_TIER);
	public static final Item IRON_GREATSWORD = greatsword("iron", Tiers.IRON);
	public static final Item GOLDEN_GREATSWORD = greatsword("golden", Tiers.GOLD);
	public static final Item DIAMOND_GREATSWORD = greatsword("diamond", Tiers.DIAMOND);
	public static final Item NETHERITE_GREATSWORD = greatsword("netherite", Tiers.NETHERITE);

	public static final Item WOODEN_DAGGER = dagger("wooden", Tiers.WOOD);
	public static final Item STONE_DAGGER = dagger("stone", Tiers.STONE);
	public static final Item COPPER_DAGGER = dagger("copper", COPPER_TIER);
	public static final Item IRON_DAGGER = dagger("iron", Tiers.IRON);
	public static final Item GOLDEN_DAGGER = dagger("golden", Tiers.GOLD);
	public static final Item DIAMOND_DAGGER = dagger("diamond", Tiers.DIAMOND);
	public static final Item NETHERITE_DAGGER = dagger("netherite", Tiers.NETHERITE);
	*///?}

	/** The Seeker's casting foci. No melee stats: a wand casts, it does not
	 * club — whacking with one is exactly as effective as an empty fist.
	 * Every spell requires SOME wand in the main hand; the specialist wands
	 * discount and empower their school (see SeekerSpells). */
	/** The Arcane Mote: the missile projectile's look — a violet four-point
	 * star, its 8-point empowered sister (sources in notes/art/missile_fx).
	 * Never obtainable; SpellProjectile wears them in flight. */
	public static final Item MAGIC_BOLT = plain("magic_bolt");
	public static final Item MAGIC_BOLT_EMPOWERED = plain("magic_bolt_empowered");

	/** The conjured armaments: a diamond-tier unbreakable sword and a bow, held
	 * only while a Magic Armaments channel runs. Not craftable, not in any tab —
	 * MagicArmaments spawns and reclaims them; server guards keep them from ever
	 * dropping, being stored, or surviving death. */
	public static final Item MAGIC_SWORD = registerMagicSword();
	public static final Item MAGIC_BOW = registerMagicBow();

	public static final Item MAGIC_WAND = registerWand("magic_wand");
	public static final Item APPRENTICE_WAND = registerWand("apprentice_wand");
	public static final Item BLAZE_WAND = registerWand("blaze_wand");
	public static final Item BREEZE_WAND = registerWand("breeze_wand");
	public static final Item HOLY_WAND = registerWand("holy_wand");
	/** The one wand that owes no school: x1.5 and a tenth off EVERY spell.
	 * See {@link Tuning#ORACLE_WAND_POWER} and SeekerSpells.wandPower. */
	public static final Item ORACLE_WAND = registerWand("oracle_wand");

	private ModItems() {
	}

	/** Base damage that makes this material's greatsword exactly 1.5x its sword. */
	// Only the SIGNATURE and the one accessor call fork. The formula line below is outside
	// the block on purpose (conventions §5b): two copies of a balance expression are two
	// things that can drift, and this one decides every greatsword's damage.
	//? if >=1.21.11 {
	private static float baseDamageFor(final ToolMaterial material) {
		float bonus = material.attackDamageBonus();
	//?} else {
	/*private static float baseDamageFor(final Tier material) {
		float bonus = material.getAttackDamageBonus();
	*///?}
		return DAMAGE_MULTIPLIER * (1.0F + SWORD_BASE_DAMAGE + bonus) - 1.0F - bonus;
	}

	private static Item registerSkillToken(final String path, final int levels) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Archetypes.id(path));
		//? if >=1.21.11 {
		return Registry.register(BuiltInRegistries.ITEM, key,
				new SkillTokenItem(new Item.Properties().setId(key), levels));
		//?} else {
		/*return Registry.register(BuiltInRegistries.ITEM, key,
				new SkillTokenItem(new Item.Properties(), levels));
		*///?}
	}

	private static Item registerTome(final int levels) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
				Archetypes.id("spellcasting_tome_" + levels));
		//? if >=1.21.11 {
		return Registry.register(BuiltInRegistries.ITEM, key,
				new com.archetypes.items.SpellcastingTomeItem(new Item.Properties().setId(key), levels));
		//?} else {
		/*return Registry.register(BuiltInRegistries.ITEM, key,
				new com.archetypes.items.SpellcastingTomeItem(new Item.Properties(), levels));
		*///?}
	}

	// WHY THE LEGACY ARM BUILDS A `SwordItem` AND NOT A PLAIN `Item`, measured rather than
	// stylistic. 1.21.11 moved everything a sword is into data components, so 26.x can hang
	// them on a bare `Item`. Below the boundary three of them still live in the CLASS:
	//   * repairability — `TieredItem.isValidRepairItem`; there is no repair component yet
	//   * enchantment value — `TieredItem.getEnchantmentValue`; no component either
	//   * SWEEPING — `Player.attack` gates the sweep on
	//     `getItemInHand(MAIN_HAND).getItem() instanceof SwordItem` (read out of the 1.21.1
	//     `Player.attack` bytecode, offset 415). A plain Item would silently lose the sweep
	//     that the same weapon has on 26.x. That is exactly the class of divergence the
	//     port's gates exist to catch, and it is invisible to every build.
	// The attributes are NOT taken from `SwordItem.createAttributes`, which quantises the
	// base damage to an `int` — these are derived floats (`5 + bonus/2` and friends), so
	// they are built here with the same two modifiers, ids and operations vanilla uses.
	//? if >=1.21.11 {
	private static Item greatsword(final String prefix, final ToolMaterial material) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Archetypes.id(prefix + "_greatsword"));
		Item.Properties properties = material.applySwordProperties(
				new Item.Properties().setId(key), baseDamageFor(material), GREATSWORD_ATTACK_SPEED)
				// Three times the ingots, three times the life in it.
				.durability(material.durability() * 3);
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties));
	}
	//?} elif >=1.21 {
	/*private static Item greatsword(final String prefix, final Tier material) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Archetypes.id(prefix + "_greatsword"));
		// Three times the ingots, three times the life in it — carried on the TIER rather
		// than on the properties, because `TieredItem`'s constructor applies
		// `properties.durability(tier.getUses())` AFTER whatever the caller set.
		Item.Properties properties = new Item.Properties()
				.attributes(swordAttributes(material, baseDamageFor(material), GREATSWORD_ATTACK_SPEED));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new net.minecraft.world.item.SwordItem(scaledDurability(material, 3), properties));
	}

	// A tier that is `base` in every respect but durability. Line comments, not javadoc:
	// a `*` followed by `/` inside a disabled `//?` branch closes Stonecutter's own block
	// comment early (the trap Stage 2 hit on LevelExtractorMixin).
	private static Tier scaledDurability(final Tier base, final int factor) {
		return new Tier() {
			@Override
			public int getUses() {
				return base.getUses() * factor;
			}

			@Override
			public float getSpeed() {
				return base.getSpeed();
			}

			@Override
			public float getAttackDamageBonus() {
				return base.getAttackDamageBonus();
			}

			@Override
			public net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> getIncorrectBlocksForDrops() {
				return base.getIncorrectBlocksForDrops();
			}

			@Override
			public int getEnchantmentValue() {
				return base.getEnchantmentValue();
			}

			@Override
			public net.minecraft.world.item.crafting.Ingredient getRepairIngredient() {
				return base.getRepairIngredient();
			}
		};
	}

	// The two modifiers `ToolMaterial.createSwordAttributes` adds on 26.x, rebuilt here.
	// Same attributes, same modifier ids, same ADD_VALUE operation, same MAINHAND group —
	// read off both versions' bytecode, which differ only in the mapping namespace of the
	// id type. Vanilla's own `SwordItem.createAttributes` is deliberately NOT used: its
	// base-damage parameter is an `int`.
	private static net.minecraft.world.item.component.ItemAttributeModifiers swordAttributes(
			final Tier material, final float damage, final float speed) {
		return net.minecraft.world.item.component.ItemAttributeModifiers.builder()
				.add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
						new net.minecraft.world.entity.ai.attributes.AttributeModifier(
								Item.BASE_ATTACK_DAMAGE_ID, damage + material.getAttackDamageBonus(),
								net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
						net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
				.add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
						new net.minecraft.world.entity.ai.attributes.AttributeModifier(
								Item.BASE_ATTACK_SPEED_ID, speed,
								net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
						net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
				.build();
	}
	*///?} else {
	/*// STAGE 5. Below 1.21 there is no attribute COMPONENT and no `Item.Properties
	// .attributes(...)` to hang one on, so the pair goes onto the item itself — see
	// `items/LegacySword`, which is this arm's only addition and exists for exactly the
	// reason the note above gives: vanilla's own `SwordItem` takes an INT damage and these
	// numbers are derived floats.
	private static Item greatsword(final String prefix, final Tier material) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Archetypes.id(prefix + "_greatsword"));
		// Three times the ingots, three times the life in it — carried on the TIER for the
		// same reason as the arm above: `TieredItem` applies `tier.getUses()` last.
		return Registry.register(BuiltInRegistries.ITEM, key,
				new com.archetypes.items.LegacySword(scaledDurability(material, 3),
						baseDamageFor(material), GREATSWORD_ATTACK_SPEED, new Item.Properties()));
	}

	// The 1.20.1 `Tier`: `getLevel()` where 1.21 has `getIncorrectBlocksForDrops()`.
	private static Tier scaledDurability(final Tier base, final int factor) {
		return new Tier() {
			@Override
			public int getUses() {
				return base.getUses() * factor;
			}

			@Override
			public float getSpeed() {
				return base.getSpeed();
			}

			@Override
			public float getAttackDamageBonus() {
				return base.getAttackDamageBonus();
			}

			@Override
			public int getLevel() {
				return base.getLevel();
			}

			@Override
			public int getEnchantmentValue() {
				return base.getEnchantmentValue();
			}

			@Override
			public net.minecraft.world.item.crafting.Ingredient getRepairIngredient() {
				return base.getRepairIngredient();
			}
		};
	}
	*///?}

	/** A dagger's full swing damage (fist plus item), for Twin Fangs'
	 * off-hand ratio. Zero for anything that isn't one of our daggers. */
	// ⚠ THE ONLY PLACE IN THIS FILE WHERE A BALANCE EXPRESSION IS WRITTEN TWICE. Everywhere
	// else the fork is narrowed to the signature and the accessor so the formula stays
	// shared (conventions §5b); here the accessor sits INSIDE the returned expression, and
	// hoisting it into a local would change the 26.x bytecode — which the per-stage gate
	// forbids. Edit both arms together; the parity gate compares the two nodes' numbers.
	public static float daggerSwingDamage(final net.minecraft.world.item.ItemStack stack) {
		//? if >=1.21.11 {
		ToolMaterial material = stack.is(WOODEN_DAGGER) ? ToolMaterial.WOOD
				: stack.is(STONE_DAGGER) ? ToolMaterial.STONE
				: stack.is(COPPER_DAGGER) ? ToolMaterial.COPPER
				: stack.is(IRON_DAGGER) ? ToolMaterial.IRON
				: stack.is(GOLDEN_DAGGER) ? ToolMaterial.GOLD
				: stack.is(DIAMOND_DAGGER) ? ToolMaterial.DIAMOND
				: stack.is(NETHERITE_DAGGER) ? ToolMaterial.NETHERITE : null;
		return material == null ? 0.0F
				: DAGGER_MULTIPLIER * (1.0F + SWORD_BASE_DAMAGE + material.attackDamageBonus());
		//?} else {
		/*Tier material = stack.is(WOODEN_DAGGER) ? Tiers.WOOD
				: stack.is(STONE_DAGGER) ? Tiers.STONE
				: stack.is(COPPER_DAGGER) ? COPPER_TIER
				: stack.is(IRON_DAGGER) ? Tiers.IRON
				: stack.is(GOLDEN_DAGGER) ? Tiers.GOLD
				: stack.is(DIAMOND_DAGGER) ? Tiers.DIAMOND
				: stack.is(NETHERITE_DAGGER) ? Tiers.NETHERITE : null;
		return material == null ? 0.0F
				: DAGGER_MULTIPLIER * (1.0F + SWORD_BASE_DAMAGE + material.getAttackDamageBonus());
		*///?}
	}

	/** Base damage that makes this material's dagger exactly 0.6x its sword. */
	//? if >=1.21.11 {
	private static float daggerDamageFor(final ToolMaterial material) {
		float bonus = material.attackDamageBonus();
	//?} else {
	/*private static float daggerDamageFor(final Tier material) {
		float bonus = material.getAttackDamageBonus();
	*///?}
		return DAGGER_MULTIPLIER * (1.0F + SWORD_BASE_DAMAGE + bonus) - 1.0F - bonus;
	}

	//? if >=1.21.11 {
	private static Item dagger(final String prefix, final ToolMaterial material) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Archetypes.id(prefix + "_dagger"));
		Item.Properties properties = material.applySwordProperties(
				new Item.Properties().setId(key), daggerDamageFor(material), DAGGER_ATTACK_SPEED);
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties));
	}
	//?} elif >=1.21 {
	/*private static Item dagger(final String prefix, final Tier material) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Archetypes.id(prefix + "_dagger"));
		Item.Properties properties = new Item.Properties()
				.attributes(swordAttributes(material, daggerDamageFor(material), DAGGER_ATTACK_SPEED));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new net.minecraft.world.item.SwordItem(material, properties));
	}
	*///?} else {
	/*private static Item dagger(final String prefix, final Tier material) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Archetypes.id(prefix + "_dagger"));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new com.archetypes.items.LegacySword(material, daggerDamageFor(material),
						DAGGER_ATTACK_SPEED, new Item.Properties()));
	}
	*///?}

	private static Item plain(final String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Archetypes.id(path));
		//? if >=1.21.11 {
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
		//?} else {
		/*return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties()));
		*///?}
	}

	/**
	 * A conjured weapon shows its name and nothing else. The mechanics stay —
	 * unbreakable, Sharpness, the attribute block, the glider a Levitation
	 * channel stamps on — but every line they would print is hidden, because a
	 * spell-shaped weapon that lists its stats reads like loot. Note this is
	 * per-component hiding, NOT the hide_tooltip flag: that one suppresses the
	 * name too (ItemStack.getTooltipLines returns an empty list for it).
	 */
	// Below 1.21.11 there IS no `TooltipDisplay` component: hiding was a per-component
	// `showInTooltip` flag on each of the components that print, and 1.21.11 lifted all of
	// them into one. The legacy arms below therefore set the flags one at a time; the
	// intent — name only — carries across, and the two residues are stated rather than
	// hidden:
	//   * the DAMAGE line has no flag on 1.21.1, and needs none: both conjured weapons are
	//     UNBREAKABLE, so vanilla never prints a durability line for them;
	//   * the ENCHANTMENTS flag cannot be set here, because MagicArmaments REPLACES that
	//     component every time it re-enchants. It is set at that write instead — see the
	//     `withTooltip(false)` fork in `MagicArmaments.enchant`.
	//? if >=1.21.11 {
	private static net.minecraft.world.item.component.TooltipDisplay silentTooltip() {
		return net.minecraft.world.item.component.TooltipDisplay.DEFAULT
				.withHidden(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, true)
				.withHidden(net.minecraft.core.component.DataComponents.UNBREAKABLE, true)
				.withHidden(net.minecraft.core.component.DataComponents.ENCHANTMENTS, true)
				.withHidden(net.minecraft.core.component.DataComponents.DAMAGE, true);
	}
	//?}

	/** The conjured sword: exactly a diamond sword's melee (3 base + diamond's
	 * bonus, -2.4 speed), but unbreakable and single-stack. Its real damage is
	 * the Sharpness {@link MagicArmaments} stamps on the stack at conjure time.
	 * The glint override is FALSE, not absent: the stack carries enchantments,
	 * so only an explicit false keeps vanilla's glint off the animated sprite. */
	private static Item registerMagicSword() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Archetypes.id("magic_sword"));
		//? if >=1.21.11 {
		Item.Properties properties = ToolMaterial.DIAMOND.applySwordProperties(
				new Item.Properties().setId(key), SWORD_BASE_DAMAGE, -2.4F)
				.stacksTo(1)
				.rarity(net.minecraft.world.item.Rarity.EPIC)
				.component(net.minecraft.core.component.DataComponents.UNBREAKABLE,
						net.minecraft.util.Unit.INSTANCE)
				.component(net.minecraft.core.component.DataComponents.TOOLTIP_DISPLAY, silentTooltip())
				.component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
		//?} elif >=1.21 {
		/*Item.Properties properties = new Item.Properties()
				.attributes(swordAttributes(Tiers.DIAMOND, SWORD_BASE_DAMAGE, -2.4F).withTooltip(false))
				.stacksTo(1)
				.rarity(net.minecraft.world.item.Rarity.EPIC)
				.component(net.minecraft.core.component.DataComponents.UNBREAKABLE,
						new net.minecraft.world.item.component.Unbreakable(false))
				.component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
		*///?} else {
		/*// No components at all below 1.21, so the three the arms above set move to where
		// each of them lived then: the attribute block onto the item (items/LegacySword),
		// unbreakability onto the ABSENCE of a durability (an item with max damage 0 is
		// what `isDamageableItem` calls unbreakable — and the conjured weapon has no
		// durability bar on any node), and the glint override has no legacy equivalent at
		// all. The stack is enchanted with Sharpness on conjure, so it glints regardless;
		// what is lost is the ability to turn that glint OFF, which the modern arm does
		// not do either (it passes `false` for the sword only, matching vanilla's default
		// for an unenchanted item).
		Item.Properties properties = new Item.Properties()
				.stacksTo(1)
				.rarity(net.minecraft.world.item.Rarity.EPIC);
		*///?}
		return Registry.register(BuiltInRegistries.ITEM, key,
				new com.archetypes.items.MagicSwordItem(properties));
	}

	private static Item registerMagicBow() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Archetypes.id("magic_bow"));
		//? if >=1.21.11 {
		Item.Properties properties = new Item.Properties().setId(key)
				.stacksTo(1)
				.rarity(net.minecraft.world.item.Rarity.EPIC)
				.component(net.minecraft.core.component.DataComponents.UNBREAKABLE,
						net.minecraft.util.Unit.INSTANCE)
				.component(net.minecraft.core.component.DataComponents.TOOLTIP_DISPLAY, silentTooltip())
				.component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		//?} elif >=1.21 {
		/*Item.Properties properties = new Item.Properties()
				.stacksTo(1)
				.rarity(net.minecraft.world.item.Rarity.EPIC)
				.component(net.minecraft.core.component.DataComponents.UNBREAKABLE,
						new net.minecraft.world.item.component.Unbreakable(false))
				.component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		*///?} else {
		/*// See the sword above: no components below 1.21. The bow's forced glint is the one
		// thing that genuinely goes — a conjured bow carries no enchantment of its own, so
		// it simply does not shimmer on this node.
		Item.Properties properties = new Item.Properties()
				.stacksTo(1)
				.rarity(net.minecraft.world.item.Rarity.EPIC);
		*///?}
		return Registry.register(BuiltInRegistries.ITEM, key,
				new com.archetypes.items.MagicBowItem(properties));
	}

	private static Item registerWand(final String path) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Archetypes.id(path));
		//? if >=1.21.11 {
		return Registry.register(BuiltInRegistries.ITEM, key,
				new com.archetypes.items.WandItem(new Item.Properties().setId(key).stacksTo(1),
		//?} else {
		/*return Registry.register(BuiltInRegistries.ITEM, key,
				new com.archetypes.items.WandItem(new Item.Properties().stacksTo(1),
		*///?}
						path.equals("magic_wand") ? null : "item.archetypes." + path + ".tooltip"));
	}

	public static void initialize() {
		// Registration only — the three accepts are shared. A loader helper takes
		// (ResourceKey<CreativeModeTab>, Consumer<CreativeModeTab.Output>) and must fire once for
		// that tab, on the MOD event bus.
		//? if fabric && >=26.1 {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> {
		//?} elif fabric {
		/*ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> {
		*///?} elif neoforge {
		/*NeoForgeEvents.creativeTabOutput(CreativeModeTabs.TOOLS_AND_UTILITIES, output -> {
		*///?} elif forge {
		/*ForgeEvents.creativeTabOutput(CreativeModeTabs.TOOLS_AND_UTILITIES, output -> {
		*///?}
					output.accept(SKILL_TOKEN);
					output.accept(SPELLCASTING_TOME_25);
					output.accept(SPELLCASTING_TOME_100);
				});

		// Registration only; the twenty-two accepts below are shared.
		//? if fabric && >=26.1 {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
		//?} elif fabric {
		/*ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(output -> {
		*///?} elif neoforge {
		/*NeoForgeEvents.creativeTabOutput(CreativeModeTabs.COMBAT, output -> {
		*///?} elif forge {
		/*ForgeEvents.creativeTabOutput(CreativeModeTabs.COMBAT, output -> {
		*///?}
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
			output.accept(ORACLE_WAND);
		});

		// The mod's own creative tab: weapons, wands, potions, testing items.
		net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab> tabKey =
				net.minecraft.resources.ResourceKey.create(Registries.CREATIVE_MODE_TAB,
						Archetypes.id("archetypes"));
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tabKey,
				// The BUILDER call only; every line of the chain below it — title, icon and the
				// whole `displayItems` body — is shared, which is what matters: that body is the
				// tab's CONTENTS.
				//
				// Both fabric-api helpers exist to hand back a `CreativeModeTab.Builder` without
				// vanilla's `(Row, int column)` arguments, which are meaningless for a tab that
				// is not one of vanilla's own. Both loaders are believed to patch a no-arg
				// `CreativeModeTab.builder()` in for the same reason — BELIEVED, not measured,
				// so it goes through a helper rather than being written here as fact. If the
				// no-arg overload is there, each helper is a one-line `return
				// CreativeModeTab.builder();`.
				//? if fabric && >=26.1 {
				net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab.builder()
				//?} elif fabric {
				/*net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup.builder()
				*///?} elif neoforge {
				/*NeoForgeEvents.creativeTabBuilder()
				*///?} elif forge {
				/*ForgeEvents.creativeTabBuilder()
				*///?}
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
							output.accept(ORACLE_WAND);
							//? if >=1.21 {
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
							output.accept(net.minecraft.world.item.alchemy.PotionContents.createItemStack(
									net.minecraft.world.item.Items.POTION, AmnesiaPotions.AMNESIA));
							output.accept(net.minecraft.world.item.alchemy.PotionContents.createItemStack(
									net.minecraft.world.item.Items.POTION, AmnesiaPotions.STRONG_AMNESIA));
							//?} else {
							/*// STAGE 5: `PotionContents` is the 1.21 component and its `createItemStack`
							// with it. Below that a potion stack is a plain stack with the potion
							// written into its NBT, which is exactly what `PotionUtils.setPotion`
							// does — vanilla's own creative tab builds its potions that way there.
							output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(
									new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION), ManaPotions.MANA_RESTORE.value()));
							output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(
									new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION), ManaPotions.STRONG_MANA_RESTORE.value()));
							output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(
									new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION), ManaPotions.MANA_REGENERATION.value()));
							output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(
									new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION), ManaPotions.STRONG_MANA_REGENERATION.value()));
							output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(
									new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SPLASH_POTION), ManaPotions.MANA_RESTORE.value()));
							output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(
									new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SPLASH_POTION), ManaPotions.STRONG_MANA_RESTORE.value()));
							output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(
									new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SPLASH_POTION), ManaPotions.MANA_REGENERATION.value()));
							output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(
									new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SPLASH_POTION), ManaPotions.STRONG_MANA_REGENERATION.value()));
							output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(
									new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION), AmnesiaPotions.AMNESIA.value()));
							output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(
									new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION), AmnesiaPotions.STRONG_AMNESIA.value()));
							*///?}
							output.accept(SKILL_TOKEN);
							output.accept(SKILL_TOKEN_60);
							output.accept(SPELLCASTING_TOME_25);
							output.accept(SPELLCASTING_TOME_100);
						})
						.build());
	}
}
