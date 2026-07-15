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

		long now = client.level.getGameTime();
		int width = client.getWindow().getGuiScaledWidth();
		int height = client.getWindow().getGuiScaledHeight();
		int center = width / 2;
		int mainhandX = center - 90 + player.getInventory().getSelectedSlot() * 20 + 2;
		AttachmentTarget target = (AttachmentTarget) player;

		// Bash rides the shield, wherever the shield is. Vanilla hotbar
		// geometry: items at centre-90 + slot*20 + 2, offhand 26px outside on
		// the side opposite the main arm.
		Long bash = target.getAttached(ModAttachments.BASH_READY_AT);

		if (bash != null && bash > now) {
			int slotX;

			if (player.getOffhandItem().is(Items.SHIELD)) {
				slotX = player.getMainArm() == HumanoidArm.RIGHT ? center - 91 - 26 : center + 91 + 10;
			} else if (player.getMainHandItem().is(Items.SHIELD)) {
				slotX = mainhandX;
			} else {
				slotX = Integer.MIN_VALUE;
			}

			if (slotX != Integer.MIN_VALUE) {
				drawSeconds(graphics, client, bash - now, slotX, height);
			}
		}

		// The Slayer actives ride the mainhand weapon.
		if (com.archetypes.ModItems.isClaymore(player.getMainHandItem())) {
			Long decimate = target.getAttached(ModAttachments.DECIMATE_READY_AT);

			if (decimate != null && decimate > now) {
				drawSeconds(graphics, client, decimate - now, mainhandX, height);
			}
		} else if (com.archetypes.ModItems.isSword(player.getMainHandItem())) {
			Long storm = target.getAttached(ModAttachments.BLADESTORM_READY_AT);

			if (storm != null && storm > now) {
				drawSeconds(graphics, client, storm - now, mainhandX, height);
			}
		}
	}

	private static void drawSeconds(final GuiGraphicsExtractor graphics, final Minecraft client,
			final long remainingTicks, final int slotX, final int screenHeight) {
		String seconds = Long.toString((remainingTicks + 19) / 20);
		graphics.text(client.font, seconds,
				slotX + 8 - client.font.width(seconds) / 2, screenHeight - 16 - 3 + 4, 0xFFFFFFFF, true);
	}
}
