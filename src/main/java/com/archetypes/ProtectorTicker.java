package com.archetypes;

import java.util.Set;

import com.archetypes.platform.ArchetypeStore;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * The Protector's two standing, shield-gated effects.
 *
 * <p>{@link ModState#BULWARK_ACTIVE} is held true exactly while a capstone
 * holder blocks. The attachment syncs to every client, where a render layer
 * draws ghost shields orbiting the player — set/removed only on change, so the
 * common case costs one boolean check per player per tick and no traffic.
 *
 * <p>Taunt lives here too, and it is a passive rather than a rider on the bash:
 * the node's promise is "holding the shield forces hostile creatures within 8
 * blocks to attack you", which is a stance, not an ability. Keeping it on the
 * bash meant a Protector could only pull a pack on a seven-second timer, and
 * the description would have had to say so.
 */
public final class ProtectorTicker {
	private ProtectorTicker() {
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
			// One clock for the whole pass: the sweep below is periodic and
			// every player should land on the same beat of it.
			boolean sweep = server.overworld().getGameTime() % Tuning.TAUNT_PERIOD_TICKS == 0;

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Set<Integer> owned = NodePurchases.owned(player, SubTree.PROTECTOR);
				boolean blocking = player.isBlocking();
				boolean should = blocking
						&& ProtectorNodes.rank(SubTree.PROTECTOR, owned,
								ProtectorNodes.Family.OMNI_BLOCK) > 0;

				final Entity target = player;
				Boolean current = ArchetypeStore.INSTANCE.get(target, ModState.BULWARK_ACTIVE);

				if (should && current == null) {
					ArchetypeStore.INSTANCE.set(target, ModState.BULWARK_ACTIVE, true);
				} else if (!should && current != null) {
					ArchetypeStore.INSTANCE.remove(target, ModState.BULWARK_ACTIVE);
				}

				if (sweep && blocking
						&& ProtectorNodes.rank(SubTree.PROTECTOR, owned,
								ProtectorNodes.Family.TAUNT) > 0) {
					taunt(player);
				}
			}
		});
	}

	/**
	 * Everything hostile in range drops what it is doing and comes for the
	 * shield. Vanilla target AI does the rest; no custom goal is involved.
	 *
	 * <p>Re-asserted on a period rather than every tick because that is all a
	 * forced target needs — a mob whose target is already the taunter is left
	 * alone entirely, which is also what keeps the angry-villager puff meaning
	 * "you just pulled this one" instead of raining continuously.
	 */
	private static void taunt(final ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();

		for (Mob mob : level.getEntitiesOfClass(Mob.class,
				player.getBoundingBox().inflate(Tuning.TAUNT_RADIUS),
				mob -> mob instanceof Enemy && mob.isAlive() && mob.getTarget() != player)) {
			mob.setTarget(player);
			level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
					mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(),
					1, 0.1, 0.1, 0.1, 0.0);
		}
	}
}
