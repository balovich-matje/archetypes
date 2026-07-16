package com.archetypes.compat;

import java.util.List;

import com.archetypes.Archetypes;
import com.archetypes.ModItems;
import com.archetypes.Tuning;
import com.specialities.api.SkillType;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * The Spellcasting skill Archetypes contributes to Specialities' engine: XP
 * from spending mana, +1 max mana per level, +1 mana regen per 20 levels.
 *
 * <p>Only ever classloaded when Specialities is installed — referenced solely
 * from {@link SpellcastingEntrypoint} (which Specialities instantiates) and
 * {@link SpecialitiesBridge}'s inner class (guarded by an isModLoaded check).
 */
public final class SpellcastingSkill implements SkillType {
	public static final SpellcastingSkill INSTANCE = new SpellcastingSkill();

	/** The intellect archetype's blue, so the skill reads as the Seeker's. */
	private static final int COLOR = 0xFF7A9CEE;

	private SpellcastingSkill() {
	}

	@Override
	public String id() {
		return "spellcasting";
	}

	@Override
	public int color() {
		return COLOR;
	}

	@Override
	public Item icon() {
		return ModItems.MAGIC_WAND;
	}

	@Override
	public Identifier iconTexture() {
		return Archetypes.id("item/magic_wand");
	}

	@Override
	public List<Component> screenLines(final int level) {
		return List.of(
				Component.translatable("tooltip.archetypes.spellcasting.mana", level),
				Component.translatable("tooltip.archetypes.spellcasting.regen",
						level / Tuning.MANA_REGEN_LEVELS_PER_POINT));
	}
}
