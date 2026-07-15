package com.archetypes;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The three archetypes. Each has a start name (what you pick, minute one) and a
 * peak name (the same archetype fully levelled) — the display name depends on
 * your tier, but the id is the neutral stat so renaming a tier never touches
 * saved data.
 */
public enum Archetype {
	/** Brawler -> Colossus. Melee, face to face. */
	STRENGTH("strength", 0xFFE06C4A, () -> Items.IRON_SWORD),
	/** Cutpurse -> Nemesis. Stealth melee and ranged. */
	AGILITY("agility", 0xFF7FCF9F, () -> Items.BOW),
	/** Seeker -> Oracle. Casting. */
	INTELLECT("intellect", 0xFF7A9CEE, () -> Items.ENCHANTED_BOOK);

	/** How many named tiers an archetype has: start and peak, for now. */
	public static final int TIERS = 2;

	public static final Codec<Archetype> CODEC = Codec.STRING.comapFlatMap(
			id -> byId(id).map(com.mojang.serialization.DataResult::success)
					.orElseGet(() -> com.mojang.serialization.DataResult.error(() -> "Unknown archetype: " + id)),
			Archetype::id);

	private final String id;
	private final int color;
	private final Supplier<Item> icon;

	Archetype(final String id, final int color, final Supplier<Item> icon) {
		this.id = id;
		this.color = color;
		this.icon = icon;
	}

	public String id() {
		return this.id;
	}

	public int color() {
		return this.color;
	}

	public Item icon() {
		return this.icon.get();
	}

	/** Display name at a given tier: 0 = start (Brawler), 1 = peak (Colossus). */
	public Component tierName(final int tier) {
		return Component.translatable("archetype." + Archetypes.MOD_ID + "." + this.id + ".tier." + tier);
	}

	/** One-line pitch shown on the picker. */
	public Component blurb() {
		return Component.translatable("archetype." + Archetypes.MOD_ID + "." + this.id + ".blurb");
	}

	/**
	 * Class-fantasy backdrop for the skill tree. The dim and vignette are baked
	 * into the texture, so it can be drawn flat with the nodes straight on top.
	 */
	public Identifier treeBackground() {
		return Archetypes.id("textures/gui/tree/" + this.id + ".png");
	}

	public static Optional<Archetype> byId(final String id) {
		for (Archetype archetype : values()) {
			if (archetype.id.equals(id)) {
				return Optional.of(archetype);
			}
		}

		return Optional.empty();
	}

	@Override
	public String toString() {
		return this.id.toLowerCase(Locale.ROOT);
	}
}
