package com.archetypes;

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

/**
 * Keeps the Assassin's dagger-stance modifier (Lightfoot's feet) in step
 * with what's in the main hand, and runs the Deadeye stance. Shadow Flurry's
 * extra strikes used to be delivered here — the capstone is one triple-weight
 * strike now.
 */
public final class AgilityTicker {
	private static final net.minecraft.resources.Identifier LIGHTFOOT_ID =
			Archetypes.id("lightfoot");

	private AgilityTicker() {
	}

	public static void initialize() {
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
				// The stance's own beat: the effect re-assert, the still test
				// and the lapse. Run for every player, not just Cutpurses —
				// the strand-guard inside it is what ends a stance whose
				// archetype went away (NightFormTicker's lesson).
				Deadeye.tick(player);
				// The mark's own beat: the lapse, the smoke tell, Stalk and
				// Carrier. Same reason it runs for every player — the guards
				// inside it are what drop a mark whose owner lost the node.
				DeathMark.tick(player);

				boolean dagger = ModItems.isDagger(player.getMainHandItem());
				var owned = NodePurchases.owned(player, SubTree.ASSASSIN);
				stance(player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED),
						LIGHTFOOT_ID, dagger,
						Tuning.LIGHTFOOT_PER_RANK
								* AssassinNodes.rank(SubTree.ASSASSIN, owned, AssassinNodes.Family.LIGHTFOOT));
			}
		});
	}

	/** Keep a transient stance modifier in step with rank and hand. */
	private static void stance(final net.minecraft.world.entity.ai.attributes.AttributeInstance attribute,
			final net.minecraft.resources.Identifier id, final boolean active, final double value) {
		if (attribute == null) {
			return;
		}

		boolean should = active && value > 0.0;
		//? if >=1.21 {
		boolean has = attribute.hasModifier(id);

		if (should && !has) {
			attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
					id, value, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else if (!should && has) {
			attribute.removeModifier(id);
		}
		//?} else {
		/*boolean has = LegacyAttributes.has(attribute, id);

		if (should && !has) {
			attribute.addTransientModifier(LegacyAttributes.modifier(
					id, value, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else if (!should && has) {
			LegacyAttributes.remove(attribute, id);
		}
		*///?}
	}
}
