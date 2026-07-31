package com.archetypes.client;

import java.util.List;

import com.archetypes.DeathMark;
import com.archetypes.NemesisAssassinNodes;
import com.archetypes.NightForm;

import net.minecraft.client.Minecraft;
// STAGE 4 — `EntityRenderState` is `>=1.21.11`, and with it the single field that was both
// the ticket into the outline pass and the colour drawn. Below the boundary those are TWO
// channels and the mixin side is rebuilt by hand (see LevelRendererMixin); on THIS side the
// only thing that moves is the name of the "no outline" sentinel. It is a compile-time
// constant int with the value 0 (`javap -c -constants` on the 1.21.11 jar), so the four
// forks below emit exactly the instruction the shared arm always emitted, and the two arms
// cannot drift apart in value — only in spelling.
//? if >=1.21.11 {
import net.minecraft.client.renderer.entity.state.EntityRenderState;
//?}
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * The two things that outline a creature for the local player — Extra Sensory
 * Perception's rosters and the Nemesis Assassin's Death Mark — and the two
 * questions the client mixins ask about them: what colour does this entity
 * wear ({@link #outlineColor}), and does that outline reach through walls
 * ({@link #piercesWalls}).
 *
 * <p>Both answers are membership tests against synced state, never world scans.
 * ESP's rosters are rebuilt server-side twice a second and synced to their owner
 * only; the mark is a single id on the marked body itself, synced to everyone.
 *
 * <h2>Colour and occlusion are two questions, not one</h2>
 * They used to be one method, which quietly welded Stalk's perk to the mark's
 * colour: no Stalk meant no outline at all. They are split because the mark
 * alone earns the RED — wearing an assassin's name is the whole reason to be
 * lit up — while seeing that red THROUGH terrain is what Stalk is bought for.
 * A marked target with no Stalk behind it is outlined exactly when it is
 * genuinely visible; only Stalk (and ESP, whose entire point is sensing what
 * you cannot see) is exempt from the occlusion test.
 *
 * <h2>Precedence: the mark wins, over our own outlines and over vanilla's</h2>
 * A vampire assassin can hold both trees, and a mark that disappeared into a
 * roster of thirty violet outlines would not be a mark — so the mark is tested
 * first here and no ESP colour can be reached for a marked body.
 *
 * <p>It also beats everything vanilla can paint. The Glowing effect and a
 * scoreboard team colour reach the renderer through one field,
 * {@code EntityRenderState.outlineColor}, written once per frame in
 * {@code EntityRenderer.extractRenderState} as
 * {@code appearsGlowing ? ARGB.opaque(entity.getTeamColor()) : 0} — and
 * {@code EntityRendererMixin} overwrites that field at the TAIL of the same
 * method, after vanilla's only write and before anything reads it. Nothing
 * downstream recomputes it: the submit path just passes {@code outlineColor}
 * along, and a non-zero value is both the ticket into the outline pass and the
 * colour that comes out. Glowing, team colours and Umbral Sight's borrowed
 * Glowing therefore all lose to the red.
 *
 * <p><b>Below 1.21.11 that one field does not exist and the precedence has to be
 * rebuilt from two separate channels</b> — membership from
 * {@code Minecraft.shouldEntityAppearGlowing(Entity)} and colour from
 * {@code Entity.getTeamColor()}, the two calls {@code LevelRenderer.renderLevel}
 * makes back to back before it hands the entity to the outline buffer source.
 * {@code LevelRendererMixin} wraps exactly those two, scoped to that one method,
 * and answers this class on both — so the rule above holds unchanged: our colour
 * replaces the team colour outright, and an entity we neither mark nor sense
 * keeps vanilla's glow exactly as it was. What is NOT rebuilt is the wall-piercing
 * exemption: {@link #piercesWalls} has no caller below 26.2, because
 * {@code LevelExtractor} is 26.2-only (design §4.3).
 */
public final class ExtraSensoryPerception {
	/** Players read AMBER: the thing that can plan against you is never the same
	 * colour as the thing that cannot. It was red until Death Mark took red for
	 * itself — two reds a shade apart, seen through the blurred outline pass,
	 * are one colour, and the mark is the one that has to be unmistakable. */
	/*? if >=1.21 {*/private static final int PLAYER_COLOR = ARGB.opaque(0xFFB300);
	/*?} else *///private static final int PLAYER_COLOR = archetypes$opaque(0xFFB300);
	/** Everything else in a cold violet — far enough from red to be told apart
	 * at a glance and from vanilla's white team outline. */
	/*? if >=1.21 {*/private static final int CREATURE_COLOR = ARGB.opaque(0x9A5CFF);
	/*?} else *///private static final int CREATURE_COLOR = archetypes$opaque(0x9A5CFF);
	/**
	 * The mark: arterial red, the one colour that has to read as danger rather
	 * than as information. Nothing else in this class is within a hue of it —
	 * ESP's two tones are amber and violet, either side of red on the wheel —
	 * so a red outline means a mark and only ever a mark.
	 */
	/*? if >=1.21 {*/private static final int MARK_COLOR = ARGB.opaque(0xD10000);
	/*?} else *///private static final int MARK_COLOR = archetypes$opaque(0xD10000);

	// STAGE 5: 1.21's `ARGB` (spelled `FastColor.ARGB32` below 1.21.11 by the controller's
	// own replacement) gained `opaque`; the 1.20.1 class has only the four-channel `color`.
	// One line, restored here rather than reached for — Skill Proficiencies did the same for
	// the same two members.
	//? if >=1.21 {
	//?} else {
	/*private static int archetypes$opaque(final int rgb) {
		return 0xFF000000 | rgb;
	}
	*///?}

	private ExtraSensoryPerception() {
	}

	/**
	 * The outline this entity should wear for the local player, or
	 * {@link EntityRenderState#NO_OUTLINE} when nothing marks or senses it.
	 *
	 * <p>The mark is asked first and needs no node behind it, but it does need
	 * line of sight unless Stalk paid for the exemption — see the comment on
	 * that branch for why the occlusion hook alone does not hold it back. Only
	 * the local player's OWN mark counts: {@code MARKED_BY} is synced to
	 * everyone so the body can be asked without handing out a roster, but the
	 * outline is the assassin's tool, and a bystander lighting up someone
	 * else's quarry would hand the whole server the hunt.
	 */
	public static int outlineColor(final Entity entity) {
		Player self = Minecraft.getInstance().player;

		if (self == null || entity == self) {
			//? if >=1.21.11 {
			return EntityRenderState.NO_OUTLINE;
			//?} else {
			/*return 0;
			*///?}
		}

		if (DeathMark.isMarkedBy(entity, self)) {
			// Line of sight, not the extractor's visibility test, decides
			// whether a bare mark is lit. {@code LevelExtractor.isEntityVisible}
			// is frustum plus a per-chunk-section occlusion graph — it lets
			// through anything in a section the player can see into — and the
			// outline is drawn to its own render target and composited over the
			// world, so a mark two blocks behind a wall would already glow.
			// Refusing the colour here is what keeps Stalk's whole perk from
			// being free.
			//? if >=1.21.11 {
			return DeathMark.rank(self, NemesisAssassinNodes.Family.STALK) > 0
					|| self.hasLineOfSight(entity) ? MARK_COLOR : EntityRenderState.NO_OUTLINE;
			//?} else {
			/*return DeathMark.rank(self, NemesisAssassinNodes.Family.STALK) > 0
					|| self.hasLineOfSight(entity) ? MARK_COLOR : 0;
			*///?}
		}

		return sensedColor(self, entity);
	}

	/**
	 * Whether this entity's outline survives the occlusion test — the question
	 * the visibility hook asks before it lets a walled-off creature through.
	 * This is the ONLY thing Stalk grants that the bare mark does not.
	 *
	 * <p>ESP keeps its own exemption unchanged: a roster built by sensing is
	 * worth nothing if terrain can hide what it sensed. A mark without Stalk
	 * gets no exemption from either branch, so it is outlined only while it is
	 * genuinely in sight.
	 */
	public static boolean piercesWalls(final Entity entity) {
		Player self = Minecraft.getInstance().player;

		if (self == null || entity == self) {
			return false;
		}

		if (DeathMark.isMarkedBy(entity, self)
				&& DeathMark.rank(self, NemesisAssassinNodes.Family.STALK) > 0) {
			return true;
		}

		// The player roster is a subset of the creature roster (NightForm
		// builds it that way), so one membership test covers both.
		return NightForm.isActive(self) && contains(NightForm.sensed(self), entity.getId());
	}

	/** ESP's half of the answer, shared by both questions so the two can never
	 * disagree about who is sensed. */
	private static int sensedColor(final Player self, final Entity entity) {
		if (!NightForm.isActive(self)) {
			//? if >=1.21.11 {
			return EntityRenderState.NO_OUTLINE;
			//?} else {
			/*return 0;
			*///?}
		}

		int id = entity.getId();

		if (contains(NightForm.sensedPlayers(self), id)) {
			return PLAYER_COLOR;
		}

		//? if >=1.21.11 {
		return contains(NightForm.sensed(self), id) ? CREATURE_COLOR
				: EntityRenderState.NO_OUTLINE;
		//?} else {
		/*return contains(NightForm.sensed(self), id) ? CREATURE_COLOR : 0;
		*///?}
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
