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
			AttachmentType<Long> readyAt, int totalTicks, float manaCost, boolean spendsAll) {
		/** Cooldown-driven active: no mana involved. */
		private Ability(final Identifier sprite, final int texSize, final ItemStack item,
				final Identifier overlay, final int overlaySize, final net.minecraft.client.KeyMapping key,
				final AttachmentType<Long> readyAt, final int totalTicks) {
			this(sprite, texSize, item, overlay, overlaySize, key, readyAt, totalTicks, 0.0F, false);
		}

		/** Mana-driven spell: no cooldown clock, greyed while unaffordable.
		 * An all-mana spell (Meteorite) prices itself at the whole pool. */
		private Ability(final ItemStack item, final net.minecraft.client.KeyMapping key,
				final float manaCost, final boolean spendsAll) {
			this(null, 0, item, null, 0, key, null, 0, manaCost, spendsAll);
		}
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

			Long readyAt = ability.readyAt() == null ? null
					: ((AttachmentTarget) player).getAttached(ability.readyAt());
			long remaining = readyAt == null ? 0 : readyAt - now;

			// Spells: the price tag sits top-left, and an unaffordable spell
			// dims exactly like one on cooldown — the bar reads one language.
			if (ability.manaCost() > 0.0F) {
				float current = com.archetypes.Mana.current(player);

				if (current < ability.manaCost()) {
					graphics.fill(iconX, iconY, iconX + ICON, iconY + ICON, 0xB3000000);
				}

				// An all-mana spell shows what it would drink right now; the
				// threshold only decides when it greys out.
				int shown = ability.spendsAll() && current >= ability.manaCost()
						? (int) current : Math.round(ability.manaCost());
				graphics.text(client.font, Integer.toString(shown),
						x + 2, y + 2, 0xFF7FB2FF, true);
			}

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
			boolean seeker = com.archetypes.MarksmanNodes.rank(SubTree.MARKSMAN, marksman,
					com.archetypes.MarksmanNodes.Family.SEEKER_ARROW) > 0;
			abilities.add(new Ability(null, 0, new ItemStack(Items.SPECTRAL_ARROW),
					null, 0, ArchetypesClient.ABILITY_KEYS[0],
					ModAttachments.TRUE_SHOT_READY_AT, seeker
							? Tuning.TRUE_SHOT_SEEKER_COOLDOWN_TICKS : Tuning.TRUE_SHOT_COOLDOWN_TICKS));
		}

		var assassin = NodePurchases.owned(player, SubTree.ASSASSIN);

		if (com.archetypes.AssassinNodes.rank(SubTree.ASSASSIN, assassin,
				com.archetypes.AssassinNodes.Family.SHADOW_STEP) > 0) {
			boolean flurry = com.archetypes.AssassinNodes.rank(SubTree.ASSASSIN, assassin,
					com.archetypes.AssassinNodes.Family.SHADOW_FLURRY) > 0;
			abilities.add(new Ability(null, 0, new ItemStack(Items.ENDER_PEARL),
					null, 0, ArchetypesClient.ABILITY_KEYS[1],
					ModAttachments.SHADOW_STEP_READY_AT, flurry
							? Tuning.SHADOW_STEP_FLURRY_COOLDOWN_TICKS
							: Tuning.SHADOW_STEP_COOLDOWN_TICKS));
		}

		var shadow = NodePurchases.owned(player, SubTree.SHADOW);

		if (com.archetypes.ShadowNodes.rank(SubTree.SHADOW, shadow,
				com.archetypes.ShadowNodes.Family.INVISIBILITY) > 0) {
			var family = com.archetypes.ShadowNodes.Family.INVISIBILITY;
			abilities.add(new Ability(family.sprite(), family.spriteSize(), ItemStack.EMPTY,
					null, 0, ArchetypesClient.ABILITY_KEYS[2],
					ModAttachments.INVIS_READY_AT, Tuning.INVIS_COOLDOWN_TICKS));
		}

		// The Seeker's spells: no cooldowns, so these are keybind + price
		// tags over the mana bar, dimming when the pool can't pay.
		var elementalist = NodePurchases.owned(player, SubTree.ELEMENTALIST);
		boolean fire = com.archetypes.ElementalistNodes.rank(SubTree.ELEMENTALIST, elementalist,
				com.archetypes.ElementalistNodes.Family.FIREBALL) > 0;
		boolean ice = com.archetypes.ElementalistNodes.rank(SubTree.ELEMENTALIST, elementalist,
				com.archetypes.ElementalistNodes.Family.ICE_BLAST) > 0;

		if (fire || ice) {
			boolean meteor = fire && com.archetypes.ElementalistNodes.rank(SubTree.ELEMENTALIST,
					elementalist, com.archetypes.ElementalistNodes.Family.METEORITE) > 0;
			boolean flame = fire && com.archetypes.ElementalistNodes.rank(SubTree.ELEMENTALIST,
					elementalist, com.archetypes.ElementalistNodes.Family.FLAMETHROWER) > 0;
			boolean glacial = ice && com.archetypes.ElementalistNodes.rank(SubTree.ELEMENTALIST,
					elementalist, com.archetypes.ElementalistNodes.Family.GLACIAL_SPIKE) > 0;
			boolean blizzard = ice && com.archetypes.ElementalistNodes.rank(SubTree.ELEMENTALIST,
					elementalist, com.archetypes.ElementalistNodes.Family.BLIZZARD) > 0;

			net.minecraft.world.item.Item icon = meteor ? Items.MAGMA_BLOCK
					: flame ? Items.BLAZE_ROD
					: glacial ? Items.BLUE_ICE
					: blizzard ? Items.SNOW_BLOCK
					: ice ? Items.ICE : Items.FIRE_CHARGE;
			float cost = meteor ? Tuning.METEOR_MIN_MANA
					: com.archetypes.SeekerSpells.elementCost(player,
							flame || blizzard ? Tuning.FLAME_START_COST
									: ice ? Tuning.ICE_BLAST_COST : Tuning.FIREBALL_COST,
							fire, ice);
			abilities.add(new Ability(new ItemStack(icon), ArchetypesClient.ABILITY_KEYS[0], cost, meteor));
		}

		var wizard = NodePurchases.owned(player, SubTree.WIZARD);

		if (com.archetypes.PlaceholderNodes.owns(SubTree.WIZARD, wizard,
				com.archetypes.PlaceholderNodes.Kind.ACTIVE)) {
			abilities.add(new Ability(new ItemStack(Items.AMETHYST_SHARD),
					ArchetypesClient.ABILITY_KEYS[1], Tuning.MISSILE_COST, false));
		}

		var priest = NodePurchases.owned(player, SubTree.PRIEST);

		if (com.archetypes.PlaceholderNodes.owns(SubTree.PRIEST, priest,
				com.archetypes.PlaceholderNodes.Kind.ACTIVE)) {
			abilities.add(new Ability(new ItemStack(Items.GLOWSTONE_DUST),
					ArchetypesClient.ABILITY_KEYS[2], Tuning.HOLY_COST, false));
		}

		return abilities;
	}
}
