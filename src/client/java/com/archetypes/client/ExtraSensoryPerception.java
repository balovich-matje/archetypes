package com.archetypes.client;

import java.util.List;

import com.archetypes.NightForm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Extra Sensory Perception's colours, and the one question its two client
 * mixins ask: is this entity on the local player's roster, and in which of the
 * two colours.
 *
 * <p>The rosters are rebuilt server-side twice a second and synced to their
 * owner only, so nothing here scans the world — it is a membership test against
 * a list that is never longer than the creatures inside 32 blocks.
 */
public final class ExtraSensoryPerception {
	/** Players read RED (author's spec): the thing that can plan against you is
	 * never the same colour as the thing that cannot. */
	private static final int PLAYER_COLOR = ARGB.opaque(0xFF2A2A);
	/** Everything else in a cold violet — far enough from red to be told apart
	 * at a glance and from vanilla's white team outline. */
	private static final int CREATURE_COLOR = ARGB.opaque(0x9A5CFF);

	private ExtraSensoryPerception() {
	}

	/** The outline this entity should wear for the local player, or
	 * {@link EntityRenderState#NO_OUTLINE} when it is not sensed. */
	public static int outlineColor(final Entity entity) {
		Player self = Minecraft.getInstance().player;

		if (self == null || entity == self || !NightForm.isActive(self)) {
			return EntityRenderState.NO_OUTLINE;
		}

		int id = entity.getId();

		if (contains(NightForm.sensedPlayers(self), id)) {
			return PLAYER_COLOR;
		}

		return contains(NightForm.sensed(self), id) ? CREATURE_COLOR
				: EntityRenderState.NO_OUTLINE;
	}

	/** Whether this entity is sensed at all — the question the visibility hook
	 * asks before it lets a walled-off creature through the occlusion test. */
	public static boolean senses(final Entity entity) {
		return outlineColor(entity) != EntityRenderState.NO_OUTLINE;
	}

	/** Indexed rather than {@code List.contains}, which would box the id for
	 * every entity in the level, every frame. */
	private static boolean contains(final List<Integer> ids, final int id) {
		for (int i = 0; i < ids.size(); i++) {
			if (ids.get(i) == id) {
				return true;
			}
		}

		return false;
	}
}
