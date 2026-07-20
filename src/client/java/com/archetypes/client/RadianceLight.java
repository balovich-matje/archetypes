package com.archetypes.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.archetypes.RadianceAura;
import com.archetypes.Tuning;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/**
 * Aura of Radiance's actual light: a glowstone-strength block light that
 * follows whoever is glowing, placed <em>only inside the client's own copy of
 * the level</em>.
 *
 * <h2>Why client-side, and why this is airtight</h2>
 * The author asked for light level {@value com.archetypes.Tuning#RADIANCE_LIGHT_LEVEL}
 * on the player, and Minecraft still has no dynamic entity lighting: the only
 * thing that emits block light is a block. Doing that on the server cannot be
 * made safe — a chunk that autosaves while the aura is up, followed by a
 * process kill before any cleanup runs, leaves an invisible permanent
 * {@code minecraft:light} in the saved world, and no sweep can find what it
 * does not know about.
 *
 * <p>So the block is never placed on the server. This class runs on the client
 * tick and writes into {@link ClientLevel}, which is built from packets when a
 * world is joined and thrown away when it is left. It is never serialised,
 * never saved, and the server is never told. A hard kill at any instant leaves
 * nothing behind, because the only thing that was ever modified died with the
 * process. That is the whole reason this is client-side rather than a tracked
 * server block with sweeps: there is no window to close.
 *
 * <p>The approach is LambDynamicLights' — a synced flag re-lights the client
 * level around the source — but it goes through the vanilla light engine
 * rather than a render-time override, so falloff, occlusion and shadow are the
 * real thing. {@code ClientLevel.setBlock} funnels into the same
 * {@code LevelChunk.setBlockState} the server's does, which calls
 * {@code checkBlock} on the client's own {@code LevelLightEngine};
 * {@code ClientLevel.tickEntities} runs those updates every tick.
 *
 * <h2>Rules this obeys</h2>
 * <ul>
 * <li>Air only. A light is placed only where the client currently sees air, and
 *     removed only where the client still sees exactly our light state — so a
 *     real block arriving from the server is never overwritten by the placement
 *     nor stomped by the cleanup.</li>
 * <li>Update flags {@code UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE}: redraw and
 *     re-light, but no neighbour updates and no shape updates, so a fake block
 *     cannot knock a torch off a wall or start client-side sand falling.</li>
 * <li>A new {@link ClientLevel} (join, dimension change, respawn) drops the
 *     bookkeeping without touching blocks — the old level object is already
 *     gone.</li>
 * <li>Nothing is placed while the local player is holding a light item.
 *     {@code LightBlock.getShape} is a full cube for exactly that holder, and
 *     the local player is the only entity whose collision the client
 *     simulates, so this is the one case where a fake block could be stood
 *     on.</li>
 * </ul>
 *
 * <h2>Why the light has to know about itself</h2>
 * The seat is picked by asking whether a block is air, and the light we placed
 * last tick is <em>not</em> air. For a player standing on flat ground the chest
 * block and the feet block are the same block — a 1.8-tall player standing at
 * y=64.0 has its middle at 64.9, which floors to the same 64 the feet are in —
 * so a seat search that only accepts air found the seat on one tick, found our
 * own light in it on the next, fell through to the feet block, found the same
 * light there too, gave up, and the light was taken back. On the tick after
 * that the block was air again and the light returned. That is a full on/off
 * strobe at ten hertz while standing perfectly still, and a one-block-per-tick
 * hop between chest and feet whenever those two blocks differ. Hence
 * {@link #free}: the block we are already holding for <em>this</em> player
 * counts as available to that same player, and to nobody else.
 *
 * <p>On top of that the seat only moves when it has to. Vanilla's light data is
 * consistent every frame — {@code LevelLightEngine.runLightUpdates} drains its
 * decrease and increase queues to exhaustion and only then publishes a fresh
 * {@code visibleSectionData}, so a move can never show a half-propagated
 * world — but every move still wipes and refills a 14-block sphere of block
 * light and dirties every section it touched. Keeping the light where it is
 * while it is still within a block of where we would put it costs at most a
 * block and a half of lag on a light that carries fourteen, and it removes the
 * churn from a player jittering across a block boundary.
 */
public final class RadianceLight {
	private static final BlockState LIGHT = Blocks.LIGHT.defaultBlockState()
			.setValue(LightBlock.LEVEL, Tuning.RADIANCE_LIGHT_LEVEL);
	/** Redraw and re-light, but no neighbour or shape updates. */
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	/** How far the player may drift from the light before it follows, in blocks
	 * along each axis. One means the light moves every second block walked
	 * instead of every block, and a player jittering across a boundary does not
	 * move it at all. */
	private static final int SLACK = 1;
	/** Ticks a light is left alone after being placed before {@link
	 * Placement#hold} starts checking that its light data survived, and the
	 * gap between one failed check and the next. Long enough that a frame has
	 * certainly run and published the light, short enough that a stomped light
	 * comes back within half a second. */
	private static final int SETTLE_TICKS = 10;

	/** Where each glowing player's light currently sits. Keyed by player uuid
	 * so a player who walks out of tracking range still gets cleaned up. */
	private static final Map<UUID, Placement> PLACED = new HashMap<>();
	private static ClientLevel tracked;

	private RadianceLight() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientLevel level = client.level;

			if (level != tracked) {
				// The level these lights lived in no longer exists; there is
				// nothing to undo, only bookkeeping to drop.
				PLACED.clear();
				tracked = level;
			}

			if (level == null) {
				return;
			}

			boolean allowed = client.player == null
					|| (!client.player.getMainHandItem().is(Items.LIGHT)
							&& !client.player.getOffhandItem().is(Items.LIGHT));

			Map<UUID, BlockPos> want = new HashMap<>();

			for (AbstractClientPlayer player : level.players()) {
				if (allowed && RadianceAura.isActive(player)) {
					Placement held = PLACED.get(player.getUUID());
					BlockPos seat = seat(level, player, held == null ? null : held.pos);

					if (seat != null) {
						want.put(player.getUUID(), seat);
					}
				}
			}

			// Anyone who stopped glowing, left tracking range or logged out
			// leaves a light behind that is ours to take back. A seat that only
			// moved is taken back here too, and re-placed below; both edits land
			// in the same tick, so the light engine sees the removal and the
			// placement in one batch and no frame can fall between them.
			PLACED.entrySet().removeIf(placed -> {
				if (placed.getValue().pos.equals(want.get(placed.getKey()))) {
					return false;
				}

				clear(level, placed.getValue().pos);
				return true;
			});

			want.forEach((id, seat) -> {
				Placement held = PLACED.get(id);

				if (held != null) {
					held.hold(level);
				} else if (level.getBlockState(seat).isAir()) {
					level.setBlock(seat, LIGHT, FLAGS);
					relight(level, seat);
					PLACED.put(id, new Placement(seat));
				}
			});
		});
	}

	/**
	 * The block this player's light sits in: chest height if that is free, else
	 * the feet block, else nowhere — a player buried in blocks simply does not
	 * glow. Free means air, or the light we are already holding for this same
	 * player; see the class notes on why leaving our own block out of that test
	 * strobed the aura. The answer sticks to where the light already is while
	 * that is within {@link #SLACK} of it, so walking does not move the light
	 * once per block.
	 */
	private static @Nullable BlockPos seat(final ClientLevel level,
			final AbstractClientPlayer player, final @Nullable BlockPos held) {
		BlockPos chest = BlockPos.containing(player.getX(),
				player.getY() + player.getBbHeight() * 0.5, player.getZ());
		BlockPos seat = null;

		if (free(level, chest, held)) {
			seat = chest;
		} else {
			BlockPos feet = player.blockPosition();

			if (free(level, feet, held)) {
				seat = feet;
			}
		}

		if (seat == null) {
			return null;
		}

		return held != null && ours(level, held) && within(held, seat, SLACK) ? held : seat;
	}

	/** Somewhere this player's light may sit: air, or the block already holding
	 * this player's light. Another player's light, or anyone else's block, is
	 * not free. */
	private static boolean free(final ClientLevel level, final BlockPos pos, final @Nullable BlockPos held) {
		return pos.equals(held) ? ours(level, pos) : level.getBlockState(pos).isAir();
	}

	/** Whether the client still sees exactly the state we put here. */
	private static boolean ours(final ClientLevel level, final BlockPos pos) {
		return level.getBlockState(pos) == LIGHT;
	}

	/** Chebyshev distance, so "no further than {@code slack} along any axis". */
	private static boolean within(final BlockPos a, final BlockPos b, final int slack) {
		return Math.abs(a.getX() - b.getX()) <= slack
				&& Math.abs(a.getY() - b.getY()) <= slack
				&& Math.abs(a.getZ() - b.getZ()) <= slack;
	}

	/** Take a light back, but only if it is still ours. */
	private static void clear(final ClientLevel level, final BlockPos pos) {
		if (ours(level, pos)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
			relight(level, pos);
		}
	}

	/**
	 * Rebuild the section meshes the change can reach. Terrain light is baked
	 * into a section's vertices. The light engine dirties the sections it
	 * touched by itself — {@code LayerLightSectionStorage.setStoredLevel} files
	 * every block it writes under {@code sectionsAffectedByLightUpdates}, and
	 * {@code swapSectionMap} hands each of those to
	 * {@code ClientChunkCache.onLightUpdate} — so this is belt to that braces
	 * rather than the only thing keeping the terrain honest. It costs one flag
	 * per section and it only runs when a block actually changed. The reach is
	 * the emission level, which is the furthest block light can travel from
	 * here.
	 */
	private static void relight(final ClientLevel level, final BlockPos pos) {
		int reach = Tuning.RADIANCE_LIGHT_LEVEL;
		level.setSectionRangeDirty(
				SectionPos.blockToSectionCoord(pos.getX() - reach),
				SectionPos.blockToSectionCoord(pos.getY() - reach),
				SectionPos.blockToSectionCoord(pos.getZ() - reach),
				SectionPos.blockToSectionCoord(pos.getX() + reach),
				SectionPos.blockToSectionCoord(pos.getY() + reach),
				SectionPos.blockToSectionCoord(pos.getZ() + reach));
	}

	/**
	 * One player's standing light, and the watch kept on it.
	 *
	 * <p>The block itself is safe — the server does not know it exists, so
	 * nothing ever sends a block update for it — but the client's <em>light
	 * data</em> is not. {@code ClientboundLightUpdatePacket} and a re-sent
	 * chunk both funnel into {@code ClientPacketListener.applyLightData}, which
	 * hands whole sections of server-computed block light to
	 * {@code LevelLightEngine.queueSectionData}; that data was computed without
	 * our block in the world, so it overwrites our glow and nothing re-derives
	 * it. Re-placing the block would not help, because the block is still
	 * there. So instead: if the seat still holds our light but the light engine
	 * no longer reports our level there, hand the position back to
	 * {@code checkBlock} and let it propagate again.
	 */
	private static final class Placement {
		private final BlockPos pos;
		/** Ticks until the next check; the light is left alone until it has
		 * certainly been through a {@code ClientLevel.update}. */
		private int settle = SETTLE_TICKS;

		private Placement(final BlockPos pos) {
			this.pos = pos;
		}

		private void hold(final ClientLevel level) {
			if (this.settle > 0) {
				this.settle--;
				return;
			}

			if (!ours(level, this.pos)) {
				// Something real took the seat. Leave it alone; the seat search
				// will move on next tick and clear() will not touch it.
				return;
			}

			if (level.getLightEngine().getLayerListener(LightLayer.BLOCK)
					.getLightValue(this.pos) >= Tuning.RADIANCE_LIGHT_LEVEL) {
				return;
			}

			level.getLightEngine().checkBlock(this.pos);
			relight(level, this.pos);
			this.settle = SETTLE_TICKS;
		}
	}
}
