package com.archetypes.client;

import com.archetypes.Archetypes;
import com.archetypes.ModState;
import com.archetypes.NodePurchases;
import com.archetypes.SlayerNodes;
import com.archetypes.SubTree;
import com.archetypes.Tuning;
import com.zigythebird.playeranim.accessors.IAnimatedAvatar;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;

import com.archetypes.platform.ArchetypeStore;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

/**
 * Experiment branch: real player poses via Player Animation Library.
 *
 * One controller layer per player, but the controller's own state handler
 * never starts anything: PAL only consults it while the controller is active
 * or the player is being rendered, so a stopped controller on an unrendered
 * player — exactly the first-person case — would deadlock, never starting and
 * therefore never opening PAL's first-person render pass. Instead a client
 * tick pass mirrors the synced ability attachments into explicit
 * triggerAnimation/stop calls, which force the controller RUNNING from any
 * state. Same path for every camera and for onlookers.
 *
 * <h2>Decimate's two poses</h2>
 * Decimate is cast twice over, and the two casts are different beats:
 *
 * <ul>
 *   <li>The key's cast winds up for {@link Tuning#DECIMATE_WINDUP_TICKS} and
 *       lands on the last tick of it. It plays {@code decimate_charge.json},
 *       which hauls the greatsword up behind the right shoulder, holds the
 *       coil, and drops the cleave on animation tick 20 — the same tick
 *       {@code SlayerActives}' pending list resolves the blow.</li>
 *   <li>The Colossus Slayer parry's riposte lands the tick it is cast and
 *       keeps the original {@code decimate.json}: a 12-tick cleave with no
 *       wind-up, because a parry is already a reaction and must not look like
 *       it is asking for a second one.</li>
 * </ul>
 *
 * <p>They are two files rather than one file entered at an offset. The cleave
 * out of a full coil wants a follow-through the counter must not have, the
 * riposte's animation is meant to be exactly what it always was, and coupling
 * them would mean every retime of the wind-up silently retimes the riposte.
 * The start offset PAL does give us is spent on the thing it is actually good
 * at, in {@link #drive}: entering an animation that is ALREADY RUNNING at the
 * right tick.
 *
 * <p><b>Each window below must equal its file's total length in ticks.</b>
 * {@link #drive} re-triggers whenever the controller is not active, so a
 * window longer than its animation restarts the animation and loops it; a
 * window shorter than its animation calls {@code stop()} early and snaps the
 * body back to vanilla. Both files therefore end on an all-zero keyframe
 * before their last tick, so a stop anywhere in the tail is invisible.
 */
public final class SlayerAnimations {
	private static final Identifier LAYER_ID = Archetypes.id("slayer_pose");
	private static final Identifier BLADESTORM_ANIM = Archetypes.id("bladestorm");
	private static final Identifier DECIMATE_ANIM = Archetypes.id("decimate");
	private static final Identifier DECIMATE_CHARGE_ANIM = Archetypes.id("decimate_charge");
	private static final Identifier QUAKE_ANIM = Archetypes.id("quake_charge");
	/** The riposte's instant cleave; matches decimate.json's 0.6s. */
	private static final int DECIMATE_SWING_TICKS = 12;
	/** The charged cast: decimate_charge.json's stopTick. Twenty ticks of
	 * wind-up-then-cleave — impact ON tick 20, matching
	 * {@link Tuning#DECIMATE_WINDUP_TICKS} — plus six of follow-through back to
	 * neutral and two of hand-back. */
	private static final int DECIMATE_CHARGE_TICKS = 28;

	private SlayerAnimations() {
	}

	public static void initialize() {
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER_ID, 1000, avatar -> {
			// The handler runs while a state-handler animation plays (never —
			// we only trigger) and once after a triggered one-shot finishes,
			// where STOP is the right answer anyway.
			PlayerAnimationController controller =
					new PlayerAnimationController(avatar, (ctrl, data, setter) -> PlayState.STOP);

			// In first person, swap the vanilla arms for the animated
			// third-person model while a pose runs — otherwise the spin is
			// invisible to the player doing it.
			controller.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
			controller.setFirstPersonConfiguration(
					new FirstPersonConfiguration(true, true, true, false));
			return controller;
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null) {
				return;
			}

			for (AbstractClientPlayer player : client.level.players()) {
				drive(player, client.player, client.level.getGameTime());
			}
		});
	}

	/** Mirror the player's synced ability state onto their pose controller. */
	private static void drive(final AbstractClientPlayer player, final Player self, final long now) {
		if (!(((Avatar) player) instanceof IAnimatedAvatar animated)
				|| !(animated.playerAnimLib$getAnimation(LAYER_ID)
						instanceof PlayerAnimationController controller)) {
			return;
		}

		final Entity target = player;
		Long end = ArchetypeStore.INSTANCE.get(target, ModState.BLADESTORM_END);
		boolean storming = end != null && end > now;
		Long swingAt = ArchetypeStore.INSTANCE.get(target, ModState.DECIMATE_SWING_AT);
		boolean charged = swingAt != null && chargedCast(player, self, swingAt);
		// Ticks the swing is already into itself. Normally 0 on the tick the
		// attachment arrives, but not for a client that learned late.
		long since = swingAt == null ? 0L : now - swingAt;
		boolean cleaving = swingAt != null && since >= 0
				&& since < (charged ? DECIMATE_CHARGE_TICKS : DECIMATE_SWING_TICKS);
		Long chargeEnd = ArchetypeStore.INSTANCE.get(target, ModState.QUAKE_CHARGE_END);
		boolean quaking = chargeEnd != null && chargeEnd > now;

		if ((storming || cleaving || quaking) && !controller.isActive()) {
			// Loop flags come from the animation files: bladestorm loops until
			// stopped below, the one-shots play once and stop themselves.
			if (storming) {
				controller.triggerAnimation(BLADESTORM_ANIM);
			} else if (quaking) {
				controller.triggerAnimation(QUAKE_ANIM);
			} else {
				// Enter the cleave where it already is rather than at zero.
				// A player who walks into view halfway through a wind-up, or a
				// client the attachment reached a few ticks late, still sees
				// the impact frame on the tick the server lands the blow.
				controller.triggerAnimation(charged ? DECIMATE_CHARGE_ANIM : DECIMATE_ANIM, since);
			}
		} else if (!storming && !cleaving && !quaking && controller.isActive()
				&& controller.getTriggeredAnimation() != null) {
			controller.stop();
		}
	}

	/**
	 * Which of Decimate's two paths wrote this swing stamp.
	 *
	 * <p>Both paths call the same {@code pose()}, so {@code DECIMATE_SWING_AT}
	 * alone reads identically for a charged cast and for a riposte. The free
	 * branch therefore stamps {@code DECIMATE_INSTANT_AT} beside it, synced to
	 * everyone for exactly this: the wind-up is the victim's entire counterplay
	 * budget, so a telegraph an onlooker cannot see is a broken ability, and a
	 * riposte an onlooker sees telegraphed is a lie about a blow that already
	 * landed. Equality with the swing stamp is what makes a stale value from an
	 * earlier riposte harmless.
	 */
	private static boolean chargedCast(final AbstractClientPlayer player, final Player self,
			final long swingAt) {
		Long instantAt = ArchetypeStore.INSTANCE.get(player, ModState.DECIMATE_INSTANT_AT);
		return instantAt == null || instantAt != swingAt;
	}

}
