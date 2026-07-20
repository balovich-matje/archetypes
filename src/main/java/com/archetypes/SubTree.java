package com.archetypes;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The three constellation sub-trees of each archetype. Each one's node graph
 * will eventually trace the outline of its symbol (Skyrim-style); the icon here
 * is the vanilla item standing in for that symbol.
 */
public enum SubTree {
	PROTECTOR(Archetype.STRENGTH, "protector", () -> Items.SHIELD, Constellations.PROTECTOR_SHIELD, false),
	SLAYER(Archetype.STRENGTH, "slayer", () -> Items.IRON_SWORD, Constellations.SLAYER_SWORD, false),
	CRUSHER(Archetype.STRENGTH, "crusher", () -> Items.MACE, Constellations.CRUSHER_MACE, false),

	MARKSMAN(Archetype.AGILITY, "marksman", () -> Items.BOW, Constellations.MARKSMAN_BOW, false),
	ASSASSIN(Archetype.AGILITY, "assassin", () -> ModItems.IRON_DAGGER, Constellations.ASSASSIN_DAGGER, false),
	SHADOW(Archetype.AGILITY, "shadow", () -> Items.PHANTOM_MEMBRANE, Constellations.SHADOW_MOON, false),

	// Was Fire Mage; the rename widened it to every element, so the fire
	// charge gave way to a neutral shard of raw magic.
	ELEMENTALIST(Archetype.INTELLECT, "elementalist", () -> Items.AMETHYST_SHARD, Constellations.ELEMENTALIST_FLAME,
			false),
	WIZARD(Archetype.INTELLECT, "wizard", () -> ModItems.MAGIC_WAND, Constellations.WIZARD_STAFF, false),
	// Was Healer, then briefly Apothecary; the holy-light kit is a priest's.
	PRIEST(Archetype.INTELLECT, "priest", () -> Items.TOTEM_OF_UNDYING, Constellations.PRIEST_ANKH, false),

	// Epic sub-trees: upgraded siblings of the base trees, spent from the epic
	// pool (levels 46-60). They live in the enum so ids persist and byId
	// resolves them, but SubTree.of never lists them — ability-slot dispatch,
	// the picker and the legends stay on the three base trees.
	ORACLE_ELEMENTALIST(Archetype.INTELLECT, "oracle_elementalist", () -> Items.END_ROD,
			Constellations.ORACLE_ELEMENTALIST, true),
	ORACLE_WIZARD(Archetype.INTELLECT, "oracle_wizard", () -> Items.ENCHANTED_GOLDEN_APPLE,
			Constellations.ORACLE_WIZARD, true),
	ORACLE_PRIEST(Archetype.INTELLECT, "oracle_priest", () -> Items.BEACON,
			Constellations.ORACLE_PRIEST, true),
	NEMESIS_MARKSMAN(Archetype.AGILITY, "nemesis_marksman", () -> Items.SPYGLASS,
			Constellations.NEMESIS_MARKSMAN, true),
	NEMESIS_ASSASSIN(Archetype.AGILITY, "nemesis_assassin", () -> Items.WITHER_ROSE,
			Constellations.NEMESIS_ASSASSIN, true),
	// The Cutpurse's first epic tree: the Shadow's dark taken all the way to a
	// night form. Agility's, so the epic switcher finally lights up outside
	// Intellect.
	NEMESIS_SHADOW(Archetype.AGILITY, "nemesis_shadow", () -> Items.WITHER_SKELETON_SKULL,
			Constellations.NEMESIS_SHADOW, true),

	COLOSSUS_PROTECTOR(Archetype.STRENGTH, "colossus_protector", () -> Items.DIAMOND_CHESTPLATE,
			Constellations.COLOSSUS_PROTECTOR, true),
	COLOSSUS_SLAYER(Archetype.STRENGTH, "colossus_slayer", () -> Items.NETHERITE_SWORD,
			Constellations.COLOSSUS_SLAYER, true),
	COLOSSUS_CRUSHER(Archetype.STRENGTH, "colossus_crusher", () -> Items.ANVIL,
			Constellations.COLOSSUS_CRUSHER, true);

	private final Archetype archetype;
	private final String id;
	private final Supplier<Item> icon;
	private final Constellation constellation;
	private final boolean epic;

	SubTree(final Archetype archetype, final String id, final Supplier<Item> icon,
			final Constellation constellation, final boolean epic) {
		this.archetype = archetype;
		this.id = id;
		this.icon = icon;
		this.constellation = constellation;
		this.epic = epic;
	}

	/** The node layout: this sub-tree's symbol, drawn in nodes. */
	public Constellation constellation() {
		return this.constellation;
	}

	public Archetype archetype() {
		return this.archetype;
	}

	/** Whether this is an epic sub-tree (spent from the epic pool, 5-point cap). */
	public boolean isEpic() {
		return this.epic;
	}

	/** The epic upgrade of this base tree, or null if it has none yet. */
	public @Nullable SubTree epicCounterpart() {
		return switch (this) {
			case ELEMENTALIST -> ORACLE_ELEMENTALIST;
			case WIZARD -> ORACLE_WIZARD;
			case PRIEST -> ORACLE_PRIEST;
			case SHADOW -> NEMESIS_SHADOW;
			case MARKSMAN -> NEMESIS_MARKSMAN;
			case ASSASSIN -> NEMESIS_ASSASSIN;
			case PROTECTOR -> COLOSSUS_PROTECTOR;
			case SLAYER -> COLOSSUS_SLAYER;
			case CRUSHER -> COLOSSUS_CRUSHER;
			default -> null;
		};
	}

	/** The base tree this epic tree upgrades, or null for a base tree. */
	public @Nullable SubTree baseCounterpart() {
		return switch (this) {
			case ORACLE_ELEMENTALIST -> ELEMENTALIST;
			case ORACLE_WIZARD -> WIZARD;
			case ORACLE_PRIEST -> PRIEST;
			case NEMESIS_SHADOW -> SHADOW;
			case NEMESIS_MARKSMAN -> MARKSMAN;
			case NEMESIS_ASSASSIN -> ASSASSIN;
			case COLOSSUS_PROTECTOR -> PROTECTOR;
			case COLOSSUS_SLAYER -> SLAYER;
			case COLOSSUS_CRUSHER -> CRUSHER;
			default -> null;
		};
	}

	public String id() {
		return this.id;
	}

	public Item icon() {
		return this.icon.get();
	}

	public Component displayName() {
		return Component.translatable("subtree." + Archetypes.MOD_ID + "." + this.id);
	}

	/** Resolve a wire id back to a sub-tree; null for garbage from the client. */
	public static SubTree byId(final String id) {
		for (SubTree tree : values()) {
			if (tree.id.equals(id)) {
				return tree;
			}
		}

		return null;
	}

	/** The three sub-trees of an archetype, in left-to-right screen order. */
	public static List<SubTree> of(final Archetype archetype) {
		return switch (archetype) {
			case STRENGTH -> List.of(PROTECTOR, SLAYER, CRUSHER);
			case AGILITY -> List.of(MARKSMAN, ASSASSIN, SHADOW);
			case INTELLECT -> List.of(ELEMENTALIST, WIZARD, PRIEST);
		};
	}
}
