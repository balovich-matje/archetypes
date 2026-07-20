package com.archetypes;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Archetypes implements ModInitializer {
	public static final String MOD_ID = "archetypes";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModAttachments.initialize();
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

		PayloadTypeRegistry.clientboundPlay().register(PassiveProcPayload.TYPE, PassiveProcPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ParrySwingPayload.TYPE, ParrySwingPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PickArchetypePayload.TYPE, PickArchetypePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ResetArchetypePayload.TYPE, ResetArchetypePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BuyNodePayload.TYPE, BuyNodePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ActiveAbilityPayload.TYPE, ActiveAbilityPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SpellChannelPayload.TYPE, SpellChannelPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MeleeSwingPayload.TYPE, MeleeSwingPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RushPayload.TYPE, RushPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DisengagePayload.TYPE, DisengagePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(NightDashPayload.TYPE, NightDashPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ParryPayload.TYPE, ParryPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(BuyNodePayload.TYPE, (payload, context) -> context
				.server().execute(() -> {
					SubTree tree = SubTree.byId(payload.subTreeId());

					// Only spend into trees of the archetype you actually are.
					if (tree == null || ModAttachments.get(context.player()) != tree.archetype()) {
						return;
					}

					NodePurchases.buy(context.player(), tree, payload.node());
				}));

		// The three ability keys are slots, one per sub-tree in screen order;
		// what a slot casts depends on the archetype. Strength trees keep
		// their internal weapon dispatch (the capstone pairs are exclusive).
		ServerPlayNetworking.registerGlobalReceiver(ActiveAbilityPayload.TYPE, (payload, context) -> context
				.server().execute(() -> {
					var player = context.player();
					Archetype archetype = ModAttachments.get(player);

					if (archetype == null || payload.slot() < 0 || payload.slot() >= 7) {
						return;
					}

					// Slot 3 is the capstone key — Elementalist-only for now,
					// with room for more trees in later versions.
					if (payload.slot() == 3) {
						if (archetype == Archetype.INTELLECT) {
							SeekerSpells.castElementalistCapstone(player);
						}

						return;
					}

					// Slots 4-6 are the epic actives, and archetypes share them:
					// an epic tree takes slot 4 + N, where N is its base tree's
					// place in SubTree.of. Two trees on one slot never collide,
					// because they belong to different archetypes.
					if (payload.slot() == 4) {
						if (archetype == Archetype.INTELLECT) {
							OracleSpells.lightningStrike(player);
						} else if (archetype == Archetype.AGILITY) {
							Deadeye.activate(player);
						}

						return;
					}

					if (payload.slot() == 5) {
						if (archetype == Archetype.INTELLECT) {
							OracleSpells.magicArmaments(player);
						} else if (archetype == Archetype.AGILITY) {
							DeathMark.mark(player);
						}

						return;
					}

					if (payload.slot() == 6) {
						if (archetype == Archetype.AGILITY) {
							NightForm.beginRitual(player);
						} else if (archetype == Archetype.STRENGTH) {
							TitansLeap.leap(player);
						}

						return;
					}

					switch (SubTree.of(archetype).get(payload.slot())) {
						case PROTECTOR -> ShieldBash.execute(player);
						case SLAYER -> {
							if (ModItems.isGreatsword(player.getMainHandItem())) {
								SlayerActives.decimate(player);
							} else if (ModItems.isSword(player.getMainHandItem())) {
								SlayerActives.bladestorm(player);
							}
						}
						case CRUSHER -> {
							if (player.getMainHandItem().is(net.minecraft.world.item.Items.MACE)) {
								CrusherActives.quake(player);
							} else {
								CrusherActives.haymaker(player);
							}
						}
						case MARKSMAN -> AgilityActives.trueShot(player);
						case ASSASSIN -> AgilityActives.shadowStep(player);
						case SHADOW -> AgilityActives.invisibility(player);
						case ELEMENTALIST -> SeekerSpells.castElementalist(player);
						case WIZARD -> SeekerSpells.castMissile(player);
						case PRIEST -> SeekerSpells.castHolyLight(player);
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(SpellChannelPayload.TYPE, (payload, context) -> context
				.server().execute(() -> SeekerSpells.channelFlame(context.player())));

		ServerPlayNetworking.registerGlobalReceiver(RushPayload.TYPE, (payload, context) -> context
				.server().execute(() -> ShieldRush.execute(context.player())));

		ServerPlayNetworking.registerGlobalReceiver(DisengagePayload.TYPE, (payload, context) -> context
				.server().execute(() -> AgilityActives.acrobatics(context.player())));

		ServerPlayNetworking.registerGlobalReceiver(NightDashPayload.TYPE, (payload, context) -> context
				.server().execute(() -> NightForm.dash(context.player())));

		// Parry: attack and block reported together. The client consumes no
		// clicks to say so, so a press that buys nothing costs nothing either.
		ServerPlayNetworking.registerGlobalReceiver(ParryPayload.TYPE, (payload, context) -> context
				.server().execute(() -> ColossusSlayer.open(context.player())));

		// The greatsword is strictly two-handed: while it's in the main hand
		// the offhand is dead weight — no shields, no food, no blocks from it.
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) ->
				hand == net.minecraft.world.InteractionHand.OFF_HAND
						&& ModItems.isGreatsword(player.getMainHandItem())
						? net.minecraft.world.InteractionResult.FAIL
						: net.minecraft.world.InteractionResult.PASS);
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				hand == net.minecraft.world.InteractionHand.OFF_HAND
						&& ModItems.isGreatsword(player.getMainHandItem())
						? net.minecraft.world.InteractionResult.FAIL
						: net.minecraft.world.InteractionResult.PASS);

		// Custom swing poses were deprecated (see notes/design.md — Better
		// Combat compat), but the slab still announces itself: a deep whoosh
		// under every charged greatsword swing.
		ServerPlayNetworking.registerGlobalReceiver(MeleeSwingPayload.TYPE, (payload, context) -> context
				.server().execute(() -> {
					var player = context.player();

					if (WeaponClass.of(player) == WeaponClass.GREATSWORD) {
						player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
								net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
								net.minecraft.sounds.SoundSource.PLAYERS, 1.1F, 0.55F);
					}

					// A conjured-sword swing with no hostile aimed at is a Blink.
					if (ModItems.isMagicSword(player.getMainHandItem())) {
						MagicArmaments.blink(player);
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(PickArchetypePayload.TYPE, (payload, context) -> {
			Archetype picked = Archetype.byId(payload.archetypeId()).orElse(null);

			if (picked == null) {
				return;
			}

			context.server().execute(() -> {
				// One pick for now: ignore attempts to re-pick until we decide
				// whether (and at what cost) an archetype can be changed.
				if (ModAttachments.get(context.player()) == null) {
					ModAttachments.set(context.player(), picked);
					LOGGER.info("{} chose the {} archetype", context.player().getName().getString(), picked.id());
				}
			});
		});

		// Fresh advancement count each login (self-heals staleness), and the
		// bank-covers-spent guard that makes any future curve retune safe.
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
				(handler, sender, server) -> {
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

		ServerPlayNetworking.registerGlobalReceiver(ResetArchetypePayload.TYPE, (payload, context) -> context
				.server().execute(() -> {
					// The client only shows this button in creative, but the client
					// is not to be trusted about game mode — check it here.
					if (!context.player().isCreative()) {
						return;
					}

					ModAttachments.clear(context.player());
					LOGGER.info("{} reset their archetype", context.player().getName().getString());
				}));

		LOGGER.info("Archetypes initialized");
	}

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
