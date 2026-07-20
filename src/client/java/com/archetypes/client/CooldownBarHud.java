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
import net.minecraft.world.entity.player.Player;

/**
 * Every owned active's cooldown in one place, docked to the RIGHT of the
 * hotbar and moving with it — one hotbar-sized slot per owned active,
 * wearing the same icon its skill-tree node wears (via
 * {@link VanillaUi#nodeIcon}): bright when ready, dimmed with a draining
 * overlay and a seconds count while recharging. Reads the synced ready-at
 * attachments — no packets of its own.
 */
public final class CooldownBarHud {
	/** Hotbar geometry: the bar is 182 wide, its slots 22 tall. */
	private static final int HOTBAR_HALF = 91;
	private static final int FRAME = 22;
	private static final int ICON = 16;
	/** Breathing room between the hotbar's edge and our first slot. */
	private static final int MARGIN = 4;

	/** How many ticks a tile still has to drain, or 0 when it is ready. Kept as
	 * a function rather than an attachment type because not every clock IS an
	 * attachment: the night form's lockout is derived from when the form was
	 * entered, not stored as a ready-at stamp. */
	private interface Clock {
		int remainingTicks(Player player);
	}

	private record Ability(SubTree tree, int node, net.minecraft.client.KeyMapping key,
			Clock clock, int totalTicks, float manaCost, boolean spendsAll) {
		/** Cooldown-driven active: no mana involved. */
		private Ability(final SubTree tree, final Enum<?> family,
				final net.minecraft.client.KeyMapping key,
				final AttachmentType<Long> readyAt, final int totalTicks) {
			this(tree, com.archetypes.TreeNodes.indexOfFamily(tree, family), key,
					player -> {
						Long ready = ((AttachmentTarget) player).getAttached(readyAt);
						long left = ready == null ? 0L : ready - player.level().getGameTime();
						return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, left));
					}, totalTicks, 0.0F, false);
		}

		/** An active whose clock is not a ready-at attachment. */
		private Ability(final SubTree tree, final Enum<?> family,
				final net.minecraft.client.KeyMapping key,
				final Clock clock, final int totalTicks) {
			this(tree, com.archetypes.TreeNodes.indexOfFamily(tree, family), key, clock, totalTicks,
					0.0F, false);
		}

		/** Mana-driven spell: no cooldown clock, greyed while unaffordable.
		 * An all-mana spell (Meteorite) prices itself at the whole pool. */
		private Ability(final SubTree tree, final Enum<?> family,
				final net.minecraft.client.KeyMapping key, final float manaCost, final boolean spendsAll) {
			this(tree, com.archetypes.TreeNodes.indexOfFamily(tree, family), key, (Clock) null, 0,
					manaCost, spendsAll);
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

		int width = client.getWindow().getGuiScaledWidth();
		int height = client.getWindow().getGuiScaledHeight();
		// Docked to the hotbar's right edge, bottom-aligned with it — the
		// hotbar is centred, so this position tracks it at every GUI scale.
		int x = width / 2 + HOTBAR_HALF + MARGIN;
		int y = height - FRAME;

		for (Ability ability : abilities) {
			VanillaUi.inset(graphics, x, y, FRAME, FRAME);

			int iconX = x + (FRAME - ICON) / 2;
			int iconY = y + (FRAME - ICON) / 2;

			// The node's own icon, native 16px — whatever the tree screen
			// shows (bake-off sets included), the tracker shows.
			VanillaUi.nodeIcon(graphics, ability.tree(), ability.node(), iconX, iconY);

			long remaining = ability.clock() == null ? 0 : ability.clock().remainingTicks(player);

			// Spells: the price tag sits top-left, and an unaffordable spell
			// dims exactly like one on cooldown — the bar reads one language.
			if (ability.manaCost() > 0.0F) {
				float current = com.archetypes.Mana.current(player);

				// Dim when the pool can't pay — or when no wand is in hand,
				// since every spell now requires one.
				if (current < ability.manaCost()
						|| !com.archetypes.ModItems.isWand(player.getMainHandItem())) {
					graphics.fill(iconX, iconY, iconX + ICON, iconY + ICON, 0xB3000000);
				}

				// An all-mana spell shows what it would drink right now; the
				// threshold only decides when it greys out.
				int shown = ability.spendsAll() && current >= ability.manaCost()
						? (int) current : Math.round(ability.manaCost());
				graphics.text(client.font, Integer.toString(shown),
						x + 1, y + 1, 0xFF7FB2FF, true);
			}

			if (remaining > 0) {
				// Draining overlay from the top, vanilla item-cooldown style.
				float fraction = Math.min(1.0F, remaining / (float) ability.totalTicks());
				graphics.fill(iconX, iconY, iconX + ICON, iconY + Math.round(ICON * fraction),
						0xB3000000);

				String label = clock(remaining);
				graphics.text(client.font, label,
						x + FRAME / 2 - client.font.width(label) / 2,
						y + FRAME / 2 - 4, 0xFFFFFFFF, true);
			}

			// The slot's current bind, bottom-right corner of the frame.
			String bind = ability.key().getTranslatedKeyMessage().getString();

			if (bind.length() <= 3) {
				graphics.text(client.font, bind,
						x + FRAME - 1 - client.font.width(bind), y + FRAME - 9, 0xFFFFFF55, true);
			}

			x += FRAME;
		}
	}

	/**
	 * A remaining-ticks count that fits a 22px tile. Seconds up to 99, then
	 * whole minutes — the night form runs an hour, and "3600" would spill out
	 * of the frame and mean nothing at a glance anyway.
	 */
	private static String clock(final long remainingTicks) {
		long seconds = (remainingTicks + 19) / 20;
		return seconds < 100 ? Long.toString(seconds) : (seconds + 59) / 60 + "m";
	}

	/** The actives this player owns, tree by tree. */
	private static List<Ability> collect(final Player player) {
		List<Ability> abilities = new ArrayList<>(3);

		var protector = NodePurchases.owned(player, SubTree.PROTECTOR);

		if (ProtectorNodes.rank(SubTree.PROTECTOR, protector, ProtectorNodes.Family.BASH) > 0) {
			int slam = ProtectorNodes.rank(SubTree.PROTECTOR, protector, ProtectorNodes.Family.SLAM);
			int recovery = ProtectorNodes.rank(SubTree.PROTECTOR, protector, ProtectorNodes.Family.COOLDOWN);
			abilities.add(new Ability(SubTree.PROTECTOR, ProtectorNodes.Family.BASH,
					ArchetypesClient.ABILITY_KEYS[0],
					ModAttachments.BASH_READY_AT, Tuning.bashCooldownTicks(slam, recovery)));
		}

		var slayer = NodePurchases.owned(player, SubTree.SLAYER);
		int relentless = SlayerNodes.rank(SubTree.SLAYER, slayer, SlayerNodes.Family.RELENTLESS) > 0
				? Tuning.RELENTLESS_REDUCTION_TICKS : 0;

		if (SlayerNodes.rank(SubTree.SLAYER, slayer, SlayerNodes.Family.DECIMATE) > 0) {
			abilities.add(new Ability(SubTree.SLAYER, SlayerNodes.Family.DECIMATE,
					ArchetypesClient.ABILITY_KEYS[1],
					ModAttachments.DECIMATE_READY_AT, Tuning.DECIMATE_COOLDOWN_TICKS - relentless));
		}

		if (SlayerNodes.rank(SubTree.SLAYER, slayer, SlayerNodes.Family.BLADESTORM) > 0) {
			abilities.add(new Ability(SubTree.SLAYER, SlayerNodes.Family.BLADESTORM,
					ArchetypesClient.ABILITY_KEYS[1],
					ModAttachments.BLADESTORM_READY_AT, Tuning.BLADESTORM_COOLDOWN_TICKS - relentless));
		}

		var crusher = NodePurchases.owned(player, SubTree.CRUSHER);

		if (com.archetypes.CrusherNodes.rank(SubTree.CRUSHER, crusher,
				com.archetypes.CrusherNodes.Family.HAYMAKER) > 0) {
			abilities.add(new Ability(SubTree.CRUSHER, com.archetypes.CrusherNodes.Family.HAYMAKER,
					ArchetypesClient.ABILITY_KEYS[2],
					ModAttachments.HAYMAKER_READY_AT, Tuning.HAYMAKER_COOLDOWN_TICKS));
		}

		if (com.archetypes.CrusherNodes.rank(SubTree.CRUSHER, crusher,
				com.archetypes.CrusherNodes.Family.QUAKE) > 0) {
			abilities.add(new Ability(SubTree.CRUSHER, com.archetypes.CrusherNodes.Family.QUAKE,
					ArchetypesClient.ABILITY_KEYS[2],
					ModAttachments.QUAKE_READY_AT, Tuning.QUAKE_COOLDOWN_TICKS));
		}

		// The Cutpurse actives: cooldown-driven like Strength's. Seeker spells
		// don't appear here — mana is their gauge and the bottle bar shows it.
		var marksman = NodePurchases.owned(player, SubTree.MARKSMAN);

		if (com.archetypes.MarksmanNodes.rank(SubTree.MARKSMAN, marksman,
				com.archetypes.MarksmanNodes.Family.TRUE_SHOT) > 0) {
			boolean seeker = com.archetypes.MarksmanNodes.rank(SubTree.MARKSMAN, marksman,
					com.archetypes.MarksmanNodes.Family.SEEKER_ARROW) > 0;
			abilities.add(new Ability(SubTree.MARKSMAN, com.archetypes.MarksmanNodes.Family.TRUE_SHOT,
					ArchetypesClient.ABILITY_KEYS[0],
					ModAttachments.TRUE_SHOT_READY_AT, seeker
							? Tuning.TRUE_SHOT_SEEKER_COOLDOWN_TICKS : Tuning.TRUE_SHOT_COOLDOWN_TICKS));
		}

		var assassin = NodePurchases.owned(player, SubTree.ASSASSIN);

		if (com.archetypes.AssassinNodes.rank(SubTree.ASSASSIN, assassin,
				com.archetypes.AssassinNodes.Family.SHADOW_STEP) > 0) {
			boolean flurry = com.archetypes.AssassinNodes.rank(SubTree.ASSASSIN, assassin,
					com.archetypes.AssassinNodes.Family.SHADOW_FLURRY) > 0;
			abilities.add(new Ability(SubTree.ASSASSIN, com.archetypes.AssassinNodes.Family.SHADOW_STEP,
					ArchetypesClient.ABILITY_KEYS[1],
					ModAttachments.SHADOW_STEP_READY_AT, flurry
							? Tuning.SHADOW_STEP_FLURRY_COOLDOWN_TICKS
							: Tuning.SHADOW_STEP_COOLDOWN_TICKS));
		}

		var shadow = NodePurchases.owned(player, SubTree.SHADOW);

		if (com.archetypes.ShadowNodes.rank(SubTree.SHADOW, shadow,
				com.archetypes.ShadowNodes.Family.INVISIBILITY) > 0) {
			abilities.add(new Ability(SubTree.SHADOW, com.archetypes.ShadowNodes.Family.INVISIBILITY,
					ArchetypesClient.ABILITY_KEYS[2],
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

			// The base spell always sits on the element key; the capstone,
			// when owned, gets its own tile on the capstone key.
			abilities.add(new Ability(SubTree.ELEMENTALIST,
					ice ? com.archetypes.ElementalistNodes.Family.ICE_BLAST
							: com.archetypes.ElementalistNodes.Family.FIREBALL,
					ArchetypesClient.ABILITY_KEYS[0],
					com.archetypes.SeekerSpells.elementCost(player,
							ice ? Tuning.ICE_BLAST_COST : Tuning.FIREBALL_COST, fire, ice, false),
					false));

			if (meteor || flame || glacial || blizzard) {
				var capstone = meteor ? com.archetypes.ElementalistNodes.Family.METEORITE
						: flame ? com.archetypes.ElementalistNodes.Family.FLAMETHROWER
						: glacial ? com.archetypes.ElementalistNodes.Family.GLACIAL_SPIKE
						: com.archetypes.ElementalistNodes.Family.BLIZZARD;
				float cost = meteor ? Tuning.METEOR_MIN_MANA
						: com.archetypes.SeekerSpells.elementCost(player,
								flame ? Tuning.FLAME_START_COST
										: blizzard ? Tuning.BLIZZARD_COST : Tuning.ICE_BLAST_COST,
								fire, ice, false);
				abilities.add(new Ability(SubTree.ELEMENTALIST, capstone,
						ArchetypesClient.ABILITY_KEYS[3], cost, meteor));
			}
		}

		var wizard = NodePurchases.owned(player, SubTree.WIZARD);

		if (com.archetypes.WizardNodes.rank(SubTree.WIZARD, wizard,
				com.archetypes.WizardNodes.Family.MAGIC_MISSILE) > 0) {
			abilities.add(new Ability(SubTree.WIZARD, com.archetypes.WizardNodes.Family.MAGIC_MISSILE,
					ArchetypesClient.ABILITY_KEYS[1],
					com.archetypes.SeekerSpells.missileCost(player), false));
		}

		var priest = NodePurchases.owned(player, SubTree.PRIEST);

		if (com.archetypes.PriestNodes.rank(SubTree.PRIEST, priest,
				com.archetypes.PriestNodes.Family.HOLY_LIGHT) > 0) {
			abilities.add(new Ability(SubTree.PRIEST, com.archetypes.PriestNodes.Family.HOLY_LIGHT,
					ArchetypesClient.ABILITY_KEYS[2],
					com.archetypes.SeekerSpells.holyCost(player), false));
		}

		// The epic Oracle actives: mana-priced tiles on the epic keys, same as
		// the base Seeker spells.
		var oracleElem = NodePurchases.owned(player, SubTree.ORACLE_ELEMENTALIST);

		if (com.archetypes.OracleElementalistNodes.rank(SubTree.ORACLE_ELEMENTALIST, oracleElem,
				com.archetypes.OracleElementalistNodes.Family.LIGHTNING_STRIKE) > 0) {
			abilities.add(new Ability(SubTree.ORACLE_ELEMENTALIST,
					com.archetypes.OracleElementalistNodes.Family.LIGHTNING_STRIKE,
					ArchetypesClient.ABILITY_KEYS[4],
					com.archetypes.SeekerSpells.wandDiscount(player, Tuning.LIGHTNING_STRIKE_COST),
					false));
		}

		// The Dark Ritual's tile is the night form's lockout: while it drains,
		// the key is refused; bright means "press to go back". A mortal
		// Cutpurse's tile is bright too — for them the press starts the ritual.
		var nemesis = NodePurchases.owned(player, SubTree.NEMESIS_SHADOW);

		if (com.archetypes.NemesisShadowNodes.rank(SubTree.NEMESIS_SHADOW, nemesis,
				com.archetypes.NemesisShadowNodes.Family.DARK_RITUAL) > 0) {
			abilities.add(new Ability(SubTree.NEMESIS_SHADOW,
					com.archetypes.NemesisShadowNodes.Family.DARK_RITUAL,
					ArchetypesClient.ABILITY_KEYS[6],
					com.archetypes.NightForm::lockoutRemainingTicks,
					Tuning.NIGHT_FORM_LOCKOUT_TICKS));
		}

		// Deadeye's tile drains its 90-second lockout. Long Watch lengthens the
		// stance, not the cooldown, so the total never moves.
		var nemesisMarksman = NodePurchases.owned(player, SubTree.NEMESIS_MARKSMAN);

		if (com.archetypes.NemesisMarksmanNodes.rank(SubTree.NEMESIS_MARKSMAN, nemesisMarksman,
				com.archetypes.NemesisMarksmanNodes.Family.DEADEYE) > 0) {
			abilities.add(new Ability(SubTree.NEMESIS_MARKSMAN,
					com.archetypes.NemesisMarksmanNodes.Family.DEADEYE,
					ArchetypesClient.ABILITY_KEYS[4],
					ModAttachments.DEADEYE_READY_AT, Tuning.DEADEYE_COOLDOWN_TICKS));
		}

		// Death Mark's tile drains its 45 seconds. It empties early rather than
		// counting down to zero — a mark that dies hands the key straight back
		// — which is the one thing the tile has to show.
		var nemesisAssassin = NodePurchases.owned(player, SubTree.NEMESIS_ASSASSIN);

		if (com.archetypes.NemesisAssassinNodes.rank(SubTree.NEMESIS_ASSASSIN, nemesisAssassin,
				com.archetypes.NemesisAssassinNodes.Family.DEATH_MARK) > 0) {
			abilities.add(new Ability(SubTree.NEMESIS_ASSASSIN,
					com.archetypes.NemesisAssassinNodes.Family.DEATH_MARK,
					ArchetypesClient.ABILITY_KEYS[5],
					ModAttachments.DEATH_MARK_READY_AT, Tuning.DEATH_MARK_COOLDOWN_TICKS));
		}

		// Titan's Leap shares slot 6 with the Dark Ritual; a Brawler only ever
		// sees this one, a Cutpurse only ever sees that one.
		var colossusCrusher = NodePurchases.owned(player, SubTree.COLOSSUS_CRUSHER);

		if (com.archetypes.ColossusCrusherNodes.rank(SubTree.COLOSSUS_CRUSHER, colossusCrusher,
				com.archetypes.ColossusCrusherNodes.Family.TITAN_LEAP) > 0) {
			abilities.add(new Ability(SubTree.COLOSSUS_CRUSHER,
					com.archetypes.ColossusCrusherNodes.Family.TITAN_LEAP,
					ArchetypesClient.ABILITY_KEYS[6],
					ModAttachments.LEAP_READY_AT, Tuning.TITAN_LEAP_COOLDOWN_TICKS));
		}

		var oracleWiz = NodePurchases.owned(player, SubTree.ORACLE_WIZARD);

		if (com.archetypes.OracleWizardNodes.rank(SubTree.ORACLE_WIZARD, oracleWiz,
				com.archetypes.OracleWizardNodes.Family.MAGIC_ARMAMENTS) > 0) {
			abilities.add(new Ability(SubTree.ORACLE_WIZARD,
					com.archetypes.OracleWizardNodes.Family.MAGIC_ARMAMENTS,
					ArchetypesClient.ABILITY_KEYS[5],
					com.archetypes.SeekerSpells.wandDiscount(player, Tuning.MAGIC_ARMAMENTS_COST),
					false));
		}

		return abilities;
	}
}
