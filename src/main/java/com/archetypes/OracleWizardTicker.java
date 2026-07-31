package com.archetypes;

//? if fabric {
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
//?}
// STAGE 6 — the loader-event helpers live in `com.archetypes.platform`, the one package
// allowed to name loader API (conventions §5g). Only this import and the registration line
// below fork; the tick body is one implementation on all seven nodes.
//? if fabric {
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
//?} elif neoforge {
/*import com.archetypes.platform.NeoForgeEvents;
*///?} elif forge {
/*import com.archetypes.platform.ForgeEvents;
*///?}
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;

/** Drives the Magic Armaments channel: upkeep, its grants, and the guards that
 * end it, for every Seeker each tick (mirrors SeekerTicker's shape). */
public final class OracleWizardTicker {
	private OracleWizardTicker() {
	}

	public static void initialize() {
		// A conjured weapon must never exist as a world item: a Q-drop, a
		// broken container, or a chunk saved before this guard shipped all
		// surface here, and the entity is voided before anyone reaches it.
		// (Death drops don't: the death hook ends the channel first.)
		// A loader helper fires its consumer once per entity ADDED TO A SERVER LEVEL, with the
		// level — early enough that `discard()` here means the item is never reachable. Firing
		// on the client side too would be harmless for this body but is not the contract.
		//? if fabric {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
		//?} elif neoforge {
		/*NeoForgeEvents.entityLoad((entity, level) -> {
		*///?} elif forge {
		/*ForgeEvents.entityLoad((entity, level) -> {
		*///?}
			if (entity instanceof ItemEntity item && ModItems.isSummoned(item.getItem())) {
				item.discard();
			}
		});

		// Registration only; the body below is shared. A loader helper fires its consumer ONCE
		// per server tick, at the END of it, with the `MinecraftServer` — the END_SERVER_TICK
		// contract, which is what R-20 says a re-rooted event has to reproduce rather than
		// merely fire somewhere plausible.
		//? if fabric {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
		//?} elif neoforge {
		/*NeoForgeEvents.endServerTick(server -> {
		*///?} elif forge {
		/*ForgeEvents.endServerTick(server -> {
		*///?}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (ModState.get(player) == Archetype.INTELLECT) {
					MagicArmaments.tick(player);
				}
			}
		});
	}
}
