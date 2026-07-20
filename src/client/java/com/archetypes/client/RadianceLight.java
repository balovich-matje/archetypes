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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

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
 */
public final class RadianceLight {
	private static final BlockState LIGHT = Blocks.LIGHT.defaultBlockState()
			.setValue(LightBlock.LEVEL, Tuning.RADIANCE_LIGHT_LEVEL);
	/** Redraw and re-light, but no neighbour or shape updates. */
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	/** Where each glowing player's light currently sits. Keyed by player uuid
	 * so a player who walks out of tracking range still gets cleaned up. */
	private static final Map<UUID, BlockPos> PLACED = new HashMap<>();
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
					BlockPos seat = seat(level, player);

					if (seat != null) {
						want.put(player.getUUID(), seat);
					}
				}
			}

			// Anyone who stopped glowing, left tracking range or logged out
			// leaves a light behind that is ours to take back.
			PLACED.entrySet().removeIf(placed -> {
				if (placed.getValue().equals(want.get(placed.getKey()))) {
					return false;
				}

				clear(level, placed.getValue());
				return true;
			});

			want.forEach((id, seat) -> {
				if (!PLACED.containsKey(id) && level.getBlockState(seat).isAir()) {
					level.setBlock(seat, LIGHT, FLAGS);
					relight(level, seat);
					PLACED.put(id, seat);
				}
			});
		});
	}

	/** The block the light sits in: chest height if that is air, else the feet
	 * block, else nowhere — a player buried in blocks simply does not glow. */
	private static BlockPos seat(final ClientLevel level, final AbstractClientPlayer player) {
		BlockPos chest = BlockPos.containing(player.getX(),
				player.getY() + player.getBbHeight() * 0.5, player.getZ());

		if (level.getBlockState(chest).isAir()) {
			return chest;
		}

		BlockPos feet = player.blockPosition();
		return level.getBlockState(feet).isAir() ? feet : null;
	}

	/** Take a light back, but only if it is still ours. */
	private static void clear(final ClientLevel level, final BlockPos pos) {
		if (level.getBlockState(pos) == LIGHT) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
			relight(level, pos);
		}
	}

	/**
	 * Rebuild the section meshes the change can reach. Terrain light is baked
	 * into a section's vertices, and vanilla's own {@code setBlocksDirty} only
	 * dirties the sections touching the changed block — enough for a torch
	 * whose light packet follows from the server, not for a light nothing else
	 * will ever announce. The reach is the emission level, which is the
	 * furthest block light can travel from here.
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
}
