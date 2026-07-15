package com.archetypes.client;

import com.archetypes.ModAttachments;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * The bash's ability-layer countdown: whole seconds in the default font, drawn
 * on the shield's own slot — the offhand slot beside the hotbar, or the
 * selected hotbar slot if the shield is in the main hand. The swing layer keeps
 * the vanilla grey sweep; this is only the long cooldown.
 *
 * <p>Reads the synced {@code BASH_READY_AT} timestamp — no packet of its own.
 */
public final class BashCooldownHud {
	private BashCooldownHud() {
	}

	public static void render(final GuiGraphicsExtractor graphics, final DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;

		if (player == null || client.level == null) {
			return;
		}

		Long readyAt = ((AttachmentTarget) player).getAttached(ModAttachments.BASH_READY_AT);

		if (readyAt == null) {
			return;
		}

		long remaining = readyAt - client.level.getGameTime();

		if (remaining <= 0) {
			return;
		}

		int width = client.getWindow().getGuiScaledWidth();
		int height = client.getWindow().getGuiScaledHeight();
		int center = width / 2;
		int slotX;

		// Vanilla hotbar geometry: items sit at centre-90 + slot*20 + 2, the
		// offhand slot 26px outside the hotbar on the side opposite the main arm.
		if (player.getOffhandItem().is(Items.SHIELD)) {
			slotX = player.getMainArm() == HumanoidArm.RIGHT
					? center - 91 - 26
					: center + 91 + 10;
		} else if (player.getMainHandItem().is(Items.SHIELD)) {
			slotX = center - 90 + player.getInventory().getSelectedSlot() * 20 + 2;
		} else {
			// No shield in hand: the countdown has nothing to attach to.
			return;
		}

		int slotY = height - 16 - 3;
		String seconds = Long.toString((remaining + 19) / 20);
		graphics.text(client.font, seconds,
				slotX + 8 - client.font.width(seconds) / 2, slotY + 4, 0xFFFFFFFF, true);
	}
}
