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

		PayloadTypeRegistry.clientboundPlay().register(PassiveProcPayload.TYPE, PassiveProcPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PickArchetypePayload.TYPE, PickArchetypePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ResetArchetypePayload.TYPE, ResetArchetypePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BuyNodePayload.TYPE, BuyNodePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ActiveAbilityPayload.TYPE, ActiveAbilityPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SpellChannelPayload.TYPE, SpellChannelPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MeleeSwingPayload.TYPE, MeleeSwingPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RushPayload.TYPE, RushPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DisengagePayload.TYPE, DisengagePayload.CODEC);

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

					if (archetype == null || payload.slot() < 0 || payload.slot() >= 3) {
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
