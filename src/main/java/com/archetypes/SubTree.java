package com.archetypes;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The three constellation sub-trees of each archetype. Each one's node graph
 * will eventually trace the outline of its symbol (Skyrim-style); the icon here
 * is the vanilla item standing in for that symbol.
 */
public enum SubTree {
	PROTECTOR(Archetype.STRENGTH, "protector", () -> Items.SHIELD, Constellations.PROTECTOR_SHIELD),
	SLAYER(Archetype.STRENGTH, "slayer", () -> Items.IRON_SWORD, Constellations.SLAYER_SWORD),
	CRUSHER(Archetype.STRENGTH, "crusher", () -> Items.MACE, Constellations.CRUSHER_MACE),

	MARKSMAN(Archetype.AGILITY, "marksman", () -> Items.BOW, Constellations.MARKSMAN_BOW),
	// No dagger in vanilla; the sword stands in for the label icon.
	ASSASSIN(Archetype.AGILITY, "assassin", () -> Items.IRON_SWORD, Constellations.ASSASSIN_DAGGER),
	SHADOW(Archetype.AGILITY, "shadow", () -> Items.LEATHER_CHESTPLATE, Constellations.SHADOW_MOON),

	// Was Fire Mage; the rename widened it to every element, so the fire
	// charge gave way to a neutral shard of raw magic.
	ELEMENTALIST(Archetype.INTELLECT, "elementalist", () -> Items.AMETHYST_SHARD, Constellations.ELEMENTALIST_FLAME),
	WIZARD(Archetype.INTELLECT, "wizard", () -> Items.BLAZE_ROD, Constellations.WIZARD_STAFF),
	// Was Healer, then briefly Apothecary; the holy-light kit is a priest's.
	PRIEST(Archetype.INTELLECT, "priest", () -> Items.TOTEM_OF_UNDYING, Constellations.PRIEST_ANKH);

	private final Archetype archetype;
	private final String id;
	private final Supplier<Item> icon;
	private final Constellation constellation;

	SubTree(final Archetype archetype, final String id, final Supplier<Item> icon,
			final Constellation constellation) {
		this.archetype = archetype;
		this.id = id;
		this.icon = icon;
		this.constellation = constellation;
	}

	/** The node layout: this sub-tree's symbol, drawn in nodes. */
	public Constellation constellation() {
		return this.constellation;
	}

	public Archetype archetype() {
		return this.archetype;
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
