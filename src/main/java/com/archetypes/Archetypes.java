package com.archetypes;

import com.archetypes.platform.ArchetypeStore;
import com.archetypes.platform.Net;
import com.archetypes.state.WireId;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Archetypes implements ModInitializer {
	public static final String MOD_ID = "archetypes";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModState.initialize();
		ModItems.initialize();
		ModEntities.initialize();
		ManaEffects.initialize();
		RadianceEffect.initialize();
		ManaPotions.initialize();
		AmnesiaPotions.initialize();
		ModParticles.initialize();
		ProtectorTicker.initialize();
		SlayerCombat.initialize();
		SlayerTicker.initialize();
		SlayerActives.initialize();
		CrusherTicker.initialize();
		AgilityCombat.initialize();
		AgilityTicker.initialize();
		ShadowTicker.initialize();
		SeekerTicker.initialize();
		SeekerCombat.initialize();
		BlizzardZones.initialize();
		OracleStrikes.initialize();
		OracleWizardTicker.initialize();
		NightFormTicker.initialize();
		RadianceAura.initialize();
		ColossusProtector.initialize();
		ColossusSlayer.initialize();
		// The op-gated dev test kit: /archetypes set|level|buy|dummy|trace.
		// Idle until somebody with permission level 2 asks for it.
		ArchetypeCommands.initialize();

		// All eleven channel types, both directions, in one call — a dedicated server
		// registers the clientbound pair too, which is why this cannot live client-side.
		Net.INSTANCE.registerAll();

		Net.INSTANCE.onServerbound(WireId.BUY_NODE, (player, buf) -> {
			SubTree tree = SubTree.byId(buf.readUtf());
			int node = buf.readVarInt();

			// Only spend into trees of the archetype you actually are.
			if (tree == null || ModState.get(player) != tree.archetype()) {
				return;
			}

			NodePurchases.buy(player, tree, node);
		});

		// The three ability keys are slots, one per sub-tree in screen order;
		// what a slot casts depends on the archetype. Strength trees keep
		// their internal weapon dispatch (the capstone pairs are exclusive).
		Net.INSTANCE.onServerbound(WireId.ACTIVE_ABILITY, (player, buf) -> {
					int slot = buf.readVarInt();
					Archetype archetype = ModState.get(player);

					if (archetype == null || slot < 0 || slot >= 7) {
						return;
					}

					// Slot 3 is the capstone key — Elementalist-only for now,
					// with room for more trees in later versions.
					if (slot == 3) {
						if (archetype == Archetype.INTELLECT) {
							SeekerSpells.castElementalistCapstone(player);
						}

						return;
					}

					// Slots 4-6 are the epic actives, and archetypes share them:
					// an epic tree takes slot 4 + N, where N is its base tree's
					// place in SubTree.of. Two trees on one slot never collide,
					// because they belong to different archetypes.
					if (slot == 4) {
						if (archetype == Archetype.INTELLECT) {
							OracleSpells.lightningStrike(player);
						} else if (archetype == Archetype.AGILITY) {
							Deadeye.activate(player);
						}

						return;
					}

					if (slot == 5) {
						if (archetype == Archetype.INTELLECT) {
							OracleSpells.magicArmaments(player);
						} else if (archetype == Archetype.AGILITY) {
							DeathMark.mark(player);
						} else if (archetype == Archetype.STRENGTH) {
							ColossusSlayer.parry(player);
						}

						return;
					}

					if (slot == 6) {
						if (archetype == Archetype.AGILITY) {
							NightForm.beginRitual(player);
						} else if (archetype == Archetype.STRENGTH) {
							TitansLeap.leap(player);
						}

						return;
					}

					switch (SubTree.of(archetype).get(slot)) {
						case PROTECTOR -> ShieldBash.execute(player);
						case SLAYER -> {
							if (ModItems.isGreatsword(player.getMainHandItem())) {
								SlayerActives.decimate(player);
							} else if (ModItems.isSword(player.getMainHandItem())) {
								SlayerActives.bladestorm(player);
							}
						}
						case CRUSHER -> {
							//? if >=1.21 {
							if (player.getMainHandItem().is(net.minecraft.world.item.Items.MACE)) {
								CrusherActives.quake(player);
							} else {
								CrusherActives.haymaker(player);
							}
							//?} else {
							/*CrusherActives.haymaker(player);
							*///?}
						}
						case MARKSMAN -> AgilityActives.trueShot(player);
						case ASSASSIN -> AgilityActives.shadowStep(player);
						case SHADOW -> AgilityActives.invisibility(player);
						case ELEMENTALIST -> SeekerSpells.castElementalist(player);
						case WIZARD -> SeekerSpells.castMissile(player);
						case PRIEST -> SeekerSpells.castHolyLight(player);
					}
		});

		Net.INSTANCE.onServerbound(WireId.SPELL_CHANNEL,
				(player, buf) -> SeekerSpells.channelFlame(player));

		Net.INSTANCE.onServerbound(WireId.RUSH, (player, buf) -> ShieldRush.execute(player));

		Net.INSTANCE.onServerbound(WireId.DISENGAGE, (player, buf) -> AgilityActives.acrobatics(player));

		Net.INSTANCE.onServerbound(WireId.NIGHT_DASH, (player, buf) -> NightForm.dash(player));

		// The greatsword is strictly two-handed: while it's in the main hand
		// the offhand is dead weight — no shields, no food, no blocks from it.
		// `UseItemCallback` returns fabric-api's mirror of `Item.use`'s return type, so it
		// follows the same 1.21.2 boundary: a bare `InteractionResult` above,
		// `InteractionResultHolder<ItemStack>` below. `UseBlockCallback` below is NOT
		// affected — it returned a plain `InteractionResult` on every version (measured on
		// fabric-api 0.116.14 and 0.155.2).
		//? if >=1.21.2 {
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) ->
				hand == net.minecraft.world.InteractionHand.OFF_HAND
						&& ModItems.isGreatsword(player.getMainHandItem())
						? net.minecraft.world.InteractionResult.FAIL
						: net.minecraft.world.InteractionResult.PASS);
		//?} else {
		/*net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) ->
				hand == net.minecraft.world.InteractionHand.OFF_HAND
						&& ModItems.isGreatsword(player.getMainHandItem())
						? net.minecraft.world.InteractionResultHolder.fail(player.getItemInHand(hand))
						: net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand)));
		*///?}
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				hand == net.minecraft.world.InteractionHand.OFF_HAND
						&& ModItems.isGreatsword(player.getMainHandItem())
						? net.minecraft.world.InteractionResult.FAIL
						: net.minecraft.world.InteractionResult.PASS);

		// Custom swing poses were deprecated (see notes/design.md — Better
		// Combat compat), but the slab still announces itself: a deep whoosh
		// under every charged greatsword swing.
		Net.INSTANCE.onServerbound(WireId.MELEE_SWING, (player, buf) -> {
					if (WeaponClass.of(player) == WeaponClass.GREATSWORD) {
						player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
								net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
								net.minecraft.sounds.SoundSource.PLAYERS, 1.1F, 0.55F);
					}

					// A conjured-sword swing with no hostile aimed at is a Blink.
					if (ModItems.isMagicSword(player.getMainHandItem())) {
						MagicArmaments.blink(player);
					}
		});

		Net.INSTANCE.onServerbound(WireId.PICK_ARCHETYPE, (player, buf) -> {
			Archetype picked = Archetype.byId(buf.readUtf()).orElse(null);

			if (picked == null) {
				return;
			}

			// One pick for now: ignore attempts to re-pick until we decide
			// whether (and at what cost) an archetype can be changed.
			if (ModState.get(player) == null) {
				ModState.set(player, picked);
				LOGGER.info("{} chose the {} archetype", player.getName().getString(), picked.id());
			}
		});

		// Fresh advancement count each login (self-heals staleness), and the
		// bank-covers-spent guard that makes any future curve retune safe.
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
				(handler, sender, server) -> {
					// A no-op on every node whose platform syncs attached state itself
					// (all of them today). It is the whole of the client's state on the
					// nodes that have no such thing — see ArchetypeStore#resyncAll.
					ArchetypeStore.INSTANCE.resyncAll(handler.player);
					SkillPoints.refreshAdvancementCount(handler.player);
					SkillPoints.ensureBankCoversSpent(handler.player);
					// A Magic Armaments channel that outlived its server hands the
					// wand back and clears the conjured weapon on the way in.
					MagicArmaments.restoreDirty(handler.player);
					// A ritual cannot survive a relog; the hour of night form
					// can and must (it is the node's whole price), so only the
					// channel is torn down here.
					NightForm.interrupt(handler.player);
					// Same rule for Deadeye: fifteen seconds is not worth
					// persisting, and a stance stamp restored without its
					// ticker would hand out free arrows until it lapsed.
					Deadeye.end(handler.player);
					// And the mark: a minute is not worth persisting, and an
					// entity id is not stable across a relog anyway — a restored
					// stamp would name whatever wears that id now.
					DeathMark.clear(handler.player);
					// And an in-flight Titan's Leap: the stamp is what waives
					// fall damage, and only a landing spends it — a leap
					// restored on a player standing safely on the ground would
					// waive every fall they ever took after it.
					TitansLeap.clear(handler.player);
				});

		// A player dying mid-channel: end it before drops so the real wand
		// returns to the inventory (dropping or kept as any item would) while the
		// conjured weapon vanishes with the channel.
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register(
				(entity, source, amount) -> {
					if (entity instanceof net.minecraft.server.level.ServerPlayer player
							&& MagicArmaments.isActive(player)) {
						MagicArmaments.end(player);
					}

					return true;
				});

		Net.INSTANCE.onServerbound(WireId.RESET_ARCHETYPE, (player, buf) -> {
			// The client only shows this button in creative, but the client
			// is not to be trusted about game mode — check it here.
			if (!player.isCreative()) {
				return;
			}

			ModState.clear(player);
			LOGGER.info("{} reset their archetype", player.getName().getString());
		});

		LOGGER.info("Archetypes initialized");
	}

	public static Identifier id(final String path) {
		/*? if >=1.21 {*/return Identifier.fromNamespaceAndPath(MOD_ID, path);
		/*?} else *///return new Identifier(MOD_ID, path);
	}
}
