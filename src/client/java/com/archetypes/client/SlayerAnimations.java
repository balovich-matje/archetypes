package com.archetypes.client;

import com.archetypes.Archetypes;
import com.archetypes.ModAttachments;
import com.zigythebird.playeranim.accessors.IAnimatedAvatar;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;

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
 */
public final class SlayerAnimations {
	private static final Identifier LAYER_ID = Archetypes.id("slayer_pose");
	private static final Identifier BLADESTORM_ANIM = Archetypes.id("bladestorm");
	private static final Identifier DECIMATE_ANIM = Archetypes.id("decimate");
	/** Decimate's pose length in ticks; matches decimate.json's 0.6s. */
	private static final int DECIMATE_SWING_TICKS = 12;

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
				drive(player, client.level.getGameTime());
			}
		});
	}

	/** Mirror the player's synced ability state onto their pose controller. */
	private static void drive(final AbstractClientPlayer player, final long now) {
		if (!(((Avatar) player) instanceof IAnimatedAvatar animated)
				|| !(animated.playerAnimLib$getAnimation(LAYER_ID)
						instanceof PlayerAnimationController controller)) {
			return;
		}

		AttachmentTarget target = (AttachmentTarget) player;
		Long end = target.getAttached(ModAttachments.BLADESTORM_END);
		boolean storming = end != null && end > now;
		Long swingAt = target.getAttached(ModAttachments.DECIMATE_SWING_AT);
		boolean cleaving = swingAt != null && now - swingAt < DECIMATE_SWING_TICKS;

		if ((storming || cleaving) && !controller.isActive()) {
			// Loop flags come from the animation files: bladestorm loops until
			// stopped below, decimate plays once and stops itself.
			controller.triggerAnimation(storming ? BLADESTORM_ANIM : DECIMATE_ANIM);
		} else if (!storming && !cleaving && controller.isActive()
				&& controller.getTriggeredAnimation() != null) {
			controller.stop();
		}
	}
}
