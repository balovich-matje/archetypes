package com.archetypes.client;

import com.archetypes.ModAttachments;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The bash's ability-layer countdown: whole seconds in the default font, just
 * under the crosshair. The swing layer keeps the vanilla grey sweep on the
 * shield item; this is only the long cooldown.
 *
 * <p>Reads the synced {@code BASH_READY_AT} timestamp — no packet of its own.
 */
public final class BashCooldownHud {
	private BashCooldownHud() {
	}

	public static void render(final GuiGraphicsExtractor graphics, final DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();

		// No hide-GUI check needed: attached to the crosshair layer, this is
		// suppressed by F1 along with the rest of the HUD.
		if (client.player == null || client.level == null) {
			return;
		}

		Long readyAt = ((AttachmentTarget) client.player).getAttached(ModAttachments.BASH_READY_AT);

		if (readyAt == null) {
			return;
		}

		long remaining = readyAt - client.level.getGameTime();

		if (remaining <= 0) {
			return;
		}

		String seconds = Long.toString((remaining + 19) / 20);
		int x = (client.getWindow().getGuiScaledWidth() - client.font.width(seconds)) / 2;
		int y = client.getWindow().getGuiScaledHeight() / 2 + 9;
		graphics.text(client.font, seconds, x, y, 0xFFFFFFFF, true);
	}
}
