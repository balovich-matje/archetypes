package com.archetypes.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodConstants;

/**
 * What Well Fed's banked hunger looks like.
 *
 * <p>Vanilla's hunger row is ten drumsticks and knows nothing above twenty, so
 * a bar that can hold forty would otherwise read "full" for the whole second
 * half of it and the node would be invisible. The bank is drawn as the same
 * drumsticks in the same ten sockets, gilded — absorption's language, where
 * capacity past the normal maximum sits on the existing row rather than
 * claiming a new one. Ten sockets is one whole extra bar's worth, which is
 * exactly rank 2's ceiling.
 *
 * <p>Filling right to left, over the vanilla row: at 20 nothing is drawn, at 30
 * the outer five are gold, at 40 all ten are.
 */
public final class BankedHungerHud {
	private static final Identifier FULL = Identifier.withDefaultNamespace("hud/food_full");
	private static final Identifier HALF = Identifier.withDefaultNamespace("hud/food_half");

	private static final int SLOTS = 10;
	private static final int SPRITE = 9;
	private static final int STEP = 8;
	/** Vanilla's hunger row: {@code guiHeight() - 39}, the same line the hearts
	 * are drawn on. */
	private static final int BOTTOM = 39;
	/** Specialities raises the whole vanilla stack by this much (its HUD_SHIFT),
	 * and the hunger row goes with it. Mirrored from {@code ManaHud}. */
	private static final int SPECIALITIES_SHIFT = 7;
	/** Gold, so a banked drumstick reads as the same food worth more. */
	private static final int GILT = 0xFFFFC24A;

	private static final boolean SPECIALITIES_LOADED =
			FabricLoader.getInstance().isModLoaded("specialities");

	private BankedHungerHud() {
	}

	public static void render(final GuiGraphicsExtractor graphics, final DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;

		// The row is not ours when the night form has taken it away, and not
		// there at all when a mount's health has replaced it.
		if (player == null || client.level == null || UndeadHud.active()
				|| player.getVehicle() instanceof net.minecraft.world.entity.LivingEntity) {
			return;
		}

		// Guarded on the number, not on the node: a player who respecs out of
		// Well Fed while over twenty keeps what they banked until they burn it,
		// and the bar has to keep saying so.
		int banked = player.getFoodData().getFoodLevel() - FoodConstants.MAX_FOOD;

		if (banked <= 0) {
			return;
		}

		int right = client.getWindow().getGuiScaledWidth() / 2 + 91;
		int y = client.getWindow().getGuiScaledHeight() - BOTTOM
				- (SPECIALITIES_LOADED ? SPECIALITIES_SHIFT : 0);

		for (int i = 0; i < SLOTS; i++) {
			int x = right - i * STEP - SPRITE;

			if (i * 2 + 1 < banked) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FULL, x, y, SPRITE, SPRITE, GILT);
			} else if (i * 2 + 1 == banked) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HALF, x, y, SPRITE, SPRITE, GILT);
			}
		}
	}
}
