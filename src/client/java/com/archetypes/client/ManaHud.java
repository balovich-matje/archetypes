package com.archetypes.client;

import com.archetypes.Archetype;
import com.archetypes.Archetypes;
import com.archetypes.Mana;
import com.archetypes.ModState;

import com.archetypes.compat.SpecialitiesBridge;

//? if >=1.21 {
import net.minecraft.client.DeltaTracker;
//?}
import net.minecraft.client.Minecraft;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
//? if >=1.21.11 {
import net.minecraft.client.renderer.RenderPipelines;
//?}
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
	private ManaHud() {
	}

	/** Whether the bar is on screen — the air-bar shift keys off this. */
	static boolean visible() {
		Minecraft client = Minecraft.getInstance();
		return client.player != null && client.level != null
				&& ModState.get(client.player) == Archetype.INTELLECT;
	}

	// STAGE 4 — the mana row displaces vanilla's air bubbles, and from 1.21.11 up
	// ArchetypesClient says so by wrapping the AIR_BAR element and translating it. Below the
	// boundary there is no such element and the shift is applied by client/mixin/GuiMixin, in
	// another package, which cannot see the package-private gate above. This is the seam, and
	// it is declared inside the fork so no other node grows a member: prior-node instruction
	// identity is the hard gate on this port.
	//
	// It returns the DISPLACEMENT rather than the flag so the caller needs no second branch,
	// and so the "10" lives in exactly one place per node — the 26.x arm's own `-10.0F` is the
	// other one, in ArchetypesClient, inside the matching half of the same fork.
	//? if <1.21.11 {
	/*public static int airBarShift() {
		return visible() ? 10 : 0;
	}
	*///?}

	// STAGE 5: `DeltaTracker` is 1.21's (frozen row); below it every HUD callback is handed
	// the raw partial tick as a float. None of these six reads it — it is carried because the
	// element signature carries it — so the third arm is the parameter TYPE and nothing else.
	//? if >=26.1 {
	public static void render(final GuiGraphicsExtractor graphics, final DeltaTracker delta) {
	//?} elif >=1.21 {
	/*public static void render(final GuiGraphics graphics, final DeltaTracker delta) {
	*///?} else {
	/*public static void render(final GuiGraphics graphics, final float delta) {
	*///?}
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;

		if (player == null || client.level == null
				|| ModState.get(player) != Archetype.INTELLECT) {
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
		// Whatever Specialities has raised the vanilla stack by THIS FRAME, read from
		// it rather than mirrored — its HUD-bar toggle can make that 0 (design R-C4).
		int y = height - BOTTOM - SpecialitiesBridge.hudShift();

		// Right-to-left like the hunger bar beneath: the first full orb is
		// the outermost right one.
		for (int i = 0; i < ORBS; i++) {
			Identifier sprite = i < full ? FULL : EMPTY;
			int x = right - SPRITE - i * STEP;
			// THE BLIT FAMILY, 1.21.11 -> 1.21.1, measured against both jars rather than guessed:
	// the RenderPipeline first argument does not exist below the boundary (there is no
	// Blaze3D pipeline object to pass), and the parameter ORDER moves — `(x, y, u, v, w, h,
	// regionW, regionH, texW, texH)` above becomes `(x, y, w, h, u, v, regionW, regionH,
	// texW, texH)` below. Both delegate to the same private twelve-argument blit with the
	// same arguments in the same roles (read out of the 1.21.1 bytecode), so this is an
	// argument shuffle and not a different draw.
			//
			// The tint has no parameter below the boundary: `setColor` is the whole
	// mechanism there, and it is STATE — it has to be put back, or every draw after this
	// one in the same frame inherits the tint (Skill Proficiencies' R-17).
			//? if >=1.21.11 {
			graphics.blit(RenderPipelines.GUI_TEXTURED, sprite, x, y,
					0.0F, 0.0F, SPRITE, SPRITE, SPRITE, SPRITE, SPRITE, SPRITE,
					blocked ? 0xFF666666 : 0xFFFFFFFF);
			//?} else {
			/*float grey = blocked ? 0.4F : 1.0F;
			graphics.setColor(grey, grey, grey, 1.0F);
			graphics.blit(sprite, x, y, SPRITE, SPRITE, 0.0F, 0.0F, SPRITE, SPRITE, SPRITE, SPRITE);
			graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
			*///?}
		}

		// The exact count over the row's middle, outlined the way the XP bar
		// draws its level so it survives any backdrop.
		String label = Integer.toString((int) current);
		int rowWidth = SPRITE + (ORBS - 1) * STEP;
		int textX = right - rowWidth + (rowWidth - client.font.width(label)) / 2;
		int textY = y - 1;

		//? if >=26.1 {
		graphics.text(client.font, label, textX + 1, textY, 0xFF000000, false);
		//?} else {
		/*graphics.drawString(client.font, label, textX + 1, textY, 0xFF000000, false);
		*///?}
		//? if >=26.1 {
		graphics.text(client.font, label, textX - 1, textY, 0xFF000000, false);
		//?} else {
		/*graphics.drawString(client.font, label, textX - 1, textY, 0xFF000000, false);
		*///?}
		//? if >=26.1 {
		graphics.text(client.font, label, textX, textY + 1, 0xFF000000, false);
		//?} else {
		/*graphics.drawString(client.font, label, textX, textY + 1, 0xFF000000, false);
		*///?}
		//? if >=26.1 {
		graphics.text(client.font, label, textX, textY - 1, 0xFF000000, false);
		//?} else {
		/*graphics.drawString(client.font, label, textX, textY - 1, 0xFF000000, false);
		*///?}
		//? if >=26.1 {
		graphics.text(client.font, label, textX, textY, blocked ? 0xFF999999 : 0xFF7FB2FF, false);
		//?} else {
		/*graphics.drawString(client.font, label, textX, textY, blocked ? 0xFF999999 : 0xFF7FB2FF, false);
		*///?}
	}
}
