package com.archetypes.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import com.archetypes.PassiveProcPayload;
import com.archetypes.ProtectorNodes;
import com.archetypes.SlayerNodes;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

/**
 * "That passive just fired": a chime, and the node's own icon born at the
 * crosshair, falling and fading like a struck spark. Purely reactive — the
 * server sends a {@link PassiveProcPayload} per proc and this draws it.
 */
public final class ProcIndicatorHud {
	/** Fall-and-fade lifetime, in milliseconds. */
	private static final int LIFE_MS = 650;
	/** How far the icon has fallen by the end, in gui pixels. */
	private static final float FALL = 42.0F;
	private static final int MAX_ACTIVE = 6;

	private record Proc(Identifier sprite, int texSize, long spawnedMs) {
	}

	private static final Deque<Proc> ACTIVE = new ArrayDeque<>();

	private ProcIndicatorHud() {
	}

	public static void push(final PassiveProcPayload payload) {
		Identifier sprite;
		int size;

		// The indicator wears the skill tree's own icon for the family.
		if ("slayer".equals(payload.subTreeId())) {
			var family = SlayerNodes.Family.valueOf(payload.family());
			sprite = family.sprite();
			size = family.spriteSize();
		} else {
			var family = ProtectorNodes.Family.valueOf(payload.family());
			sprite = family.sprite();
			size = family.spriteSize();
		}

		if (sprite == null) {
			return;
		}

		if (ACTIVE.size() >= MAX_ACTIVE) {
			ACTIVE.removeFirst();
		}

		ACTIVE.addLast(new Proc(sprite, size, System.currentTimeMillis()));

		var player = Minecraft.getInstance().player;

		if (player != null) {
			player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.7F);
		}
	}

	public static void render(final GuiGraphicsExtractor graphics, final DeltaTracker delta) {
		if (ACTIVE.isEmpty()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		int centerX = client.getWindow().getGuiScaledWidth() / 2;
		int centerY = client.getWindow().getGuiScaledHeight() / 2;
		long now = System.currentTimeMillis();

		int slot = 0;
		int count = ACTIVE.size();

		for (Iterator<Proc> it = ACTIVE.iterator(); it.hasNext(); slot++) {
			Proc proc = it.next();
			float t = (now - proc.spawnedMs()) / (float) LIFE_MS;

			if (t >= 1.0F) {
				it.remove();
				continue;
			}

			// Gravity-ish: slow birth, quick fall. Fade all the way out.
			int x = centerX - 8 + Math.round((slot - (count - 1) / 2.0F) * 14.0F);
			int y = centerY + 10 + Math.round(t * t * FALL);
			int alpha = Math.round((1.0F - t) * 216.0F);
			int tint = alpha << 24 | 0xFFFFFF;

			graphics.blit(RenderPipelines.GUI_TEXTURED, proc.sprite(), x, y, 0.0F, 0.0F,
					16, 16, proc.texSize(), proc.texSize(), proc.texSize(), proc.texSize(), tint);
		}
	}
}
