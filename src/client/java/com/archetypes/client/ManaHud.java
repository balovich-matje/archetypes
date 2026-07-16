package com.archetypes.client;

import com.archetypes.Archetype;
import com.archetypes.Archetypes;
import com.archetypes.Mana;
import com.archetypes.ModAttachments;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * The Seeker's mana as ten little bottles above the hunger bar. The count is
 * a percentage gauge, not a unit one: 47/100 mana is 4 full bottles (floor),
 * however big the pool has grown. First-iteration art — the user may replace
 * the bottles later.
 */
public final class ManaHud {
	private static final Identifier FULL = Archetypes.id("textures/gui/mana_bottle_full.png");
	private static final Identifier EMPTY = Archetypes.id("textures/gui/mana_bottle_empty.png");

	private static final int BOTTLES = 10;
	private static final int SPRITE = 9;
	private static final int STEP = 8;
	/** The hunger row's height above the screen bottom, plus one row. */
	private static final int BOTTOM = 49;
	/** Specialities raises the whole vanilla stack by this much (its HUD_SHIFT). */
	private static final int SPECIALITIES_SHIFT = 7;

	private static final boolean SPECIALITIES_LOADED =
			FabricLoader.getInstance().isModLoaded("specialities");

	private ManaHud() {
	}

	public static void render(final GuiGraphicsExtractor graphics, final DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;

		if (player == null || client.level == null
				|| ModAttachments.get(player) != Archetype.INTELLECT) {
			return;
		}

		float max = Mana.max(player);
		int full = max <= 0.0F ? 0 : (int) (Mana.current(player) / max * BOTTLES);

		int width = client.getWindow().getGuiScaledWidth();
		int height = client.getWindow().getGuiScaledHeight();
		int right = width / 2 + 91;
		int y = height - BOTTOM - (SPECIALITIES_LOADED ? SPECIALITIES_SHIFT : 0);

		// Right-to-left like the hunger bar beneath: the first full bottle is
		// the outermost right one.
		for (int i = 0; i < BOTTLES; i++) {
			Identifier sprite = i < full ? FULL : EMPTY;
			int x = right - SPRITE - i * STEP;
			graphics.blit(RenderPipelines.GUI_TEXTURED, sprite, x, y,
					0.0F, 0.0F, SPRITE, SPRITE, SPRITE, SPRITE, SPRITE, SPRITE);
		}
	}
}
