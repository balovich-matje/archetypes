package com.archetypes.client;

import java.util.ArrayList;
import java.util.List;

import com.archetypes.ModAttachments;
import com.archetypes.NodePurchases;
import com.archetypes.ProtectorNodes;
import com.archetypes.SlayerNodes;
import com.archetypes.SubTree;
import com.archetypes.Tuning;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Every owned active's cooldown in one place, centred above the whole bottom
 * HUD stack — no more swapping to a weapon just to read its timer. One slot
 * per owned active (Bash, Decimate, Bladestorm), half again the size of a
 * hotbar icon: bright when ready, dimmed with a draining overlay and a
 * seconds count while recharging. Reads the synced ready-at attachments —
 * no packets of its own.
 */
public final class CooldownBarHud {
	/** 1.5x a hotbar icon. */
	private static final int ICON = 24;
	private static final int FRAME = ICON + 4;
	private static final int GAP = 4;
	/** Gap between the bar's bottom and the top of the hearts/armor rows. */
	private static final int BOTTOM = 56;

	private record Ability(Identifier sprite, int texSize, ItemStack item, Identifier overlay,
			int overlaySize, net.minecraft.client.KeyMapping key,
			AttachmentType<Long> readyAt, int totalTicks) {
	}

	private CooldownBarHud() {
	}

	public static void render(final GuiGraphicsExtractor graphics, final DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;

		if (player == null || client.level == null) {
			return;
		}

		List<Ability> abilities = collect(player);

		if (abilities.isEmpty()) {
			return;
		}

		long now = client.level.getGameTime();
		int width = client.getWindow().getGuiScaledWidth();
		int height = client.getWindow().getGuiScaledHeight();
		int totalWidth = abilities.size() * FRAME + (abilities.size() - 1) * GAP;
		int x = (width - totalWidth) / 2;
		int y = height - BOTTOM - FRAME;

		for (Ability ability : abilities) {
			VanillaUi.inset(graphics, x, y, FRAME, FRAME);

			int iconX = x + (FRAME - ICON) / 2;
			int iconY = y + (FRAME - ICON) / 2;

			if (ability.sprite() != null) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, ability.sprite(), iconX, iconY,
						0.0F, 0.0F, ICON, ICON,
						ability.texSize(), ability.texSize(), ability.texSize(), ability.texSize());
			} else {
				// fakeItem always draws 16px; scale the pose up to 1.5x.
				var pose = graphics.pose();
				pose.pushMatrix();
				pose.scale(1.5F, 1.5F);
				graphics.fakeItem(ability.item(), Math.round(iconX / 1.5F), Math.round(iconY / 1.5F));
				pose.popMatrix();
			}

			if (ability.overlay() != null) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, ability.overlay(), iconX, iconY,
						0.0F, 0.0F, ICON, ICON, ability.overlaySize(), ability.overlaySize(),
						ability.overlaySize(), ability.overlaySize());
			}

			Long readyAt = ((AttachmentTarget) player).getAttached(ability.readyAt());
			long remaining = readyAt == null ? 0 : readyAt - now;

			if (remaining > 0) {
				// Draining overlay from the top, vanilla item-cooldown style.
				float fraction = Math.min(1.0F, remaining / (float) ability.totalTicks());
				graphics.fill(iconX, iconY, iconX + ICON, iconY + Math.round(ICON * fraction),
						0xB3000000);

				String seconds = Long.toString((remaining + 19) / 20);
				graphics.text(client.font, seconds,
						x + FRAME / 2 - client.font.width(seconds) / 2,
						y + FRAME / 2 - 4, 0xFFFFFFFF, true);
			}

			// The slot's current bind, bottom-right corner of the frame.
			String bind = ability.key().getTranslatedKeyMessage().getString();

			if (bind.length() <= 3) {
				graphics.text(client.font, bind,
						x + FRAME - 2 - client.font.width(bind), y + FRAME - 9, 0xFFFFFF55, true);
			}

			x += FRAME + GAP;
		}
	}

	/** The actives this player owns, tree by tree. */
	private static List<Ability> collect(final Player player) {
		List<Ability> abilities = new ArrayList<>(3);

		var protector = NodePurchases.owned(player, SubTree.PROTECTOR);

		if (ProtectorNodes.rank(SubTree.PROTECTOR, protector, ProtectorNodes.Family.BASH) > 0) {
			int slam = ProtectorNodes.rank(SubTree.PROTECTOR, protector, ProtectorNodes.Family.SLAM);
			int recovery = ProtectorNodes.rank(SubTree.PROTECTOR, protector, ProtectorNodes.Family.COOLDOWN);
			var family = ProtectorNodes.Family.BASH;
			abilities.add(new Ability(null, 0, new ItemStack(Items.SHIELD),
					family.overlay(), family.overlaySize(), ArchetypesClient.ABILITY_KEYS[0],
					ModAttachments.BASH_READY_AT, Tuning.bashCooldownTicks(slam, recovery)));
		}

		var slayer = NodePurchases.owned(player, SubTree.SLAYER);
		int relentless = SlayerNodes.rank(SubTree.SLAYER, slayer, SlayerNodes.Family.RELENTLESS) > 0
				? Tuning.RELENTLESS_REDUCTION_TICKS : 0;

		if (SlayerNodes.rank(SubTree.SLAYER, slayer, SlayerNodes.Family.DECIMATE) > 0) {
			var family = SlayerNodes.Family.DECIMATE;
			abilities.add(new Ability(family.sprite(), family.spriteSize(), ItemStack.EMPTY,
					null, 0, ArchetypesClient.ABILITY_KEYS[1],
					ModAttachments.DECIMATE_READY_AT, Tuning.DECIMATE_COOLDOWN_TICKS - relentless));
		}

		if (SlayerNodes.rank(SubTree.SLAYER, slayer, SlayerNodes.Family.BLADESTORM) > 0) {
			var family = SlayerNodes.Family.BLADESTORM;
			abilities.add(new Ability(family.sprite(), family.spriteSize(), ItemStack.EMPTY,
					null, 0, ArchetypesClient.ABILITY_KEYS[1],
					ModAttachments.BLADESTORM_READY_AT, Tuning.BLADESTORM_COOLDOWN_TICKS - relentless));
		}

		var crusher = NodePurchases.owned(player, SubTree.CRUSHER);

		if (com.archetypes.CrusherNodes.rank(SubTree.CRUSHER, crusher,
				com.archetypes.CrusherNodes.Family.HAYMAKER) > 0) {
			var icon = com.archetypes.CrusherNodes.Family.HAYMAKER.icon();
			abilities.add(new Ability(null, 0, icon == null ? ItemStack.EMPTY : new ItemStack(icon),
					null, 0, ArchetypesClient.ABILITY_KEYS[2],
					ModAttachments.HAYMAKER_READY_AT, Tuning.HAYMAKER_COOLDOWN_TICKS));
		}

		if (com.archetypes.CrusherNodes.rank(SubTree.CRUSHER, crusher,
				com.archetypes.CrusherNodes.Family.QUAKE) > 0) {
			abilities.add(new Ability(null, 0, new ItemStack(Items.MACE),
					null, 0, ArchetypesClient.ABILITY_KEYS[2],
					ModAttachments.QUAKE_READY_AT, Tuning.QUAKE_COOLDOWN_TICKS));
		}

		// The Cutpurse actives: cooldown-driven like Strength's. Seeker spells
		// don't appear here — mana is their gauge and the bottle bar shows it.
		var marksman = NodePurchases.owned(player, SubTree.MARKSMAN);

		if (com.archetypes.MarksmanNodes.rank(SubTree.MARKSMAN, marksman,
				com.archetypes.MarksmanNodes.Family.TRUE_SHOT) > 0) {
			abilities.add(new Ability(null, 0, new ItemStack(Items.SPECTRAL_ARROW),
					null, 0, ArchetypesClient.ABILITY_KEYS[0],
					ModAttachments.TRUE_SHOT_READY_AT, Tuning.TRUE_SHOT_COOLDOWN_TICKS));
		}

		var assassin = NodePurchases.owned(player, SubTree.ASSASSIN);

		if (com.archetypes.PlaceholderNodes.owns(SubTree.ASSASSIN, assassin,
				com.archetypes.PlaceholderNodes.Kind.ACTIVE)) {
			boolean flurry = com.archetypes.PlaceholderNodes.owns(SubTree.ASSASSIN, assassin,
					com.archetypes.PlaceholderNodes.Kind.CAPSTONE_A);
			abilities.add(new Ability(null, 0, new ItemStack(Items.ENDER_PEARL),
					null, 0, ArchetypesClient.ABILITY_KEYS[1],
					ModAttachments.SHADOW_STEP_READY_AT, flurry
							? Tuning.SHADOW_STEP_FLURRY_COOLDOWN_TICKS
							: Tuning.SHADOW_STEP_COOLDOWN_TICKS));
		}

		var shadow = NodePurchases.owned(player, SubTree.SHADOW);

		if (com.archetypes.PlaceholderNodes.owns(SubTree.SHADOW, shadow,
				com.archetypes.PlaceholderNodes.Kind.ACTIVE)) {
			abilities.add(new Ability(null, 0, new ItemStack(Items.FERMENTED_SPIDER_EYE),
					null, 0, ArchetypesClient.ABILITY_KEYS[2],
					ModAttachments.INVIS_READY_AT, Tuning.INVIS_COOLDOWN_TICKS));
		}

		return abilities;
	}
}
