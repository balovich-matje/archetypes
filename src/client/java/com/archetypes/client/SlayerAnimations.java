package com.archetypes.client;

import com.archetypes.Archetypes;
import com.archetypes.ModAttachments;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.resources.Identifier;

/**
 * Experiment branch: real player poses via Player Animation Library. One
 * controller layer per player; its state handler watches the synced
 * bladestorm-end attachment and loops the spin animation while the channel
 * runs — body and camera-independent, first and third person, ours to keyframe.
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
			PlayerAnimationController controller = new PlayerAnimationController(avatar,
					(ctrl, data, setter) -> {
						long now = avatar.level().getGameTime();

						Long end = ((AttachmentTarget) avatar).getAttached(ModAttachments.BLADESTORM_END);
						boolean storming = (end != null && end > now)
								|| avatar.isShiftKeyDown(); // XXX debug probe, do not commit

						if (storming) {
							return PlayerAnimResources.getAnimationOptional(BLADESTORM_ANIM)
									.map(animation -> setter.setAnimation(RawAnimation.begin().thenLoop(animation)))
									.orElse(PlayState.STOP);
						}

						Long swingAt = ((AttachmentTarget) avatar).getAttached(ModAttachments.DECIMATE_SWING_AT);

						if (swingAt != null && now - swingAt < DECIMATE_SWING_TICKS) {
							return PlayerAnimResources.getAnimationOptional(DECIMATE_ANIM)
									.map(animation -> setter.setAnimation(RawAnimation.begin().thenPlay(animation)))
									.orElse(PlayState.STOP);
						}

						return PlayState.STOP;
					});

			// In first person, swap the vanilla arms for the animated
			// third-person model while the storm runs — otherwise the spin is
			// invisible to the player doing it.
			controller.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
			controller.setFirstPersonConfiguration(
					new FirstPersonConfiguration(true, true, true, false));
			return controller;
		});

		// XXX debug probe, do not commit: log PAL's first-person gate once a second.
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.player.tickCount % 20 != 0 || !client.player.isShiftKeyDown()) {
				return;
			}
			var manager = ((com.zigythebird.playeranim.accessors.IAnimatedAvatar) client.player)
					.playerAnimLib$getAnimManager();
			com.archetypes.Archetypes.LOGGER.info(
					"[FP probe] shouldBeFirstPersonPass={} managerActive={} managerMode={} detached={}",
					com.zigythebird.playeranim.util.ClientUtil.shouldBeFirstPersonPass(),
					manager.isActive(), manager.getFirstPersonMode(),
					client.gameRenderer.mainCamera().isDetached());
		});
	}
}
