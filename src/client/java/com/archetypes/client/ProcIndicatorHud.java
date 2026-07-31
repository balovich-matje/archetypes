package com.archetypes.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;

import com.archetypes.ProtectorNodes;
import com.archetypes.SlayerNodes;

import net.minecraft.client.DeltaTracker;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
//? if >=1.21.11 {
import org.jspecify.annotations.Nullable;
//?} else {
/*import org.jetbrains.annotations.Nullable;
*///?}

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

	private record Proc(@Nullable Identifier sprite, int texSize,
			ItemStack item, int direction, long spawnedMs) {
	}

	private static final Deque<Proc> ACTIVE = new ArrayDeque<>();

	private ProcIndicatorHud() {
	}

	public static void push(final String subTreeId, final String familyName) {
		Identifier sprite;
		int size;
		ItemStack item = ItemStack.EMPTY;

		// The indicator wears the skill tree's own icon for the family: the
		// Slayer families are full sprites, the Protector ones are the real
		// item render with the effect layer over it.
		if ("slayer".equals(subTreeId)) {
			var family = SlayerNodes.Family.valueOf(familyName);
			sprite = com.archetypes.TreeNodes.familySprite(com.archetypes.SubTree.SLAYER, family);
			size = 32;

			if (sprite == null) {
				sprite = family.sprite();
				size = family.spriteSize();
			}
		} else if ("crusher".equals(subTreeId)) {
			var family = com.archetypes.CrusherNodes.Family.valueOf(familyName);
			sprite = com.archetypes.TreeNodes.familySprite(com.archetypes.SubTree.CRUSHER, family);
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
		} else if ("wizard".equals(subTreeId)) {
			// The wizard flash wears the same bake-off sprite the tree
			// screen shows, so the proc display follows TEST_ICON_SET.
			var family = com.archetypes.WizardNodes.Family.valueOf(familyName);
			sprite = com.archetypes.TreeNodes.familySprite(com.archetypes.SubTree.WIZARD, family);
			size = 32;

			if (sprite == null) {
				Item base = family.icon();
				item = base == null ? ItemStack.EMPTY : new ItemStack(base);
			}
		} else if ("colossus_crusher".equals(subTreeId)) {
			// The epic tree ships a complete 32px set, so there is no item or
			// overlay fallback to walk — the sprite is the whole resolution.
			var family = com.archetypes.ColossusCrusherNodes.Family.valueOf(familyName);
			sprite = com.archetypes.TreeNodes.familySprite(
					com.archetypes.SubTree.COLOSSUS_CRUSHER, family);
			size = 32;
		} else if ("colossus_slayer".equals(subTreeId)) {
			var family = com.archetypes.ColossusSlayerNodes.Family.valueOf(familyName);
			sprite = com.archetypes.TreeNodes.familySprite(
					com.archetypes.SubTree.COLOSSUS_SLAYER, family);
			size = 32;
		} else if ("colossus_protector".equals(subTreeId)) {
			var family = com.archetypes.ColossusProtectorNodes.Family.valueOf(familyName);
			sprite = com.archetypes.TreeNodes.familySprite(
					com.archetypes.SubTree.COLOSSUS_PROTECTOR, family);
			size = 32;
		} else if ("nemesis_assassin".equals(subTreeId)) {
			var family = com.archetypes.NemesisAssassinNodes.Family.valueOf(familyName);
			sprite = com.archetypes.TreeNodes.familySprite(
					com.archetypes.SubTree.NEMESIS_ASSASSIN, family);
			size = 32;
		} else {
			// The Protector's is the FALLBACK branch, so it has to survive a
			// proc from a tree with no branch of its own: Family.valueOf throws
			// on an unknown name, and a thrown packet handler takes the client
			// with it. An unrecognised family simply does not flash.
			ProtectorNodes.Family family = null;

			for (ProtectorNodes.Family candidate : ProtectorNodes.Family.values()) {
				if (candidate.name().equals(family)) {
					family = candidate;
					break;
				}
			}

			if (family == null) {
				return;
			}

			sprite = com.archetypes.TreeNodes.familySprite(com.archetypes.SubTree.PROTECTOR, family);
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

	//? if >=26.1 {
	public static void render(final GuiGraphicsExtractor graphics, final DeltaTracker delta) {
	//?} else {
	/*public static void render(final GuiGraphics graphics, final DeltaTracker delta) {
	*///?}
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
				// 1.21.11 replaced the GUI's 3D `PoseStack` with a 2D `Matrix3x2fStack`, so the
				// push/scale/pop verbs all rename and the 2D scale gains a third argument
				// below the boundary. Same transform either way; the arithmetic that computes
				// `scale` and the two rounded coordinates stays outside the fork.
				var pose = graphics.pose();
				//? if >=1.21.11 {
				pose.pushMatrix();
				pose.scale(scale, scale);
				//?} else {
				/*pose.pushPose();
				pose.scale(scale, scale, 1.0F);
				*///?}
				//? if >=26.1 {
				graphics.fakeItem(proc.item(),
				//?} else {
				/*graphics.renderFakeItem(proc.item(),
				*///?}
						Math.round((x + (SIZE - drawn) / 2.0F) / scale),
						Math.round((y + (SIZE - drawn) / 2.0F) / scale));
				//? if >=1.21.11 {
				pose.popMatrix();
				//?} else {
				/*pose.popPose();
				*///?}
			}

			if (proc.sprite() != null) {
				// The tint is a parameter above and `setColor` STATE below — see ManaHud. The
				// alpha is the whole point here (the indicator fades as it falls), so it is
				// unpacked from the same `tint` word rather than recomputed.
				//? if >=1.21.11 {
				graphics.blit(RenderPipelines.GUI_TEXTURED, proc.sprite(), x, y, 0.0F, 0.0F,
						SIZE, SIZE, proc.texSize(), proc.texSize(), proc.texSize(), proc.texSize(),
						tint);
				//?} else {
				/*graphics.setColor(1.0F, 1.0F, 1.0F, (tint >>> 24) / 255.0F);
				graphics.blit(proc.sprite(), x, y, SIZE, SIZE, 0.0F, 0.0F,
						proc.texSize(), proc.texSize(), proc.texSize(), proc.texSize());
				graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
				*///?}
			}
		}
	}
}
