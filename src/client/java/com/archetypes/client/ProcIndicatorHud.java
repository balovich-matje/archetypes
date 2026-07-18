package com.archetypes.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;

import com.archetypes.PassiveProcPayload;
import com.archetypes.ProtectorNodes;
import com.archetypes.SlayerNodes;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * "That passive just fired": the node's own icon born at the crosshair,
 * swept aside as it falls and fades — each proc drifts left or right at
 * random, like sparks off the blow. Purely reactive — the server sends a
 * {@link PassiveProcPayload} per proc and this draws it.
 */
public final class ProcIndicatorHud {
	/** Fall-and-fade lifetime, in milliseconds. */
	private static final int LIFE_MS = 700;
	/** How far the icon has fallen by the end, in gui pixels. */
	private static final float FALL = 46.0F;
	/** Sideways drift by the end — the curve of the fall. */
	private static final float DRIFT = 24.0F;
	/** Icon size on screen; procs deserve more presence than a slot icon. */
	private static final int SIZE = 24;
	private static final int MAX_ACTIVE = 6;

	private record Proc(@org.jspecify.annotations.Nullable Identifier sprite, int texSize,
			ItemStack item, int direction, long spawnedMs) {
	}

	private static final Deque<Proc> ACTIVE = new ArrayDeque<>();

	private ProcIndicatorHud() {
	}

	public static void push(final PassiveProcPayload payload) {
		Identifier sprite;
		int size;
		ItemStack item = ItemStack.EMPTY;

		// The indicator wears the skill tree's own icon for the family: the
		// Slayer families are full sprites, the Protector ones are the real
		// item render with the effect layer over it.
		if ("slayer".equals(payload.subTreeId())) {
			var family = SlayerNodes.Family.valueOf(payload.family());
			sprite = com.archetypes.TreeNodes.testSprite(com.archetypes.SubTree.SLAYER, family);
			size = 32;

			if (sprite == null) {
				sprite = family.sprite();
				size = family.spriteSize();
			}
		} else if ("crusher".equals(payload.subTreeId())) {
			var family = com.archetypes.CrusherNodes.Family.valueOf(payload.family());
			sprite = com.archetypes.TreeNodes.testSprite(com.archetypes.SubTree.CRUSHER, family);
			size = 32;

			if (sprite == null && family.sprite() != null) {
				sprite = family.sprite();
				size = family.spriteSize();
			} else if (sprite == null) {
				sprite = family.overlay();
				size = family.overlaySize();
				Item base = family.icon();
				item = base == null ? ItemStack.EMPTY : new ItemStack(base);
			}
		} else if ("wizard".equals(payload.subTreeId())) {
			// The wizard flash wears the same bake-off sprite the tree
			// screen shows, so the proc display follows TEST_ICON_SET.
			var family = com.archetypes.WizardNodes.Family.valueOf(payload.family());
			sprite = com.archetypes.TreeNodes.testSprite(com.archetypes.SubTree.WIZARD, family);
			size = 32;

			if (sprite == null) {
				Item base = family.icon();
				item = base == null ? ItemStack.EMPTY : new ItemStack(base);
			}
		} else {
			var family = ProtectorNodes.Family.valueOf(payload.family());
			sprite = com.archetypes.TreeNodes.testSprite(com.archetypes.SubTree.PROTECTOR, family);
			size = 32;

			if (sprite == null && family.sprite() != null) {
				sprite = family.sprite();
				size = family.spriteSize();
			} else if (sprite == null) {
				sprite = family.overlay();
				size = family.overlaySize();
				Item base = family.icon();
				item = base == null ? ItemStack.EMPTY : new ItemStack(base);
			}
		}

		if (sprite == null && item.isEmpty()) {
			return;
		}

		if (ACTIVE.size() >= MAX_ACTIVE) {
			ACTIVE.removeFirst();
		}

		ACTIVE.addLast(new Proc(sprite, size, item,
				ThreadLocalRandom.current().nextBoolean() ? 1 : -1, System.currentTimeMillis()));
	}

	public static void render(final GuiGraphicsExtractor graphics, final DeltaTracker delta) {
		if (ACTIVE.isEmpty()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		int centerX = client.getWindow().getGuiScaledWidth() / 2;
		int centerY = client.getWindow().getGuiScaledHeight() / 2;
		long now = System.currentTimeMillis();

		for (Iterator<Proc> it = ACTIVE.iterator(); it.hasNext();) {
			Proc proc = it.next();
			float t = (now - proc.spawnedMs()) / (float) LIFE_MS;

			if (t >= 1.0F) {
				it.remove();
				continue;
			}

			// Gravity-ish: slow birth, quick fall, curving off to one side.
			int x = centerX - SIZE / 2 + Math.round(proc.direction() * t * t * DRIFT);
			int y = centerY + 10 + Math.round(t * t * FALL);
			int alpha = Math.round((1.0F - t) * 224.0F);
			int tint = alpha << 24 | 0xFFFFFF;

			if (!proc.item().isEmpty()) {
				// Item renders can't fade, so the base shrinks away instead
				// while its effect layer fades on top.
				float scale = (SIZE / 16.0F) * (1.0F - 0.35F * t);
				int drawn = Math.round(16.0F * scale);
				var pose = graphics.pose();
				pose.pushMatrix();
				pose.scale(scale, scale);
				graphics.fakeItem(proc.item(),
						Math.round((x + (SIZE - drawn) / 2.0F) / scale),
						Math.round((y + (SIZE - drawn) / 2.0F) / scale));
				pose.popMatrix();
			}

			if (proc.sprite() != null) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, proc.sprite(), x, y, 0.0F, 0.0F,
						SIZE, SIZE, proc.texSize(), proc.texSize(), proc.texSize(), proc.texSize(),
						tint);
			}
		}
	}
}
