package com.archetypes.client;

import java.util.List;

import com.archetypes.DeathMark;
import com.archetypes.NemesisAssassinNodes;
import com.archetypes.NightForm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * The two things that outline a creature for the local player — Extra Sensory
 * Perception's rosters and the Nemesis Assassin's Stalk — and the one question
 * the two client mixins ask: should this entity be outlined, and in what
 * colour.
 *
 * <p>Both answers are membership tests against synced state, never world scans.
 * ESP's rosters are rebuilt server-side twice a second and synced to their owner
 * only; the mark is a single id on the marked body itself, synced to everyone.
 *
 * <p><b>Precedence: the mark wins.</b> A vampire assassin can hold both, and a
 * mark that disappeared into a roster of thirty violet outlines would not be a
 * mark.
 */
public final class ExtraSensoryPerception {
	/** Players read RED (author's spec): the thing that can plan against you is
	 * never the same colour as the thing that cannot. */
	private static final int PLAYER_COLOR = ARGB.opaque(0xFF2A2A);
	/** Everything else in a cold violet — far enough from red to be told apart
	 * at a glance and from vanilla's white team outline. */
	private static final int CREATURE_COLOR = ARGB.opaque(0x9A5CFF);
	/** The mark: bone white, the one colour neither ESP tone is near. */
	private static final int MARK_COLOR = ARGB.opaque(0xFFF3D0);

	private ExtraSensoryPerception() {
	}

	/** The outline this entity should wear for the local player, or
	 * {@link EntityRenderState#NO_OUTLINE} when nothing marks or senses it. */
	public static int outlineColor(final Entity entity) {
		Player self = Minecraft.getInstance().player;

		if (self == null || entity == self) {
			return EntityRenderState.NO_OUTLINE;
		}

		// Stalk: the mark is outlined through walls at any distance, and only
		// for the assassin who named it.
		if (DeathMark.isMarkedBy(entity, self)
				&& DeathMark.rank(self, NemesisAssassinNodes.Family.STALK) > 0) {
			return MARK_COLOR;
		}

		if (!NightForm.isActive(self)) {
			return EntityRenderState.NO_OUTLINE;
		}

		int id = entity.getId();

		if (contains(NightForm.sensedPlayers(self), id)) {
			return PLAYER_COLOR;
		}

		return contains(NightForm.sensed(self), id) ? CREATURE_COLOR
				: EntityRenderState.NO_OUTLINE;
	}

	/** Whether this entity is sensed or marked at all — the question the
	 * visibility hook asks before it lets a walled-off creature through the
	 * occlusion test. Stalk's "through walls" is this call, not a second one. */
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
