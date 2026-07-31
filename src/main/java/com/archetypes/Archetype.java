package com.archetypes;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
// jspecify is one of the game's OWN libraries only from 1.21.11 up (Skill
// Proficiencies' conventions §5e-bis); below that it is absent, and
// org.jetbrains:annotations 26.0.2 — on the compile classpath via fabric-loader,
// checked with `dependencies --configuration compileClasspath` rather than assumed
// — supplies a @Nullable that is @Target(TYPE_USE) as well. That last part is what
// makes this an import-only fork: this tree writes @Nullable in type-use position
// (`net.minecraft.resources.@Nullable Identifier`, ten sites in CrusherNodes and
// TreeNodes), which a METHOD/FIELD-only annotation could not occupy.
//
// The other 43 files in this tree carry the bare fork without this note.
//? if >=1.21.11 {
import org.jspecify.annotations.Nullable;
//?} else {
/*import org.jetbrains.annotations.Nullable;
*///?}

/**
 * The three archetypes. Each has a start name (what you pick, minute one) and a
 * peak name (the same archetype fully levelled) — the display name depends on
 * your tier, but the id is the neutral stat so renaming a tier never touches
 * saved data.
 */
public enum Archetype {
	/** Brawler -> Colossus. Melee, face to face. */
	STRENGTH("strength", 0xFFE06C4A, () -> Items.IRON_SWORD, true),
	/** Cutpurse -> Nemesis. Stealth melee and ranged. */
	AGILITY("agility", 0xFF7FCF9F, () -> Items.BOW, false),
	/** Seeker -> Oracle. Casting. */
	INTELLECT("intellect", 0xFF7A9CEE, () -> Items.ENCHANTED_BOOK, false);

	/** How many named tiers an archetype has: start and peak, for now. */
	public static final int TIERS = 2;

	public static final Codec<Archetype> CODEC = Codec.STRING.comapFlatMap(
			id -> byId(id).map(com.mojang.serialization.DataResult::success)
					.orElseGet(() -> com.mojang.serialization.DataResult.error(() -> "Unknown archetype: " + id)),
			Archetype::id);

	private final String id;
	private final int color;
	private final Supplier<Item> icon;
	private final boolean hasPortraits;

	Archetype(final String id, final int color, final Supplier<Item> icon, final boolean hasPortraits) {
		this.id = id;
		this.color = color;
		this.icon = icon;
		this.hasPortraits = hasPortraits;
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

	/** The picker card's role line: what you'll be doing, in five words. */
	public Component role() {
		return Component.translatable("archetype." + Archetypes.MOD_ID + "." + this.id + ".role");
	}

	/**
	 * Class-fantasy backdrop for the skill tree. The dim and vignette are baked
	 * into the texture, so it can be drawn flat with the nodes straight on top.
	 */
	public Identifier treeBackground() {
		return Archetypes.id("textures/gui/tree/" + this.id + ".png");
	}

	/**
	 * The picker collage — the archetype's three sub-archetype weapons in one
	 * crest — or null where the art does not exist yet and the caller should
	 * fall back to the item icon. Tracked as a flag rather than probed at
	 * runtime: a missing texture would silently render as the purple-and-black
	 * checkerboard.
	 */
	public @Nullable Identifier portrait() {
		return this.hasPortraits
				? Archetypes.id("textures/gui/picker/" + this.id + ".png")
				: null;
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
