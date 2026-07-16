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

		PayloadTypeRegistry.clientboundPlay().register(PassiveProcPayload.TYPE, PassiveProcPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PickArchetypePayload.TYPE, PickArchetypePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ResetArchetypePayload.TYPE, ResetArchetypePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BuyNodePayload.TYPE, BuyNodePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ShieldBashPayload.TYPE, ShieldBashPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SlayerAbilityPayload.TYPE, SlayerAbilityPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MeleeSwingPayload.TYPE, MeleeSwingPayload.CODEC);
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

		// One key per active now that the cooldown bar shows them separately.
		// The bash key is bash alone; the Slayer key still dispatches on the
		// mainhand, since the two capstones are mutually exclusive anyway.
		ServerPlayNetworking.registerGlobalReceiver(ShieldBashPayload.TYPE, (payload, context) -> context
				.server().execute(() -> ShieldBash.execute(context.player())));

		ServerPlayNetworking.registerGlobalReceiver(SlayerAbilityPayload.TYPE, (payload, context) -> context
				.server().execute(() -> {
					var player = context.player();

					if (ModItems.isGreatsword(player.getMainHandItem())) {
						SlayerActives.decimate(player);
					} else if (ModItems.isSword(player.getMainHandItem())) {
						SlayerActives.bladestorm(player);
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(RushPayload.TYPE, (payload, context) -> context
				.server().execute(() -> ShieldRush.execute(context.player())));

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

		// A combat swing began client-side; bump the synced counter that every
		// client turns into the matching pose. The class is derived here, not
		// trusted from the wire.
		ServerPlayNetworking.registerGlobalReceiver(MeleeSwingPayload.TYPE, (payload, context) -> context
				.server().execute(() -> {
					var player = context.player();
					WeaponClass weapon = WeaponClass.of(player);

					if (weapon == WeaponClass.NONE) {
						return;
					}

					var target = (net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player;
					Integer previous = target.getAttached(ModAttachments.MELEE_SWING);
					int sequence = previous == null ? 1 : ((previous >> 2) + 1) & 0x3FFF;
					target.setAttached(ModAttachments.MELEE_SWING,
							sequence << 2 | weapon.ordinal());
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
