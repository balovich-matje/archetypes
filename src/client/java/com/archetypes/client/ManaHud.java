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
 * The Seeker's mana as ten blue orbs above the hunger bar, outlined like the
 * hearts beside them. The count is a percentage gauge, not a unit one:
 * 47/100 mana is 4 full orbs (floor), however big the pool has grown — the
 * exact number sits over the middle of the row, the way the XP bar wears
 * its level.
 */
public final class ManaHud {
	private static final Identifier FULL = Archetypes.id("textures/gui/mana_orb_full.png");
	private static final Identifier EMPTY = Archetypes.id("textures/gui/mana_orb_empty.png");

	private static final int ORBS = 10;
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

	/** Whether the bar is on screen — the air-bar shift keys off this. */
	static boolean visible() {
		Minecraft client = Minecraft.getInstance();
		return client.player != null && client.level != null
				&& ModAttachments.get(client.player) == Archetype.INTELLECT;
	}

	public static void render(final GuiGraphicsExtractor graphics, final DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;

		if (player == null || client.level == null
				|| ModAttachments.get(player) != Archetype.INTELLECT) {
			return;
		}

		// A drawn weapon stops the flow: the whole bar turns grey so the WHY
		// of "my mana isn't coming back" is on screen, not in a wiki.
		boolean blocked = com.archetypes.ModItems.holdingCombatWeapon(player);

		float current = Mana.current(player);
		float max = Mana.max(player);
		int full = max <= 0.0F ? 0 : (int) (current / max * ORBS);

		int width = client.getWindow().getGuiScaledWidth();
		int height = client.getWindow().getGuiScaledHeight();
		int right = width / 2 + 91;
		int y = height - BOTTOM - (SPECIALITIES_LOADED ? SPECIALITIES_SHIFT : 0);

		// Right-to-left like the hunger bar beneath: the first full orb is
		// the outermost right one.
		for (int i = 0; i < ORBS; i++) {
			Identifier sprite = i < full ? FULL : EMPTY;
			int x = right - SPRITE - i * STEP;
			graphics.blit(RenderPipelines.GUI_TEXTURED, sprite, x, y,
					0.0F, 0.0F, SPRITE, SPRITE, SPRITE, SPRITE, SPRITE, SPRITE,
					blocked ? 0xFF666666 : 0xFFFFFFFF);
		}

		// The exact count over the row's middle, outlined the way the XP bar
		// draws its level so it survives any backdrop.
		String label = Integer.toString((int) current);
		int rowWidth = SPRITE + (ORBS - 1) * STEP;
		int textX = right - rowWidth + (rowWidth - client.font.width(label)) / 2;
		int textY = y - 1;

		graphics.text(client.font, label, textX + 1, textY, 0xFF000000, false);
		graphics.text(client.font, label, textX - 1, textY, 0xFF000000, false);
		graphics.text(client.font, label, textX, textY + 1, 0xFF000000, false);
		graphics.text(client.font, label, textX, textY - 1, 0xFF000000, false);
		graphics.text(client.font, label, textX, textY, blocked ? 0xFF999999 : 0xFF7FB2FF, false);
	}
}
