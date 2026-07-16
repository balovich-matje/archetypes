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
		ModParticles.initialize();
		ProtectorTicker.initialize();
		SlayerCombat.initialize();
		SlayerTicker.initialize();

		PayloadTypeRegistry.serverboundPlay().register(PickArchetypePayload.TYPE, PickArchetypePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ResetArchetypePayload.TYPE, ResetArchetypePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BuyNodePayload.TYPE, BuyNodePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ShieldBashPayload.TYPE, ShieldBashPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RushPayload.TYPE, RushPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(BuyNodePayload.TYPE, (payload, context) -> context
				.server().execute(() -> {
					SubTree tree = SubTree.byId(payload.subTreeId());

					// Only spend into trees of the archetype you actually are.
					if (tree == null || ModAttachments.get(context.player()) != tree.archetype()) {
						return;
					}

					NodePurchases.buy(context.player(), tree, payload.node());
				}));

		// One ability key: the server dispatches on the mainhand. Greatsword is
		// Decimate, sword is Bladestorm, anything else falls through to the
		// bash (which requires a shield and checks that itself).
		ServerPlayNetworking.registerGlobalReceiver(ShieldBashPayload.TYPE, (payload, context) -> context
				.server().execute(() -> {
					var player = context.player();

					if (ModItems.isGreatsword(player.getMainHandItem())) {
						SlayerActives.decimate(player);
					} else if (ModItems.isSword(player.getMainHandItem())) {
						SlayerActives.bladestorm(player);
					} else {
						ShieldBash.execute(player);
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(RushPayload.TYPE, (payload, context) -> context
				.server().execute(() -> ShieldRush.execute(context.player())));

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
